package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Attempt;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Phase;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.SqlScope;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.OrderRow;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationTestFixture;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T020 authorship: B must commit while A is physically blocked; execution belongs to S3. */
@RootHttpContext
@Import(PaymentRegistrationObservationConfig.class)
class PaymentDistinctOrdersIntegrationTest extends PaymentRegistrationHttpTestSupport {
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final DateTimeFormatter UTC_MILLISECONDS = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    @Autowired private Observation observation;
    @Autowired private OrderRepository orders;
    private PaymentRegistrationTestFixture fixture;
    private PaymentRegistrationDatabaseProbe database;
    private Snapshot cleanupBaseline;
    private OrderManifest orderA, orderB, canary;
    private String tokenA, tokenB;
    private Instant reference, occurrenceA, occurrenceB;

    @Override protected String expectedContextPath() { return ""; }

    @BeforeEach
    void prepareCommittedDistinctHistoriesAndRealAdministrativeCredentials() {
        cleanupBaseline = null;
        fixture = new PaymentRegistrationTestFixture(context);
        database = new PaymentRegistrationDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        cleanupBaseline = database.snapshot();
        var accounts = fixture.prepareAccessAccounts();
        var buyer = fixture.prepareBuyer();
        var productA = fixture.createProduct("T020 catalog A", new BigDecimal("12.00"));
        var productB = fixture.createProduct("T020 catalog B", new BigDecimal("99.00"));
        tokenA = realHttpLogin(accounts.admin().id(), accounts.admin().name(), accounts.admin().password());
        tokenB = realHttpLogin(accounts.both().id(), accounts.both().name(), accounts.both().password());
        reference = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        clock.setInstant(reference);
        occurrenceA = reference.minusSeconds(3600);
        occurrenceB = occurrenceA.plusMillis(1);
        orderA = fixture.createOrder(buyer, reference.minusSeconds(7200),
                List.of(new ItemInput(productA.id(), 2, "10.00", "20.00")), "20.00");
        orderB = fixture.createOrder(accounts.client(), reference.minusSeconds(10800), List.of(
                new ItemInput(productA.id(), 2, "10.00", "20.00"),
                new ItemInput(productB.id(), 3, "7.50", "22.50")), "42.50");
        canary = fixture.createOrder(buyer, reference.minusSeconds(14400),
                List.of(new ItemInput(productB.id(), 1, "8.00", "8.00")), "8.00");
        requirePaymentServiceProxy();
    }

    @AfterEach
    void cleanOnlyOwnedRowsAfterWindowsLocksAndObserversClose() {
        try { if (fixture != null) { fixture.close(); } }
        finally {
            if (cleanupBaseline != null) {
                PaymentRegistrationDatabaseProbe.assertUnchanged(cleanupBaseline, database.snapshot());
            }
        }
    }

    @ParameterizedTest(name = "hold order {0}; the other commits before release")
    @ValueSource(strings = {"A", "B"})
    void distinctOrderCommitsWhileTheOtherStillWaitsForItsRootLock(String heldOrder) throws Exception {
        assertNoExternalTransaction();
        var before = database.snapshot();
        assertThat(orders).isInstanceOf(Advised.class);
        var repository = (Advised) orders;
        // Closing order is intentional: auxiliary lock first, then window joins HTTP, then remove advice.
        try (var window = observation.openWindow(WAIT)) {
            var a = new RequestState(window.newRequest(), orderA, occurrenceA, tokenA);
            var b = new RequestState(window.newRequest(), orderB, occurrenceB, tokenB);
            var states = Map.of(orderA.id(), a, orderB.id(), b);
            var blocked = heldOrder.equals("A") ? a : b;
            var free = heldOrder.equals("A") ? b : a;
            MethodInterceptor acquisition = invocation -> {
                if (!invocation.getMethod().getName().equals("findPaymentOrderForUpdate")
                        || invocation.getArguments().length != 1
                        || !(invocation.getArguments()[0] instanceof Long id) || !states.containsKey(id)) {
                    return invocation.proceed();
                }
                var state = states.get(id);
                assertThat(state.lookups.incrementAndGet()).isEqualTo(1);
                var connection = DataSourceUtils.getConnection(dataSource);
                try {
                    assertOwnedPaymentTransaction(connection);
                    state.session.set(session(connection));
                    state.url = connection.getMetaData().getURL();
                    state.threadId = Thread.currentThread().threadId();
                } finally { DataSourceUtils.releaseConnection(connection, dataSource); }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {
                        assertThat(readOnly).isFalse();
                        state.beforeCommits.incrementAndGet();
                    }
                    @Override public void afterCommit() { state.commits.incrementAndGet(); }
                    @Override public void afterCompletion(int status) { state.completion.set(status); }
                });
                var result = invocation.proceed(); // Keep the real root acquisition and refresh.
                assertThat(result).isInstanceOf(Optional.class);
                var root = ((Optional<?>) result).orElseThrow();
                assertThat(root).isInstanceOf(Order.class);
                state.loadedStatus = ((Order) root).getStatus().name();
                return result;
            };
            repository.addAdvice(0, acquisition);
            try {
                try (var holder = window.independentTransaction(database);
                     var lockSql = blocked.attempt.pauseAt(Phase.ORDER_LOCK_SQL)) {
                    int holderSession;
                    try (var query = holder.prepare("select session_id()"); var rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        holderSession = rows.getInt(1);
                        assertThat(holderSession).isPositive();
                        assertThat(rows.next()).isFalse();
                    }
                    try (var query = holder.prepare("select id from tb_order where id = ? for update")) {
                        query.setLong(1, blocked.order.id());
                        try (var rows = query.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getLong(1)).isEqualTo(blocked.order.id());
                            assertThat(rows.next()).isFalse();
                        }
                    }
                    var pendingBlocked = window.submit(() -> send(request(blocked)));
                    lockSql.awaitReached();
                    assertThat(blocked.session.get()).isPositive().isNotEqualTo(holderSession);
                    lockSql.release();
                    assertBlocked(holderSession, blocked);
                    assertThat(pendingBlocked.isDone()).isFalse();
                    PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());

