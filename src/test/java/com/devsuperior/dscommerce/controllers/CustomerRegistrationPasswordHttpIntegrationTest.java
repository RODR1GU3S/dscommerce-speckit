package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import com.devsuperior.dscommerce.security.AccountPasswordVerifier;
import com.devsuperior.dscommerce.security.RegistrationPasswordHasher;
import com.devsuperior.dscommerce.services.AuthenticationService;
import com.devsuperior.dscommerce.services.CustomerRegistrationService;
import com.devsuperior.dscommerce.services.JwtTokenService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

@RootHttpContext
class CustomerRegistrationPasswordHttpIntegrationTest extends CustomerRegistrationPasswordScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T062 authorship only. T064 owns execution/discovery of both concrete HTTP children. */
@Execution(ExecutionMode.SAME_THREAD)
abstract class CustomerRegistrationPasswordScenarios extends CustomerRegistrationHttpTestSupport {
    private static final String NAME = "Pass.client";
    private static final String EMAIL = "password@example.test";
    private static final String FULL_NAME = "Password Person";
    private static final String PHONE = "+12345678";
    private static final String PASSWORD = "AbCdEfGhIjKl";
    private static final String EMOJI = "\uD83D\uDE00";
    @SpyBean private CustomerRegistrationService registration;
    @SpyBean private AuthenticationService authentication;
    @SpyBean private RegistrationPasswordHasher hasher;
    @SpyBean private AccountPasswordVerifier verifier;
    @SpyBean private JwtTokenService tokens;
    private final Set<Long> createdAccounts = new LinkedHashSet<>();
    private final Set<Long> createdOrders = new LinkedHashSet<>();

