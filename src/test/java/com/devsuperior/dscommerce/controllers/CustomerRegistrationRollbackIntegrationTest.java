package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig.Failures;
import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig.Fault;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Event;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Point;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.SqlKind;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.dto.CustomerRegistrationConfirmationDTO;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.services.CustomerRegistrationTransaction;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static com.devsuperior.dscommerce.config.CustomerRegistrationTransactionOracle.audit;
import static com.devsuperior.dscommerce.config.CustomerRegistrationTransactionOracle.completed;
import static com.devsuperior.dscommerce.config.CustomerRegistrationTransactionOracle.workerEvents;
import static org.assertj.core.api.Assertions.assertThat;

@RootHttpContext
@Import({CustomerRegistrationObservationConfig.class, CustomerRegistrationFailureInjectionConfig.class})
class CustomerRegistrationRollbackIntegrationTest extends CustomerRegistrationRollbackScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T073 authorship only. T076 owns compilation, discovery and the first joint US4 execution. */
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class CustomerRegistrationRollbackScenarios extends CustomerRegistrationHttpTestSupport {
    private static final String NAME = "Atomic.client";
    private static final String FULL_NAME = "Atomic Person";
    private static final String EMAIL = "Atomic.Email@Example.test";
    private static final String EMAIL_KEY = "atomic.email@example.test";
    private static final String PHONE = "+5511999999999";
    private static final String PASSWORD = "Atomic-password-009";
    @Autowired private Observation observation;
    @Autowired private Failures failures;
    @Autowired private CustomerRegistrationTransaction worker;
    private final Map<String, Witness> witnesses = new ConcurrentHashMap<>();
    private final Set<Long> createdAccounts = new LinkedHashSet<>();
    private Advised workerProxy;
    private MethodInterceptor dtoObserver;
    private boolean safeToClean = true;

    /** Appended inside the existing transaction/observation advices; returns the actual DTO unchanged. */
    @BeforeAll
    void observeTheActualWorkerResultWithoutReplacingItsBodyOrTransaction() {
        assertThat(observation.workerInstalled()).as("original worker observation installed").isTrue();
        assertThat(worker instanceof Advised).as("separate original transactional worker proxy").isTrue();
        workerProxy = (Advised) worker;
        assertThat(workerProxy.isFrozen()).isFalse();
        assertThat(Arrays.stream(workerProxy.getAdvisors())
                .filter(advisor -> advisor.getAdvice() instanceof TransactionInterceptor).count())
                .as("preserve the one original Spring transaction interceptor").isEqualTo(1);
        dtoObserver = invocation -> {
            Object result = invocation.proceed();
            if ("register".equals(invocation.getMethod().getName())
                    && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request) {
                String id = request.getRequest().getHeader(CustomerRegistrationObservationConfig.HEADER);
                var witness = id == null ? null : witnesses.get(id);
                if (witness != null) {
                    witness.bodyReturned = true;
                    witness.dtoInsideTransaction = TransactionSynchronizationManager.isActualTransactionActive();
                    witness.dtoValid = result instanceof CustomerRegistrationConfirmationDTO dto
                            && dto.id() != null && dto.id() > 0 && NAME.equals(dto.name());
                    if (result instanceof CustomerRegistrationConfirmationDTO dto && dto.id() != null) {
                        witness.dtoId = dto.id();
                    }
                }
            }
            return result;
        };
        workerProxy.addAdvice(dtoObserver);
    }

    @BeforeEach
    void prepareCommittedLegacyAndFinancialGuards() {
        if (!safeToClean) {
            fixture = null;
            throw new AssertionError("Request termination is unproved; rollback fixture setup is withheld");
        }
        fixture.prepareScenario();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(Boundary.class)
    void knownPreCommitFailuresRollBackEveryReachedWriteWithoutChangingPreviousData(Boundary boundary) {
        var run = request(boundary);
        assertError(run.response(), 500, "Internal Server Error", "Customer registration could not be completed");
        run.fault().assertReached();
        assertThat(run.fault().hits()).as("one deliberate fault, no retry").isEqualTo(1);
        assertBoundaryState(run, boundary.accounts, boundary.clients, boundary.dtoReturned);
        assertThat(run.events().stream().filter(event -> event.point() == boundary.point).count())
                .as("the chosen failure boundary was actually reached once").isEqualTo(1);
        assertReachedWrites(run.events(), boundary);
        assertThat(run.events().stream().noneMatch(event -> Set.of(Point.JDBC_COMMIT_DELEGATED,
                Point.JDBC_COMMIT_RETURNED, Point.JDBC_COMMIT_FAILED, Point.AFTER_COMMIT, Point.PROXY_RETURNED)
                .contains(event.point()))).as("original commit was never delegated; no success is inferred").isTrue();
        assertOne(run.events(), Point.JDBC_ROLLBACK_BEFORE_DELEGATE);
        assertOne(run.events(), Point.JDBC_ROLLBACK_RETURNED);
        assertOne(run.events(), Point.PROXY_FAILED);
        // The pre-JDBC sentinel is wrapped as TransactionSystemException: Spring reports UNKNOWN,
        // independently of the returned physical rollback and unchanged external snapshot required below.
        assertCompletion(run.events(), boundary == Boundary.BEFORE_JDBC_COMMIT
                ? TransactionSynchronization.STATUS_UNKNOWN : TransactionSynchronization.STATUS_ROLLED_BACK);
        assertOrder(run.events(), boundary.point, Point.JDBC_ROLLBACK_BEFORE_DELEGATE,
                Point.JDBC_ROLLBACK_RETURNED, Point.AFTER_COMPLETION, Point.PROXY_FAILED);
        long proxyFailed = first(run.events(), Point.PROXY_FAILED).sequence();
        assertThat(run.events().stream().noneMatch(event -> event.sequence() > proxyFailed
                && event.point() == Point.SQL_BEFORE)).as("no recovery lookup after the failed proxy").isTrue();
        assertUnchanged(run.before(), run.after());
        completed(getClass().getSimpleName() + "-" + boundary.name());
    }

    @Test
    void unarmedControlCommitsAccountAndClientThroughTheOriginalJdbcConnection() throws SQLException {
        var run = request(null);
        assertThat(run.response().statusCode()).isEqualTo(201);
        assertJsonAndNoAuthentication(run.response());
        var body = jsonBody(run.response());
        assertThat(body.isObject() && body.size() == 2 && body.path("id").isIntegralNumber()
                && body.path("id").canConvertToLong() && body.path("id").longValue() > 0
                && NAME.equals(body.path("name").textValue())).as("only committed new id/name in confirmation").isTrue();
        long id = body.path("id").longValue();
        assertBoundaryState(run, 1, 1, true);
        assertThat(run.witness().dtoId == id).as("actual internally constructed DTO matches the response").isTrue();
        for (Point point : List.of(Point.ACCOUNT_INSERT_BEFORE, Point.ACCOUNT_INSERT_RETURNED,
                Point.ROLE_INSERT_BEFORE, Point.ROLE_INSERT_RETURNED, Point.WORKER_RETURNED,
                Point.BEFORE_COMMIT, Point.JDBC_COMMIT_BEFORE_DELEGATE, Point.JDBC_COMMIT_DELEGATED,
                Point.JDBC_COMMIT_RETURNED, Point.AFTER_COMMIT, Point.PROXY_RETURNED)) { assertOne(run.events(), point); }
        assertThat(run.events().stream().anyMatch(event -> event.point() == Point.FLUSH_BEFORE)
                && run.events().stream().anyMatch(event -> event.point() == Point.FLUSH_RETURNED))
                .as("real explicit flush observed; possible later flushes are not hidden").isTrue();
        assertThat(run.events().stream().noneMatch(event -> Set.of(Point.SQL_FAILED, Point.PROXY_FAILED,
                Point.JDBC_COMMIT_FAILED, Point.JDBC_ROLLBACK_BEFORE_DELEGATE).contains(event.point())))
                .as("unarmed control has no injected or operational failure").isTrue();
        assertCompletion(run.events(), TransactionSynchronization.STATUS_COMMITTED);
        assertOrder(run.events(), Point.ACCOUNT_INSERT_RETURNED, Point.FLUSH_BEFORE, Point.ROLE_INSERT_RETURNED,
                Point.FLUSH_RETURNED, Point.WORKER_RETURNED, Point.BEFORE_COMMIT, Point.JDBC_COMMIT_DELEGATED,
                Point.JDBC_COMMIT_RETURNED, Point.AFTER_COMMIT, Point.AFTER_COMPLETION, Point.PROXY_RETURNED);
        assertOnlyOneNewAccountAndClient(run.before(), run.after(), id);
        assertCommittedData(id);
        assertUnchanged(run.after(), database.snapshot());
        completed(getClass().getSimpleName() + "-CONTROL");
    }

    private Run request(Boundary boundary) {
        assertNoCallerTransaction();
        var before = database.snapshot();
        var attempt = failures.openControl();
        var witness = new Witness();
        witnesses.put(attempt.headerValue(), witness);
        boolean submitted = false;
        HttpResponse<byte[]> response;
        Fault fault;
        safeToClean = false;
        try {
            Point capture = boundary == null ? Point.BEFORE_COMMIT : boundary.point;
            attempt.onWorker(capture, () -> {
                witness.hits.incrementAndGet();
                witness.ownState = readOwnUncommittedState();
                // DriverManager-backed probe reads independently, even from this transaction's thread.
                witness.externalBeforeCompletion = database.snapshot();
            });
            // Register AFTER the state callback: observe the real reached state before throwing the sentinel.
            fault = boundary == null ? null : failures.failOnceInWorker(attempt, boundary.point);
            var body = mapper.createObjectNode().put("name", NAME).put("fullName", "\u00A0 " + FULL_NAME + " \u3000")
                    .put("email", "\t" + EMAIL + " ").put("phone", PHONE).put("birthDate", "2000-02-29")
                    .put("password", PASSWORD);
            var request = jsonRequest("/customers").header(CustomerRegistrationObservationConfig.HEADER, attempt.headerValue())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(body))).build();
            submitted = true;
            response = send(request);
        } finally {
            safeToClean = !submitted;
            if (submitted) {
                try { attempt.awaitFinished(); safeToClean = true; }
                catch (AssertionError incomplete) { safeToClean = false; }
            }
            if (safeToClean) {
                try { rememberNewAccounts(before, database.snapshot()); }
                finally { attempt.close(); witnesses.remove(attempt.headerValue()); }
            } else {
                fixture = null;
                throw new AssertionError("Server attempt termination is unproved; rollback fixture cleanup is withheld");
            }
        }
        assertNoCallerTransaction();
        audit(getClass().getSimpleName() + "-" + (boundary == null ? "CONTROL" : boundary.name()), attempt.events());
        return new Run(response, before, database.snapshot(), workerEvents(attempt.events()), witness, fault);
    }

    private OwnState readOwnUncommittedState() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                .as("capture inside the original write transaction").isTrue();
        var connection = DataSourceUtils.getConnection(dataSource);
        try {
            assertThat(DataSourceUtils.isConnectionTransactional(connection, dataSource)
                    && !connection.getAutoCommit() && connection.getTransactionIsolation() == Connection.TRANSACTION_READ_COMMITTED)
                    .as("read only through the current physical transaction connection").isTrue();
            int session;
            int accounts;
            int roles;
            int clients;
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(2);
                try (var rows = statement.executeQuery("select session_id()")) {
                    assertThat(rows.next()).isTrue();
                    session = rows.getInt(1);
                }
            }
            try (var statement = connection.prepareStatement("select count(*) from tb_user_account where name = ?")) {
                statement.setQueryTimeout(2);
                statement.setString(1, NAME);
                try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); accounts = rows.getInt(1); }
            }
            try (var statement = connection.prepareStatement(
                    "select count(*), coalesce(sum(case when r.role = 'CLIENT' then 1 else 0 end), 0) "
                            + "from tb_user_account_role r join tb_user_account a on a.id = r.user_account_id where a.name = ?")) {
                statement.setQueryTimeout(2);
                statement.setString(1, NAME);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    roles = rows.getInt(1);
                    clients = rows.getInt(2);
                }
            }
            return new OwnState(session, accounts, roles, clients);
        } catch (SQLException failure) {
            throw new AssertionError("Uncommitted state observation failed; no rollback criterion is inferred");
        } finally { DataSourceUtils.releaseConnection(connection, dataSource); }
    }

    private static void assertBoundaryState(Run run, int accounts, int clients, boolean dtoReturned) {
        assertThat(run.witness().hits.get()).as("one state capture at the chosen boundary").isEqualTo(1);
        var own = run.witness().ownState;
        assertThat(own != null && own.accounts() == accounts && own.roles() == clients && own.clients() == clients)
                .as("actual uncommitted account/CLIENT rows at the controlled boundary").isTrue();
        assertUnchanged(run.before(), run.witness().externalBeforeCompletion);
        assertThat(run.witness().bodyReturned == dtoReturned).as("actual worker body return was or was not reached").isTrue();
        if (dtoReturned) {
            assertThat(run.witness().dtoValid && run.witness().dtoInsideTransaction)
                    .as("real constructed DTO observed inside the original transaction").isTrue();
        }
        assertOne(run.events(), Point.PROXY_ENTRY);
        assertOne(run.events(), Point.WORKER_ENTRY);
        var worker = first(run.events(), Point.WORKER_ENTRY);
        var proxy = first(run.events(), Point.PROXY_ENTRY);
        assertThat(!proxy.transactionActive() && worker.transactionActive() && !worker.readOnly() && !worker.autoCommit()
                && worker.isolation() == Connection.TRANSACTION_READ_COMMITTED && worker.transactionId() > 0
                && worker.connectionId() > 0 && worker.sessionId() == own.session())
                .as("separate proxy owns the real physical write transaction used by the state probe").isTrue();
        assertThat(run.events().stream().filter(event -> event.sequence() >= worker.sequence())
                .allMatch(event -> event.threadId() == worker.threadId() && event.connectionId() == worker.connectionId()
                        && event.sessionId() == worker.sessionId() && event.transactionId() == worker.transactionId()))
                .as("worker transaction and JDBC session stay bound through completion").isTrue();
        assertThat(run.events().stream().noneMatch(event -> event.sqlKind() == SqlKind.UPDATE || event.sqlKind() == SqlKind.DELETE))
                .as("no overwrite or compensating DML").isTrue();
        for (Point point : List.of(Point.ACCOUNT_INSERT_BEFORE, Point.ROLE_INSERT_BEFORE)) {
            assertThat(run.events().stream().filter(event -> event.point() == point).count()).as("no repeated %s", point)
                    .isLessThanOrEqualTo(1);
        }
    }

    private static void assertReachedWrites(List<Event> events, Boundary boundary) {
        assertThat(events.stream().filter(event -> event.point() == Point.ACCOUNT_INSERT_RETURNED).count())
                .as("successful original account inserts before failure").isEqualTo(boundary.accounts);
        assertThat(events.stream().filter(event -> event.point() == Point.ROLE_INSERT_RETURNED).count())
                .as("successful original CLIENT inserts before failure").isEqualTo(boundary.clients);
        assertThat(events.stream().filter(event -> event.point() == Point.WORKER_RETURNED).count())
                .as("constructed DTO is still not a committed success").isEqualTo(boundary.dtoReturned ? 1 : 0);
    }

    private static void assertOne(List<Event> events, Point point) {
        assertThat(events.stream().filter(event -> event.point() == point).count()).as("one %s", point).isEqualTo(1);
    }

    private static Event first(List<Event> events, Point point) {
        return events.stream().filter(event -> event.point() == point).findFirst().orElseThrow();
    }

    private static void assertOrder(List<Event> events, Point... points) {
        long previous = 0;
        for (var point : points) {
            long sequence = first(events, point).sequence();
            assertThat(sequence).as("order of %s", point).isGreaterThan(previous);
            previous = sequence;
        }
    }

    private static void assertCompletion(List<Event> events, int status) {
        assertOne(events, Point.AFTER_COMPLETION);
        assertThat(first(events, Point.AFTER_COMPLETION).completion()).as("observed Spring completion").isEqualTo(status);
    }

    private void assertError(HttpResponse<byte[]> response, int status, String error, String message) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertJsonAndNoAuthentication(response);
        var expected = mapper.createObjectNode().put("status", status).put("error", error).put("message", message)
                .put("path", expectedContextPath() + "/customers");
        assertThat(expected.equals(jsonBody(response))).as("exact safe error without internal cause, identity or secret").isTrue();
    }

    private static void assertJsonAndNoAuthentication(HttpResponse<byte[]> response) {
        boolean json;
        try { json = MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""))
                .isCompatibleWith(MediaType.APPLICATION_JSON); }
        catch (IllegalArgumentException invalidHeader) { json = false; }
        assertThat(json).isTrue();
        for (String header : List.of("Set-Cookie", "Authorization", "Location")) {
            assertThat(response.headers().allValues(header).isEmpty()).as("no %s header", header).isTrue();
        }
    }

    private void assertCommittedData(long id) throws SQLException {
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select name, full_name, email, email_key, phone, birth_date, password_hash from tb_user_account where id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(NAME.equals(rows.getString(1)) && FULL_NAME.equals(rows.getString(2)) && EMAIL.equals(rows.getString(3))
                        && Arrays.equals(EMAIL_KEY.getBytes(StandardCharsets.UTF_8), rows.getBytes(4))
                        && PHONE.equals(rows.getString(5)) && LocalDate.of(2000, 2, 29).equals(rows.getObject(6, LocalDate.class)))
                        .as("all accepted data and independent literal key committed in the new account").isTrue();
                String hash = rows.getString(7);
                assertThat(hash != null && hash.length() == 97 && hash.startsWith("$argon2id$v=19$m=19456,t=2,p=1$")
                        && !PASSWORD.equals(hash)).as("approved stored hash without plaintext").isTrue();
                assertThat(rows.next()).isFalse();
            }
            try (var statementRoles = connection.prepareStatement("select role from tb_user_account_role where user_account_id = ?")) {
                statementRoles.setLong(1, id);
                try (var roles = statementRoles.executeQuery()) {
                    assertThat(roles.next() && "CLIENT".equals(roles.getString(1)) && !roles.next())
                            .as("committed account has exactly CLIENT").isTrue();
                }
            }
        }
    }

    private static void assertOnlyOneNewAccountAndClient(Snapshot before, Snapshot after, long id) {
        assertThat(before.tables().keySet().equals(after.tables().keySet())).isTrue();
        before.tables().forEach((table, previous) -> {
            var current = after.tables().get(table);
            if (!Set.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE").contains(table)) {
                assertThat(previous.equals(current)).as("unchanged table %s", table).isTrue();
            } else {
                String column = "TB_USER_ACCOUNT".equals(table) ? "ID" : "USER_ACCOUNT_ID";
                assertThat(previous.columns().equals(current.columns())).isTrue();
                assertThat(current.rows().size()).as("one added row in %s", table).isEqualTo(previous.rows().size() + 1);
                assertThat(current.rows().stream().filter(row -> id(row, column) != id).toList().equals(previous.rows()))
                        .as("all previous fields, hashes, IDs and profiles preserved in %s", table).isTrue();
            }
        });
    }

    private void rememberNewAccounts(Snapshot before, Snapshot after) {
        var previous = before.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID"))
                .collect(java.util.stream.Collectors.toSet());
        after.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID"))
                .filter(value -> !previous.contains(value)).forEach(createdAccounts::add);
    }

    private static long id(Row row, String column) { return ((Number) row.values().get(column).value()).longValue(); }

    @AfterEach
    void cleanOnlyOwnedRowsAfterTheServerAttemptFinished() throws SQLException {
        if (!safeToClean) {
            fixture = null;
            throw new AssertionError("Server completion is unproved; rollback fixture cleanup is withheld");
        }
        assertNoCallerTransaction();
        if (createdAccounts.isEmpty()) { return; }
        try (var connection = openIndependentConnection()) {
            connection.setAutoCommit(false);
            try {
                for (long id : createdAccounts) {
                    for (String sql : List.of("delete from tb_user_account_role where user_account_id = ?",
                            "delete from tb_user_account where id = ?")) {
                        try (var statement = connection.prepareStatement(sql)) {
                            statement.setLong(1, id);
                            statement.executeUpdate();
                        }
                    }
                }
                connection.commit();
                createdAccounts.clear();
            } catch (SQLException | RuntimeException | Error failure) {
                connection.rollback();
                throw new AssertionError("Cleanup of rollback-scenario owned rows failed");
            }
        } catch (SQLException failure) {
            throw new SQLException("Cleanup of rollback-scenario owned rows could not complete");
        }
    }

    @AfterAll
    void removeOnlyTheDtoAdviceInstalledByThisClass() {
        if (workerProxy != null && dtoObserver != null) { workerProxy.removeAdvice(dtoObserver); }
    }

    enum Boundary {
        BEFORE_ACCOUNT(Point.ACCOUNT_INSERT_BEFORE, 0, 0, false),
        AFTER_ACCOUNT(Point.ACCOUNT_INSERT_RETURNED, 1, 0, false),
        BEFORE_CLIENT(Point.ROLE_INSERT_BEFORE, 1, 0, false),
        AFTER_CLIENT(Point.ROLE_INSERT_RETURNED, 1, 1, false),
        BEFORE_FLUSH(Point.FLUSH_BEFORE, 1, 0, false),
        AFTER_FLUSH_BEFORE_DTO(Point.FLUSH_RETURNED, 1, 1, false),
        AFTER_DTO(Point.WORKER_RETURNED, 1, 1, true),
        BEFORE_COMMIT(Point.BEFORE_COMMIT, 1, 1, true),
        BEFORE_JDBC_COMMIT(Point.JDBC_COMMIT_BEFORE_DELEGATE, 1, 1, true);

        final Point point;
        final int accounts;
        final int clients;
        final boolean dtoReturned;
        Boundary(Point point, int accounts, int clients, boolean dtoReturned) {
            this.point = point; this.accounts = accounts; this.clients = clients; this.dtoReturned = dtoReturned;
        }
    }

    private static final class Witness {
        private final AtomicInteger hits = new AtomicInteger();
        private volatile OwnState ownState;
        private volatile Snapshot externalBeforeCompletion;
        private volatile boolean bodyReturned;
        private volatile boolean dtoInsideTransaction;
        private volatile boolean dtoValid;
        private volatile long dtoId;
    }
    private record OwnState(int session, int accounts, int roles, int clients) { }
    private record Run(HttpResponse<byte[]> response, Snapshot before, Snapshot after, List<Event> events, Witness witness, Fault fault) {
        @Override public String toString() { return "RollbackRun[redacted]"; }
    }
}
