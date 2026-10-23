package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig;
import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig.FailureProbe;
import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig.Point;
import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig.Run;
import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig.State;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.dto.PaymentConfirmationDTO;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.entities.Payment;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.OrderRow;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationTestFixture;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import com.devsuperior.dscommerce.repositories.PaymentRepository;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.services.PaymentRegistrationService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T024 authorship. The inherited cases must execute in both concrete HTTP contexts at the approved S4 stage. */
@RootHttpContext
@Import({PaymentFailureInjectionTestConfig.class, PaymentRollbackScenarios.FailureStagesConfig.class})
class PaymentRollbackIntegrationTest extends PaymentRollbackScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

abstract class PaymentRollbackScenarios extends PaymentRegistrationHttpTestSupport {
    private static final DateTimeFormatter UTC_MILLISECONDS = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    @Autowired private FailureProbe failures;
    @Autowired private LocalFaults localFaults;
    private PaymentRegistrationTestFixture fixture;
    private PaymentRegistrationDatabaseProbe database;
    private Snapshot cleanupBaseline;
    private OrderManifest target, canary;
    private String credential;
    private Instant occurrence;

    @BeforeEach
    void prepareOnlyOwnedCommittedHistoryBeforeAnyFailureScope() {
        cleanupBaseline = null;
        localFaults.requireNoOpenScopes();
        fixture = new PaymentRegistrationTestFixture(context);
        database = new PaymentRegistrationDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        cleanupBaseline = database.snapshot();
        var accounts = fixture.prepareAccessAccounts();
        var buyer = fixture.prepareBuyer();
        var productA = fixture.createProduct("T024 current catalog A", new BigDecimal("12.00"));
        var productB = fixture.createProduct("T024 current catalog B", new BigDecimal("99.00"));
        credential = realHttpLogin(accounts.admin().id(), accounts.admin().name(), accounts.admin().password());
        var reference = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        clock.setInstant(reference);
        occurrence = reference.minusSeconds(3600);
        target = fixture.createOrder(buyer, reference.minusSeconds(7200), List.of(
                new ItemInput(productA.id(), 2, "10.00", "20.00"),
                new ItemInput(productB.id(), 3, "7.50", "22.50")), "42.50");
        canary = fixture.createOrder(accounts.client(), reference.minusSeconds(10800),
                List.of(new ItemInput(productA.id(), 2, "10.00", "20.00")), "20.00");
        requirePaymentServiceProxy();
    }

    @AfterEach
    void cleanOnlyFixtureRowsAfterRequestsScopesAndTemporaryConstraints() {
        try {
            localFaults.requireNoOpenScopes();
        } finally {
            try { if (fixture != null) { fixture.close(); } }
            finally {
                credential = null;
                if (cleanupBaseline != null) { PaymentRegistrationDatabaseProbe.assertUnchanged(cleanupBaseline, database.snapshot()); }
            }
        }
    }

