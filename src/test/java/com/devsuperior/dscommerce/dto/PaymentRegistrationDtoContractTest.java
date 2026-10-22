package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.entities.OrderStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Boot JSON and local Bean Validation only; no MVC, HTTP, security, clock or persistence. */
@JsonTest
class PaymentRegistrationDtoContractTest {
    private static final String OCCURRENCE = "2026-10-21T11:00:00.123Z";
    private static ValidatorFactory validationFactory;
    private static Validator validator;
    @Autowired private ObjectMapper mapper;
    @Autowired private ApplicationContext context;

    @BeforeAll
    static void openLocalValidation() {
        validationFactory = Validation.buildDefaultValidatorFactory();
        validator = validationFactory.getValidator();
    }

    @AfterAll
    static void closeLocalValidation() {
        validationFactory.close();
    }

    @ParameterizedTest(name = "accepted occurrence: {0}")
    @MethodSource("acceptedOccurrences")
    void acceptsOnlyLosslessOccurrencesAndKeepsTheExactInstant(String text, String canonical) throws Exception {
        var request = readOccurrence(text);
        assertThat(request.occurredAt()).isEqualTo(Instant.parse(canonical));
        assertThat(validator.validate(request)).isEmpty();
        var json = mapper.readTree(mapper.writeValueAsString(request));
        assertThat(fields(json)).containsExactly("occurredAt");
        assertThat(Instant.parse(json.path("occurredAt").textValue())).isEqualTo(request.occurredAt());
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

    @ParameterizedTest(name = "rejected temporal syntax: {0}")
    @ValueSource(strings = {
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
            "2026-10-21T11:00:00.0000000000Z", "2026-10-21T11:00:00Zsuffix"
    })
    void rejectsInvalidGrammarCalendarRangeAndSubmillisecondsWithoutRounding(String text) {
        assertThatThrownBy(() -> readOccurrence(text)).isInstanceOf(JsonProcessingException.class);
    }

    @ParameterizedTest(name = "rejected document: {0}")
    @MethodSource("invalidDocuments")
    void rejectsNonobjectsWrongTypesUnknownOrDuplicateFieldsAndTrailingContent(String json) {
        assertThatThrownBy(() -> mapper.readValue(json, PaymentRegistrationRequestDTO.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    static Stream<String> invalidDocuments() {
        String good = "{\"occurredAt\":\"" + OCCURRENCE + "\"}";
        return Stream.of("", " ", "null", "[]", "[" + good + "]", "true", "42", "1.25", "\"text\"",
                "{\"occurredAt\":42}", "{\"occurredAt\":1.25}", "{\"occurredAt\":true}",
                "{\"occurredAt\":false}", "{\"occurredAt\":[]}", "{\"occurredAt\":{}}",
                good.replace("}", ",\"amount\":\"42.50\"}"),
                good.replace("}", ",\"buyerId\":1}"), good.replace("}", ",\"reference\":null}"),
                good.replace("}", ",\"status\":\"PAID\"}"), "{\"occurredat\":null}",
                "{\"occurredAt\":null,\"occurredAt\":\"" + OCCURRENCE + "\"}",
                good.replace("}", ",\"occurredAt\":\"" + OCCURRENCE + "\"}"),
                good.replace("}", ",\"occurred\\u0041t\":null}"),
                good + " {}", good + " []", good + " true", good + " null", good + " garbage",
                "{", "{\"occurredAt\":", "{\"occurredAt\":\"unterminated", good.substring(0, good.length() - 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"occurredAt\":null}"})
    void leavesMissingOrNullOccurrenceForLocalNotNullBeanValidation(String json) throws Exception {
        var request = mapper.readValue(json, PaymentRegistrationRequestDTO.class);
        assertThat(request.occurredAt()).isNull();
        var violations = validator.validate(request);
        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("occurredAt");
            assertThat(violation.getConstraintDescriptor().getAnnotation()).isInstanceOf(NotNull.class);
        });
        assertThat(validator.validate(new PaymentRegistrationRequestDTO(null))).hasSize(1);
    }

    @ParameterizedTest(name = "canonical confirmation from: {0}")
    @MethodSource("acceptedOccurrences")
    void confirmationContainsExactlyThreeFieldsAndCanonicalUtcMilliseconds(String text, String canonical)
            throws Exception {
        var request = readOccurrence(text);
        var confirmation = new PaymentConfirmationDTO(Long.MAX_VALUE, request.occurredAt(), OrderStatus.PAID);
        var json = mapper.readTree(mapper.writeValueAsString(confirmation));
        assertThat(fields(json)).containsExactlyInAnyOrder("orderId", "occurredAt", "status");
        assertThat(json.path("orderId").isIntegralNumber()).isTrue();
        assertThat(json.path("orderId").longValue()).isEqualTo(Long.MAX_VALUE);
        assertThat(json.path("status").textValue()).isEqualTo("PAID");
        assertThat(json.path("occurredAt").textValue()).isEqualTo(canonical);
        assertThat(Instant.parse(json.path("occurredAt").textValue())).isEqualTo(request.occurredAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000-12-31T23:59:59.999Z", "+10000-01-01T00:00:00Z",
            "2026-10-21T11:00:00.123000001Z", "2026-10-21T11:00:00.000000001Z",
            "+1000000000-12-31T23:59:59.999999999Z", "-1000000000-01-01T00:00:00Z"})
    void confirmationRejectsUnrepresentableInstantsInsteadOfSilentlyTruncating(String text) {
        assertThatThrownBy(() -> new PaymentConfirmationDTO(1L, Instant.parse(text), OrderStatus.PAID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmationRejectsMissingValuesInvalidIdentityAndAnyStatusOtherThanPaid() {
        Instant instant = Instant.parse(OCCURRENCE);
        for (Long id : java.util.Arrays.asList(null, 0L, -1L)) {
            assertThatThrownBy(() -> new PaymentConfirmationDTO(id, instant, OrderStatus.PAID))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new PaymentConfirmationDTO(1L, null, OrderStatus.PAID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentConfirmationDTO(1L, instant, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentConfirmationDTO(1L, instant, OrderStatus.WAITING_PAYMENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paymentReaderFailuresKeepAFixedMessageWithoutEchoingReceivedValues() {
        for (String json : List.of("{\"secret-value\":\"secret-value\"}",
                "{\"occurredAt\":\"secret-value\"}", "{\"occurredAt\":\"secret-value\" garbage")) {
            assertThatThrownBy(() -> mapper.readValue(json, PaymentRegistrationRequestDTO.class))
                    .isInstanceOfSatisfying(JsonProcessingException.class,
                            failure -> assertThat(failure.getOriginalMessage()).isEqualTo("Invalid payment data"));
        }
    }

    @Test
    void paymentSpecificRulesDoNotChangeOtherInstantCoercionOrDocumentPolicies() throws Exception {
        var legacy = mapper.readValue("{\"occurredAt\":\"2026-10-21T11:00:00.123000001Z\","
                + "\"quantity\":\"7\",\"unknown\":true}", UnrelatedInput.class);
        assertThat(legacy.occurredAt()).isEqualTo(Instant.parse("2026-10-21T11:00:00.123000001Z"));
        assertThat(legacy.quantity()).isEqualTo(7);
        var duplicate = mapper.readValue("{\"quantity\":1,\"quantity\":2} {}", UnrelatedInput.class);
        assertThat(duplicate.quantity()).isEqualTo(2);
        assertThat(mapper.writeValueAsString(legacy.occurredAt()))
                .isEqualTo("\"2026-10-21T11:00:00.123000001Z\"");
    }

    @Test
    void existingOwnRepresentationKeepsItsCreationFormatAndExactMoneyStrings() throws Exception {
        String maximum = "99999999999999999.99";
        var own = new OwnOrderSummaryDTO(1L, Instant.parse("2026-10-21T11:00:00Z"),
                OrderStatus.WAITING_PAYMENT, maximum);
        var json = mapper.readTree(mapper.writeValueAsString(own));
        assertThat(fields(json)).containsExactlyInAnyOrder("id", "createdAt", "status", "total");
        assertThat(json.path("createdAt").textValue()).isEqualTo("2026-10-21T11:00:00.000Z");
        assertThat(json.path("total").isTextual()).isTrue();
        assertThat(json.path("total").textValue()).isEqualTo(maximum);
        assertThat(json.path("status").textValue()).isEqualTo("WAITING_PAYMENT");
    }

    @Test
    void jsonSliceHasNoPersistenceClockOrApplicationController() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(EntityManagerFactory.class)).isEmpty();
        assertThat(context.getBeanNamesForType(Clock.class)).isEmpty();
        assertThat(context.getBeansWithAnnotation(RestController.class)).isEmpty();
    }

    private PaymentRegistrationRequestDTO readOccurrence(String text) throws Exception {
        return mapper.readValue("{\"occurredAt\":" + mapper.writeValueAsString(text) + "}",
                PaymentRegistrationRequestDTO.class);
    }

    private static Set<String> fields(com.fasterxml.jackson.databind.JsonNode json) {
        Set<String> names = new HashSet<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }

    record UnrelatedInput(Instant occurredAt, Integer quantity) { }
}