                    var pendingFree = window.submit(() -> send(request(free)));
                    assertSuccess(window.awaitResult(pendingFree), free);
                    assertThat(free.session.get()).isPositive().isNotIn(holderSession, blocked.session.get());
                    assertThat(free.url).isEqualTo(blocked.url);
                    assertThat(free.threadId).isPositive().isNotEqualTo(blocked.threadId);
                    assertSettlements(before, database.snapshot(), List.of(new PaymentRow(free.order.id(), free.occurrence)));
                    // B's 201 + proxy completion + independent JDBC prove commit while A still cannot acquire its root.
                    assertBlocked(holderSession, blocked);
                    assertThat(pendingBlocked.isDone()).isFalse();
                    assertThat(blocked.loadedStatus).isNull();
                    assertThat(blocked.commits.get()).isZero();
                    assertThat(blocked.attempt.sql()).isNotEmpty().allSatisfy(event ->
                            assertThat(event.sql().strip().toLowerCase(Locale.ROOT)).startsWith("select "));
                    assertThat(bodySql(blocked.attempt)).noneSatisfy(sql ->
                            assertThat(sql).containsPattern("\\b(?:tb_payment|tb_order_item|tb_product)\\b"));
                    System.out.println("T020_DISTINCT committedOrder=" + free.order.id() + " session=" + free.session.get()
                            + " blockedOrder=" + blocked.order.id() + " session=" + blocked.session.get()
                            + " holder=" + holderSession);
                    holder.rollback();
                    assertSuccess(window.awaitResult(pendingBlocked), blocked);
                    assertSettlements(before, database.snapshot(), List.of(
                            new PaymentRow(orderA.id(), occurrenceA), new PaymentRow(orderB.id(), occurrenceB)));
                }
            } finally {
                // Window must join/cancel requests before advice is detached, including failure paths.
                try { window.close(); }
                finally { assertThat(repository.removeAdvice(acquisition)).isTrue(); }
            }
        }
        assertRootAvailable(orderA.id());
        assertRootAvailable(orderB.id());
    }

    private HttpRequest request(RequestState state) {
        return jsonRequest("/admin/orders/" + state.order.id() + "/payments")
                .header("Authorization", "Bearer " + state.token)
                .header(PaymentRegistrationObservationConfig.HEADER, state.attempt.id())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.createObjectNode()
                        .put("occurredAt", UTC_MILLISECONDS.format(state.occurrence)).toString())).build();
    }

    private void assertSuccess(HttpResponse<byte[]> response, RequestState state) throws Exception {
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().allValues("Location")).isEmpty();
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        var expected = mapper.createObjectNode().put("orderId", state.order.id())
                .put("occurredAt", UTC_MILLISECONDS.format(state.occurrence)).put("status", "PAID");
        var actual = mapper.readTree(response.body());
        assertThat(actual).isEqualTo(mapper.readTree(expected.toString()));
        assertThat(actual.get("orderId").isIntegralNumber()).isTrue();
        assertThat(actual.get("orderId").canConvertToLong()).isTrue();
        assertThat(actual.get("orderId").longValue()).isEqualTo(state.order.id());
        state.attempt.awaitFinished();
        state.attempt.assertOwnedTransactionObserved();
        assertThat(state.attempt.reference()).isEqualTo(reference);
        assertThat(state.attempt.clockCalls()).isEqualTo(1);
        assertThat(state.attempt.proxyReturned()).isTrue();
        assertThat(state.attempt.proxyFailed()).isFalse();
        assertThat(state.loadedStatus).isEqualTo("WAITING_PAYMENT");
        assertThat(state.lookups.get()).isEqualTo(1);
        assertThat(state.beforeCommits.get()).isEqualTo(1);
        assertThat(state.commits.get()).isEqualTo(1);
        assertThat(state.completion.get()).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        var sql = bodySql(state.attempt);
        var locks = sql.stream().filter(statement -> statement.contains("for update")).toList();
        assertThat(locks).hasSize(2).allSatisfy(statement -> {
            assertThat(statement).startsWith("select ").contains("from tb_order ", " where ");
            assertThat(statement).doesNotContain(" join ", "distinct", "nowait", "skip locked");
            assertThat(statement).doesNotContainPattern("\\b(?:tb_product|tb_order_item|tb_payment)\\b");
            assertThat(statement.substring(statement.indexOf(" where ")))
                    .matches(" where (?:[a-z0-9_]+\\.)?id\\s*=\\s*\\? for update");
        });
        assertThat(sql).noneSatisfy(statement -> assertThat(statement).containsPattern("\\btb_product\\b"));
        var writes = sql.stream().filter(statement -> !statement.startsWith("select ")).toList();
        assertThat(writes).hasSize(2);
        assertThat(writes.stream().filter(statement -> statement.startsWith("insert into tb_payment "))).hasSize(1);
        assertThat(writes.stream().filter(statement -> statement.startsWith("update tb_order "))).hasSize(1);
    }

    private List<String> bodySql(Attempt attempt) {
        var events = attempt.sql().stream().filter(event -> event.scope() == SqlScope.SERVICE_BODY).toList();
        assertThat(events).isNotEmpty().allSatisfy(event -> assertThat(event.transactionActive()).isTrue());
        return events.stream().map(event -> event.sql().replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT)).toList();
    }

    private void assertSettlements(Snapshot before, Snapshot after, List<PaymentRow> accepted) {
        assertThat(after.payments()).containsExactlyInAnyOrderElementsOf(Stream.concat(
                before.payments().stream(), accepted.stream()).toList());
        var ids = accepted.stream().map(PaymentRow::orderId).toList();
        assertThat(after.previous().data().orders()).isEqualTo(before.previous().data().orders().stream()
                .map(row -> ids.contains(row.id()) ? new OrderRow(row.id(), row.buyerId(), row.createdAt(), "PAID") : row).toList());
        assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before.previous(), after.previous()))
                .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_order");
        for (var order : List.of(orderA, orderB, canary)) {
            var total = after.previous().data().items().stream().filter(row -> row.orderId() == order.id())
                    .map(row -> row.unitPrice().multiply(BigDecimal.valueOf(row.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(total).isEqualByComparingTo(order.total());
        }
    }

    private void assertBlocked(int holderSession, RequestState blocked) throws SQLException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        try (var connection = database.openConnection(); var query = connection.prepareStatement(
                "select session_state, blocker_id, executing_statement from information_schema.sessions where session_id = ?")) {
            assertPaymentConnection(connection);
            assertThat(session(connection)).isNotIn(holderSession, blocked.session.get());
            assertThat(connection.getMetaData().getURL()).isEqualTo(blocked.url);
            query.setQueryTimeout(2);
            query.setInt(1, blocked.session.get());
            while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
                try (var rows = query.executeQuery()) {
                    if (rows.next() && "BLOCKED".equals(rows.getString(1)) && rows.getInt(2) == holderSession) {
                        assertThat(rows.getString(3).toLowerCase(Locale.ROOT)).contains("tb_order", "for update");
                        return;
                    }
                }
                // Only polling cadence; the metadata above, never elapsed time, is the ordering oracle.
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            }
        }
        throw new AssertionError("T020 coordination timeout: expected physical root wait behind auxiliary holder");
    }

    private static int session(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement("select session_id()")) {
            query.setQueryTimeout(2);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                int id = rows.getInt(1);
                assertThat(rows.next()).isFalse();
                return id;
            }
        }
    }

    private void assertRootAvailable(long id) throws SQLException {
        try (var connection = database.openConnection()) {
            assertPaymentConnection(connection);
            connection.setAutoCommit(false);
            try (var timeout = connection.createStatement()) { timeout.execute("set lock_timeout 2000"); }
            try (var query = connection.prepareStatement("select id from tb_order where id = ? for update")) {
                query.setQueryTimeout(2);
                query.setLong(1, id);
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isEqualTo(id);
                    assertThat(rows.next()).isFalse();
                }
            } finally { connection.rollback(); }
        }
    }

    private static final class RequestState {
        final Attempt attempt;
        final OrderManifest order;
        final Instant occurrence;
        final String token;
        final AtomicInteger session = new AtomicInteger(), lookups = new AtomicInteger();
        final AtomicInteger beforeCommits = new AtomicInteger(), commits = new AtomicInteger();
        final AtomicInteger completion = new AtomicInteger(-1);
        volatile long threadId;
        volatile String url, loadedStatus;
        RequestState(Attempt attempt, OrderManifest order, Instant occurrence, String token) {
            this.attempt = attempt; this.order = order; this.occurrence = occurrence; this.token = token;
        }
    }
}
