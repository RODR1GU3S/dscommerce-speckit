package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ItemRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** T069: actual servlet HTTP, production filters/services/JWT and isolated H2; no mocks or outer transaction. */
@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.open-in-view=false", "server.address=127.0.0.1", "server.servlet.context-path=/api"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
class AdminProductPublicRegressionIntegrationTest {
    private static final String INVALID_TOKEN = "not-a-valid-jwt";
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtDecoder decoder;
    @LocalServerPort private int port;
    private AdminProductTestFixture fixture;
    private AdminProductDatabaseProbe database;
    private HttpClient client;
    private final List<Long> buyers = new ArrayList<>();

    @BeforeEach
    void isolatedSeedFreeContext() {
        noCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmpty();
        assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("test");
        assertThat(context.getEnvironment().getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(context.getEnvironment().getProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")).isEmpty();
        assertThat(Arrays.stream(context.getBeanDefinitionNames()).map(context::getType).filter(Objects::nonNull)
                .map(Class::getName).anyMatch(name -> name.contains(".demo.") || name.endsWith("OrderTestFixture"))).isFalse();
        assertThat(AopUtils.isAopProxy(context.getBean(com.devsuperior.dscommerce.services.OrderService.class))).isTrue();
        assertThat(AopUtils.isAopProxy(context.getBean(com.devsuperior.dscommerce.services.ProductMaintenanceService.class))).isTrue();
        fixture = new AdminProductTestFixture(context);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach
    void cleanOwnedRowsOnlyAfterObservations() {
        noCallerTransaction();
        if (client != null) client.close();
        if (fixture != null) {
            for (long buyer : buyers) {
                jdbc.update("delete from tb_order_item where order_id in (select id from tb_order where buyer_id = ?)", buyer);
                jdbc.update("delete from tb_order where buyer_id = ?", buyer);
            }
            fixture.close();
            assertEmpty();
        }
    }

    @ParameterizedTest(name = "public catalog/detail, irrelevant invalid token={0}")
    @ValueSource(booleans = {false, true})
    void publicReadingKeepsPaginationOrderingNumericPricesAndLegacyData(boolean invalid) throws Exception {
        var numbered = new ArrayList<ProductData>();
        for (int index = 13; index >= 1; index--) {
            numbered.add(fixture.createProduct("Product %02d".formatted(index), index == 13 ? "Old" : "Description long enough",
                    index == 13 ? "legacy-image" : "https://example.invalid/product.jpg", new BigDecimal(index == 13 ? "0.00" : "10.00")));
        }
        Collections.reverse(numbered);
        var first = product("Duplicate", "7.50");
        var second = product("Duplicate", "3.25");
        var expected = new ArrayList<>(List.of(first, second));
        expected.addAll(numbered);
        var z = fixture.createCategory("Zulu");
        var a = fixture.createCategory("Alpha");
        var same = fixture.createCategory("Alpha");
        fixture.associate(first.id(), z.id());
        fixture.associate(first.id(), same.id());
        fixture.associate(first.id(), a.id());
        var before = database.snapshot();
        String token = invalid ? INVALID_TOKEN : null;
        assertPage(json(send("GET", "/products", token, null, null), 200), 0, 12, expected, false);
        var seen = new ArrayList<Long>();
        for (int page = 0; page < 8; page++) {
            var response = json(send("GET", "/products?page=" + page + "&size=2", token, null, null), 200);
            assertPage(response, page, 2, expected, false);
            response.path("content").forEach(row -> seen.add(row.path("id").longValue()));
        }
        assertThat(seen).containsExactlyElementsOf(expected.stream().map(ProductData::id).toList()).doesNotHaveDuplicates();
        assertPage(json(send("GET", "/products?page=999", token, null, null), 200), 999, 12, expected, false);
        assertPage(json(send("GET", "/products?size=50", token, null, null), 200), 0, 50, expected, false);
        assertPage(json(send("GET", "/products?page=1", token, null, null), 200), 1, 12, expected, false);
        for (var value : expected) {
            var detail = json(send("GET", "/products/" + value.id(), token, null, null), 200);
            assertProduct(detail, value, true);
            assertThat(detail.path("categories")).isEqualTo(mapper.valueToTree(value.id() == first.id() ? List.of("Alpha", "Zulu") : List.of()));
        }
        assertThat(database.snapshot()).isEqualTo(before);
        proof("public-reading", invalid ? "INVALID_IRRELEVANT" : "ANONYMOUS");
    }

    @ParameterizedTest(name = "public errors/empty catalog, irrelevant invalid token={0}")
    @ValueSource(booleans = {false, true})
    void publicErrorsAndEmptyPageKeepFourFieldEnvelope(boolean invalid) throws Exception {
        String token = invalid ? INVALID_TOKEN : null;
        assertPage(json(send("GET", "/products", token, null, null), 200), 0, 12, List.of(), false);
        assertError(send("GET", "/products?page=-1", token, null, null), 400, "page must be greater than or equal to 0", "/products");
        for (int size : List.of(0, 51)) {
            assertError(send("GET", "/products?size=" + size, token, null, null), 400, "size must be between 1 and 50", "/products");
        }
        assertError(send("GET", "/products/9223372036854775807", token, null, null), 404, "Product not found", "/products/9223372036854775807");
        assertEmpty();
        proof("public-errors", invalid ? "INVALID_IRRELEVANT" : "ANONYMOUS");
    }

    @ParameterizedTest(name = "public name filter/category representations, irrelevant invalid token={0}")
    @ValueSource(booleans = {false, true})
    void publicNameFilterPreservesCategoryRepresentationsAndAdministrativeIsolation(boolean invalid) throws Exception {
        var alpha = product("Alpha", "10.00");
        var beta = product("Beta", "12.00");
        var c1 = fixture.createCategory("Same name");
        var c2 = fixture.createCategory("Same name");
        fixture.associate(alpha.id(), c1.id());
        fixture.associate(alpha.id(), c2.id());
        var token = login(account(Set.of(UserRole.ADMIN)), false);
        var before = database.snapshot();
        var admin = json(send("GET", "/admin/products?name=ALP", token, null, null), 200);
        assertThat(admin.path("totalElements").longValue()).isEqualTo(1);
        assertThat(admin.path("content").get(0).path("id").longValue()).isEqualTo(alpha.id());
        assertThat(admin.path("content").get(0).path("price").isTextual()).isTrue();
        var edit = json(send("GET", "/admin/products/" + alpha.id(), token, null, null), 200);
        assertThat(edit.path("categories").size()).isEqualTo(2);
        var ids = new ArrayList<Long>();
        edit.path("categories").forEach(row -> {
            fields(row, "id", "name");
            assertThat(row.path("name").textValue()).isEqualTo("Same name");
            ids.add(row.path("id").longValue());
        });
        assertThat(ids).containsExactlyInAnyOrder(c1.id(), c2.id());
        assertPage(json(send("GET", "/products", invalid ? INVALID_TOKEN : null, null, null), 200),
                0, 12, List.of(alpha, beta), false);
        assertPage(json(send("GET", "/products?name=ALP", invalid ? INVALID_TOKEN : null, null, null), 200),
                0, 12, List.of(alpha), false);
        assertPage(json(send("GET", "/products?name=does-not-exist", invalid ? INVALID_TOKEN : null, null, null), 200),
                0, 12, List.of(), false);
        assertPage(json(send("GET", "/products?name=%25_", invalid ? INVALID_TOKEN : null, null, null), 200),
                0, 12, List.of(), false);
        var publicDetail = json(send("GET", "/products/" + alpha.id(), invalid ? INVALID_TOKEN : null, null, null), 200);
        assertProduct(publicDetail, alpha, true);
        assertThat(publicDetail.path("categories")).isEqualTo(mapper.valueToTree(List.of("Same name")));
        assertThat(database.snapshot()).isEqualTo(before);
        proof("scoped-filter", invalid ? "INVALID_IRRELEVANT" : "ANONYMOUS");
    }

    static Stream<Arguments> loginCases() {
        return Stream.of(Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN), Set.of(UserRole.CLIENT, UserRole.ADMIN), Set.<UserRole>of())
                .flatMap(roles -> Stream.of(Arguments.of(roles, false), Arguments.of(roles, true)));
    }

    @ParameterizedTest(name = "login roles={0}, irrelevant invalid token={1}")
    @MethodSource("loginCases")
    void loginKeepsExactCredentialsTokenAndSafeRejections(Set<UserRole> roles, boolean invalid) throws Exception {
        var account = account(roles);
        var before = database.snapshot();
        login(account, invalid);
        String bearer = invalid ? INVALID_TOKEN : null;
        for (var body : List.of(Map.of("name", account.name(), "password", "wrong-password"),
                Map.of("name", "missing-" + UUID.randomUUID(), "password", account.password()),
                Map.of("name", account.name().toUpperCase(java.util.Locale.ROOT), "password", account.password()))) {
            assertError(send("POST", "/login", bearer, mapper.writeValueAsString(body), "application/json"), 401, "Invalid credentials", "/login");
        }
        assertError(send("POST", "/login", bearer, "{\"name\":\" \",\"password\":\" \"}", "application/json"), 400, "Invalid login data", "/login");
        assertThat(database.snapshot()).isEqualTo(before);
        assertThat(database.accountIds()).containsExactly(account.id());
        assertThat(database.roles(account.id())).isEqualTo(roles);
        proof("login", roles + "/invalid=" + invalid);
    }

    static Stream<Set<UserRole>> buyers() {
        return Stream.of(Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN), Set.of(UserRole.CLIENT, UserRole.ADMIN));
    }

    @ParameterizedTest(name = "orders and history roles={0}")
    @MethodSource("buyers")
    void ordersKeepAuthorizationNewOrderPerSendAndHistoricalValuesAfterMaintenance(Set<UserRole> roles) throws Exception {
        var buyer = account(roles);
        var token = login(buyer, false);
        var adminToken = login(account(Set.of(UserRole.ADMIN)), false);
        var target = product("Product old price", "10.00");
        var other = product("Product other", "3.25");
        var category = fixture.createCategory("History category");
        fixture.associate(target.id(), category.id());
        var body = orderBody(target.id(), other.id());
        var first = confirmedOrder(send("POST", "/orders", token, body, "application/json"), buyer.id(), target.id(), other.id(), "10.00", "29.75");
        var old = database.snapshot();
        assertHistory(old, first, buyer.id(), target.id(), other.id(), "10.00", "29.75");
        var replacement = mapper.createObjectNode().put("name", target.name()).put("description", target.description())
                .put("image", target.image()).put("price", "12.00");
        replacement.putArray("categoryIds").add(category.id());
        var updated = json(send("PUT", "/admin/products/" + target.id(), adminToken, replacement.toString(), "application/json"), 200);
        assertThat(updated.path("id").longValue()).isEqualTo(target.id());
        assertThat(updated.path("price").textValue()).isEqualTo("12.00");
        var afterPut = database.snapshot();
        assertThat(afterPut.orders()).isEqualTo(old.orders());
        assertThat(afterPut.items()).isEqualTo(old.items());
        assertThat(afterPut.categories()).isEqualTo(old.categories());
        assertThat(afterPut.associations()).isEqualTo(old.associations());
        assertThat(afterPut.products().stream().filter(row -> row.id() == target.id()).findFirst().orElseThrow().price()).isEqualByComparingTo("12.00");
        var detail = json(send("GET", "/products/" + target.id(), INVALID_TOKEN, null, null), 200);
        assertThat(detail.path("price").isNumber()).isTrue();
        assertThat(detail.path("price").decimalValue()).isEqualByComparingTo("12.00");
        var denied = json(send("DELETE", "/admin/products/" + target.id(), adminToken, null, null), 409);
        fields(denied, "status", "error", "message", "path", "errors");
        assertThat(denied.path("message").textValue()).isEqualTo("Product deletion violates referential integrity");
        assertThat(denied.path("errors")).isEmpty();
        assertThat(database.snapshot()).isEqualTo(afterPut);
        var second = confirmedOrder(send("POST", "/orders", token, body, "application/json"), buyer.id(), target.id(), other.id(), "12.00", "33.75");
        var third = confirmedOrder(send("POST", "/orders", token, body, "application/json"), buyer.id(), target.id(), other.id(), "12.00", "33.75");
        assertThat(List.of(first, second, third)).doesNotHaveDuplicates();
        var finalState = database.snapshot();
        assertThat(finalState.orders()).hasSize(3);
        assertThat(finalState.items()).hasSize(6);
        assertThat(finalState.orders().stream().filter(row -> row.id() == first).toList()).isEqualTo(old.orders());
        assertThat(finalState.items().stream().filter(row -> row.orderId() == first).toList()).isEqualTo(old.items());
        assertThat(finalState.products()).isEqualTo(afterPut.products());
        assertThat(finalState.categories()).isEqualTo(afterPut.categories());
        assertThat(finalState.associations()).isEqualTo(afterPut.associations());
        assertHistory(finalState, first, buyer.id(), target.id(), other.id(), "10.00", "29.75");
        assertHistory(finalState, second, buyer.id(), target.id(), other.id(), "12.00", "33.75");
        assertHistory(finalState, third, buyer.id(), target.id(), other.id(), "12.00", "33.75");
        proof("orders-history", roles.toString());
    }

    enum Rejection { INVALID_BODY, DUPLICATE, MISSING, ZERO_PRICE }
    static Stream<Arguments> orderRejections() {
        return buyers().flatMap(roles -> Arrays.stream(Rejection.values()).map(reason -> Arguments.of(roles, reason)));
    }

    @ParameterizedTest(name = "order rejection roles={0}, reason={1}")
    @MethodSource("orderRejections")
    void rejectedOrdersKeepExistingEnvelopeAndNoPartialRows(Set<UserRole> roles, Rejection reason) throws Exception {
        var token = login(account(roles), false);
        var target = product("Product rejected", reason == Rejection.ZERO_PRICE ? "0.00" : "10.00");
        var before = database.snapshot();
        String line = "{\"productId\":" + target.id() + ",\"quantity\":2}";
        String body = switch (reason) {
            case INVALID_BODY -> "{\"items\":[]}";
            case DUPLICATE -> "{\"items\":[" + line + "," + line + "]}";
            case MISSING -> "{\"items\":[" + line + ",{\"productId\":9223372036854775807,\"quantity\":1}]}";
            case ZERO_PRICE -> "{\"items\":[" + line + "]}";
        };
        int status = reason == Rejection.INVALID_BODY ? 400 : reason == Rejection.MISSING ? 404 : 422;
        String message = switch (reason) {
            case INVALID_BODY -> "Invalid order data";
            case DUPLICATE -> "Duplicate product in order";
            case MISSING -> "Product not found";
            case ZERO_PRICE -> "Product price is not valid for order";
        };
        assertError(send("POST", "/orders", token, body, "application/json"), status, message, "/orders");
        assertThat(database.snapshot()).isEqualTo(before);
        proof("order-rejection-" + reason, roles.toString());
    }

    @ParameterizedTest(name = "order security rejection={0}")
    @ValueSource(strings = {"ANONYMOUS", "INVALID", "NO_ROLE"})
    void securityStillPrecedesInvalidOrderBody(String identity) throws Exception {
        String token = identity.equals("ANONYMOUS") ? null : identity.equals("INVALID") ? INVALID_TOKEN : login(account(Set.of()), false);
        var before = database.snapshot();
        int status = identity.equals("NO_ROLE") ? 403 : 401;
        var response = send("POST", "/orders", token, "broken", "text/plain");
        assertError(response, status, status == 401 ? "Authentication required or invalid" : "Order registration not permitted", "/orders");
        if (status == 401) assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(database.snapshot()).isEqualTo(before);
        proof("order-security", identity);
    }

    @ParameterizedTest(name = "native order 415 after positive control roles={0}")
    @MethodSource("buyers")
    void unsupportedOrderMediaTypeKeepsServletEnvelopeWithoutAdminFields(Set<UserRole> roles) throws Exception {
        var buyer = account(roles);
        var token = login(buyer, false);
        var target = product("HTTP media type product", "10.00");
        String body = "{\"items\":[{\"productId\":" + target.id() + ",\"quantity\":2}]}";
        var control = json(send("POST", "/orders", token, body, "application/json"), 201);
        assertThat(control.path("total").textValue()).isEqualTo("20.00");
        var before = database.snapshot();
        assertThat(before.orders()).hasSize(1);
        assertThat(before.items()).hasSize(1);
        var response = send("POST", "/orders", token, body, "text/plain");
        var error = json(response, 415);
        // Boot's servlet error dispatch is different from the four-field business-error DTO.
        fields(error, "timestamp", "status", "error", "path");
        assertThat(error.path("timestamp").isTextual()).isTrue();
        assertThat(error.path("timestamp").textValue()).isNotBlank();
        assertThat(error.path("status").intValue()).isEqualTo(415);
        assertThat(error.path("error").textValue()).isEqualTo("Unsupported Media Type");
        assertThat(error.path("path").textValue()).isEqualTo("/api/orders");
        assertThat(response.headers().firstValue("Accept").orElseThrow()).contains("application/json");
        assertThat(database.snapshot()).isEqualTo(before);
        proof("order-native-415", roles.toString());
    }

    private Account account(Set<UserRole> roles) {
        var value = fixture.createAccount("t069-" + UUID.randomUUID(), UUID.randomUUID().toString(), roles);
        buyers.add(value.id());
        return value;
    }

    private ProductData product(String name, String price) {
        return fixture.createProduct(name, "Description long enough", "https://example.invalid/t069.jpg", new BigDecimal(price));
    }

    private String login(Account account, boolean invalidBearer) throws Exception {
        var response = send("POST", "/login", invalidBearer ? INVALID_TOKEN : null,
                mapper.writeValueAsString(Map.of("name", account.name(), "password", account.password())), "application/json");
        // Never assert/print an entire object containing a password or token.
        assertThat(response.statusCode()).as("real login preparation").isEqualTo(200);
        var body = mapper.readTree(response.body());
        fields(body, "accessToken", "tokenType");
        assertThat(body.path("tokenType").textValue()).isEqualTo("Bearer");
        assertThat(body.path("accessToken").isTextual()).isTrue();
        String token = body.path("accessToken").textValue();
        assertThat(token == null || token.isBlank()).isFalse();
        var jwt = decoder.decode(token);
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(jwt.getSubject().equals(Long.toString(account.id()))).isTrue();
        assertThat(jwt.getClaimAsString("name").equals(account.name())).isTrue();
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt()).isAfter(Instant.now());
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
        return token;
    }

