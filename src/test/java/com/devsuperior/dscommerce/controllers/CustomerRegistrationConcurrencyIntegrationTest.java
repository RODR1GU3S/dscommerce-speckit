package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig.Failures;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Attempt;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Event;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Point;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.SqlKind;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Table;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.annotation.DirtiesContext;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static com.devsuperior.dscommerce.config.CustomerRegistrationTransactionOracle.audit;
import static com.devsuperior.dscommerce.config.CustomerRegistrationTransactionOracle.completed;
import static com.devsuperior.dscommerce.config.CustomerRegistrationTransactionOracle.workerEvents;
import static org.assertj.core.api.Assertions.assertThat;

@RootHttpContext
@Import({CustomerRegistrationObservationConfig.class, CustomerRegistrationFailureInjectionConfig.class})
class CustomerRegistrationConcurrencyIntegrationTest extends CustomerRegistrationConcurrencyScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T072 authorship only; T076 owns compilation, discovery and the first joint US4 execution. */
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class CustomerRegistrationConcurrencyScenarios extends CustomerRegistrationHttpTestSupport {
    @Autowired private Observation observation;
    @Autowired private Failures failures;
    @Autowired private UserAccountRepository accounts;
    @Autowired private EntityManagerFactory entityManagerFactory;
    private final Map<String, LookupWitness> lookups = new ConcurrentHashMap<>();
    private final Set<Long> createdAccounts = new LinkedHashSet<>();
    private Advised repositoryProxy;
    private MethodInterceptor lookupObserver;
    private boolean safeToClean = true;

    /** Observe original Optional results; never replace a lookup, exception, transaction or response. */
    @BeforeAll
    void observeRealAvailabilityResultsOnlyForTheTwoTaggedRequests() {
        assertThat(observation.workerInstalled()).as("original transactional worker is instrumented").isTrue();
        assertThat(accounts instanceof Advised).as("existing repository proxy").isTrue();
        repositoryProxy = (Advised) accounts;
        assertThat(repositoryProxy.isFrozen()).as("repository accepts removable test observation").isFalse();
        lookupObserver = invocation -> {
            Object result = invocation.proceed();
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request) {
                String id = request.getRequest().getHeader(CustomerRegistrationObservationConfig.HEADER);
                var witness = id == null ? null : lookups.get(id);
                if (witness != null) {
                    witness.returned(invocation.getMethod().getName(), invocation.getArguments(), result);
                }
            }
            return result;
        };
        repositoryProxy.addAdvice(0, lookupObserver);
    }

    @BeforeEach
    void prepareOnlyCommittedGuardDataBeforeStartingRequests() {
        if (!safeToClean) {
            fixture = null; // Do not mutate a quarantined database whose request completion is unproved.
            throw new AssertionError("Concurrent request completion is unproved; fixture setup is withheld");
        }
        fixture.prepareScenario();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(Conflict.class)
    void competingIdentitiesHaveOneWinnerAndAnUnmodifiedGenericLoser(Conflict conflict) throws SQLException {
        var pair = conflictingPair(conflict);
        var run = compete(pair, false, conflict.name());
        assertThat(run.responses().stream().map(HttpResponse::statusCode).sorted().toList())
                .as("exactly one success and one recognized conflict; timeout or 500 never counts as a loser")
                .containsExactly(201, 409);
        int winner = run.responses().get(0).statusCode() == 201 ? 0 : 1;
        int loser = 1 - winner;
        long winnerId = assertSuccess(run.responses().get(winner), pair.get(winner));
        assertConflict(run.responses().get(loser));
        assertCommitted(run.events().get(winner));
        assertFailedWithoutCommit(run.events().get(loser));
        assertOnlyNewAccountsAndClients(run.before(), run.after(), Set.of(winnerId));
        assertStoredAccount(winnerId, pair.get(winner));
        assertIdentityCounts(pair, List.of(pair.get(winner)));
        assertOwnCredentialsAndRejectOther(winnerId, pair.get(winner), pair.get(loser).password());
        if (!pair.get(winner).name().equals(pair.get(loser).name())) {
            assertInvalidCredentials(pair.get(loser).name(), pair.get(loser).password());
        }
        assertUnchanged(run.after(), database.snapshot());
        completed(getClass().getSimpleName() + "-" + conflict.name());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(Distinct.class)
    void distinctIdentitiesCommitWhileTheFirstAccountWriteIsStillHeld(Distinct distinct) throws SQLException {
        var pair = distinctPair(distinct);
        var run = compete(pair, true, distinct.name());
        long firstId = assertSuccess(run.responses().get(0), pair.get(0));
        long secondId = assertSuccess(run.responses().get(1), pair.get(1));
        assertThat(firstId).isNotEqualTo(secondId);
        assertOnlyNewAccountsAndClients(run.before(), run.after(), Set.of(firstId, secondId));
        for (int index = 0; index < 2; index++) {
            assertCommitted(run.events().get(index));
            assertStoredAccount(index == 0 ? firstId : secondId, pair.get(index));
            assertOwnCredentialsAndRejectOther(index == 0 ? firstId : secondId,
                    pair.get(index), pair.get(1 - index).password());
        }
        assertIdentityCounts(pair, pair);
        assertUnchanged(run.after(), database.snapshot());
        completed(getClass().getSimpleName() + "-" + distinct.name());
    }

    private Run compete(List<Signup> pair, boolean holdFirstWrite, String label) throws SQLException {
        assertNoCallerTransaction();
        var before = database.snapshot();
        var attempts = new ArrayList<Attempt>();
        var witnesses = new ArrayList<LookupWitness>();
        var gate = failures.gateBeforeWrites(2);
        var firstInserted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var firstHeld = new AtomicBoolean();
        var executor = Executors.newFixedThreadPool(2);
        var requests = new ArrayList<Future<HttpResponse<byte[]>>>();
        List<HttpResponse<byte[]>> responses;
        safeToClean = false;
        try {
            for (int index = 0; index < 2; index++) {
                var attempt = failures.openControl();
                attempts.add(attempt);
                var witness = new LookupWitness(pair.get(index), attempt);
                witnesses.add(witness);
                lookups.put(attempt.headerValue(), witness);
                attempt.on(Point.WORKER_ENTRY, () -> {
                    Object resource = TransactionSynchronizationManager.getResource(entityManagerFactory);
                    witness.workerEntityManager = resource instanceof EntityManagerHolder holder
                            ? holder.getEntityManager() : null;
                });
                // Copy on the owner thread BEFORE gate.attach's blocking callback, not from the test thread.
                attempt.on(Point.ACCOUNT_INSERT_BEFORE, () -> witness.beforeWrite = attempt.events());
                gate.attach(attempt);
            }
            if (holdFirstWrite) {
                // Only distinct keys: never wait for a competing unique-key writer after acquiring that key.
                attempts.get(0).on(Point.ACCOUNT_INSERT_RETURNED, () -> {
                    firstHeld.set(true);
                    firstInserted.countDown();
                    try { await(releaseFirst); }
                    finally { firstHeld.set(false); }
                });
                attempts.get(1).on(Point.ACCOUNT_INSERT_BEFORE, () -> await(firstInserted));
            }
            for (int index = 0; index < 2; index++) {
                var input = pair.get(index);
                var attempt = attempts.get(index);
                requests.add(executor.submit(() -> send(signupRequest(input, attempt))));
            }
            gate.awaitAll();
            assertFreeReadsAndIndependentWorkers(witnesses);
            assertUnchanged(before, database.snapshot());
            assertIdentityCounts(pair, List.of());
            assertProbeSessionIsIndependent(witnesses);
            gate.release();
            if (holdFirstWrite) {
                await(firstInserted);
                try {
                    var second = result(requests.get(1));
                    attempts.get(1).awaitFinished();
                    assertThat(firstHeld.get() && !requests.get(0).isDone())
                            .as("first transaction still holds an uncommitted account insert").isTrue();
                    long secondId = assertSuccess(second, pair.get(1));
                    assertCommitted(workerEvents(attempts.get(1).events()));
                    assertOnlyNewAccountsAndClients(before, database.snapshot(), Set.of(secondId));
                    assertStoredAccount(secondId, pair.get(1));
                    assertIdentityCounts(List.of(pair.get(0)), List.of());
                } finally { releaseFirst.countDown(); }
            }
            responses = List.of(result(requests.get(0)), result(requests.get(1)));
        } finally {
            // Release every server-side wait even if preparation/assertion/client transport fails.
            gate.release();
            releaseFirst.countDown();
            boolean clientsStopped = stopAndJoin(executor);
            boolean serversStopped = true;
            for (int index = 0; index < requests.size(); index++) {
                try { attempts.get(index).awaitFinished(); }
                catch (AssertionError incomplete) { serversStopped = false; }
            }
            safeToClean = clientsStopped && serversStopped;
            if (safeToClean) {
                for (int index = 0; index < attempts.size(); index++) {
                    audit(getClass().getSimpleName() + "-" + label + "-" + index, attempts.get(index).events());
                }
                try { rememberNewAccounts(before, database.snapshot()); }
                finally {
                    attempts.forEach(Attempt::close);
                    attempts.forEach(attempt -> lookups.remove(attempt.headerValue()));
                }
            } else {
                // Base @AfterEach must not delete fixtures while an unproved server writer may remain.
                fixture = null;
                throw new AssertionError("Concurrent request termination is unproved; cleanup is withheld");
            }
        }
        for (var witness : witnesses) {
            assertThat(witness.nameCalls.get()).as("no name retry or recovery query after the write").isEqualTo(1);
            assertThat(witness.emailCalls.get()).as("no email retry or recovery query after the write").isEqualTo(1);
        }
        return new Run(before, database.snapshot(), responses,
                List.of(workerEvents(attempts.get(0).events()), workerEvents(attempts.get(1).events())));
    }

    private void assertFreeReadsAndIndependentWorkers(List<LookupWitness> witnesses) {
        for (var witness : witnesses) {
            assertThat(witness.nameCalls.get()).as("one original name lookup, no retry or recovery query").isEqualTo(1);
            assertThat(witness.emailCalls.get()).as("one original email lookup, no retry or recovery query").isEqualTo(1);
            assertThat(witness.nameFree && witness.emailFree && witness.exactArguments)
                    .as("both consumed repository Optional results are empty for the exact submitted identities").isTrue();
            assertThat(witness.workerEntityManager != null).as("actual transaction-bound EntityManager").isTrue();
            var events = witness.beforeWrite;
            assertThat(events != null).as("pre-DML callback was reached").isTrue();
            long firstInsert = events.stream().filter(event -> event.point() == Point.ACCOUNT_INSERT_BEFORE)
                    .findFirst().orElseThrow().sequence();
            assertThat(witness.readReturnedAt > 0 && witness.readReturnedAt < firstInsert)
                    .as("free Optional results returned before the first DML").isTrue();
            assertThat(events.stream().filter(event -> event.point() == Point.SQL_RETURNED
                    && event.sqlKind() == SqlKind.SELECT && event.table() == Table.ACCOUNT).count())
                    .as("real JDBC availability SELECTs returned").isEqualTo(2);
            assertThat(events.stream().noneMatch(event -> event.point() == Point.SQL_RETURNED
                    && Set.of(SqlKind.INSERT, SqlKind.UPDATE, SqlKind.DELETE).contains(event.sqlKind())))
                    .as("no DML returned before the rendezvous").isTrue();
            var worker = workerEntry(events);
            assertThat(worker.transactionActive() && !worker.readOnly() && !worker.autoCommit()
                    && worker.isolation() == Connection.TRANSACTION_READ_COMMITTED && worker.transactionId() > 0
                    && worker.connectionId() > 0 && worker.sessionId() > 0)
                    .as("real independent write transaction with approved isolation").isTrue();
        }
        var first = workerEntry(witnesses.get(0).beforeWrite);
        var second = workerEntry(witnesses.get(1).beforeWrite);
        assertThat(first.threadId() != second.threadId() && first.connectionId() != second.connectionId()
                && first.sessionId() != second.sessionId()
                && witnesses.get(0).workerEntityManager != witnesses.get(1).workerEntityManager)
                .as("simultaneous requests have distinct server threads, JDBC sessions and actual EntityManagers").isTrue();
        // transactionId is a sequence LOCAL to an Attempt, never a globally unique transaction identity.
    }

    private void assertProbeSessionIsIndependent(List<LookupWitness> witnesses) throws SQLException {
        try (var connection = openIndependentConnection(); var statement = connection.createStatement()) {
            assertRegistrationConnection(connection);
            statement.setQueryTimeout(2);
            try (var rows = statement.executeQuery("select session_id()")) {
                assertThat(rows.next()).isTrue();
                int session = rows.getInt(1);
                assertThat(session > 0 && witnesses.stream().noneMatch(witness ->
                        workerEntry(witness.beforeWrite).sessionId() == session) && !rows.next())
                        .as("external probe uses neither active worker connection").isTrue();
            }
        }
    }

    private HttpRequest signupRequest(Signup input, Attempt attempt) {
        var body = mapper.createObjectNode().put("name", input.name()).put("fullName", input.fullName())
                .put("email", input.rawEmail()).put("phone", input.phone()).put("birthDate", input.birthDate())
                .put("password", input.password());
        return jsonRequest("/customers").header(CustomerRegistrationObservationConfig.HEADER, attempt.headerValue())
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(body))).build();
    }

    private long assertSuccess(HttpResponse<byte[]> response, Signup input) {
        assertThat(response.statusCode()).isEqualTo(201);
        assertJsonAndNoAuthentication(response);
        var body = jsonBody(response);
        assertThat(body.isObject() && body.size() == 2 && body.path("id").isIntegralNumber()
                && body.path("id").canConvertToLong() && body.path("id").longValue() > 0
                && input.name().equals(body.path("name").textValue())).as("only new id and exact chosen name").isTrue();
        return body.path("id").longValue();
    }

    private void assertConflict(HttpResponse<byte[]> response) {
        assertThat(response.statusCode()).isEqualTo(409);
        assertJsonAndNoAuthentication(response);
        var expected = mapper.createObjectNode().put("status", 409).put("error", "Conflict")
                .put("message", "Customer registration not available").put("path", expectedContextPath() + "/customers");
        assertThat(expected.equals(jsonBody(response))).as("same generic conflict without field, identity or old confirmation").isTrue();
    }

    private static void assertJsonAndNoAuthentication(HttpResponse<byte[]> response) {
        boolean json;
        try { json = MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""))
                .isCompatibleWith(MediaType.APPLICATION_JSON); }
        catch (IllegalArgumentException invalidHeader) { json = false; }
        assertThat(json).as("JSON response").isTrue();
        for (String header : List.of("Set-Cookie", "Authorization", "Location")) {
            assertThat(response.headers().allValues(header).isEmpty()).as("no %s header", header).isTrue();
        }
    }

    private static void assertCommitted(List<Event> events) {
        assertWorkerConnectionStayedBound(events);
        for (Point point : List.of(Point.ACCOUNT_INSERT_RETURNED, Point.ROLE_INSERT_RETURNED,
                Point.JDBC_COMMIT_DELEGATED, Point.JDBC_COMMIT_RETURNED, Point.AFTER_COMMIT, Point.PROXY_RETURNED)) {
            assertThat(events.stream().filter(event -> event.point() == point).count()).as("one %s", point).isEqualTo(1);
        }
        assertThat(events.stream().noneMatch(event -> event.point() == Point.SQL_FAILED
                || event.point() == Point.PROXY_FAILED || event.point() == Point.JDBC_COMMIT_FAILED)).isTrue();
        assertThat(events.stream().filter(event -> event.point() == Point.AFTER_COMPLETION)
                .allMatch(event -> event.completion() == TransactionSynchronization.STATUS_COMMITTED)
                && events.stream().filter(event -> event.point() == Point.AFTER_COMPLETION).count() == 1)
                .as("Spring reports the observed original commit").isTrue();
        assertEventOrder(events, Point.ACCOUNT_INSERT_RETURNED, Point.ROLE_INSERT_RETURNED,
                Point.JDBC_COMMIT_DELEGATED, Point.JDBC_COMMIT_RETURNED, Point.AFTER_COMMIT, Point.PROXY_RETURNED);
    }

    private static void assertFailedWithoutCommit(List<Event> events) {
        assertWorkerConnectionStayedBound(events);
        assertThat(events.stream().anyMatch(event -> event.point() == Point.SQL_FAILED)
                && events.stream().anyMatch(event -> event.point() == Point.PROXY_FAILED))
                .as("database rejected a real competing write, not a fabricated HTTP response").isTrue();
        assertThat(events.stream().noneMatch(event -> Set.of(Point.JDBC_COMMIT_DELEGATED,
                Point.JDBC_COMMIT_RETURNED, Point.AFTER_COMMIT, Point.PROXY_RETURNED).contains(event.point())))
                .as("no loser commit or success").isTrue();
        assertThat(events.stream().anyMatch(event -> event.point() == Point.JDBC_ROLLBACK_RETURNED)
                && events.stream().anyMatch(event -> event.point() == Point.AFTER_COMPLETION
                && event.completion() == TransactionSynchronization.STATUS_ROLLED_BACK))
                .as("completed rollback of the controlled losing write").isTrue();
    }

    private static void assertWorkerConnectionStayedBound(List<Event> events) {
        var worker = workerEntry(events);
        assertThat(events.stream().filter(event -> event.point() == Point.WORKER_ENTRY).count()).isEqualTo(1);
        assertThat(events.stream().filter(event -> event.sequence() >= worker.sequence())
                .allMatch(event -> event.threadId() == worker.threadId()
                        && event.connectionId() == worker.connectionId() && event.sessionId() == worker.sessionId()
                        && event.transactionId() == worker.transactionId()))
                .as("each attempt keeps its own physical worker connection and transaction through completion").isTrue();
    }

    private static void assertEventOrder(List<Event> events, Point... points) {
        long previous = 0;
        for (var point : points) {
            long sequence = events.stream().filter(event -> event.point() == point).findFirst().orElseThrow().sequence();
            assertThat(sequence).as("event sequence for %s", point).isGreaterThan(previous);
            previous = sequence;
        }
    }

    private static Event workerEntry(List<Event> events) {
        return events.stream().filter(event -> event.point() == Point.WORKER_ENTRY).findFirst().orElseThrow();
    }

    private void assertStoredAccount(long id, Signup input) throws SQLException {
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select name, full_name, email, email_key, phone, birth_date, password_hash from tb_user_account where id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(input.name().equals(rows.getString(1)) && input.fullName().equals(rows.getString(2))
                        && input.storedEmail().equals(rows.getString(3)) && Arrays.equals(key(input), rows.getBytes(4))
                        && input.phone().equals(rows.getString(5))
                        && LocalDate.parse(input.birthDate()).equals(rows.getObject(6, LocalDate.class)))
                        .as("winner keeps only its own data and literal email key; DATE stays LocalDate").isTrue();
                String hash = rows.getString(7);
                assertThat(hash != null && hash.length() == 97 && hash.startsWith("$argon2id$v=19$m=19456,t=2,p=1$")
                        && !input.password().equals(hash)).as("approved password storage").isTrue();
                assertThat(rows.next()).isFalse();
            }
            try (var statementRoles = connection.prepareStatement("select role from tb_user_account_role where user_account_id = ?")) {
                statementRoles.setLong(1, id);
                try (var roles = statementRoles.executeQuery()) {
                    assertThat(roles.next() && "CLIENT".equals(roles.getString(1)) && !roles.next())
                            .as("exactly CLIENT, never combined with caller or legacy profiles").isTrue();
                }
            }
        }
    }

    private void assertIdentityCounts(List<Signup> examined, List<Signup> committed) throws SQLException {
        try (var connection = openIndependentConnection()) {
            for (var input : examined) {
                try (var name = connection.prepareStatement("select count(*) from tb_user_account where name = ?");
                     var email = connection.prepareStatement("select count(*) from tb_user_account where email_key = ?")) {
                    name.setQueryTimeout(2);
                    email.setQueryTimeout(2);
                    name.setString(1, input.name());
                    email.setBytes(1, key(input));
                    long expectedName = committed.stream().filter(value -> value.name().equals(input.name())).count();
                    long expectedEmail = committed.stream().filter(value -> value.emailKey().equals(input.emailKey())).count();
                    try (var rows = name.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getLong(1)).as("exact name reservation count").isEqualTo(expectedName);
                    }
                    try (var rows = email.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getLong(1)).as("literal email key reservation count").isEqualTo(expectedEmail);
                    }
                }
            }
        }
    }

    private void assertOwnCredentialsAndRejectOther(long id, Signup input, String wrongPassword) {
        var before = database.snapshot();
        realHttpLogin(id, input.name(), input.password());
        assertInvalidCredentials(input.name(), wrongPassword);
        assertUnchanged(before, database.snapshot());
    }

    private void assertInvalidCredentials(String name, String password) {
        var before = database.snapshot();
        var body = mapper.createObjectNode().put("name", name).put("password", password);
        var response = send(jsonRequest("/login").POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(body))).build());
        assertThat(response.statusCode()).isEqualTo(401);
        assertJsonAndNoAuthentication(response);
        var expected = mapper.createObjectNode().put("status", 401).put("error", "Unauthorized")
                .put("message", "Invalid credentials").put("path", expectedContextPath() + "/login");
        assertThat(expected.equals(jsonBody(response))).as("wrong or absent credential remains generic").isTrue();
        assertUnchanged(before, database.snapshot());
    }

    private static void assertOnlyNewAccountsAndClients(Snapshot before, Snapshot after, Set<Long> ids) {
        assertThat(before.tables().keySet().equals(after.tables().keySet())).isTrue();
        before.tables().forEach((table, previous) -> {
            var current = after.tables().get(table);
            if (!Set.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE").contains(table)) {
                assertThat(previous.equals(current)).as("unchanged legacy/catalog/financial table %s", table).isTrue();
            } else {
                String column = "TB_USER_ACCOUNT".equals(table) ? "ID" : "USER_ACCOUNT_ID";
                assertThat(previous.columns().equals(current.columns())).as("same schema %s", table).isTrue();
                assertThat(current.rows().size()).as("only accepted rows in %s", table).isEqualTo(previous.rows().size() + ids.size());
                assertThat(current.rows().stream().filter(row -> !ids.contains(id(row, column))).toList().equals(previous.rows()))
                        .as("all previous IDs, fields, hashes and roles preserved in %s", table).isTrue();
            }
        });
    }

    private void rememberNewAccounts(Snapshot before, Snapshot after) {
        var existing = before.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID"))
                .collect(java.util.stream.Collectors.toSet());
        after.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID"))
                .filter(value -> !existing.contains(value)).forEach(createdAccounts::add);
    }

    private static long id(Row row, String column) { return ((Number) row.values().get(column).value()).longValue(); }
    private static byte[] key(Signup input) { return input.emailKey().getBytes(StandardCharsets.UTF_8); }

    private static HttpResponse<byte[]> result(Future<HttpResponse<byte[]>> request) {
        try { return request.get(35, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent HTTP request wait was interrupted");
        } catch (ExecutionException | TimeoutException failure) {
            throw new AssertionError("Concurrent HTTP request did not complete; no conflict is inferred");
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Concurrent test boundary timed out"); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent test boundary was interrupted");
        }
    }

    private static boolean stopAndJoin(ExecutorService executor) {
        executor.shutdown();
        try {
            if (executor.awaitTermination(35, TimeUnit.SECONDS)) { return true; }
            executor.shutdownNow();
            return executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @AfterEach
    void cleanOwnedRowsOnlyAfterBothClientsAndServerAttemptsFinished() throws SQLException {
        if (!safeToClean) {
            fixture = null;
            throw new AssertionError("Concurrent completion is unproved; fixture cleanup is withheld");
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
                throw new AssertionError("Cleanup of concurrency-owned rows failed");
            }
        } catch (SQLException failure) {
            throw new SQLException("Cleanup of concurrency-owned rows could not complete");
        }
    }

    @AfterAll
    void removeOnlyTheRepositoryAdviceInstalledByThisClass() {
        if (repositoryProxy != null && lookupObserver != null) { repositoryProxy.removeAdvice(lookupObserver); }
    }

    private static List<Signup> conflictingPair(Conflict conflict) {
        String secondName = conflict == Conflict.EMAIL ? "Second.racer" : "First.racer";
        boolean sameEmail = conflict != Conflict.NAME;
        return List.of(first("First.racer", "First.Email@Example.test", "first.email@example.test"),
                second(secondName, sameEmail ? "\u00A0 FIRST.EMAIL@EXAMPLE.TEST \u3000" : "Second.Email@Example.test",
                        sameEmail ? "FIRST.EMAIL@EXAMPLE.TEST" : "Second.Email@Example.test",
                        sameEmail ? "first.email@example.test" : "second.email@example.test"));
    }

    private static List<Signup> distinctPair(Distinct distinct) {
        return List.of(first(distinct == Distinct.CASE_DISTINCT ? "Ana" : "First.racer",
                        "First.Email@Example.test", "first.email@example.test"),
                second(distinct == Distinct.CASE_DISTINCT ? "ana" : "Second.racer", "Second.Email@Example.test",
                        "Second.Email@Example.test", "second.email@example.test"));
    }

    private static Signup first(String name, String email, String emailKey) {
        return new Signup(name, "First Person", email, email, emailKey,
                "+5511999999999", "2000-02-29", "First-password-009");
    }

    private static Signup second(String name, String rawEmail, String storedEmail, String emailKey) {
        return new Signup(name, "Second Person", rawEmail, storedEmail, emailKey,
                "+442012345678", "1991-12-31", "Second-password-009");
    }

    /** No entity/result/exception/SQL/bind is retained; only counts and booleans from the real calls. */
    private static final class LookupWitness {
        private final Signup input;
        private final Attempt attempt;
        private final AtomicInteger nameCalls = new AtomicInteger();
        private final AtomicInteger emailCalls = new AtomicInteger();
        private volatile boolean nameFree;
        private volatile boolean emailFree;
        private volatile boolean exactArguments = true;
        private volatile long readReturnedAt;
        private volatile EntityManager workerEntityManager;
        private volatile List<Event> beforeWrite;

        private LookupWitness(Signup input, Attempt attempt) { this.input = input; this.attempt = attempt; }

        private void returned(String method, Object[] arguments, Object result) {
            if ("findByName".equals(method)) {
                nameCalls.incrementAndGet();
                nameFree = result instanceof Optional<?> optional && optional.isEmpty();
                exactArguments &= arguments.length == 1 && input.name().equals(arguments[0]);
            } else if ("findByEmailKey".equals(method)) {
                emailCalls.incrementAndGet();
                emailFree = result instanceof Optional<?> optional && optional.isEmpty();
                exactArguments &= arguments.length == 1 && arguments[0] instanceof byte[] value && Arrays.equals(key(input), value);
            } else { return; }
            var events = attempt.events();
            readReturnedAt = events.isEmpty() ? 0 : events.getLast().sequence();
        }
    }

    enum Conflict { NAME, EMAIL, BOTH }
    enum Distinct { DISTINCT, CASE_DISTINCT }
    private record Signup(String name, String fullName, String rawEmail, String storedEmail, String emailKey,
                          String phone, String birthDate, String password) {
        @Override public String toString() { return "ConcurrentSignup[redacted]"; }
    }
    private record Run(Snapshot before, Snapshot after, List<HttpResponse<byte[]>> responses, List<List<Event>> events) {
        @Override public String toString() { return "ConcurrentRun[redacted]"; }
    }
}
