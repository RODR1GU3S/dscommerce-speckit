package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Attempt;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.SqlScope;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.OrderRow;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RootHttpContext
@Import(PaymentRegistrationObservationConfig.class)
class PaymentInputHttpIntegrationTest extends PaymentInputScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T015 authorship only. The original service, security chain and persistence remain in these HTTP contexts. */
abstract class PaymentInputScenarios extends PaymentRegistrationHttpTestSupport {
    @Autowired private Observation observation;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    private PaymentRegistrationTestFixture fixture;
    private PaymentRegistrationDatabaseProbe database;
    private Snapshot cleanupBaseline;
    private OrderManifest target;
    private String bearer;
    private Instant reference, occurrence;

    @BeforeEach
    void prepareOneCommittedTargetAndCanaryWithRealAdminLogin() {
        cleanupBaseline = null;
        fixture = new PaymentRegistrationTestFixture(context);
        database = new PaymentRegistrationDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        cleanupBaseline = database.snapshot();
        Account admin = fixture.createAccount("t015-admin-" + UUID.randomUUID(), "t015-disposable-password",
                Set.of(UserRole.ADMIN));
        var buyer = fixture.prepareBuyer();
        var product = fixture.createProduct("T015 current product", new BigDecimal("12.00"));
        bearer = realHttpLogin(admin.id(), admin.name(), admin.password());
        reference = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        clock.setInstant(reference);
        occurrence = reference.minusSeconds(3600);
        target = fixture.createOrder(buyer, reference.minusSeconds(7200),
                List.of(new ItemInput(product.id(), 2, "10.00", "20.00")), "20.00");
        fixture.createOrder(admin, reference.minusSeconds(10800),
                List.of(new ItemInput(product.id(), 1, "8.00", "8.00")), "8.00");
        requirePaymentServiceProxy();
    }

