package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig.Failures;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Attempt;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Event;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Point;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.config.CustomerRegistrationTransactionOracle;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static com.devsuperior.dscommerce.config.CustomerRegistrationTransactionOracle.workerEvents;
import static org.assertj.core.api.Assertions.assertThat;

@RootHttpContext
@Import({CustomerRegistrationObservationConfig.class, CustomerRegistrationFailureInjectionConfig.class,
        CustomerRegistrationDeliveryScenarios.DeliveryFilterConfig.class})
class CustomerRegistrationDeliveryIntegrationTest extends CustomerRegistrationDeliveryScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T074 authorship only: controlled filter, distinct from the socket proxy in Transport tests. */
abstract class CustomerRegistrationDeliveryScenarios extends CustomerRegistrationDeliveryStateSupport {
    @Autowired private DeliveryControls controls;

    @Test
    void unarmedFilterDeliversTheOriginalCommittedConfirmation() throws SQLException {
        exerciseFilter(false);
    }

    @Test
    void controlledFilterSuppressesConfirmationAfterCommitWithoutUndoingTheAccount() throws SQLException {
        exerciseFilter(true);
    }

    private void exerciseFilter(boolean suppress) throws SQLException {
        var observed = openObserved(getClass().getSimpleName() + (suppress ? "-FILTER_SUPPRESSED" : "-FILTER_CONTROL"));
        try (observed) {
            var control = new DeliveryControl(observed.attempt(), suppress, observed.label());
            controls.pending.put(observed.attempt().headerValue(), control);
            observed.submitted();
            var response = send(signupRequest(observed.attempt()));
            observed.finish();
            assertOriginalCommit(observed.events());
            assertThat(control.reached && control.commitBeforeDelivery && control.upstreamStatus == 201)
                    .as("filter reached after the real application success and original commit").isTrue();
            assertThat(control.upstreamNoAuthentication)
                    .as("original buffered success has no authentication or Location header before suppression").isTrue();
            assertThat(control.beforeDeliverySequence).as("delivery guard follows the original registration proxy return")
                    .isGreaterThanOrEqualTo(observed.events().stream().filter(event -> event.point() == Point.PROXY_RETURNED)
                            .findFirst().orElseThrow().sequence());
            long id = confirmationId(control.originalBody);
            assertThat(control.originalBody.length).as("serialized original confirmation captured").isGreaterThan(0);
            if (suppress) {
                // Synthetic filter failure, not a new production error contract or a network incident.
                assertThat(response.statusCode()).isEqualTo(500);
                assertThat(response.body().length).isZero();
                assertThat(control.deliveredBodyBytes == 0 && control.suppressedBodyBytes == control.originalBody.length)
                        .as("the complete buffered confirmation was withheld").isTrue();
                assertNoAuthenticationHeaders(response.headers());
            } else {
                assertThat(response.statusCode()).isEqualTo(201);
                assertJson(response);
                assertThat(Arrays.equals(response.body(), control.originalBody)
                        && control.deliveredBodyBytes == response.body().length && control.suppressedBodyBytes == 0)
                        .as("unarmed filter copies the actual original confirmation").isTrue();
                assertThat(confirmationId(response.body())).isEqualTo(id);
            }
            auditDelivery(observed.label(), "mechanism=FILTER\nupstreamStatus=" + control.upstreamStatus
                    + "\nreceivedBodyBytes=" + control.originalBody.length + "\ndeliveredBodyBytes=" + control.deliveredBodyBytes
                    + "\nsuppressedBodyBytes=" + control.suppressedBodyBytes + "\nclientStatus=" + response.statusCode()
                    + "\nclientBodyBytes=" + response.body().length + "\ncommitPredicate=" + control.commitBeforeDelivery
                    + "\nbeforeDeliverySequence=" + control.beforeDeliverySequence + "\nactionReached=true\n");
            assertRetainedAccountLoginAndReplay(observed, id);
        } finally {
            if (observed.completed()) { controls.pending.remove(observed.attempt().headerValue()); }
        }
    }

