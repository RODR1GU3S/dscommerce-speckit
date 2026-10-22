package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Attempt;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Phase;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.SqlScope;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.OrderRow;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationTestFixture;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T019 authorship only. Execution belongs to S3; callbacks are not driver commit instrumentation. */
@RootHttpContext
@Import(PaymentRegistrationObservationConfig.class)
class PaymentConcurrencyIntegrationTest extends PaymentRegistrationHttpTestSupport {
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final DateTimeFormatter UTC_MILLISECONDS = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    @Autowired private Observation observation;
    @Autowired private OrderRepository orders;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    private PaymentRegistrationTestFixture fixture;
    private PaymentRegistrationDatabaseProbe database;
    private Snapshot cleanupBaseline;
    private Account adminA, adminB;
    private OrderManifest target, other;
    private String tokenA, tokenB;
    private Instant reference, occurrence;

    @Override protected String expectedContextPath() { return ""; }

    @BeforeEach
    void prepareOwnedCommittedOrdersAndTwoRealAdministrativeCredentials() {
        cleanupBaseline = null;
        fixture = new PaymentRegistrationTestFixture(context);
        database = new PaymentRegistrationDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        cleanupBaseline = database.snapshot();
        var accounts = fixture.prepareAccessAccounts();
        adminA = accounts.admin();
        adminB = accounts.both();
        var buyer = fixture.prepareBuyer();
        var product = fixture.createProduct("T019 current catalog", new BigDecimal("12.00"));
        tokenA = realHttpLogin(adminA.id(), adminA.name(), adminA.password());
        tokenB = realHttpLogin(adminB.id(), adminB.name(), adminB.password());
        reference = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        clock.setInstant(reference);
        occurrence = reference.minusSeconds(3600);
        target = fixture.createOrder(buyer, reference.minusSeconds(7200),
                List.of(new ItemInput(product.id(), 2, "10.00", "20.00")), "20.00");
        other = fixture.createOrder(accounts.client(), reference.minusSeconds(10800),
                List.of(new ItemInput(product.id(), 3, "7.50", "22.50")), "22.50");
        requirePaymentServiceProxy();
    }

    @AfterEach
    void cleanOnlyOwnedRowsAfterAllGatesRequestsAndTransactionsClose() {
        try {
            if (fixture != null) {
                fixture.close();
                if (cleanupBaseline != null) {
                    PaymentRegistrationDatabaseProbe.assertUnchanged(cleanupBaseline, database.snapshot());
                }
            }
        } finally {
            fixture = null;
            cleanupBaseline = null;
            tokenA = null;
            tokenB = null;
        }
    }

    @ParameterizedTest(name = "US3.1/2 repeat actor={0}, same occurrence={1}")
    @CsvSource({"A,true", "A,false", "B,true", "B,false"})
    void sequentialRepeatNeverReusesSuccessOrOverwritesTheOriginal(String actor, boolean sameOccurrence) throws Exception {
        settleOriginal();
        var before = database.snapshot();
        rejectRepeat(actor, sameOccurrence, before);
    }

