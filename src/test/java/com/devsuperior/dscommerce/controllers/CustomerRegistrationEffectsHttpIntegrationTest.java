package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Attempt;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Event;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Point;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.devsuperior.dscommerce.config.CustomerRegistrationTransactionOracle.workerEvents;
import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;

@RootHttpContext
@Import({CustomerRegistrationObservationConfig.class, CustomerRegistrationFailureInjectionConfig.class,
        CustomerRegistrationEffectsScenarios.EffectsDeliveryConfig.class})
class CustomerRegistrationEffectsHttpIntegrationTest extends CustomerRegistrationEffectsScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T091 authorship only. T092 owns compilation, discovery and actual observations. */
abstract class CustomerRegistrationEffectsScenarios extends CustomerRegistrationDeliveryStateSupport {
    // The reused parent has no @Test methods. It owns committed legacy/financial guards,
    // request termination, independent snapshots, selective account cleanup and transaction oracles.
    // Cart state belongs to the consumer: no server-side cart fixture, endpoint or table is invented.
    @Autowired private EffectsDeliveryControls delivery;

    @Test
    void acceptedRegistrationAddsOnlyItsAccountDataAndClientWhileAllPreviousRowsStayEqual() throws SQLException {
        try (var run = openObserved(getClass().getSimpleName() + "-EFFECTS_ACCEPTED")) {
            assertGuardedDataset(run.before());
            run.submitted();
            var response = send(signupRequest(run.attempt()));
            run.finish();
            assertThat(response.statusCode()).isEqualTo(201);
            assertJson(response);
            assertOriginalCommit(run.events());
            assertOnlyAcceptedRows(run.before(), run.after(), confirmationId(response.body()));
            assertUnchanged(run.after(), database.snapshot());
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(Rejection.class)
    void rejectedRegistrationLeavesEveryTableAndLegacyFieldUnchanged(Rejection rejection) {
        if (rejection == Rejection.NAME_CONFLICT) {
            // A committed pre-existing account is the conflict victim, never a signup-created placeholder.
            fixture.createAccount(NAME, LegacyCredential.SHORT, Set.of(UserRole.ADMIN));
        }
        try (var run = openObserved(getClass().getSimpleName() + "-EFFECTS_" + rejection.name())) {
            assertGuardedDataset(run.before());
            byte[] body = signupBody();
            if (rejection == Rejection.INVALID_PHONE) {
                body = jsonBytes(mapper.createObjectNode().put("name", NAME).put("fullName", "Delivery Person")
                        .put("email", EMAIL).put("phone", "123").put("birthDate", "2000-02-29").put("password", PASSWORD));
            }
            var request = jsonRequest("/customers")
                    .header(CustomerRegistrationObservationConfig.HEADER, run.attempt().headerValue())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            run.submitted();
            var response = send(request);
            run.finish();
            assertError(response, rejection == Rejection.INVALID_PHONE ? 400 : 409,
                    rejection == Rejection.INVALID_PHONE ? "Bad Request" : "Conflict",
                    rejection == Rejection.INVALID_PHONE ? "Invalid customer registration data" : "Customer registration not available");
            assertThat(workerEvents(run.events()).isEmpty()).as("rejection does not enter the registration worker").isTrue();
            assertUnchanged(run.before(), run.after());
            assertUnchanged(run.after(), database.snapshot());
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(PreCommitFailure.class)
    void knownPreCommitFailureRollsBackReachedWritesWithoutAnyFinancialOrLegacyMutation(PreCommitFailure boundary) {
        try (var run = openObserved(getClass().getSimpleName() + "-EFFECTS_" + boundary.name())) {
            assertGuardedDataset(run.before());
            var fault = failures.failOnceInWorker(run.attempt(), boundary.point);
            run.submitted();
            var response = send(signupRequest(run.attempt()));
            run.finish();
            assertError(response, 500, "Internal Server Error", "Customer registration could not be completed");
            fault.assertReached();
            assertThat(fault.hits()).as("one controlled boundary, no automatic retry").isEqualTo(1);
            var events = workerEvents(run.events());
            var worker = one(events, Point.WORKER_ENTRY);
            assertThat(worker.transactionActive() && !worker.readOnly() && !worker.autoCommit()
                    && worker.isolation() == java.sql.Connection.TRANSACTION_READ_COMMITTED
                    && worker.transactionId() > 0 && worker.connectionId() > 0 && worker.sessionId() > 0).isTrue();
            long previous = 0;
            for (Point point : List.of(Point.WORKER_ENTRY, Point.ACCOUNT_INSERT_RETURNED, Point.ROLE_INSERT_RETURNED,
                    Point.JDBC_ROLLBACK_BEFORE_DELEGATE, Point.JDBC_ROLLBACK_RETURNED, Point.AFTER_COMPLETION, Point.PROXY_FAILED)) {
                var event = one(events, point);
                assertThat(event.sequence()).as("ordered rollback point %s", point).isGreaterThan(previous);
                previous = event.sequence();
                assertThat(event.transactionId() == worker.transactionId() && event.connectionId() == worker.connectionId()
                        && event.sessionId() == worker.sessionId()).as("same original registration transaction").isTrue();
            }
            assertThat(one(events, boundary.point).sequence())
                    .isLessThan(one(events, Point.JDBC_ROLLBACK_BEFORE_DELEGATE).sequence());
            assertThat(events.stream().noneMatch(event -> Set.of(Point.JDBC_COMMIT_DELEGATED, Point.JDBC_COMMIT_RETURNED,
                    Point.JDBC_COMMIT_FAILED, Point.AFTER_COMMIT, Point.PROXY_RETURNED, Point.JDBC_ROLLBACK_FAILED)
                    .contains(event.point()))).as("original commit was not delegated; physical rollback returned").isTrue();
            // Spring UNKNOWN at the pre-JDBC sentinel is distinct from the proven physical rollback.
            assertThat(one(events, Point.AFTER_COMPLETION).completion()).isEqualTo(boundary.springCompletion);
            assertUnchanged(run.before(), run.after());
            assertUnchanged(run.after(), database.snapshot());
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(Delivery.class)
    void controlledDeliveryOutcomeKeepsOnlyTheCommittedNewAccountAndNeverChangesPreviousPurchases(Delivery mode)
            throws SQLException {
        var run = openObserved(getClass().getSimpleName() + "-EFFECTS_" + mode.name());
        var control = new EffectsDeliveryControl(run.attempt(), mode == Delivery.SUPPRESS_AFTER_COMMIT);
        delivery.pending.put(run.attempt().headerValue(), control);
        try (run) {
            assertGuardedDataset(run.before());
            run.submitted();
            var response = send(signupRequest(run.attempt()));
            run.finish();
            assertOriginalCommit(run.events());
            assertThat(control.reached && control.upstreamStatus == 201 && control.commitVerified
                    && control.noAuthenticationHeaders && control.originalBody.length > 0).isTrue();
            assertThat(control.beforeDeliverySequence).isGreaterThanOrEqualTo(one(workerEvents(run.events()), Point.PROXY_RETURNED).sequence());
            long id = confirmationId(control.originalBody);
            assertNoAuthenticationHeaders(response.headers());
            if (mode == Delivery.SUPPRESS_AFTER_COMMIT) {
                // Synthetic empty 500 is a filter artifact, not a production contract or a physical network failure.
                assertThat(response.statusCode()).isEqualTo(500);
                assertThat(response.body().length == 0 && control.deliveredBodyBytes == 0
                        && control.suppressedBodyBytes == control.originalBody.length).isTrue();
            } else {
                assertThat(response.statusCode()).isEqualTo(201);
                assertJson(response);
                assertThat(Arrays.equals(response.body(), control.originalBody)
                        && control.deliveredBodyBytes == response.body().length && control.suppressedBodyBytes == 0)
                        .as("unarmed control forwards the entire original serialized confirmation").isTrue();
                assertThat(confirmationId(response.body())).isEqualTo(id);
            }
            assertOnlyAcceptedRows(run.before(), run.after(), id);
            assertUnchanged(run.after(), database.snapshot());
        } finally {
            if (run.completed()) { delivery.pending.remove(run.attempt().headerValue()); }
        }
    }

    private void assertError(HttpResponse<byte[]> response, int status, String error, String message) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertJson(response);
        var expected = mapper.createObjectNode().put("status", status).put("error", error).put("message", message)
                .put("path", expectedContextPath() + "/customers");
        assertThat(expected.equals(jsonBody(response))).as("exact safe four-field rejection, no identity or cause").isTrue();
    }

    private static Event one(List<Event> events, Point point) {
        var found = events.stream().filter(event -> event.point() == point).toList();
        assertThat(found.size()).as("one %s", point).isEqualTo(1);
        return found.getFirst();
    }

    private static void assertGuardedDataset(Snapshot before) {
        assertThat(before.tables().get("TB_USER_ACCOUNT").rows().size()).isGreaterThanOrEqualTo(4);
        assertThat(before.tables().get("TB_USER_ACCOUNT_ROLE").rows().size()).isGreaterThanOrEqualTo(4);
        assertThat(before.tables().get("TB_CATEGORY").rows().size()).isEqualTo(1);
        assertThat(before.tables().get("TB_PRODUCT").rows().size()).isEqualTo(2);
        assertThat(before.tables().get("TB_PRODUCT_CATEGORY").rows().size()).isEqualTo(2);
        assertThat(before.tables().get("TB_ORDER").rows().size()).isEqualTo(2);
        assertThat(before.tables().get("TB_ORDER_ITEM").rows().size()).isEqualTo(3);
        assertThat(before.tables().get("TB_PAYMENT").rows().size()).isEqualTo(1);
        assertThat(before.tables().get("TB_ORDER").rows().stream()
                .map(row -> row.values().get("STATUS").value()).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of("WAITING_PAYMENT", "PAID"))).as("both prior financial states are present").isTrue();
        assertThat(before.tables().get("TB_USER_ACCOUNT").rows().stream().allMatch(row ->
                List.of("FULL_NAME", "EMAIL", "EMAIL_KEY", "PHONE", "BIRTH_DATE").stream()
                        .allMatch(column -> row.values().get(column).isNull())))
                .as("legacy nullable fields are genuine NULL guards, never repaired").isTrue();
    }

    private void assertOnlyAcceptedRows(Snapshot before, Snapshot after, long accountId) throws SQLException {
        assertThat(before.tables().keySet().equals(after.tables().keySet())).isTrue();
        before.tables().forEach((table, previous) -> {
            var current = after.tables().get(table);
            assertThat(previous.columns().equals(current.columns())).isTrue();
            if (!Set.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE").contains(table)) {
                assertThat(previous.equals(current)).as("all previous rows unchanged in %s", table).isTrue();
            } else {
                String key = "TB_USER_ACCOUNT".equals(table) ? "ID" : "USER_ACCOUNT_ID";
                assertThat(previous.rows().stream().noneMatch(row -> id(row, key) == accountId)).isTrue();
                assertThat(current.rows().size()).isEqualTo(previous.rows().size() + 1);
                assertThat(current.rows().stream().filter(row -> id(row, key) != accountId).toList().equals(previous.rows()))
                        .as("all previous identities, exact hashes, profiles, NULLs and fields remain equal").isTrue();
            }
        });
        var account = after.tables().get("TB_USER_ACCOUNT").rows().stream().filter(row -> id(row, "ID") == accountId).toList();
        assertThat(account.size()).isEqualTo(1);
        var values = account.getFirst().values();
        assertThat(NAME.equals(values.get("NAME").value()) && "Delivery Person".equals(values.get("FULL_NAME").value())
                && EMAIL.equals(values.get("EMAIL").value())
                && Arrays.equals("delivery.email@example.test".getBytes(StandardCharsets.UTF_8), (byte[]) values.get("EMAIL_KEY").value())
                && "+5511999999999".equals(values.get("PHONE").value())
                && LocalDate.of(2000, 2, 29).equals(values.get("BIRTH_DATE").value())).as("only the accepted registration data").isTrue();
        Object stored = values.get("PASSWORD_HASH").value();
        assertThat(stored instanceof String hash && hash.length() == 97
                && hash.startsWith("$argon2id$v=19$m=19456,t=2,p=1$") && !PASSWORD.equals(hash))
                .as("approved new-account profile, no raw secret").isTrue();
        var roles = after.tables().get("TB_USER_ACCOUNT_ROLE").rows().stream()
                .filter(row -> id(row, "USER_ACCOUNT_ID") == accountId).toList();
        assertThat(roles.size() == 1 && "CLIENT".equals(roles.getFirst().values().get("ROLE").value())).isTrue();
        // DATE remains a physical DATE, not merely a matching display or a timezone-derived timestamp.
        assertThat(after.tables().get("TB_USER_ACCOUNT").columns().stream()
                .anyMatch(column -> column.name().equals("BIRTH_DATE") && column.jdbcType() == Types.DATE)).isTrue();
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select birth_date from tb_user_account where id=?")) {
            statement.setLong(1, accountId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next() && LocalDate.of(2000, 2, 29).equals(rows.getObject(1, LocalDate.class)) && !rows.next()).isTrue();
            }
        }
    }

    private static long id(Row row, String column) { return ((Number) row.values().get(column).value()).longValue(); }

    enum Rejection { INVALID_PHONE, NAME_CONFLICT }
    enum Delivery { FORWARD, SUPPRESS_AFTER_COMMIT }
    enum PreCommitFailure {
        AFTER_ROLE(Point.ROLE_INSERT_RETURNED, TransactionSynchronization.STATUS_ROLLED_BACK),
        BEFORE_JDBC_COMMIT(Point.JDBC_COMMIT_BEFORE_DELEGATE, TransactionSynchronization.STATUS_UNKNOWN);
        final Point point;
        final int springCompletion;
        PreCommitFailure(Point point, int springCompletion) { this.point = point; this.springCompletion = springCompletion; }
    }

    /** Explicit lite config, isolated to the two Effects children; no component-scanned hook. */
    @Profile("test")
    public static class EffectsDeliveryConfig {
        @Bean public EffectsDeliveryControls customerRegistrationEffectsDeliveryControls() { return new EffectsDeliveryControls(); }

        @Bean
        public FilterRegistrationBean<OncePerRequestFilter> customerRegistrationEffectsDeliveryFilter(EffectsDeliveryControls controls) {
            var filter = new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                    String attempt = request.getHeader(CustomerRegistrationObservationConfig.HEADER);
                    var control = attempt == null ? null : controls.pending.get(attempt);
                    if (control == null) { chain.doFilter(request, response); return; }
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    if (!"POST".equals(request.getMethod()) || !"/customers".equals(path)) {
                        throw new ServletException("Effects delivery control requires its own registration request");
                    }
                    var buffered = new ContentCachingResponseWrapper(response);
                    chain.doFilter(request, buffered);
                    control.upstreamStatus = buffered.getStatus();
                    control.originalBody = buffered.getContentAsByteArray();
                    control.noAuthenticationHeaders = List.of("Set-Cookie", "Authorization", "Location").stream()
                            .allMatch(header -> buffered.getHeaders(header).isEmpty());
                    control.commitVerified = committed(control.attempt.events());
                    if (!control.commitVerified || control.upstreamStatus != 201 || control.originalBody.length == 0
                            || response.isCommitted()) {
                        throw new ServletException("Effects delivery action withheld: original committed confirmation was not proved");
                    }
                    assertOriginalCommit(control.attempt.events());
                    control.beforeDeliverySequence = control.attempt.events().getLast().sequence();
                    control.reached = true;
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
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            return registration;
        }
    }

    public static final class EffectsDeliveryControls {
        private final Map<String, EffectsDeliveryControl> pending = new ConcurrentHashMap<>();
    }

    private static final class EffectsDeliveryControl {
        private final Attempt attempt;
        private final boolean suppress;
        private volatile boolean reached;
        private volatile boolean commitVerified;
        private volatile boolean noAuthenticationHeaders;
        private volatile int upstreamStatus;
        private volatile byte[] originalBody = new byte[0];
        private volatile int deliveredBodyBytes;
        private volatile int suppressedBodyBytes;
        private volatile long beforeDeliverySequence;
        private EffectsDeliveryControl(Attempt attempt, boolean suppress) { this.attempt = attempt; this.suppress = suppress; }
        @Override public String toString() { return "EffectsDeliveryControl[redacted]"; }
    }
}
