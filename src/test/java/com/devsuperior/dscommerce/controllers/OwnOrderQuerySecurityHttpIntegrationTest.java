package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryTestObservationConfig;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.Scenario;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=", "spring.jpa.open-in-view=false",
                "spring.h2.console.enabled=false", "feature007.own-order-query-test-context=root"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(OwnOrderQueryTestObservationConfig.class)
class OwnOrderQuerySecurityHttpIntegrationTest extends OwnOrderQueryUs3HttpTestSupport {
    @Override protected String expectedContextPath() { return ""; }
    @Override protected String expectedContextIdentity() { return "root"; }
}

/** Adds US3 business assertions while inheriting the original authenticationGate cases unchanged. */
abstract class OwnOrderQueryUs3HttpTestSupport extends OwnOrderQueryHttpTestSupport {

    @Autowired private JwtDecoder decoder;
    @Autowired private JwtEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    private HttpClient transport;
    private OwnOrderQueryDatabaseProbe.Snapshot gateSetup;
    private String stableUrl;
    private int us3Logins;
    private int us3Queries;

    @BeforeAll
    void prepareUs3TransportAfterTheInheritedGateSetup() {
        outsideTransaction();
        stableUrl = context.getEnvironment().getRequiredProperty("spring.datasource.url");
        require("never".equals(context.getEnvironment().getRequiredProperty("spring.sql.init.mode")), "No SQL seed");
        require("".equals(context.getEnvironment().getRequiredProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")),
                "No Hibernate seed");
        gateSetup = database.snapshot();
        transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @AfterEach
    void preserveInheritedAccountsAndCleanOnlyEachUs3Fixture() {
        outsideTransaction();
        OwnOrderQueryDatabaseProbe.assertUnchanged(gateSetup, database.snapshot());
        require(stableUrl.equals(context.getEnvironment().getRequiredProperty("spring.datasource.url")),
                "Keep the same isolated database for this context");
    }

    @AfterAll
    void closeUs3Transport() {
        if (transport != null) { transport.close(); }
        System.out.println("T023_COUNTS context=" + expectedContextIdentity() + " httpLogins=" + us3Logins
                + " queryHttpRequests=" + us3Queries + " mockMvcDispatches=0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "admin", "both"})
    void ownershipUs31ListsCountsAndDetailsOnlyTheCurrentOwnersPurchases(String identity) {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var s = fixture.preparePaginationScenario();
            var account = account(s, identity);
            var token = login(account);
            var expected = owned(s, identity);
            var foreign = s.insertedOrders().get(1);
            var query = "&accountId=" + foreign.buyerId() + "&buyerId=" + foreign.buyerId() + "&sort=id,asc";
            for (int page = 0; page <= (expected.size() - 1) / 2 + 1; page++) {
                assertOwnPage(token, page, 2, expected, query);
            }
            for (var order : expected) { assertOwnDetail(token, order); }
            assertOwnPage(token, 0, 2, expected, query);
            var head = request("HEAD", "/orders/" + expected.getFirst().id(), token, 200);
            assertThat(head.body()).isEmpty();
            assertThat(request("HEAD", "/orders?page=0&size=2" + query, token, 200).body()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "admin", "both"})
    void ownershipUs34And35ForeignAndAbsentIdsHaveTheSame404EvenForAdmin(String identity) {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var s = fixture.preparePaginationScenario();
            var token = login(account(s, identity));
            var foreign = s.insertedOrders().get(1);
            // The same ID is first proved to be a functioning existing purchase under its actual owner.
            assertOwnDetail(login(s.otherBuyer()), foreign);
            require(jdbc.queryForObject("select count(*) from tb_order where id = ?", Long.class, Long.MAX_VALUE) == 0,
                    "The absent control must not exist");
            JsonNode previous = null;
            for (var id : List.of(foreign.id(), Long.MAX_VALUE)) {
                var path = "/orders/" + id + "?private=must-not-appear&buyerId=" + foreign.buyerId();
                var error = assertError("GET", path, token, 404, "Not Found", "Order not found");
                var outcome = error.deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode) outcome).remove("path");
                if (previous != null) { assertThat(outcome).isEqualTo(previous); }
                previous = outcome;
                assertError("HEAD", path, token, 404, "Not Found", "Order not found");
            }
            assertOwnPage(token, 0, 2, owned(s, identity), "&accountId=" + foreign.buyerId());
            assertOwnDetail(token, owned(s, identity).getFirst());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "admin", "both", "none"})
    void ownershipUsesSubjectAndCurrentDatabaseRolesInsteadOfSignedNameOrRoleClaims(String identity) {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var s = fixture.preparePaginationScenario();
            var account = account(s, identity);
            var original = decode(login(account));
            var falseRoles = identity.equals("none") ? List.of("ADMIN", "CLIENT", "ROLE_ADMIN") : List.of("ROLE_AUDITOR");
            var claims = JwtClaimsSet.builder().claims(values -> values.putAll(original.getClaims()))
                    .claim("name", s.otherBuyer().name()).claim("roles", falseRoles).build();
            var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                    .getTokenValue();
            var decoded = decode(token);
            require(decoded.getSubject().equals(Long.toString(account.id())), "Keep the real account subject");
            require(decoded.getClaimAsStringList("roles").equals(falseRoles), "Use the deliberately misleading claims");
            require(s.otherBuyer().name().equals(decoded.getClaimAsString("name")), "Use the other account's name claim");
            if (identity.equals("none")) {
                assertError("GET", "/orders?private=must-not-appear", token, 403, "Forbidden", "Order query not permitted");
                assertError("GET", "/orders/" + s.insertedOrders().get(1).id(), token, 403,
                        "Forbidden", "Order query not permitted");
            } else {
                assertOwnPage(token, 0, 2, owned(s, identity), "");
                assertOwnDetail(token, owned(s, identity).getFirst());
                assertError("GET", "/orders/" + s.insertedOrders().get(1).id(), token, 404, "Not Found", "Order not found");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/orders?page=-1&size=2", "/orders?page=0&size=51", "/orders?page=0&page=1",
            "/orders/0", "/orders/-1", "/orders/9223372036854775808"})
    void ownershipUs36AuthenticatesAndAuthorizesBeforeAdditionalInvalidInputs(String invalidPath) {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var s = fixture.preparePaginationScenario();
            var allowed = login(s.accounts().client());
            var denied = login(s.accounts().none());
            var path = invalidPath + (invalidPath.contains("?") ? "&" : "?") + "private=must-not-appear";
            // Positive boundary control: this really is invalid input once access has been granted.
            assertError("GET", path, allowed, 400, "Bad Request", "Invalid order query data");
            for (var method : List.of("GET", "HEAD")) {
                assertError(method, path, null, 401, "Unauthorized", "Authentication required or invalid");
                assertError(method, path, denied, 403, "Forbidden", "Order query not permitted");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "ADMIN", "BOTH"})
    void ownershipCurrentRoleGrantAndRevocationApplyToTheSamePreviouslyIssuedJwt(String granted) {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var s = fixture.preparePaginationScenario();
            var account = s.accounts().none();
            var order = fixture.createOrder(account, OwnOrderQueryTestFixture.FIRST_INSTANT,
                    List.of(new ItemInput(s.productB().id(), 2, "10.00", "20.00")), "20.00");
            var token = login(account);
            for (var path : List.of("/orders?page=0&size=2", "/orders/" + order.id())) {
                assertError("GET", path, token, 403, "Forbidden", "Order query not permitted");
            }
            var roles = granted.equals("BOTH") ? Set.of(UserRole.CLIENT, UserRole.ADMIN) : Set.of(UserRole.valueOf(granted));
            try {
                replaceRoles(account.id(), roles);
                assertOwnPage(token, 0, 2, List.of(order), "");
                assertOwnDetail(token, order);
                assertThat(request("HEAD", "/orders/" + order.id(), token, 200).body()).isEmpty();
                replaceRoles(account.id(), Set.of());
                for (var method : List.of("GET", "HEAD")) {
                    for (var path : List.of("/orders?page=0&size=2", "/orders/" + order.id(), "/orders/0")) {
                        assertError(method, path, token, 403, "Forbidden", "Order query not permitted");
                    }
                }
                replaceRoles(account.id(), roles);
                assertOwnPage(token, 0, 2, List.of(order), "");
                assertOwnDetail(token, order);
                require(decode(token).getSubject().equals(Long.toString(account.id())),
                        "The unchanged token remains authentic throughout these committed role changes");
            } finally {
                // Restore this account's fixture-owned roles before its tracked cleanup, outside every HTTP window.
                replaceRoles(account.id(), Set.of());
            }
        }
    }

