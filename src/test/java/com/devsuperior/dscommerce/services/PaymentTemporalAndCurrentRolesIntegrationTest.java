package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Attempt;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.IndependentTransaction;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Phase;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.SqlScope;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Window;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.controllers.PaymentRegistrationHttpTestSupport;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.OrderRow;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.AccessAccounts;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationTestFixture;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceUtils;

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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T016 authorship: real ROOT HTTP and physical H2 waits, awaiting the approved S2 execution. */
@RootHttpContext
@Import(PaymentRegistrationObservationConfig.class)
class PaymentTemporalAndCurrentRolesIntegrationTest extends PaymentRegistrationHttpTestSupport {
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final DateTimeFormatter UTC_MILLISECONDS = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    @Autowired private Observation observation;
    @Autowired private OrderRepository orders;
    @Autowired private UserAccountRepository users;
    @PersistenceContext private EntityManager entityManager;
    private PaymentRegistrationTestFixture fixture;
    private PaymentRegistrationDatabaseProbe database;
    private Snapshot cleanupBaseline;
    private AccessAccounts accounts;
    private Account buyer;
    private ProductData product;

    @Override protected String expectedContextPath() { return ""; }

    private enum TemporalCase {
        LOWER_EQUAL(0, 201), BEFORE_CREATION(0, 400), UPPER_EQUAL(0, 201), AFTER_REFERENCE(0, 400),
        NATIVE_FLOOR(1, 201), NATIVE_CEILING(999_999, 400);

        final long extraNanos;
        final int expectedStatus;
        TemporalCase(long extraNanos, int expectedStatus) {
            this.extraNanos = extraNanos;
            this.expectedStatus = expectedStatus;
        }
    }

    @BeforeEach
    void prepareOnlyOwnedCommittedAccountsAndCatalog() {
        cleanupBaseline = null;
        fixture = new PaymentRegistrationTestFixture(context);
        database = new PaymentRegistrationDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        cleanupBaseline = database.snapshot();
        accounts = fixture.prepareAccessAccounts();
        buyer = fixture.prepareBuyer();
        product = fixture.createProduct("T016 current catalog twelve", new BigDecimal("12.00"));
        requirePaymentServiceProxy();
    }

