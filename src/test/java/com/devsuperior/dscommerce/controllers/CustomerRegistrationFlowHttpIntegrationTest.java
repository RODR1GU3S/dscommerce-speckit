package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.ORDER_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;

@RootHttpContext
class CustomerRegistrationFlowHttpIntegrationTest extends CustomerRegistrationFlowScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T032 authorship only. T034 first executes U1 after T033; no future controller import or mock. */
@Execution(ExecutionMode.SAME_THREAD)
abstract class CustomerRegistrationFlowScenarios extends CustomerRegistrationHttpTestSupport {

    private static final String FULL_NAME = "Ana da Silva";
    private static final String PHONE = "+5511999999999";
    private static final String PASSWORD = "Chosen-password-009";
    private final Set<Long> createdAccounts = new LinkedHashSet<>();
    private final Set<Long> createdOrders = new LinkedHashSet<>();

    @Test
    void anonymousSignupPersistsAcceptedDataWithOnlyClientAndAnExactConfirmation() throws SQLException {
        register(input("Ana", "Ana.Silva@Example.test", "ana.silva@example.test", PASSWORD), null);
    }

    @Test
    void signupLeavesTheCallerAnonymousUntilSeparateLoginAndOwnPurchase() throws SQLException {
        var other = fixture.createAccount("Other.client", LegacyCredential.SHORT, Set.of(UserRole.CLIENT));
        var product = fixture.createProduct("US1 own purchase", new BigDecimal("12.50"), List.of());
        var otherOrder = fixture.createOrder(other, ORDER_INSTANT,
                List.of(new ItemInput(product.id(), 1, new BigDecimal("7.50"))), null);
        var signup = input("Ana", "ana@example.test", "ana@example.test", PASSWORD);
        long accountId = register(signup, null);
        var afterSignup = database.snapshot();
        var purchase = mapper.createObjectNode();
        purchase.putArray("items").addObject().put("productId", product.id()).put("quantity", 2);

        // No cookie jar or bearer: even a request immediately after signup must remain anonymous.
        assertError(send(jsonRequest("/orders").GET().build()), 401, "Unauthorized",
                "Authentication required or invalid", "/orders");
        assertError(send(jsonRequest("/orders").POST(body(purchase)).build()), 401, "Unauthorized",
                "Authentication required or invalid", "/orders");
        assertUnchanged(afterSignup, database.snapshot());

        String token = login(accountId, signup.name(), signup.password());
        var empty = send(jsonRequest("/orders?page=0&size=10").header("Authorization", "Bearer " + token).GET().build());
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(jsonBody(empty).path("totalElements").asLong(-1)).isZero();
        assertThat(jsonBody(empty).path("content").isArray() && jsonBody(empty).path("content").isEmpty()).isTrue();
        assertUnchanged(afterSignup, database.snapshot());
        var exchange = exchange(jsonRequest("/orders").header("Authorization", "Bearer " + token)
                .POST(body(purchase)).build());
        assertThat(exchange.response().statusCode()).isEqualTo(201);
        var confirmation = jsonBody(exchange.response());
        long orderId = positiveId(confirmation);
        assertThat(confirmation.path("accountId").isIntegralNumber()
                && confirmation.path("accountId").longValue() == accountId).as("new account owns its purchase").isTrue();
        assertThat("WAITING_PAYMENT".equals(confirmation.path("status").textValue())
                && "25.00".equals(confirmation.path("total").textValue())).as("existing purchase contract").isTrue();
        assertTablesUnchangedExcept(exchange.before(), exchange.after(), Set.of("TB_ORDER", "TB_ORDER_ITEM"));
        for (var table : List.of("TB_ORDER", "TB_ORDER_ITEM")) {
            var previous = exchange.before().tables().get(table);
            var current = exchange.after().tables().get(table);
            String key = "TB_ORDER".equals(table) ? "ID" : "ORDER_ID";
            assertThat(current.columns().equals(previous.columns())).as("purchase keeps schema: %s", table).isTrue();
            assertThat(current.rows().size()).as("one own purchase row: %s", table).isEqualTo(previous.rows().size() + 1);
            assertThat(current.rows().stream().filter(row -> id(row, key) != orderId).toList().equals(previous.rows()))
                    .as("purchase preserves existing rows: %s", table).isTrue();
        }
        assertOrderRows(accountId, orderId, product.id());

        var page = send(jsonRequest("/orders?page=0&size=10").header("Authorization", "Bearer " + token).GET().build());
        assertThat(page.statusCode()).isEqualTo(200);
        var content = jsonBody(page).path("content");
        assertThat(jsonBody(page).path("totalElements").asLong(-1)).isEqualTo(1);
        assertThat(content.isArray() && content.size() == 1 && content.get(0).path("id").asLong() == orderId)
                .as("only the new account's own order is listed").isTrue();
        var detail = send(jsonRequest("/orders/" + orderId).header("Authorization", "Bearer " + token).GET().build());
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(jsonBody(detail).path("id").asLong()).isEqualTo(orderId);
        assertError(send(jsonRequest("/orders/" + otherOrder.id()).header("Authorization", "Bearer " + token).GET().build()),
                404, "Not Found", "Order not found", "/orders/" + otherOrder.id());
        assertError(send(jsonRequest("/orders").GET().build()), 401, "Unauthorized",
                "Authentication required or invalid", "/orders");
        assertUnchanged(exchange.after(), database.snapshot());
    }

