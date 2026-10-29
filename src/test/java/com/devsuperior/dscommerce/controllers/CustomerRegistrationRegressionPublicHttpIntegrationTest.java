package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.services.AuthenticationService;
import com.devsuperior.dscommerce.services.exceptions.AuthenticationProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;

@RootHttpContext
class CustomerRegistrationRegressionPublicHttpIntegrationTest extends CustomerRegistrationRegressionPublicScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T089 authorship only. T092 owns compilation, providers, HTTP execution and observed quantities. */
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class CustomerRegistrationRegressionPublicScenarios extends CustomerRegistrationHttpTestSupport {
    private static final String NEW_NAME = "New.public";
    private static final String NEW_PASSWORD = "  New-public-password-009  ";
    private static final String IMAGE = "https://example.invalid/customer009.jpg";
    private static final String DESCRIPTION = "Catalogo previo ao cadastro009";
    private final Set<Long> createdAccounts = new LinkedHashSet<>();
    private boolean safeToClean = true;

    @Autowired private JwtDecoder decoder;
    @SpyBean private AuthenticationService authentication;
    @SpyBean private PasswordEncoder bcrypt;

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RegistrationOutcome.class)
    void catalogPagesAndDetailsRemainPublicAndUnchangedAfterRegistration(RegistrationOutcome outcome) {
        var catalog = prepareCatalog();
        for (boolean invalidBearer : List.of(false, true)) { assertCatalog(catalog, invalidBearer); }
        register(outcome);
        var afterRegistration = database.snapshot();
        for (boolean invalidBearer : List.of(false, true)) { assertCatalog(catalog, invalidBearer); }
        assertUnchanged(afterRegistration, database.snapshot());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RegistrationOutcome.class)
    void literalFiltersKeepAccentsWhitespaceWildcardsAndFilteredTotalsAfterRegistration(RegistrationOutcome outcome) {
        var catalog = prepareCatalog();
        assertFilters(catalog);
        register(outcome);
        var afterRegistration = database.snapshot();
        assertFilters(catalog);
        assertUnchanged(afterRegistration, database.snapshot());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RegistrationOutcome.class)
    void publicPaginationBoundsKeepTheirOriginalMessagesAfterRegistration(RegistrationOutcome outcome) {
        prepareCatalog();
        assertPaginationErrors();
        register(outcome);
        var afterRegistration = database.snapshot();
        assertPaginationErrors();
        assertUnchanged(afterRegistration, database.snapshot());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("legacyLoginCases")
    void legacyLoginKeepsExactNamesCredentialsJwtAndProfilesBeforeAndAfterRegistration(LegacyLoginCase input)
            throws SQLException {
        var catalog = prepareCatalog();
        // Spaces remain part of this existing login name; it is not valid new-signup input.
        var legacy = fixture.createAccount("  Legacy.Exact  ", input.credential(), input.roles());
        assertLegacyLogin(legacy, catalog);
        register(input.outcome());
        var afterRegistration = database.snapshot();
        assertLegacyLogin(legacy, catalog);
        assertUnchanged(afterRegistration, database.snapshot());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RegistrationOutcome.class)
    void loginParserRetainsInvalidInputEnvelopeWithoutAuthenticatingAfterRegistration(RegistrationOutcome outcome) {
        prepareCatalog();
        assertInvalidLoginDocuments();
        register(outcome);
        var afterRegistration = database.snapshot();
        assertInvalidLoginDocuments();
        assertUnchanged(afterRegistration, database.snapshot());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RegistrationOutcome.class)
    void controlledLoginProcessingFailureKeepsItsSafe500AndCanRecoverWithoutWrites(RegistrationOutcome outcome) {
        var catalog = prepareCatalog();
        assertLogin(catalog.reserved().id(), catalog.reserved().name(), catalog.reserved().password());
        register(outcome);
        var beforeFailure = database.snapshot();
        long calls = loginCalls();
        // API error-contract control only: no encoder/database/network incident is claimed.
        doThrow(new AuthenticationProcessingException("Authentication could not be completed",
                new IllegalStateException("Controlled authentication failure")))
                .when(authentication).login(anyString(), anyString());
        try {
            assertError(login(catalog.reserved().name(), catalog.reserved().password()),
                    500, "Internal Server Error", "Authentication could not be completed", "/login");
            assertThat(loginCalls()).as("one login attempt, no automatic retry").isEqualTo(calls + 1);
            assertUnchanged(beforeFailure, database.snapshot());
        } finally { reset(authentication); }
        assertLogin(catalog.reserved().id(), catalog.reserved().name(), catalog.reserved().password());
        assertUnchanged(beforeFailure, database.snapshot());
    }

    @Test
    void newlyRegisteredAccountUsesItsExactPasswordAndNameWithoutCreatingLoginAliases() {
        var catalog = prepareCatalog();
        long id = register(RegistrationOutcome.ACCEPTED);
        var beforeLogin = database.snapshot();
        String token = assertLogin(id, NEW_NAME, NEW_PASSWORD);
        assertError(login(NEW_NAME, NEW_PASSWORD.trim()), 401, "Unauthorized", "Invalid credentials", "/login");
        for (String alias : List.of("new.public", NEW_NAME + " ", "Public Person", "New.Public@Example.test")) {
            assertError(login(alias, NEW_PASSWORD), 401, "Unauthorized", "Invalid credentials", "/login");
        }
        assertPage(get("/products", token), catalog.all(), 0, 12, 6);
        assertUnchanged(beforeLogin, database.snapshot());
    }

    static Stream<LegacyLoginCase> legacyLoginCases() {
        return Stream.of(RegistrationOutcome.values()).flatMap(outcome ->
                Stream.of(LegacyCredential.SHORT, LegacyCredential.LONG).flatMap(credential -> Stream.of(
                        new LegacyLoginCase(outcome, credential, "NONE", Set.of()),
                        new LegacyLoginCase(outcome, credential, "CLIENT", Set.of(UserRole.CLIENT)),
                        new LegacyLoginCase(outcome, credential, "ADMIN", Set.of(UserRole.ADMIN)),
                        new LegacyLoginCase(outcome, credential, "BOTH", Set.of(UserRole.CLIENT, UserRole.ADMIN)))));
    }

    private Catalog prepareCatalog() {
        if (!safeToClean) {
            fixture = null;
            throw new AssertionError("Previous HTTP completion is unproved; fixture setup is withheld");
        }
        var scenario = fixture.prepareScenario();
        var reserved = fixture.createAccount("Reserved.public", LegacyCredential.SHORT, Set.of(UserRole.ADMIN));
        var alpha = fixture.createCategory("Alpha publica");
        var first = fixture.createProduct("A Café kit_ 10%", new BigDecimal("9.99"),
                List.of(scenario.category(), alpha));
        var second = fixture.createProduct("A Café kit_ 10%", new BigDecimal("11.50"),
                List.of(alpha, scenario.category()));
        var plain = fixture.createProduct("A Cafe kitX 100", new BigDecimal("3.25"), List.of());
        var slash = fixture.createProduct("A Kit/  Duplo", new BigDecimal("4.00"), List.of());
        assertThat(first.id()).isLessThan(second.id());
        // Explicit expected order: original name ASC, equal names by ID, never a production comparator.
        return new Catalog(reserved, List.of(
                new ProductExpected(plain.id(), "A Cafe kitX 100", new BigDecimal("3.25"), List.of()),
                new ProductExpected(first.id(), "A Café kit_ 10%", new BigDecimal("9.99"),
                        List.of("Alpha publica", "Catalogo anterior009")),
                new ProductExpected(second.id(), "A Café kit_ 10%", new BigDecimal("11.50"),
                        List.of("Alpha publica", "Catalogo anterior009")),
                new ProductExpected(slash.id(), "A Kit/  Duplo", new BigDecimal("4.00"), List.of()),
                new ProductExpected(scenario.products().get(0).id(), "Produto anterior A", new BigDecimal("99.00"),
                        List.of("Catalogo anterior009")),
                new ProductExpected(scenario.products().get(1).id(), "Produto anterior B", new BigDecimal("12.00"),
                        List.of("Catalogo anterior009"))));
    }

    private long register(RegistrationOutcome outcome) {
        var before = database.snapshot();
        String name = outcome == RegistrationOutcome.CONFLICT ? "Reserved.public" : NEW_NAME;
        var body = mapper.createObjectNode().put("name", name).put("fullName", "Public Person")
                .put("email", "New.Public@Example.test").put("phone", outcome == RegistrationOutcome.INVALID ? "123" : "+5511999999999")
                .put("birthDate", "2000-02-29").put("password", NEW_PASSWORD);
        HttpResponse<byte[]> response;
        try {
            response = exchange(jsonRequest("/customers").POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(body))).build());
        } finally {
            if (safeToClean) { rememberNewAccounts(before, database.snapshot()); }
        }
        if (outcome != RegistrationOutcome.ACCEPTED) {
            boolean conflict = outcome == RegistrationOutcome.CONFLICT;
            assertError(response, conflict ? 409 : 400, conflict ? "Conflict" : "Bad Request",
                    conflict ? "Customer registration not available" : "Invalid customer registration data", "/customers");
            assertUnchanged(before, database.snapshot());
            return 0;
        }
        assertThat(response.statusCode()).isEqualTo(201);
        assertJsonHeaders(response);
        var confirmation = jsonBody(response);
        assertFields(confirmation, Set.of("id", "name"));
        assertThat(confirmation.path("id").isIntegralNumber() && confirmation.path("id").canConvertToLong()
                && confirmation.path("id").longValue() > 0 && NEW_NAME.equals(confirmation.path("name").textValue()))
                .as("only a new ID and exact name, with no authentication or personal data").isTrue();
        long id = confirmation.path("id").longValue();
        assertOnlyNewAccountAndClient(before, database.snapshot(), id);
        return id;
    }

    private void assertCatalog(Catalog catalog, boolean invalidBearer) {
        String bearer = invalidBearer ? "deliberately-invalid-public-token" : null;
        assertPage(get("/products", bearer), catalog.all(), 0, 12, 6);
        assertPage(get("/products?page=0&size=2", bearer), catalog.all().subList(0, 2), 0, 2, 6);
        assertPage(get("/products?page=1&size=2", bearer), catalog.all().subList(2, 4), 1, 2, 6);
        assertPage(get("/products?page=3&size=2", bearer), List.of(), 3, 2, 6);
        for (var product : List.of(catalog.all().get(1), catalog.all().get(3))) {
            // A name query never restricts detail lookup by ID.
            var response = get("/products/" + product.id() + "?name=does-not-match", bearer);
            assertThat(response.statusCode()).isEqualTo(200);
            assertJsonHeaders(response);
            var body = jsonBody(response);
            assertFields(body, Set.of("id", "name", "description", "image", "price", "categories"));
            assertProduct(body, product);
            assertThat(DESCRIPTION.equals(body.path("description").textValue())
                    && mapper.valueToTree(product.categories()).equals(body.path("categories")))
                    .as("original description and distinct sorted category names remain public").isTrue();
        }
        assertError(get("/products/" + Long.MAX_VALUE, bearer), 404, "Not Found", "Product not found",
                "/products/" + Long.MAX_VALUE);
    }

    private void assertFilters(Catalog catalog) {
        var all = catalog.all();
        var cafes = all.subList(1, 3);
        var cases = List.of(
                new FilterCase("name=%20CAF%C3%89%20&page=0&size=1", cafes.subList(0, 1), 0, 1, 2),
                new FilterCase("name=CAF%C3%89&page=1&size=1", cafes.subList(1, 2), 1, 1, 2),
                new FilterCase("name=CAF%C3%89&page=2&size=1", List.of(), 2, 1, 2),
                new FilterCase("name=CAFE", all.subList(0, 1), 0, 12, 1),
                new FilterCase("name=kit_", cafes, 0, 12, 2),
                new FilterCase("name=%25", cafes, 0, 12, 2),
                new FilterCase("name=kit%2F%20%20duplo", all.subList(3, 4), 0, 12, 1),
                new FilterCase("name=kit%2F%20duplo", List.of(), 0, 12, 0),
                new FilterCase("name=%C2%A0%E2%80%83", all, 0, 12, 6),
                new FilterCase("name=no-match", List.of(), 0, 12, 0));
        for (var input : cases) {
            assertPage(get("/products?" + input.query(), "irrelevant-invalid-bearer"),
                    input.content(), input.page(), input.size(), input.total());
        }
        assertPage(get("/products", null), all, 0, 12, 6); // No filter state carried between requests.
    }

    private void assertPaginationErrors() {
        assertError(get("/products?name=kit&page=-1", null), 400, "Bad Request",
                "page must be greater than or equal to 0", "/products");
        for (int size : List.of(0, 51)) {
            assertError(get("/products?name=kit&size=" + size, "irrelevant-invalid-bearer"), 400, "Bad Request",
                    "size must be between 1 and 50", "/products");
        }
    }

    private void assertLegacyLogin(Account legacy, Catalog catalog) throws SQLException {
        var before = database.snapshot();
        assertLegacyStored(legacy);
        int bytes = legacy.password().getBytes(StandardCharsets.UTF_8).length;
        assertThat(legacy.credential() == LegacyCredential.SHORT ? bytes == 3 : bytes == 98)
                .as("fixed credentials stay outside signup minimum or byte maximum").isTrue();
        long encodes = encoderCalls("encode");
        long upgrades = encoderCalls("upgradeEncoding");
        String token = assertLogin(legacy.id(), legacy.name(), legacy.password());
        for (String wrongName : List.of(legacy.name().trim(), "  legacy.exact  ", "Absent.public")) {
            assertError(login(wrongName, legacy.password()), 401, "Unauthorized", "Invalid credentials", "/login");
        }
        var wrongPassword = login(legacy.name(), "Wrong-public-password-009");
        var absent = login("Absent.public", legacy.password());
        assertError(wrongPassword, 401, "Unauthorized", "Invalid credentials", "/login");
        assertError(absent, 401, "Unauthorized", "Invalid credentials", "/login");
        assertThat(jsonBody(wrongPassword).equals(jsonBody(absent))).as("unknown and wrong credentials are indistinguishable").isTrue();
        assertThat(encoderCalls("encode")).as("no BCrypt re-encode during login").isEqualTo(encodes);
        assertThat(encoderCalls("upgradeEncoding")).as("no automatic hash upgrade").isEqualTo(upgrades);
        assertPage(get("/products", token), catalog.all(), 0, 12, 6);
        assertLegacyStored(legacy);
        assertUnchanged(before, database.snapshot());
    }

    private void assertInvalidLoginDocuments() {
        // Existing guaranteed invalid shapes only; no new duplicate/trailing-JSON policy for login.
        var documents = List.of("", "{", "[]", "null", "{}",
                "{\"password\":\"U*U\"}", "{\"name\":\"Reserved.public\"}",
                "{\"name\":null,\"password\":\"U*U\"}", "{\"name\":\"Reserved.public\",\"password\":null}",
                "{\"name\":\" \",\"password\":\"U*U\"}", "{\"name\":\"Reserved.public\",\"password\":\" \\t \"}",
                "{\"name\":123,\"password\":\"U*U\"}", "{\"name\":\"Reserved.public\",\"password\":true}",
                "{\"name\":\"Reserved.public\",\"password\":{}}",
                "{\"name\":\"Reserved.public\",\"password\":\"U*U\",\"role\":\"ADMIN\"}");
        var before = database.snapshot();
        long calls = loginCalls();
        for (String body : documents) {
            assertError(exchange(jsonRequest("/login").header("Authorization", "Bearer deliberately-invalid")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build()),
                    400, "Bad Request", "Invalid login data", "/login");
            assertThat(loginCalls()).as("invalid login JSON or fields never reach authentication").isEqualTo(calls);
        }
        assertUnchanged(before, database.snapshot());
    }

    private String assertLogin(long id, String name, String password) {
        var response = login(name, password);
        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonHeaders(response);
        var body = jsonBody(response);
        assertFields(body, Set.of("accessToken", "tokenType"));
        String token = body.path("accessToken").textValue();
        assertThat("Bearer".equals(body.path("tokenType").textValue()) && token != null && !token.isBlank())
                .as("only a bearer credential in the existing wire fields").isTrue();
        try {
            var jwt = decoder.decode(token);
            assertThat("HS256".equals(jwt.getHeaders().get("alg")) && Long.toString(id).equals(jwt.getSubject())
                    && name.equals(jwt.getClaimAsString("name")) && jwt.getIssuedAt() != null && jwt.getExpiresAt() != null
                    && Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).equals(Duration.ofMinutes(15)))
                    .as("verified signed JWT identifies the exact committed account and keeps the configured finite TTL").isTrue();
        } catch (JwtException invalidToken) { throw new AssertionError("Returned login token could not be verified"); }
        return token;
    }

    private HttpResponse<byte[]> login(String name, String password) {
        var body = mapper.createObjectNode().put("name", name).put("password", password);
        return exchange(jsonRequest("/login").header("Authorization", "Bearer deliberately-invalid")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(body))).build());
    }

    private HttpResponse<byte[]> get(String path, String token) {
        var request = jsonRequest(path).GET();
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        return exchange(request.build());
    }

    private HttpResponse<byte[]> exchange(HttpRequest request) {
        safeToClean = false;
        var result = send(request);
        safeToClean = true; // No asynchronous actor is created by these synchronous scenarios.
        return result;
    }

    private void assertPage(HttpResponse<byte[]> response, List<ProductExpected> products, int page, int size, long total) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonHeaders(response);
        var body = jsonBody(response);
        assertFields(body, Set.of("content", "page", "size", "totalElements", "totalPages"));
        long pages = total == 0 ? 0 : (total + size - 1) / size;
        assertThat(body.path("page").isIntegralNumber() && body.path("page").intValue() == page
                && body.path("size").isIntegralNumber() && body.path("size").intValue() == size
                && body.path("totalElements").isIntegralNumber() && body.path("totalElements").longValue() == total
                && body.path("totalPages").isIntegralNumber() && body.path("totalPages").longValue() == pages)
                .as("page and totals describe the same finite filtered set").isTrue();
        var content = body.path("content");
        assertThat(content.isArray() && content.size() == products.size()).isTrue();
        for (int index = 0; index < products.size(); index++) {
            assertFields(content.get(index), Set.of("id", "name", "image", "price"));
            assertProduct(content.get(index), products.get(index));
        }
    }

    private static void assertProduct(JsonNode body, ProductExpected expected) {
        assertThat(body.path("id").isIntegralNumber() && body.path("id").longValue() == expected.id()
                && expected.name().equals(body.path("name").textValue()) && IMAGE.equals(body.path("image").textValue())
                && body.path("price").isNumber() && expected.price().compareTo(body.path("price").decimalValue()) == 0)
                .as("original public product values and order, with exact finite prices").isTrue();
    }

    private void assertError(HttpResponse<byte[]> response, int status, String error, String message, String path) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertJsonHeaders(response);
        var expected = mapper.createObjectNode().put("status", status).put("error", error)
                .put("message", message).put("path", expectedContextPath() + path);
        assertThat(expected.equals(jsonBody(response))).as("exact existing error envelope without identity, secret or cause").isTrue();
    }

    private static void assertJsonHeaders(HttpResponse<byte[]> response) {
        boolean json;
        try { json = MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""))
                .isCompatibleWith(MediaType.APPLICATION_JSON); }
        catch (IllegalArgumentException invalidHeader) { json = false; }
        assertThat(json).isTrue();
        for (String header : List.of("Authorization", "Set-Cookie", "Location", "WWW-Authenticate")) {
            assertThat(response.headers().allValues(header).isEmpty()).as("no unexpected public %s header", header).isTrue();
        }
    }

    private static void assertFields(JsonNode body, Set<String> expected) {
        var fields = new LinkedHashSet<String>();
        body.fieldNames().forEachRemaining(fields::add);
        assertThat(body.isObject() && fields.equals(expected)).as("only the existing public wire fields").isTrue();
    }

    private void assertLegacyStored(Account legacy) throws SQLException {
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select id, name, password_hash, full_name, email, email_key, phone, birth_date from tb_user_account where id = ?")) {
            statement.setLong(1, legacy.id());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                String hash = rows.getString("PASSWORD_HASH");
                assertThat(rows.getLong("ID") == legacy.id() && legacy.name().equals(rows.getString("NAME"))
                        && legacy.passwordHash().equals(hash) && Arrays.equals(legacy.passwordHash().getBytes(StandardCharsets.UTF_8),
                        hash == null ? null : hash.getBytes(StandardCharsets.UTF_8)))
                        .as("legacy ID, exact name and original BCrypt bytes remain unchanged").isTrue();
                for (String column : List.of("FULL_NAME", "EMAIL", "EMAIL_KEY", "PHONE", "BIRTH_DATE")) {
                    assertThat(rows.getObject(column) == null).as("legacy %s stays genuinely NULL", column).isTrue();
                }
                assertThat(rows.next()).isFalse();
            }
            try (var roles = connection.prepareStatement("select role from tb_user_account_role where user_account_id = ?")) {
                roles.setLong(1, legacy.id());
                try (var rows = roles.executeQuery()) {
                    var actual = new LinkedHashSet<String>();
                    while (rows.next()) { assertThat(actual.add(rows.getString(1))).isTrue(); }
                    assertThat(actual.equals(legacy.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet())))
                            .as("no legacy role is added, merged, removed or repaired").isTrue();
                }
            }
        }
    }

    private static void assertOnlyNewAccountAndClient(Snapshot before, Snapshot after, long id) {
        before.tables().forEach((table, previous) -> {
            var current = after.tables().get(table);
            if (!Set.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE").contains(table)) {
                assertThat(previous.equals(current)).as("unchanged catalog/financial table %s", table).isTrue();
            } else {
                String column = table.equals("TB_USER_ACCOUNT") ? "ID" : "USER_ACCOUNT_ID";
                assertThat(previous.columns().equals(current.columns()) && current.rows().size() == previous.rows().size() + 1)
                        .as("one new row only in %s", table).isTrue();
                assertThat(current.rows().stream().filter(row -> id(row, column) != id).toList().equals(previous.rows()))
                        .as("all existing identities, hashes, fields and profiles are preserved").isTrue();
                if (table.equals("TB_USER_ACCOUNT_ROLE")) {
                    assertThat(current.rows().stream().anyMatch(row -> id(row, column) == id
                            && "CLIENT".equals(row.values().get("ROLE").value()))).isTrue();
                }
            }
        });
    }

    private void rememberNewAccounts(Snapshot before, Snapshot after) {
        var old = before.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID"))
                .collect(java.util.stream.Collectors.toSet());
        after.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID"))
                .filter(id -> !old.contains(id)).forEach(createdAccounts::add);
    }

    private long loginCalls() { return mockingDetails(authentication).getInvocations().stream()
            .filter(call -> call.getMethod().getName().equals("login")).count(); }
    private long encoderCalls(String name) { return mockingDetails(bcrypt).getInvocations().stream()
            .filter(call -> call.getMethod().getName().equals(name)).count(); }
    private static long id(Row row, String column) { return ((Number) row.values().get(column).value()).longValue(); }

    @AfterEach
    void cleanOnlyRegistrationOwnedAccountsAfterCompletedHttpResponses() throws SQLException {
        if (!safeToClean) {
            fixture = null;
            throw new AssertionError("HTTP completion is unproved; fixture cleanup is withheld");
        }
        if (createdAccounts.isEmpty()) { return; }
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
                throw new AssertionError("Cleanup of public-regression-owned rows failed");
            }
        }
    }

    enum RegistrationOutcome { ACCEPTED, INVALID, CONFLICT }
    record LegacyLoginCase(RegistrationOutcome outcome, LegacyCredential credential, String label, Set<UserRole> roles) {
        @Override public String toString() { return outcome.name() + "-" + credential.name() + "-" + label; }
    }
    private record Catalog(Account reserved, List<ProductExpected> all) { }
    private record ProductExpected(long id, String name, BigDecimal price, List<String> categories) { }
    private record FilterCase(String query, List<ProductExpected> content, int page, int size, long total) { }
}