    private void replaceRoles(long id, Set<UserRole> roles) {
        outsideTransaction();
        var committed = new TransactionTemplate(transactions);
        committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        committed.executeWithoutResult(status -> {
            jdbc.update("delete from tb_user_account_role where user_account_id = ?", id);
            for (var role : roles) {
                require(jdbc.update("insert into tb_user_account_role (user_account_id, role) values (?, ?)", id, role.name()) == 1,
                        "Commit the requested role grant");
            }
        });
        var actual = database.snapshot().roles().stream().filter(row -> row.accountId() == id)
                .map(OwnOrderQueryDatabaseProbe.AccountRole::role).collect(java.util.stream.Collectors.toSet());
        require(actual.equals(roles), "The explicit role preparation must already be committed");
    }

    private void assertOwnPage(String token, int page, int size, List<OrderManifest> orders, String extra) {
        var actual = json(request("GET", "/orders?page=" + page + "&size=" + size + extra, token, 200));
        var expected = mapper.createObjectNode().put("page", page).put("size", size)
                .put("totalElements", orders.size()).put("totalPages", (orders.size() + size - 1) / size);
        var content = expected.putArray("content");
        orders.stream().skip((long) page * size).limit(size).forEach(order -> content.add(header(order)));
        assertExpectedJson(actual, expected);
    }