    @AfterEach
    void cleanOnlyFixtureRowsAfterEveryWindowThreadAndConnectionHasClosed() {
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
        }
    }

    @ParameterizedTest(name = "fixed native reference across physical wait: {0}")
    @EnumSource(TemporalCase.class)
    void initialReferenceAndInclusiveWindowSurviveARealRootLockWait(TemporalCase temporalCase) throws Exception {
        exercise(accounts.admin(), temporalCase, false);
    }

    @ParameterizedTest(name = "stale managed ADMIN roles while waiting: initial={0}")
    @ValueSource(strings = {"ADMIN", "BOTH"})
    void scalarGuardAfterTheWaitRejectsRevocationDespiteInitializedManagedRoles(String profile) throws Exception {
        exercise(profile.equals("ADMIN") ? accounts.admin() : accounts.both(), TemporalCase.UPPER_EQUAL, true);
    }

    private void exercise(Account actor, TemporalCase temporalCase, boolean revokeAdmin) throws Exception {
        var credential = realHttpLogin(actor.id(), actor.name(), actor.password());
        var millisecondBase = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusMillis(123);
        var reference = millisecondBase.plusNanos(temporalCase.extraNanos);
        clock.setInstant(reference);
        var createdAt = millisecondBase.minusSeconds(3600);
        var occurrence = switch (temporalCase) {
            case LOWER_EQUAL -> createdAt;
            case BEFORE_CREATION -> createdAt.minusMillis(1);
            case UPPER_EQUAL -> reference;
            case AFTER_REFERENCE, NATIVE_CEILING -> millisecondBase.plusMillis(1);
            case NATIVE_FLOOR -> millisecondBase;
        };
        assertThat(occurrence.getNano() % 1_000_000).isZero();
        var target = fixture.createOrder(buyer, createdAt,
                List.of(new ItemInput(product.id(), 2, "10.00", "20.00")), "20.00");
        fixture.createOrder(accounts.client(), createdAt.minusSeconds(60),
                List.of(new ItemInput(product.id(), 1, "8.00", "8.00")), "8.00");
        var before = database.snapshot();
        var state = new AcquisitionState();
        assertThat(orders).isInstanceOf(Advised.class);
        var proxy = (Advised) orders;
        var advice = acquisitionObservation(target.id(), actor, revokeAdmin, state);
        proxy.addAdvice(0, advice);
        try {
            // Closing the holder before the window releases locks before joining workers on failure too.
            try (var window = observation.openWindow(WAIT);
                 var holder = window.independentTransaction(database)) {
                int holderSession = session(holder);
                lockOwnedRoot(holder, target.id());
                var attempt = window.newRequest();
                try (var referenceGate = attempt.pauseAt(Phase.REFERENCE_READ);
                     var lockSqlGate = attempt.pauseAt(Phase.ORDER_LOCK_SQL)) {
                    var request = request(attempt, target.id(), credential, occurrence);
                    var pending = window.submit(() -> send(request));
                    referenceGate.awaitReached();
                    assertThat(attempt.reference()).isEqualTo(reference);
                    assertThat(attempt.clockCalls()).isEqualTo(1);
                    assertThat(state.calls.get()).isZero();
                    assertThat(attempt.sql().stream().filter(event -> event.scope() == SqlScope.SERVICE_BODY)).isEmpty();
                    // The clock value was read and is about to be returned; do not claim the local assignment yet.
                    referenceGate.release();
                    lockSqlGate.awaitReached();
                    assertThat(state.calls.get()).isEqualTo(1);
                    assertThat(state.session.get()).isPositive().isNotEqualTo(holderSession);
                    lockSqlGate.release(); // Preparation alone is not a JDBC wait; require H2 metadata below.
                    assertBlocked(holderSession, state.session.get());
                    assertThat(pending.isDone()).isFalse();
                    assertThat(state.returned.get()).isFalse();

                    clock.advance(Duration.ofMinutes(5));
                    assertThat(clock.instant()).isEqualTo(reference.plusSeconds(300));
                    assertThat(attempt.reference()).isEqualTo(reference);
                    assertThat(attempt.clockCalls()).isEqualTo(1);
                    Snapshot rejectionBaseline = before;
                    if (revokeAdmin) {
                        assertThat(occurrence).isEqualTo(reference); // Time is valid; this isolates permission.
                        assertThat(state.preloaded.get()).isTrue();
                        revokeOnlyOwnedAdmin(window, actor);
                        rejectionBaseline = database.snapshot();
                        assertOnlyExternalRoleRevocation(before, rejectionBaseline, actor);
                    } else {
                        PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());
                    }
                    // The holder has changed no rows. Rollback releases only its acquired root lock.
                    assertThat(pending.isDone()).isFalse();
                    holder.rollback();
                    var response = window.awaitResult(pending);
                    attempt.awaitFinished();
                    attempt.assertOwnedTransactionObserved();
                    assertThat(attempt.reference()).isEqualTo(reference);
                    assertThat(state.returned.get()).isTrue();
                    assertScalarGuardsAndRootSql(attempt);
                    int expectedStatus = revokeAdmin ? 403 : temporalCase.expectedStatus;
                    assertThat(response.statusCode()).isEqualTo(expectedStatus);
                    if (expectedStatus == 201) {
                        assertSuccess(response, target, occurrence, before, attempt);
                    } else {
                        assertError(response, target.id(), expectedStatus);
                        assertThat(attempt.proxyFailed()).isTrue();
                        assertThat(attempt.proxyReturned()).isFalse();
                        assertReadOnlyRejectionSql(attempt, revokeAdmin);
                        PaymentRegistrationDatabaseProbe.assertUnchanged(rejectionBaseline, database.snapshot());
                    }
                    if (revokeAdmin) {
                        assertThat(state.managedAfterLock.get()).isTrue();
                        assertThat(state.rolesAfterLock.get()).isEqualTo(actor.roles()).contains(UserRole.ADMIN);
                    }
                    System.out.println("T016_RESULT case=" + temporalCase + " revoked=" + revokeAdmin
                            + " status=" + response.statusCode() + " clockCalls=" + attempt.clockCalls()
                            + " nativeReference=" + attempt.reference() + " independentSnapshot=asserted");
                }
            }
        } finally {
            assertThat(proxy.removeAdvice(advice)).as("Remove only the local acquisition observer after workers close").isTrue();
        }
    }

    private MethodInterceptor acquisitionObservation(long targetId, Account actor, boolean preloadRoles,
                                                      AcquisitionState state) {
        return invocation -> {
            if (!invocation.getMethod().getName().equals("findPaymentOrderForUpdate")
                    || invocation.getArguments().length != 1
                    || !Long.valueOf(targetId).equals(invocation.getArguments()[0])) {
                return invocation.proceed();
            }
            assertThat(state.calls.incrementAndGet()).isEqualTo(1);
            var connection = DataSourceUtils.getConnection(dataSource);
            try {
                assertOwnedPaymentTransaction(connection);
                state.session.set(session(connection));
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
            UserAccount cached = null;
            if (preloadRoles) {
                cached = users.findByIdWithRoles(actor.id()).orElseThrow();
                assertThat(entityManager.contains(cached)).isTrue();
                assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(cached, "roles")).isTrue();
                assertThat(cached.getRoles()).containsExactlyInAnyOrderElementsOf(actor.roles());
                state.preloaded.set(true);
            }
            var result = invocation.proceed(); // Real find(PESSIMISTIC_WRITE) and refresh, no substituted result.
            if (preloadRoles) {
                state.managedAfterLock.set(entityManager.contains(cached));
                state.rolesAfterLock.set(Set.copyOf(cached.getRoles()));
                assertThat(state.managedAfterLock.get()).isTrue();
                assertThat(state.rolesAfterLock.get()).isEqualTo(actor.roles()).contains(UserRole.ADMIN);
            }
            state.returned.set(true);
            return result;
        };
    }

    private void lockOwnedRoot(IndependentTransaction holder, long targetId) throws SQLException {
        try (var statement = holder.prepare("select id from tb_order where id = ? for update")) {
            statement.setLong(1, targetId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(targetId);
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private int session(IndependentTransaction transaction) throws SQLException {
        try (var statement = transaction.prepare("select session_id()"); var rows = statement.executeQuery()) {
            assertThat(rows.next()).isTrue();
            int session = rows.getInt(1);
            assertThat(session).isPositive();
            assertThat(rows.next()).isFalse();
            return session;
        }
    }

    private int session(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("select session_id()")) {
            statement.setQueryTimeout(2);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                int session = rows.getInt(1);
                assertThat(session).isPositive();
                assertThat(rows.next()).isFalse();
                return session;
            }
        }
    }

    private void assertBlocked(int holderSession, int contenderSession) throws SQLException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        String lastState = "absent";
        Integer lastBlocker = null;
        try (var connection = database.openConnection(); var statement = connection.prepareStatement(
                "select session_state, blocker_id, executing_statement from information_schema.sessions where session_id = ?")) {
            assertPaymentConnection(connection);
            statement.setQueryTimeout(2);
            statement.setInt(1, contenderSession);
            while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
                try (var rows = statement.executeQuery()) {
                    if (rows.next()) {
                        lastState = rows.getString(1);
                        int blocker = rows.getInt(2);
                        lastBlocker = rows.wasNull() ? null : blocker;
                        if ("BLOCKED".equals(lastState) && Integer.valueOf(holderSession).equals(lastBlocker)) {
                            assertThat(rows.getString(3).toLowerCase(Locale.ROOT)).contains("tb_order", "for update");
                            System.out.println("T016_WAIT holder=" + holderSession + " contender=" + contenderSession
                                    + " state=BLOCKED blocker=" + lastBlocker);
                            return;
                        }
                    }
                }
                // Same bounded metadata-polling technique as the approved product concurrency harness.
                // The five-millisecond interval is only polling cadence; elapsed time never proves a lock wait.
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            }
        }
        throw new AssertionError("Expected physical root wait: state=" + lastState + ", blocker=" + lastBlocker);
    }

    private void revokeOnlyOwnedAdmin(Window window, Account actor) throws SQLException {
        assertNoExternalTransaction();
        assertThat(List.of(accounts.admin().id(), accounts.both().id())).contains(actor.id());
        try (var writer = window.independentTransaction(database)) {
            try (var statement = writer.prepare("delete from tb_user_account_role where user_account_id = ? and role = ?")) {
                statement.setLong(1, actor.id());
                statement.setString(2, "ADMIN");
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            writer.commit();
        }
    }

    private void assertOnlyExternalRoleRevocation(Snapshot before, Snapshot changed, Account actor) {
        assertThat(changed.payments()).isEqualTo(before.payments());
        assertThat(changed.previous().data()).isEqualTo(before.previous().data());
        assertThat(changed.previous().accountIds()).isEqualTo(before.previous().accountIds());
        var expectedRoles = before.previous().roles().stream()
                .filter(row -> row.accountId() != actor.id() || row.role() != UserRole.ADMIN).toList();
        assertThat(changed.previous().roles()).isEqualTo(expectedRoles);
        assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before.previous(), changed.previous()))
                .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_user_account_role");
    }

    private void assertScalarGuardsAndRootSql(Attempt attempt) {
        var sql = bodyStatements(attempt);
        assertThat(sql).noneSatisfy(statement -> assertThat(statement).containsPattern("\\btb_product\\b"));
        var locks = IntStream.range(0, sql.size()).filter(index -> sql.get(index).contains("for update")).boxed().toList();
        assertThat(locks).hasSize(2);
        assertThat(locks).allSatisfy(index -> assertThat(sql.get(index)).contains("from tb_order ")
                .doesNotContain(" join ", "tb_order_item", "tb_user_account", "tb_payment"));
        var guards = IntStream.range(0, sql.size()).filter(index -> sql.get(index).contains("tb_user_account_role")
                && !sql.get(index).contains("password_hash")).boxed().toList();
        assertThat(guards).hasSize(2);
        assertThat(guards.getFirst()).isLessThan(locks.getFirst());
        assertThat(guards.getLast()).isGreaterThan(locks.getLast());
    }

    private void assertReadOnlyRejectionSql(Attempt attempt, boolean revoked) {
        assertThat(attempt.sql()).allSatisfy(event -> assertThat(event.sql().strip().toLowerCase(Locale.ROOT)).startsWith("select "));
        var sql = bodyStatements(attempt);
        assertThat(sql).noneSatisfy(statement -> assertThat(statement).containsPattern("\\btb_order_item\\b"));
        if (revoked) {
            assertThat(sql).noneSatisfy(statement -> assertThat(statement).containsPattern("\\btb_payment\\b"));
        }
    }

    private List<String> bodyStatements(Attempt attempt) {
        var events = attempt.sql().stream().filter(event -> event.scope() == SqlScope.SERVICE_BODY).toList();
        assertThat(events).isNotEmpty().allSatisfy(event -> assertThat(event.transactionActive()).isTrue());
        return events.stream().map(event -> event.sql().replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT)).toList();
    }

    private void assertSuccess(HttpResponse<byte[]> response, OrderManifest target, Instant occurrence,
                               Snapshot before, Attempt attempt) throws Exception {
        assertThat(response.headers().allValues("Location")).isEmpty();
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        var expected = mapper.createObjectNode().put("orderId", target.id())
                .put("occurredAt", UTC_MILLISECONDS.format(occurrence)).put("status", "PAID");
        var actual = mapper.readTree(response.body());
        // Parse both trees alike: integer node width must not change exact JSON equality.
        assertThat(actual).isEqualTo(mapper.readTree(expected.toString()));
        assertThat(actual.get("orderId").isIntegralNumber()).isTrue();
        assertThat(attempt.proxyReturned()).isTrue();
        assertThat(attempt.proxyFailed()).isFalse();
        var after = database.snapshot();
        assertThat(after.payments()).containsExactlyInAnyOrderElementsOf(Stream.concat(before.payments().stream(),
                Stream.of(new PaymentRow(target.id(), occurrence))).toList());
        var expectedOrders = before.previous().data().orders().stream().map(row -> row.id() == target.id()
                ? new OrderRow(row.id(), row.buyerId(), row.createdAt(), "PAID") : row).toList();
        assertThat(after.previous().data().orders()).isEqualTo(expectedOrders);
        assertThat(after.previous().data().items()).isEqualTo(before.previous().data().items());
        assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before.previous(), after.previous()))
                .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_order");
        var historicalTotal = after.previous().data().items().stream().filter(row -> row.orderId() == target.id())
                .map(row -> row.unitPrice().multiply(BigDecimal.valueOf(row.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(historicalTotal).isEqualByComparingTo("20.00");
    }

    private void assertError(HttpResponse<byte[]> response, long orderId, int status) throws Exception {
        assertThat(response.headers().allValues("Location")).isEmpty();
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        var expected = mapper.createObjectNode().put("status", status)
                .put("error", HttpStatus.valueOf(status).getReasonPhrase())
                .put("message", status == 403 ? "Payment registration not permitted" : "Invalid payment data")
                .put("path", "/admin/orders/" + orderId + "/payments");
        assertThat(mapper.readTree(response.body())).isEqualTo(expected);
    }

    private HttpRequest request(Attempt attempt, long orderId, String credential, Instant occurrence) {
        var body = mapper.createObjectNode().put("occurredAt", UTC_MILLISECONDS.format(occurrence)).toString();
        return jsonRequest("/admin/orders/" + orderId + "/payments").header("Authorization", "Bearer " + credential)
                .header(PaymentRegistrationObservationConfig.HEADER, attempt.id())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private static final class AcquisitionState {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger session = new AtomicInteger();
        final AtomicBoolean preloaded = new AtomicBoolean();
        final AtomicBoolean returned = new AtomicBoolean();
        final AtomicBoolean managedAfterLock = new AtomicBoolean();
        final AtomicReference<Set<UserRole>> rolesAfterLock = new AtomicReference<>();
    }
}