    /** Lite opt-in configuration: never scanned as application or shared test configuration. */
    @Profile("test")
    public static class DeliveryFilterConfig {
        @Bean public DeliveryControls customerRegistrationDeliveryControls() { return new DeliveryControls(); }

        @Bean
        public FilterRegistrationBean<OncePerRequestFilter> customerRegistrationDeliveryFilter(DeliveryControls controls) {
            var filter = new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                    String id = request.getHeader(CustomerRegistrationObservationConfig.HEADER);
                    var control = id == null ? null : controls.pending.get(id);
                    if (control == null) { chain.doFilter(request, response); return; }
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    if (!"POST".equals(request.getMethod()) || !"/customers".equals(path)) {
                        throw new ServletException("Delivery control was attached to an unexpected request");
                    }
                    var buffered = new ContentCachingResponseWrapper(response);
                    chain.doFilter(request, buffered);
                    control.reached = true;
                    control.upstreamStatus = buffered.getStatus();
                    control.originalBody = buffered.getContentAsByteArray();
                    control.upstreamNoAuthentication = List.of("Set-Cookie", "Authorization", "Location").stream()
                            .allMatch(name -> buffered.getHeaders(name).isEmpty());
                    // Same request thread, still inside the outer Observation filter's Attempt scope.
                    control.commitBeforeDelivery = committed(control.attempt.events());
                    auditDelivery(control.label, "mechanism=FILTER\nupstreamStatus=" + control.upstreamStatus
                            + "\nreceivedBodyBytes=" + control.originalBody.length
                            + "\ncommitPredicate=" + control.commitBeforeDelivery
                            + "\nresponseAlreadyCommitted=" + response.isCommitted() + "\nactionReached=false\n");
                    if (!control.commitBeforeDelivery || control.upstreamStatus != 201 || response.isCommitted()) {
                        throw new ServletException("Original commit and buffered success were not observed before delivery control");
                    }
                    assertOriginalCommit(control.attempt.events());
                    control.beforeDeliverySequence = control.attempt.events().getLast().sequence();
                    if (control.suppress) {
                        response.reset();
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        response.setContentLength(0);
                        response.flushBuffer();
                        control.suppressedBodyBytes = control.originalBody.length;
                    } else {
                        buffered.copyBodyToResponse();
                        control.deliveredBodyBytes = control.originalBody.length;
                    }
                }
            };
            var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
            // Observation enters first and finishes only after this controlled filter returns.
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            return registration;
        }
    }

    public static final class DeliveryControls {
        private final Map<String, DeliveryControl> pending = new ConcurrentHashMap<>();
    }
    private static final class DeliveryControl {
        private final Attempt attempt;
        private final boolean suppress;
        private final String label;
        private volatile boolean reached;
        private volatile boolean commitBeforeDelivery;
        private volatile boolean upstreamNoAuthentication;
        private volatile int upstreamStatus;
        private volatile byte[] originalBody = new byte[0];
        private volatile int deliveredBodyBytes;
        private volatile int suppressedBodyBytes;
        private volatile long beforeDeliverySequence;
        private DeliveryControl(Attempt attempt, boolean suppress, String label) {
            this.attempt = attempt; this.suppress = suppress; this.label = label;
        }
        @Override public String toString() { return "DeliveryControl[redacted]"; }
    }
}

