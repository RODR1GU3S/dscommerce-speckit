package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.OrderData;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.Scenario;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;

@RootHttpContext
class CustomerRegistrationRegressionProtectedHttpIntegrationTest extends CustomerRegistrationRegressionProtectedScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T090 authorship only: T092 owns compilation, discovery, JWT/KDF/HTTP and observed results. */
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class CustomerRegistrationRegressionProtectedScenarios extends CustomerRegistrationHttpTestSupport {
    private static final String NEW_NAME = "Protected.client";
    private static final String NEW_PASSWORD = "Protected-password-009";
    private static final String UPDATED_NAME = "Updated protected product";
    private static final String MAINTENANCE_NAME = "Maintenance protected product";
    private static final String MAINTENANCE_IMAGE = "https://example.invalid/protected.jpg";
    private final Set<Long> newAccounts = new LinkedHashSet<>();
    private final Set<Long> newOrders = new LinkedHashSet<>();
    private final Set<Long> newProducts = new LinkedHashSet<>();
    private boolean safeToClean = true;

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RegistrationOutcome.class)
    void eligibleLegacyAccountsCreateOnlyTheirOwnWaitingOrdersAtCurrentPrices(RegistrationOutcome outcome)
            throws SQLException {
        var scenario = prepare();
        register(outcome);
        var accountsBefore = database.snapshot();
        for (var buyer : List.of(scenario.access().client(), scenario.access().admin(), scenario.access().both())) {
            String token = login(buyer);
            var forbiddenOwner = orderInput(scenario).put("accountId", scenario.access().none().id());
            var beforeRejection = database.snapshot();
            assertError(request("POST", "/orders", token, forbiddenOwner), 400, "Bad Request",
                    "Invalid order data", "/orders", false);
            assertUnchanged(beforeRejection, database.snapshot());
            createOwnOrder(scenario, buyer.id(), token);
        }
        assertAccountsUnchanged(accountsBefore, database.snapshot());
        // The original historical lines are not repriced by these new purchases.
        assertHistoricalWaiting(scenario, login(scenario.access().client()), "WAITING_PAYMENT", "Produto anterior A");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RegistrationOutcome.class)
    void ownPagesCountsOrderAndHeadKeepTheSameOwnerAndHistoricalMoney(RegistrationOutcome outcome) {
        var scenario = prepare();
        var product = scenario.products().get(0);
        Instant tiedTime = Instant.parse("2026-01-04T10:00:00.123Z");
        var older = fixture.createOrder(scenario.access().client(), Instant.parse("2026-01-01T10:00:00.123Z"),
                List.of(new ItemInput(product.id(), 2, new BigDecimal("0.25")),
                        new ItemInput(scenario.products().get(1).id(), 4, new BigDecimal("2.25"))), null);
        var firstTie = fixture.createOrder(scenario.access().client(), tiedTime,
                List.of(new ItemInput(product.id(), 2, new BigDecimal("1.25"))), null);
        var secondTie = fixture.createOrder(scenario.access().client(), tiedTime,
                List.of(new ItemInput(product.id(), 3, new BigDecimal("2.00"))), tiedTime);
        assertThat(secondTie.id()).isGreaterThan(firstTie.id());
        register(outcome);
        var beforeReads = database.snapshot();
        String client = login(scenario.access().client());
        var ordered = List.of(summary(secondTie, "6.00"), summary(firstTie, "2.50"),
                summary(scenario.waiting(), "42.50"), summary(older, "9.50"));
        assertOwnPage(request("GET", "/orders", client, null), ordered, 0, 12, 4);
        assertOwnPage(request("GET", "/orders?page=0&size=2", client, null), ordered.subList(0, 2), 0, 2, 4);
        assertOwnPage(request("GET", "/orders?page=1&size=2", client, null), ordered.subList(2, 4), 1, 2, 4);
        assertOwnPage(request("GET", "/orders?page=2&size=2", client, null), List.of(), 2, 2, 4);
        assertOwnPage(request("GET", "/orders?accountId=" + scenario.access().both().id()
                + "&buyerId=" + scenario.access().admin().id() + "&sort=id,asc", client, null), ordered, 0, 12, 4);
        assertHead(request("HEAD", "/orders?page=0&size=2", client, null), 200);
        assertHistoricalWaiting(scenario, client, "WAITING_PAYMENT", "Produto anterior A");
        assertHead(request("HEAD", "/orders/" + scenario.waiting().id(), client, null), 200);
        assertNotOwned(client, scenario.paid().id());

        String admin = login(scenario.access().admin());
        assertOwnPage(request("GET", "/orders?page=4&size=1", admin, null), List.of(), 4, 1, 0);
        assertNotOwned(admin, scenario.waiting().id());
        String both = login(scenario.access().both());
        assertOwnPage(request("GET", "/orders", both, null), List.of(summary(scenario.paid(), "7.50")), 0, 12, 1);
        assertOwnDetail(request("GET", "/orders/" + scenario.paid().id(), both, null),
                scenario.paid(), "PAID", "7.50",
                List.of(new Line(product.id(), "Produto anterior A", 1, "7.50", "7.50")));
        assertNotOwned(both, scenario.waiting().id());
        assertUnchanged(beforeReads, database.snapshot());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RegistrationOutcome.class)
    void adminMaintenanceDoesNotRepriceHistoricalOrdersOrChangeLegacyAccounts(RegistrationOutcome outcome) {
        var scenario = prepare();
        register(outcome);
        String admin = login(scenario.access().admin());
        String client = login(scenario.access().client());
        var beforeMaintenance = database.snapshot();
        var categories = request("GET", "/admin/categories", admin, null);
        assertThat(categories.statusCode()).isEqualTo(200);
        assertJson(categories);
        var expectedCategories = mapper.createArrayNode().add(mapper.createObjectNode()
                .put("id", scenario.category().id()).put("name", "Catalogo anterior009"));
        assertBody(categories, expectedCategories);

        var created = mutate("POST", "/admin/products", admin,
                productInput(MAINTENANCE_NAME, "19.25", scenario.category().id()));
        assertThat(created.statusCode()).isEqualTo(201);
        long id = jsonBody(created).path("id").longValue();
        assertThat(id).isPositive();
        assertThat(uri("/admin/products/" + id).toString()
                .equals(created.headers().firstValue("Location").orElse(""))).isTrue();
        assertAdminProduct(created, id, MAINTENANCE_NAME, "19.25", scenario.category().id());
        var retrieved = request("GET", "/admin/products/" + id, admin, null);
        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertAdminProduct(retrieved, id, MAINTENANCE_NAME, "19.25", scenario.category().id());
        var page = request("GET", "/admin/products?name=Maintenance&page=0&size=1", admin, null);
        assertThat(page.statusCode()).isEqualTo(200);
        assertJson(page);
        var expectedPage = mapper.createObjectNode().put("page", 0).put("size", 1).put("totalElements", 1).put("totalPages", 1);
        expectedPage.putArray("content").add(mapper.createObjectNode().put("id", id).put("name", MAINTENANCE_NAME)
                .put("image", MAINTENANCE_IMAGE).put("price", "19.25"));
        assertBody(page, expectedPage);

        long existingProduct = scenario.products().get(0).id();
        var replaced = mutate("PUT", "/admin/products/" + existingProduct, admin,
                productInput(UPDATED_NAME, "150.25", scenario.category().id()));
        assertThat(replaced.statusCode()).isEqualTo(200);
        assertAdminProduct(replaced, existingProduct, UPDATED_NAME, "150.25", scenario.category().id());
        assertHistoricalWaiting(scenario, client, "WAITING_PAYMENT", UPDATED_NAME);
        var deleted = mutate("DELETE", "/admin/products/" + id, admin, null);
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(deleted.body().length).isZero();
        assertError(request("GET", "/admin/products/" + id, admin, null), 404, "Not Found",
                "Product not found", "/admin/products/" + id, true);
        assertOnlyTablesMayDiffer(beforeMaintenance, database.snapshot(), Set.of("TB_PRODUCT", "TB_PRODUCT_CATEGORY"));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RegistrationOutcome.class)
    void adminPaymentOfAnotherOwnersOrderDoesNotGrantReadOwnership(RegistrationOutcome outcome) throws SQLException {
        var scenario = prepare();
        register(outcome);
        String admin = login(scenario.access().admin());
        String owner = login(scenario.access().client());
        long id = scenario.waiting().id();
        assertNotOwned(admin, id);
        var before = database.snapshot();
        Instant occurredAt = Instant.parse("2026-01-04T10:00:00.123Z");
        String path = "/admin/orders/" + id + "/payments";
        var input = mapper.createObjectNode().put("occurredAt", wire(occurredAt));
        var response = mutate("POST", path, admin, input);
        assertThat(response.statusCode()).isEqualTo(201);
        assertJson(response);
        assertThat(response.headers().allValues("Location")).isEmpty();
        var expected = mapper.createObjectNode().put("orderId", id).put("occurredAt", wire(occurredAt)).put("status", "PAID");
        assertBody(response, expected);
        assertPaymentStored(id, occurredAt, scenario.access().client().id());
        var paid = database.snapshot();
        assertOnlyPaymentChanged(before, paid, id);
        assertHistoricalWaiting(scenario, owner, "PAID", "Produto anterior A");
        assertOwnPage(request("GET", "/orders", owner, null),
                List.of(new Summary(id, scenario.waiting().createdAt(), "PAID", "42.50")), 0, 12, 1);
        assertNotOwned(admin, id); // Both GET and HEAD must still return the same not-found result.
        assertOwnPage(request("GET", "/orders?accountId=" + scenario.access().client().id(), admin, null),
                List.of(), 0, 12, 0);
        assertError(mutate("POST", path, admin, input), 409, "Conflict", "Payment already registered", path, false);
        assertUnchanged(paid, database.snapshot());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RegistrationOutcome.class)
    void authorizationPrecedesInputAndAbsentProfilesAreNeverRepaired(RegistrationOutcome outcome) {
        var scenario = prepare();
        register(outcome);
        var before = database.snapshot();
        for (Actor actor : Actor.values()) {
            String token = switch (actor) {
                case ABSENT -> null;
                case INVALID -> "invalid-protected-token";
                case CLIENT -> login(scenario.access().client());
                case ADMIN -> login(scenario.access().admin());
                case BOTH -> login(scenario.access().both());
                case NONE -> login(scenario.access().none());
            };
            boolean authenticated = actor != Actor.ABSENT && actor != Actor.INVALID;
            boolean buyer = authenticated && actor != Actor.NONE;
            boolean admin = actor == Actor.ADMIN || actor == Actor.BOTH;
            var order = raw("POST", "/orders", token, "{");
            assertError(order, !authenticated ? 401 : buyer ? 400 : 403,
                    !authenticated ? "Unauthorized" : buyer ? "Bad Request" : "Forbidden",
                    !authenticated ? "Authentication required or invalid" : buyer ? "Invalid order data" : "Order registration not permitted",
                    "/orders", false);
            var page = request("GET", "/orders", token, null);
            if (buyer) {
                List<Summary> summaries = actor == Actor.CLIENT ? List.of(summary(scenario.waiting(), "42.50"))
                        : actor == Actor.BOTH ? List.of(summary(scenario.paid(), "7.50")) : List.of();
                assertOwnPage(page, summaries, 0, 12, summaries.size());
            } else {
                assertError(page, authenticated ? 403 : 401, authenticated ? "Forbidden" : "Unauthorized",
                        authenticated ? "Order query not permitted" : "Authentication required or invalid", "/orders", false);
            }
            assertHead(request("HEAD", "/orders/not-a-number", token, null), !authenticated ? 401 : buyer ? 400 : 403);
            assertError(raw("POST", "/admin/products", token, "{"), !authenticated ? 401 : admin ? 400 : 403,
                    !authenticated ? "Unauthorized" : admin ? "Bad Request" : "Forbidden",
                    !authenticated ? "Authentication required or invalid"
                            : admin ? "Invalid product maintenance data" : "Product maintenance not permitted",
                    "/admin/products", true);
            var categories = request("GET", "/admin/categories", token, null);
            if (admin) {
                assertThat(categories.statusCode()).isEqualTo(200);
                assertJson(categories);
                assertThat(jsonBody(categories).isArray() && jsonBody(categories).size() == 1).isTrue();
            } else {
                assertError(categories, authenticated ? 403 : 401, authenticated ? "Forbidden" : "Unauthorized",
                        authenticated ? "Product maintenance not permitted" : "Authentication required or invalid",
                        "/admin/categories", true);
            }
            String paymentPath = "/admin/orders/not-a-number/payments";
            assertError(raw("POST", paymentPath, token, "{"), !authenticated ? 401 : admin ? 400 : 403,
                    !authenticated ? "Unauthorized" : admin ? "Bad Request" : "Forbidden",
                    !authenticated ? "Authentication required or invalid" : admin ? "Invalid payment data" : "Payment registration not permitted",
                    paymentPath, false);
            assertUnchanged(before, database.snapshot());
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RegistrationOutcome.class)
    void currentDatabaseRolesRemainAuthoritativeForAlreadyIssuedCredentials(RegistrationOutcome outcome)
            throws SQLException {
        prepare();
        var changing = fixture.createAccount("Changing.roles", LegacyCredential.LONG, Set.of(UserRole.ADMIN));
        register(outcome);
        String token = login(changing);
        var original = database.snapshot();
        try {
            setOwnedRoles(changing.id(), Set.of());
            var roleless = database.snapshot();
            assertError(request("GET", "/admin/categories", token, null), 403, "Forbidden",
                    "Product maintenance not permitted", "/admin/categories", true);
            assertError(request("GET", "/orders", token, null), 403, "Forbidden",
                    "Order query not permitted", "/orders", false);
            assertError(raw("POST", "/orders", token, "{"), 403, "Forbidden",
                    "Order registration not permitted", "/orders", false);
            assertError(raw("POST", "/admin/orders/not-a-number/payments", token, "{"), 403, "Forbidden",
                    "Payment registration not permitted", "/admin/orders/not-a-number/payments", false);
            assertUnchanged(roleless, database.snapshot());
            setOwnedRoles(changing.id(), Set.of(UserRole.CLIENT));
            var clientOnly = database.snapshot();
            assertOwnPage(request("GET", "/orders", token, null), List.of(), 0, 12, 0);
            assertError(raw("POST", "/orders", token, "{"), 400, "Bad Request", "Invalid order data", "/orders", false);
            assertError(request("GET", "/admin/categories", token, null), 403, "Forbidden",
                    "Product maintenance not permitted", "/admin/categories", true);
            assertUnchanged(clientOnly, database.snapshot());
        } finally { if (safeToClean) { setOwnedRoles(changing.id(), changing.roles()); } }
        assertUnchanged(original, database.snapshot());
    }

    @Test
    void newClientStartsWithOwnEmptyQueriesAndCannotReadOrPayAnotherBuyersOrders() throws SQLException {
        var scenario = prepare();
        long id = register(RegistrationOutcome.ACCEPTED);
        String token = login(id, NEW_NAME, NEW_PASSWORD);
        var before = database.snapshot();
        assertOwnPage(request("GET", "/orders?accountId=" + scenario.access().client().id(), token, null), List.of(), 0, 12, 0);
        assertNotOwned(token, scenario.waiting().id());
        assertError(raw("POST", "/admin/orders/" + scenario.waiting().id() + "/payments", token, "{"), 403, "Forbidden",
                "Payment registration not permitted", "/admin/orders/" + scenario.waiting().id() + "/payments", false);
        assertError(request("GET", "/admin/categories", token, null), 403, "Forbidden",
                "Product maintenance not permitted", "/admin/categories", true);
        assertUnchanged(before, database.snapshot());
        long orderId = createOwnOrder(scenario, id, token);
        assertOwnPage(request("GET", "/orders", token, null),
                List.of(new Summary(orderId, clock.instant(), "WAITING_PAYMENT", "234.00")), 0, 12, 1);
        assertAccountsUnchanged(before, database.snapshot());
    }

    private Scenario prepare() {
        if (!safeToClean) {
            fixture = null;
            throw new AssertionError("Previous HTTP completion is unproved; fixture setup is withheld");
        }
        clock.setInstant(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        return fixture.prepareScenario();
    }

    private long register(RegistrationOutcome outcome) {
        String name = outcome == RegistrationOutcome.CONFLICT ? "Reserved.protected" : NEW_NAME;
        if (outcome == RegistrationOutcome.CONFLICT) {
            fixture.createAccount(name, LegacyCredential.SHORT, Set.of(UserRole.ADMIN));
        }
        var input = mapper.createObjectNode().put("name", name).put("fullName", "Protected Person")
                .put("email", "protected@example.test").put("phone", outcome == RegistrationOutcome.INVALID ? "123" : "+5511999999999")
                .put("birthDate", "2000-02-29").put("password", NEW_PASSWORD);
        var before = database.snapshot();
        var response = mutate("POST", "/customers", null, input);
        if (outcome != RegistrationOutcome.ACCEPTED) {
            boolean conflict = outcome == RegistrationOutcome.CONFLICT;
            assertError(response, conflict ? 409 : 400, conflict ? "Conflict" : "Bad Request",
                    conflict ? "Customer registration not available" : "Invalid customer registration data", "/customers", false);
            assertUnchanged(before, database.snapshot());
            return 0;
        }
        assertThat(response.statusCode()).isEqualTo(201);
        assertJson(response);
        var body = jsonBody(response);
        assertFields(body, Set.of("id", "name"));
        assertThat(body.path("id").isIntegralNumber() && body.path("id").longValue() > 0
                && NEW_NAME.equals(body.path("name").textValue())).isTrue();
        assertThat(response.headers().allValues("Location")).isEmpty();
        long id = body.path("id").longValue();
        var after = database.snapshot();
        assertOnlyTablesMayDiffer(before, after, Set.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE"));
        assertAddedRows(before, after, "TB_USER_ACCOUNT", "ID", id, 1);
        assertAddedRows(before, after, "TB_USER_ACCOUNT_ROLE", "USER_ACCOUNT_ID", id, 1);
        assertThat(after.tables().get("TB_USER_ACCOUNT_ROLE").rows().stream().anyMatch(row ->
                id(row, "USER_ACCOUNT_ID") == id && "CLIENT".equals(row.values().get("ROLE").value()))).isTrue();
        return id;
    }

    private long createOwnOrder(Scenario scenario, long buyerId, String token) throws SQLException {
        var before = database.snapshot();
        Instant createdAt = clock.instant();
        var response = mutate("POST", "/orders", token, orderInput(scenario));
        assertThat(response.statusCode()).isEqualTo(201);
        assertJson(response);
        assertThat(response.headers().allValues("Location")).isEmpty();
        var body = jsonBody(response);
        long id = body.path("id").longValue();
        assertThat(id).isPositive();
        var expected = mapper.createObjectNode().put("id", id).put("accountId", buyerId).put("createdAt", wire(createdAt))
                .put("status", "WAITING_PAYMENT").put("total", "234.00");
        expected.putArray("items").add(orderLine(scenario.products().get(1).id(), 3, "12.00", "36.00"))
                .add(orderLine(scenario.products().get(0).id(), 2, "99.00", "198.00"));
        assertBody(response, expected);
        var after = database.snapshot();
        assertOnlyTablesMayDiffer(before, after, Set.of("TB_ORDER", "TB_ORDER_ITEM"));
        assertAddedRows(before, after, "TB_ORDER", "ID", id, 1);
        assertAddedRows(before, after, "TB_ORDER_ITEM", "ORDER_ID", id, 2);
        try (var connection = openIndependentConnection()) {
            try (var statement = connection.prepareStatement("select buyer_id, created_at, status from tb_order where id = ?")) {
                statement.setLong(1, id);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next() && rows.getLong(1) == buyerId && createdAt.equals(rows.getTimestamp(2).toInstant())
                            && "WAITING_PAYMENT".equals(rows.getString(3)) && !rows.next()).isTrue();
                }
            }
            try (var statement = connection.prepareStatement(
                    "select product_id, quantity, unit_price from tb_order_item where order_id = ? order by position, id")) {
                statement.setLong(1, id);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next() && rows.getLong(1) == scenario.products().get(1).id() && rows.getInt(2) == 3
                            && new BigDecimal("12.00").compareTo(rows.getBigDecimal(3)) == 0).isTrue();
                    assertThat(rows.next() && rows.getLong(1) == scenario.products().get(0).id() && rows.getInt(2) == 2
                            && new BigDecimal("99.00").compareTo(rows.getBigDecimal(3)) == 0 && !rows.next()).isTrue();
                }
            }
            try (var statement = connection.prepareStatement("select count(*) from tb_payment where order_id = ?")) {
                statement.setLong(1, id);
                try (var rows = statement.executeQuery()) { assertThat(rows.next() && rows.getInt(1) == 0).isTrue(); }
            }
        }
        return id;
    }

    private ObjectNode orderInput(Scenario scenario) {
        var body = mapper.createObjectNode();
        body.putArray("items").add(mapper.createObjectNode().put("productId", scenario.products().get(1).id()).put("quantity", 3))
                .add(mapper.createObjectNode().put("productId", scenario.products().get(0).id()).put("quantity", 2));
        return body;
    }

    private ObjectNode productInput(String name, String price, long category) {
        var body = mapper.createObjectNode().put("name", name).put("description", "Protected regression product")
                .put("image", MAINTENANCE_IMAGE).put("price", price);
        body.putArray("categoryIds").add(category);
        return body;
    }

    private ObjectNode orderLine(long productId, int quantity, String price, String subtotal) {
        return mapper.createObjectNode().put("productId", productId).put("quantity", quantity)
                .put("unitPrice", price).put("subtotal", subtotal);
    }

    private void assertHistoricalWaiting(Scenario scenario, String token, String status, String firstProductName) {
        assertOwnDetail(request("GET", "/orders/" + scenario.waiting().id(), token, null), scenario.waiting(), status, "42.50",
                List.of(new Line(scenario.products().get(0).id(), firstProductName, 3, "7.50", "22.50"),
                        new Line(scenario.products().get(1).id(), "Produto anterior B", 2, "10.00", "20.00")));
    }

    private void assertOwnPage(HttpResponse<byte[]> response, List<Summary> summaries, int page, int size, long total) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertJson(response);
        var expected = mapper.createObjectNode().put("page", page).put("size", size)
                .put("totalElements", total).put("totalPages", total == 0 ? 0 : (total + size - 1) / size);
        var content = expected.putArray("content");
        summaries.forEach(summary -> content.add(mapper.createObjectNode().put("id", summary.id())
                .put("createdAt", wire(summary.createdAt())).put("status", summary.status()).put("total", summary.total())));
        assertBody(response, expected);
    }

    private void assertOwnDetail(HttpResponse<byte[]> response, OrderData order, String status, String total, List<Line> lines) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertJson(response);
        var expected = mapper.createObjectNode().put("id", order.id()).put("createdAt", wire(order.createdAt()))
                .put("status", status).put("total", total);
        var items = expected.putArray("items");
        lines.forEach(line -> items.add(orderLine(line.productId(), line.quantity(), line.unitPrice(), line.subtotal())
                .put("productName", line.productName())));
        assertBody(response, expected);
    }

    private void assertAdminProduct(HttpResponse<byte[]> response, long id, String name, String price, long category) {
        assertJson(response);
        var expected = productInput(name, price, category);
        expected.remove("categoryIds");
        expected.put("id", id);
        expected.putArray("categories").add(mapper.createObjectNode().put("id", category).put("name", "Catalogo anterior009"));
        assertBody(response, expected);
    }

    private void assertNotOwned(String token, long orderId) {
        String path = "/orders/" + orderId;
        assertError(request("GET", path, token, null), 404, "Not Found", "Order not found", path, false);
        assertHead(request("HEAD", path, token, null), 404);
        assertError(request("GET", "/orders/" + Long.MAX_VALUE, token, null), 404, "Not Found",
                "Order not found", "/orders/" + Long.MAX_VALUE, false);
    }

    private void assertHead(HttpResponse<byte[]> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.body().length).as("HEAD carries no representation even on rejection").isZero();
        if (status == 401) { assertBearerChallenge(response); }
    }

    private void assertError(HttpResponse<byte[]> response, int status, String error, String message, String path, boolean admin) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertJson(response);
        var expected = mapper.createObjectNode().put("status", status).put("error", error).put("message", message)
                .put("path", expectedContextPath() + path);
        if (admin) {
            var errors = expected.putArray("errors");
            if (status == 400) {
                errors.add(mapper.createObjectNode().put("field", "body").put("code", "INVALID_BODY")
                        .put("message", "Invalid request body"));
            }
        }
        assertBody(response, expected);
        if (status == 401) { assertBearerChallenge(response); }
    }

    private static void assertBearerChallenge(HttpResponse<byte[]> response) {
        assertThat(response.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Bearer")).isTrue();
    }

    private static void assertJson(HttpResponse<byte[]> response) {
        boolean json;
        try { json = MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""))
                .isCompatibleWith(MediaType.APPLICATION_JSON); }
        catch (IllegalArgumentException invalidHeader) { json = false; }
        assertThat(json).isTrue();
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(response.headers().allValues("Authorization")).isEmpty();
    }

    private static void assertFields(JsonNode body, Set<String> expected) {
        var fields = new LinkedHashSet<String>();
        body.fieldNames().forEachRemaining(fields::add);
        assertThat(body.isObject() && fields.equals(expected)).isTrue();
    }

    private void assertBody(HttpResponse<byte[]> response, JsonNode expected) {
        // Compare parsed wire trees on both sides: an ID's Java long/int storage is not a JSON type.
        // Fields, array order, numeric values and monetary strings still have to match exactly.
        try {
            assertThat(mapper.readTree(jsonBytes(expected)).equals(jsonBody(response)))
                    .as("exact response fields and values; no financial data, credential or cause in diagnostics").isTrue();
        } catch (IOException failure) {
            throw new AssertionError("Protected-regression expected JSON could not be parsed");
        }
    }

    private String login(Account account) { return login(account.id(), account.name(), account.password()); }

    private String login(long id, String name, String password) {
        safeToClean = false;
        String token = realHttpLogin(id, name, password);
        safeToClean = true;
        // Login timestamps use the real wall clock; protected converters use this context's Clock.
        // Align after issuance, retaining millisecond precision for controlled business timestamps.
        clock.setInstant(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        return token;
    }

    private HttpResponse<byte[]> request(String method, String path, String token, ObjectNode body) {
        var builder = jsonRequest(path);
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
        return exchange(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(jsonBytes(body))).build());
    }

    private HttpResponse<byte[]> raw(String method, String path, String token, String body) {
        var builder = jsonRequest(path);
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
        return exchange(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private HttpResponse<byte[]> mutate(String method, String path, String token, ObjectNode body) {
        var before = database.snapshot();
        try { return request(method, path, token, body); }
        finally {
            if (safeToClean) {
                var after = database.snapshot();
                remember(before, after, "TB_USER_ACCOUNT", newAccounts);
                remember(before, after, "TB_ORDER", newOrders);
                remember(before, after, "TB_PRODUCT", newProducts);
            }
        }
    }

    private HttpResponse<byte[]> exchange(HttpRequest request) {
        safeToClean = false;
        var result = send(request);
        safeToClean = true;
        return result;
    }

    private static void remember(Snapshot before, Snapshot after, String table, Set<Long> owned) {
        var existing = before.tables().get(table).rows().stream().map(row -> id(row, "ID")).collect(Collectors.toSet());
        after.tables().get(table).rows().stream().map(row -> id(row, "ID"))
                .filter(id -> !existing.contains(id)).forEach(owned::add);
    }

    private static void assertAccountsUnchanged(Snapshot before, Snapshot after) {
        for (String table : List.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE")) {
            assertThat(before.tables().get(table).equals(after.tables().get(table)))
                    .as("legacy/new identities, exact hashes, roles and nullable registration fields stay unchanged").isTrue();
        }
    }

    private static void assertOnlyTablesMayDiffer(Snapshot before, Snapshot after, Set<String> allowed) {
        assertThat(before.tables().keySet().equals(after.tables().keySet())).isTrue();
        before.tables().forEach((table, previous) -> {
            assertThat(previous.columns().equals(after.tables().get(table).columns())).isTrue();
            if (!allowed.contains(table)) {
                assertThat(previous.equals(after.tables().get(table))).as("unchanged table %s", table).isTrue();
            }
        });
    }

    private static void assertAddedRows(Snapshot before, Snapshot after, String table, String key, long id, int added) {
        var previous = before.tables().get(table);
        var current = after.tables().get(table);
        assertThat(current.rows().size()).isEqualTo(previous.rows().size() + added);
        assertThat(current.rows().stream().filter(row -> id(row, key) != id).toList().equals(previous.rows()))
                .as("all previous rows preserved in %s", table).isTrue();
    }

    private void assertPaymentStored(long id, Instant occurredAt, long buyer) throws SQLException {
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select o.buyer_id, o.status, p.occurred_at from tb_order o join tb_payment p on p.order_id=o.id where o.id=?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next() && rows.getLong(1) == buyer && "PAID".equals(rows.getString(2))
                        && occurredAt.equals(rows.getTimestamp(3).toInstant()) && !rows.next())
                        .as("original owner, PAID and the single accepted instant are independently visible").isTrue();
            }
        }
    }

    private static void assertOnlyPaymentChanged(Snapshot before, Snapshot after, long id) {
        assertOnlyTablesMayDiffer(before, after, Set.of("TB_ORDER", "TB_PAYMENT"));
        var previousOrders = before.tables().get("TB_ORDER").rows();
        var currentOrders = after.tables().get("TB_ORDER").rows();
        assertThat(previousOrders.size()).isEqualTo(currentOrders.size());
        for (int index = 0; index < previousOrders.size(); index++) {
            var previous = previousOrders.get(index);
            var current = currentOrders.get(index);
            if (id(previous, "ID") != id) { assertThat(previous.equals(current)).isTrue(); }
            else {
                assertThat("PAID".equals(current.values().get("STATUS").value())).isTrue();
                Map<String, ?> previousFields = previous.values().entrySet().stream().filter(entry -> !entry.getKey().equals("STATUS"))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                Map<String, ?> currentFields = current.values().entrySet().stream().filter(entry -> !entry.getKey().equals("STATUS"))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                assertThat(previousFields.equals(currentFields)).as("settlement never changes buyer, date or other root fields").isTrue();
            }
        }
        assertAddedRows(before, after, "TB_PAYMENT", "ORDER_ID", id, 1);
    }

    private void setOwnedRoles(long id, Set<UserRole> roles) throws SQLException {
        assertNoCallerTransaction();
        try (var connection = openIndependentConnection()) {
            connection.setAutoCommit(false);
            try (var delete = connection.prepareStatement("delete from tb_user_account_role where user_account_id=?");
                 var insert = connection.prepareStatement("insert into tb_user_account_role(user_account_id,role) values (?,?)")) {
                delete.setLong(1, id);
                delete.executeUpdate();
                for (var role : roles) {
                    insert.setLong(1, id);
                    insert.setString(2, role.name());
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException | Error failure) {
                connection.rollback();
                throw new AssertionError("Controlled owned-account role setup failed");
            }
        }
    }

    @AfterEach
    void cleanOwnedOrdersProductsAndAccountsOnlyAfterCompletedHttpResponses() throws SQLException {
        if (!safeToClean) {
            fixture = null;
            throw new AssertionError("HTTP completion is unproved; fixture cleanup is withheld");
        }
        if (newOrders.isEmpty() && newProducts.isEmpty() && newAccounts.isEmpty()) { return; }
        try (var connection = openIndependentConnection()) {
            connection.setAutoCommit(false);
            try {
                for (long id : newOrders) {
                    delete(connection, "delete from tb_payment where order_id=?", id);
                    delete(connection, "delete from tb_order_item where order_id=?", id);
                    delete(connection, "delete from tb_order where id=?", id);
                }
                for (long id : newProducts) {
                    delete(connection, "delete from tb_product_category where product_id=?", id);
                    delete(connection, "delete from tb_product where id=?", id);
                }
                for (long id : newAccounts) {
                    delete(connection, "delete from tb_user_account_role where user_account_id=?", id);
                    delete(connection, "delete from tb_user_account where id=?", id);
                }
                connection.commit();
                newOrders.clear(); newProducts.clear(); newAccounts.clear();
            } catch (SQLException | RuntimeException | Error failure) {
                connection.rollback();
                throw new AssertionError("Cleanup of protected-regression-owned rows failed");
            }
        }
    }

    private static void delete(java.sql.Connection connection, String sql, long id) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) { statement.setLong(1, id); statement.executeUpdate(); }
    }

    private static Summary summary(OrderData order, String total) {
        return new Summary(order.id(), order.createdAt(), order.status(), total);
    }
    private static String wire(Instant instant) { return new DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(instant); }
    private static long id(Row row, String column) { return ((Number) row.values().get(column).value()).longValue(); }
    enum RegistrationOutcome { ACCEPTED, INVALID, CONFLICT }
    enum Actor { ABSENT, INVALID, CLIENT, ADMIN, BOTH, NONE }
    private record Summary(long id, Instant createdAt, String status, String total) { }
    private record Line(long productId, String productName, int quantity, String unitPrice, String subtotal) { }
}