    @Test
    void onlyExactNameAuthenticatesAndAnEmailShapedLegacyNameKeepsItsOwnLookup() throws SQLException {
        String email = "legacy.identifier@example.test";
        var signup = input("Ana", email, email, PASSWORD);
        long id = register(signup, null);
        var beforeLogin = database.snapshot();
        login(id, "Ana", PASSWORD);
        assertInvalidCredentials("ANA", PASSWORD);
        assertInvalidCredentials(FULL_NAME, PASSWORD);
        assertInvalidCredentials(email, PASSWORD);
        assertUnchanged(beforeLogin, database.snapshot());

        // The same email text can independently be a legacy name; it must never become an alias.
        var legacy = fixture.createAccount(email, LegacyCredential.SHORT, Set.of(UserRole.CLIENT));
        var beforeLegacyLogin = database.snapshot();
        assertInvalidCredentials(email, PASSWORD);
        login(legacy.id(), email, legacy.password());
        assertUnchanged(beforeLegacyLogin, database.snapshot());
    }

    @Test
    void caseDistinctNamesCreateDistinctAccountsAndKeepTheirSeparateCredentials() throws SQLException {
        var upper = input("Ana", "upper@example.test", "upper@example.test", "Upper-name-password-009");
        var lower = input("ana", "lower@example.test", "lower@example.test", "Lower-name-password-009");
        long upperId = register(upper, null);
        long lowerId = register(lower, null);
        assertThat(upperId).isNotEqualTo(lowerId);
        var beforeLogin = database.snapshot();
        login(upperId, "Ana", upper.password());
        login(lowerId, "ana", lower.password());
        assertInvalidCredentials("Ana", lower.password());
        assertInvalidCredentials("ana", upper.password());
        assertUnchanged(beforeLogin, database.snapshot());
    }