/** Shared T074 state oracles only; no @Test methods inherited across the two different mechanisms. */
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class CustomerRegistrationDeliveryStateSupport extends CustomerRegistrationHttpTestSupport {
    protected static final String NAME = "Delivery.client";
    protected static final String EMAIL = "Delivery.Email@Example.test";
    protected static final String PASSWORD = "Delivery-password-009";
    @Autowired protected Observation observation;
    @Autowired protected Failures failures;
    private final Set<Long> createdAccounts = new LinkedHashSet<>();
    private boolean safeToClean = true;

    @BeforeEach
    void prepareCommittedGuardsOutsideTheDeliveryAttempt() {
        if (!safeToClean) {
            fixture = null;
            throw new AssertionError("Delivery actor termination is unproved; fixture setup is withheld");
        }
        assertThat(observation.workerInstalled()).as("original worker observation installed").isTrue();
        fixture.prepareScenario();
    }

    protected final byte[] signupBody() {
        return jsonBytes(mapper.createObjectNode().put("name", NAME).put("fullName", "Delivery Person")
                .put("email", EMAIL).put("phone", "+5511999999999").put("birthDate", "2000-02-29")
                .put("password", PASSWORD));
    }

    protected final HttpRequest signupRequest(Attempt attempt) {
        return jsonRequest("/customers").header(CustomerRegistrationObservationConfig.HEADER, attempt.headerValue())
                .POST(HttpRequest.BodyPublishers.ofByteArray(signupBody())).build();
    }

    protected final ObservedRequest openObserved() { return new ObservedRequest(null); }
    protected final ObservedRequest openObserved(String label) { return new ObservedRequest(label); }

    /** Local opt-in evidence: fixed labels, stages and numbers only; never bodies, credentials or exception messages. */
    protected static void auditDelivery(String label, String details) {
        String directory = System.getProperty("feature009.oracle.auditDirectory");
        if (directory == null || label == null) { return; }
        if (!label.matches("[A-Za-z0-9._-]+")) { throw new AssertionError("Invalid delivery evidence label"); }
        try {
            var path = java.nio.file.Path.of(directory);
            java.nio.file.Files.createDirectories(path);
            java.nio.file.Files.writeString(path.resolve(label + ".delivery.txt"), details, StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new AssertionError("Could not preserve delivery observation"); }
    }

    protected final class ObservedRequest implements AutoCloseable {
        private final Snapshot before;
        private final Attempt attempt;
        private final String label;
        private Snapshot after;
        private boolean submitted;
        private boolean actorsStopped = true;
        private boolean closed;
        private boolean completed;

        private ObservedRequest(String label) {
            assertNoCallerTransaction();
            this.label = label;
            before = database.snapshot();
            attempt = failures.openControl();
        }
        Attempt attempt() { return attempt; }
        String label() { return label; }
        Snapshot before() { return before; }
        Snapshot after() { return after; }
        List<Event> events() { assertThat(completed).isTrue(); return attempt.events(); }
        boolean completed() { return completed; }
        void submitted() { submitted = true; safeToClean = false; }
        void actorStarted() { actorsStopped = false; safeToClean = false; }
        void actorsStopped(boolean stopped) {
            actorsStopped = stopped;
            if (!stopped) { safeToClean = false; }
        }

        void finish() {
            if (closed) { return; }
            closed = true;
            boolean serverStopped = !submitted;
            if (submitted) {
                try { attempt.awaitFinished(); serverStopped = true; }
                catch (AssertionError incomplete) { serverStopped = false; }
            }
            safeToClean = serverStopped && actorsStopped;
            if (!safeToClean) {
                fixture = null;
                throw new AssertionError("Delivery actor termination is unproved; fixture cleanup is withheld");
            }
            try {
                after = database.snapshot();
                rememberNewAccounts(before, after);
                completed = true;
                if (label != null) { CustomerRegistrationTransactionOracle.audit(label, attempt.events()); }
            } finally { attempt.close(); }
        }
        @Override public void close() { finish(); }
        @Override public String toString() { return "ObservedDeliveryRequest[redacted]"; }
    }

    protected static boolean committed(List<Event> events) {
        // Validate the earlier read-only transaction independently; only the owned write is registration.
        var registration = workerEvents(events);
        return registration.stream().filter(event -> event.point() == Point.JDBC_COMMIT_RETURNED).count() == 1
                && registration.stream().filter(event -> event.point() == Point.AFTER_COMMIT).count() == 1
                && registration.stream().filter(event -> event.point() == Point.PROXY_RETURNED).count() == 1
                && registration.stream().anyMatch(event -> event.point() == Point.AFTER_COMPLETION
                && event.completion() == TransactionSynchronization.STATUS_COMMITTED);
    }

    protected static void assertOriginalCommit(List<Event> events) {
        assertThat(committed(events)).as("original JDBC commit and Spring completion observed").isTrue();
        var registration = workerEvents(events);
        var worker = registration.stream().filter(event -> event.point() == Point.WORKER_ENTRY).findFirst().orElseThrow();
        assertThat(worker.transactionActive() && !worker.readOnly() && !worker.autoCommit()
                && worker.isolation() == java.sql.Connection.TRANSACTION_READ_COMMITTED
                && worker.transactionId() > 0 && worker.connectionId() > 0 && worker.sessionId() > 0)
                .as("registration owns an identified writable physical transaction").isTrue();
        long previous = 0;
        for (Point point : List.of(Point.PROXY_ENTRY, Point.WORKER_ENTRY, Point.ACCOUNT_INSERT_RETURNED,
                Point.ROLE_INSERT_RETURNED, Point.WORKER_RETURNED, Point.JDBC_COMMIT_DELEGATED,
                Point.JDBC_COMMIT_RETURNED, Point.AFTER_COMMIT, Point.AFTER_COMPLETION, Point.PROXY_RETURNED)) {
            assertThat(registration.stream().filter(event -> event.point() == point).count()).as("one %s", point).isEqualTo(1);
            var event = registration.stream().filter(value -> value.point() == point).findFirst().orElseThrow();
            assertThat(event.sequence()).as("order of %s", point).isGreaterThan(previous);
            previous = event.sequence();
            if (point != Point.PROXY_ENTRY) {
                assertThat(event.transactionId() == worker.transactionId() && event.connectionId() == worker.connectionId()
                        && event.sessionId() == worker.sessionId()).as("%s belongs to the original registration transaction", point).isTrue();
            }
        }
        assertThat(registration.stream().noneMatch(event -> Set.of(Point.PROXY_FAILED, Point.SQL_FAILED,
                Point.JDBC_COMMIT_FAILED, Point.JDBC_ROLLBACK_BEFORE_DELEGATE, Point.JDBC_ROLLBACK_RETURNED).contains(event.point())))
                .as("delivery loss follows a completed success, never an inferred rollback").isTrue();
    }

    protected final long confirmationId(byte[] bytes) {
        try {
            var body = mapper.readTree(bytes);
            assertThat(body != null && body.isObject() && body.size() == 2 && body.path("id").isIntegralNumber()
                    && body.path("id").canConvertToLong() && body.path("id").longValue() > 0
                    && NAME.equals(body.path("name").textValue())).as("actual serialized confirmation contains only new id/name").isTrue();
            return body.path("id").longValue();
        } catch (IOException failure) { throw new AssertionError("Original delivery confirmation could not be parsed"); }
    }

    protected final void assertRetainedAccountLoginAndReplay(ObservedRequest original, long id) throws SQLException {
        assertOnlyNewAccountAndClient(original.before(), original.after(), id);
        assertStoredAccount(id);
        realHttpLogin(id, NAME, PASSWORD);
        assertUnchanged(original.after(), database.snapshot());
        try (var replay = openObserved()) {
            replay.submitted();
            var response = send(signupRequest(replay.attempt()));
            replay.finish();
            if (original.label() != null) { CustomerRegistrationTransactionOracle.audit(original.label() + "-REPLAY", replay.events()); }
            assertThat(response.statusCode()).isEqualTo(409);
            assertJson(response);
            var expected = mapper.createObjectNode().put("status", 409).put("error", "Conflict")
                    .put("message", "Customer registration not available").put("path", expectedContextPath() + "/customers");
            assertThat(expected.equals(jsonBody(response))).as("fresh consumer replay is generic conflict, never old confirmation").isTrue();
            assertThat(replay.events().stream().noneMatch(event -> event.point() == Point.JDBC_COMMIT_RETURNED
                    || event.point() == Point.AFTER_COMMIT)).as("replay creates no second committed account").isTrue();
            assertUnchanged(original.after(), replay.before());
            assertUnchanged(replay.before(), replay.after());
        }
        auditDelivery(original.label() + "-STATE", "externalAccountAndClientVerified=true\nacceptedDataAndDateVerified=true\n"
                + "previousRowsAndOtherTablesPreserved=true\nlogin200Verified=true\nreplay409Verified=true\n"
                + "snapshotsAfterLoginAndReplayUnchanged=true\n");
        CustomerRegistrationTransactionOracle.completed(original.label());
    }

    protected static void assertJson(HttpResponse<byte[]> response) {
        boolean json;
        try { json = MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""))
                .isCompatibleWith(MediaType.APPLICATION_JSON); }
        catch (IllegalArgumentException invalidHeader) { json = false; }
        assertThat(json).isTrue();
        assertNoAuthenticationHeaders(response.headers());
    }

    protected static void assertNoAuthenticationHeaders(HttpHeaders headers) {
        for (String name : List.of("Set-Cookie", "Authorization", "Location")) {
            assertThat(headers.allValues(name).isEmpty()).as("no %s header", name).isTrue();
        }
    }

    private void assertStoredAccount(long id) throws SQLException {
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select name, full_name, email, email_key, phone, birth_date, password_hash from tb_user_account where id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(NAME.equals(rows.getString(1)) && "Delivery Person".equals(rows.getString(2)) && EMAIL.equals(rows.getString(3))
                        && Arrays.equals("delivery.email@example.test".getBytes(StandardCharsets.UTF_8), rows.getBytes(4))
                        && "+5511999999999".equals(rows.getString(5))
                        && LocalDate.of(2000, 2, 29).equals(rows.getObject(6, LocalDate.class)))
                        .as("all original accepted data and literal email key remain committed").isTrue();
                String hash = rows.getString(7);
                assertThat(hash != null && hash.length() == 97 && hash.startsWith("$argon2id$v=19$m=19456,t=2,p=1$")
                        && !PASSWORD.equals(hash)).as("approved stored hash without plaintext").isTrue();
                assertThat(rows.next()).isFalse();
            }
            try (var statementRoles = connection.prepareStatement("select role from tb_user_account_role where user_account_id = ?")) {
                statementRoles.setLong(1, id);
                try (var roles = statementRoles.executeQuery()) {
                    assertThat(roles.next() && "CLIENT".equals(roles.getString(1)) && !roles.next()).as("exactly CLIENT persists").isTrue();
                }
            }
        }
    }

    private static void assertOnlyNewAccountAndClient(Snapshot before, Snapshot after, long id) {
        assertThat(before.tables().keySet().equals(after.tables().keySet())).isTrue();
        before.tables().forEach((table, previous) -> {
            var current = after.tables().get(table);
            if (!Set.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE").contains(table)) {
                assertThat(previous.equals(current)).as("unchanged table %s", table).isTrue();
            } else {
                String column = "TB_USER_ACCOUNT".equals(table) ? "ID" : "USER_ACCOUNT_ID";
                assertThat(previous.columns().equals(current.columns())).isTrue();
                assertThat(current.rows().size()).as("one new row in %s", table).isEqualTo(previous.rows().size() + 1);
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
    void cleanOwnedRowsOnlyAfterAllDeliveryActorsStopped() throws SQLException {
        if (!safeToClean) {
            fixture = null;
            throw new AssertionError("Delivery completion is unproved; fixture cleanup is withheld");
        }
        assertNoCallerTransaction();
        if (createdAccounts.isEmpty()) { return; }
        try (var connection = openIndependentConnection()) {
            connection.setAutoCommit(false);
            try {
                for (long id : createdAccounts) {
                    for (String sql : List.of("delete from tb_user_account_role where user_account_id = ?",
                            "delete from tb_user_account where id = ?")) {
                        try (var statement = connection.prepareStatement(sql)) { statement.setLong(1, id); statement.executeUpdate(); }
                    }
                }
                connection.commit();
                createdAccounts.clear();
            } catch (SQLException | RuntimeException | Error failure) {
                connection.rollback();
                throw new AssertionError("Cleanup of delivery-owned rows failed");
            }
        } catch (SQLException failure) { throw new SQLException("Cleanup of delivery-owned rows could not complete"); }
    }
}
