package com.devsuperior.dscommerce.validation;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ApiHttpContext;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.controllers.CustomerRegistrationController;
import com.devsuperior.dscommerce.controllers.CustomerRegistrationHttpTestSupport;
import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.services.RegistrationAttemptReference;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** T048 authorship only; T049 owns execution/discovery of this outer and both HTTP nested classes. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationReferenceTest {
    private static final Instant BEFORE = Instant.parse("2024-02-29T23:59:59Z");
    private static final Instant AFTER = Instant.parse("2024-03-01T00:00:01Z");
    private static final String HEADER = "X-Feature009-Reference-Time";
    private final Map<String, ContextIdentity> contexts = new LinkedHashMap<>();

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("dateCases")
    void realLocalValidationUsesTheExplicitCivilReference(DateCase vector) {
        var reference = RegistrationAttemptReference.fixed(vector.reference());
        var snapshot = reference.snapshot();
        var request = input(vector.birthDate());
        try (var local = new LocalValidation(reference)) {
            assertThat(local.validator.validate(input("0001-01-01")).isEmpty()).as("valid control for every reference").isTrue();
            assertThat(local.validator.validate(request).isEmpty()).as("civil date outcome: %s", vector.id())
                    .isEqualTo(vector.accepted());
            assertThat(reference.snapshot() == snapshot).as("validation never replaces the attempt snapshot").isTrue();
            assertRaw(request, input(vector.birthDate()));
        }
    }

    static Stream<DateCase> dateCases() {
        return Stream.of(
                new DateCase("year0001", "0001-01-01", Instant.parse("0001-01-01T00:00:00Z"), true),
                new DateCase("year9999", "9999-12-31", Instant.parse("9999-12-31T23:59:59Z"), true),
                date("yesterday", "2024-02-28", true), date("today-inclusive", "2024-02-29", true),
                date("tomorrow", "2024-03-01", false), date("leap2000", "2000-02-29", true),
                date("century1900", "1900-02-29", false),
                new DateCase("century2100", "2100-02-29", Instant.parse("2200-01-01T00:00:00Z"), false),
                date("common-february28", "2023-02-28", true),
                date("year0000", "0000-01-01", false), date("year10000", "10000-01-01", false),
                date("month00", "2024-00-01", false), date("month13", "2024-13-01", false),
                date("day00", "2024-02-00", false), date("february30", "2024-02-30", false),
                date("april31", "2023-04-31", false), date("common-february29", "2023-02-29", false),
                date("unpadded-month", "2024-2-09", false), date("unpadded-day", "2024-02-9", false),
                date("slashes", "2024/02/29", false), date("time-suffix", "2024-02-29T00:00:00Z", false),
                date("zone-suffix", "2024-02-29Z", false), date("signed-year", "+2024-02-29", false),
                date("non-ascii-digits", "\u0662\u0660\u0662\u0664-02-29", false),
                date("leading-space", " 2024-02-29", false), date("trailing-space", "2024-02-29 ", false));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("utcCases")
    void referenceDateUsesUtcInsteadOfTheClockZone(UtcCase vector) {
        var clock = Clock.fixed(vector.instant(), vector.zone());
        var reference = RegistrationAttemptReference.fixed(clock.instant());
        assertThat(reference.snapshot().instant()).isEqualTo(vector.instant());
        assertThat(reference.snapshot().utcDate()).isEqualTo(vector.expectedUtcDate());
    }

    static Stream<UtcCase> utcCases() {
        return Stream.of(
                new UtcCase("before-plus14", BEFORE, ZoneOffset.ofHours(14), LocalDate.of(2024, 2, 29)),
                new UtcCase("before-minus12", BEFORE, ZoneOffset.ofHours(-12), LocalDate.of(2024, 2, 29)),
                new UtcCase("after-plus14", AFTER, ZoneOffset.ofHours(14), LocalDate.of(2024, 3, 1)),
                new UtcCase("after-minus12", AFTER, ZoneOffset.ofHours(-12), LocalDate.of(2024, 3, 1)));
    }

    @Test
    void aSecondInitializationCannotRecaptureTheNextDay() {
        var reference = RegistrationAttemptReference.fixed(BEFORE);
        var initial = reference.snapshot();
        assertThrows(IllegalStateException.class, () -> reference.initialize(AFTER));
        assertThat(reference.snapshot() == initial).isTrue();
        assertThat(reference.snapshot().instant()).isEqualTo(BEFORE);
        assertThat(reference.snapshot().utcDate()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void anUninitializedReferenceIsAPreparationFailureRatherThanInvalidConsumerInput() {
        var reference = new RegistrationAttemptReference();
        assertThrows(IllegalStateException.class, reference::snapshot);
        try (var local = new LocalValidation(reference)) {
            assertThrows(ValidationException.class, () -> local.validator.validate(input("2000-02-29")));
        }
    }

    @Test
    void nonHttpCallersSupplyIndependentExplicitReferences() {
        assertThat(RequestContextHolder.getRequestAttributes() == null).as("no HTTP request scope in the caller").isTrue();
        var first = RegistrationAttemptReference.fixed(BEFORE);
        var second = RegistrationAttemptReference.fixed(AFTER);
        var request = input("2024-03-01");
        try (var before = new LocalValidation(first); var after = new LocalValidation(second)) {
            assertThat(before.validator.validate(request).isEmpty()).as("future for the first explicit attempt").isFalse();
            assertThat(after.validator.validate(request).isEmpty()).as("today for the independent next attempt").isTrue();
            assertThat(first.snapshot() == second.snapshot()).isFalse();
            assertThat(first.snapshot().utcDate()).isEqualTo(LocalDate.of(2024, 2, 29));
            assertRaw(request, input("2024-03-01"));
        }
    }

    @Test
    void aNullInstantDoesNotConsumeTheSingleInitialization() {
        var reference = new RegistrationAttemptReference();
        assertThrows(IllegalArgumentException.class, () -> reference.initialize(null));
        reference.initialize(BEFORE);
        assertThat(reference.snapshot().instant()).isEqualTo(BEFORE);
        assertThrows(IllegalArgumentException.class, () -> RegistrationAttemptReference.fixed(null));
    }

    @Test
    void validationKeepsBothTheReferenceAndAllRawValues() {
        var reference = RegistrationAttemptReference.fixed(BEFORE);
        var initial = reference.snapshot();
        var raw = new CustomerRegistrationRequestDTO("Ana", " \u00C1na  Silva\u3000", " Ana@Example.test ",
                "+12345678", "2024-02-29", " Chosen-password ");
        var expected = new CustomerRegistrationRequestDTO("Ana", " \u00C1na  Silva\u3000", " Ana@Example.test ",
                "+12345678", "2024-02-29", " Chosen-password ");
        try (var local = new LocalValidation(reference)) {
            assertThat(local.validator.validate(raw).isEmpty()).isTrue();
            assertRaw(raw, expected);
            assertThat(reference.snapshot() == initial).isTrue();
        }
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @RootHttpContext
    class RootHttpTest extends HttpCases {
        @Override protected String expectedContextPath() { return ""; }
        @Override void rememberContext() { remember("", clock, port, context.getEnvironment().getRequiredProperty("spring.datasource.url")); }
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @ApiHttpContext
    class ApiHttpTest extends HttpCases {
        @Override protected String expectedContextPath() { return "/api"; }
        @Override void rememberContext() { remember("/api", clock, port, context.getEnvironment().getRequiredProperty("spring.datasource.url")); }
    }

    private void remember(String path, Clock clock, int port, String url) {
        assertThat(contexts.putIfAbsent(path, new ContextIdentity(clock, port, url)) == null).isTrue();
    }

    @AfterAll
    void bothHttpContextsMustActuallyBeDiscoveredAndRemainIndependent() {
        assertThat(contexts).containsOnlyKeys("", "/api");
        var root = contexts.get("");
        var api = contexts.get("/api");
        assertThat(root.clock() == api.clock()).isFalse();
        assertThat(root.port()).isNotEqualTo(api.port());
        assertThat(root.url().equals(api.url())).isFalse();
    }

    @Import({TimingConfig.class, WaitingAdvice.class})
    abstract static class HttpCases extends CustomerRegistrationHttpTestSupport {
        @Autowired private TimeProbe timing;
        private final Set<Long> createdAccounts = new LinkedHashSet<>();
        abstract void rememberContext();
        @BeforeAll void recordReferenceContext() { rememberContext(); }
        @BeforeEach void setReferenceClock() { clock.setInstant(BEFORE); }

        @ParameterizedTest(name = "{index}: {0}")
        @MethodSource("timedCases")
        void capturePrecedesBodyAndSurvivesParsingOrWaiting(Timed vector) throws SQLException {
            var before = database.snapshot();
            var body = mapper.createObjectNode().put("name", "Ana").put("fullName", "Ana Silva")
                    .put("email", "ana@example.test").put("phone", "+12345678")
                    .put("birthDate", vector.accepted() ? "2024-02-29" : "2024-03-01").put("password", "Chosen-password-009");
            HttpResponse<byte[]> response;
            try (var pause = timing.open(vector.stage())) {
                var request = jsonRequest("/customers").header(HEADER, pause.id)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(body))).build();
                try (var executor = Executors.newSingleThreadExecutor()) {
                    var future = executor.submit(() -> send(request));
                    try { await(pause.arrived); clock.setInstant(AFTER); }
                    finally { pause.release.countDown(); }
                    try { response = future.get(35, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Reference HTTP wait interrupted");
                    } catch (Exception incomplete) { throw new AssertionError("Reference HTTP attempt did not complete"); }
                    pause.awaitFinished();
                } finally {
                    pause.release.countDown();
                    if (pause.started) { pause.awaitFinished(); }
                    rememberCreatedAccounts(before, database.snapshot());
                }
                assertThat(pause.reads.get()).as("body bytes were actually consumed").isGreaterThanOrEqualTo(2);
                assertThat(pause.beforeBody != null && pause.atWait == pause.beforeBody && pause.afterWait == pause.beforeBody)
                        .as("one snapshot before reading, during parsing/wait and afterwards").isTrue();
                assertThat(BEFORE.equals(pause.beforeBody.instant())
                        && LocalDate.of(2024, 2, 29).equals(pause.beforeBody.utcDate())
                        && AFTER.equals(clock.instant())).as("UTC date never recaptured across midnight").isTrue();
                assertThat(response.statusCode()).isEqualTo(vector.accepted() ? 201 : 400);
                assertThat(MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""))
                        .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
                for (String header : List.of("Set-Cookie", "Authorization", "Location")) {
                    assertThat(response.headers().allValues(header).isEmpty()).isTrue();
                }
                if (vector.accepted()) { assertStoredToday(response, before, database.snapshot()); }
                else {
                    var expected = mapper.createObjectNode().put("status", 400).put("error", "Bad Request")
                            .put("message", "Invalid customer registration data").put("path", expectedContextPath() + "/customers");
                    assertThat(expected.equals(jsonBody(response))).as("safe future-date rejection").isTrue();
                    assertUnchanged(before, database.snapshot());
                }
            }
        }

        static Stream<Timed> timedCases() {
            return Stream.of(new Timed(Stage.PARSING, true), new Timed(Stage.PARSING, false),
                    new Timed(Stage.WAITING, true), new Timed(Stage.WAITING, false));
        }

        private void assertStoredToday(HttpResponse<byte[]> response, Snapshot before, Snapshot after) throws SQLException {
            var body = jsonBody(response);
            assertThat(body.isObject() && body.size() == 2 && "Ana".equals(body.path("name").textValue())
                    && body.path("id").isIntegralNumber() && body.path("id").canConvertToLong()
                    && body.path("id").longValue() > 0).as("only positive id and exact name").isTrue();
            long account = body.path("id").longValue();
            for (var entry : before.tables().entrySet()) {
                String table = entry.getKey();
                var previous = entry.getValue();
                var current = after.tables().get(table);
                if ("TB_USER_ACCOUNT".equals(table) || "TB_USER_ACCOUNT_ROLE".equals(table)) {
                    String key = "TB_USER_ACCOUNT".equals(table) ? "ID" : "USER_ACCOUNT_ID";
                    assertThat(current.columns().equals(previous.columns())).isTrue();
                    assertThat(current.rows().size()).isEqualTo(previous.rows().size() + 1);
                    assertThat(current.rows().stream().filter(row -> id(row, key) != account).toList().equals(previous.rows()))
                            .as("previous accounts/roles unchanged").isTrue();
                } else { assertThat(previous.equals(current)).as("unchanged table %s", table).isTrue(); }
            }
            try (var connection = openIndependentConnection();
                 var statement = connection.prepareStatement("select birth_date from tb_user_account where id = ?")) {
                statement.setLong(1, account);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next() && LocalDate.of(2024, 2, 29).equals(rows.getObject(1, LocalDate.class))
                            && !rows.next()).as("initial-day birth date visible after response").isTrue();
                }
                try (var roles = connection.prepareStatement("select role from tb_user_account_role where user_account_id = ?")) {
                    roles.setLong(1, account);
                    try (var rows = roles.executeQuery()) {
                        assertThat(rows.next() && "CLIENT".equals(rows.getString(1)) && !rows.next()).isTrue();
                    }
                }
            }
        }

        private void rememberCreatedAccounts(Snapshot before, Snapshot after) {
            var previous = before.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID")).toList();
            after.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID"))
                    .filter(id -> !previous.contains(id)).forEach(createdAccounts::add);
        }

        @AfterEach
        void cleanOnlyFinishedReferenceScenarioAccounts() throws SQLException {
            assertNoCallerTransaction();
            assertThat(timing.pauses.isEmpty()).as("all request waits finished before cleanup").isTrue();
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
                    throw new AssertionError("Reference-scenario cleanup failed");
                }
            } catch (SQLException failure) { throw new SQLException("Reference-scenario cleanup could not complete"); }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimingConfig {
        @Bean TimeProbe customerRegistrationReferenceTimeProbe() { return new TimeProbe(); }
        @Bean FilterRegistrationBean<OncePerRequestFilter> customerRegistrationReferenceTimeFilter(
                TimeProbe probe, RegistrationAttemptReference reference) {
            var filter = new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                          FilterChain chain) throws ServletException, IOException {
                    String key = request.getHeader(HEADER);
                    if (key == null) { chain.doFilter(request, response); return; }
                    var pause = probe.pauses.get(key);
                    if (pause == null || probe.current.get() != null || !"POST".equals(request.getMethod())
                            || !"/customers".equals(request.getRequestURI().substring(request.getContextPath().length()))) {
                        throw new AssertionError("Invalid reference timing scope");
                    }
                    pause.started = true;
                    probe.current.set(pause);
                    var wrapped = new HttpServletRequestWrapper(request) {
                        @Override public ServletInputStream getInputStream() throws IOException {
                            var input = super.getInputStream();
                            return new ServletInputStream() {
                                @Override public int read() throws IOException {
                                    probe.beforeRead(reference);
                                    return input.read();
                                }
                                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                                    if (length == 0) { return 0; }
                                    probe.beforeRead(reference);
                                    // Force multiple genuine reads instead of buffering the whole JSON at once.
                                    return input.read(bytes, offset, Math.min(length, 32));
                                }
                                @Override public boolean isFinished() { return input.isFinished(); }
                                @Override public boolean isReady() { return input.isReady(); }
                                @Override public void setReadListener(ReadListener listener) { input.setReadListener(listener); }
                            };
                        }
                    };
                    try { chain.doFilter(wrapped, response); }
                    finally { probe.current.remove(); pause.finished.countDown(); }
                }
            };
            var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
            return registration;
        }
    }

    @TestComponent
    @ControllerAdvice(assignableTypes = CustomerRegistrationController.class)
    static class WaitingAdvice extends RequestBodyAdviceAdapter {
        private final TimeProbe probe;
        private final RegistrationAttemptReference reference;
        WaitingAdvice(TimeProbe probe, RegistrationAttemptReference reference) { this.probe = probe; this.reference = reference; }
        @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
            return type == CustomerRegistrationRequestDTO.class;
        }
        @Override public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter, Type type,
                                             Class<? extends HttpMessageConverter<?>> converter) {
            probe.waitAt(Stage.WAITING, reference);
            return body;
        }
    }

    static final class TimeProbe {
        private final ConcurrentHashMap<String, Pause> pauses = new ConcurrentHashMap<>();
        private final ThreadLocal<Pause> current = new ThreadLocal<>();
        Pause open(Stage stage) {
            var pause = new Pause(this, stage);
            pauses.put(pause.id, pause);
            return pause;
        }
        void beforeRead(RegistrationAttemptReference reference) {
            var pause = current.get();
            if (pause == null) { return; }
            int read = pause.reads.incrementAndGet();
            if (read == 1) { pause.beforeBody = reference.snapshot(); }
            if (read == 2) { waitAt(Stage.PARSING, reference); }
        }
        void waitAt(Stage stage, RegistrationAttemptReference reference) {
            var pause = current.get();
            if (pause == null || pause.stage != stage) { return; }
            if (pause.atWait != null) { throw new AssertionError("Reference wait entered twice"); }
            pause.atWait = reference.snapshot();
            pause.arrived.countDown();
            await(pause.release);
            pause.afterWait = reference.snapshot();
        }
    }

    static final class Pause implements AutoCloseable {
        private final TimeProbe owner;
        private final Stage stage;
        private final String id = UUID.randomUUID().toString();
        private final AtomicInteger reads = new AtomicInteger();
        private final CountDownLatch arrived = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);
        private volatile boolean started;
        private volatile RegistrationAttemptReference.Snapshot beforeBody;
        private volatile RegistrationAttemptReference.Snapshot atWait;
        private volatile RegistrationAttemptReference.Snapshot afterWait;
        private Pause(TimeProbe owner, Stage stage) { this.owner = owner; this.stage = stage; }
        void awaitFinished() { await(finished); }
        @Override public void close() {
            release.countDown();
            if (started) { awaitFinished(); }
            owner.pauses.remove(id, this);
        }
    }

    /** Real provider/constraint, explicit non-HTTP reference, no domain context or fake validation result. */
    private static final class LocalValidation implements AutoCloseable {
        private final ValidatorFactory factory;
        private final Validator validator;
        LocalValidation(RegistrationAttemptReference reference) {
            var configuration = Validation.byProvider(HibernateValidator.class).configure();
            var defaults = configuration.getDefaultConstraintValidatorFactory();
            var delegate = new java.util.concurrent.atomic.AtomicReference<Validator>();
            factory = configuration.constraintValidatorFactory(new ConstraintValidatorFactory() {
                @Override public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> type) {
                    if (CustomerRegistrationValidator.class.equals(type)) {
                        return type.cast(new CustomerRegistrationValidator(reference, Objects.requireNonNull(delegate.get())));
                    }
                    return defaults.getInstance(type);
                }
                @Override public void releaseInstance(ConstraintValidator<?, ?> instance) {
                    if (!(instance instanceof CustomerRegistrationValidator)) { defaults.releaseInstance(instance); }
                }
            }).clockProvider(() -> Clock.fixed(Instant.parse("2099-01-01T00:00:00Z"), ZoneOffset.UTC)).buildValidatorFactory();
            validator = factory.getValidator();
            delegate.set(validator);
        }
        @Override public void close() { factory.close(); }
    }

    private static DateCase date(String id, String raw, boolean accepted) { return new DateCase(id, raw, BEFORE, accepted); }
    private static CustomerRegistrationRequestDTO input(String date) {
        return new CustomerRegistrationRequestDTO("Ana", "Ana Silva", "ana@example.test", "+12345678", date, "ValidPass123");
    }
    private static void assertRaw(CustomerRegistrationRequestDTO actual, CustomerRegistrationRequestDTO expected) {
        assertThat(Objects.equals(actual.name(), expected.name()) && Objects.equals(actual.fullName(), expected.fullName())
                && Objects.equals(actual.email(), expected.email()) && Objects.equals(actual.phone(), expected.phone())
                && Objects.equals(actual.birthDate(), expected.birthDate()) && Objects.equals(actual.password(), expected.password()))
                .as("all six raw values preserved without rendering secrets").isTrue();
    }
    private static long id(Row row, String column) { return ((Number) row.values().get(column).value()).longValue(); }
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Controlled reference boundary timed out"); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Controlled reference boundary interrupted");
        }
    }
    private enum Stage { PARSING, WAITING }
    private record DateCase(String id, String birthDate, Instant reference, boolean accepted) {
        @Override public String toString() { return id; }
    }
    private record UtcCase(String id, Instant instant, ZoneOffset zone, LocalDate expectedUtcDate) {
        @Override public String toString() { return id; }
    }
    private record Timed(Stage stage, boolean accepted) { }
    private record ContextIdentity(Clock clock, int port, String url) {
        @Override public String toString() { return "ReferenceHttpContext[details=omitted]"; }
    }
}
