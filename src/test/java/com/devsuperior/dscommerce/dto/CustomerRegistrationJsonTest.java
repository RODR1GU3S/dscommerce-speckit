package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.services.RegistrationAttemptReference;
import com.devsuperior.dscommerce.validation.CustomerRegistrationValidator;
import com.devsuperior.dscommerce.validation.ValidCustomerRegistration;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** T033 authorship; T034 owns execution. Boot's real JSON slice, no replacement reader or HTTP. */
@JsonTest
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationJsonTest {

    private static final String INVALID_INPUT = "Invalid customer registration data";
    private static final String[] VALUES = {
            "JSON_NAME_CANARY_009", "JSON_FULL_NAME_CANARY_009", "JSON_EMAIL_CANARY_009@example.test",
            "+5511999900033", "2000-02-29", "JSON_PASSWORD_CANARY_009"
    };
    private static final String[] TOKENS = Arrays.stream(VALUES).map(value -> "\"" + value + "\"").toArray(String[]::new);
    private static final List<String> PRIVATE_VALUES = List.of(
            VALUES[0], VALUES[1], VALUES[2], VALUES[3], VALUES[4], VALUES[5],
            "DUPLICATE_VALUE_CANARY_009", "UNKNOWN_FIELD_CANARY_009", "UNKNOWN_VALUE_CANARY_009",
            "STORED_HASH_CANARY_009", "TOKEN_CANARY_009", "REFERENCE_CANARY_009",
            "ROOT_VALUE_CANARY_009", "TRAILING_VALUE_CANARY_009", "TRAILING_TOKEN_CANARY_009");
    private static ValidatorFactory validationFactory;
    private static Validator validator;

    @Autowired private ObjectMapper mapper;
    @Autowired private ApplicationContext context;

    @BeforeAll
    static void prepareRealLocalValidationWithAnExplicitReference() {
        var configuration = Validation.byProvider(HibernateValidator.class).configure();
        var defaults = configuration.getDefaultConstraintValidatorFactory();
        var reference = RegistrationAttemptReference.fixed(Instant.parse("2030-01-01T00:00:00Z"));
        // Only supply the real validator's existing constructor dependencies. No rule or result is mocked.
        validationFactory = configuration.constraintValidatorFactory(new ConstraintValidatorFactory() {
            @Override
            public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> type) {
                if (CustomerRegistrationValidator.class.equals(type)) {
                    return type.cast(new CustomerRegistrationValidator(reference,
                            Objects.requireNonNull(validator, "Initialize the local validator first")));
                }
                return defaults.getInstance(type);
            }

            @Override
            public void releaseInstance(ConstraintValidator<?, ?> instance) {
                if (!(instance instanceof CustomerRegistrationValidator)) { defaults.releaseInstance(instance); }
            }
        }).buildValidatorFactory();
        validator = validationFactory.getValidator();
    }

    @AfterAll
    static void closeLocalValidation() {
        if (validationFactory != null) { validationFactory.close(); }
        validator = null;
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("acceptedDocuments")
    void sixStringsBindWithoutCoercionTrimmingOrNormalization(AcceptedDocument vector) {
        assertRawValues(vector.expected(), decode(vector.json(), CustomerRegistrationRequestDTO.class));
    }

    static Stream<AcceptedDocument> acceptedDocuments() {
        String raw = "{\"name\":\" Raw.Name \",\"fullName\":\" \\u00C1na  e\\u0301 \\uD83D\\uDE00 \","
                + "\"email\":\" Raw@Example.test \",\"phone\":\" +12345678 \",\"birthDate\":\"not-a-date\","
                + "\"password\":\" \\u0000Pass\\uD83D\\uDE00e\\u0301 \\t\\r\\n\"}";
        return Stream.of(
                new AcceptedDocument("six-strings", document(null, null, false), VALUES),
                new AcceptedDocument("raw-sequences-and-json-escapes", raw,
                        new String[]{" Raw.Name ", " \u00C1na  e\u0301 \uD83D\uDE00 ", " Raw@Example.test ",
                                " +12345678 ", "not-a-date", " \u0000Pass\uD83D\uDE00e\u0301 \t\r\n"}),
                new AcceptedDocument("reordered-escaped-property-names", reversedEscapedDocument(), VALUES),
                new AcceptedDocument("trailing-json-whitespace-only", document(null, null, false) + " \r\n\t", VALUES));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("missingOrNullFields")
    void missingOrNullFieldsRemainRawAndFailRealLocalValidation(PresenceCase vector) {
        var expected = VALUES.clone();
        expected[vector.field().ordinal()] = null;
        var request = decode(document(vector.field(), "null", vector.missing()), CustomerRegistrationRequestDTO.class);
        assertRawValues(expected, request);
        assertLocalValidity(request, false);
        assertRawValues(expected, request);
    }

    static Stream<PresenceCase> missingOrNullFields() {
        return Arrays.stream(Field.values()).flatMap(field -> Stream.of(
                new PresenceCase(field, true), new PresenceCase(field, false)));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("wrongFieldTypes")
    void eachFieldRejectsNumbersBooleansArraysAndObjectsInsteadOfCoercingThem(RejectedDocument vector) {
        assertRejected(vector);
    }

    static Stream<RejectedDocument> wrongFieldTypes() {
        String[] types = {"integer", "decimal", "true", "false", "array", "object"};
        String[] tokens = {"42", "1.25", "true", "false", "[]", "{}"};
        var cases = new ArrayList<RejectedDocument>();
        for (var field : Field.values()) {
            for (int index = 0; index < tokens.length; index++) {
                cases.add(rejected("type-" + field + "-" + types[index], document(field, tokens[index], false)));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("nonobjectDocuments")
    void rejectsEmptyInputNullAndEveryNonobjectRoot(RejectedDocument vector) {
        assertRejected(vector);
    }

    static Stream<RejectedDocument> nonobjectDocuments() {
        return Stream.of(
                new RejectedDocument("empty-input", "", false),
                new RejectedDocument("whitespace-input", " \r\n\t", false),
                rejected("root-null", "null"), rejected("root-empty-array", "[]"),
                rejected("root-object-in-array", "[" + document(null, null, false) + "]"),
                rejected("root-true", "true"), rejected("root-false", "false"),
                rejected("root-integer", "42"), rejected("root-decimal", "1.25"),
                rejected("root-string", "\"ROOT_VALUE_CANARY_009\""));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("extraProperties")
    void rejectsAllAdditionalPropertiesIncludingIdentityPrivilegesHashesAndTokens(RejectedDocument vector) {
        assertRejected(vector);
    }

    static Stream<RejectedDocument> extraProperties() {
        return Stream.of(
                extra("id", "id", "7"), extra("roles-client", "roles", "[\"CLIENT\"]"),
                extra("roles-admin", "roles", "[\"ADMIN\"]"), extra("role-admin", "role", "\"ADMIN\""),
                extra("profile-admin", "profile", "\"ADMIN\""), extra("admin-property", "ADMIN", "true"),
                extra("hash", "hash", "\"STORED_HASH_CANARY_009\""),
                extra("password-hash", "passwordHash", "\"STORED_HASH_CANARY_009\""),
                extra("token", "token", "\"TOKEN_CANARY_009\""),
                extra("access-token", "accessToken", "\"TOKEN_CANARY_009\""),
                extra("reference", "reference", "\"REFERENCE_CANARY_009\""),
                extra("unknown", "UNKNOWN_FIELD_CANARY_009", "\"UNKNOWN_VALUE_CANARY_009\""));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("duplicateProperties")
    void rejectsRepeatedDecodedNamesEvenForEqualValuesNullsOrEscapedSpellings(RejectedDocument vector) {
        assertRejected(vector);
    }

    static Stream<RejectedDocument> duplicateProperties() {
        var cases = new ArrayList<RejectedDocument>();
        for (var field : Field.values()) {
            String complete = document(null, null, false);
            cases.add(rejected("duplicate-equal-" + field, appendProperty(complete, field.wire, TOKENS[field.ordinal()])));
            cases.add(rejected("duplicate-different-" + field,
                    appendProperty(complete, field.wire, "\"DUPLICATE_VALUE_CANARY_009\"")));
            cases.add(rejected("duplicate-null-first-" + field,
                    "{\"" + field.wire + "\":null," + complete.substring(1)));
            cases.add(rejected("duplicate-null-last-" + field, appendProperty(complete, field.wire, "null")));
            cases.add(rejected("duplicate-escaped-name-" + field,
                    appendProperty(complete, field.escapedWire, TOKENS[field.ordinal()])));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("trailingDocuments")
    void rejectsAnySecondJsonValueOrTrailingGarbage(RejectedDocument vector) {
        assertRejected(vector);
    }

    static Stream<RejectedDocument> trailingDocuments() {
        String complete = document(null, null, false);
        return Stream.of(
                rejected("trailing-object", complete + " {}"), rejected("trailing-array", complete + " []"),
                rejected("trailing-true", complete + " true"), rejected("trailing-false", complete + " false"),
                rejected("trailing-null", complete + " null"), rejected("trailing-integer", complete + " 42"),
                rejected("trailing-decimal", complete + " 1.25"),
                rejected("trailing-string", complete + " \"TRAILING_VALUE_CANARY_009\""),
                rejected("trailing-garbage", complete + " TRAILING_TOKEN_CANARY_009"));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("malformedObjects")
    void malformedObjectsFailWithSafeDiagnosticsInsteadOfEchoingTheirSource(RejectedDocument vector) {
        assertRejected(vector);
    }

    static Stream<RejectedDocument> malformedObjects() {
        String complete = document(null, null, false);
        return Stream.of(
                rejected("unfinished-value", "{\"password\":"),
                rejected("unterminated-string", "{\"password\":\"" + VALUES[5]),
                rejected("invalid-string-escape", "{\"password\":\"" + VALUES[5] + "\\q\"}"),
                rejected("missing-comma", "{\"password\":" + TOKENS[5] + " \"name\":" + TOKENS[0] + "}"),
                rejected("missing-colon", "{\"password\" " + TOKENS[5] + "}"),
                rejected("unclosed-object", complete.substring(0, complete.length() - 1)));
    }

    @Test
    void aCompleteValidDtoPassesTheRealConstraintWithTheFixedReference() {
        var request = decode(document(null, null, false), CustomerRegistrationRequestDTO.class);
        assertLocalValidity(request, true);
        assertRawValues(VALUES, request);
    }

    @Test
    void requestToStringNeverRendersTheSixRawValues() {
        var request = decode(document(null, null, false), CustomerRegistrationRequestDTO.class);
        assertThat("CustomerRegistrationRequestDTO[redacted]".equals(request.toString())).as("fixed redacted representation").isTrue();
    }

    @Test
    void registrationRulesDoNotChangeUnrelatedJsonCoercionUnknownDuplicateOrTrailingPolicies() {
        var input = decode("{\"quantity\":\"7\",\"unknown\":true}", UnrelatedInput.class);
        assertThat(Integer.valueOf(7).equals(input.quantity())).as("historical scalar coercion").isTrue();
        var duplicateAndTrailing = decode("{\"quantity\":1,\"quantity\":2} {}", UnrelatedInput.class);
        assertThat(Integer.valueOf(2).equals(duplicateAndTrailing.quantity()))
                .as("unrelated reader retains last-property and trailing-document policy").isTrue();
    }

    @Test
    void theJsonSliceHasNoDomainControllerPersistenceOrClock() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(EntityManagerFactory.class)).isEmpty();
        assertThat(context.getBeanNamesForType(Clock.class)).isEmpty();
        assertThat(context.getBeansWithAnnotation(RestController.class)).isEmpty();
    }

    private <T> T decode(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException | RuntimeException failure) {
            // Keep types and frames, never source values, exception messages or original causes.
            var causeTypes = new ArrayList<String>();
            Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            Throwable cause = failure;
            while (cause != null && causeTypes.size() < 16 && seen.add(cause)) {
                causeTypes.add(cause.getClass().getName());
                cause = cause.getCause();
            }
            if (cause != null) { causeTypes.add("[truncated-or-cycle]"); }
            var diagnostic = new AssertionError("Expected JSON input could not be bound; cause types: "
                    + String.join(" -> ", causeTypes) + "; mapper=" + mapper.getClass().getName()
                    + "; version=" + mapper.version()
                    + "; failUnknown=" + mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    + "; failTrailing=" + mapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    + "; strictDuplicates=" + mapper.getFactory().isEnabled(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
            diagnostic.setStackTrace(failure.getStackTrace());
            throw diagnostic;
        }
    }

    private void assertRejected(RejectedDocument vector) {
        JsonProcessingException rejected = null;
        try {
            mapper.readValue(vector.json(), CustomerRegistrationRequestDTO.class);
        } catch (JsonProcessingException failure) {
            rejected = failure;
        } catch (RuntimeException preparationFailure) {
            throw new AssertionError("Registration JSON rejection did not reach the parsing boundary");
        }
        assertThat(rejected != null).as("document must be rejected: %s", vector.id()).isTrue();
        if (vector.fixedMessage()) {
            assertThat(INVALID_INPUT.equals(rejected.getOriginalMessage())).as("fixed safe parser message: %s", vector.id()).isTrue();
        }
        // Empty input can be rejected by Jackson before a DTO deserializer is invoked; no fixed text imposed there.
        var pending = new ArrayDeque<Throwable>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(rejected);
        while (!pending.isEmpty() && seen.size() < 64) {
            var failure = pending.removeFirst();
            if (!seen.add(failure)) { continue; }
            assertNoPrivateValue(failure.getMessage(), vector.id());
            assertNoPrivateValue(failure.toString(), vector.id());
            if (failure instanceof JsonProcessingException jsonFailure) {
                assertNoPrivateValue(jsonFailure.getOriginalMessage(), vector.id());
            }
            if (failure.getCause() != null) { pending.add(failure.getCause()); }
            pending.addAll(Arrays.asList(failure.getSuppressed()));
        }
        assertThat(pending.isEmpty()).as("complete bounded diagnostic inspection").isTrue();
    }

    private static void assertNoPrivateValue(String diagnostic, String id) {
        boolean safe = diagnostic == null || PRIVATE_VALUES.stream().noneMatch(diagnostic::contains);
        assertThat(safe).as("received values absent from diagnostics: %s", id).isTrue();
    }

    private static void assertLocalValidity(CustomerRegistrationRequestDTO request, boolean expectedValid) {
        try {
            var violations = validator.validate(request);
            assertThat(violations.isEmpty()).as("real local constraint outcome").isEqualTo(expectedValid);
            if (!expectedValid) {
                assertThat(violations.size()).isEqualTo(1);
                assertThat(violations.iterator().next().getConstraintDescriptor().getAnnotation()
                        instanceof ValidCustomerRegistration).as("real registration constraint, no unrelated failure").isTrue();
            }
        } catch (RuntimeException preparationFailure) {
            throw new AssertionError("Local Bean Validation preparation did not complete");
        }
    }

    private static void assertRawValues(String[] expected, CustomerRegistrationRequestDTO request) {
        assertThat(request != null).as("non-null request DTO").isTrue();
        String[] actual = {request.name(), request.fullName(), request.email(), request.phone(), request.birthDate(), request.password()};
        for (var field : Field.values()) {
            assertThat(Objects.equals(expected[field.ordinal()], actual[field.ordinal()]))
                    .as("exact raw value for %s", field).isTrue();
        }
    }

    private static String document(Field changed, String token, boolean omit) {
        var properties = new ArrayList<String>();
        for (var field : Field.values()) {
            if (field == changed && omit) { continue; }
            properties.add("\"" + field.wire + "\":" + (field == changed ? token : TOKENS[field.ordinal()]));
        }
        return "{" + String.join(",", properties) + "}";
    }

    private static String reversedEscapedDocument() {
        var fields = Field.values();
        var properties = new ArrayList<String>();
        for (int index = fields.length - 1; index >= 0; index--) {
            properties.add("\"" + fields[index].escapedWire + "\":" + TOKENS[index]);
        }
        return "{" + String.join(",", properties) + "}";
    }

    private static String appendProperty(String complete, String name, String token) {
        return complete.substring(0, complete.length() - 1) + ",\"" + name + "\":" + token + "}";
    }

    private static RejectedDocument rejected(String id, String json) {
        return new RejectedDocument(id, json, true);
    }

    private static RejectedDocument extra(String id, String name, String token) {
        return rejected("extra-" + id, appendProperty(document(null, null, false), name, token));
    }

    enum Field {
        NAME("name", "\\u006eame"), FULL_NAME("fullName", "\\u0066ullName"), EMAIL("email", "\\u0065mail"),
        PHONE("phone", "\\u0070hone"), BIRTH_DATE("birthDate", "\\u0062irthDate"), PASSWORD("password", "\\u0070assword");

        private final String wire;
        private final String escapedWire;
        Field(String wire, String escapedWire) { this.wire = wire; this.escapedWire = escapedWire; }
    }

    private record AcceptedDocument(String id, String json, String[] expected) {
        private AcceptedDocument { expected = expected.clone(); }
        @Override public String[] expected() { return expected.clone(); }
        @Override public String toString() { return id; }
    }
    private record RejectedDocument(String id, String json, boolean fixedMessage) {
        @Override public String toString() { return id; }
    }
    private record PresenceCase(Field field, boolean missing) {
        @Override public String toString() { return field + (missing ? "-missing" : "-null"); }
    }
    // A mutable control isolates mapper policies from immutable creator update restrictions.
    static final class UnrelatedInput {
        public Integer quantity;
        Integer quantity() { return quantity; }
    }
}
