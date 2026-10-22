package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Attempt;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Gate;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Phase;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.SqlScope;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Window;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.OrderRow;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.AccessAccounts;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RootHttpContext
@Import(PaymentRegistrationObservationConfig.class)
class PaymentRegistrationHttpIntegrationTest extends PaymentRegistrationScenarioSupport {
    @Override protected String expectedContextPath() { return ""; }
}

/** Same scenarios inherited by ROOT and /api; no imports of still-absent production payment types. */
abstract class PaymentRegistrationScenarioSupport extends PaymentRegistrationHttpTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final DateTimeFormatter UTC_MILLISECONDS = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    @Autowired private Observation observation;
    private PaymentRegistrationTestFixture fixture;
    private PaymentRegistrationDatabaseProbe database;
    private OwnOrderQueryDatabaseProbe previous;
    private AccessAccounts accounts;
    private Account otherBuyer;
    private ProductData first, second;
    private Instant reference, createdAt, occurredAt;
    private PaymentRegistrationDatabaseProbe.Snapshot cleanupBaseline;
    private final Map<Long, String> tokens = new HashMap<>();

    @BeforeEach
    void prepareCommittedScenarioAndRealHttpCredentials() {
        // Assign before any inserts, so @AfterEach can clean even a partially prepared scenario.
        fixture = new PaymentRegistrationTestFixture(context);
        var url = context.getEnvironment().getRequiredProperty("spring.datasource.url");
        database = new PaymentRegistrationDatabaseProbe(url);
        previous = new OwnOrderQueryDatabaseProbe(url);
        // T013: the real schema now exists; require all eight tables before fixture writes.
        cleanupBaseline = database.snapshot();
        accounts = fixture.prepareAccessAccounts();
        otherBuyer = fixture.prepareBuyer();
        first = fixture.createProduct("Produto A atual", new BigDecimal("10.00"));
        second = fixture.createProduct("Produto B atual", new BigDecimal("7.50"));
        for (var account : List.of(accounts.admin(), accounts.both(), accounts.client(), accounts.none())) {
            tokens.put(account.id(), realHttpLogin(account.id(), account.name(), account.password()));
        }
        // JWTs use wall time for issuance and the injected Clock for conversion. Keep both valid.
        reference = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        clock.setInstant(reference);
        createdAt = reference.minusSeconds(7200);
        occurredAt = reference.minusSeconds(3600);
        // Real authenticated controls distinguish failed credentials/setup from a missing POST operation.
        for (var account : List.of(accounts.admin(), accounts.both())) {
            var response = send(jsonRequest("/admin/products?page=0&size=2")
                    .header("Authorization", "Bearer " + token(account)).GET().build());
            assertThat(response.statusCode()).as("Existing admin GET accepts the real fixture credential").isEqualTo(200);
        }
        // An owned, non-target purchase must also survive every attempted settlement unchanged.
        fixture.createOrder(accounts.client(), createdAt.minusSeconds(60),
                List.of(new ItemInput(second.id(), 1, "8.00", "8.00")), "8.00");
    }

    @AfterEach
    void cleanOnlyOwnedRowsAfterRequestsAndObservationWindows() {
        try {
            if (fixture != null) {
                fixture.close();
                if (cleanupBaseline != null) {
                    PaymentRegistrationDatabaseProbe.assertUnchanged(cleanupBaseline, database.snapshot());
                    System.out.println("T013_CLEANUP context=" + contextLabel() + " eightTablesRestored=true");
                }
            }
        } finally {
            fixture = null;
            cleanupBaseline = null;
            tokens.clear();
        }
    }

    @Test
    void us11AdminSettlesForeignOrderWithExactlyThreeConfirmationFieldsAndHistorical4250() throws Exception {
        var order = basket(otherBuyer);
        assertAccepted(order, accounts.admin(), occurredAt, body(occurredAt));
    }

    @ParameterizedTest(name = "US1.2 profile={0}, own={1}")
    @CsvSource({"ADMIN,true", "BOTH,true", "BOTH,false"})
    void us12OwnAdminWithoutClientAndBothProfilesUseTheSameSettlementRules(String profile, boolean own) throws Exception {
        var payer = profile.equals("ADMIN") ? accounts.admin() : accounts.both();
        var order = singleLine(own ? payer : otherBuyer);
        assertAccepted(order, payer, occurredAt, body(occurredAt));
    }

    @Test
    void us13CatalogPriceTwelveDoesNotReplaceHistoricalTwentyOrOrderData() throws Exception {
        var order = singleLine(otherBuyer);
        var setup = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        setup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        setup.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        setup.executeWithoutResult(status -> assertThat(context.getBean(JdbcTemplate.class).update(
                "update tb_product set price = ? where id = ?", new BigDecimal("12.00"), first.id())).isEqualTo(1));
        assertAccepted(order, accounts.admin(), occurredAt, body(occurredAt));
    }

    @Test
    void us14EquivalentOffsetWithExtraZerosPreservesOccurrenceRatherThanBusinessReference() throws Exception {
        var order = singleLine(otherBuyer);
        var wire = occurredAt.atOffset(ZoneOffset.ofHours(3)).format(
                DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'000000'XXX"));
        assertThat(occurredAt).isNotEqualTo(reference);
        assertAccepted(order, accounts.admin(), occurredAt,
                mapper.createObjectNode().put("occurredAt", wire).toString());
    }

    @Test
    void us15OccurrenceEqualToCreationIsAccepted() throws Exception {
        var order = singleLine(otherBuyer);
        assertAccepted(order, accounts.admin(), createdAt, body(createdAt));
    }

    @Test
    void us16OccurrenceEqualToInitialReferenceSurvivesClockAdvanceBeforeCompletion() throws Exception {
        var order = singleLine(otherBuyer);
        var before = previous.snapshot();
        try (var window = observation.openWindow(WAIT)) {
            var attempt = window.newRequest();
            try (var gate = attempt.pauseAt(Phase.REFERENCE_READ)) {
                var request = paymentRequest(window, attempt, order.id(), token(accounts.admin()), body(reference));
                var future = window.submit(() -> send(request));
                awaitReferenceOrReportHttpOutcome(gate, future, window);
                assertThat(attempt.reference()).isEqualTo(reference);
                clock.advance(Duration.ofMinutes(5));
                gate.release();
                var response = window.awaitResult(future);
                assertConfirmation(response, order.id(), reference);
                attempt.awaitFinished();
                attempt.assertOwnedTransactionObserved();
                assertThat(attempt.proxyReturned()).isTrue();
                assertThat(clock.instant()).isAfter(reference);
                assertCommittedSettlement(before, order, reference);
                assertSuccessfulAttemptEvidence(attempt, order);
            }
        }
    }

    @ParameterizedTest(name = "basic authentication: {0}")
    @ValueSource(strings = {"ABSENT", "MALFORMED"})
    void basicAuthenticationRejectsMissingAndMalformedBearerWithSafePaymentEnvelope(String credential) throws Exception {
        var order = singleLine(otherBuyer);
        assertRejected(order, credential.equals("ABSENT") ? null : "invalid-feature008-bearer",
                body(occurredAt), 401, "Unauthorized", "Authentication required or invalid");
    }

    @ParameterizedTest(name = "basic non-admin: {0}")
    @ValueSource(strings = {"CLIENT", "NONE"})
    void basicAuthorizationRejectsClientAndNoProfileWithoutDisclosingOrderData(String profile) throws Exception {
        var order = singleLine(otherBuyer);
        var payer = profile.equals("CLIENT") ? accounts.client() : accounts.none();
        assertRejected(order, token(payer), body(occurredAt), 403, "Forbidden", "Payment registration not permitted");
    }

    @Test
    void basicClosedRequestRejectsAnAttemptToSupplyAmount() throws Exception {
        var order = basket(otherBuyer);
        var input = mapper.createObjectNode().put("occurredAt", UTC_MILLISECONDS.format(occurredAt))
                .put("amount", "0.01").toString();
        assertRejected(order, token(accounts.admin()), input, 400, "Bad Request", "Invalid payment data");
    }

    @ParameterizedTest(name = "basic temporal window: {0}")
    @ValueSource(strings = {"BEFORE_CREATION", "AFTER_REFERENCE"})
    void basicWindowRejectsInstantsOutsideInclusiveBoundsWithoutChangingHistoricalTables(String side) throws Exception {
        var order = singleLine(otherBuyer);
        var invalid = side.equals("BEFORE_CREATION") ? createdAt.minusMillis(1) : reference.plusMillis(1);
        assertRejected(order, token(accounts.admin()), body(invalid), 400, "Bad Request", "Invalid payment data");
    }

    @Test
    void basicNewAttemptAfterAcceptanceReturns409AndPreservesTheFirstPayment() throws Exception {
        var order = singleLine(otherBuyer);
        assertAccepted(order, accounts.admin(), occurredAt, body(occurredAt));
        var before = database.snapshot();
        try (var window = observation.openWindow(WAIT)) {
            var attempt = window.newRequest();
            var response = send(paymentRequest(window, attempt, order.id(), token(accounts.both()), body(occurredAt)));
            assertError(response, order.id(), 409, "Conflict", "Payment already registered");
            attempt.awaitFinished();
            attempt.assertOwnedTransactionObserved();
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());
        }
    }

    private OrderManifest basket(Account buyer) {
        return fixture.createOrder(buyer, createdAt,
                List.of(new ItemInput(first.id(), 2, "10.00", "20.00"),
                        new ItemInput(second.id(), 3, "7.50", "22.50")), "42.50");
    }

    private OrderManifest singleLine(Account buyer) {
        return fixture.createOrder(buyer, createdAt,
                List.of(new ItemInput(first.id(), 2, "10.00", "20.00")), "20.00");
    }

    private String token(Account account) { return tokens.get(account.id()); }
    private String body(Instant instant) {
        return mapper.createObjectNode().put("occurredAt", UTC_MILLISECONDS.format(instant)).toString();
    }

    private HttpRequest paymentRequest(Window window, Attempt attempt, long orderId, String bearer, String input) {
        var builder = jsonRequest("/admin/orders/" + orderId + "/payments")
                .header(PaymentRegistrationObservationConfig.HEADER, attempt.id());
        if (bearer != null) { builder.header("Authorization", "Bearer " + bearer); }
        return builder.POST(HttpRequest.BodyPublishers.ofString(input)).build();
    }

    private void assertAccepted(OrderManifest order, Account payer, Instant occurrence, String input) throws Exception {
        var before = previous.snapshot();
        try (var window = observation.openWindow(WAIT)) {
            var attempt = window.newRequest();
            var response = send(paymentRequest(window, attempt, order.id(), token(payer), input));
            // Mandatory first: do not turn the initial missing-route RED into a tb_payment setup error.
            assertConfirmation(response, order.id(), occurrence);
            attempt.awaitFinished();
            attempt.assertOwnedTransactionObserved();
            assertThat(attempt.proxyReturned()).isTrue();
            assertThat(attempt.proxyFailed()).isFalse();
            assertThat(attempt.reference()).isEqualTo(reference);
            assertCommittedSettlement(before, order, occurrence);
            assertSuccessfulAttemptEvidence(attempt, order);
        }
    }

    private void assertRejected(OrderManifest order, String bearer, String input, int status,
                                String error, String message) throws Exception {
        // Preserve the original seven-table assertion and complete its planned T013 observation.
        var before = previous.snapshot();
        var completeBefore = database.snapshot();
        try (var window = observation.openWindow(WAIT)) {
            var attempt = window.newRequest();
            var response = send(paymentRequest(window, attempt, order.id(), bearer, input));
            assertError(response, order.id(), status, error, message);
            attempt.awaitFinished();
            OwnOrderQueryDatabaseProbe.assertUnchanged(before, previous.snapshot());
            PaymentRegistrationDatabaseProbe.assertUnchanged(completeBefore, database.snapshot());
            System.out.println("T013_REJECTION context=" + contextLabel() + " status=" + status
                    + " eightTablesUnchanged=true");
        }
    }

    private void assertConfirmation(HttpResponse<byte[]> response, long orderId, Instant occurrence) throws Exception {
        assertStatus(response, 201);
        assertThat(response.headers().allValues("Location")).isEmpty();
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        var json = mapper.readTree(response.body());
        assertFields(json, Set.of("orderId", "occurredAt", "status"));
        assertThat(json.get("orderId").isIntegralNumber()).isTrue();
        assertThat(json.get("orderId").canConvertToLong()).isTrue();
        assertThat(json.get("orderId").longValue()).isEqualTo(orderId);
        assertThat(json.get("occurredAt").isTextual()).isTrue();
        assertThat(json.get("occurredAt").textValue()).isEqualTo(UTC_MILLISECONDS.format(occurrence));
        assertThat(json.get("status").textValue()).isEqualTo("PAID");
    }

    private void assertError(HttpResponse<byte[]> response, long orderId, int status,
                             String error, String message) throws Exception {
        assertStatus(response, status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        assertThat(response.headers().allValues("WWW-Authenticate"))
                .isEqualTo(status == 401 ? List.of("Bearer") : List.of());
        var json = mapper.readTree(response.body());
        assertFields(json, Set.of("status", "error", "message", "path"));
        var expected = mapper.createObjectNode().put("status", status).put("error", error).put("message", message)
                .put("path", expectedContextPath() + "/admin/orders/" + orderId + "/payments");
        assertThat(json.equals(expected)).as("Exact safe payment error envelope").isTrue();
    }

    private void assertStatus(HttpResponse<byte[]> response, int expected) {
        System.out.println("T006_HTTP context=" + (expectedContextPath().isEmpty() ? "root" : "api")
                + " expected=" + expected + " actual=" + response.statusCode());
        assertThat(response.statusCode()).as("POST payment contract HTTP status").isEqualTo(expected);
    }

    private static void assertFields(JsonNode json, Set<String> expected) {
        assertThat(json.isObject()).isTrue();
        var fields = new HashSet<String>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).as("Closed payment JSON fields").isEqualTo(expected);
    }

    private void assertCommittedSettlement(OwnOrderQueryDatabaseProbe.Snapshot before,
                                           OrderManifest order, Instant occurrence) {
        var after = database.snapshot();
        assertThat(after.payments()).containsExactly(new PaymentRow(order.id(), occurrence));
        var expectedOrders = before.data().orders().stream().map(row -> row.id() == order.id()
                ? new OrderRow(row.id(), row.buyerId(), row.createdAt(), "PAID") : row).toList();
        assertThat(after.previous().data().orders()).isEqualTo(expectedOrders);
        assertThat(after.previous().data().items()).isEqualTo(before.data().items());
        // The existing opaque comparison also checks account names/hashes without exposing them.
        assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before, after.previous()))
                .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_order");
        assertThat(after.previous().data().orders().stream().filter(row -> row.id() == order.id()).findFirst())
                .hasValue(new OrderRow(order.id(), order.buyerId(), order.createdAt(), "PAID"));
        var historicalTotal = after.previous().data().items().stream().filter(row -> row.orderId() == order.id())
                .map(row -> row.unitPrice().multiply(BigDecimal.valueOf(row.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(historicalTotal).isEqualByComparingTo(order.total());
    }

    /** SQL inspection is complemented by the independent post-completion snapshot above, never called commit itself. */
    private void assertSuccessfulAttemptEvidence(Attempt attempt, OrderManifest order) {
        assertThat(attempt.proxyFailed()).isFalse();
        var events = attempt.sql().stream().filter(event -> event.scope() == SqlScope.SERVICE_BODY).toList();
        assertThat(events).isNotEmpty().allSatisfy(event -> assertThat(event.transactionActive()).isTrue());
        var statements = events.stream().map(event -> event.sql().toLowerCase(java.util.Locale.ROOT)).toList();
        var locks = statements.stream().filter(sql -> sql.matches("(?s).*\\bfrom\\s+tb_order\\b.*\\bfor\\s+update\\b.*")).toList();
        assertThat(locks).as("Root acquisition and refresh SQL under the owned transaction").hasSize(2);
        assertThat(statements).anySatisfy(sql -> assertThat(sql).contains("from tb_payment"));
        assertThat(statements).anySatisfy(sql -> assertThat(sql).contains("from tb_order_item"));
        assertThat(statements.stream().filter(sql -> sql.contains("insert into tb_payment"))).hasSize(1);
        assertThat(statements.stream().filter(sql -> sql.contains("update tb_order set"))).hasSize(1);
        assertThat(statements).noneSatisfy(sql -> assertThat(sql).containsPattern("\\btb_product\\b"));
        for (var event : events) {
            // Hibernate templates only, no bind values, JWTs or JDBC setup/cleanup statements.
            System.out.println("T013_SQL context=" + contextLabel() + " orderId=" + order.id()
                    + " scope=" + event.scope() + " transactionActive=" + event.transactionActive()
                    + " sql=" + event.sql().replaceAll("\\s+", " ").strip());
        }
        System.out.println("T013_SUCCESS context=" + contextLabel() + " orderId=" + order.id()
                + " ownedReadCommitted=asserted clockCalls=" + attempt.clockCalls()
                + " independentPaymentAndPaid=asserted");
    }

    private String contextLabel() { return expectedContextPath().isEmpty() ? "root" : "api"; }

    private void awaitReferenceOrReportHttpOutcome(Gate gate, Future<HttpResponse<byte[]>> future,
                                                  Window window) throws Exception {
        try { gate.awaitReached(); }
        catch (AssertionError phaseFailure) {
            // Preserve HTTP absence as the primary RED if the absent service cannot reach this gate.
            var response = window.awaitResult(future);
            try { assertStatus(response, 201); }
            catch (AssertionError httpFailure) { httpFailure.addSuppressed(phaseFailure); throw httpFailure; }
            throw phaseFailure; // A 201 without the expected observation remains an instrumentation failure.
        }
    }
}
