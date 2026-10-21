package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryTestObservationConfig;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.Scenario;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=", "spring.jpa.open-in-view=false",
                "spring.h2.console.enabled=false", "feature007.own-order-regression-test-context=root"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(OwnOrderQueryTestObservationConfig.class)
class OwnOrderQueryRegressionHttpIntegrationTest extends OwnOrderQueryRegressionHttpTestSupport {
    @Override protected String contextPath() { return ""; }
    @Override protected String identity() { return "root"; }
}

/** US4 read windows and post-query interactions; legacy contract matrices stay in their original suites. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class OwnOrderQueryRegressionHttpTestSupport {
    private static final DateTimeFormatter INSTANT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtDecoder decoder;
    @Autowired private OwnOrderQueryDatabaseProbe database;
    @Autowired private SqlObservation sql;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @LocalServerPort private int port;
    private HttpClient http;
    private OwnOrderQueryTestFixture fixture;
    private Scenario scenario;
    // Only newly observed roots belonging to this fixture's accounts; never a global cleanup predicate.
    private final Map<Long, Long> registeredRoots = new LinkedHashMap<>();
    private String stableUrl;
    private int logins, queries, posts;
    private int compatibilityRequests;

    protected abstract String contextPath();
    protected abstract String identity();

    @BeforeAll
    void verifyDedicatedSeedFreeContextAndOpenRealTransport() {
        outsideTransaction();
        var env = context.getEnvironment();
        require(contextPath().equals(context.getServletContext().getContextPath()), "Expected regression context path");
        require(identity().equals(env.getRequiredProperty("feature007.own-order-regression-test-context")),
                "Expected regression context identity");
        require("false".equals(env.getRequiredProperty("spring.jpa.open-in-view")), "Disable OSIV");
        require("create-drop".equals(env.getRequiredProperty("spring.jpa.hibernate.ddl-auto")), "Keep create-drop");
        require("never".equals(env.getRequiredProperty("spring.sql.init.mode")), "No SQL seed in this isolated context");
        require("".equals(env.getRequiredProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")), "No Hibernate seed");
        stableUrl = env.getRequiredProperty("spring.datasource.url");
        var metadata = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        require(stableUrl.startsWith("jdbc:h2:mem:admin-product-test-") && stableUrl.split(";", 2)[0].equals(metadata),
                "Expected stable isolated H2 database");
        assertEmpty();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        System.out.println("T028_CONTEXT context=" + identity() + " port=" + port + " database=" + metadata
                + " mockMvcDispatches=0");
    }

    @BeforeEach
    void prepareOnlyOwnedCommittedRowsOutsideReadWindows() {
        outsideTransaction();
        require(fixture == null && registeredRoots.isEmpty(), "Finish the previous owned cleanup before preparing another case");
        assertEmpty();
        fixture = new OwnOrderQueryTestFixture(context);
        scenario = fixture.preparePaginationScenario();
    }

    @AfterEach
    void removeTrackedHttpPurchasesBeforeClosingTheApprovedFixture() {
        outsideTransaction();
        if (fixture != null) {
            var committed = new TransactionTemplate(transactions);
            committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            committed.executeWithoutResult(status -> {
                for (var entry : registeredRoots.entrySet()) {
                    jdbc.update("delete from tb_order_item where order_id = ? and exists "
                                    + "(select 1 from tb_order where id = ? and buyer_id = ?)",
                            entry.getKey(), entry.getKey(), entry.getValue());
                    jdbc.update("delete from tb_order where id = ? and buyer_id = ?", entry.getKey(), entry.getValue());
                }
            });
            registeredRoots.clear();
            fixture.close();
            fixture = null;
            scenario = null;
        }
        assertEmpty();
        require(stableUrl.equals(context.getEnvironment().getRequiredProperty("spring.datasource.url")), "Keep the same database");
        sql.clear();
    }

    @AfterAll
    void closeTransportAndReportActualCounts() {
        if (http != null) { http.close(); }
        System.out.println("T028_COUNTS context=" + identity() + " httpLogins=" + logins + " queryHttpRequests=" + queries
                + " registrationHttpRequests=" + posts + " mockMvcDispatches=0");
        System.out.println("T029_COUNTS context=" + identity() + " compatibilityHttpRequests=" + compatibilityRequests
                + " mockMvcDispatches=0");
    }

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @MethodSource("com.devsuperior.dscommerce.controllers.OwnOrderQueryRegressionHttpTestSupport#readCases")
    void regressionUs41RepeatedSuccessfulEmptyOrRejectedReadsPreserveEveryObservedTable(String kind, String method) {
        var target = target(kind);
        var token = target.account() == null ? null : login(target.account());
        var baseline = database.snapshot();
        for (int repetition = 0; repetition < 3; repetition++) {
            var response = read(method, target.path(), token, target.status());
            if (method.equals("GET")) { expect(json(response.body()), target.body()); }
        }
        // This also preserves account names/hashes, buyer/time/status and item IDs/positions/quantities/prices.
        OwnOrderQueryDatabaseProbe.assertUnchanged(baseline, database.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "admin", "both"})
    void regressionUs44ReadsDoNotCreateOrdersOrReuseTheNextIdenticalRegistration(String identity) {
        var account = account(identity);
        var token = login(account);
        var body = registrationBody();
        var first = register(account, token, body);
        var baseline = database.snapshot();
        var headers = new ArrayList<ObjectNode>(ownedOrders(identity).stream().map(this::header).toList());
        headers.add(purchaseHeader(first));
        for (int repetition = 0; repetition < 3; repetition++) {
            assertOwnPage(read("GET", "/orders?page=0&size=12", token, 200), headers);
            expect(json(read("GET", "/orders/" + first.id(), token, 200).body()), purchaseDetail(first));
            read("HEAD", "/orders?page=0&size=12", token, 200);
            read("HEAD", "/orders/" + first.id(), token, 200);
        }
        OwnOrderQueryDatabaseProbe.assertUnchanged(baseline, database.snapshot());
        // The same request bytes create a new complete purchase; timestamps are allowed to coincide.
        var second = register(account, token, body);
        require(first.id() != second.id(), "A later identical POST must create another order ID");
        headers.add(purchaseHeader(second));
        assertOwnPage(read("GET", "/orders?page=0&size=12", token, 200), headers);
        expect(json(read("GET", "/orders/" + first.id(), token, 200).body()), purchaseDetail(first));
        expect(json(read("GET", "/orders/" + second.id(), token, 200).body()), purchaseDetail(second));
    }

    @ParameterizedTest(name = "public contracts after own queries, irrelevant Bearer={0}")
    @ValueSource(booleans = {false, true})
    void regressionUs42PublicCatalogAndDetailsRemainIndependentOfPreviousOwnQueries(boolean invalidBearer) {
        consultOwnOrdersBeforeAnotherJourney();
        var baseline = database.snapshot();
        var token = invalidBearer ? "irrelevant.invalid.token" : null;
        var expected = mapper.createObjectNode().put("page", 0).put("size", 12).put("totalElements", 2).put("totalPages", 1);
        var content = expected.putArray("content");
        // Explicit stored-name order, including the leading/trailing spaces in A; prices remain numeric.
        for (var product : List.of(scenario.productA(), scenario.productB())) {
            content.addObject().put("id", product.id()).put("name", product.name()).put("image", product.image())
                    .put("price", product.price());
        }
        expect(json(read("GET", "/products?name=PRODUTO&page=0&size=12", token, 200).body()), expected);
        for (var product : List.of(scenario.productA(), scenario.productB())) {
            expect(json(read("GET", "/products/" + product.id(), token, 200).body()), publicDetail(product));
        }
        require(baseline.data().products().stream().noneMatch(product -> product.id() == Long.MAX_VALUE), "Use an absent public product");
        expect(json(read("GET", "/products/" + Long.MAX_VALUE, token, 404).body()),
                error(404, "Not Found", "Product not found", "/products/" + Long.MAX_VALUE));
        OwnOrderQueryDatabaseProbe.assertUnchanged(baseline, database.snapshot());
    }

    @ParameterizedTest(name = "login after own queries, irrelevant Bearer={0}")
    @ValueSource(booleans = {false, true})
    void regressionUs43RealLoginKeepsExactCredentialsAndConfidentialContractsAfterOwnQueries(boolean invalidBearer) {
        var previousToken = consultOwnOrdersBeforeAnotherJourney();
        var baseline = database.snapshot();
        var account = scenario.accounts().client();
        var bearer = invalidBearer ? "irrelevant.invalid.token" : null;
        var credentials = mapper.createObjectNode().put("name", account.name()).put("password", account.password());
        var response = compatibilityRequest("POST", "/login", bearer, credentials, 200);
        var credential = json(response.body());
        require(credential.isObject() && credential.size() == 2 && credential.path("accessToken").isTextual()
                && credential.path("tokenType").asText().equals("Bearer"), "Return only the real Bearer credential fields");
        try {
            require(Long.toString(account.id()).equals(decoder.decode(credential.path("accessToken").textValue()).getSubject()),
                    "The new login must identify the same committed account");
        } catch (JwtException failure) { throw new AssertionError("Verify the real post-query login credential"); }
        require(!credential.toString().contains(account.name()) && !credential.toString().contains(account.password()),
                "Do not disclose plaintext credentials");
        // Changing surrounding spaces must not silently authenticate the original name/password.
        var wrongName = compatibilityRequest("POST", "/login", bearer, credentials.deepCopy().put("name", " " + account.name() + " "), 401);
        var wrongPassword = compatibilityRequest("POST", "/login", bearer, credentials.deepCopy().put("password", account.password() + " "), 401);
        expect(json(wrongName.body()), error(401, "Unauthorized", "Invalid credentials", "/login"));
        expect(json(wrongPassword.body()), json(wrongName.body()));
        require(java.util.Arrays.equals(wrongName.body(), wrongPassword.body()), "Credential rejections must have identical safe bodies");
        expect(json(compatibilityRequest("POST", "/login", bearer, mapper.createObjectNode().put("name", account.name()), 400).body()),
                error(400, "Bad Request", "Invalid login data", "/login"));
        // Failed/incomplete login must not replace the identity carried by the earlier valid token.
        expect(json(read("GET", "/orders/" + scenario.expectedClientOrders().getLast().id(), previousToken, 200).body()),
                detail(scenario.expectedClientOrders().getLast()));
        OwnOrderQueryDatabaseProbe.assertUnchanged(baseline, database.snapshot());
    }

    @Test
    void regressionUs45OwnQueriesDoNotGrantMaintenanceOrDisturbStrictRegistrationAndReferencedHistory() {
        var clientToken = consultOwnOrdersBeforeAnotherJourney();
        var adminToken = login(scenario.accounts().admin());
        var before = database.snapshot();
        require(before.data().categories().size() == 1, "Use the fixture's single committed category");
        var category = before.data().categories().getFirst();
        var product = scenario.productB();
        var renamed = "Produto B depois da consulta";
        var route = "/admin/products/" + product.id();
        var replacement = mapper.createObjectNode().put("name", renamed).put("description", product.description())
                .put("image", product.image()).put("price", "12.00");
        replacement.putArray("categoryIds").add(category.id());
        var denied = error(403, "Forbidden", "Product maintenance not permitted", route);
        denied.putArray("errors");
        expect(json(compatibilityRequest("PUT", route, clientToken, replacement, 403).body()), denied);
        OwnOrderQueryDatabaseProbe.assertUnchanged(before, database.snapshot());

        // Deliberate catalog DML, outside every GET/HEAD observation; historical money is already 10.00/7.50.
        var expectedAdmin = mapper.createObjectNode().put("id", product.id()).put("name", renamed)
                .put("description", product.description()).put("image", product.image()).put("price", "12.00");
        expectedAdmin.putArray("categories").addObject().put("id", category.id()).put("name", category.name());
        expect(json(compatibilityRequest("PUT", route, adminToken, replacement, 200).body()), expectedAdmin);
        var afterPut = database.snapshot();
        var expectedProducts = before.data().products().stream().map(row -> row.id() == product.id()
                ? new ProductRow(product.id(), renamed, product.description(), product.image(), product.price()) : row).toList();
        require(afterPut.data().products().equals(expectedProducts)
                && afterPut.data().categories().equals(before.data().categories())
                && afterPut.data().associations().equals(before.data().associations())
                && afterPut.data().orders().equals(before.data().orders()) && afterPut.data().items().equals(before.data().items())
                && afterPut.accountIds().equals(before.accountIds()) && afterPut.roles().equals(before.roles()),
                "Only the selected product name may change in this maintenance interaction");
        var oldOrder = scenario.expectedClientOrders().getLast();
        var historicalDetail = detail(oldOrder);
        ((ObjectNode) historicalDetail.path("items").get(0)).put("productName", renamed);
        expect(json(read("GET", "/orders/" + oldOrder.id(), clientToken, 200).body()), historicalDetail);
        var conflict = error(409, "Conflict", "Product deletion violates referential integrity", route);
        conflict.putArray("errors");
        expect(json(compatibilityRequest("DELETE", route, adminToken, null, 409).body()), conflict);
        OwnOrderQueryDatabaseProbe.assertUnchanged(afterPut, database.snapshot());

        var invalidOrder = (ObjectNode) json(registrationBody());
        invalidOrder.put("unexpected", true);
        expect(json(compatibilityRequest("POST", "/orders", clientToken, invalidOrder, 400).body()),
                error(400, "Bad Request", "Invalid order data", "/orders"));
        OwnOrderQueryDatabaseProbe.assertUnchanged(afterPut, database.snapshot());
        var purchase = register(scenario.accounts().client(), clientToken, registrationBody());
        var newDetail = purchaseDetail(purchase);
        ((ObjectNode) newDetail.path("items").get(0)).put("productName", renamed);
        var headers = new ArrayList<ObjectNode>(scenario.expectedClientOrders().stream().map(this::header).toList());
        headers.add(purchaseHeader(purchase));
        assertOwnPage(read("GET", "/orders?page=0&size=12", clientToken, 200), headers);
        expect(json(read("GET", "/orders/" + oldOrder.id(), clientToken, 200).body()), historicalDetail);
        expect(json(read("GET", "/orders/" + purchase.id(), clientToken, 200).body()), newDetail);
        expect(json(read("GET", "/products/" + product.id(), null, 200).body()),
                publicDetail(new ProductData(product.id(), renamed, product.description(), product.image(), product.price())));
    }

    private String consultOwnOrdersBeforeAnotherJourney() {
        var token = login(scenario.accounts().client());
        var own = scenario.expectedClientOrders();
        assertOwnPage(read("GET", "/orders?page=0&size=12", token, 200), own.stream().map(this::header).toList());
        expect(json(read("GET", "/orders/" + own.getLast().id(), token, 200).body()), detail(own.getLast()));
        read("HEAD", "/orders?page=0&size=12", token, 200);
        read("HEAD", "/orders/" + own.getLast().id(), token, 200);
        return token;
    }

    /** Mutating verbs/login have before/after checks in their caller, never a GET/HEAD SELECT-only claim. */
    private HttpResponse<byte[]> compatibilityRequest(String method, String path, String token, JsonNode body, int expected) {
        outsideTransaction();
        try {
            var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
            if (token != null) { request.header("Authorization", "Bearer " + token); }
            if (body != null) { request.header("Content-Type", "application/json"); }
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
            var registrationBefore = method.equals("POST") && path.equals("/orders") ? database.snapshot() : null;
            HttpResponse<byte[]> response;
            try {
                response = send(request.build());
                compatibilityRequests++;
            } finally {
                // Even an erroneously accepted invalid POST must be tracked before status/body assertions fail.
                if (registrationBefore != null) {
                    var previousIds = registrationBefore.data().orders().stream().map(row -> row.id()).toList();
                    for (var row : database.snapshot().data().orders()) {
                        if (!previousIds.contains(row.id()) && ownedAccountIds().contains(row.buyerId())) {
                            registeredRoots.put(row.id(), row.buyerId());
                        }
                    }
                }
            }
            require(response.statusCode() == expected, "Expected post-query compatibility status");
            require(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"), "Expected compatibility JSON");
            require(response.headers().allValues("WWW-Authenticate").isEmpty(), "These compatibility responses must not issue a challenge");
            require(response.headers().allValues("X-Frame-Options").equals(List.of("SAMEORIGIN")), "Keep the existing frame policy");
            System.out.println("T029_HTTP context=" + identity() + " method=" + method + " path=" + path
                    + " expected=" + expected + " actual=" + response.statusCode());
            return response;
        } catch (IOException failure) { throw new AssertionError("Explicit compatibility input must be serializable"); }
    }

    private ObjectNode publicDetail(ProductData product) {
        var expected = mapper.createObjectNode().put("id", product.id()).put("name", product.name())
                .put("description", product.description()).put("image", product.image()).put("price", product.price());
        expected.putArray("categories").add("Categoria de pedidos");
        return expected;
    }

    private ObjectNode error(int status, String reason, String message, String path) {
        return mapper.createObjectNode().put("status", status).put("error", reason).put("message", message).put("path", contextPath() + path);
    }

    private HttpResponse<byte[]> read(String method, String path, String token, int expected) {
        outsideTransaction();
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30)).header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        var observation = database.observeRead(sql, () -> send(request.build()));
        queries++;
        observation.assertNoWrites();
        var response = observation.result();
        require(response.statusCode() == expected, "Expected regression query status");
        require(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"), "Expected JSON content type");
        require(response.headers().allValues("WWW-Authenticate").equals(expected == 401 ? List.of("Bearer") : List.of()),
                "Preserve the query authentication challenge policy");
        if (method.equals("HEAD")) { require(response.body().length == 0, "HEAD must have no response body"); }
        System.out.println("T028_HTTP context=" + identity() + " method=" + method + " path=" + path
                + " expected=" + expected + " actual=" + response.statusCode() + " snapshotEqual=true");
        return response;
    }

    private Purchase register(Account account, String token, byte[] body) {
        outsideTransaction();
        var before = database.snapshot();
        HttpResponse<byte[]> response;
        Snapshot after;
        try {
            response = send(HttpRequest.newBuilder(uri("/orders")).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
            posts++;
        } finally {
            after = database.snapshot();
            var previousIds = before.data().orders().stream().map(row -> row.id()).toList();
            // Capture committed owned roots before response assertions, including an unexpected response shape.
            for (var row : after.data().orders()) {
                if (!previousIds.contains(row.id()) && ownedAccountIds().contains(row.buyerId())) {
                    registeredRoots.put(row.id(), row.buyerId());
                }
            }
        }
        require(response.statusCode() == 201, "A valid repeated registration must create an order");
        require(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"), "Expected registration JSON");
        require(response.headers().allValues("Location").isEmpty(), "Keep the registration contract without Location");
        require(response.headers().allValues("WWW-Authenticate").isEmpty(), "Successful registration must have no challenge");
        var actual = json(response.body());
        require(actual.path("id").isIntegralNumber() && actual.path("id").canConvertToLong() && actual.path("id").longValue() > 0,
                "Expected a positive registration ID");
        var createdAt = instant(actual.path("createdAt"));
        var purchase = new Purchase(actual.path("id").longValue(), createdAt);
        var expected = purchaseHeader(purchase).put("accountId", account.id());
        expected.set("items", registrationItems());
        expect(actual, expected); // Exactly six fields; no cart, clearing or idempotency promise.
        assertRegisteredDelta(before, after, purchase, account.id());
        System.out.println("T028_POST context=" + identity() + " expected=201 actual=" + response.statusCode() + " newRoot=true");
        return purchase;
    }

    private void assertRegisteredDelta(Snapshot before, Snapshot after, Purchase purchase, long buyer) {
        var roots = after.data().orders().stream().filter(row -> row.id() == purchase.id()).toList();
        require(roots.size() == 1 && before.data().orders().stream().noneMatch(row -> row.id() == purchase.id()), "Expected one new root");
        var root = roots.getFirst();
        require(root.buyerId() == buyer && root.createdAt().equals(purchase.createdAt()) && root.status().equals("WAITING_PAYMENT"),
                "Persist the authenticated buyer, creation instant and stored state");
        require(after.data().orders().stream().filter(row -> row.id() != purchase.id()).toList().equals(before.data().orders()),
                "The POST must keep all previous own and foreign roots intact");
        require(after.data().items().stream().filter(row -> row.orderId() != purchase.id()).toList().equals(before.data().items()),
                "The POST must keep every previous item intact");
        var items = after.data().items().stream().filter(row -> row.orderId() == purchase.id()).toList();
        require(items.size() == 2 && items.get(0).id() != items.get(1).id(), "Persist exactly two distinct new item rows");
        require(items.get(0).position() == 0 && items.get(0).productId() == scenario.productB().id()
                        && items.get(0).quantity() == 2 && items.get(0).unitPrice().toPlainString().equals("12.00"), "Keep the first submitted line");
        require(items.get(1).position() == 1 && items.get(1).productId() == scenario.productA().id()
                        && items.get(1).quantity() == 3 && items.get(1).unitPrice().toPlainString().equals("99.00"), "Keep the second submitted line");
        require(before.data().products().equals(after.data().products()) && before.data().categories().equals(after.data().categories())
                        && before.data().associations().equals(after.data().associations()), "Registration cannot change this catalog");
        require(before.accountIds().equals(after.accountIds()) && before.roles().equals(after.roles()), "Keep account identities and roles");
    }

    private Target target(String kind) {
        var own = scenario.expectedClientOrders();
        if (kind.equals("absent-id")) {
            require(database.snapshot().data().orders().stream().noneMatch(row -> row.id() == Long.MAX_VALUE),
                    "The absent rejection control must not be a persisted order");
        }
        var path = switch (kind) {
            case "own-list", "unauthenticated-list", "denied-list" -> "/orders?page=0&size=2";
            case "empty-list" -> "/orders?page=0&size=2";
            case "later-empty-list", "beyond-list" -> "/orders?page=3&size=2";
            case "own-detail", "unauthenticated-detail", "denied-detail" -> "/orders/" + own.getLast().id();
            case "invalid-page" -> "/orders?page=-1&size=2";
            case "invalid-id" -> "/orders/0";
            case "foreign-id" -> "/orders/" + scenario.insertedOrders().get(1).id();
            case "absent-id" -> "/orders/" + Long.MAX_VALUE;
            default -> throw new AssertionError("Unknown regression target");
        };
        path += (path.contains("?") ? "&" : "?") + "private=must-not-appear";
        var account = kind.startsWith("unauthenticated") ? null : kind.startsWith("denied") ? scenario.accounts().none()
                : kind.contains("empty-list") ? scenario.emptyBuyer() : scenario.accounts().client();
        int status = kind.startsWith("unauthenticated") ? 401 : kind.startsWith("denied") ? 403
                : kind.startsWith("invalid") ? 400 : kind.endsWith("-id") ? 404 : 200;
        ObjectNode body;
        if (status != 200) {
            body = mapper.createObjectNode().put("status", status)
                    .put("error", status == 401 ? "Unauthorized" : status == 403 ? "Forbidden" : status == 400 ? "Bad Request" : "Not Found")
                    .put("message", status == 401 ? "Authentication required or invalid" : status == 403 ? "Order query not permitted"
                            : status == 400 ? "Invalid order query data" : "Order not found")
                    .put("path", contextPath() + path.split("\\?", 2)[0]);
        } else if (kind.equals("own-detail")) { body = detail(own.getLast()); }
        else {
            int page = kind.equals("later-empty-list") || kind.equals("beyond-list") ? 3 : 0;
            int total = kind.contains("empty-list") ? 0 : 5;
            body = mapper.createObjectNode().put("page", page).put("size", 2).put("totalElements", total).put("totalPages", total == 0 ? 0 : 3);
            var content = body.putArray("content");
            if (kind.equals("own-list")) { own.subList(0, 2).forEach(order -> content.add(header(order))); }
        }
        return new Target(path, account, status, body);
    }

    private String login(Account account) {
        outsideTransaction();
        try {
            var response = send(HttpRequest.newBuilder(uri("/login")).timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(Map.of("name", account.name(), "password", account.password())))).build());
            logins++;
            require(response.statusCode() == 200, "Expected real regression-fixture login");
            var credential = mapper.readValue(response.body(), TokenResponseDTO.class);
            require("Bearer".equals(credential.tokenType()) && credential.accessToken() != null, "Expected real Bearer credential");
            require(Long.toString(account.id()).equals(decoder.decode(credential.accessToken()).getSubject()), "Login identifies the fixture account");
            return credential.accessToken();
        } catch (IOException | JwtException failure) { throw new IllegalStateException("Regression fixture login could not be verified"); }
    }

    private byte[] registrationBody() {
        var body = mapper.createObjectNode();
        var items = body.putArray("items");
        items.addObject().put("productId", scenario.productB().id()).put("quantity", 2);
        items.addObject().put("productId", scenario.productA().id()).put("quantity", 3);
        try { return mapper.writeValueAsBytes(body); }
        catch (IOException failure) { throw new AssertionError("Explicit registration request must be serializable"); }
    }

    private com.fasterxml.jackson.databind.node.ArrayNode registrationItems() {
        var items = mapper.createArrayNode();
        items.addObject().put("productId", scenario.productB().id()).put("quantity", 2).put("unitPrice", "12.00").put("subtotal", "24.00");
        items.addObject().put("productId", scenario.productA().id()).put("quantity", 3).put("unitPrice", "99.00").put("subtotal", "297.00");
        return items;
    }

    private ObjectNode purchaseHeader(Purchase purchase) {
        return mapper.createObjectNode().put("id", purchase.id()).put("createdAt", INSTANT.format(purchase.createdAt()))
                .put("status", "WAITING_PAYMENT").put("total", "321.00");
    }

    private ObjectNode purchaseDetail(Purchase purchase) {
        var body = purchaseHeader(purchase);
        var items = registrationItems();
        ((ObjectNode) items.get(0)).put("productName", scenario.productB().name());
        ((ObjectNode) items.get(1)).put("productName", scenario.productA().name());
        body.set("items", items);
        return body;
    }

    private ObjectNode header(OrderManifest order) {
        return mapper.createObjectNode().put("id", order.id()).put("createdAt", INSTANT.format(order.createdAt()))
                .put("status", order.status()).put("total", order.total());
    }

    private ObjectNode detail(OrderManifest order) {
        var body = header(order);
        var items = body.putArray("items");
        order.items().forEach(item -> items.addObject().put("productId", item.productId()).put("productName", item.productName())
                .put("quantity", item.quantity()).put("unitPrice", item.unitPrice()).put("subtotal", item.subtotal()));
        return body;
    }

    private void assertOwnPage(HttpResponse<byte[]> response, List<ObjectNode> headers) {
        var page = json(response.body());
        require(page.isObject(), "Expected a page object");
        var expectedHeaders = new LinkedHashMap<Long, ObjectNode>();
        headers.forEach(header -> expectedHeaders.put(header.get("id").longValue(), header));
        require(expectedHeaders.size() == headers.size(), "The explicit header manifest must have unique IDs");
        require(page.path("content").isArray() && page.path("content").size() == headers.size(), "List all and only this owner's purchases");
        var metadata = (ObjectNode) page.deepCopy();
        metadata.remove("content");
        expect(metadata, mapper.createObjectNode().put("page", 0).put("size", 12).put("totalElements", headers.size()).put("totalPages", 1));
        var seen = new HashSet<Long>();
        for (var row : page.get("content")) {
            require(row.path("id").isIntegralNumber() && row.path("id").canConvertToLong(), "Expected integral order ID");
            long id = row.path("id").longValue();
            require(seen.add(id) && expectedHeaders.containsKey(id), "Each own purchase must occur exactly once");
            expect(row, expectedHeaders.get(id));
        }
        require(seen.equals(expectedHeaders.keySet()), "No order may be lost or inherited from another account");
    }

    private Account account(String identity) {
        return switch (identity) {
            case "client" -> scenario.accounts().client();
            case "admin" -> scenario.accounts().admin();
            case "both" -> scenario.accounts().both();
            default -> throw new AssertionError("Expected an authorized regression account");
        };
    }

    private List<OrderManifest> ownedOrders(String identity) {
        return switch (identity) {
            case "client" -> scenario.expectedClientOrders();
            case "admin" -> List.of(scenario.insertedOrders().get(3));
            case "both" -> List.of(scenario.insertedOrders().get(7));
            default -> throw new AssertionError("Expected an authorized regression account");
        };
    }

    private Set<Long> ownedAccountIds() {
        return Set.of(scenario.accounts().client().id(), scenario.accounts().admin().id(), scenario.accounts().both().id(),
                scenario.accounts().none().id(), scenario.otherBuyer().id(), scenario.emptyBuyer().id());
    }

    private JsonNode json(byte[] body) {
        try {
            var result = mapper.readTree(body);
            require(result != null, "Expected a nonempty JSON response");
            return result;
        }
        catch (IOException failure) { throw new AssertionError("Regression response must be valid JSON"); }
    }

    private void expect(JsonNode actual, JsonNode expected) {
        try { require(actual.equals(mapper.readTree(mapper.writeValueAsBytes(expected))), "Expected exact safe regression JSON"); }
        catch (IOException failure) { throw new AssertionError("Explicit regression oracle must be serializable"); }
    }

    private static Instant instant(JsonNode value) {
        require(value.isTextual(), "Expected a creation instant");
        try {
            var result = Instant.parse(value.textValue());
            require(INSTANT.format(result).equals(value.textValue()), "Preserve UTC millisecond representation");
            return result;
        } catch (java.time.format.DateTimeParseException failure) { throw new AssertionError("Expected a valid creation instant"); }
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try { return http.send(request, HttpResponse.BodyHandlers.ofByteArray()); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("Regression HTTP interrupted"); }
        catch (IOException failure) { throw new IllegalStateException("Regression HTTP transport failed"); }
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + contextPath() + path); }

    private void assertEmpty() {
        var snapshot = database.snapshot();
        require(snapshot.accountIds().isEmpty() && snapshot.roles().isEmpty() && snapshot.data().orders().isEmpty()
                        && snapshot.data().items().isEmpty() && snapshot.data().products().isEmpty()
                        && snapshot.data().categories().isEmpty() && snapshot.data().associations().isEmpty(),
                "Only this fixture's seed-free rows may exist and they must be cleaned after each case");
    }

    private static void outsideTransaction() {
        require(!TransactionSynchronizationManager.isActualTransactionActive(), "Real regression HTTP must have no caller transaction");
    }

    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }

    static Stream<Arguments> readCases() {
        return Stream.of("own-list", "own-detail", "empty-list", "later-empty-list", "beyond-list", "unauthenticated-list",
                        "unauthenticated-detail", "denied-list", "denied-detail", "invalid-page", "invalid-id", "foreign-id", "absent-id")
                .flatMap(kind -> Stream.of("GET", "HEAD").map(method -> Arguments.of(kind, method)));
    }

    private record Target(String path, Account account, int status, ObjectNode body) { }
    private record Purchase(long id, Instant createdAt) { }
}
