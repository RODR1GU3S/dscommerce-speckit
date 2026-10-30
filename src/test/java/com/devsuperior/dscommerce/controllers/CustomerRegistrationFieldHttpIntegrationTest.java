package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Event;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Point;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.SqlKind;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import com.devsuperior.dscommerce.services.CustomerRegistrationService;
import com.devsuperior.dscommerce.services.RegistrationAttemptReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

@RootHttpContext
class CustomerRegistrationFieldHttpIntegrationTest extends CustomerRegistrationFieldScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T047 authorship only. T049 executes both concrete children after T048 supplies V-reference. */
@Execution(ExecutionMode.SAME_THREAD)
@Import({CustomerRegistrationObservationConfig.class, CustomerRegistrationFieldScenarios.TimingConfig.class,
        CustomerRegistrationFieldScenarios.BodyTimingAdvice.class})
abstract class CustomerRegistrationFieldScenarios extends CustomerRegistrationHttpTestSupport {
    private static final Instant BEFORE = Instant.parse("2024-02-29T23:59:59Z");
    private static final Instant AFTER = Instant.parse("2024-03-01T00:00:01Z");
    private static final String EMOJI = "\uD83D\uDE00";
    private static final String TIME_HEADER = "X-Feature009-Field-Time";
    private static final int[] SPACES = {
            0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x0020, 0x0085, 0x00A0, 0x1680,
            0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200A,
            0x2028, 0x2029, 0x202F, 0x205F, 0x3000
    };
    @SpyBean private CustomerRegistrationService service; // Real delegate; no stubbing of results or failures.
    @Autowired private Observation observation;
    @Autowired private TimeProbe timing;
    @Autowired private RegistrationAttemptReference reference;
    private final Set<Long> createdAccounts = new LinkedHashSet<>();

    @BeforeEach
    void setTheCivilReferenceForFieldScenarios() {
        clock.setInstant(BEFORE);
        assertThat(observation.workerInstalled()).as("real transaction worker observation is installed").isTrue();
        fixture.createAccount("Existing.field.guard", LegacyCredential.SHORT, Set.of(UserRole.CLIENT));
    }