    private void assertOwnDetail(String token, OrderManifest order) {
        var actual = json(request("GET", "/orders/" + order.id(), token, 200));
        var expected = header(order);
        var items = expected.putArray("items");
        for (var item : order.items()) {
            items.addObject().put("productId", item.productId()).put("productName", item.productName())
                    .put("quantity", item.quantity()).put("unitPrice", item.unitPrice()).put("subtotal", item.subtotal());
        }
        assertExpectedJson(actual, expected);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode header(OrderManifest order) {
        return mapper.createObjectNode().put("id", order.id()).put("createdAt", order.createdAt().toString())
                .put("status", order.status()).put("total", order.total());
    }

    private JsonNode assertError(String method, String path, String token, int status, String error, String message) {
        var response = request(method, path, token, status);
        assertThat(response.headers().allValues("WWW-Authenticate"))
                .containsExactlyElementsOf(status == 401 ? List.of("Bearer") : List.of());
        if (method.equals("HEAD")) { assertThat(response.body()).isEmpty(); return null; }
        var expected = mapper.createObjectNode().put("status", status).put("error", error).put("message", message)
                .put("path", expectedContextPath() + path.split("\\?", 2)[0]);
        var actual = json(response);
        // Equality rejects extra account/order/credential/SQL data and any query-string leakage.
        require(expected.equals(actual), "Expected only the fixed safe four-field query error");
        return actual;
    }

    private HttpResponse<byte[]> request(String method, String path, String token, int expectedStatus) {
        outsideTransaction();
        var builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30)).header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
        var observed = database.observeRead(sql, () -> send(builder.build()));
        var response = observed.result();
        us3Queries++;
        System.out.println("T023_HTTP context=" + expectedContextIdentity() + " method=" + method + " path=" + path
                + " expected=" + expectedStatus + " actual=" + response.statusCode() + " bodyBytes=" + response.body().length);
        observed.assertNoWrites();
        assertThat(response.statusCode()).as("US3 HTTP status").isEqualTo(expectedStatus);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        return response;
    }

    private String login(Account account) {
        outsideTransaction();
        var roles = database.snapshot().roles().stream().filter(row -> row.accountId() == account.id())
                .map(OwnOrderQueryDatabaseProbe.AccountRole::role).collect(java.util.stream.Collectors.toSet());
        require(roles.equals(account.roles()), "Use the account's explicitly prepared database roles at login");
        try {
            var response = send(HttpRequest.newBuilder(uri("/login")).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(
                            mapper.writeValueAsBytes(Map.of("name", account.name(), "password", account.password())))).build());
            us3Logins++;
            require(response.statusCode() == 200, "US3 real HTTP login must succeed");
            var credential = mapper.readValue(response.body(), TokenResponseDTO.class);
            require("Bearer".equals(credential.tokenType()) && credential.accessToken() != null, "Use a real bearer token");
            require(decode(credential.accessToken()).getSubject().equals(Long.toString(account.id())),
                    "The real login must identify the fixture account");
            return credential.accessToken();
        } catch (IOException failure) { throw new AssertionError("US3 login must return its safe JSON contract"); }
    }

    private JsonNode json(HttpResponse<byte[]> response) {
        try { return mapper.readTree(response.body()); }
        catch (IOException failure) { throw new AssertionError("US3 query must return valid JSON"); }
    }

    private void assertExpectedJson(JsonNode actual, JsonNode expected) {
        try {
            // Parse both sides as wire JSON so a small int64 ID is not compared as LongNode versus IntNode.
            require(actual.equals(mapper.readTree(mapper.writeValueAsBytes(expected))),
                    "Expected only the current owner's exact fields, order, values and page metadata");
        } catch (IOException failure) { throw new AssertionError("The explicit US3 JSON oracle must be serializable"); }
    }

    private Jwt decode(String token) {
        try { return decoder.decode(token); }
        catch (JwtException failure) { throw new IllegalStateException("US3 fixture credential must be authentic"); }
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try { return transport.send(request, HttpResponse.BodyHandlers.ofByteArray()); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("US3 HTTP transport interrupted");
        } catch (IOException failure) { throw new IllegalStateException("US3 HTTP transport could not complete"); }
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + expectedContextPath() + path); }

    private static Account account(Scenario s, String identity) {
        return switch (identity) {
            case "client" -> s.accounts().client();
            case "admin" -> s.accounts().admin();
            case "both" -> s.accounts().both();
            case "none" -> s.accounts().none();
            default -> throw new AssertionError("Unknown US3 account");
        };
    }

    private static List<OrderManifest> owned(Scenario s, String identity) {
        return switch (identity) {
            case "client" -> s.expectedClientOrders();
            case "admin" -> List.of(s.insertedOrders().get(3));
            case "both" -> List.of(s.insertedOrders().get(7));
            default -> throw new AssertionError("Use an authorized owner");
        };
    }

    private static void outsideTransaction() {
        require(!TransactionSynchronizationManager.isActualTransactionActive(), "US3 HTTP must have no caller transaction");
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