    @Test
    void homonymsMaySharePhoneAndBirthDateWithDistinctNamesAndEmails() throws SQLException {
        long first = register(input("Ana.first", "first@example.test", "first@example.test", PASSWORD), null);
        long second = register(input("Ana.second", "second@example.test", "second@example.test", PASSWORD), null);
        assertThat(first).isNotEqualTo(second);
        // register inspects the same literal fullName, phone and birthDate for both committed rows.
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(BearerMode.class)
    void signupIsPublicForEveryBearerModeWithoutInheritingIdentityOrPrivileges(BearerMode mode) throws SQLException {
        String token = bearer(mode);
        register(input("Public.client", "public@example.test", "public@example.test", PASSWORD), token);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("forbiddenAttempts")
    void forbiddenIdentityAndPrivilegeFieldsAreRejectedWithoutMutatingAnyAccount(ForbiddenAttempt attempt) {
        var victim = fixture.createAccount("Existing.victim", LegacyCredential.LONG, Set.of(UserRole.ADMIN, UserRole.CLIENT));
        String token = bearer(attempt.bearer());
        var payload = request(input("Attempted.new", "attempt@example.test", "attempt@example.test", PASSWORD));
        switch (attempt.field()) {
            case ID -> payload.put("id", victim.id());
            case CLIENT_ROLES -> payload.putArray("roles").add("CLIENT");
            case ADMIN_ROLES -> payload.putArray("roles").add("ADMIN");
            case ROLE -> payload.put("role", "ADMIN");
            case PROFILE -> payload.put("profile", "ADMIN");
            case PASSWORD_HASH -> payload.put("passwordHash", LegacyCredential.SHORT.passwordHash());
            case TOKEN -> payload.put("token", "discarded-input-token-009");
        }
        var exchange = exchange(post("/customers", payload, token));
        assertError(exchange.response(), 400, "Bad Request", "Invalid customer registration data", "/customers");
        assertUnchanged(exchange.before(), exchange.after());
    }

    static Stream<ForbiddenAttempt> forbiddenAttempts() {
        return Arrays.stream(ForbiddenField.values()).flatMap(field -> Arrays.stream(BearerMode.values())
                .map(mode -> new ForbiddenAttempt(field, mode)));
    }

    @Test
    void queryRejectionUsesTheServletContextPathWithoutEchoingTheQuery() {
        var exchange = exchange(post("/customers?trace=private-query-009",
                request(input("Ana", "ana@example.test", "ana@example.test", PASSWORD)), null));
        assertError(exchange.response(), 400, "Bad Request", "Invalid customer registration data", "/customers");
        assertUnchanged(exchange.before(), exchange.after());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "PATCH"})
    void unsupportedMethodsKeepNative405AndAllowPost(String method) {
        var exchange = exchange(jsonRequest("/customers?trace=private-query-009")
                .method(method, HttpRequest.BodyPublishers.noBody()).build());
        assertNative(exchange, 405);
        var allowed = Arrays.stream(String.join(",", exchange.response().headers().allValues("Allow")).split(","))
                .map(String::trim).collect(java.util.stream.Collectors.toSet());
        assertThat(allowed).containsExactly("POST");
        if ("HEAD".equals(method)) { assertThat(exchange.response().body().length).isZero(); }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"text/plain", "application/xml"})
    void unacceptableRepresentationsKeepNative406WithoutForcingJson(String accept) {
        var exchange = exchange(jsonRequest("/customers").setHeader("Accept", accept)
                .POST(body(request(input("Ana", "ana@example.test", "ana@example.test", PASSWORD)))).build());
        assertNative(exchange, 406);
        if (exchange.response().body().length > 0) {
            assertThat(compatibleContentType(exchange.response(), MediaType.parseMediaType(accept)))
                    .as("a native body must respect the requested representation").isTrue();
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"text/plain", "application/xml"})
    void unsupportedRequestTypesKeepNative415AndAdvertiseJson(String contentType) {
        var exchange = exchange(jsonRequest("/customers").setHeader("Content-Type", contentType)
                .POST(body(request(input("Ana", "ana@example.test", "ana@example.test", PASSWORD)))).build());
        assertNative(exchange, 415);
        assertThat(String.join(",", exchange.response().headers().allValues("Accept")).contains("application/json"))
                .as("native supported request media type").isTrue();
    }

    private long register(Signup input, String bearer) throws SQLException {
        var exchange = exchange(post("/customers", request(input), bearer));
        var response = exchange.response();
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(compatibleContentType(response, MediaType.APPLICATION_JSON)).isTrue();
        assertNoAuthenticationHeaders(response);
        assertThat(response.headers().allValues("WWW-Authenticate").isEmpty()).as("public signup has no challenge").isTrue();
        assertThat(response.headers().allValues("Location").isEmpty()).as("no invented account GET location").isTrue();
        var confirmation = jsonBody(response);
        assertThat(confirmation.isObject() && confirmation.size() == 2 && confirmation.has("id") && confirmation.has("name"))
                .as("confirmation contains only id/name, never personal data, roles, password, hash or token").isTrue();
        long id = positiveId(confirmation);
        assertThat(input.name().equals(confirmation.path("name").textValue())).as("exact chosen login in confirmation").isTrue();
        assertOnlyNewAccountAndRole(exchange.before(), exchange.after(), id);
        assertAccountRows(id, input);
        return id;
    }