    @AfterEach
    void cleanOnlyOwnedDataAndRequireTheCompleteBaseline() {
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
            bearer = null;
        }
    }

    @ParameterizedTest(name = "invalid JSON document {index}")
    @MethodSource("com.devsuperior.dscommerce.controllers.PaymentInputCases#invalidDocuments")
    void rejectsMissingNullWrongTypeExtraDuplicateAndTrailingJsonBeforeBusiness(String input) throws Exception {
        assertRejected(path(target.id()), input, 400, "Invalid payment data", true);
    }

    @ParameterizedTest(name = "invalid R3 occurrence {index}")
    @MethodSource("com.devsuperior.dscommerce.controllers.PaymentInputCases#invalidOccurrences")
    void rejectsEveryInvalidR3GrammarCalendarRangeAndPrecisionBeforeBusiness(String text) throws Exception {
        assertRejected(path(target.id()), "{\"occurredAt\":" + mapper.writeValueAsString(text) + "}",
                400, "Invalid payment data", true);
    }

    @ParameterizedTest(name = "valid R3 grammar reaches business {index}")
    @MethodSource("com.devsuperior.dscommerce.controllers.PaymentInputCases#acceptedOccurrences")
    void allValidR3FormsReachTheRealServiceRatherThanFailingInputParsing(String text, String canonical) throws Exception {
        // Absent root deliberately precedes the business time window. Years 9999/2400 are grammar controls,
        // not temporally eligible settlements; exact DTO binding is also asserted in the MockMvc matrix.
        assertThat(Instant.parse(canonical).getNano() % 1_000_000).isZero();
        assertThat(database.snapshot().previous().data().orders()).noneMatch(row -> row.id() == Long.MAX_VALUE);
        assertRejected(path(Long.MAX_VALUE), "{\"occurredAt\":" + mapper.writeValueAsString(text) + "}",
                404, "Order not found", false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid", "9223372036854775808", "-9223372036854775809", "1.0", "1e2"})
    void selectedPaymentRouteRejectsNonpositiveMalformedOrOutOfRangeIds(String id) throws Exception {
        assertRejected("/admin/orders/" + id + "/payments", body(), 400, "Invalid payment data", true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?amount=1", "?buyerId=1", "?occurredAt=ignored", "?x=1&x=2", "?private=T015-canary"})
    void everyQueryParameterIsInvalidAndExcludedFromTheSafePath(String query) throws Exception {
        assertRejected(path(target.id()) + query, body(), 400, "Invalid payment data", true);
    }

    @Test
    void absentPositiveIdentityIsBusiness404WithoutCreatingOrChangingAnyRow() throws Exception {
        assertThat(database.snapshot().previous().data().orders()).noneMatch(row -> row.id() == Long.MAX_VALUE);
        assertRejected(path(Long.MAX_VALUE), body(), 404, "Order not found", false);
    }

    @Test
    void paidWithoutPaymentIsIncompatibleState409AndIsNeverAutomaticallyRepaired() throws Exception {
        assertNoExternalTransaction();
        var setup = new TransactionTemplate(transactions);
        setup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        setup.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        setup.executeWithoutResult(status -> {
            assertThat(status.isNewTransaction()).isTrue();
            assertThat(jdbc.update("update tb_order set status = ? where id = ?", "PAID", target.id())).isEqualTo(1);
        });
        var prepared = database.snapshot();
        assertThat(prepared.payments()).noneMatch(row -> row.orderId() == target.id());
        assertThat(prepared.previous().data().orders()).contains(
                new OrderRow(target.id(), target.buyerId(), target.createdAt(), "PAID"));
        // The deliberately incompatible committed preparation belongs to the baseline, not the POST.
        assertRejected(path(target.id()), body(), 409, "Order is not awaiting payment", false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/orders/payments", "/admin/orders/1", "/admin/orders/1/payment"})
    void omittedOrDifferentOperationSegmentsAreRouting404NotInputValidationProof(String route) throws Exception {
        var before = database.snapshot();
        try (var window = observation.openWindow(java.time.Duration.ofSeconds(10))) {
            var attempt = window.newRequest();
            var response = send(request(attempt, route, body()));
            attempt.awaitFinished();
            assertThat(response.statusCode()).isEqualTo(404);
            assertLocalBoundaryOnly(attempt);
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());
            // No assertion of the payment envelope here: no payment controller was selected.
        }
    }

    @Test
    void positiveSelectedRouteControlConservesEquivalentOffsetAndExtraZeroMilliseconds() throws Exception {
        var text = occurrence.atOffset(ZoneOffset.ofHours(3))
                .format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'000000'XXX"));
        var before = database.snapshot();
        try (var window = observation.openWindow(java.time.Duration.ofSeconds(10))) {
            var attempt = window.newRequest();
            var response = send(request(attempt, path(target.id()), "{\"occurredAt\":\"" + text + "\"}"));
            attempt.awaitFinished();
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.headers().allValues("Location")).isEmpty();
            assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
            var canonical = new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(occurrence);
            var expected = mapper.createObjectNode().put("orderId", target.id())
                    .put("occurredAt", canonical).put("status", "PAID");
            var json = mapper.readTree(response.body());
            // Parse both trees alike: integer node width must not change exact JSON equality.
            assertThat(json).isEqualTo(mapper.readTree(expected.toString()));
            assertThat(json.get("orderId").isIntegralNumber()).isTrue();
            attempt.assertOwnedTransactionObserved();
            assertThat(attempt.reference()).isEqualTo(reference);
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
        }
    }

    private void assertRejected(String route, String input, int status, String message, boolean local) throws Exception {
        var before = database.snapshot();
        try (var window = observation.openWindow(java.time.Duration.ofSeconds(10))) {
            var attempt = window.newRequest();
            var response = send(request(attempt, route, input));
            attempt.awaitFinished();
            assertError(response, route, status, message);
            if (local) {
                assertLocalBoundaryOnly(attempt);
            } else {
                attempt.assertOwnedTransactionObserved();
                assertThat(attempt.reference()).isEqualTo(reference);
                assertThat(attempt.proxyFailed()).isTrue();
                assertThat(attempt.proxyReturned()).isFalse();
                assertSelectOnly(attempt);
                var bodySql = attempt.sql().stream().filter(event -> event.scope() == SqlScope.SERVICE_BODY)
                        .map(event -> event.sql().toLowerCase(Locale.ROOT)).toList();
                assertThat(bodySql.stream().filter(sql -> sql.contains("for update"))).hasSize(status == 404 ? 1 : 2);
                assertThat(bodySql).noneSatisfy(sql -> assertThat(sql).containsPattern("\\b(?:tb_order_item|tb_product)\\b"));
            }
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());
        }
    }

    private void assertLocalBoundaryOnly(Attempt attempt) {
        assertThat(attempt.clockCalls()).isZero();
        assertThat(attempt.proxyReturned()).isFalse();
        assertThat(attempt.proxyFailed()).isFalse();
        assertThat(attempt.sql()).allSatisfy(event -> {
            assertThat(event.scope()).isEqualTo(SqlScope.OUTSIDE_SERVICE);
            assertThat(event.sql().toLowerCase(Locale.ROOT))
                    .doesNotContainPattern("\\b(?:tb_order|tb_order_item|tb_payment|tb_product|tb_category|tb_product_category)\\b");
        });
        assertSelectOnly(attempt);
    }

    private void assertSelectOnly(Attempt attempt) {
        assertThat(attempt.sql()).allSatisfy(event ->
                assertThat(event.sql().strip().toLowerCase(Locale.ROOT)).startsWith("select "));
        // Templates inspected by Hibernate, complemented by independent committed snapshots; not JDBC execution tracing.
    }

    private void assertError(HttpResponse<byte[]> response, String route, int status, String message) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().allValues("Location")).isEmpty();
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        var expected = mapper.createObjectNode().put("status", status)
                .put("error", HttpStatus.valueOf(status).getReasonPhrase()).put("message", message)
                .put("path", expectedContextPath() + route.split("\\?", 2)[0]);
        assertThat(mapper.readTree(response.body())).isEqualTo(expected);
    }

    private HttpRequest request(Attempt attempt, String route, String input) {
        return jsonRequest(route).header("Authorization", "Bearer " + bearer)
                .header(PaymentRegistrationObservationConfig.HEADER, attempt.id())
                .POST(HttpRequest.BodyPublishers.ofString(input)).build();
    }

    private String path(long id) { return "/admin/orders/" + id + "/payments"; }
    private String body() { return mapper.createObjectNode().put("occurredAt", occurrence.toString()).toString(); }
}