    private HttpResponse<String> send(String method, String path, String token, String body, String type) throws Exception {
        noCallerTransaction();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
                .timeout(Duration.ofSeconds(20)).header("Accept", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (type != null) request.header("Content-Type", type);
        var result = client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        noCallerTransaction();
        return result;
    }

    private JsonNode json(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("SAMEORIGIN");
        return mapper.readTree(response.body());
    }

    private void assertError(HttpResponse<String> response, int status, String message, String path) throws Exception {
        var body = json(response, status);
        var expected = mapper.createObjectNode().put("status", status)
                .put("error", org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase())
                .put("message", message).put("path", "/api" + path);
        assertThat(body).isEqualTo(expected);
        assertThat(response.headers().firstValue("Location")).isEmpty();
    }

    private static void fields(JsonNode object, String... names) {
        assertThat(object.isObject()).isTrue();
        var actual = new ArrayList<String>();
        object.fieldNames().forEachRemaining(actual::add);
        assertThat(actual).containsExactlyInAnyOrder(names);
    }

    private void assertPage(JsonNode page, int number, int size, List<ProductData> expected, boolean detail) {
        fields(page, "content", "page", "size", "totalElements", "totalPages");
        for (String field : List.of("page", "size", "totalElements", "totalPages")) assertThat(page.path(field).isIntegralNumber()).isTrue();
        assertThat(page.path("page").intValue()).isEqualTo(number);
        assertThat(page.path("size").intValue()).isEqualTo(size);
        assertThat(page.path("totalElements").longValue()).isEqualTo(expected.size());
        assertThat(page.path("totalPages").intValue()).isEqualTo((expected.size() + size - 1) / size);
        assertThat(page.path("content").isArray()).isTrue();
        var start = Math.min(number * size, expected.size());
        var rows = expected.subList(start, Math.min(start + size, expected.size()));
        assertThat(page.path("content").size()).isEqualTo(rows.size());
        for (int index = 0; index < rows.size(); index++) assertProduct(page.path("content").get(index), rows.get(index), detail);
    }

    private static void assertProduct(JsonNode row, ProductData expected, boolean detail) {
        if (detail) fields(row, "id", "name", "description", "image", "price", "categories");
        else fields(row, "id", "name", "image", "price");
        assertThat(row.path("id").isIntegralNumber()).isTrue();
        assertThat(row.path("id").longValue()).isEqualTo(expected.id());
        assertThat(row.path("name").textValue()).isEqualTo(expected.name());
        assertThat(row.path("image").textValue()).isEqualTo(expected.image());
        assertThat(row.path("price").isNumber()).isTrue();
        assertThat(row.path("price").decimalValue()).isEqualByComparingTo(expected.price());
        if (detail) {
            assertThat(row.path("description").textValue()).isEqualTo(expected.description());
            assertThat(row.path("categories").isArray()).isTrue();
        }
    }

    private String orderBody(long target, long other) throws Exception {
        return mapper.writeValueAsString(Map.of("items", List.of(Map.of("productId", other, "quantity", 3), Map.of("productId", target, "quantity", 2))));
    }

    private long confirmedOrder(HttpResponse<String> response, long buyer, long target, long other, String price, String total) throws Exception {
        var body = json(response, 201);
        fields(body, "id", "accountId", "createdAt", "status", "items", "total");
        assertThat(body.path("id").isIntegralNumber()).isTrue();
        assertThat(body.path("id").longValue()).isPositive();
        assertThat(body.path("accountId").longValue()).isEqualTo(buyer);
        assertThat(body.path("createdAt").textValue()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
        assertThat(body.path("status").textValue()).isEqualTo("WAITING_PAYMENT");
        assertThat(body.path("total").textValue()).isEqualTo(total);
        assertThat(body.path("items")).isEqualTo(mapper.readTree(mapper.writeValueAsString(List.of(
                Map.of("productId", other, "quantity", 3, "unitPrice", "3.25", "subtotal", "9.75"),
                Map.of("productId", target, "quantity", 2, "unitPrice", price, "subtotal", new BigDecimal(price).multiply(BigDecimal.valueOf(2)).toPlainString())))));
        assertThat(response.headers().firstValue("Location")).isEmpty();
        long id = body.path("id").longValue();
        var snapshot = database.snapshot();
        assertHistory(snapshot, id, buyer, target, other, price, total);
        assertThat(snapshot.orders().stream().filter(row -> row.id() == id).findFirst().orElseThrow().createdAt()).isEqualTo(Instant.parse(body.path("createdAt").textValue()));
        return id;
    }

    private static void assertHistory(Snapshot state, long id, long buyer, long target, long other, String price, String total) {
        var order = state.orders().stream().filter(row -> row.id() == id).toList();
        assertThat(order).hasSize(1);
        assertThat(order.getFirst().buyerId()).isEqualTo(buyer);
        assertThat(order.getFirst().status()).isEqualTo("WAITING_PAYMENT");
        var items = state.items().stream().filter(row -> row.orderId() == id).toList();
        assertThat(items).hasSize(2);
        assertThat(items).extracting(ItemRow::id).doesNotHaveDuplicates().allMatch(value -> value > 0);
        assertThat(items).extracting(ItemRow::position).containsExactly(0, 1);
        assertThat(items).extracting(ItemRow::productId).containsExactly(other, target);
        assertThat(items).extracting(ItemRow::quantity).containsExactly(3, 2);
        assertThat(items.getFirst().unitPrice()).isEqualByComparingTo("3.25");
        assertThat(items.get(1).unitPrice()).isEqualByComparingTo(price);
        // These totals are independent decimal calculations, not nonexistent SQL subtotal/total columns.
        var subtotals = items.stream().map(row -> row.unitPrice().multiply(BigDecimal.valueOf(row.quantity()))).toList();
        assertThat(subtotals.getFirst()).isEqualByComparingTo("9.75");
        assertThat(subtotals.get(1)).isEqualByComparingTo(new BigDecimal(price).multiply(BigDecimal.valueOf(2)));
        assertThat(subtotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total);
    }

    private void assertEmpty() {
        assertThat(database.snapshot()).isEqualTo(new Snapshot(List.of(), List.of(), Set.of(), List.of(), List.of()));
        assertThat(database.accountIds()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from tb_user_account_role", Long.class)).isZero();
    }

    private static void noCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    private static void proof(String group, String identity) {
        noCallerTransaction();
        System.out.printf("T069_PROOF group=%s identity=%s transport=servlet-http database=H2 independentRead=true callerTransaction=false%n", group, identity);
    }
}