    private void assertAccountRows(long id, Signup input) throws SQLException {
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select name, full_name, email, email_key, phone, birth_date, password_hash from tb_user_account where id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).as("response identifies an externally visible committed account").isTrue();
                assertThat(input.name().equals(rows.getString(1)) && FULL_NAME.equals(rows.getString(2))
                        && input.email().equals(rows.getString(3)) && PHONE.equals(rows.getString(5))
                        && LocalDate.of(2000, 2, 29).equals(rows.getObject(6, LocalDate.class)))
                        .as("accepted personal data, only permitted external trimming").isTrue();
                // ASCII literal expectations, independent of RegistrationInputRules/emailKey implementation.
                assertThat(Arrays.equals(input.expectedEmailKey().getBytes(StandardCharsets.UTF_8), rows.getBytes(4)))
                        .as("independent binary email key").isTrue();
                String hash = rows.getString(7);
                assertThat(hash != null && hash.length() == 97 && hash.startsWith("$argon2id$v=19$m=19456,t=2,p=1$")
                        && !input.password().equals(hash)).as("new account stores the approved hash, never plaintext").isTrue();
                assertThat(rows.next()).isFalse();
            }
            try (var roles = connection.prepareStatement("select role from tb_user_account_role where user_account_id = ?")) {
                roles.setLong(1, id);
                try (var rows = roles.executeQuery()) {
                    assertThat(rows.next()).as("committed CLIENT row accompanies account").isTrue();
                    assertThat("CLIENT".equals(rows.getString(1))).as("exactly CLIENT, never caller profiles").isTrue();
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }

    private void assertOrderRows(long accountId, long orderId, long productId) throws SQLException {
        try (var connection = openIndependentConnection(); var order = connection.prepareStatement(
                "select buyer_id, status from tb_order where id = ?")) {
            order.setLong(1, orderId);
            try (var rows = order.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(accountId);
                assertThat("WAITING_PAYMENT".equals(rows.getString(2)) && !rows.next()).isTrue();
            }
            try (var item = connection.prepareStatement(
                    "select product_id, quantity, unit_price from tb_order_item where order_id = ?")) {
                item.setLong(1, orderId);
                try (var rows = item.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1) == productId && rows.getInt(2) == 2
                            && new BigDecimal("12.50").compareTo(rows.getBigDecimal(3)) == 0 && !rows.next())
                            .as("own purchase retains the selected product, quantity and historical price").isTrue();
                }
            }
        }
    }

    private String bearer(BearerMode mode) {
        if (mode == BearerMode.ABSENT) { return null; }
        if (mode == BearerMode.INVALID) { return "invalid-feature009-bearer"; }
        var caller = fixture.createAccount("Existing.caller", LegacyCredential.SHORT,
                mode == BearerMode.CLIENT ? Set.of(UserRole.CLIENT) : Set.of(UserRole.ADMIN));
        return login(caller.id(), caller.name(), caller.password());
    }

    private String login(long id, String name, String password) {
        String token = realHttpLogin(id, name, password);
        // Existing JWT issuance/decoder use wall time; protected account conversion uses this test Clock.
        clock.setInstant(context.getBean(JwtDecoder.class).decode(token).getIssuedAt().plusSeconds(1));
        return token;
    }

    private void assertInvalidCredentials(String name, String password) {
        var login = mapper.createObjectNode().put("name", name).put("password", password);
        assertError(send(post("/login", login, null)), 401, "Unauthorized", "Invalid credentials", "/login");
    }

    private ObjectNode request(Signup input) {
        // Explicit six-string JSON; never serialize the WRITE_ONLY DTO as an empty request.
        return mapper.createObjectNode().put("name", input.name()).put("fullName", "\u00A0 " + FULL_NAME + " \u3000")
                .put("email", "\u00A0 " + input.email() + " \u3000").put("phone", PHONE)
                .put("birthDate", "2000-02-29").put("password", input.password());
    }

    private static Signup input(String name, String email, String expectedEmailKey, String password) {
        return new Signup(name, email, expectedEmailKey, password);
    }

    private HttpRequest post(String path, ObjectNode payload, String token) {
        var request = jsonRequest(path).POST(body(payload));
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        return request.build();
    }

    private HttpRequest.BodyPublisher body(ObjectNode payload) {
        return HttpRequest.BodyPublishers.ofByteArray(jsonBytes(payload));
    }

    private Exchange exchange(HttpRequest request) {
        var before = database.snapshot();
        var response = send(request); // Synchronous completion only; no timeout/commit-indeterminate inference.
        var after = database.snapshot();
        rememberNewIds(before, after, "TB_USER_ACCOUNT", createdAccounts);
        rememberNewIds(before, after, "TB_ORDER", createdOrders);
        return new Exchange(response, before, after);
    }

    private static void rememberNewIds(Snapshot before, Snapshot after, String table, Set<Long> owned) {
        var previous = before.tables().get(table).rows().stream().map(row -> id(row, "ID")).collect(java.util.stream.Collectors.toSet());
        after.tables().get(table).rows().stream().map(row -> id(row, "ID"))
                .filter(value -> !previous.contains(value)).forEach(owned::add);
    }

    private static void assertOnlyNewAccountAndRole(Snapshot before, Snapshot after, long id) {
        assertTablesUnchangedExcept(before, after, Set.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE"));
        for (var table : List.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE")) {
            var previous = before.tables().get(table);
            var current = after.tables().get(table);
            String key = "TB_USER_ACCOUNT".equals(table) ? "ID" : "USER_ACCOUNT_ID";
            assertThat(current.columns().equals(previous.columns())).as("schema unchanged: %s", table).isTrue();
            assertThat(current.rows().size()).as("only one new row: %s", table).isEqualTo(previous.rows().size() + 1);
            assertThat(current.rows().stream().filter(row -> id(row, key) != id).toList().equals(previous.rows()))
                    .as("all previous rows preserved: %s", table).isTrue();
        }
    }

    private static void assertTablesUnchangedExcept(Snapshot before, Snapshot after, Set<String> permitted) {
        before.tables().forEach((table, rows) -> {
            if (!permitted.contains(table)) {
                assertThat(rows.equals(after.tables().get(table))).as("unchanged table %s", table).isTrue();
            }
        });
    }

    private void assertError(HttpResponse<byte[]> response, int status, String error, String message, String path) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(compatibleContentType(response, MediaType.APPLICATION_JSON)).isTrue();
        var expected = mapper.createObjectNode().put("status", status).put("error", error).put("message", message)
                .put("path", expectedContextPath() + path);
        assertThat(expected.equals(jsonBody(response))).as("exact safe error, context included, no query/value/extra fields").isTrue();
        assertNoAuthenticationHeaders(response);
    }

    private static void assertNoAuthenticationHeaders(HttpResponse<byte[]> response) {
        assertThat(response.headers().allValues("Set-Cookie").isEmpty()).as("no authentication/session cookie").isTrue();
        assertThat(response.headers().allValues("Authorization").isEmpty()).as("no authentication response header").isTrue();
    }

    private void assertNative(Exchange exchange, int status) {
        assertThat(exchange.response().statusCode()).isEqualTo(status);
        assertThat(exchange.response().headers().allValues("WWW-Authenticate").isEmpty()).isTrue();
        assertNoAuthenticationHeaders(exchange.response());
        assertUnchanged(exchange.before(), exchange.after());
        // Native bodies are deliberately not parsed or forced into the registration error envelope.
    }

    private static boolean compatibleContentType(HttpResponse<byte[]> response, MediaType expected) {
        try {
            return MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""))
                    .isCompatibleWith(expected);
        } catch (IllegalArgumentException invalidHeader) { return false; }
    }

    private static long positiveId(JsonNode body) {
        var value = body.path("id");
        assertThat(value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0)
                .as("positive integral generated identity").isTrue();
        return value.longValue();
    }

    private static long id(Row row, String key) {
        Object value = row.values().get(key).value();
        assertThat(value instanceof Number).as("numeric snapshot identity").isTrue();
        return ((Number) value).longValue();
    }

    /** Runs before superclass fixture cleanup. Only IDs observed as new across our completed calls. */
    @AfterEach
    void cleanOnlyRowsCreatedByThisScenario() throws SQLException {
        assertNoCallerTransaction();
        if (createdAccounts.isEmpty() && createdOrders.isEmpty()) { return; }
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
                createdOrders.clear();
                createdAccounts.clear();
            } catch (SQLException | RuntimeException | Error failure) {
                connection.rollback();
                throw new AssertionError("Cleanup of scenario-owned rows failed");
            }
        } catch (SQLException failure) {
            throw new SQLException("Cleanup of scenario-owned rows could not complete");
        }
    }

    private static void delete(Connection connection, String sql, long id) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    enum BearerMode { ABSENT, INVALID, CLIENT, ADMIN }
    enum ForbiddenField { ID, CLIENT_ROLES, ADMIN_ROLES, ROLE, PROFILE, PASSWORD_HASH, TOKEN }
    record ForbiddenAttempt(ForbiddenField field, BearerMode bearer) { }
    private record Signup(String name, String email, String expectedEmailKey, String password) {
        @Override public String toString() { return "Signup[redacted]"; }
    }
    private record Exchange(HttpResponse<byte[]> response, Snapshot before, Snapshot after) {
        @Override public String toString() { return "RegistrationExchange[redacted]"; }
    }
}