    @BeforeEach
    void prepareOnlyCommittedLegacyGuardData() {
        fixture.createAccount("Existing.password.guard", LegacyCredential.SHORT, Set.of(UserRole.CLIENT));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("acceptedPasswords")
    void acceptedPasswordsAuthenticateExactlyAndRejectVariantsOrMissingNames(PasswordVector vector) throws SQLException {
        assertUnits(vector.password(), vector.points(), vector.bytes());
        var account = register(NAME, EMAIL, vector.password());
        assertLoginSuccess(account);
        for (String variant : vector.variants()) {
            assertThat(!vector.password().equals(variant)).as("distinct negative credential fixture").isTrue();
            assertLoginError(account, account.name(), variant, 401);
        }
        assertLoginError(null, "Absent.password", vector.password(), 401);
    }

    static Stream<PasswordVector> acceptedPasswords() {
        var fixed = Stream.of(
                new PasswordVector("ascii12", "a".repeat(12), 12, 12, List.of("a".repeat(11))),
                new PasswordVector("ascii64", "a".repeat(64), 64, 64, List.of("a".repeat(63))),
                new PasswordVector("acute32-bytes64", "\u00E1".repeat(32), 32, 64, List.of("\u00E1".repeat(33))),
                new PasswordVector("emoji12-bytes48", EMOJI.repeat(12), 12, 48, List.of(EMOJI.repeat(11))),
                new PasswordVector("emoji16-bytes64", EMOJI.repeat(16), 16, 64, List.of(EMOJI.repeat(17))),
                new PasswordVector("mixed-bytes64", "\u00E1".repeat(31) + "ab", 33, 64, List.of("\u00E1".repeat(31) + "a")),
                new PasswordVector("outer-spaces", " abcdefghijkl ", 14, 14, List.of("abcdefghijkl")),
                new PasswordVector("interior-space", "abcdef ghijkl", 13, 13, List.of("abcdefghijkl")),
                new PasswordVector("case", PASSWORD, 12, 12, List.of("abcdefghijkl")),
                new PasswordVector("composed", "\u00E9".repeat(12), 12, 24, List.of("e\u0301".repeat(12))),
                new PasswordVector("decomposed", "e\u0301".repeat(12), 24, 36, List.of("\u00E9".repeat(12))),
                new PasswordVector("nul12", "\0".repeat(12), 12, 12, List.of("\u001C".repeat(12))),
                new PasswordVector("control001c12", "\u001C".repeat(12), 12, 12, List.of("\0".repeat(12))),
                new PasswordVector("bell12", "\u0007".repeat(12), 12, 12, List.of("\u001C".repeat(12))));
        var spaces = closedSpaces().map(point -> {
            String space = new String(Character.toChars(point.codePoint()));
            return new PasswordVector("closed-" + Integer.toHexString(point.codePoint()),
                    space + "abcde" + space + "fghijk" + space, 14, 11 + 3 * point.bytes(),
                    List.of("abcde" + space + "fghijk", space + "abcdefghijk" + space));
        });
        return Stream.concat(fixed, spaces);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("rejectedPasswords")
    void invalidRegistrationPasswordsStopBeforeTheServiceHasherAndWrites(RejectedPassword vector) {
        if (vector.password() != null) { assertUnits(vector.password(), vector.points(), vector.bytes()); }
        assertRejectedSignup(passwordJson(signup(NAME, EMAIL), vector.password()));
    }

    static Stream<RejectedPassword> rejectedPasswords() {
        var fixed = Stream.of(
                new RejectedPassword("ascii11", "a".repeat(11), 11, 11),
                new RejectedPassword("ascii65", "a".repeat(65), 65, 65),
                new RejectedPassword("acute33", "\u00E1".repeat(33), 33, 66),
                new RejectedPassword("emoji11", EMOJI.repeat(11), 11, 44),
                new RejectedPassword("emoji17", EMOJI.repeat(17), 17, 68),
                new RejectedPassword("mixed65bytes", "\u00E1".repeat(31) + "abc", 34, 65),
                new RejectedPassword("decomposed66bytes", "e\u0301".repeat(22), 44, 66),
                new RejectedPassword("null", null, 0, 0), new RejectedPassword("empty", "", 0, 0));
        return Stream.concat(fixed, closedSpaces().map(point -> new RejectedPassword(
                "closed-only-" + Integer.toHexString(point.codePoint()),
                new String(Character.toChars(point.codePoint())).repeat(12), 12, 12 * point.bytes())));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("malformedUnicode")
    void malformedUnicodeEscapesAreRejectedWithoutRepairOrPartialSignup(Malformed vector) {
        // ASCII JSON with actual JSON escapes; do not ask a UTF-8 encoder to replace a Java surrogate.
        String json = "{\"name\":\"Pass.client\",\"fullName\":\"Password Person\",\"email\":\"password@example.test\","
                + "\"phone\":\"+12345678\",\"birthDate\":\"2000-02-29\",\"password\":\""
                + "a".repeat(12) + vector.escapes() + "\"}";
        assertRejectedSignup(json.getBytes(StandardCharsets.UTF_8));
    }

    static Stream<Malformed> malformedUnicode() {
        return Stream.of(new Malformed("high", "\\uD800"), new Malformed("low", "\\uDC00"),
                new Malformed("reversed", "\\uDE00\\uD83D"),
                new Malformed("broken-before-valid-pair", "\\uD800\\uD83D\\uDE00"));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("exactPairs")
    void pQAndPPlusNulRemainDistinctCredentialsInBothHttpDirections(PasswordPair pair) throws SQLException {
        var first = register("Pass.first", "first.password@example.test", pair.first());
        var second = register("Pass.second", "second.password@example.test", pair.second());
        assertThat(first.id() != second.id()).as("distinct committed accounts").isTrue();
        assertLoginSuccess(first);
        assertLoginSuccess(second);
        assertLoginError(first, first.name(), second.password(), 401);
        assertLoginError(second, second.name(), first.password(), 401);
    }

    static Stream<PasswordPair> exactPairs() {
        String p = "abcdefghijkl";
        String q = p + "\0" + p;
        return Stream.of(new PasswordPair("p-q", p, q), new PasswordPair("p-p-plus-nul", p, p + "\0"),
                new PasswordPair("q-p-plus-nul", q, p + "\0"));
    }

    @ParameterizedTest(name = "legacy {0}")
    @EnumSource(LegacyCredential.class)
    void previousBcryptCredentialsKeepTheirHashNullFieldsAndUnrestrictedLoginDomain(LegacyCredential credential)
            throws SQLException {
        var legacy = fixture.createAccount("Legacy.password", credential, Set.of(UserRole.CLIENT));
        long derivations = calls(hasher, "hash");
        long argonMatches = calls(hasher, "matches");
        assertLoginSuccess(new Registered(legacy.id(), legacy.name(), null, legacy.password(), credential.passwordHash()));
        assertThat(calls(hasher, "hash") == derivations && calls(hasher, "matches") == argonMatches)
                .as("legacy login neither derives nor falls back to registration hashing").isTrue();
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select password_hash, full_name, email, email_key, phone, birth_date from tb_user_account where id = ?")) {
            statement.setLong(1, legacy.id());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(credential.passwordHash().equals(rows.getString(1))).as("fixed BCrypt bytes preserved").isTrue();
                for (int column = 2; column <= 6; column++) {
                    assertThat(rows.getObject(column) == null).as("legacy optional registration fields remain NULL").isTrue();
                }
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidLoginDocuments")
    void loginKeepsItsInputFieldsAndSafeValidationMessage(String kind) throws SQLException {
        var account = register(NAME, EMAIL, PASSWORD);
        assertLoginSuccess(account);
        var payload = login(NAME);
        payload.put("password", PASSWORD);
        switch (kind) {
            case "password-missing" -> payload.remove("password");
            case "password-null" -> payload.putNull("password");
            case "password-empty" -> payload.put("password", "");
            case "password-spaces" -> payload.put("password", " ".repeat(12));
            case "password-tabs" -> payload.put("password", "\t".repeat(12));
            case "password-ascii-mixed" -> payload.put("password", " \t\r\n\f\u000B".repeat(2));
            case "password-boolean" -> payload.put("password", true);
            case "name-number" -> payload.put("name", 42);
            case "unknown-field" -> payload.put("unexpectedField", "discarded");
            case "name-missing" -> payload.remove("name");
            case "name-null" -> payload.putNull("name");
            case "name-blank" -> payload.put("name", " ");
            case "password-array" -> payload.putArray("password");
            default -> throw new AssertionError("Unknown login fixture kind");
        }
        long auth = calls(authentication, "login"), verified = calls(verifier, "matches"), issued = calls(tokens, "issueToken");
        var exchange = exchange("/login", jsonBytes(payload));
        assertError(exchange.response(), 400, "Bad Request", "Invalid login data", "/login");
        assertUnchanged(exchange.before(), exchange.after());
        assertThat(calls(authentication, "login") == auth && calls(verifier, "matches") == verified
                && calls(tokens, "issueToken") == issued).as("invalid login stops before service, verification and token").isTrue();
    }

    static Stream<String> invalidLoginDocuments() {
        return Stream.of("password-missing", "password-null", "password-empty", "password-spaces", "password-tabs",
                "password-ascii-mixed", "password-boolean", "name-number", "unknown-field", "name-missing",
                "name-null", "name-blank", "password-array");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("historicalOnlyLoginInputs")
    void historicalOnlyInputReachesVerificationUnchangedWithoutBecomingACorrectCredential(LoginInput input)
            throws SQLException {
        var account = register(NAME, EMAIL, PASSWORD);
        assertLoginSuccess(account);
        // Literal ASCII JSON preserves malformed UTF-16 escape sequences through the HTTP boundary.
        String json = "{\"name\":\"Pass.client\",\"password\":\"" + input.jsonEscapes() + "\"}";
        var original = (Argon2PasswordEncoder) ReflectionTestUtils.getField(hasher, "encoder");
        assertThat(original != null).as("observe the actual registration encoder, without replacing its result").isTrue();
        var observed = spy(original);
        var lowerFailure = new AtomicReference<RuntimeException>();
        var exactArguments = new AtomicBoolean();
        doAnswer(invocation -> {
            exactArguments.set(input.raw().equals(invocation.getArgument(0)) && account.hash().equals(invocation.getArgument(1)));
            try { return invocation.callRealMethod(); }
            catch (RuntimeException failure) { lowerFailure.set(failure); throw failure; }
        }).when(observed).matches(any(CharSequence.class), anyString());
        try {
            ReflectionTestUtils.setField(hasher, "encoder", observed);
            long auth = calls(authentication, "login"), verified = calls(verifier, "matches"), issued = calls(tokens, "issueToken");
            var exchange = exchange("/login", json.getBytes(StandardCharsets.UTF_8));
            RuntimeException cause = lowerFailure.get();
            String frame = cause == null ? "NONE" : Arrays.stream(cause.getStackTrace())
                    .filter(element -> element.getClassName().startsWith("org.bouncycastle."))
                    .findFirst().map(element -> element.getClassName() + "." + element.getMethodName()).orElse("OTHER");
            String reason = cause == null ? "NONE" : "invalid UTF-16 codepoint".equals(cause.getMessage()) ? "INVALID_UTF16" : "OTHER";
            String type = cause == null ? "NONE" : cause.getClass().getName();
            // Symbolic evidence only: no exception message, arguments, password/hash or Throwable publication.
            System.out.println("T064_ENCODER vector=" + input.id() + " type=" + type + " frame=" + frame + " reason=" + reason);
            assertAll("historical input contract and independently observed effects",
                    () -> assertError(exchange.response(), 401, "Unauthorized", "Invalid credentials", "/login"),
                    () -> {
                        assertJson(exchange.response());
                        assertNoAuthenticationHeaders(exchange.response());
                        if (exchange.response().statusCode() == 500) {
                            // Classify an observed error envelope; the independent 401 assertion above still fails.
                            assertError(exchange.response(), 500, "Internal Server Error", "Authentication could not be completed", "/login");
                        }
                    },
                    () -> assertThat(calls(authentication, "login") == auth + 1 && calls(verifier, "matches") == verified + 1
                            && calls(tokens, "issueToken") == issued).as("one verification and no token despite any encoder failure").isTrue(),
                    () -> assertThat(exactArguments.get() && calls(observed, "matches") == 1)
                            .as("original Java String and recognized stored hash reach the real lower encoder exactly once").isTrue(),
                    () -> assertVerificationArguments(account, input.raw()),
                    () -> assertUnchanged(exchange.before(), exchange.after()));
        } finally { ReflectionTestUtils.setField(hasher, "encoder", original); }
    }

    static Stream<LoginInput> historicalOnlyLoginInputs() {
        return Stream.of(new LoginInput("short", "a", "a"),
                new LoginInput("over64", "a".repeat(65), "a".repeat(65)),
                new LoginInput("nbsp-only", "\u00A0", "\\u00A0"),
                new LoginInput("isolated-high", "\uD800", "\\uD800"),
                new LoginInput("isolated-low", "\uDC00", "\\uDC00"),
                new LoginInput("reversed", "\uDE00\uD83D", "\\uDE00\\uD83D"),
                new LoginInput("high-with-outer-spaces", " \uD800 ", " \\uD800 "));
    }

    @Test
    void onlyTheExactNameAuthenticatesAndPersonalFieldsNeverBecomeAliases() throws SQLException {
        var account = register(NAME, EMAIL, PASSWORD);
        assertLoginSuccess(account);
        assertLoginError(null, "pass.client", PASSWORD, 401);
        assertLoginError(null, FULL_NAME, PASSWORD, 401);
        assertLoginError(null, EMAIL, PASSWORD, 401);
    }

    @Test
    void controlledVerificationFailureIsSafe500WithoutTokenWritesOrCredentialDamage() throws SQLException {
        var account = register(NAME, EMAIL, PASSWORD);
        assertLoginSuccess(account); // Real hashing/verification succeeds before the fault is armed.
        var hits = new AtomicInteger();
        doAnswer(invocation -> {
            if (account.password().equals(invocation.getArgument(0)) && account.hash().equals(invocation.getArgument(1))) {
                hits.incrementAndGet();
                throw new IllegalStateException("Controlled password verification failure");
            }
            return invocation.callRealMethod();
        }).when(verifier).matches(anyString(), anyString());
        try {
            assertLoginError(account, account.name(), account.password(), 500);
            assertThat(hits.get()).as("the armed verification boundary was reached exactly once").isEqualTo(1);
        } finally { reset(verifier); }
        assertLoginSuccess(account); // Restored real verifier; the persisted credential remains usable.
    }

    private Registered register(String name, String email, String password) throws SQLException {
        long registrations = calls(registration, "register"), hashes = calls(hasher, "hash"), issued = calls(tokens, "issueToken");
        var exchange = exchange("/customers", passwordJson(signup(name, email), password));
        var response = exchange.response();
        assertThat(response.statusCode()).isEqualTo(201);
        assertJson(response);
        assertNoAuthenticationHeaders(response);
        var body = jsonBody(response);
        assertThat(body.isObject() && body.size() == 2 && body.has("id") && body.has("name")
                && name.equals(body.path("name").textValue())).as("signup confirmation is only id and exact name").isTrue();
        var identity = body.path("id");
        assertThat(identity.isIntegralNumber() && identity.canConvertToLong() && identity.longValue() > 0).isTrue();
        long id = identity.longValue();
        assertOnlyNewAccountAndClient(exchange.before(), exchange.after(), id);
        assertThat(calls(registration, "register") == registrations + 1 && calls(hasher, "hash") == hashes + 1
                && calls(tokens, "issueToken") == issued).as("one real signup/hash and no automatic token").isTrue();
        var invocation = lastArguments(registration, "register");
        assertThat(invocation[0] instanceof CustomerRegistrationRequestDTO raw && password.equals(raw.password()))
                .as("actual JSON escapes reach the signup DTO as the exact chosen sequence").isTrue();
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select name, full_name, email, email_key, phone, birth_date, password_hash from tb_user_account where id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(name.equals(rows.getString(1)) && FULL_NAME.equals(rows.getString(2))
                        && email.equals(rows.getString(3)) && Arrays.equals(email.getBytes(StandardCharsets.UTF_8), rows.getBytes(4))
                        && PHONE.equals(rows.getString(5)) && LocalDate.of(2000, 2, 29).equals(rows.getObject(6, LocalDate.class)))
                        .as("accepted personal data and exact civil date visible by independent JDBC").isTrue();
                String hash = rows.getString(7);
                assertThat(hash != null && !password.equals(hash)).as("no plaintext password stored").isTrue();
                assertRegistrationProfile(hash);
                assertThat(rows.next()).isFalse();
                return new Registered(id, name, email, password, hash);
            }
        }
    }

    private void assertRejectedSignup(byte[] payload) {
        long before = calls(registration, "register"), hashes = calls(hasher, "hash"), issued = calls(tokens, "issueToken");
        var exchange = exchange("/customers", payload);
        assertError(exchange.response(), 400, "Bad Request", "Invalid customer registration data", "/customers");
        assertUnchanged(exchange.before(), exchange.after());
        assertThat(calls(registration, "register") == before && calls(hasher, "hash") == hashes
                && calls(tokens, "issueToken") == issued).as("invalid signup never reaches service, KDF or token").isTrue();
    }

    private void assertLoginSuccess(Registered account) {
        long auth = calls(authentication, "login"), verified = calls(verifier, "matches"), issued = calls(tokens, "issueToken");
        var exchange = exchange("/login", passwordJson(login(account.name()), account.password()));
        var response = exchange.response();
        assertThat(response.statusCode()).isEqualTo(200);
        assertJson(response);
        assertNoAuthenticationHeaders(response);
        var body = jsonBody(response);
        assertThat(body.isObject() && body.size() == 2 && body.has("accessToken") && body.has("tokenType")
                && body.path("accessToken").isTextual() && !body.path("accessToken").textValue().isBlank()
                && "Bearer".equals(body.path("tokenType").textValue())).as("unchanged exact login token fields").isTrue();
        try {
            var jwt = context.getBean(JwtDecoder.class).decode(body.path("accessToken").textValue());
            assertThat(Long.toString(account.id()).equals(jwt.getSubject()) && account.name().equals(jwt.getClaimAsString("name"))
                    && "HS256".equals(jwt.getHeaders().get("alg")) && jwt.getIssuedAt() != null && jwt.getExpiresAt() != null
                    && jwt.getExpiresAt().equals(jwt.getIssuedAt().plusSeconds(900))).as("signed identity and configured finite JWT lifetime").isTrue();
        } catch (JwtException | IllegalArgumentException failure) { throw new AssertionError("Login token could not be verified safely"); }
        assertThat(calls(authentication, "login") == auth + 1 && calls(verifier, "matches") == verified + 1
                && calls(tokens, "issueToken") == issued + 1).as("one real authentication, verification and token issuance").isTrue();
        assertVerificationArguments(account, account.password());
        assertUnchanged(exchange.before(), exchange.after());
    }

    private void assertLoginError(Registered account, String name, String password, int status) {
        assertLoginError(account, name, password, status, passwordJson(login(name), password));
    }

    private void assertLoginError(Registered account, String name, String password, int status, byte[] payload) {
        long auth = calls(authentication, "login"), verified = calls(verifier, "matches"), issued = calls(tokens, "issueToken");
        var exchange = exchange("/login", payload);
        assertError(exchange.response(), status, status == 401 ? "Unauthorized" : "Internal Server Error",
                status == 401 ? "Invalid credentials" : "Authentication could not be completed", "/login");
        assertThat(calls(authentication, "login") == auth + 1 && calls(tokens, "issueToken") == issued)
                .as("failed authentication never issues a token").isTrue();
        var raw = lastArguments(authentication, "login");
        assertThat(name.equals(raw[0]) && password.equals(raw[1])).as("failed login preserves both original fields").isTrue();
        assertThat(calls(verifier, "matches") == verified + (account == null ? 0 : 1))
                .as("only an exactly found account reaches stored-hash verification").isTrue();
        if (account != null) { assertVerificationArguments(account, password); }
        assertUnchanged(exchange.before(), exchange.after());
    }

    private void assertVerificationArguments(Registered account, String password) {
        Object[] raw = lastArguments(authentication, "login");
        Object[] checked = lastArguments(verifier, "matches");
        assertThat(account.name().equals(raw[0]) && password.equals(raw[1]) && password.equals(checked[0])
                && account.hash().equals(checked[1])).as("exact name, original password and unchanged stored hash").isTrue();
    }

    private Exchange exchange(String path, byte[] payload) {
        var before = database.snapshot();
        var response = send(jsonRequest(path).POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build());
        var after = database.snapshot();
        rememberNewIds(before, after, "TB_USER_ACCOUNT", createdAccounts);
        rememberNewIds(before, after, "TB_ORDER", createdOrders);
        return new Exchange(response, before, after);
    }

    private ObjectNode signup(String name, String email) {
        return mapper.createObjectNode().put("name", name).put("fullName", FULL_NAME).put("email", email)
                .put("phone", PHONE).put("birthDate", "2000-02-29");
    }

    private ObjectNode login(String name) { return mapper.createObjectNode().put("name", name); }

    private byte[] passwordJson(ObjectNode payload, String password) {
        payload.put("password", password);
        byte[] bytes = jsonBytes(payload);
        String wire = new String(bytes, StandardCharsets.UTF_8);
        if (password != null && password.indexOf('\0') >= 0) {
            assertThat(wire.contains("\\u0000") && wire.indexOf('\0') < 0).as("real JSON NUL escape on the wire").isTrue();
        }
        if (password != null && password.indexOf('\u001C') >= 0) {
            assertThat((wire.contains("\\u001c") || wire.contains("\\u001C")) && wire.indexOf('\u001C') < 0)
                    .as("real JSON U+001C escape on the wire").isTrue();
        }
        return bytes;
    }

    private static void assertUnits(String password, int points, int bytes) {
        assertThat(password.codePointCount(0, password.length())).as("independent literal point count").isEqualTo(points);
        assertThat(password.getBytes(StandardCharsets.UTF_8).length).as("independent literal UTF-8 byte count").isEqualTo(bytes);
    }

    private static long calls(Object bean, String method) {
        assertThat(mockingDetails(bean).isSpy()).as("HTTP observation delegates to a real bean").isTrue();
        return mockingDetails(bean).getInvocations().stream().filter(call -> method.equals(call.getMethod().getName())).count();
    }

    private static Object[] lastArguments(Object bean, String method) {
        var calls = mockingDetails(bean).getInvocations().stream().filter(call -> method.equals(call.getMethod().getName())).toList();
        assertThat(!calls.isEmpty()).as("expected real boundary was reached").isTrue();
        return calls.getLast().getArguments();
    }

    private void assertError(HttpResponse<byte[]> response, int status, String error, String message, String path) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertJson(response);
        assertNoAuthenticationHeaders(response);
        var expected = mapper.createObjectNode().put("status", status).put("error", error).put("message", message)
                .put("path", expectedContextPath() + path);
        assertThat(expected.equals(jsonBody(response))).as("exact safe error envelope without input, token or hash").isTrue();
    }

    private static void assertJson(HttpResponse<byte[]> response) {
        boolean json;
        try { json = MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""))
                .isCompatibleWith(MediaType.APPLICATION_JSON); }
        catch (IllegalArgumentException failure) { json = false; }
        assertThat(json).as("JSON response media type").isTrue();
    }

    private static void assertNoAuthenticationHeaders(HttpResponse<byte[]> response) {
        for (String header : List.of("Set-Cookie", "Authorization", "Location")) {
            assertThat(response.headers().allValues(header).isEmpty()).as("no authentication/location header: %s", header).isTrue();
        }
    }

    private static void assertRegistrationProfile(String hash) {
        assertThat(hash.length() == 97).as("full approved PHC length").isTrue();
        String[] parts = hash.split("\\$", -1);
        assertThat(parts.length == 6 && parts[0].isEmpty() && "argon2id".equals(parts[1]) && "v=19".equals(parts[2])
                && "m=19456,t=2,p=1".equals(parts[3])).as("unchanged approved registration profile").isTrue();
        try {
            byte[] salt = Base64.getDecoder().decode(parts[4]), digest = Base64.getDecoder().decode(parts[5]);
            assertThat(salt.length == 16 && digest.length == 32
                    && Base64.getEncoder().withoutPadding().encodeToString(salt).equals(parts[4])
                    && Base64.getEncoder().withoutPadding().encodeToString(digest).equals(parts[5]))
                    .as("canonical salt16 and digest32 without padding").isTrue();
        } catch (IllegalArgumentException failure) { throw new AssertionError("Stored registration PHC is not canonical"); }
    }

    private void assertOnlyNewAccountAndClient(Snapshot before, Snapshot after, long identity) throws SQLException {
        for (var entry : before.tables().entrySet()) {
            String table = entry.getKey();
            var previous = entry.getValue();
            var current = after.tables().get(table);
            if ("TB_USER_ACCOUNT".equals(table) || "TB_USER_ACCOUNT_ROLE".equals(table)) {
                String key = "TB_USER_ACCOUNT".equals(table) ? "ID" : "USER_ACCOUNT_ID";
                assertThat(current.columns().equals(previous.columns()) && current.rows().size() == previous.rows().size() + 1
                        && current.rows().stream().filter(row -> id(row, key) != identity).toList().equals(previous.rows()))
                        .as("one new account/role and every previous row preserved").isTrue();
            } else { assertThat(previous.equals(current)).as("unchanged table %s", table).isTrue(); }
        }
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select role from tb_user_account_role where user_account_id = ?")) {
            statement.setLong(1, identity);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next() && "CLIENT".equals(rows.getString(1)) && !rows.next()).as("exactly CLIENT").isTrue();
            }
        }
    }

    private static void rememberNewIds(Snapshot before, Snapshot after, String table, Set<Long> owned) {
        var previous = before.tables().get(table).rows().stream().map(row -> id(row, "ID")).collect(java.util.stream.Collectors.toSet());
        after.tables().get(table).rows().stream().map(row -> id(row, "ID")).filter(id -> !previous.contains(id)).forEach(owned::add);
    }

    private static long id(Row row, String key) { return ((Number) row.values().get(key).value()).longValue(); }

    @AfterEach
    void cleanOnlyIdsObservedAsNewAfterCompletedRequests() throws SQLException {
        assertNoCallerTransaction();
        try (var connection = openIndependentConnection()) {
            connection.setAutoCommit(false);
            try {
                for (long id : createdOrders) {
                    delete(connection, "delete from tb_payment where order_id = ?", id);
                    delete(connection, "delete from tb_order_item where order_id = ?", id);
                    delete(connection, "delete from tb_order where id = ?", id);
                }
                for (long id : createdAccounts) {
                    delete(connection, "delete from tb_user_account_role where user_account_id = ?", id);
                    delete(connection, "delete from tb_user_account where id = ?", id);
                }
                connection.commit();
                createdAccounts.clear();
                createdOrders.clear();
            } catch (SQLException | RuntimeException | Error failure) {
                connection.rollback();
                throw new AssertionError("Password scenario cleanup failed");
            }
        } catch (SQLException failure) { throw new SQLException("Password scenario cleanup could not complete"); }
    }

    private static void delete(Connection connection, String sql, long id) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) { statement.setLong(1, id); statement.executeUpdate(); }
    }

    private static Stream<PointWidth> closedSpaces() {
        // Independent transcription of all 25 approved spaces and their literal UTF-8 widths.
        return Stream.of(new PointWidth(0x0009, 1), new PointWidth(0x000A, 1), new PointWidth(0x000B, 1),
                new PointWidth(0x000C, 1), new PointWidth(0x000D, 1), new PointWidth(0x0020, 1),
                new PointWidth(0x0085, 2), new PointWidth(0x00A0, 2), new PointWidth(0x1680, 3),
                new PointWidth(0x2000, 3), new PointWidth(0x2001, 3), new PointWidth(0x2002, 3),
                new PointWidth(0x2003, 3), new PointWidth(0x2004, 3), new PointWidth(0x2005, 3),
                new PointWidth(0x2006, 3), new PointWidth(0x2007, 3), new PointWidth(0x2008, 3),
                new PointWidth(0x2009, 3), new PointWidth(0x200A, 3), new PointWidth(0x2028, 3),
                new PointWidth(0x2029, 3), new PointWidth(0x202F, 3), new PointWidth(0x205F, 3), new PointWidth(0x3000, 3));
    }

    private record PointWidth(int codePoint, int bytes) { }
    private record PasswordVector(String id, String password, int points, int bytes, List<String> variants) {
        @Override public String toString() { return id; }
    }
    private record RejectedPassword(String id, String password, int points, int bytes) {
        @Override public String toString() { return id; }
    }
    private record Malformed(String id, String escapes) { @Override public String toString() { return id; } }
    private record PasswordPair(String id, String first, String second) { @Override public String toString() { return id; } }
    private record LoginInput(String id, String raw, String jsonEscapes) { @Override public String toString() { return id; } }
    private record Registered(long id, String name, String email, String password, String hash) {
        @Override public String toString() { return "PasswordAccount[redacted]"; }
    }
    private record Exchange(HttpResponse<byte[]> response, Snapshot before, Snapshot after) {
        @Override public String toString() { return "PasswordHttpExchange[redacted]"; }
    }
}