/** Approved R3/closed-document oracles shared only by the new T015 tests; no implementation predicate is called. */
final class PaymentInputCases {
    static final String OCCURRENCE = "2026-10-21T11:00:00.123Z";
    static final String GOOD = "{\"occurredAt\":\"" + OCCURRENCE + "\"}";

    private PaymentInputCases() { }

    static Stream<String> invalidDocuments() {
        return Stream.of("", " ", "null", "[]", "[" + GOOD + "]", "true", "42", "1.25", "\"text\"",
                "{}", "{\"occurredAt\":null}", "{\"occurredAt\":42}", "{\"occurredAt\":1.25}",
                "{\"occurredAt\":true}", "{\"occurredAt\":false}", "{\"occurredAt\":[]}", "{\"occurredAt\":{}}",
                GOOD.replace("}", ",\"amount\":\"0.01\"}"), GOOD.replace("}", ",\"buyer\":1}"),
                GOOD.replace("}", ",\"buyerId\":1}"), GOOD.replace("}", ",\"createdAt\":null}"),
                GOOD.replace("}", ",\"items\":[]}"), GOOD.replace("}", ",\"price\":\"0.01\"}"),
                GOOD.replace("}", ",\"status\":\"PAID\"}"), GOOD.replace("}", ",\"orderId\":1}"),
                GOOD.replace("}", ",\"reference\":null}"), "{\"occurredat\":null}",
                "{\"occurredAt\":null,\"occurredAt\":\"" + OCCURRENCE + "\"}",
                GOOD.replace("}", ",\"occurredAt\":\"" + OCCURRENCE + "\"}"),
                GOOD.replace("}", ",\"occurred\\u0041t\":null}"),
                GOOD + " {}", GOOD + " []", GOOD + " true", GOOD + " null", GOOD + " garbage",
                "{", "{\"occurredAt\":", "{\"occurredAt\":\"unterminated", GOOD.substring(0, GOOD.length() - 1));
    }