    @ParameterizedTest(name = "US3.3 existing Payment with WAITING_PAYMENT, actor={0}, same occurrence={1}")
    @CsvSource({"A,true", "A,false", "B,true", "B,false"})
    void existingPaymentPrecedesWaitingStateWithoutRepair(String actor, boolean sameOccurrence) throws Exception {
        settleOriginal();
        var paid = database.snapshot();
        assertNoExternalTransaction();
        var setup = new TransactionTemplate(transactions);
        setup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        setup.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        setup.executeWithoutResult(status -> {
            assertThat(status.isNewTransaction()).isTrue();
            assertThat(jdbc.update("update tb_order set status = ? where id = ?", "WAITING_PAYMENT", target.id())).isEqualTo(1);
        });
        var prepared = database.snapshot(); // Deliberate committed corruption belongs to setup, not the POST.
        assertThat(prepared.payments()).isEqualTo(paid.payments());
        assertThat(prepared.previous().data().orders()).isEqualTo(paid.previous().data().orders().stream()
                .map(row -> row.id() == target.id()
                        ? new OrderRow(row.id(), row.buyerId(), row.createdAt(), "WAITING_PAYMENT") : row).toList());
        assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(paid.previous(), prepared.previous()))
                .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_order");
        rejectRepeat(actor, sameOccurrence, prepared);
    }

    @ParameterizedTest(name = "US3.4 first root holder={0}, same occurrence={1}")
    @CsvSource({"A,true", "A,false", "B,true", "B,false"})
    void overlappingTransactionsForTheSameOrderAcceptExactlyOnePayment(String firstActor, boolean sameOccurrence)
            throws Exception {
        var before = database.snapshot();
        try (var race = new RaceObservation(); var window = observation.openWindow(WAIT);
             var release = race.releaseGatesOnClose()) {
            var first = race.add(window.newRequest(), account(firstActor), target, occurrence, true);
            var second = race.add(window.newRequest(), account(firstActor.equals("A") ? "B" : "A"), target,
                    sameOccurrence ? occurrence : occurrence.plusMillis(1), false);
            try (var secondSql = second.attempt.pauseAt(Phase.ORDER_LOCK_SQL)) {
                var winningRequest = window.submit(() -> send(request(first)));
                await(first.acquired, "first real lock and refresh");
                assertThat(first.loadedStatus).isEqualTo("WAITING_PAYMENT");
                var losingRequest = window.submit(() -> send(request(second)));
                secondSql.awaitReached();
                secondSql.release();
                assertIndependentTransactions(first, second);
                assertBlocked(first, second);
                assertThat(second.acquired.getCount()).isEqualTo(1);
                assertThat(winningRequest.isDone()).isFalse();
                assertThat(losingRequest.isDone()).isFalse();
                assertSelectOnly(first.attempt);
                assertSelectOnly(second.attempt);
                PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());

                first.allowReturn.countDown();
                await(first.beforeCommitReached, "first flushed body reaches beforeCommit");
                assertThat(first.commits.get()).isZero();
                assertThat(first.completion.get()).isEqualTo(-1);
                assertBlocked(first, second); // Root remains locked after flush and before commit.
                assertThat(second.acquired.getCount()).isEqualTo(1);
                PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());
                assertSuccessfulSql(first.attempt);
                first.allowCommit.countDown();

                await(second.acquired, "second lock and refresh after first commit releases the root");
                assertThat(second.loadedStatus).isEqualTo("PAID");
                assertThat(losingRequest.isDone()).isFalse(); // Held before second service eligibility checks.
                var accepted = window.awaitResult(winningRequest);
                first.attempt.awaitFinished();
                assertSuccess(accepted, target, first.occurrence, first.attempt);
                assertCommitted(first);
                var winnerCommitted = database.snapshot();
                assertSettlements(before, winnerCommitted, List.of(new PaymentRow(target.id(), first.occurrence)));

                second.allowReturn.countDown();
                var rejected = window.awaitResult(losingRequest);
                second.attempt.awaitFinished();
                assertRepeat(rejected, target.id(), second.attempt);
                assertThat(List.of(accepted.statusCode(), rejected.statusCode())).containsExactlyInAnyOrder(201, 409);
                assertThat(second.commits.get()).isZero();
                assertThat(second.beforeCommits.get()).isZero();
                assertCompletion(second, TransactionSynchronization.STATUS_ROLLED_BACK);
                PaymentRegistrationDatabaseProbe.assertUnchanged(winnerCommitted, database.snapshot());
                assertRootAvailable(target.id());
                System.out.println("T019_RACE first=" + firstActor + " sameOccurrence=" + sameOccurrence
                        + " sessions=" + first.session.get() + "," + second.session.get()
                        + " statuses=201,409 commits=1 loserRollback=1 originalOccurrencePreserved=true");
            }
        }
    }

    /** Basic requested positive control; T020 still owns B-commits-while-A-stays-blocked and stale-context refresh. */
    @Test
    void validRequestsForDistinctOrdersBothSettleWithOverlappingTransactions() throws Exception {
        var before = database.snapshot();
        try (var race = new RaceObservation(); var window = observation.openWindow(WAIT);
             var release = race.releaseGatesOnClose()) {
            var first = race.add(window.newRequest(), adminA, target, occurrence, false);
            var second = race.add(window.newRequest(), adminB, other, occurrence.plusMillis(1), false);
            var a = window.submit(() -> send(request(first)));
            var b = window.submit(() -> send(request(second)));
            await(first.acquired, "first distinct root acquired");
            await(second.acquired, "second distinct root acquired while first transaction is open");
            assertIndependentTransactions(first, second);
            assertThat(a.isDone()).isFalse();
            assertThat(b.isDone()).isFalse();
            assertThat(first.loadedStatus).isEqualTo("WAITING_PAYMENT");
            assertThat(second.loadedStatus).isEqualTo("WAITING_PAYMENT");
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());
            first.allowReturn.countDown();
            second.allowReturn.countDown();
            var acceptedA = window.awaitResult(a);
            var acceptedB = window.awaitResult(b);
            first.attempt.awaitFinished();
            second.attempt.awaitFinished();
            assertSuccess(acceptedA, target, first.occurrence, first.attempt);
            assertSuccess(acceptedB, other, second.occurrence, second.attempt);
            assertCommitted(first);
            assertCommitted(second);
            assertSettlements(before, database.snapshot(), List.of(
                    new PaymentRow(target.id(), first.occurrence), new PaymentRow(other.id(), second.occurrence)));
            assertRootAvailable(target.id());
            assertRootAvailable(other.id());
            System.out.println("T019_DISTINCT statuses=201,201 independentTransactions=true historicalTotals=20.00,22.50");
        }
    }

    private void settleOriginal() throws Exception {
        var before = database.snapshot();
        try (var window = observation.openWindow(WAIT)) {
            var attempt = window.newRequest();
            var response = send(request(attempt, adminA, target, occurrence));
            attempt.awaitFinished();
            assertSuccess(response, target, occurrence, attempt);
            assertSettlements(before, database.snapshot(), List.of(new PaymentRow(target.id(), occurrence)));
        }
    }

    private void rejectRepeat(String actor, boolean sameOccurrence, Snapshot before) throws Exception {
        try (var window = observation.openWindow(WAIT)) {
            var attempt = window.newRequest();
            var response = send(request(attempt, account(actor), target,
                    sameOccurrence ? occurrence : occurrence.plusMillis(1)));
            attempt.awaitFinished();
            assertRepeat(response, target.id(), attempt);
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());
        }
    }

    private void assertSuccess(HttpResponse<byte[]> response, OrderManifest order, Instant occurredAt, Attempt attempt)
            throws Exception {
        assertThat(response.statusCode()).isEqualTo(201);
        assertHeaders(response);
        var expected = mapper.createObjectNode().put("orderId", order.id())
                .put("occurredAt", UTC_MILLISECONDS.format(occurredAt)).put("status", "PAID");
        var json = mapper.readTree(response.body());
        assertThat(json).isEqualTo(mapper.readTree(expected.toString()));
        assertThat(json.get("orderId").isIntegralNumber()).isTrue();
        assertThat(json.get("orderId").canConvertToLong()).isTrue();
        assertThat(json.get("orderId").longValue()).isEqualTo(order.id());
        assertAttempt(attempt, true);
        assertSuccessfulSql(attempt);
    }

    private void assertRepeat(HttpResponse<byte[]> response, long orderId, Attempt attempt) throws Exception {
        assertThat(response.statusCode()).isEqualTo(409);
        assertHeaders(response);
        var expected = mapper.createObjectNode().put("status", 409).put("error", "Conflict")
                .put("message", "Payment already registered").put("path", "/admin/orders/" + orderId + "/payments");
        assertThat(mapper.readTree(response.body())).isEqualTo(expected);
        assertAttempt(attempt, false);
        assertSelectOnly(attempt);
        var sql = bodySql(attempt);
        assertThat(sql.stream().filter(statement -> statement.contains("for update"))).hasSize(2);
        assertThat(sql).anySatisfy(statement -> assertThat(statement).contains("from tb_payment "));
        assertThat(sql).noneSatisfy(statement -> assertThat(statement).containsPattern("\\b(?:tb_order_item|tb_product)\\b"));
    }

    private void assertHeaders(HttpResponse<byte[]> response) {
        assertThat(response.headers().allValues("Location")).isEmpty();
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
    }

    private void assertAttempt(Attempt attempt, boolean success) {
        attempt.assertOwnedTransactionObserved();
        assertThat(attempt.reference()).isEqualTo(reference);
        assertThat(attempt.clockCalls()).isEqualTo(1);
        assertThat(attempt.proxyReturned()).isEqualTo(success);
        assertThat(attempt.proxyFailed()).isEqualTo(!success);
    }

    private List<String> bodySql(Attempt attempt) {
        var events = attempt.sql().stream().filter(event -> event.scope() == SqlScope.SERVICE_BODY).toList();
        assertThat(events).isNotEmpty().allSatisfy(event -> assertThat(event.transactionActive()).isTrue());
        return events.stream().map(event -> event.sql().replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT)).toList();
    }

    private void assertSelectOnly(Attempt attempt) {
        assertThat(attempt.sql()).isNotEmpty().allSatisfy(event ->
                assertThat(event.sql().strip().toLowerCase(Locale.ROOT)).startsWith("select "));
    }

    private void assertSuccessfulSql(Attempt attempt) {
        var sql = bodySql(attempt);
        assertThat(sql.stream().filter(statement -> statement.contains("for update"))).hasSize(2);
        assertThat(sql).noneSatisfy(statement -> assertThat(statement).containsPattern("\\btb_product\\b"));
        var writes = sql.stream().filter(statement -> !statement.startsWith("select ")).toList();
        assertThat(writes).hasSize(2);
        assertThat(writes.stream().filter(statement -> statement.startsWith("insert into tb_payment "))).hasSize(1);
        assertThat(writes.stream().filter(statement -> statement.startsWith("update tb_order "))).hasSize(1);
    }

    private void assertSettlements(Snapshot before, Snapshot after, List<PaymentRow> accepted) {
        assertThat(after.payments()).containsExactlyInAnyOrderElementsOf(
                Stream.concat(before.payments().stream(), accepted.stream()).toList());
        var acceptedIds = accepted.stream().map(PaymentRow::orderId).toList();
        assertThat(after.previous().data().orders()).isEqualTo(before.previous().data().orders().stream()
                .map(row -> acceptedIds.contains(row.id())
                        ? new OrderRow(row.id(), row.buyerId(), row.createdAt(), "PAID") : row).toList());
        // The probe compares every other table, including opaque account credentials, before reporting table names.
        assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before.previous(), after.previous()))
                .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_order");
        for (var order : List.of(target, other)) {
            var total = after.previous().data().items().stream().filter(row -> row.orderId() == order.id())
                    .map(row -> row.unitPrice().multiply(BigDecimal.valueOf(row.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(total).isEqualByComparingTo(order.total());
        }
    }

    private Account account(String name) { return name.equals("A") ? adminA : adminB; }

    private HttpRequest request(Racer racer) { return request(racer.attempt, racer.actor, racer.order, racer.occurrence); }

    private HttpRequest request(Attempt attempt, Account actor, OrderManifest order, Instant occurredAt) {
        var body = mapper.createObjectNode().put("occurredAt", UTC_MILLISECONDS.format(occurredAt)).toString();
        return jsonRequest("/admin/orders/" + order.id() + "/payments")
                .header("Authorization", "Bearer " + (actor.id() == adminA.id() ? tokenA : tokenB))
                .header(PaymentRegistrationObservationConfig.HEADER, attempt.id())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private void assertIndependentTransactions(Racer first, Racer second) throws SQLException {
        await(second.entered, "second independent service transaction enters root lookup");
        assertThat(first.session.get()).isPositive();
        assertThat(second.session.get()).isPositive().isNotEqualTo(first.session.get());
        assertThat(first.databaseUrl).isEqualTo(second.databaseUrl);
        assertThat(first.threadId).isPositive().isNotEqualTo(second.threadId);
        try (var observer = database.openConnection()) {
            assertPaymentConnection(observer);
            assertThat(first.databaseUrl).isEqualTo(observer.getMetaData().getURL());
            assertThat(session(observer)).isNotIn(first.session.get(), second.session.get());
        }
    }

    private void assertCommitted(Racer racer) {
        assertCompletion(racer, TransactionSynchronization.STATUS_COMMITTED);
        assertThat(racer.beforeCommits.get()).isEqualTo(1);
        assertThat(racer.commits.get()).isEqualTo(1);
    }

    private void assertCompletion(Racer racer, int status) {
        await(racer.completed, "transaction afterCompletion callback");
        assertThat(racer.completion.get()).isEqualTo(status);
        assertThat(racer.lookups.get()).isEqualTo(1);
    }

    private void assertRootAvailable(long id) throws SQLException {
        try (var connection = database.openConnection()) {
            assertPaymentConnection(connection);
            connection.setAutoCommit(false);
            try (var timeout = connection.createStatement()) {
                timeout.setQueryTimeout(2);
                timeout.execute("set lock_timeout 2000");
            }
            try {
                try (var statement = connection.prepareStatement("select id from tb_order where id = ? for update")) {
                    statement.setQueryTimeout(2);
                    statement.setLong(1, id);
                    try (var rows = statement.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getLong(1)).isEqualTo(id);
                        assertThat(rows.next()).isFalse();
                    }
                }
            } finally { connection.rollback(); }
        }
    }

    private int session(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("select session_id()")) {
            statement.setQueryTimeout(2);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                int id = rows.getInt(1);
                assertThat(rows.next()).isFalse();
                return id;
            }
        }
    }

    private void assertBlocked(Racer holder, Racer contender) throws SQLException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        try (var connection = database.openConnection(); var statement = connection.prepareStatement(
                "select session_state, blocker_id, executing_statement from information_schema.sessions where session_id = ?")) {
            assertPaymentConnection(connection);
            statement.setQueryTimeout(2);
            statement.setInt(1, contender.session.get());
            while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
                try (var rows = statement.executeQuery()) {
                    if (rows.next() && "BLOCKED".equals(rows.getString(1)) && rows.getInt(2) == holder.session.get()) {
                        assertThat(rows.getString(3).toLowerCase(Locale.ROOT)).contains("tb_order", "for update");
                        System.out.println("T019_WAIT holder=" + holder.session.get() + " contender=" + contender.session.get());
                        return;
                    }
                }
                // Metadata, not elapsed time, proves the physical wait; this is only bounded polling cadence.
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            }
        }
        throw new AssertionError("Expected the second real H2 transaction to block on the first root holder");
    }

    private static void await(CountDownLatch latch, String label) {
        try {
            if (!latch.await(WAIT.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new AssertionError("T019 coordination timeout: " + label);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("T019 coordination interrupted: " + label, failure);
        }
    }

    private record RequestKey(long actorId, long orderId) { }

    private static final class Racer {
        final Attempt attempt;
        final Account actor;
        final OrderManifest order;
        final Instant occurrence;
        final boolean holdBeforeCommit;
        final CountDownLatch entered = new CountDownLatch(1), acquired = new CountDownLatch(1);
        final CountDownLatch allowReturn = new CountDownLatch(1), beforeCommitReached = new CountDownLatch(1);
        final CountDownLatch allowCommit = new CountDownLatch(1), completed = new CountDownLatch(1);
        final AtomicInteger session = new AtomicInteger(), lookups = new AtomicInteger();
        final AtomicInteger beforeCommits = new AtomicInteger(), commits = new AtomicInteger();
        final AtomicInteger completion = new AtomicInteger(-1);
        volatile long threadId;
        volatile String databaseUrl, loadedStatus;
        Racer(Attempt attempt, Account actor, OrderManifest order, Instant occurrence, boolean holdBeforeCommit) {
            this.attempt = attempt; this.actor = actor; this.order = order;
            this.occurrence = occurrence; this.holdBeforeCommit = holdBeforeCommit;
        }
        void release() { allowReturn.countDown(); allowCommit.countDown(); }
    }

    /** Installs only local observers; the original transaction interceptor, repository and service still execute. */
    private final class RaceObservation implements AutoCloseable {
        private final Map<RequestKey, Racer> racers = new ConcurrentHashMap<>();
        private final ThreadLocal<Racer> current = new ThreadLocal<>();
        private final Advised service = requirePaymentServiceProxy();
        private final Advised repository;
        private final MethodInterceptor scope;
        private final MethodInterceptor acquisition;

        RaceObservation() {
            assertThat(orders).isInstanceOf(Advised.class);
            repository = (Advised) orders;
            scope = invocation -> {
                var args = invocation.getArguments();
                if (!invocation.getMethod().getName().equals("register") || args.length != 3
                        || !(args[0] instanceof Long id) || !(args[2] instanceof Authentication authentication)
                        || !(authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal principal)) {
                    return invocation.proceed();
                }
                var racer = racers.get(new RequestKey(principal.accountId(), id));
                if (racer == null) { return invocation.proceed(); }
                assertThat(current.get()).isNull();
                current.set(racer);
                try { return invocation.proceed(); }
                finally { current.remove(); }
            };
            acquisition = invocation -> {
                var racer = current.get();
                if (racer == null || !invocation.getMethod().getName().equals("findPaymentOrderForUpdate")) {
                    return invocation.proceed();
                }
                assertThat(invocation.getArguments()).containsExactly(racer.order.id());
                assertThat(racer.lookups.incrementAndGet()).isEqualTo(1);
                var connection = DataSourceUtils.getConnection(dataSource);
                try {
                    assertOwnedPaymentTransaction(connection);
                    racer.session.set(session(connection));
                    racer.databaseUrl = connection.getMetaData().getURL();
                    racer.threadId = Thread.currentThread().threadId();
                } finally { DataSourceUtils.releaseConnection(connection, dataSource); }
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {
                        assertThat(readOnly).isFalse();
                        racer.beforeCommits.incrementAndGet();
                        racer.beforeCommitReached.countDown();
                        if (racer.holdBeforeCommit) { await(racer.allowCommit, "allow first commit"); }
                    }
                    @Override public void afterCommit() { racer.commits.incrementAndGet(); }
                    @Override public void afterCompletion(int status) {
                        racer.completion.set(status);
                        racer.completed.countDown();
                    }
                });
                racer.entered.countDown();
                var result = invocation.proceed(); // Real pessimistic acquisition and refresh, not a substitute result.
                assertThat(result).isInstanceOf(Optional.class);
                var locked = ((Optional<?>) result).orElseThrow();
                assertThat(locked).isInstanceOf(Order.class);
                racer.loadedStatus = ((Order) locked).getStatus().name();
                racer.acquired.countDown();
                await(racer.allowReturn, "allow locked root to return to service");
                return result;
            };
            service.addAdvice(0, scope);
            try { repository.addAdvice(0, acquisition); }
            catch (RuntimeException | Error failure) { service.removeAdvice(scope); throw failure; }
        }

        Racer add(Attempt attempt, Account actor, OrderManifest order, Instant occurredAt, boolean holdCommit) {
            var racer = new Racer(attempt, actor, order, occurredAt, holdCommit);
            assertThat(racers.putIfAbsent(new RequestKey(actor.id(), order.id()), racer)).isNull();
            return racer;
        }

        AutoCloseable releaseGatesOnClose() { return () -> racers.values().forEach(Racer::release); }

        @Override public void close() {
            // try-with-resources order: release custom gates, close/join window, then remove observers.
            try { assertThat(repository.removeAdvice(acquisition)).isTrue(); }
            finally { assertThat(service.removeAdvice(scope)).isTrue(); }
        }
    }
}
