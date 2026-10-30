package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig.Failures;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Event;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Point;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.SqlKind;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.repositories.UserAccountInsertRepository.RegistrationIdentityMetadata;
import com.devsuperior.dscommerce.repositories.UserAccountInsertRepositoryImpl;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.RegistrationPasswordHasher;
import com.devsuperior.dscommerce.services.CustomerRegistrationTransaction;
import com.devsuperior.dscommerce.services.JwtTokenService;
import org.aopalliance.intercept.MethodInterceptor;
import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;
import org.hibernate.MappingException;
import org.hibernate.exception.ConstraintViolationException;
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
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static com.devsuperior.dscommerce.config.CustomerRegistrationTransactionOracle.audit;
import static com.devsuperior.dscommerce.config.CustomerRegistrationTransactionOracle.completed;
import static com.devsuperior.dscommerce.config.CustomerRegistrationTransactionOracle.workerEvents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;

@RootHttpContext
@Import({CustomerRegistrationObservationConfig.class, CustomerRegistrationFailureInjectionConfig.class,
        CustomerRegistrationOperationalScenarios.CommitFaultConfig.class})
class CustomerRegistrationOperationalHttpIntegrationTest extends CustomerRegistrationOperationalScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T075 authorship only. T076 owns compilation, discovery and the first joint US4 execution. */
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class CustomerRegistrationOperationalScenarios extends CustomerRegistrationHttpTestSupport {
    private static final String NAME = "Operational.client";
    private static final String PASSWORD = "Operational-password-009";
    @Autowired private Observation observation;
    @Autowired private Failures failures;
    @Autowired private CommitFaults commitFaults;
    @Autowired private UserAccountRepository accounts;
    @Autowired private CustomerRegistrationTransaction worker;
    @SpyBean private RegistrationPasswordHasher hasher;
    @SpyBean private UserAccountInsertRepositoryImpl insertion;
    @SpyBean private JwtTokenService tokens;
    private UserAccountInsertRepositoryImpl insertionTarget;
    private Advised repositoryProxy;
    private Advised workerProxy;
    private MethodInterceptor readObserver;
    private MethodInterceptor failureObserver;
    private final Set<Long> createdAccounts = new LinkedHashSet<>();
    private boolean safeToClean = true;
    private boolean extraIndex;
    private boolean requiredColumn;
    private boolean referenceTable;
    private boolean extraFk;
    private boolean restartIdentity;
    private long guardId;

    @BeforeAll
    void addOnlyScopedObservationAroundTheOriginalRepositoryAndWorker() {
        assertThat(observation.workerInstalled() && accounts instanceof Advised && worker instanceof Advised).isTrue();
        repositoryProxy = (Advised) accounts;
        workerProxy = (Advised) worker;
        readObserver = invocation -> {
            var scope = commitFaults.current();
            String method = invocation.getMethod().getName();
            if (scope != null && Set.of("findByName", "findByEmailKey", "registrationIdentityMetadata").contains(method)) {
                if (scope.failed.get()) { scope.lateReads.incrementAndGet(); }
                scope.reads.computeIfAbsent(method, ignored -> new AtomicInteger()).incrementAndGet();
                if ((scope.kind == Kind.NAME_LOOKUP && method.equals("findByName"))
                        || (scope.kind == Kind.EMAIL_LOOKUP && method.equals("findByEmailKey"))) {
                    scope.faultHits.incrementAndGet(); scope.failed.set(true);
                    throw new DataAccessResourceFailureException("Controlled lookup failure");
                }
            }
            Object result = invocation.proceed();
            if (scope != null && method.equals("registrationIdentityMetadata") && scope.kind != null && scope.kind.metadata()) {
                scope.metadataHits.incrementAndGet();
                if (scope.kind == Kind.METADATA_ABSENT) { return null; }
                var good = (RegistrationIdentityMetadata) result;
                return new RegistrationIdentityMetadata(good.databaseProduct(), good.databaseVersion(), good.catalog(),
                        good.schema(), good.table(), good.name(), good.name());
            }
            return result;
        };
        failureObserver = invocation -> {
            try { return invocation.proceed(); }
            catch (Throwable failure) {
                var scope = commitFaults.current();
                if (scope != null) {
                    scope.proxyFailureType = failure.getClass().getName();
                    scope.failed.set(true);
                    var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
                    for (Throwable cause = failure; cause != null && seen.size() < 32 && seen.add(cause); cause = cause.getCause()) {
                        if (cause instanceof SQLException sql) {
                            scope.sqlState = sql.getSQLState(); scope.errorCode = sql.getErrorCode(); break;
                        }
                    }
                }
                throw failure;
            }
        };
        repositoryProxy.addAdvice(0, readObserver);
        workerProxy.addAdvice(0, failureObserver);
    }

    @BeforeEach
    void prepareCommittedFixturesAndKeepTheMandatoryInsertProxy() {
        if (!safeToClean) {
            fixture = null;
            throw new AssertionError("Operational request termination is unproved; fixture setup is withheld");
        }
        guardId = fixture.prepareScenario().access().client().id();
        insertionTarget = AopTestUtils.getUltimateTargetObject(insertion);
        assertThat(mockingDetails(insertionTarget).isSpy()).as("stub only the target behind the unchanged proxy").isTrue();
        assertThat(catchThrowable(() -> insertion.insert(null)) instanceof IllegalTransactionStateException)
                .as("MANDATORY insert still rejects preparation outside a transaction").isTrue();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(value = Kind.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"COMMIT_FAILED_COMMITTED", "COMMIT_FAILED_ROLLED_BACK"})
    void operationalFailuresRemainSafe500WithoutRetryCompensationOrInventedConflict(Kind kind) throws SQLException {
        assertOperationalFailure(kind);
    }

    @Test
    void committedPhysicalOutcomeWithoutAcknowledgementRemainsSafe500() throws SQLException {
        assertOperationalFailure(Kind.COMMIT_FAILED_COMMITTED);
    }

    @Test
    void rolledBackPhysicalOutcomeWithoutAcknowledgementRemainsSafe500() throws SQLException {
        assertOperationalFailure(Kind.COMMIT_FAILED_ROLLED_BACK);
    }

    private void assertOperationalFailure(Kind kind) throws SQLException {
        var run = execute(kind);
        assertSafe500(run.response());
        assertNoTokenOrRecovery(run);
        if (kind.metadata()) {
            assertThat(run.scope().metadataHits.get()).as("missing/ambiguous metadata control actually reached").isEqualTo(1);
        } else if (!Set.of(Kind.PRIMARY_KEY, Kind.OTHER_UNIQUE, Kind.FOREIGN_KEY, Kind.NOT_NULL).contains(kind)) {
            assertThat(run.scope().faultHits.get()).as("requested controlled fault reached once").isEqualTo(1);
        }
        if (kind.state != null && !kind.metadata()) {
            assertThat(kind.state.equals(run.scope().sqlState) && kind.code == run.scope().errorCode)
                    .as("expected SQLState and code observed, without printing driver causes").isTrue();
        }
        if (kind.commit()) {
            assertPoint(run.events(), Point.JDBC_COMMIT_DELEGATED, 1);
            assertPoint(run.events(), Point.JDBC_COMMIT_FAILED, 1);
            assertPoint(run.events(), Point.JDBC_COMMIT_RETURNED, 0);
            assertPoint(run.events(), Point.AFTER_COMMIT, 0);
            // The pinned dialect translates 08007 into DataAccessResourceFailureException; Spring's
            // rollback branch reports ROLLED_BACK. This callback cannot establish the physical outcome.
            assertThat(run.scope().proxyFailureType).isEqualTo(DataAccessResourceFailureException.class.getName());
            assertPoint(run.events(), Point.AFTER_COMPLETION, 1);
            assertThat(run.events().stream().filter(event -> event.point() == Point.AFTER_COMPLETION)
                    .allMatch(event -> event.completion() == TransactionSynchronization.STATUS_ROLLED_BACK))
                    .as("actual Spring callback, independent of the controlled physical result").isTrue();
            assertThat(run.scope().physicalReturns.get()).as("one actual controlled physical completion returned").isEqualTo(1);
            assertThat(run.scope().physicalSession).as("fault targets the worker JDBC session, not metadata")
                    .isEqualTo(run.scope().workerSession).isPositive();
            assertThat(run.scope().physicalOutcome).isEqualTo(kind == Kind.COMMIT_FAILED_COMMITTED
                    ? PhysicalOutcome.COMMITTED : PhysicalOutcome.ROLLED_BACK);
        } else {
            assertPoint(run.events(), Point.JDBC_COMMIT_DELEGATED, 0);
        }
        if (kind == Kind.COMMIT_FAILED_COMMITTED) {
            long id = newId(run.before(), run.after());
            assertOnlyNewAccountAndClient(run.before(), run.after(), id);
            assertStoredAccount(id);
        } else {
            assertUnchanged(run.before(), run.after());
        }
        completed(getClass().getSimpleName() + "-" + kind.name());
    }

    @Test
    void unarmedControlUsesTheSameDecoratorsAndCommitsAnOrdinary201() throws SQLException {
        var run = execute(null);
        assertThat(run.response().statusCode()).isEqualTo(201);
        assertHeaders(run.response());
        var body = jsonBody(run.response());
        assertThat(body.isObject() && body.size() == 2 && body.path("id").isIntegralNumber()
                && body.path("id").canConvertToLong() && body.path("id").longValue() > 0 && NAME.equals(body.path("name").textValue()))
                .as("ordinary original confirmation only").isTrue();
        long id = body.path("id").longValue();
        assertPoint(run.events(), Point.JDBC_COMMIT_DELEGATED, 1);
        assertPoint(run.events(), Point.JDBC_COMMIT_RETURNED, 1);
        assertPoint(run.events(), Point.JDBC_COMMIT_FAILED, 0);
        assertPoint(run.events(), Point.AFTER_COMMIT, 1);
        assertPoint(run.events(), Point.PROXY_RETURNED, 1);
        assertOnlyNewAccountAndClient(run.before(), run.after(), id);
        assertStoredAccount(id);
        assertNoTokenOrRecovery(run);
        assertThat(run.scope().physicalOutcome).isEqualTo(PhysicalOutcome.NOT_INVOKED);
        assertThat(run.scope().physicalReturns.get()).isZero();
        completed(getClass().getSimpleName() + "-CONTROL");
    }

    private Run execute(Kind kind) throws SQLException {
        var healthyMetadata = insertion.registrationIdentityMetadata(); // Healthy preparation before any armed write.
        prepareDatabaseFault(kind);
        var before = database.snapshot();
        var attempt = failures.openControl();
        var scope = new Scope(kind, healthyMetadata);
        commitFaults.pending.put(attempt.headerValue(), scope);
        long issuedBefore = calls(tokens, "issueToken");
        boolean submitted = false;
        HttpResponse<byte[]> response;
        safeToClean = false;
        try {
            attempt.onWorker(Point.WORKER_ENTRY, () -> scope.workerSession = attempt.events().stream()
                    .filter(event -> event.point() == Point.WORKER_ENTRY).findFirst().orElseThrow().sessionId());
            armTargetSpies(scope);
            if (kind == Kind.ROLE_UNIQUE) {
                attempt.on(Point.ROLE_INSERT_BEFORE, () -> {
                    if (!scope.roleSeeded.compareAndSet(false, true)) { return; }
                    scope.faultHits.incrementAndGet();
                    seedSameClientRoleInsideTheWorker();
                });
            }
            var body = mapper.createObjectNode().put("name", NAME).put("fullName", "Operational Person")
                    .put("email", "Operational.Email@Example.test").put("phone", "+5511999999999")
                    .put("birthDate", "2000-02-29").put("password", PASSWORD);
            submitted = true;
            response = send(jsonRequest("/customers").header(CustomerRegistrationObservationConfig.HEADER, attempt.headerValue())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(body))).build());
        } finally {
            safeToClean = !submitted;
            if (submitted) {
                try { attempt.awaitFinished(); safeToClean = true; }
                catch (AssertionError incomplete) { safeToClean = false; }
            }
            if (safeToClean) {
                try { rememberNewAccounts(before, database.snapshot()); }
                finally { attempt.close(); commitFaults.pending.remove(attempt.headerValue()); }
            } else {
                fixture = null;
                throw new AssertionError("Operational server attempt is unfinished; cleanup is withheld");
            }
        }
        audit(getClass().getSimpleName() + "-" + (kind == null ? "CONTROL" : kind.name()), attempt.events());
        return new Run(response, before, database.snapshot(), workerEvents(attempt.events()), scope, issuedBefore);
    }

    private void armTargetSpies(Scope scope) {
        doAnswer(invocation -> {
            scope.hashCalls.incrementAndGet();
            assertThat(PASSWORD.equals(invocation.getArgument(0))).as("exact raw password at the existing hasher").isTrue();
            if (scope.kind == Kind.ENCODER) {
                scope.faultHits.incrementAndGet(); scope.failed.set(true);
                throw new IllegalStateException("Controlled encoder failure");
            }
            return invocation.callRealMethod();
        }).when(hasher).hash(anyString());
        doAnswer(invocation -> {
            scope.insertCalls.incrementAndGet();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).as("real worker transaction").isTrue();
            UserAccount account = invocation.getArgument(0);
            if (scope.kind == Kind.LENGTH) {
                scope.faultHits.incrementAndGet();
                ReflectionTestUtils.setField(account, "phone", "+" + "1".repeat(40));
            } else if (scope.kind == Kind.TIMEOUT || scope.kind == Kind.DEADLOCK) {
                scope.faultHits.incrementAndGet();
                SQLException sql = scope.kind == Kind.TIMEOUT
                        ? new SQLTimeoutException("Controlled timeout", "HYT00", 50200)
                        : new SQLException("Controlled deadlock", "40001", 40001);
                throw new DataAccessResourceFailureException("Controlled operational failure", sql);
            } else if (scope.kind == Kind.MAPPING) {
                scope.faultHits.incrementAndGet();
                throw new MappingException("Controlled mapping failure");
            } else if (scope.kind != null && scope.kind.metadata()) {
                scope.faultHits.incrementAndGet();
                throw syntheticIdentityFailure(scope.healthyMetadata);
            }
            return invocation.callRealMethod();
        }).when(insertionTarget).insert(any(UserAccount.class));
    }

    private void prepareDatabaseFault(Kind kind) throws SQLException {
        if (kind == Kind.PRIMARY_KEY) {
            jdbc("ALTER TABLE tb_user_account ALTER COLUMN id RESTART WITH " + guardId); restartIdentity = true;
        } else if (kind == Kind.OTHER_UNIQUE) {
            jdbc("UPDATE tb_user_account SET full_name = 'Operational Person' WHERE id = " + guardId);
            jdbc("CREATE UNIQUE INDEX UK_USER_ACCOUNT_NAME_SHADOW ON tb_user_account(full_name)"); extraIndex = true;
        } else if (kind == Kind.FOREIGN_KEY) {
            jdbc("CREATE TABLE FEATURE009_OPERATIONAL_REFERENCE(label VARCHAR(240) PRIMARY KEY)"); referenceTable = true;
            jdbc("ALTER TABLE tb_user_account ADD CONSTRAINT FK_F009_OPERATIONAL FOREIGN KEY(full_name) REFERENCES FEATURE009_OPERATIONAL_REFERENCE(label)");
            extraFk = true;
        } else if (kind == Kind.NOT_NULL) {
            jdbc("ALTER TABLE tb_user_account ADD COLUMN feature009_required INTEGER DEFAULT 1 NOT NULL"); requiredColumn = true;
            jdbc("ALTER TABLE tb_user_account ALTER COLUMN feature009_required DROP DEFAULT");
        }
    }

    private void seedSameClientRoleInsideTheWorker() {
        var connection = DataSourceUtils.getConnection(dataSource);
        try {
            assertThat(DataSourceUtils.isConnectionTransactional(connection, dataSource)).isTrue();
            long id;
            try (var statement = connection.prepareStatement("select id from tb_user_account where name = ?")) {
                statement.setQueryTimeout(2);
                statement.setString(1, NAME);
                try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); id = rows.getLong(1); }
            }
            try (var statement = connection.prepareStatement("insert into tb_user_account_role(user_account_id,role) values (?, 'CLIENT')")) {
                statement.setQueryTimeout(2);
                statement.setLong(1, id);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
        } catch (SQLException failure) { throw new AssertionError("Controlled role collision preparation failed"); }
        finally { DataSourceUtils.releaseConnection(connection, dataSource); }
    }

    private static RuntimeException syntheticIdentityFailure(RegistrationIdentityMetadata metadata) {
        String identity = "PUBLIC." + metadata.name().indexName()
                + " ON PUBLIC.TB_USER_ACCOUNT(NAME NULLS FIRST) VALUES ( /* 1 */ 'controlled' )";
        String sql = "insert into tb_user_account (name,password_hash) values (?,?)";
        var cause = new JdbcSQLIntegrityConstraintViolationException("Unique index or primary key violation: \"" + identity + "\"",
                sql, "23505", 23505, null, null);
        return new DataIntegrityViolationException("Controlled diagnosis", new ConstraintViolationException("Controlled", cause, sql, identity));
    }

    private void assertNoTokenOrRecovery(Run run) {
        assertThat(calls(tokens, "issueToken")).as("signup failure or success never authenticates").isEqualTo(run.issuedBefore());
        if (run.scope().kind != null && run.scope().kind.metadata()) {
            assertThat(run.scope().hashCalls.get()).as("invalid metadata may stop before derivation").isLessThanOrEqualTo(1);
        } else {
            assertThat(run.scope().hashCalls.get()).isEqualTo(1);
        }
        assertThat(run.scope().insertCalls.get()).as("no write retry").isLessThanOrEqualTo(1);
        assertThat(run.scope().lateReads.get()).as("no availability or metadata recovery query").isZero();
        run.scope().reads.forEach((name, count) -> assertThat(count.get()).as("no repeated %s", name).isLessThanOrEqualTo(1));
        assertThat(run.events().stream().filter(event -> event.point() == Point.PROXY_ENTRY).count()).isLessThanOrEqualTo(1);
        assertThat(run.events().stream().noneMatch(event -> event.sqlKind() == SqlKind.UPDATE || event.sqlKind() == SqlKind.DELETE))
                .as("no product overwrite or compensating DML").isTrue();
    }

    private void assertSafe500(HttpResponse<byte[]> response) {
        assertThat(response.statusCode()).isEqualTo(500);
        assertHeaders(response);
        var expected = mapper.createObjectNode().put("status", 500).put("error", "Internal Server Error")
                .put("message", "Customer registration could not be completed").put("path", expectedContextPath() + "/customers");
        assertThat(expected.equals(jsonBody(response))).as("exact safe processing error, no field, identity, SQL or token").isTrue();
    }

    private static void assertHeaders(HttpResponse<byte[]> response) {
        boolean json;
        try { json = MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""))
                .isCompatibleWith(MediaType.APPLICATION_JSON); }
        catch (IllegalArgumentException invalidHeader) { json = false; }
        assertThat(json).isTrue();
        for (String header : List.of("Set-Cookie", "Authorization", "Location")) {
            assertThat(response.headers().allValues(header).isEmpty()).as("no %s header", header).isTrue();
        }
    }

    private void assertStoredAccount(long id) throws SQLException {
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select name,full_name,email,email_key,phone,birth_date,password_hash from tb_user_account where id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(NAME.equals(rows.getString(1)) && "Operational Person".equals(rows.getString(2))
                        && "Operational.Email@Example.test".equals(rows.getString(3))
                        && Arrays.equals("operational.email@example.test".getBytes(StandardCharsets.UTF_8), rows.getBytes(4))
                        && "+5511999999999".equals(rows.getString(5)) && LocalDate.of(2000, 2, 29).equals(rows.getObject(6, LocalDate.class)))
                        .as("observed physical outcome retains exactly the accepted fields and DATE").isTrue();
                String hash = rows.getString(7);
                assertThat(hash != null && hash.length() == 97 && hash.startsWith("$argon2id$v=19$m=19456,t=2,p=1$")
                        && !PASSWORD.equals(hash)).as("approved stored hash without plaintext").isTrue();
                assertThat(rows.next()).isFalse();
            }
            try (var roles = connection.prepareStatement("select role from tb_user_account_role where user_account_id = ?")) {
                roles.setLong(1, id);
                try (var rows = roles.executeQuery()) { assertThat(rows.next() && "CLIENT".equals(rows.getString(1)) && !rows.next()).isTrue(); }
            }
        }
    }

    private static void assertPoint(List<Event> events, Point point, int count) {
        assertThat(events.stream().filter(event -> event.point() == point).count()).as("%s", point).isEqualTo(count);
    }
    private static long calls(Object spy, String name) {
        return mockingDetails(spy).getInvocations().stream().filter(call -> call.getMethod().getName().equals(name)).count();
    }
    private static long id(Row row, String column) { return ((Number) row.values().get(column).value()).longValue(); }
    private static long newId(Snapshot before, Snapshot after) {
        var old = before.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID")).collect(java.util.stream.Collectors.toSet());
        var added = after.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID")).filter(value -> !old.contains(value)).toList();
        assertThat(added.size()).isEqualTo(1);
        return added.get(0);
    }
    private static void assertOnlyNewAccountAndClient(Snapshot before, Snapshot after, long id) {
        before.tables().forEach((table, previous) -> {
            var current = after.tables().get(table);
            if (!Set.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE").contains(table)) {
                assertThat(previous.equals(current)).as("unchanged table %s", table).isTrue();
            } else {
                String column = "TB_USER_ACCOUNT".equals(table) ? "ID" : "USER_ACCOUNT_ID";
                assertThat(previous.columns().equals(current.columns())).isTrue();
                assertThat(current.rows().size()).isEqualTo(previous.rows().size() + 1);
                assertThat(current.rows().stream().filter(row -> id(row, column) != id).toList().equals(previous.rows()))
                        .as("all previous fields, hashes, identities and profiles preserved in %s", table).isTrue();
            }
        });
    }
    private void rememberNewAccounts(Snapshot before, Snapshot after) {
        var old = before.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID")).collect(java.util.stream.Collectors.toSet());
        after.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID"))
                .filter(value -> !old.contains(value)).forEach(createdAccounts::add);
    }
    private void jdbc(String sql) throws SQLException {
        try (var connection = openIndependentConnection(); var statement = connection.createStatement()) {
            statement.setQueryTimeout(2); statement.execute(sql);
        }
    }

    @AfterEach
    void restoreOnlyTestOwnedSchemaAndRowsAfterTheServerFinished() throws SQLException {
        if (!safeToClean) {
            fixture = null;
            throw new AssertionError("Operational completion is unproved; schema and fixture cleanup are withheld");
        }
        reset(hasher, tokens);
        if (insertionTarget != null) { reset(insertionTarget); }
        if (extraIndex) { jdbc("DROP INDEX UK_USER_ACCOUNT_NAME_SHADOW"); extraIndex = false; }
        if (extraFk) { jdbc("ALTER TABLE tb_user_account DROP CONSTRAINT FK_F009_OPERATIONAL"); extraFk = false; }
        if (referenceTable) { jdbc("DROP TABLE FEATURE009_OPERATIONAL_REFERENCE"); referenceTable = false; }
        if (requiredColumn) { jdbc("ALTER TABLE tb_user_account DROP COLUMN feature009_required"); requiredColumn = false; }
        long restartAt = 0;
        try (var connection = openIndependentConnection()) {
            connection.setAutoCommit(false);
            try {
                for (long id : createdAccounts) {
                    for (String sql : List.of("delete from tb_user_account_role where user_account_id = ?", "delete from tb_user_account where id = ?")) {
                        try (var statement = connection.prepareStatement(sql)) { statement.setLong(1, id); statement.executeUpdate(); }
                    }
                }
                connection.commit(); createdAccounts.clear();
            } catch (SQLException | RuntimeException | Error failure) {
                connection.rollback(); throw new AssertionError("Cleanup of operational-owned rows failed");
            }
            if (restartIdentity) {
                long next;
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("select coalesce(max(id),0)+1 from tb_user_account")) {
                    assertThat(rows.next()).isTrue(); next = rows.getLong(1);
                }
                assertThat(next > 0).isTrue();
                restartAt = next;
                connection.commit();
            }
        } catch (SQLException failure) { throw new SQLException("Cleanup of operational-owned state could not complete"); }
        if (restartIdentity) {
            jdbc("ALTER TABLE tb_user_account ALTER COLUMN id RESTART WITH " + restartAt); restartIdentity = false;
        }
    }

    @AfterAll
    void removeOnlyTheObservationAdvicesInstalledByThisClass() {
        if (repositoryProxy != null && readObserver != null) { repositoryProxy.removeAdvice(readObserver); }
        if (workerProxy != null && failureObserver != null) { workerProxy.removeAdvice(failureObserver); }
    }

    /** Ordered before T009: its original commit observer sees a failed delegate, not a successful return. */
    @Profile("test")
    public static class CommitFaultConfig {
        @Bean public static CommitFaults customerRegistrationCommitFaults() { return new CommitFaults(); }
    }
    public static final class CommitFaults implements BeanPostProcessor, Ordered {
        private final Map<String, Scope> pending = new ConcurrentHashMap<>();
        @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
        @Override public Object postProcessAfterInitialization(Object bean, String name) {
            return name.equals("dataSource") && bean instanceof DataSource source ? new FaultDataSource(source, this) : bean;
        }
        private Scope current() {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request) {
                String id = request.getRequest().getHeader(CustomerRegistrationObservationConfig.HEADER);
                return id == null ? null : pending.get(id);
            }
            return null;
        }
    }
    private static final class FaultDataSource extends DelegatingDataSource implements AutoCloseable {
        private final CommitFaults faults;
        private FaultDataSource(DataSource target, CommitFaults faults) { super(target); this.faults = faults; }
        @Override public Connection getConnection() throws SQLException { return wrap(super.getConnection()); }
        @Override public Connection getConnection(String name, String password) throws SQLException { return wrap(super.getConnection(name, password)); }
        private Connection wrap(Connection target) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (self, method, arguments) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "OperationalJdbc[redacted]";
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == arguments[0];
                        default -> throw new AssertionError("Unexpected JDBC identity method");
                    };
                }
                var scope = faults.current();
                if (scope != null && scope.kind != null && scope.kind.commit() && method.getName().equals("commit")
                        && method.getParameterCount() == 0 && scope.workerSession > 0
                        && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                        && scope.commitArmed.compareAndSet(false, true)) {
                    scope.faultHits.incrementAndGet();
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive() && !target.getAutoCommit()).isTrue();
                    try (var statement = target.createStatement()) {
                        statement.setQueryTimeout(2);
                        try (var rows = statement.executeQuery("select session_id()")) {
                            assertThat(rows.next()).isTrue();
                            scope.physicalSession = rows.getInt(1);
                            assertThat(scope.physicalSession).isEqualTo(scope.workerSession);
                        }
                    }
                    if (scope.kind == Kind.COMMIT_FAILED_COMMITTED) {
                        target.commit();
                        scope.physicalOutcome = PhysicalOutcome.COMMITTED;
                    } else {
                        target.rollback();
                        scope.physicalOutcome = PhysicalOutcome.ROLLED_BACK;
                    }
                    scope.physicalReturns.incrementAndGet();
                    // Controlled physical outcome is known to the fixture, unavailable through this delegate result.
                    throw new SQLException("Controlled commit outcome acknowledgement failure", "08007", 8007);
                }
                return delegate(target, method, arguments);
            });
        }
        private static Object delegate(Object target, Method method, Object[] arguments) throws Throwable {
            try { return method.invoke(target, arguments); }
            catch (InvocationTargetException failure) { throw failure.getTargetException(); }
        }
        @Override public void close() throws Exception {
            if (getTargetDataSource() instanceof AutoCloseable closeable) { closeable.close(); }
        }
    }
    private static final class Scope {
        private final Kind kind;
        private final RegistrationIdentityMetadata healthyMetadata;
        private final AtomicBoolean failed = new AtomicBoolean();
        private final AtomicBoolean roleSeeded = new AtomicBoolean();
        private final AtomicBoolean commitArmed = new AtomicBoolean();
        private final AtomicInteger faultHits = new AtomicInteger();
        private final AtomicInteger metadataHits = new AtomicInteger();
        private final AtomicInteger lateReads = new AtomicInteger();
        private final AtomicInteger hashCalls = new AtomicInteger();
        private final AtomicInteger insertCalls = new AtomicInteger();
        private final AtomicInteger physicalReturns = new AtomicInteger();
        private final Map<String, AtomicInteger> reads = new ConcurrentHashMap<>();
        private volatile String sqlState;
        private volatile int errorCode;
        private volatile int workerSession;
        private volatile int physicalSession;
        private volatile PhysicalOutcome physicalOutcome = PhysicalOutcome.NOT_INVOKED;
        private volatile String proxyFailureType;
        private Scope(Kind kind, RegistrationIdentityMetadata healthyMetadata) { this.kind = kind; this.healthyMetadata = healthyMetadata; }
    }
    private enum PhysicalOutcome { NOT_INVOKED, COMMITTED, ROLLED_BACK }
    enum Kind {
        NAME_LOOKUP(null, 0), EMAIL_LOOKUP(null, 0), ENCODER(null, 0), PRIMARY_KEY("23505", 23505),
        ROLE_UNIQUE("23505", 23505), OTHER_UNIQUE("23505", 23505), FOREIGN_KEY("23506", 23506),
        NOT_NULL("23502", 23502), LENGTH("22001", 22001), METADATA_ABSENT(null, 0), METADATA_AMBIGUOUS(null, 0),
        TIMEOUT("HYT00", 50200), DEADLOCK("40001", 40001), MAPPING(null, 0),
        COMMIT_FAILED_COMMITTED("08007", 8007), COMMIT_FAILED_ROLLED_BACK("08007", 8007);
        final String state; final int code;
        Kind(String state, int code) { this.state = state; this.code = code; }
        boolean metadata() { return this == METADATA_ABSENT || this == METADATA_AMBIGUOUS; }
        boolean commit() { return this == COMMIT_FAILED_COMMITTED || this == COMMIT_FAILED_ROLLED_BACK; }
    }
    private record Run(HttpResponse<byte[]> response, Snapshot before, Snapshot after, List<Event> events, Scope scope, long issuedBefore) {
        @Override public String toString() { return "OperationalRun[redacted]"; }
    }
}