    @Test
    void aCompleteValidRequestIsThePositiveControl() throws SQLException {
        var input = base();
        assertAccepted(exchange(post("/customers", jsonBytes(json(input)), null, null)), input, input.email());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("acceptedFields")
    void acceptedFieldsPersistOnlyTheirPermittedNormalization(Accepted vector) throws SQLException {
        var raw = with(vector.field(), vector.raw());
        var expected = replace(raw, vector.field(), vector.stored());
        assertAccepted(exchange(post("/customers", jsonBytes(json(raw)), null, null)), expected,
                vector.field() == Field.EMAIL ? vector.emailKey() : base().email());
    }

    static Stream<Accepted> acceptedFields() {
        var cases = new ArrayList<Accepted>();
        for (String name : List.of("Ana", "A".repeat(40), "ana", "0._", "A._-9")) {
            cases.add(accepted("name-" + cases.size(), Field.NAME, name, name, null));
        }
        cases.add(accepted("full-min2", Field.FULL_NAME, "Al", "Al", null));
        cases.add(accepted("full-max120", Field.FULL_NAME, "A".repeat(120), "A".repeat(120), null));
        cases.add(accepted("full-decomposed120", Field.FULL_NAME, "e\u0301".repeat(60), "e\u0301".repeat(60), null));
        cases.add(accepted("full-sequence-and-interior", Field.FULL_NAME,
                "\u00A0 \u00C1na  e\u0301 \u3000", "\u00C1na  e\u0301", null));
        cases.add(accepted("full-outside-trim-list", Field.FULL_NAME, "\u001CAl\u200B\uFEFF", "\u001CAl\u200B\uFEFF", null));
        for (int point : SPACES) {
            String space = new String(Character.toChars(point));
            String label = Integer.toHexString(point);
            cases.add(accepted("full-trim-max-" + label, Field.FULL_NAME,
                    space + EMOJI.repeat(120) + space, EMOJI.repeat(120), null));
            cases.add(accepted("email-trim-max-" + label, Field.EMAIL,
                    space + email254() + space, email254(), email254()));
        }
        cases.add(accepted("email-spelling-plus-dot", Field.EMAIL,
                " \u00A0Pessoa.Tag+X@Example.TEST\u3000", "Pessoa.Tag+X@Example.TEST", "pessoa.tag+x@example.test"));
        cases.add(accepted("email-kelvin", Field.EMAIL, "\u212A@example.test", "\u212A@example.test", "k@example.test"));
        cases.add(accepted("email-final-sigma", Field.EMAIL, "\u03C2@example.test", "\u03C2@example.test", "\u03C3@example.test"));
        cases.add(accepted("email-sharp-s-no-expansion", Field.EMAIL, "stra\u00DFe@example.test",
                "stra\u00DFe@example.test", "stra\u00DFe@example.test"));
        cases.add(accepted("email-decomposed-no-nfc", Field.EMAIL, "e\u0301@example.test",
                "e\u0301@example.test", "e\u0301@example.test"));
        cases.add(accepted("email-quoted-local", Field.EMAIL, "\"ana silva\"@example.test",
                "\"ana silva\"@example.test", "\"ana silva\"@example.test"));
        cases.add(accepted("email-unicode-local64", Field.EMAIL, "\u00E9".repeat(64) + "@example.test",
                "\u00E9".repeat(64) + "@example.test", "\u00E9".repeat(64) + "@example.test"));
        for (String phone : List.of("+12345678", "+123456789012345", "+00000000")) {
            cases.add(accepted("phone-" + cases.size(), Field.PHONE, phone, phone, null));
        }
        for (String date : List.of("0001-01-01", "2000-02-29", "2024-02-28", "2024-02-29")) {
            cases.add(accepted("date-" + date, Field.BIRTH_DATE, date, date, null));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidDocuments")
    void missingNullEmptyAndWrongTypesAreRejectedBeforeTheService(Document vector) {
        var payload = json(base());
        String field = vector.field().wire;
        switch (vector.kind()) {
            case "missing" -> payload.remove(field);
            case "null" -> payload.putNull(field);
            case "empty" -> payload.put(field, "");
            case "42" -> payload.put(field, 42);
            case "1.25" -> payload.put(field, 1.25);
            case "true" -> payload.put(field, true);
            case "false" -> payload.put(field, false);
            case "[]" -> payload.putArray(field);
            case "{}" -> payload.putObject(field);
            default -> throw new AssertionError("Unknown required-field fixture kind");
        }
        assertRejected(exchange(post("/customers", jsonBytes(payload), null, null)));
    }

    static Stream<Document> invalidDocuments() {
        return Arrays.stream(Field.values()).flatMap(field -> Stream.of(
                "missing", "null", "empty", "42", "1.25", "true", "false", "[]", "{}")
                .map(kind -> new Document(field, kind)));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("rejectedFields")
    void invalidFieldValuesAreRejectedWithoutPartialWritesOrServiceCalls(Rejected vector) {
        assertRejected(exchange(post("/customers", jsonBytes(json(with(vector.field(), vector.raw()))), null, null)));
    }

    static Stream<Rejected> rejectedFields() {
        var cases = new ArrayList<Rejected>();
        for (String name : List.of("An", "A".repeat(41), ".ab", "_ab", "-ab", " Ana", "Ana ", "An a",
                "\u00C1na", "A\u0661b", "A+b", "A/b", "Ana\n")) {
            cases.add(new Rejected("name-" + cases.size(), Field.NAME, name));
        }
        for (String full : List.of("A", EMOJI, "A".repeat(121), EMOJI.repeat(121),
                "\u00A0 A \u3000", "\u00A0 \t\u3000")) {
            cases.add(new Rejected("full-" + cases.size(), Field.FULL_NAME, full));
        }
        for (String email : List.of(email255(), "\u00A0" + email255() + "\u3000", "@example.test", "ana@",
                "ana.example.test", "ana@@example.test", "ana..silva@example.test", "ana silva@example.test",
                "ana@example .test", "a@-example.test", "a@" + "b".repeat(64) + ".test",
                "a".repeat(65) + "@example.test")) {
            cases.add(new Rejected("email-" + cases.size(), Field.EMAIL, email));
        }
        for (String phone : List.of("+1234567", "+1234567890123456", "12345678", " +12345678", "+12345678 ",
                "+1234 5678", "+(12345678)", "+1234-5678", "+1234.5678", "\uFF0B12345678",
                "+\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668",
                "+\uFF11\uFF12\uFF13\uFF14\uFF15\uFF16\uFF17\uFF18")) {
            cases.add(new Rejected("phone-" + cases.size(), Field.PHONE, phone));
        }
        for (String date : List.of("2024-03-01", "1900-02-29", "2023-02-29", "2024-02-30", "2023-04-31",
                "0000-01-01", "10000-01-01", "2024-2-09", "2024-02-9", "2024/02/29",
                "2024-02-29T00:00:00Z", " 2024-02-29", "2024-02-29 ", "\u0662\u0660\u0662\u0664-02-29")) {
            cases.add(new Rejected("date-" + cases.size(), Field.BIRTH_DATE, date));
        }
        return cases.stream();
    }

    @Test
    void queryWithAnOtherwiseValidBodyNeverCallsTheService() {
        assertRejected(exchange(post("/customers?trace=private-field-query", jsonBytes(json(base())), null, null)));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("midnightScenarios")
    void theInitialUtcReferenceSurvivesBodyValidationAndIoWaits(Timed vector) throws SQLException {
        var input = with(Field.BIRTH_DATE, vector.accepted() ? "2024-02-29" : "2024-03-01");
        var before = database.snapshot();
        long calls = serviceCalls();
        HttpResponse<byte[]> response;
        try (var window = timing.open(vector.phase()); var attempt = observation.open()) {
            attempt.on(Point.SQL_BEFORE, () -> timing.pause(Phase.IO, reference));
            var request = post("/customers", jsonBytes(json(input)), attempt.headerValue(), window.id);
            try (var executor = Executors.newSingleThreadExecutor()) {
                var future = executor.submit(() -> send(request));
                try {
                    await(window.arrived);
                    clock.setInstant(AFTER); // Request thread is still blocked at its chosen boundary.
                } finally { window.release.countDown(); }
                try { response = future.get(35, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Timed HTTP scenario interrupted");
                } catch (Exception incomplete) {
                    throw new AssertionError("Timed HTTP scenario did not complete");
                }
                // Join the request/filter scope before inspecting events or cleaning scenario-owned rows.
                window.awaitFinished();
                attempt.awaitFinished();
            } finally {
                window.release.countDown();
                window.awaitFinishedIfStarted();
                rememberNewAccounts(before, database.snapshot());
            }
            assertThat(window.initial != null && window.retained == window.initial)
                    .as("same immutable attempt snapshot across the controlled wait").isTrue();
            assertThat(BEFORE.equals(window.initial.instant())
                    && LocalDate.of(2024, 2, 29).equals(window.initial.utcDate())
                    && AFTER.equals(clock.instant())).as("initial UTC reference survives midnight").isTrue();
            var exchange = new Exchange(response, before, database.snapshot(), serviceCalls() - calls, attempt.events());
            if (vector.accepted()) { assertAccepted(exchange, input, input.email()); }
            else { assertRejected(exchange); }
            if (vector.phase() == Phase.IO) {
                assertThat(attempt.events().stream().anyMatch(event -> event.point() == Point.SQL_BEFORE))
                        .as("real SQL boundary was reached").isTrue();
            }
        }
    }

    static Stream<Timed> midnightScenarios() {
        return Stream.of(new Timed(Phase.BODY, true), new Timed(Phase.VALIDATION, true),
                new Timed(Phase.IO, true), new Timed(Phase.BODY, false), new Timed(Phase.VALIDATION, false));
    }

    private Exchange exchange(HttpRequest request) {
        var before = database.snapshot();
        long calls = serviceCalls();
        try (var attempt = observation.open()) {
            var traced = HttpRequest.newBuilder(request, (name, value) -> true)
                    .header(CustomerRegistrationObservationConfig.HEADER, attempt.headerValue()).build();
            var response = send(traced);
            attempt.awaitFinished();
            var after = database.snapshot();
            rememberNewAccounts(before, after); // Before assertions, also handles an unexpected creation on 400.
            return new Exchange(response, before, after, serviceCalls() - calls, attempt.events());
        }
    }

    private long serviceCalls() {
        assertThat(mockingDetails(service).isSpy()).as("service observation delegates to the real bean").isTrue();
        return mockingDetails(service).getInvocations().stream()
                .filter(invocation -> "register".equals(invocation.getMethod().getName())).count();
    }

    private void assertRejected(Exchange exchange) {
        var response = exchange.response();
        assertThat(response.statusCode()).isEqualTo(400);
        assertJson(response);
        var expected = mapper.createObjectNode().put("status", 400).put("error", "Bad Request")
                .put("message", "Invalid customer registration data").put("path", expectedContextPath() + "/customers");
        assertThat(expected.equals(jsonBody(response))).as("exact safe rejection without query or input values").isTrue();
        assertThat(exchange.serviceCalls()).as("invalid input never enters the registration service").isZero();
        assertThat(exchange.events().stream().noneMatch(event -> event.point() == Point.PROXY_ENTRY
                || event.point() == Point.SQL_BEFORE && (event.sqlKind() == SqlKind.INSERT
                || event.sqlKind() == SqlKind.UPDATE || event.sqlKind() == SqlKind.DELETE)))
                .as("invalid input never enters the write worker or issues DML").isTrue();
        assertUnchanged(exchange.before(), exchange.after());
        assertHeaders(response);
    }

    private void assertAccepted(Exchange exchange, Input expected, String expectedKey) throws SQLException {
        var response = exchange.response();
        assertThat(response.statusCode()).isEqualTo(201);
        assertJson(response);
        assertHeaders(response);
        assertThat(exchange.serviceCalls()).as("valid input invokes the real facade once").isEqualTo(1);
        assertThat(exchange.events().stream().anyMatch(event -> event.point() == Point.SQL_BEFORE
                && event.sqlKind() == SqlKind.INSERT)).as("positive control observes real INSERT execution").isTrue();
        var body = jsonBody(response);
        assertThat(body.isObject() && body.size() == 2 && body.has("id") && body.has("name")
                && expected.name().equals(body.path("name").textValue())).as("only id and exact name in confirmation").isTrue();
        var identity = body.path("id");
        assertThat(identity.isIntegralNumber() && identity.canConvertToLong() && identity.longValue() > 0).isTrue();
        long id = identity.longValue();
        for (var entry : exchange.before().tables().entrySet()) {
            String table = entry.getKey();
            var previous = entry.getValue();
            var current = exchange.after().tables().get(table);
            if ("TB_USER_ACCOUNT".equals(table) || "TB_USER_ACCOUNT_ROLE".equals(table)) {
                String column = "TB_USER_ACCOUNT".equals(table) ? "ID" : "USER_ACCOUNT_ID";
                assertThat(current.columns().equals(previous.columns())).as("unchanged account schema").isTrue();
                assertThat(current.rows().size()).isEqualTo(previous.rows().size() + 1);
                assertThat(current.rows().stream().filter(row -> id(row, column) != id).toList().equals(previous.rows()))
                        .as("previous account and role rows unchanged").isTrue();
            } else { assertThat(previous.equals(current)).as("unchanged table %s", table).isTrue(); }
        }
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select name, full_name, email, email_key, phone, birth_date from tb_user_account where id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).as("accepted row visible through a new physical connection").isTrue();
                assertThat(expected.name().equals(rows.getString(1)) && expected.fullName().equals(rows.getString(2))
                        && expected.email().equals(rows.getString(3)) && expected.phone().equals(rows.getString(5))
                        && LocalDate.parse(expected.birthDate()).equals(rows.getObject(6, LocalDate.class)))
                        .as("literal accepted data, exact remaining Unicode and permitted trimming").isTrue();
                assertThat(Arrays.equals(expectedKey.getBytes(StandardCharsets.UTF_8), rows.getBytes(4)))
                        .as("independent literal binary email key").isTrue();
                assertThat(rows.next()).isFalse();
            }
            try (var roles = connection.prepareStatement("select role from tb_user_account_role where user_account_id = ?")) {
                roles.setLong(1, id);
                try (var rows = roles.executeQuery()) {
                    assertThat(rows.next() && "CLIENT".equals(rows.getString(1)) && !rows.next())
                            .as("exactly one CLIENT role for the new row").isTrue();
                }
            }
        }
    }

    private void assertJson(HttpResponse<byte[]> response) {
        boolean json;
        try { json = MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""))
                .isCompatibleWith(MediaType.APPLICATION_JSON); }
        catch (IllegalArgumentException failure) { json = false; }
        assertThat(json).as("JSON response media type").isTrue();
    }

    private static void assertHeaders(HttpResponse<byte[]> response) {
        for (String header : List.of("Set-Cookie", "Authorization", "Location", "WWW-Authenticate")) {
            assertThat(response.headers().allValues(header).isEmpty()).as("no signup authentication/location header: %s", header).isTrue();
        }
    }

    private HttpRequest post(String path, byte[] body, String attempt, String window) {
        var builder = jsonRequest(path).POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (attempt != null) { builder.header(CustomerRegistrationObservationConfig.HEADER, attempt); }
        if (window != null) { builder.header(TIME_HEADER, window); }
        return builder.build();
    }

    private ObjectNode json(Input input) {
        return mapper.createObjectNode().put("name", input.name()).put("fullName", input.fullName())
                .put("email", input.email()).put("phone", input.phone()).put("birthDate", input.birthDate()).put("password", input.password());
    }

    private static Input base() {
        return new Input("Ana", "Ana Silva", "ana@example.test", "+12345678", "2000-02-29", "ValidPass123");
    }

    private static Input with(Field field, String raw) { return replace(base(), field, raw); }
    private static Input replace(Input input, Field field, String value) {
        return new Input(field == Field.NAME ? value : input.name(), field == Field.FULL_NAME ? value : input.fullName(),
                field == Field.EMAIL ? value : input.email(), field == Field.PHONE ? value : input.phone(),
                field == Field.BIRTH_DATE ? value : input.birthDate(), field == Field.PASSWORD ? value : input.password());
    }
    private static Accepted accepted(String id, Field field, String raw, String stored, String key) {
        return new Accepted(id, field, raw, stored, key);
    }
    private static String email254() {
        return "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
    }
    private static String email255() {
        return "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(62);
    }
    private static long id(Row row, String column) { return ((Number) row.values().get(column).value()).longValue(); }
    private void rememberNewAccounts(Snapshot before, Snapshot after) {
        var previous = before.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID")).collect(java.util.stream.Collectors.toSet());
        after.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID"))
                .filter(id -> !previous.contains(id)).forEach(createdAccounts::add);
    }

    @AfterEach
    void cleanOnlyScenarioOwnedAccountsAfterAllRequestsHaveFinished() throws SQLException {
        assertNoCallerTransaction();
        assertThat(timing.windows.isEmpty()).as("all timing scopes closed before cleanup").isTrue();
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
                throw new AssertionError("Cleanup of field-scenario accounts failed");
            }
        } catch (SQLException failure) { throw new SQLException("Field-scenario cleanup could not complete"); }
    }

    private enum Field {
        NAME("name"), FULL_NAME("fullName"), EMAIL("email"), PHONE("phone"), BIRTH_DATE("birthDate"), PASSWORD("password");
        private final String wire;
        Field(String wire) { this.wire = wire; }
    }
    private enum Phase { BODY, VALIDATION, IO }
    private record Input(String name, String fullName, String email, String phone, String birthDate, String password) {
        @Override public String toString() { return "FieldInput[redacted]"; }
    }
    private record Accepted(String id, Field field, String raw, String stored, String emailKey) {
        @Override public String toString() { return id; }
    }
    private record Rejected(String id, Field field, String raw) { @Override public String toString() { return id; } }
    private record Document(Field field, String kind) { @Override public String toString() { return field + "-" + kind; } }
    private record Timed(Phase phase, boolean accepted) { }
    private record Exchange(HttpResponse<byte[]> response, Snapshot before, Snapshot after, long serviceCalls, List<Event> events) {
        @Override public String toString() { return "FieldExchange[redacted]"; }
    }

    /** Explicit import only. No production registration, global test initializer or fake endpoint. */
    @TestConfiguration(proxyBeanMethods = false)
    static class TimingConfig {
        @Bean TimeProbe customerRegistrationFieldTimeProbe() { return new TimeProbe(); }
        @Bean FilterRegistrationBean<OncePerRequestFilter> customerRegistrationFieldTimeScope(
                TimeProbe probe, RegistrationAttemptReference reference) {
            var filter = new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                          FilterChain chain) throws ServletException, IOException {
                    String key = request.getHeader(TIME_HEADER);
                    if (key == null) { chain.doFilter(request, response); return; }
                    var window = probe.windows.get(key);
                    if (window == null || probe.current.get() != null || !"POST".equals(request.getMethod())
                            || !"/customers".equals(request.getRequestURI().substring(request.getContextPath().length()))) {
                        throw new AssertionError("Invalid field timing scope");
                    }
                    window.started.set(true);
                    probe.current.set(window);
                    var wrapped = new HttpServletRequestWrapper(request) {
                        @Override public ServletInputStream getInputStream() throws IOException {
                            var input = super.getInputStream();
                            return new ServletInputStream() {
                                @Override public int read() throws IOException {
                                    probe.pause(Phase.BODY, reference);
                                    return input.read();
                                }
                                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                                    probe.pause(Phase.BODY, reference);
                                    return input.read(bytes, offset, length);
                                }
                                @Override public boolean isFinished() { return input.isFinished(); }
                                @Override public boolean isReady() { return input.isReady(); }
                                @Override public void setReadListener(ReadListener listener) { input.setReadListener(listener); }
                            };
                        }
                    };
                    try { chain.doFilter(wrapped, response); }
                    finally { probe.current.remove(); window.finished.countDown(); }
                }
            };
            var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
            return registration;
        }
    }

    /** TestComponent prevents discovery by application scanning; imported only by the two field children. */
    @TestComponent
    @ControllerAdvice(assignableTypes = CustomerRegistrationController.class)
    static class BodyTimingAdvice extends RequestBodyAdviceAdapter {
        private final TimeProbe probe;
        private final RegistrationAttemptReference reference;
        BodyTimingAdvice(TimeProbe probe, RegistrationAttemptReference reference) { this.probe = probe; this.reference = reference; }
        @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
            return type == CustomerRegistrationRequestDTO.class;
        }
        @Override public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter, Type type,
                                             Class<? extends HttpMessageConverter<?>> converter) {
            probe.pause(Phase.VALIDATION, reference);
            return body;
        }
    }

    static final class TimeProbe {
        private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
        private final ThreadLocal<Window> current = new ThreadLocal<>();
        Window open(Phase phase) {
            var window = new Window(this, phase);
            windows.put(window.id, window);
            return window;
        }
        void pause(Phase phase, RegistrationAttemptReference reference) {
            var window = current.get();
            if (window == null || phase != window.phase || !window.paused.compareAndSet(false, true)) { return; }
            window.initial = reference.snapshot();
            window.arrived.countDown();
            await(window.release);
            window.retained = reference.snapshot();
        }
    }

    static final class Window implements AutoCloseable {
        private final TimeProbe owner;
        private final Phase phase;
        private final String id = UUID.randomUUID().toString();
        private final AtomicBoolean paused = new AtomicBoolean();
        private final AtomicBoolean started = new AtomicBoolean();
        private final CountDownLatch arrived = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);
        private volatile RegistrationAttemptReference.Snapshot initial;
        private volatile RegistrationAttemptReference.Snapshot retained;
        private Window(TimeProbe owner, Phase phase) { this.owner = owner; this.phase = phase; }
        void awaitFinished() { await(finished); }
        void awaitFinishedIfStarted() { if (started.get()) { awaitFinished(); } }
        @Override public void close() {
            release.countDown();
            awaitFinishedIfStarted();
            owner.windows.remove(id, this);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Controlled field timing boundary timed out"); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Controlled field timing boundary interrupted");
        }
    }
}