    @ParameterizedTest(name = "transactional failure at {0}")
    @EnumSource(value = Point.class, names = {"SQL_BEFORE_FIRST_DML", "AFTER_FIRST_DML", "AFTER_FLUSH",
            "AFTER_BODY_DTO", "BEFORE_COMMIT", "JDBC_COMMIT_BEFORE_DELEGATE"})
    void reachedFailurePointReturnsSafe500AndPreservesAllCommittedRows(Point point) throws Exception {
        var before = database.snapshot();
        try (var run = failures.open(target.id(), point, this::assertReachedPoint)) {
            var response = send(request(run));
            run.requireOutsideTransaction();
            assertInternalFailure(response);
            var state = run.state();
            assertThat(state.hits()).isEqualTo(1);
            assertThat(state.injected()).isTrue();
            assertThat(causes(state.proxyFailure())).contains(run.sentinel());
            assertRollback(state, point == Point.JDBC_COMMIT_BEFORE_DELEGATE);
            assertIndependentFinalView(state, new View("WAITING_PAYMENT", 0, null));
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());
            System.out.println("T024_POINT context=" + expectedContextPath() + " point=" + point
                    + " dml=" + state.dml().size() + " flush=" + state.flushReturns() + " dto=" + state.bodyReturns()
                    + " commitAttempts=" + state.commitAttempts() + " delegated=" + state.delegatedCommits()
                    + " completion=" + state.completion() + " status=500 independentBaselineEqual=true");
        }
    }

    @Test
    void realDatabaseConstraintRejectsPaymentInsertWithoutBeingClassifiedAsRepeat() throws Exception {
        var before = database.snapshot();
        String constraint = "ck_t024_" + UUID.randomUUID().toString().replace("-", "");
        boolean installed = false;
        try {
            executeDdl("alter table tb_payment add constraint " + constraint + " check (order_id <> " + target.id() + ")");
            installed = true;
            try (var run = unarmed()) {
                var response = send(request(run));
                run.requireOutsideTransaction();
                assertInternalFailure(response);
                var state = run.state();
                assertThat(state.failedDml()).hasSize(1).allSatisfy(failure -> {
                    assertThat(failure.sql()).startsWith("insert into tb_payment ");
                    assertThat(failure.sqlState()).isEqualTo("23513");
                });
                assertThat(causes(state.proxyFailure())).anySatisfy(cause -> {
                    assertThat(cause).isInstanceOf(SQLException.class);
                    assertThat(((SQLException) cause).getSQLState()).isEqualTo("23513");
                    assertThat(cause.getMessage()).containsIgnoringCase(constraint);
                });
                assertThat(state.injected()).isFalse();
                assertThat(state.hits()).isZero();
                assertThat(state.flushReturns()).isZero();
                assertThat(state.bodyReturns()).isZero();
                // Do not assume whether another permitted DML ran before the driver rejected the insert.
                assertThat(state.dml()).allSatisfy(dml -> {
                    assertThat(dml.sql()).startsWith("update tb_order ");
                    assertThat(dml.affectedRows()).isPositive();
                });
                assertRollback(state, false);
                assertIndependentFinalView(state, new View("WAITING_PAYMENT", 0, null));
                PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());
                System.out.println("T024_SQL context=" + expectedContextPath() + " driverSqlState=23513 status=500 baselineEqual=true");
            }
        } finally {
            if (installed) { executeDdl("alter table tb_payment drop constraint " + constraint); }
        }
    }

    @Test
    void confirmationConstructorFailureAfterRealFlushRollsBackPaymentAndPaidTogether() throws Exception {
        var before = database.snapshot();
        try (var run = unarmed(); var fault = localFaults.open(LocalStage.MAPPING_CONSTRUCTOR, run, target.id(), occurrence)) {
            var response = send(request(run));
            run.requireOutsideTransaction();
            fault.verifyReached();
            assertInternalFailure(response);
            var state = run.state();
            assertThat(fault.beforeMapping).isEqualTo(new View("PAID", 1, occurrence));
            assertThat(fault.managedPayment).isTrue();
            assertThat(fault.invalidOccurrence).isEqualTo(occurrence.plusNanos(1));
            assertThat(state.flushReturns()).isEqualTo(1);
            assertFullDml(state);
            assertThat(state.bodyReturns()).isZero();
            assertThat(state.bodyDto()).isNull();
            assertThat(state.injected()).isFalse();
            assertThat(causes(state.proxyFailure())).anySatisfy(cause -> {
                assertThat(cause).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid payment confirmation");
                assertThat(List.of(cause.getStackTrace())).anySatisfy(frame -> {
                    assertThat(frame.getClassName()).isEqualTo(PaymentConfirmationDTO.class.getName());
                    assertThat(frame.getMethodName()).isEqualTo("<init>");
                });
            });
            assertRollback(state, false);
            assertIndependentFinalView(state, new View("WAITING_PAYMENT", 0, null));
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());
            System.out.println("T024_MAPPING context=" + expectedContextPath() + " realFlush=1 constructorFailure=true bodyDto=0 status=500 baselineEqual=true");
        }
    }

    @ParameterizedTest(name = "internal failure must remain 500 at {0}")
    @EnumSource(value = LocalStage.class, names = {"ACCOUNT_LOOKUP", "ROOT_LOCK", "PAYMENT_LOOKUP", "PERSIST", "FLUSH_BEFORE_DELEGATE"})
    void internalFailureIsNotAuthenticationNotFoundOrRepeatAndLeavesNoCommittedMutation(LocalStage stage) throws Exception {
        var before = database.snapshot();
        try (var run = unarmed(); var fault = localFaults.open(stage, run, target.id(), occurrence)) {
            var response = send(request(run));
            run.requireOutsideTransaction();
            fault.verifyReached();
            assertInternalFailure(response);
            var state = run.state();
            assertThat(causes(state.proxyFailure())).contains(fault.failure);
            assertThat(state.flushReturns()).isZero();
            assertThat(state.bodyReturns()).isZero();
            assertThat(state.bodyDto()).isNull();
            assertThat(state.injected()).isFalse();
            assertThat(state.failedDml()).isEmpty(); // These are deliberately pre-delegation API faults, not driver errors.
            if (stage != LocalStage.FLUSH_BEFORE_DELEGATE) { assertThat(state.dml()).isEmpty(); }
            assertRollback(state, false);
            assertIndependentFinalView(state, new View("WAITING_PAYMENT", 0, null));
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());
            System.out.println("T024_INTERNAL context=" + expectedContextPath() + " stage=" + stage + " hits=1 status=500 baselineEqual=true");
        }
    }

    @Test
    void positiveControlConfirmsCommitBefore201AndPreservesOtherOrdersAndHistory() throws Exception {
        var before = database.snapshot();
        try (var run = unarmed()) {
            var response = send(request(run));
            run.requireOutsideTransaction();
            assertThat(response.statusCode()).isEqualTo(201);
            assertHeaders(response);
            var expected = mapper.createObjectNode().put("orderId", target.id())
                    .put("occurredAt", UTC_MILLISECONDS.format(occurrence)).put("status", "PAID");
            var actual = mapper.readTree(response.body());
            assertThat(actual).isEqualTo(mapper.readTree(expected.toString()));
            assertThat(actual.get("orderId").isIntegralNumber()).isTrue();
            assertThat(actual.get("orderId").canConvertToLong()).isTrue();
            assertThat(actual.get("orderId").longValue()).isEqualTo(target.id());
            var state = run.state();
            assertThat(state.proxyReturns()).isEqualTo(1);
            assertThat(state.proxyFailures()).isZero();
            assertThat(state.flushReturns()).isEqualTo(1);
            assertThat(state.bodyReturns()).isEqualTo(1);
            assertThat(state.bodyDto()).isEqualTo(new PaymentConfirmationDTO(target.id(), occurrence,
                    OrderStatus.PAID));
            assertThat(state.beforeCommits()).isEqualTo(1);
            assertThat(state.commitAttempts()).isEqualTo(1);
            assertThat(state.delegatedCommits()).isEqualTo(1);
            assertThat(state.successfulCommits()).isEqualTo(1);
            assertThat(state.afterCommits()).isEqualTo(1);
            assertThat(state.rollbackAttempts()).isZero();
            assertThat(state.completion()).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
            assertThat(state.events()).containsSubsequence("FLUSH_RETURNED", "BODY_DTO_RETURNED", "BEFORE_COMMIT",
                    "JDBC_COMMIT_ATTEMPT", "JDBC_COMMIT_DELEGATED", "JDBC_COMMIT_SUCCEEDED", "AFTER_COMMIT",
                    "AFTER_COMPLETION:" + TransactionSynchronization.STATUS_COMMITTED, "PROXY_RETURNED", "PROXY_EXITED");
            assertFullDml(state);
            assertIndependentFinalView(state, new View("PAID", 1, occurrence));
            var after = database.snapshot();
            assertThat(after.payments()).containsExactlyInAnyOrderElementsOf(Stream.concat(
                    before.payments().stream(), Stream.of(new PaymentRow(target.id(), occurrence))).toList());
            assertThat(after.previous().data().orders()).isEqualTo(before.previous().data().orders().stream()
                    .map(row -> row.id() == target.id() ? new OrderRow(row.id(), row.buyerId(), row.createdAt(), "PAID") : row).toList());
            assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before.previous(), after.previous()))
                    .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_order");
            for (var order : List.of(target, canary)) {
                var total = after.previous().data().items().stream().filter(row -> row.orderId() == order.id())
                        .map(row -> row.unitPrice().multiply(BigDecimal.valueOf(row.quantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(total).isEqualByComparingTo(order.total());
            }
            System.out.println("T024_CONTROL context=" + expectedContextPath() + " status=201 driverCommit=1 completion=COMMITTED historicalTotals=42.50,20.00");
        }
    }

    private Run unarmed() {
        return failures.open(target.id(), Point.NONE, (point, connection, state) -> {
            throw new AssertionError("NONE must not invoke a failpoint observer");
        });
    }

    private HttpRequest request(Run run) {
        return jsonRequest("/admin/orders/" + target.id() + "/payments")
                .header("Authorization", "Bearer " + credential)
                .header(PaymentFailureInjectionTestConfig.HEADER, run.headerValue())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.createObjectNode()
                        .put("occurredAt", UTC_MILLISECONDS.format(occurrence)).toString())).build();
    }

    private void assertReachedPoint(Point point, Connection connection, State state) throws SQLException {
        assertThat(state.newTransaction()).isTrue();
        assertThat(state.boundConnection()).isTrue();
        assertThat(state.autoCommitOff()).isTrue();
        assertThat(state.isolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
        assertThat(state.successfulCommits()).isZero();
        assertThat(state.delegatedCommits()).isZero();
        assertThat(state.completion()).isEqualTo(-1);
        var view = readView(connection, target.id()); // Same-transaction evidence; never a committed-state claim.
        if (point == Point.SQL_BEFORE_FIRST_DML) {
            assertThat(state.dml()).isEmpty();
            assertThat(view).isEqualTo(new View("WAITING_PAYMENT", 0, null));
        } else if (point == Point.AFTER_FIRST_DML) {
            assertThat(state.dml()).hasSize(1);
            var first = state.dml().getFirst();
            assertThat(first.affectedRows()).isEqualTo(1);
            if (first.sql().startsWith("insert into tb_payment ")) {
                assertThat(view).isEqualTo(new View("WAITING_PAYMENT", 1, occurrence));
            } else {
                assertThat(first.sql()).startsWith("update tb_order ");
                assertThat(view).isEqualTo(new View("PAID", 0, null));
            }
        } else {
            assertFullDml(state);
            assertThat(view).isEqualTo(new View("PAID", 1, occurrence));
        }
        boolean flushed = !List.of(Point.SQL_BEFORE_FIRST_DML, Point.AFTER_FIRST_DML).contains(point);
        assertThat(state.flushReturns()).isEqualTo(flushed ? 1 : 0);
        boolean dto = List.of(Point.AFTER_BODY_DTO, Point.BEFORE_COMMIT, Point.JDBC_COMMIT_BEFORE_DELEGATE).contains(point);
        assertThat(state.bodyReturns()).isEqualTo(dto ? 1 : 0);
        if (dto) { assertThat(state.bodyDto()).isEqualTo(new PaymentConfirmationDTO(target.id(), occurrence, OrderStatus.PAID)); }
        else { assertThat(state.bodyDto()).isNull(); }
        assertThat(state.beforeCommits()).isEqualTo(List.of(Point.BEFORE_COMMIT, Point.JDBC_COMMIT_BEFORE_DELEGATE).contains(point) ? 1 : 0);
        assertThat(state.commitAttempts()).isEqualTo(point == Point.JDBC_COMMIT_BEFORE_DELEGATE ? 1 : 0);
    }

    private void assertRollback(State state, boolean failedCommitBeforeDelegate) {
        assertThat(state.instrumentationFailure()).isNull();
        assertThat(state.proxyEntries()).isEqualTo(1);
        assertThat(state.bodyEntries()).isEqualTo(1);
        assertThat(state.proxyFailures()).isEqualTo(1);
        assertThat(state.proxyReturns()).isZero();
        assertThat(state.outsideAtEntry()).isTrue();
        assertThat(state.outsideAtExit()).isTrue();
        assertThat(state.commitAttempts()).isEqualTo(failedCommitBeforeDelegate ? 1 : 0);
        assertThat(state.delegatedCommits()).isZero();
        assertThat(state.successfulCommits()).isZero();
        assertThat(state.afterCommits()).isZero();
        assertThat(state.successfulRollbacks()).isLessThanOrEqualTo(state.delegatedRollbacks());
        assertThat(state.delegatedRollbacks()).isEqualTo(state.rollbackAttempts());
        if (failedCommitBeforeDelegate) {
            // Framework UNKNOWN does not turn this known pre-delegation injection into an indeterminate driver commit.
            assertThat(state.completion()).isIn(TransactionSynchronization.STATUS_ROLLED_BACK, TransactionSynchronization.STATUS_UNKNOWN);
        } else {
            assertThat(state.successfulRollbacks()).isPositive();
            assertThat(state.completion()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
    }

    private static void assertFullDml(State state) {
        assertThat(state.failedDml()).isEmpty();
        assertThat(state.dml()).hasSize(2).allSatisfy(dml -> assertThat(dml.affectedRows()).isEqualTo(1));
        assertThat(state.dml().stream().filter(dml -> dml.sql().startsWith("insert into tb_payment "))).hasSize(1);
        assertThat(state.dml().stream().filter(dml -> dml.sql().startsWith("update tb_order "))).hasSize(1);
    }

    private void assertInternalFailure(HttpResponse<byte[]> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(500);
        assertHeaders(response);
        var expected = mapper.createObjectNode().put("status", 500).put("error", "Internal Server Error")
                .put("message", "Payment registration could not be completed")
                .put("path", expectedContextPath() + "/admin/orders/" + target.id() + "/payments");
        assertThat(mapper.readTree(response.body())).isEqualTo(expected);
    }

    private static void assertHeaders(HttpResponse<byte[]> response) {
        assertThat(response.headers().allValues("Location")).isEmpty();
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
    }

    private void assertIndependentFinalView(State state, View expected) throws SQLException {
        assertNoExternalTransaction();
        try (var connection = openIndependentConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(state.databaseUrl());
            try (var query = connection.prepareStatement("select session_id()")) {
                query.setQueryTimeout(2);
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isNotEqualTo(state.sessionId());
                    assertThat(rows.next()).isFalse();
                }
            }
            assertThat(readView(connection, target.id())).isEqualTo(expected);
            connection.setAutoCommit(false);
            try (var timeout = connection.createStatement()) { timeout.execute("set lock_timeout 2000"); }
            try (var lock = connection.prepareStatement("select id from tb_order where id = ? for update")) {
                lock.setQueryTimeout(2); lock.setLong(1, target.id());
                try (var rows = lock.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isEqualTo(target.id());
                    assertThat(rows.next()).isFalse();
                }
            } finally { connection.rollback(); }
        }
    }

    private void executeDdl(String sql) throws SQLException {
        assertNoExternalTransaction();
        try (var connection = openIndependentConnection(); var statement = connection.createStatement()) {
            statement.setQueryTimeout(2); statement.execute(sql);
        }
    }

    private static View readView(Connection connection, long orderId) throws SQLException {
        String status;
        try (var query = connection.prepareStatement("select status from tb_order where id = ?")) {
            query.setQueryTimeout(2); query.setLong(1, orderId);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue(); status = rows.getString(1); assertThat(rows.next()).isFalse();
            }
        }
        int count = 0;
        Instant occurredAt = null;
        try (var query = connection.prepareStatement("select occurred_at from tb_payment where order_id = ?")) {
            query.setQueryTimeout(2); query.setLong(1, orderId);
            try (var rows = query.executeQuery()) {
                while (rows.next()) { count++; occurredAt = rows.getObject(1, OffsetDateTime.class).toInstant(); }
            }
        }
        assertThat(count).isLessThanOrEqualTo(1);
        return new View(status, count, occurredAt);
    }

    private static List<Throwable> causes(Throwable failure) {
        var chain = new ArrayList<Throwable>();
        while (failure != null && chain.size() < 30 && !chain.contains(failure)) { chain.add(failure); failure = failure.getCause(); }
        return chain;
    }

    private record View(String status, int payments, Instant occurredAt) { }
    enum LocalStage { ACCOUNT_LOOKUP, ROOT_LOCK, PAYMENT_LOOKUP, PERSIST, FLUSH_BEFORE_DELEGATE, MAPPING_CONSTRUCTOR }

    /** Explicit import only for the two T024 contexts. The received T023 source remains unchanged. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailureStagesConfig {
        @Bean
        LocalFaults paymentRollbackLocalFaults(PaymentRegistrationService service, UserAccountRepository users,
                                               OrderRepository orders, PaymentRepository payments, DataSource source) {
            return new LocalFaults(service, users, orders, payments, source);
        }
        @Bean
        FilterRegistrationBean<OncePerRequestFilter> paymentRollbackLocalScope(LocalFaults faults) {
            var filter = new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                          FilterChain chain) throws ServletException, IOException {
                    var header = request.getHeader(PaymentFailureInjectionTestConfig.HEADER);
                    var scope = header == null ? null : faults.scopes.get(header);
                    if (scope == null) { chain.doFilter(request, response); return; }
                    if (faults.current.get() != null) { throw scope.broken("Nested local failure scope", null); }
                    faults.current.set(scope);
                    try { chain.doFilter(request, response); }
                    finally { faults.current.remove(); }
                }
            };
            var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
            return registration;
        }
    }

    static final class LocalFaults implements SmartInitializingSingleton, DisposableBean {
        private final PaymentRegistrationService service;
        private final UserAccountRepository users;
        private final OrderRepository orders;
        private final PaymentRepository payments;
        private final DataSource source;
        private final ConcurrentHashMap<String, Scope> scopes = new ConcurrentHashMap<>();
        private final ThreadLocal<Scope> current = new ThreadLocal<>();
        private final List<Installed> installed = new ArrayList<>();
        private Object targetService;
        private EntityManager receivedManager;

        LocalFaults(PaymentRegistrationService service, UserAccountRepository users, OrderRepository orders,
                    PaymentRepository payments, DataSource source) {
            this.service = service; this.users = users; this.orders = orders; this.payments = payments; this.source = source;
        }

        Scope open(LocalStage stage, Run run, long orderId, Instant occurrence) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var scope = new Scope(this, stage, run, orderId, occurrence);
            assertThat(scopes.putIfAbsent(run.headerValue(), scope)).isNull();
            return scope;
        }
        void requireNoOpenScopes() { assertThat(scopes).isEmpty(); }

        private Scope activeBody() {
            var scope = current.get();
            if (scope == null) { return null; }
            var state = scope.run.state();
            // JWT/filter account lookup is deliberately outside this service-only injection scope.
            return state.bodyEntries() == 1 && state.proxyReturns() == 0 && state.proxyFailures() == 0 ? scope : null;
        }

        @Override public void afterSingletonsInstantiated() {
            assertThat(service).isInstanceOf(Advised.class);
            try {
                targetService = ((Advised) service).getTargetSource().getTarget();
                receivedManager = (EntityManager) ReflectionTestUtils.getField(targetService, "entityManager");
                assertThat(receivedManager).isNotNull();
                var wrapper = (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                        new Class<?>[]{EntityManager.class}, (proxy, method, args) -> {
                            var scope = activeBody();
                            boolean flush = method.getName().equals("flush") && method.getParameterCount() == 0;
                            if (scope != null && flush && scope.stage == LocalStage.FLUSH_BEFORE_DELEGATE) { scope.inject(); }
                            var result = invoke(receivedManager, method, args);
                            if (scope != null && flush && scope.stage == LocalStage.MAPPING_CONSTRUCTOR) {
                                try {
                                    assertThat(scope.run.state().flushReturns()).isEqualTo(1);
                                    var connection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(source);
                                    try { scope.beforeMapping = readView(connection, scope.orderId); }
                                    finally { org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection, source); }
                                    assertThat(scope.beforeMapping).isEqualTo(new View("PAID", 1, scope.occurrence));
                                    var payment = receivedManager.find(Payment.class, scope.orderId);
                                    var order = receivedManager.find(Order.class, scope.orderId);
                                    scope.managedPayment = receivedManager.contains(payment);
                                    assertThat(scope.managedPayment).isTrue();
                                    assertThat(receivedManager.contains(order)).isTrue();
                                    assertThat(payment.getOrder()).isSameAs(order);
                                    assertThat(payment.getOccurredAt()).isEqualTo(scope.occurrence);
                                    scope.invalidOccurrence = scope.occurrence.plusNanos(1);
                                    ReflectionTestUtils.setField(payment, "occurredAt", scope.invalidOccurrence);
                                    assertThat(payment.getOccurredAt()).isEqualTo(scope.invalidOccurrence);
                                    scope.reached();
                                    // Return from flush normally; the original production DTO constructor must reject the value.
                                } catch (Throwable failure) { throw scope.broken("Cannot prepare the real constructor failure", failure); }
                            }
                            return result;
                        });
                ReflectionTestUtils.setField(targetService, "entityManager", wrapper);
                add(users, "findEffectiveAccountRolesById", LocalStage.ACCOUNT_LOOKUP);
                add(orders, "findPaymentOrderForUpdate", LocalStage.ROOT_LOCK);
                add(payments, "findById", LocalStage.PAYMENT_LOOKUP);
                add(payments, "insert", LocalStage.PERSIST);
            } catch (Exception | Error failure) {
                remove();
                throw new IllegalStateException("T024 local failure instrumentation could not be installed", failure);
            }
        }

        private void add(Object repository, String method, LocalStage stage) {
            assertThat(repository).isInstanceOf(Advised.class);
            var proxy = (Advised) repository;
            MethodInterceptor advice = call -> {
                var scope = activeBody();
                if (scope != null && scope.stage == stage && call.getMethod().getName().equals(method)) { scope.inject(); }
                return call.proceed();
            };
            proxy.addAdvice(0, advice); installed.add(new Installed(proxy, advice));
        }

        private void remove() {
            try {
                for (var entry : installed.reversed()) { assertThat(entry.proxy().removeAdvice(entry.advice())).isTrue(); }
                installed.clear();
            } finally {
                if (targetService != null && receivedManager != null) {
                    ReflectionTestUtils.setField(targetService, "entityManager", receivedManager);
                }
            }
        }
        @Override public void destroy() { try { requireNoOpenScopes(); } finally { remove(); } }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
        private record Installed(Advised proxy, MethodInterceptor advice) { }
    }

    static final class Scope implements AutoCloseable {
        final LocalFaults owner;
        final LocalStage stage;
        final Run run;
        final long orderId;
        final Instant occurrence;
        final RuntimeException failure;
        int hits;
        Throwable instrumentationFailure;
        View beforeMapping;
        boolean managedPayment;
        Instant invalidOccurrence;

        Scope(LocalFaults owner, LocalStage stage, Run run, long orderId, Instant occurrence) {
            this.owner = owner; this.stage = stage; this.run = run; this.orderId = orderId; this.occurrence = occurrence;
            failure = stage == LocalStage.PERSIST ? new DataIntegrityViolationException("T024 synthetic unexpected write failure")
                    : new DataAccessResourceFailureException("T024 synthetic pre-delegation failure at " + stage);
        }
        void reached() {
            if (++hits != 1) { throw broken("Local point reached more than once; no retries allowed", null); }
        }
        void inject() { reached(); throw failure; }
        IllegalStateException broken(String message, Throwable cause) {
            instrumentationFailure = cause == null ? new AssertionError(message) : cause;
            return new IllegalStateException("T024 instrumentation: " + message, instrumentationFailure);
        }
        void verifyReached() {
            run.awaitFinished();
            assertThat(instrumentationFailure).as("Local instrumentation must succeed before interpreting a 500").isNull();
            assertThat(hits).as("The intended local stage must be reached exactly once").isEqualTo(1);
        }
        @Override public void close() {
            try { verifyReached(); }
            finally { owner.scopes.remove(run.headerValue(), this); }
        }
    }
}
