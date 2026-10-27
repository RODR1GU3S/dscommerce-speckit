package com.devsuperior.dscommerce.services;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ApiHttpContext;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.controllers.CustomerRegistrationHttpTestSupport;
import com.devsuperior.dscommerce.dto.CustomerRegistrationConfirmationDTO;
import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import com.devsuperior.dscommerce.repositories.UserAccountInsertRepositoryImpl;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AccountPasswordVerifier;
import com.devsuperior.dscommerce.security.RegistrationPasswordHasher;
import com.devsuperior.dscommerce.services.exceptions.CustomerRegistrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.validateMockitoUsage;

/** T063 authorship only. Compilation, discovery and all execution belong to T064. */
@ExtendWith(OutputCaptureExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationSecretLoggingTest {
    private static final String NAME = "Secret.client";
    private static final String EMAIL = "secret@example.test";
    private static final String OBSERVER_READY = "T063 secret observation window ready";

    @Test
    void dtoCommandEntityAndConfirmationHaveNoSecretRenderingOrPlaintextEntityField(CapturedOutput output) throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var bag = new Secrets(mapper);
        String hash = bag.syntheticHash(); // Representation fixture, never a known password/KDF result.
        var request = new CustomerRegistrationRequestDTO(NAME, "Secret Person", EMAIL,
                "+12345678", "2000-02-29", bag.password);
        var reference = RegistrationAttemptReference.fixed(Instant.parse("2030-01-01T12:00:00Z")).snapshot();
        var command = new CustomerRegistrationCommand(NAME, "Secret Person", EMAIL,
                EMAIL.getBytes(StandardCharsets.UTF_8), "+12345678", LocalDate.of(2000, 2, 29), hash, reference);
        var account = new UserAccount(NAME, hash, "Secret Person", EMAIL,
                EMAIL.getBytes(StandardCharsets.UTF_8), "+12345678", LocalDate.of(2000, 2, 29));
        var confirmation = new CustomerRegistrationConfirmationDTO(63L, NAME);
        for (Object value : List.of(request, command, account, confirmation)) {
            bag.assertAbsent(String.valueOf(value), "public object rendering");
            bag.assertAbsent(safeJson(mapper, value), "public object JSON");
        }
        assertThat("{}".equals(safeJson(mapper, request))).as("all six request properties stay write-only").isTrue();
        var publicConfirmation = mapper.valueToTree(confirmation);
        assertThat(publicConfirmation.isObject() && publicConfirmation.size() == 2
                && publicConfirmation.has("id") && publicConfirmation.has("name")).as("confirmation only id/name").isTrue();
        for (var field : UserAccount.class.getDeclaredFields()) {
            if (field.getType() == String.class && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                assertThat(!bag.password.equals(field.get(account))).as("entity has no readable plaintext password").isTrue();
            }
        }
        assertThat(hash.equals(account.getPasswordHash())).as("protected hash is retained internally, never returned").isTrue();
        bag.assertAbsent(output.getAll(), "object representation diagnostics");
    }

    @Test
    void parserAndClosedRegistrationFailuresDiscardSensitiveSourcesCausesAndSuppressedValues(CapturedOutput output) throws Exception {
        var mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule().addDeserializer(
                CustomerRegistrationRequestDTO.class,
                new com.devsuperior.dscommerce.config.CustomerRegistrationRequestJsonConfig.CustomerRegistrationRequestDeserializer()));
        var bag = new Secrets(mapper);
        String source = "{\"password\":" + mapper.writeValueAsString(bag.password) + ",\"name\":";
        Throwable parserFailure = null;
        try { mapper.readValue(source, CustomerRegistrationRequestDTO.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) { parserFailure = failure; }
        assertThat(parserFailure != null).as("malformed source must actually fail parsing").isTrue();
        bag.assertAbsent(stack(parserFailure), "parser diagnostic chain");
        for (var reason : CustomerRegistrationException.Reason.values()) {
            var safe = new CustomerRegistrationException(reason);
            safe.addSuppressed(bag.failure());
            boolean immutableCause;
            try { safe.initCause(bag.failure()); immutableCause = false; }
            catch (IllegalStateException expected) { immutableCause = true; }
            assertThat(immutableCause && safe.getCause() == null && safe.getSuppressed().length == 0)
                    .as("closed error cannot acquire a sensitive cause or suppressed exception").isTrue();
            bag.assertAbsent(stack(safe), "closed registration error");
        }
        bag.assertAbsent(output.getAll(), "parser and exception diagnostics");
    }

    @ParameterizedTest(name = "detector {0}")
    @EnumSource(Surface.class)
    void captureDetectsCanariesInEveryWrittenSurfaceWithoutPublishingThem(Surface surface, CapturedOutput output) throws Exception {
        var mapper = new ObjectMapper();
        var bag = new Secrets(mapper);
        var recorder = new Recorder();
        recorder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        recorder.start();
        try {
            var event = new LoggingEvent();
            event.setLoggerName("T063.detector." + surface.name());
            event.setLevel(ch.qos.logback.classic.Level.WARN);
            String message = "safe detector fixture";
            event.setMDCPropertyMap(surface == Surface.MDC ? Map.of("fixture", bag.password) : Map.of());
            switch (surface) {
                case MESSAGE -> message = bag.password;
                case ARGUMENT -> event.setArgumentArray(new Object[]{bag.password});
                case FORMATTED -> { message = "value={}"; event.setArgumentArray(new Object[]{bag.password}); }
                case MDC -> { }
                case CAUSE -> event.setThrowableProxy(new ch.qos.logback.classic.spi.ThrowableProxy(bag.failure()));
                case SUPPRESSED -> {
                    var wrapper = new IllegalStateException("safe outer");
                    wrapper.addSuppressed(bag.failure());
                    event.setThrowableProxy(new ch.qos.logback.classic.spi.ThrowableProxy(wrapper));
                }
                case KEY_VALUE -> event.addKeyValuePair(new org.slf4j.event.KeyValuePair("fixture", bag.password));
                case JSON_ESCAPE -> message = mapper.writeValueAsString(bag.password);
                case ENCODED -> message = Base64.getEncoder().encodeToString(bag.password.getBytes(StandardCharsets.UTF_8));
                case DTO_ARGUMENT -> event.setArgumentArray(new Object[]{new CustomerRegistrationRequestDTO(NAME,
                        "Secret Person", EMAIL, "+12345678", "2000-02-29", bag.password)});
                case PAYLOAD_EVENT -> recorder.recordApplicationEvent(new PayloadApplicationEvent<>("safe fixture",
                        new CustomerRegistrationRequestDTO(NAME, "Secret Person", EMAIL, "+12345678", "2000-02-29", bag.password)));
            }
            event.setMessage(message); // LoggingEvent permits one assignment only; all eleven controls remain.
            // Direct appender only: this deliberate leak must never reach console/production appenders.
            recorder.doAppend(event);
            assertThat(bag.present(recorder.text())).as("observer must detect its deliberate private positive control").isTrue();
            assertThat(!recorder.failed).as("capture must not silently discard an uninspectable event").isTrue();
            bag.assertAbsent(output.getAll(), "deliberate private detector controls stay off console");
        } finally { recorder.stop(); recorder.clear(); }
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @RootHttpContext
    class RootHttpTest extends SecretHttpCases {
        @Override protected String expectedContextPath() { return ""; }
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @ApiHttpContext
    class ApiHttpTest extends SecretHttpCases {
        @Override protected String expectedContextPath() { return "/api"; }
    }

    @Import(CompletionConfig.class)
    abstract static class SecretHttpCases extends CustomerRegistrationHttpTestSupport {
        @Autowired private Completions completions;
        @SpyBean private CustomerRegistrationService registration;
        @SpyBean private RegistrationPasswordHasher hasher;
        @SpyBean private UserAccountRepository accounts;
        @SpyBean private UserAccountInsertRepositoryImpl insertion;
        private UserAccountInsertRepositoryImpl insertionTarget;
        @SpyBean private AccountPasswordVerifier verifier;
        @SpyBean private JwtTokenService tokens;
        private Secrets bag;
        private Capture capture;
        private final Set<Long> createdAccounts = new LinkedHashSet<>();
        private final AtomicReference<String> realHash = new AtomicReference<>();
        private final AtomicReference<String> sqlState = new AtomicReference<>();
        private final AtomicInteger injected = new AtomicInteger();
        private ApplicationListener<ApplicationEvent> events;

        @BeforeEach
        void observeRealChannelsWithoutChangingLoggingLevelsOrExistingAppenders() {
            validateMockitoUsage();
            insertionTarget = AopTestUtils.getUltimateTargetObject(insertion);
            assertThat(mockingDetails(insertionTarget).isSpy()).as("stub only the existing spy behind the transaction proxy").isTrue();
            bag = new Secrets(mapper);
            realHash.set(null); sqlState.set(null); injected.set(0);
            capture = new Capture();
            events = event -> capture.recorder.recordApplicationEvent(event);
            context.addApplicationListener(events);
            LoggerFactory.getLogger(CustomerRegistrationSecretLoggingTest.class).warn(OBSERVER_READY);
            assertThat(capture.recorder.text().contains(OBSERVER_READY)).as("live logger reaches installed observer").isTrue();
            doAnswer(invocation -> {
                String hash = (String) invocation.callRealMethod();
                realHash.set(hash); bag.watchHash(hash);
                return hash;
            }).when(hasher).hash(anyString());
            fixture.createAccount("Guard.secret", LegacyCredential.SHORT, Set.of(UserRole.CLIENT));
            bag.watchHash(LegacyCredential.SHORT.passwordHash());
        }

        @ParameterizedTest(name = "registration secret channel {0}")
        @EnumSource(RegistrationCase.class)
        void registrationOutcomesDoNotExposeRawPasswordHashOrDiagnosticCauses(RegistrationCase kind, CapturedOutput output)
                throws SQLException {
            if (kind == RegistrationCase.DUPLICATE_NAME) {
                fixture.createAccount(NAME, LegacyCredential.SHORT, Set.of(UserRole.CLIENT));
            }
            arm(kind);
            var payload = signup();
            switch (kind) {
                case INVALID_LENGTH -> { String invalid = bag.password + "a".repeat(65); bag.watch(invalid); payload.put("password", invalid); }
                case WRONG_TYPE -> payload.putArray("password").add(bag.password);
                case UNKNOWN_PROPERTY -> payload.put("passwordHash", bag.syntheticHash());
                case BINDING -> payload.put("fullName", " ");
                default -> { }
            }
            byte[] document = jsonBytes(payload);
            if (kind == RegistrationCase.MALFORMED_JSON) {
                document = (new String(document, StandardCharsets.UTF_8) + "," + quoted(bag.password)).getBytes(StandardCharsets.UTF_8);
            } else if (kind == RegistrationCase.DUPLICATE_PROPERTY) {
                String json = new String(document, StandardCharsets.UTF_8);
                document = (json.substring(0, json.length() - 1) + ",\"password\":" + quoted(bag.password) + "}")
                        .getBytes(StandardCharsets.UTF_8);
            }
            long services = calls(registration, "register"), hashes = calls(hasher, "hash"), issued = calls(tokens, "issueToken");
            var before = database.snapshot();
            var response = request("/customers", document);
            var after = database.snapshot();
            rememberNewAccounts(before, after);
            assertThat(response.statusCode()).isEqualTo(kind.status);
            inspectResponse(response);
            if (kind.status == 201) {
                var body = jsonBody(response);
                assertThat(body.isObject() && body.size() == 2 && body.path("id").isIntegralNumber()
                        && body.path("id").longValue() > 0 && NAME.equals(body.path("name").textValue()))
                        .as("only id/name confirms registration").isTrue();
                assertOnlyNewAccountAndClient(before, after, body.path("id").longValue());
                assertPersistedCredential(body.path("id").longValue());
            } else {
                assertError(response, kind.status, "/customers");
                assertUnchanged(before, after);
            }
            assertThat(calls(registration, "register") == services + (kind.status == 400 ? 0 : 1))
                    .as("invalid JSON/binding stops before registration service").isTrue();
            assertThat(calls(hasher, "hash") == hashes + (kind.status == 400 ? 0 : 1))
                    .as("one hashing boundary only for locally valid input").isTrue();
            assertThat(calls(tokens, "issueToken") == issued).as("registration never issues a token").isTrue();
            if (kind.status == 500) { assertThat(injected.get()).as("requested fault boundary reached once").isEqualTo(1); }
            if (kind == RegistrationCase.ORM_JDBC_FAILURE) {
                assertThat("22001".equals(sqlState.get())).as("real H2 length violation reached through Hibernate/JDBC").isTrue();
            }
            assertObservedChannels(output);
        }

        @ParameterizedTest(name = "login secret channel {0}")
        @EnumSource(LoginCase.class)
        void subsequentLoginDoesNotLogCredentialsHashesTokensOrEncoderCauses(LoginCase kind, CapturedOutput output)
                throws SQLException {
            var beforeSignup = database.snapshot();
            var signup = request("/customers", jsonBytes(signup()));
            var afterSignup = database.snapshot();
            rememberNewAccounts(beforeSignup, afterSignup);
            assertThat(signup.statusCode()).as("real registration is a required positive preparation").isEqualTo(201);
            inspectResponse(signup);
            long id = jsonBody(signup).path("id").longValue();
            assertOnlyNewAccountAndClient(beforeSignup, afterSignup, id);
            assertPersistedCredential(id);
            String name = kind == LoginCase.MISSING_NAME ? "Missing.secret" : NAME;
            String password = kind == LoginCase.WRONG_PASSWORD ? bag.password + "x" : bag.password;
            bag.watch(password);
            if (kind == LoginCase.ENCODER_FAILURE) {
                doAnswer(invocation -> { injected.incrementAndGet(); throw bag.failure(); })
                        .when(verifier).matches(anyString(), anyString());
            }
            long checks = calls(verifier, "matches"), issued = calls(tokens, "issueToken");
            var before = database.snapshot();
            var response = request("/login", jsonBytes(mapper.createObjectNode().put("name", name).put("password", password)));
            var after = database.snapshot();
            assertThat(response.statusCode()).isEqualTo(kind.status);
            var body = jsonBody(response);
            if (kind.status == 200) {
                assertThat(body.isObject() && body.size() == 2 && body.path("accessToken").isTextual()
                        && !body.path("accessToken").textValue().isBlank() && "Bearer".equals(body.path("tokenType").textValue()))
                        .as("only the existing login response may return a token").isTrue();
                String token = body.path("accessToken").textValue();
                bag.watch(token); // Token is allowed here, but must not occur in captured diagnostics/events.
                try {
                    var jwt = context.getBean(org.springframework.security.oauth2.jwt.JwtDecoder.class).decode(token);
                    assertThat(Long.toString(id).equals(jwt.getSubject()) && NAME.equals(jwt.getClaimAsString("name")))
                            .as("real JWT identifies the just-registered account").isTrue();
                } catch (org.springframework.security.oauth2.jwt.JwtException | IllegalArgumentException failure) {
                    throw new AssertionError("Login token could not be verified safely");
                }
                bag.assertAbsent(new String(response.body(), StandardCharsets.UTF_8).replace(token, "[token]"), "login body excluding allowed token");
            } else { assertError(response, kind.status, "/login"); inspectResponse(response); }
            bag.assertAbsent(response.headers().map().toString(), "login headers");
            assertHeaders(response);
            assertUnchanged(before, after);
            assertThat(calls(verifier, "matches") == checks + (kind == LoginCase.MISSING_NAME ? 0 : 1)
                    && calls(tokens, "issueToken") == issued + (kind.status == 200 ? 1 : 0))
                    .as("verification/token boundaries agree with actual login outcome").isTrue();
            if (kind == LoginCase.ENCODER_FAILURE) { assertThat(injected.get()).as("verification fault actually reached").isEqualTo(1); }
            assertObservedChannels(output);
        }

        private void arm(RegistrationCase kind) {
            if (kind == RegistrationCase.ENCODER_FAILURE) {
                doAnswer(invocation -> { injected.incrementAndGet(); throw bag.failure(); }).when(hasher).hash(anyString());
            } else if (kind == RegistrationCase.LOOKUP_FAILURE) {
                doAnswer(invocation -> { injected.incrementAndGet(); throw bag.failure(); }).when(accounts).findByName(NAME);
            } else if (kind == RegistrationCase.ORM_JDBC_FAILURE) {
                // Arming is test preparation, not a transaction. The application must still enter the proxy.
                assertThat(catchThrowable(() -> insertion.insert(null)) instanceof IllegalTransactionStateException)
                        .as("the unchanged insert proxy still requires an existing transaction").isTrue();
                doAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .as("the real worker transaction must be active at the armed insert").isTrue();
                    injected.incrementAndGet();
                    UserAccount account = invocation.getArgument(0);
                    String oversized = bag.password + account.getPasswordHash() + "x".repeat(256);
                    bag.watch(oversized);
                    // Test-only invalid value, before real persist: @NotBlank passes but VARCHAR255 cannot.
                    ReflectionTestUtils.setField(account, "passwordHash", oversized);
                    try { return invocation.callRealMethod(); }
                    catch (RuntimeException failure) {
                        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                            if (cause instanceof SQLException sql) { sqlState.set(sql.getSQLState()); break; }
                        }
                        throw failure;
                    }
                }).when(insertionTarget).insert(any(UserAccount.class));
            }
        }

        private ObjectNode signup() {
            return mapper.createObjectNode().put("name", NAME).put("fullName", "Secret Person").put("email", EMAIL)
                    .put("phone", "+12345678").put("birthDate", "2000-02-29").put("password", bag.password);
        }

        private String quoted(String value) { return new String(jsonBytes(value), StandardCharsets.UTF_8); }

        private HttpResponse<byte[]> request(String path, byte[] document) {
            String id = UUID.randomUUID().toString();
            var finished = new CountDownLatch(1);
            completions.pending.put(id, finished);
            try {
                return send(jsonRequest(path).header(Completions.HEADER, id)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(document)).build());
            } finally {
                try { awaitCompletion(finished); }
                finally { completions.pending.remove(id); }
            }
        }

        private void inspectResponse(HttpResponse<byte[]> response) {
            bag.assertAbsent(new String(response.body(), StandardCharsets.UTF_8), "HTTP body");
            bag.assertAbsent(response.headers().map().toString(), "HTTP headers");
            assertHeaders(response);
        }

        private static void assertHeaders(HttpResponse<byte[]> response) {
            for (String header : List.of("Set-Cookie", "Authorization", "Location")) {
                assertThat(response.headers().allValues(header).isEmpty()).as("no session or credential header").isTrue();
            }
            boolean json;
            try { json = org.springframework.http.MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""))
                    .isCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON); }
            catch (IllegalArgumentException failure) { json = false; }
            assertThat(json).as("JSON response media type").isTrue();
        }

        private void assertError(HttpResponse<byte[]> response, int status, String path) {
            String error = status == 400 ? "Bad Request" : status == 401 ? "Unauthorized" : status == 409 ? "Conflict" : "Internal Server Error";
            String message = path.equals("/login") ? (status == 401 ? "Invalid credentials" : "Authentication could not be completed")
                    : status == 400 ? "Invalid customer registration data" : status == 409 ? "Customer registration not available"
                    : "Customer registration could not be completed";
            var expected = mapper.createObjectNode().put("status", status).put("error", error).put("message", message)
                    .put("path", expectedContextPath() + path);
            assertThat(expected.equals(jsonBody(response))).as("exact safe error without input, hash or cause").isTrue();
        }

        private void assertObservedChannels(CapturedOutput output) {
            assertThat(!capture.recorder.failed).as("all observed log/event material was inspectable").isTrue();
            bag.assertAbsent(capture.recorder.text(), "application/framework log events and payload events");
            bag.assertAbsent(output.getAll(), "stdout and stderr including ORM/JDBC diagnostics");
        }

        private void assertPersistedCredential(long id) throws SQLException {
            String hash = realHash.get();
            assertThat(hash != null && hash.length() == 97 && hash.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"))
                    .as("real approved registration encoder was used").isTrue();
            try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                    "select password_hash, birth_date from tb_user_account where id = ?")) {
                statement.setLong(1, id);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next() && hash.equals(rows.getString(1)) && !bag.password.equals(rows.getString(1))
                            && LocalDate.of(2000, 2, 29).equals(rows.getObject(2, LocalDate.class)) && !rows.next())
                            .as("independent JDBC sees only the hash and the exact civil date").isTrue();
                }
            }
        }

        private void assertOnlyNewAccountAndClient(Snapshot before, Snapshot after, long id) {
            for (var entry : before.tables().entrySet()) {
                String table = entry.getKey();
                var previous = entry.getValue(); var current = after.tables().get(table);
                if (table.equals("TB_USER_ACCOUNT") || table.equals("TB_USER_ACCOUNT_ROLE")) {
                    String key = table.equals("TB_USER_ACCOUNT") ? "ID" : "USER_ACCOUNT_ID";
                    assertThat(current.columns().equals(previous.columns()) && current.rows().size() == previous.rows().size() + 1
                            && current.rows().stream().filter(row -> identity(row, key) != id).toList().equals(previous.rows()))
                            .as("only one account/CLIENT added; prior rows preserved").isTrue();
                    if (table.equals("TB_USER_ACCOUNT_ROLE")) {
                        assertThat(current.rows().stream().anyMatch(row -> identity(row, key) == id
                                && "CLIENT".equals(row.values().get("ROLE").value()))).as("only CLIENT assigned").isTrue();
                    }
                } else { assertThat(previous.equals(current)).as("other tables unchanged").isTrue(); }
            }
        }

        private void rememberNewAccounts(Snapshot before, Snapshot after) {
            var previous = before.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> identity(row, "ID")).toList();
            after.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> identity(row, "ID"))
                    .filter(id -> !previous.contains(id)).forEach(createdAccounts::add);
        }

        @AfterEach
        void resetOnlyTestFaultsCleanOwnedAccountsAndDetachObservers() throws SQLException {
            try {
                reset(hasher, accounts, insertionTarget, verifier);
                validateMockitoUsage();
                try (var connection = openIndependentConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        for (long id : createdAccounts) {
                            for (String sql : List.of("delete from tb_user_account_role where user_account_id = ?",
                                    "delete from tb_user_account where id = ?")) {
                                try (var statement = connection.prepareStatement(sql)) { statement.setLong(1, id); statement.executeUpdate(); }
                            }
                        }
                        connection.commit(); createdAccounts.clear();
                    } catch (SQLException | RuntimeException failure) {
                        connection.rollback(); throw new AssertionError("Secret scenario cleanup did not complete");
                    }
                }
            } finally {
                if (events != null) { context.removeApplicationListener(events); events = null; }
                if (capture != null) { capture.close(); capture = null; }
            }
        }
    }

    private enum RegistrationCase {
        SUCCESS(201), INVALID_LENGTH(400), MALFORMED_JSON(400), WRONG_TYPE(400), DUPLICATE_PROPERTY(400),
        UNKNOWN_PROPERTY(400), BINDING(400), DUPLICATE_NAME(409), ENCODER_FAILURE(500), LOOKUP_FAILURE(500), ORM_JDBC_FAILURE(500);
        final int status;
        RegistrationCase(int status) { this.status = status; }
    }
    private enum LoginCase {
        EXACT(200), WRONG_PASSWORD(401), MISSING_NAME(401), ENCODER_FAILURE(500);
        final int status;
        LoginCase(int status) { this.status = status; }
    }
    private enum Surface { MESSAGE, ARGUMENT, FORMATTED, MDC, CAUSE, SUPPRESSED, KEY_VALUE, JSON_ESCAPE, ENCODED, DTO_ARGUMENT, PAYLOAD_EVENT }

    /** Explicit import only; completion wraps filters and servlet, not just the delivered response body. */
    @Profile("test")
    static class CompletionConfig {
        @Bean Completions secretRequestCompletions() { return new Completions(); }
        @Bean FilterRegistrationBean<OncePerRequestFilter> secretRequestCompletionFilter(Completions completions) {
            var filter = new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                    var finished = completions.pending.get(request.getHeader(Completions.HEADER) == null ? "" : request.getHeader(Completions.HEADER));
                    try { chain.doFilter(request, response); }
                    finally { if (finished != null) { finished.countDown(); } }
                }
            };
            var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    private static final class Completions {
        private static final String HEADER = "X-T063-Completion";
        private final Map<String, CountDownLatch> pending = new ConcurrentHashMap<>();
        @Override public String toString() { return "SecretRequestCompletions[redacted]"; }
    }

    private static void awaitCompletion(CountDownLatch finished) {
        try {
            if (!finished.await(35, TimeUnit.SECONDS)) { throw new AssertionError("Secret observation request did not finish"); }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new AssertionError("Secret observation completion wait interrupted");
        }
    }

    private static long calls(Object bean, String method) {
        assertThat(mockingDetails(bean).isSpy()).as("observation must delegate to a real bean").isTrue();
        return mockingDetails(bean).getInvocations().stream().filter(call -> method.equals(call.getMethod().getName())).count();
    }

    private static long identity(Row row, String key) { return ((Number) row.values().get(key).value()).longValue(); }

    private static String stack(Throwable failure) {
        var text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString(); // Never log or attach this string to a failing assertion.
    }

    private static String safeJson(ObjectMapper mapper, Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new AssertionError("Secret-bearing representation could not be serialized safely");
        }
    }

    private static final class Secrets {
        private final ObjectMapper mapper;
        private final String nonce = UUID.randomUUID().toString().replace("-", "");
        private final String password = "T063-" + nonce + "-\0\u001C";
        private final Set<String> representations = ConcurrentHashMap.newKeySet();
        private Secrets(ObjectMapper mapper) {
            this.mapper = mapper;
            assertThat(password.codePointCount(0, password.length()) == 40 && password.getBytes(StandardCharsets.UTF_8).length == 40)
                    .as("disposable canary has independent literal counts within the approved 12-point/64-byte limits").isTrue();
            watch(nonce); watch(password);
        }
        private void watch(String value) {
            if (value == null || value.isEmpty()) { return; }
            representations.add(value);
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            representations.add(Base64.getEncoder().encodeToString(bytes));
            representations.add(HexFormat.of().formatHex(bytes));
            representations.add(HexFormat.of().withUpperCase().formatHex(bytes));
            representations.add(Arrays.toString(bytes));
            try {
                String quoted = mapper.writeValueAsString(value);
                representations.add(quoted.substring(1, quoted.length() - 1));
            } catch (Exception failure) { throw new AssertionError("Canary representations could not be prepared"); }
        }
        private void watchHash(String hash) {
            watch(hash);
            if (hash != null && hash.startsWith("$argon2id$")) {
                String[] parts = hash.split("\\$", -1);
                if (parts.length == 6) { watch(parts[4]); watch(parts[5]); }
            }
        }
        private String syntheticHash() {
            var encoder = Base64.getEncoder().withoutPadding();
            String value = "$argon2id$v=19$m=19456,t=2,p=1$"
                    + encoder.encodeToString(nonce.substring(0, 16).getBytes(StandardCharsets.US_ASCII)) + "$"
                    + encoder.encodeToString(nonce.getBytes(StandardCharsets.US_ASCII));
            watchHash(value); return value;
        }
        private RuntimeException failure() {
            var failure = new IllegalStateException("controlled-encoder-or-lookup " + password + " " + syntheticHash(),
                    new IllegalArgumentException("controlled-nested " + password));
            failure.addSuppressed(new SQLException("controlled-jdbc " + password, "22001", 22001));
            return failure;
        }
        private boolean present(String text) { return representations.stream().anyMatch(text::contains); }
        private void assertAbsent(String text, String channel) {
            assertThat(!present(text)).as("no disposable secret in %s", channel).isTrue();
        }
        @Override public String toString() { return "Secrets[redacted]"; }
    }

    /** Copy material while callbacks run; assertions only receive booleans and fixed descriptions. */
    private static final class Recorder extends AppenderBase<ILoggingEvent> {
        private final ConcurrentLinkedQueue<String> material = new ConcurrentLinkedQueue<>();
        private volatile boolean failed;
        @Override protected void append(ILoggingEvent event) {
            try {
                collect(event.getMessage()); collect(event.getFormattedMessage()); collect(event.getArgumentArray());
                collect(event.getMDCPropertyMap()); collect(event.getKeyValuePairs());
                if (event.getThrowableProxy() != null) { collect(ThrowableProxyUtil.asString(event.getThrowableProxy())); }
            } catch (RuntimeException failure) { failed = true; }
        }
        private void recordApplicationEvent(ApplicationEvent event) {
            try {
                collect(event.toString());
                if (event instanceof PayloadApplicationEvent<?> payload) { collect(payload.getPayload()); }
                else if (event.getClass().getName().startsWith("com.devsuperior.")) { collect(event); }
            } catch (RuntimeException failure) { failed = true; }
        }
        private void collect(Object value) {
            try { collect(value, Collections.newSetFromMap(new IdentityHashMap<>()), 0); }
            catch (Exception failure) { failed = true; }
        }
        private void collect(Object value, Set<Object> seen, int depth) throws IllegalAccessException {
            if (value == null || !seen.add(value)) { return; }
            if (depth > 12) { failed = true; return; }
            if (value instanceof CharSequence text) { material.add(text.toString()); }
            else if (value instanceof Throwable throwable) { material.add(stack(throwable)); }
            else if (value instanceof byte[] bytes) {
                material.add(new String(bytes, StandardCharsets.UTF_8)); material.add(Arrays.toString(bytes));
            } else if (value instanceof Map<?, ?> map) {
                for (var entry : map.entrySet()) { collect(entry.getKey(), seen, depth + 1); collect(entry.getValue(), seen, depth + 1); }
            } else if (value instanceof Iterable<?> values) {
                for (Object item : values) { collect(item, seen, depth + 1); }
            } else if (value.getClass().isArray()) {
                for (int i = 0; i < Array.getLength(value); i++) { collect(Array.get(value, i), seen, depth + 1); }
            } else {
                material.add(String.valueOf(value));
                // Detect credentials in DTO/command/entity event payloads even when their toString is redacted.
                if (value.getClass().getName().startsWith("com.devsuperior.") || value instanceof org.slf4j.event.KeyValuePair) {
                    for (Class<?> type = value.getClass(); type != Object.class && type != null; type = type.getSuperclass()) {
                        if (!type.getName().startsWith("com.devsuperior.") && type != org.slf4j.event.KeyValuePair.class) { break; }
                        for (var field : type.getDeclaredFields()) {
                            if (!Modifier.isStatic(field.getModifiers())) {
                                if (!field.trySetAccessible()) { failed = true; continue; }
                                collect(field.get(value), seen, depth + 1);
                            }
                        }
                    }
                }
            }
        }
        private String text() { return String.join("\n", material); }
        private void clear() { material.clear(); }
        @Override public String toString() { return "SecretRecorder[redacted]"; }
    }

    /** No level, additivity, filter or pre-existing appender is changed or removed. */
    private static final class Capture implements AutoCloseable {
        private final Recorder recorder = new Recorder();
        private final List<Logger> attached = new ArrayList<>();
        private Capture() {
            var context = (LoggerContext) LoggerFactory.getILoggerFactory();
            recorder.setContext(context); recorder.setName("T063-private-observer-" + UUID.randomUUID()); recorder.start();
            for (String channel : List.of(Logger.ROOT_LOGGER_NAME, "com.devsuperior.dscommerce", "org.springframework.web",
                    "org.springframework.validation", "org.springframework.security.crypto", "org.hibernate",
                    "org.hibernate.orm.jdbc.bind", "org.springframework.jdbc", "org.h2")) {
                Logger logger = context.getLogger(channel); logger.addAppender(recorder); attached.add(logger);
            }
        }
        @Override public void close() {
            for (Logger logger : attached) { logger.detachAppender(recorder); }
            recorder.stop(); recorder.clear(); attached.clear();
        }
        @Override public String toString() { return "SecretCapture[redacted]"; }
    }
}
