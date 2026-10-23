package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductCategory;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.services.exceptions.ProductDeletionIntegrityException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** T055: HTTP deletion contract and independent state; production DELETE remains a later task. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
class AdminProductDeletionIntegrationTest {
    private static final long ABSENT = Long.MAX_VALUE;
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    private AdminProductDatabaseProbe database;
    private PaymentRegistrationDatabaseProbe payments;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;
    private String adminToken;
    private String buyerToken;
    private long buyer;
    private ProductData target;
    private ProductData other;
    private List<AdminProductTestFixture.CategoryData> categories;
    private final Set<Long> ownedOrders = new HashSet<>();
    private boolean extraTable;

    @BeforeEach
    void prepareOwnedCommittedCatalogAndRealAuthentication() throws Exception {
        outsideTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        payments = new PaymentRegistrationDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmpty();
        fixture = new AdminProductTestFixture(context);
        ownedOrders.clear(); extraTable = false;
        var admin = fixture.createAccount("T055 admin", "Disposable-T055-admin", Set.of(UserRole.ADMIN));
        var client = fixture.prepareBuyer(); buyer = client.id();
        assertThat(database.roles(admin.id())).containsExactly(UserRole.ADMIN);
        assertThat(database.roles(buyer)).containsExactly(UserRole.CLIENT);
        adminToken = fixture.login(admin); buyerToken = fixture.login(client);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        categories = List.of(fixture.createCategory("Categoria igual"), fixture.createCategory("Categoria igual"),
                fixture.createCategory("Categoria sem produto"));
        target = fixture.createProduct("Alvo T055", "Descricao completa do alvo",
                "https://example.invalid/t055-target.jpg", new BigDecimal("10.00"));
        other = fixture.createProduct("Outro T055", "Descricao completa independente",
                "https://example.invalid/t055-other.jpg", new BigDecimal("3.25"));
        fixture.associate(target.id(), categories.get(0).id());
        fixture.associate(target.id(), categories.get(1).id());
        fixture.associate(other.id(), categories.get(0).id());
        var prepared = database.snapshot();
        assertThat(prepared.products()).containsExactly(row(target), row(other));
        assertThat(prepared.categories()).containsExactlyElementsOf(categories.stream()
                .map(category -> new AdminProductDatabaseProbe.CategoryRow(category.id(), category.name())).toList());
        assertThat(prepared.associations()).containsExactlyInAnyOrder(new ProductCategory(target.id(), categories.get(0).id()),
                new ProductCategory(target.id(), categories.get(1).id()), new ProductCategory(other.id(), categories.get(0).id()));
        assertThat(prepared.orders()).isEmpty(); assertThat(prepared.items()).isEmpty();
        assertThat(prepared.products()).noneMatch(product -> product.id() == ABSENT);
        // Nonempty history on another product also has to survive deletion of a free target.
        registerOrder(List.of(new Line(other.id(), 3, new BigDecimal("3.25"))), OrderStatus.WAITING_PAYMENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROOT", "/api"})
    void freeProductIsDeletedWithOnlyItsPairsAndIsNoLongerFound(String external) throws Exception {
        String prefix = prefix(external);
        assertReadable(prefix);
        var observed = observe(deleteRequest(prefix, target.id()), "free", 204);
        assertUnrelatedState(observed);
        assertHistory(observed.after());
        assertThat(observed.result().getResponse().getStatus()).as("free product deletion HTTP contract").isEqualTo(204);
        assertThat(observed.result().getResponse().getContentAsByteArray()).isEmpty();
        assertThat(observed.result().getResponse().getContentType()).isNull();
        var expected = new Snapshot(observed.before().products().stream().filter(product -> product.id() != target.id()).toList(),
                observed.before().categories(), Set.of(new ProductCategory(other.id(), categories.get(0).id())),
                observed.before().orders(), observed.before().items());
        assertThat(observed.after()).isEqualTo(expected);
        var missing = observe(adminGet(prefix, target.id()), "read-after-deletion", 404);
        assertThat(missing.before()).isEqualTo(expected); assertThat(missing.after()).isEqualTo(expected);
        assertEnvelope(missing.result(), 404, "Product not found");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROOT", "/api"})
    void absentProductIs404WithoutFalseDeletionOrChangingAnyOtherRow(String external) throws Exception {
        String prefix = prefix(external);
        assertReadable(prefix);
        var before = observe(adminGet(prefix, ABSENT), "missing-before", 404);
        assertEnvelope(before.result(), 404, "Product not found");
        assertThat(before.after()).isEqualTo(before.before());
        var observed = observe(deleteRequest(prefix, ABSENT), "missing", 404);
        assertThat(observed.after()).isEqualTo(observed.before());
        assertHistory(observed.after());
        var reread = observe(adminGet(prefix, ABSENT), "missing-after", 404);
        assertEnvelope(reread.result(), 404, "Product not found");
        assertThat(reread.after()).isEqualTo(observed.before());
        assertEnvelope(observed.result(), 404, "Product not found");
    }

    static Stream<Arguments> referencedProductIdRepresentations() {
        return Stream.of("ROOT", "/api").flatMap(external -> Stream.of("", "0")
                .map(idPrefix -> Arguments.of(external, idPrefix)));
    }

    @ParameterizedTest(name = "P2 context={0}, id prefix='{1}'")
    @MethodSource("referencedProductIdRepresentations")
    void referencedProductIdWithLeadingZeroKeepsConflictAndHistory(String external, String idPrefix) throws Exception {
        String prefix = prefix(external);
        registerOrder(List.of(new Line(other.id(), 3, new BigDecimal("3.25")),
                new Line(target.id(), 2, new BigDecimal("10.00"))), OrderStatus.WAITING_PAYMENT);
        var before = database.snapshot();
        var historicalItem = before.items().stream().filter(item -> item.productId() == target.id()).toList();
        assertThat(historicalItem).hasSize(1);
        assertThat(historicalItem.getFirst().unitPrice().multiply(BigDecimal.valueOf(historicalItem.getFirst().quantity())))
                .isEqualTo(new BigDecimal("20.00"));
        String path = "/admin/products/" + idPrefix + target.id();
        var observed = observe(delete(prefix + path).contextPath(prefix).servletPath(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken), "p2-id-prefix-" + idPrefix, 409);
        assertThat(observed.before()).isEqualTo(before);
        assertThat(observed.after()).isEqualTo(before);
        assertHistory(observed.after());
        assertThat(observed.result().getHandler()).isNotNull();
        assertThat(observed.result().getResolvedException()).isInstanceOf(ProductDeletionIntegrityException.class);
        assertThat(observed.result().getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        var error = json(observed.result());
        fields(error, "status", "error", "message", "path", "errors");
        assertThat(error.path("path").asText()).isEqualTo(prefix + path);
        assertThat(error.path("errors").isArray()).isTrue(); assertThat(error.path("errors")).isEmpty();
        System.out.printf("P2_DELETE_REFERENCE context=%s leadingZero=%s actual=%d envelopeShape=true pathPreserved=true independentSnapshotEqual=true historicalSubtotal=20.00%n",
                external, !idPrefix.isEmpty(), observed.result().getResponse().getStatus());
        assertAll("referenced product deletion must remain a contractual conflict",
                () -> assertThat(observed.result().getResponse().getStatus()).as("deletion conflict HTTP status").isEqualTo(409),
                () -> assertThat(error.path("status").asInt()).isEqualTo(409),
                () -> assertThat(error.path("error").asText()).isEqualTo("Conflict"),
                () -> assertThat(error.path("message").asText()).isEqualTo("Product deletion violates referential integrity"));
    }

    static Stream<Arguments> orderReferences() {
        // Keep every supported state; PAID requires a real settlement after order creation.
        return Stream.of("ROOT", "/api").flatMap(prefix -> Arrays.stream(OrderStatus.values())
                .flatMap(status -> Stream.of(1, 2).map(count -> Arguments.of(prefix, status, count))));
    }

    @ParameterizedTest(name = "context={0}, status={1}, target orders={2}")
    @MethodSource("orderReferences")
    void anyOrderItemPreventsDeletionAndPreservesTheEntireHistory(String external, OrderStatus status, int orders) throws Exception {
        String prefix = prefix(external);
        // Target appears at position 1 of a multi-item order, not just as its first/only line.
        registerOrder(List.of(new Line(other.id(), 3, new BigDecimal("3.25")),
                new Line(target.id(), 2, new BigDecimal("10.00"))), status, prefix);
        if (orders == 2) registerOrder(List.of(new Line(target.id(), 1, new BigDecimal("10.00"))), status, prefix);
        var before = database.snapshot();
        assertThat(before.items().stream().filter(item -> item.productId() == target.id())).hasSize(orders);
        assertThat(before.orders().stream().filter(order -> before.items().stream()
                .anyMatch(item -> item.orderId() == order.id() && item.productId() == target.id())))
                .allSatisfy(order -> assertThat(order.status()).isEqualTo(status.name()));
        assertReadable(prefix);
        var observed = observe(deleteRequest(prefix, target.id()), "order-reference-" + status + "-" + orders, 409);
        assertThat(observed.after()).isEqualTo(before);
        assertHistory(observed.after());
        assertReadable(prefix); // also reached on the initial absent-handler RED
        assertThat(database.snapshot()).isEqualTo(before);
        assertEnvelope(observed.result(), 409, "Product deletion violates referential integrity");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROOT", "/api"})
    void anotherRealTemporaryForeignKeyPreventsDeletionWithoutRemovingItsChildren(String external) throws Exception {
        String prefix = prefix(external);
        try (var connection = database.openConnection(); var statement = connection.createStatement()) {
            statement.execute("create table tb_t055_product_reference (id bigint primary key, product_id bigint not null, "
                    + "note varchar(80) not null, constraint fk_t055_extra_product foreign key (product_id) references tb_product(id))");
            extraTable = true;
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("insert into tb_t055_product_reference (id, product_id, note) values (?, ?, ?)")) {
                insert.setLong(1, 1); insert.setLong(2, target.id()); insert.setString(3, "Preservar filho do alvo"); insert.executeUpdate();
                insert.setLong(1, 2); insert.setLong(2, other.id()); insert.setString(3, "Preservar filho independente"); insert.executeUpdate();
                connection.commit();
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
        var children = List.of(new ExtraRow(1, target.id(), "Preservar filho do alvo"),
                new ExtraRow(2, other.id(), "Preservar filho independente"));
        assertThat(extraRows()).isEqualTo(children); // independent read after fixture commit
        var before = database.snapshot();
        assertThat(before.items()).noneMatch(item -> item.productId() == target.id());
        assertReadable(prefix);
        var observed = observe(deleteRequest(prefix, target.id()), "extra-reference", 409);
        assertThat(observed.after()).isEqualTo(before);
        assertThat(extraRows()).isEqualTo(children);
        assertHistory(observed.after());
        assertReadable(prefix);
        assertThat(database.snapshot()).isEqualTo(before); assertThat(extraRows()).isEqualTo(children);
        assertEnvelope(observed.result(), 409, "Product deletion violates referential integrity");
    }

    private void registerOrder(List<Line> lines, OrderStatus status) throws Exception {
        registerOrder(lines, status, "");
    }

    private void registerOrder(List<Line> lines, OrderStatus status, String prefix) throws Exception {
        var body = mapper.createObjectNode(); var items = body.putArray("items");
        lines.forEach(line -> items.addObject().put("productId", line.product()).put("quantity", line.quantity()));
        var before = database.snapshot();
        var observed = observe(post("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)), "prepare-order", 201);
        observed.after().orders().stream().filter(order -> before.orders().stream().noneMatch(prior -> prior.id() == order.id()))
                .forEach(order -> ownedOrders.add(order.id()));
        assertThat(observed.result().getResponse().getStatus()).isEqualTo(201);
        var response = json(observed.result()); long id = response.path("id").longValue();
        assertThat(ownedOrders).contains(id);
        assertThat(observed.after().orders()).hasSize(before.orders().size() + 1);
        assertThat(observed.after().orders().stream().filter(order -> order.id() != id).toList()).isEqualTo(before.orders());
        assertThat(observed.after().items().stream().filter(item -> item.orderId() != id).toList()).isEqualTo(before.items());
        var stored = database.snapshot();
        var order = stored.orders().stream().filter(row -> row.id() == id).findFirst().orElseThrow();
        assertThat(order.buyerId()).isEqualTo(buyer);
        assertThat(response.path("status").asText()).isEqualTo(OrderStatus.WAITING_PAYMENT.name());
        assertThat(order.status()).isEqualTo(OrderStatus.WAITING_PAYMENT.name());
        var actualItems = stored.items().stream().filter(item -> item.orderId() == id).toList();
        assertThat(actualItems).hasSize(lines.size());
        BigDecimal total = new BigDecimal("0.00");
        for (int position = 0; position < lines.size(); position++) {
            var item = actualItems.get(position); var expected = lines.get(position);
            assertThat(item.id()).isPositive(); assertThat(item.productId()).isEqualTo(expected.product());
            assertThat(item.position()).isEqualTo(position); assertThat(item.quantity()).isEqualTo(expected.quantity());
            assertThat(item.unitPrice()).isEqualTo(expected.price());
            total = total.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        assertThat(response.path("total").asText()).isEqualTo(total.toPlainString());
        assertThat(stored.products()).isEqualTo(before.products()); assertThat(stored.categories()).isEqualTo(before.categories());
        assertThat(stored.associations()).isEqualTo(before.associations());
        if (status == OrderStatus.PAID) {
            settleOwnedOrder(prefix, id, order.createdAt(), stored);
        } else {
            assertThat(status).isEqualTo(OrderStatus.WAITING_PAYMENT);
        }
        assertThat(database.snapshot().orders().stream().filter(row -> row.id() == id).findFirst().orElseThrow().status())
                .isEqualTo(status.name());
        System.out.printf("T055_ORDER id=%d createdStatus=WAITING_PAYMENT preparedStatus=%s items=%d independentTotal=%s%n",
                id, status, lines.size(), total.toPlainString());
    }

    private void settleOwnedOrder(String prefix, long id, Instant occurredAt, Snapshot before) throws Exception {
        outsideTransaction();
        var paymentBefore = payments.snapshot().payments();
        var path = "/admin/orders/" + id + "/payments";
        var result = mvc.perform(post(prefix + path).contextPath(prefix).servletPath(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(mapper.createObjectNode().put("occurredAt", occurredAt.toString()))))
                .andReturn();
        outsideTransaction();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResolvedException()).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        var confirmation = json(result);
        fields(confirmation, "orderId", "occurredAt", "status");
        assertThat(confirmation.path("orderId").isIntegralNumber()).isTrue();
        assertThat(confirmation.path("orderId").longValue()).isEqualTo(id);
        assertThat(confirmation.path("occurredAt").asText()).isEqualTo(
                new DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(occurredAt));
        assertThat(confirmation.path("status").asText()).isEqualTo("PAID");
        assertThat(payments.snapshot().payments()).containsExactlyInAnyOrderElementsOf(Stream.concat(
                paymentBefore.stream(), Stream.of(new PaymentRow(id, occurredAt))).toList());
        var after = database.snapshot();
        assertThat(after.orders()).isEqualTo(before.orders().stream().map(row -> row.id() == id
                ? new AdminProductDatabaseProbe.OrderRow(row.id(), row.buyerId(), row.createdAt(), "PAID") : row).toList());
        assertThat(after.items()).isEqualTo(before.items());
        assertThat(after.products()).isEqualTo(before.products());
        assertThat(after.categories()).isEqualTo(before.categories());
        assertThat(after.associations()).isEqualTo(before.associations());
        System.out.printf("T034_SETTLED context=%s order=%d status=201 payment=1 occurredAtPreserved=true%n",
                prefix.isEmpty() ? "ROOT" : prefix, id);
    }

    private Observation observe(MockHttpServletRequestBuilder request, String label, int expected) throws Exception {
        outsideTransaction(); var before = database.snapshot(); var accounts = database.accountIds();
        var paymentBefore = payments.snapshot().payments();
        var result = mvc.perform(request.accept(MediaType.APPLICATION_JSON)).andReturn();
        outsideTransaction(); var after = database.snapshot();
        assertThat(database.accountIds()).isEqualTo(accounts);
        assertThat(payments.snapshot().payments()).isEqualTo(paymentBefore);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        System.out.printf("T055_HTTP case=%s context=%s method=%s expected=%d actual=%d snapshotEqual=%s cause=%s%n",
                label, result.getRequest().getContextPath().isEmpty() ? "ROOT" : "/api", result.getRequest().getMethod(),
                expected, result.getResponse().getStatus(), before.equals(after),
                result.getResolvedException() == null ? "none" : result.getResolvedException().getClass().getSimpleName());
        return new Observation(result, before, after);
    }

    private void assertReadable(String prefix) throws Exception {
        for (boolean admin : List.of(true, false)) {
            var request = admin ? adminGet(prefix, target.id()) : get(prefix + "/products/" + target.id())
                    .contextPath(prefix).servletPath("/products/" + target.id());
            var read = observe(request, admin ? "admin-read-control" : "public-read-control", 200);
            assertThat(read.after()).isEqualTo(read.before());
            assertThat(read.result().getResponse().getStatus()).isEqualTo(200);
            var detail = json(read.result()); fields(detail, "id", "name", "description", "image", "price", "categories");
            assertThat(detail.path("id").longValue()).isEqualTo(target.id());
            assertThat(detail.path("name").asText()).isEqualTo(target.name());
            assertThat(detail.path("description").asText()).isEqualTo(target.description());
            assertThat(detail.path("image").asText()).isEqualTo(target.image());
            if (admin) {
                assertThat(detail.path("price").isTextual()).isTrue(); assertThat(detail.path("price").asText()).isEqualTo("10.00");
                assertThat(detail.path("categories")).hasSize(2);
                for (int i = 0; i < 2; i++) {
                    var option = detail.path("categories").get(i); fields(option, "id", "name");
                    assertThat(option.path("id").longValue()).isEqualTo(categories.get(i).id());
                    assertThat(option.path("name").asText()).isEqualTo(categories.get(i).name());
                }
            } else {
                assertThat(detail.path("price").isNumber()).isTrue();
                assertThat(detail.path("price").decimalValue()).isEqualByComparingTo(new BigDecimal("10.00"));
                assertThat(detail.path("categories")).hasSize(1);
                assertThat(detail.path("categories").get(0).asText()).isEqualTo("Categoria igual");
            }
        }
    }

    private void assertUnrelatedState(Observation observed) {
        assertThat(observed.after().categories()).isEqualTo(observed.before().categories());
        assertThat(observed.after().orders()).isEqualTo(observed.before().orders());
        assertThat(observed.after().items()).isEqualTo(observed.before().items());
        assertThat(observed.after().products().stream().filter(product -> product.id() != target.id()).toList())
                .isEqualTo(observed.before().products().stream().filter(product -> product.id() != target.id()).toList());
        assertThat(observed.after().associations().stream().filter(pair -> pair.productId() != target.id()).toList())
                .containsExactlyInAnyOrderElementsOf(observed.before().associations().stream().filter(pair -> pair.productId() != target.id()).toList());
    }

    private void assertHistory(Snapshot state) {
        assertThat(state.orders()).isNotEmpty(); assertThat(state.items()).isNotEmpty();
        assertThat(state.orders().stream().map(AdminProductDatabaseProbe.OrderRow::id)).containsExactlyInAnyOrderElementsOf(ownedOrders);
        for (var order : state.orders()) {
            assertThat(order.buyerId()).isEqualTo(buyer); assertThat(order.createdAt()).isNotNull();
            assertThat(state.items().stream().filter(item -> item.orderId() == order.id())).isNotEmpty();
        }
    }

    private List<ExtraRow> extraRows() throws Exception {
        try (var connection = database.openConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("select id, product_id, note from tb_t055_product_reference order by id")) {
            var rows = new ArrayList<ExtraRow>();
            while (result.next()) rows.add(new ExtraRow(result.getLong(1), result.getLong(2), result.getString(3)));
            return List.copyOf(rows);
        }
    }

    private void assertEnvelope(MvcResult result, int status, String message) throws Exception {
        assertThat(result.getResponse().getStatus()).as("deletion rejection HTTP contract").isEqualTo(status);
        var error = json(result); fields(error, "status", "error", "message", "path", "errors");
        assertThat(error.path("status").asInt()).isEqualTo(status);
        assertThat(error.path("error").asText()).isEqualTo(HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(error.path("message").asText()).isEqualTo(message);
        assertThat(error.path("path").asText()).isEqualTo(result.getRequest().getRequestURI());
        assertThat(error.path("errors").isArray()).isTrue(); assertThat(error.path("errors")).isEmpty();
    }

    private MockHttpServletRequestBuilder adminGet(String prefix, long id) {
        return get(prefix + route(id)).contextPath(prefix).servletPath(route(id)).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }
    private MockHttpServletRequestBuilder deleteRequest(String prefix, long id) {
        return delete(prefix + route(id)).contextPath(prefix).servletPath(route(id)).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }
    private JsonNode json(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
    private static void fields(JsonNode node, String... expected) {
        var names = new ArrayList<String>(); node.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder(expected);
    }
    private static ProductRow row(ProductData data) { return new ProductRow(data.id(), data.name(), data.description(), data.image(), data.price()); }
    private static String route(long id) { return "/admin/products/" + id; }
    private static String prefix(String external) { return external.equals("ROOT") ? "" : external; }
    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private void assertEmpty() {
        var state = database.snapshot();
        assertThat(state.products()).isEmpty(); assertThat(state.categories()).isEmpty(); assertThat(state.associations()).isEmpty();
        assertThat(state.orders()).isEmpty(); assertThat(state.items()).isEmpty(); assertThat(database.accountIds()).isEmpty();
        assertThat(payments.snapshot().payments()).isEmpty();
    }

    @AfterEach
    void cleanOnlyOwnedDataAfterIndependentAssertions() throws Exception {
        outsideTransaction(); if (fixture == null) return;
        try (var connection = database.openConnection()) {
            if (extraTable) try (var statement = connection.createStatement()) { statement.execute("drop table tb_t055_product_reference"); }
            connection.setAutoCommit(false);
            try (var payment = connection.prepareStatement("delete from tb_payment where order_id = ?");
                 var items = connection.prepareStatement("delete from tb_order_item where order_id = ?");
                 var orders = connection.prepareStatement("delete from tb_order where id = ?")) {
                for (long id : ownedOrders) {
                    payment.setLong(1, id); payment.executeUpdate();
                    items.setLong(1, id); items.executeUpdate(); orders.setLong(1, id); orders.executeUpdate();
                }
                connection.commit();
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
        fixture.close(); assertEmpty();
    }
    private record Line(long product, int quantity, BigDecimal price) { }
    private record ExtraRow(long id, long product, String note) { }
    private record Observation(MvcResult result, Snapshot before, Snapshot after) { }
}