    static Stream<String> invalidOccurrences() {
        return Stream.of(
                "", " ", "2026-10-21", "2026-10-21T11:00Z", "2026-10-21T11:00:00",
                "2026-10-21t11:00:00Z", "2026-10-21T11:00:00z", "2026-10-21 11:00:00Z",
                " 2026-10-21T11:00:00Z", "2026-10-21T11:00:00Z ", "2026-10-21T11:00:00Z\n",
                "2026-10-21T11:00:00\tZ", "2026-10-21T11:00:00-00:00",
                "2026-10-21T11:00:00+18:01", "2026-10-21T11:00:00-18:01",
                "2026-10-21T11:00:00+19:00", "2026-10-21T11:00:00-19:00",
                "2026-10-21T11:00:00+03", "2026-10-21T11:00:00+0300",
                "2026-10-21T11:00:00+03:00:00", "2026-10-21T11:00:00+00:60",
                "2026-10-21T24:00:00Z", "2026-10-21T11:60:00Z", "2016-12-31T23:59:60Z",
                "2026-02-29T00:00:00Z", "1900-02-29T00:00:00Z", "2026-04-31T00:00:00Z",
                "2026-00-01T00:00:00Z", "2026-13-01T00:00:00Z", "2026-01-00T00:00:00Z",
                "2026-01-32T00:00:00Z", "2026-1-01T00:00:00Z", "2026-01-1T00:00:00Z",
                "0000-01-01T00:00:00Z", "-0001-01-01T00:00:00Z", "+10000-01-01T00:00:00Z",
                "0001-01-01T00:00:00+00:01", "9999-12-31T23:59:59.999-00:01",
                "2026-10-21T11:00:00.Z", "2026-10-21T11:00:00,123Z",
                "2026-10-21T11:00:00.0001Z", "2026-10-21T11:00:00.000000001Z",
                "2026-10-21T11:00:00.1234Z", "2026-10-21T11:00:00.123000001Z",
                "2026-10-21T11:00:00.999999999Z", "2026-10-21T11:00:00.1230000000Z",
                "2026-10-21T11:00:00.0000000000Z", "2026-10-21T11:00:00Zsuffix");
    }

    static Stream<Arguments> acceptedOccurrences() {
        return Stream.of(
                Arguments.of("2026-10-21T11:00:00Z", "2026-10-21T11:00:00.000Z"),
                Arguments.of("2026-10-21T11:00:00.0Z", "2026-10-21T11:00:00.000Z"),
                Arguments.of("2026-10-21T11:00:00.00Z", "2026-10-21T11:00:00.000Z"),
                Arguments.of("2026-10-21T11:00:00.000Z", "2026-10-21T11:00:00.000Z"),
                Arguments.of("2026-10-21T11:00:00.1Z", "2026-10-21T11:00:00.100Z"),
                Arguments.of("2026-10-21T11:00:00.12Z", "2026-10-21T11:00:00.120Z"),
                Arguments.of(OCCURRENCE, OCCURRENCE),
                Arguments.of("2026-10-21T11:00:00.1230Z", OCCURRENCE),
                Arguments.of("2026-10-21T11:00:00.12300Z", OCCURRENCE),
                Arguments.of("2026-10-21T11:00:00.123000Z", OCCURRENCE),
                Arguments.of("2026-10-21T11:00:00.1230000Z", OCCURRENCE),
                Arguments.of("2026-10-21T11:00:00.12300000Z", OCCURRENCE),
                Arguments.of("2026-10-21T11:00:00.123000000Z", OCCURRENCE),
                Arguments.of("2026-10-21T11:00:00.123+00:00", OCCURRENCE),
                Arguments.of("2026-10-21T08:00:00.123000000-03:00", OCCURRENCE),
                Arguments.of("2026-10-21T16:45:00.123+05:45", OCCURRENCE),
                Arguments.of("2026-10-22T05:00:00.123+18:00", OCCURRENCE),
                Arguments.of("2026-10-20T17:00:00.123-18:00", OCCURRENCE),
                Arguments.of("0001-01-01T00:00:00Z", "0001-01-01T00:00:00.000Z"),
                Arguments.of("0001-01-01T18:00:00+18:00", "0001-01-01T00:00:00.000Z"),
                Arguments.of("9999-12-31T23:59:59.999Z", "9999-12-31T23:59:59.999Z"),
                Arguments.of("9999-12-31T05:59:59.999-18:00", "9999-12-31T23:59:59.999Z"),
                Arguments.of("2000-02-29T23:59:59.999Z", "2000-02-29T23:59:59.999Z"),
                Arguments.of("2400-02-29T00:00:00.000000000Z", "2400-02-29T00:00:00.000Z"));
    }
}
