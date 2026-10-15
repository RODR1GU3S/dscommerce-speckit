package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ItemRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.services.exceptions.ProductDeletionIntegrityException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** T046 / US3.7: independent historical money oracle; no product update or order getter as a substitute. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
class AdminProductHistoryIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;
    private String adminToken;
    private String buyerToken;
    private long buyerId;
    private ProductData target;
    private ProductData other;
    private long categoryId;
    private final Set<Long> ownedOrders = new HashSet<>();

    @BeforeEach
    void prepareCommittedOwnedProductsAndTwoRealLogins() {
        noCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        var admin = fixture.createAccount("T046-admin-" + UUID.randomUUID(), "Disposable-T046-admin", Set.of(UserRole.ADMIN));
        var buyer = fixture.prepareBuyer();
        buyerId = buyer.id();
        assertThat(database.roles(admin.id())).containsExactly(UserRole.ADMIN);
        assertThat(database.roles(buyerId)).containsExactly(UserRole.CLIENT);
        adminToken = fixture.login(admin);
        buyerToken = fixture.login(buyer);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        categoryId = fixture.createCategory("Categoria historico").id();
        target = fixture.createProduct("Produto historico", "Descricao completa do historico",
                "https://example.invalid/history.jpg", new BigDecimal("10.00"));
        other = fixture.createProduct("Outro produto", "Descricao completa independente",
                "https://example.invalid/other-history.jpg", new BigDecimal("3.25"));
        fixture.associate(target.id(), categoryId);
        fixture.associate(other.id(), categoryId);
        var prepared = database.snapshot();
        assertThat(prepared.products()).containsExactly(row(target), row(other));
        assertThat(prepared.categories()).containsExactly(new AdminProductDatabaseProbe.CategoryRow(categoryId, "Categoria historico"));
        assertThat(prepared.associations()).containsExactlyInAnyOrder(
                new AdminProductTestFixture.ProductCategory(target.id(), categoryId),
                new AdminProductTestFixture.ProductCategory(other.id(), categoryId));
        assertThat(prepared.orders()).isEmpty();
        assertThat(prepared.items()).isEmpty();
        assertThat(database.accountIds()).hasSize(2);
        ownedOrders.clear();
    }

    @ParameterizedTest(name = "independent order oracle context={0}, multiple items={1}")
    @CsvSource({"ROOT,false", "/api,false", "ROOT,true", "/api,true"})
    void existingOrderRegistrationHasCommittedExactHistoricalFieldsAndIndependentTotals(String external, boolean multiple) throws Exception {
        String prefix = prefix(external);
        // Deliberately reverse product-ID order in the multi-item control to check request positions.
        var lines = multiple ? List.of(new ExpectedLine(other.id(), 3, new BigDecimal("3.25")), oldLine()) : List.of(oldLine());
        var confirmation = registerOrder(prefix, lines, multiple ? "29.75" : "20.00", "control");
        assertHistoricalOrder(confirmation.snapshot(), confirmation.id(), lines, multiple ? "29.75" : "20.00", "control-reread");
        assertThat(database.snapshot()).isEqualTo(confirmation.snapshot());
        assertCatalogPrice(prefix, "10.00");
        assertThat(database.snapshot()).isEqualTo(confirmation.snapshot());
    }

    @ParameterizedTest(name = "historical price across PUT context={0}")
    @ValueSource(strings = {"ROOT", "/api"})
    void catalogReplacementKeepsOldPurchaseAtTwentyAndNextPurchaseUsesTwentyFour(String external) throws Exception {
        String prefix = prefix(external);
        var original = registerOrder(prefix, List.of(oldLine()), "20.00", "before-put");
        assertCatalogPrice(prefix, "10.00");
        var body = mapper.createObjectNode().put("name", target.name()).put("description", target.description())
                .put("price", "12.00").put("image", target.image());
        body.putArray("categoryIds").add(categoryId);
        var before = database.snapshot();
        assertThat(before).isEqualTo(original.snapshot());
        var replacement = observe(put(prefix + "/admin/products/" + target.id())
                .contextPath(prefix).servletPath("/admin/products/" + target.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)), "replace-price", 200);
        // These historical assertions run before the initial 405 fails the expected success status.
        assertThat(replacement.snapshot().orders()).isEqualTo(before.orders());
        assertThat(replacement.snapshot().items()).isEqualTo(before.items());
        assertHistoricalOrder(replacement.snapshot(), original.id(), List.of(oldLine()), "20.00", "after-put-return");
        assertThat(replacement.snapshot().categories()).isEqualTo(before.categories());
        assertThat(replacement.snapshot().associations()).isEqualTo(before.associations());
        assertThat(replacement.snapshot().products().stream().filter(product -> product.id() != target.id()).toList())
                .isEqualTo(before.products().stream().filter(product -> product.id() != target.id()).toList());
        assertThat(replacement.result().getResponse().getStatus()).as("catalog price replacement HTTP contract").isEqualTo(200);
        var detail = json(replacement.result());
        assertFields(detail, "id", "name", "description", "image", "price", "categories");
        assertThat(detail.path("id").longValue()).isEqualTo(target.id());
        assertThat(detail.path("price").isTextual()).isTrue();
        assertThat(detail.path("price").asText()).isEqualTo("12.00");
        var expectedProduct = new ProductRow(target.id(), target.name(), target.description(), target.image(), new BigDecimal("12.00"));
        var expectedProducts = before.products().stream().map(product -> product.id() == target.id() ? expectedProduct : product).toList();
        assertThat(replacement.snapshot()).isEqualTo(new Snapshot(expectedProducts, before.categories(), before.associations(), before.orders(), before.items()));
        assertCatalogPrice(prefix, "12.00");
        // Never run this against an unchanged catalog to manufacture a misleading 24.00 proof.
        var subsequent = registerOrder(prefix, List.of(new ExpectedLine(target.id(), 2, new BigDecimal("12.00"))), "24.00", "after-put");
        assertThat(subsequent.id()).isNotEqualTo(original.id());
        assertThat(subsequent.snapshot().orders().stream().filter(order -> order.id() == original.id()).toList())
                .isEqualTo(original.snapshot().orders());
        assertThat(subsequent.snapshot().items().stream().filter(item -> item.orderId() == original.id()).toList())
                .isEqualTo(original.snapshot().items());
        assertHistoricalOrder(subsequent.snapshot(), original.id(), List.of(oldLine()), "20.00", "old-after-new-order");
        assertHistoricalOrder(subsequent.snapshot(), subsequent.id(), List.of(new ExpectedLine(target.id(), 2, new BigDecimal("12.00"))),
                "24.00", "new-after-price-change");
        assertThat(database.snapshot()).isEqualTo(subsequent.snapshot());
    }

    @ParameterizedTest(name = "history after rejected DELETE context={0}, multiple items={1}")
    @CsvSource({"ROOT,false", "/api,false", "ROOT,true", "/api,true"})
    void rejectedDeletionAfterPriceChangePreservesHistoryAndNextPurchase(String external, boolean multiple) throws Exception {
        String prefix = prefix(external);
        var originalLines = multiple
                ? List.of(new ExpectedLine(other.id(), 3, new BigDecimal("3.25")), oldLine()) : List.of(oldLine());
        String originalTotal = multiple ? "29.75" : "20.00";
        var original = registerOrder(prefix, originalLines, originalTotal, "t063-before-put");
        assertThat(database.snapshot()).isEqualTo(original.snapshot());

        var body = mapper.createObjectNode().put("name", target.name()).put("description", target.description())
                .put("price", "12.00").put("image", target.image());
        body.putArray("categoryIds").add(categoryId);
        String route = "/admin/products/" + target.id();
        var replacement = observe(put(prefix + route).contextPath(prefix).servletPath(route)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)), "t063-put", 200);
        assertThat(replacement.result().getResponse().getStatus()).isEqualTo(200);
        var expectedProduct = new ProductRow(target.id(), target.name(), target.description(), target.image(), new BigDecimal("12.00"));
        var expectedProducts = original.snapshot().products().stream()
                .map(product -> product.id() == target.id() ? expectedProduct : product).toList();
        var expectedAfterPut = new Snapshot(expectedProducts, original.snapshot().categories(), original.snapshot().associations(),
                original.snapshot().orders(), original.snapshot().items());
        assertThat(replacement.snapshot()).isEqualTo(expectedAfterPut);
        assertHistoricalOrder(replacement.snapshot(), original.id(), originalLines, originalTotal, "t063-after-put");
        assertCatalogPrice(prefix, "12.00");
        var beforeDelete = database.snapshot();
        assertThat(beforeDelete).isEqualTo(expectedAfterPut);

        var rejected = observe(delete(prefix + route).contextPath(prefix).servletPath(route)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken), "t063-delete", 409);
        assertThat(rejected.result().getResponse().getStatus()).isEqualTo(409);
        assertThat(rejected.result().getResolvedException()).isInstanceOf(ProductDeletionIntegrityException.class);
        var error = json(rejected.result());
        assertFields(error, "status", "error", "message", "path", "errors");
        assertThat(error.path("status").intValue()).isEqualTo(409);
        assertThat(error.path("error").asText()).isEqualTo("Conflict");
        assertThat(error.path("message").asText()).isEqualTo("Product deletion violates referential integrity");
        assertThat(error.path("path").asText()).isEqualTo(prefix + route);
        assertThat(error.path("errors").isArray()).isTrue();
        assertThat(error.path("errors")).isEmpty();
        assertThat(rejected.snapshot()).isEqualTo(beforeDelete);
        assertHistoricalOrder(rejected.snapshot(), original.id(), originalLines, originalTotal, "t063-after-delete");
        assertCatalogPrice(prefix, "12.00");
        assertThat(database.snapshot()).isEqualTo(beforeDelete);

        var currentLine = new ExpectedLine(target.id(), 2, new BigDecimal("12.00"));
        // The target moves from position 1 to position 0 in the multi-item purchase; old positions must not change.
        var subsequentLines = multiple ? List.of(currentLine, new ExpectedLine(other.id(), 3, new BigDecimal("3.25")))
                : List.of(currentLine);
        String subsequentTotal = multiple ? "33.75" : "24.00";
        var subsequent = registerOrder(prefix, subsequentLines, subsequentTotal, "t063-after-rejected-delete");
        assertThat(subsequent.id()).isNotEqualTo(original.id());
        var reread = database.snapshot();
        assertThat(reread).isEqualTo(subsequent.snapshot());
        assertThat(reread.orders()).hasSize(2);
        assertThat(reread.items()).hasSize(originalLines.size() + subsequentLines.size());
        assertThat(reread.orders().stream().filter(order -> order.id() == original.id()).toList())
                .isEqualTo(original.snapshot().orders());
        assertThat(reread.items().stream().filter(item -> item.orderId() == original.id()).toList())
                .isEqualTo(original.snapshot().items());
        assertThat(reread.items().stream().filter(item -> item.orderId() == subsequent.id()).map(ItemRow::id).toList())
                .doesNotContainAnyElementsOf(original.snapshot().items().stream().map(ItemRow::id).toList());
        assertThat(reread.products()).isEqualTo(beforeDelete.products());
        assertThat(reread.categories()).isEqualTo(beforeDelete.categories());
        assertThat(reread.associations()).isEqualTo(beforeDelete.associations());
        assertHistoricalOrder(reread, original.id(), originalLines, originalTotal, "t063-old-after-new-order");
        assertHistoricalOrder(reread, subsequent.id(), subsequentLines, subsequentTotal, "t063-new-after-delete");
        for (var order : reread.orders()) {
            System.out.printf("T063_ORDER orderId=%d buyerId=%d createdAt=%s status=%s%n",
                    order.id(), order.buyerId(), order.createdAt(), order.status());
        }
        for (var item : reread.items()) {
            System.out.printf("T063_ITEM orderId=%d itemId=%d productId=%d position=%d quantity=%d unitPrice=%s subtotal=%s%n",
                    item.orderId(), item.id(), item.productId(), item.position(), item.quantity(), item.unitPrice().toPlainString(),
                    item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())).toPlainString());
        }
        System.out.printf("T063_HISTORY context=%s multiple=%s oldOrderId=%d newOrderId=%d oldTotal=%s newTotal=%s independent=true%n",
                external, multiple, original.id(), subsequent.id(), originalTotal, subsequentTotal);
    }

    private Confirmation registerOrder(String prefix, List<ExpectedLine> lines, String expectedTotal, String phase) throws Exception {
        var body = mapper.createObjectNode();
        var items = body.putArray("items");
        lines.forEach(line -> items.addObject().put("productId", line.productId()).put("quantity", line.quantity()));
        var before = database.snapshot();
        var observed = observe(post(prefix + "/orders").contextPath(prefix).servletPath("/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)), "order-" + phase, 201);
        assertThat(observed.result().getResponse().getStatus()).as("real order registration control").isEqualTo(201);
        var response = json(observed.result());
        assertFields(response, "id", "accountId", "createdAt", "status", "items", "total");
        assertThat(response.path("id").isIntegralNumber()).isTrue();
        long id = response.path("id").longValue();
        assertThat(id).isPositive();
        assertThat(before.orders()).noneMatch(order -> order.id() == id);
        assertThat(observed.snapshot().orders()).hasSize(before.orders().size() + 1);
        assertThat(observed.snapshot().items()).hasSize(before.items().size() + lines.size());
        assertThat(observed.snapshot().orders().stream().filter(order -> order.id() != id).toList()).isEqualTo(before.orders());
        assertThat(observed.snapshot().items().stream().filter(item -> item.orderId() != id).toList()).isEqualTo(before.items());
        assertThat(observed.snapshot().products()).isEqualTo(before.products());
        assertThat(observed.snapshot().categories()).isEqualTo(before.categories());
        assertThat(observed.snapshot().associations()).isEqualTo(before.associations());
        assertHistoricalOrder(observed.snapshot(), id, lines, expectedTotal, phase);
        var stored = observed.snapshot().orders().stream().filter(order -> order.id() == id).findFirst().orElseThrow();
        assertThat(response.path("accountId").longValue()).isEqualTo(buyerId);
        assertThat(Instant.parse(response.path("createdAt").asText())).isEqualTo(stored.createdAt());
        assertThat(response.path("status").asText()).isEqualTo("WAITING_PAYMENT");
        assertThat(response.path("total").isTextual()).isTrue();
        assertThat(response.path("total").asText()).isEqualTo(expectedTotal);
        assertThat(response.path("items").isArray()).isTrue();
        assertThat(response.path("items").size()).isEqualTo(lines.size());
        for (int position = 0; position < lines.size(); position++) {
            var item = response.path("items").get(position);
            var expected = lines.get(position);
            assertFields(item, "productId", "quantity", "unitPrice", "subtotal");
            assertThat(item.path("productId").longValue()).isEqualTo(expected.productId());
            assertThat(item.path("quantity").intValue()).isEqualTo(expected.quantity());
            assertThat(item.path("unitPrice").isTextual()).isTrue();
            assertThat(item.path("unitPrice").asText()).isEqualTo(expected.unitPrice().toPlainString());
            assertThat(item.path("subtotal").isTextual()).isTrue();
            assertThat(item.path("subtotal").asText()).isEqualTo(expected.unitPrice().multiply(BigDecimal.valueOf(expected.quantity())).toPlainString());
        }
        return new Confirmation(id, observed.snapshot());
    }

    private void assertHistoricalOrder(Snapshot snapshot, long orderId, List<ExpectedLine> expected, String expectedTotal, String phase) {
        var orders = snapshot.orders().stream().filter(order -> order.id() == orderId).toList();
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).buyerId()).isEqualTo(buyerId);
        assertThat(orders.get(0).status()).isEqualTo("WAITING_PAYMENT");
        assertThat(orders.get(0).createdAt()).isNotNull();
        var items = snapshot.items().stream().filter(item -> item.orderId() == orderId).toList();
        assertThat(items).hasSize(expected.size());
        assertThat(items.stream().map(ItemRow::id)).doesNotHaveDuplicates().allMatch(id -> id > 0);
        BigDecimal total = new BigDecimal("0.00");
        for (int position = 0; position < expected.size(); position++) {
            var item = items.get(position);
            var line = expected.get(position);
            assertThat(item.orderId()).isEqualTo(orderId);
            assertThat(item.position()).isEqualTo(position);
            assertThat(item.productId()).isEqualTo(line.productId());
            assertThat(item.quantity()).isEqualTo(line.quantity());
            assertThat(item.unitPrice()).isEqualTo(line.unitPrice());
            // Only persisted JDBC fields participate; subtotal/total are not database columns.
            var subtotal = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
            assertThat(subtotal).isEqualTo(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            total = total.add(subtotal);
        }
        assertThat(total).isEqualTo(new BigDecimal(expectedTotal));
        System.out.printf("T046_HISTORY phase=%s orderId=%d lines=%d independent=true expectedTotal=%s actualTotal=%s%n",
                phase, orderId, items.size(), expectedTotal, total.toPlainString());
    }

    private void assertCatalogPrice(String prefix, String price) throws Exception {
        var before = database.snapshot();
        var admin = observe(get(prefix + "/admin/products/" + target.id()).contextPath(prefix)
                .servletPath("/admin/products/" + target.id()).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken), "catalog-admin", 200);
        assertThat(admin.result().getResponse().getStatus()).isEqualTo(200);
        assertThat(json(admin.result()).path("price").isTextual()).isTrue();
        assertThat(json(admin.result()).path("price").asText()).isEqualTo(price);
        var catalog = observe(get(prefix + "/products").contextPath(prefix).servletPath("/products"), "catalog-public", 200);
        assertThat(catalog.result().getResponse().getStatus()).isEqualTo(200);
        var matches = new ArrayList<JsonNode>();
        for (var item : json(catalog.result()).path("content")) if (item.path("id").longValue() == target.id()) matches.add(item);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).path("price").isNumber()).isTrue();
        assertThat(matches.get(0).path("price").decimalValue()).isEqualByComparingTo(new BigDecimal(price));
        assertThat(catalog.snapshot()).isEqualTo(before);
        assertThat(admin.snapshot()).isEqualTo(before);
    }

    private Observation observe(MockHttpServletRequestBuilder request, String phase, int expectedStatus) throws Exception {
        noCallerTransaction();
        var before = database.snapshot();
        var accounts = database.accountIds();
        MvcResult result;
        Snapshot after;
        try {
            result = mvc.perform(request.accept(MediaType.APPLICATION_JSON)).andReturn();
        } finally {
            noCallerTransaction();
            after = database.snapshot();
            var priorIds = before.orders().stream().map(AdminProductDatabaseProbe.OrderRow::id).collect(Collectors.toSet());
            after.orders().stream().filter(order -> !priorIds.contains(order.id())).forEach(order -> ownedOrders.add(order.id()));
        }
        assertThat(database.accountIds()).isEqualTo(accounts);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        System.out.printf("T046_HTTP phase=%s context=%s method=%s expected=%d actual=%d snapshotEqual=%s ordersDelta=%d itemsDelta=%d cause=%s%n",
                phase, result.getRequest().getContextPath().isEmpty() ? "ROOT" : "/api", result.getRequest().getMethod(),
                expectedStatus, result.getResponse().getStatus(), before.equals(after), after.orders().size() - before.orders().size(),
                after.items().size() - before.items().size(),
                result.getResolvedException() == null ? "none" : result.getResolvedException().getClass().getSimpleName());
        return new Observation(result, after);
    }

    @AfterEach
    void cleanOnlyRequestOwnedOrdersBeforeFixtureProductsAndAccounts() throws Exception {
        noCallerTransaction();
        if (fixture == null) return;
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var items = connection.prepareStatement("delete from tb_order_item where order_id = ?");
                 var orders = connection.prepareStatement("delete from tb_order where id = ?")) {
                for (long id : ownedOrders) {
                    items.setLong(1, id);
                    items.executeUpdate();
                    orders.setLong(1, id);
                    orders.executeUpdate();
                }
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
        fixture.close();
        assertEmptyDatabase();
    }

    private void assertEmptyDatabase() {
        var snapshot = database.snapshot();
        assertThat(snapshot.products()).isEmpty();
        assertThat(snapshot.categories()).isEmpty();
        assertThat(snapshot.associations()).isEmpty();
        assertThat(snapshot.orders()).isEmpty();
        assertThat(snapshot.items()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }
    private JsonNode json(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
    private static void assertFields(JsonNode object, String... expected) {
        var fields = new ArrayList<String>();
        object.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(expected);
    }
    private static void noCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private ExpectedLine oldLine() { return new ExpectedLine(target.id(), 2, new BigDecimal("10.00")); }
    private static String prefix(String external) { return external.equals("ROOT") ? "" : external; }
    private static ProductRow row(ProductData product) {
        return new ProductRow(product.id(), product.name(), product.description(), product.image(), product.price());
    }
    private record ExpectedLine(long productId, int quantity, BigDecimal unitPrice) { }
    private record Confirmation(long id, Snapshot snapshot) { }
    private record Observation(MvcResult result, Snapshot snapshot) { }
}
