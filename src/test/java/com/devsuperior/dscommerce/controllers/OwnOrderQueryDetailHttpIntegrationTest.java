package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryTestObservationConfig;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
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
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.FIRST_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=", "spring.jpa.open-in-view=false",
                "spring.h2.console.enabled=false", "feature007.own-order-detail-test-context=root"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(OwnOrderQueryTestObservationConfig.class)
class OwnOrderQueryDetailHttpIntegrationTest extends OwnOrderQueryDetailHttpTestSupport {
    @Override protected String expectedContextPath() { return ""; }
    @Override protected String expectedContextIdentity() { return "root"; }
}

/** US2 only: both concrete contexts inherit these assertions, without rerunning US1 or authenticationGate. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class OwnOrderQueryDetailHttpTestSupport {

    @ParameterizedTest(name = "post008 PAID historical total {0}")
    @ValueSource(strings = {"42.50", "0.01", "9007199254740993.01"})
    void detailPost008RealPaymentKeepsCurrentNamesRecordedItemsAndExactMoney(String amount) {
        try (var journey = new PaidOwnOrderHttpJourney(context, mapper, port, expectedContextPath(), sql)) {
            journey.prepare("client");
            var target = journey.orders.stream().filter(order -> order.total().equals(amount)).findFirst().orElseThrow();
            journey.assertDetail(journey.ownerToken, target);
            journey.pay(target, FIRST_INSTANT.plusSeconds(7200));
            journey.renameCurrentCatalog();
            journey.assertPages();
            journey.assertDetail(journey.ownerToken, journey.order(target.id()));
            journey.assertDetail(journey.ownerToken, journey.order(target.id()));
            journey.assertHead("/orders/" + target.id(), journey.ownerToken, 200);
        }
    }

    private static final DateTimeFormatter INSTANT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final Set<String> PAGE_FIELDS = Set.of("content", "page", "size", "totalElements", "totalPages");
    private static final Set<String> SUMMARY_FIELDS = Set.of("id", "createdAt", "status", "total");
    private static final Set<String> DETAIL_FIELDS = Set.of("id", "createdAt", "status", "total", "items");
    private static final Set<String> ITEM_FIELDS = Set.of("productId", "productName", "quantity", "unitPrice", "subtotal");

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtDecoder decoder;
    @Autowired private OwnOrderQueryDatabaseProbe database;
    @Autowired private SqlObservation sql;
    @Autowired private JdbcTemplate jdbc;
    @LocalServerPort private int port;

    private OwnOrderQueryTestFixture fixture;
    private Account buyer;
    private String token;
    private HttpClient http;
    private int logins;
    private int listRequests;
    private int detailRequests;

    protected abstract String expectedContextPath();
    protected abstract String expectedContextIdentity();

    @BeforeAll
    void verifyIsolatedSeedFreeDetailContext() {
        noCallerTransaction();
        var environment = context.getEnvironment();
        require(expectedContextIdentity().equals(environment.getRequiredProperty("feature007.own-order-detail-test-context")),
                "Unexpected detail context identity");
        require(expectedContextPath().equals(context.getServletContext().getContextPath()), "Unexpected context path");
        require("false".equals(environment.getRequiredProperty("spring.jpa.open-in-view")), "OSIV must be disabled");
        require("create-drop".equals(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto")), "Keep create-drop");
        require("never".equals(environment.getRequiredProperty("spring.sql.init.mode")), "Use the seed-free context");
        require("".equals(environment.getRequiredProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")),
                "Hibernate seed import must be disabled");
        var url = environment.getRequiredProperty("spring.datasource.url");
        var metadata = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        require(url.startsWith("jdbc:h2:mem:admin-product-test-") && url.split(";", 2)[0].equals(metadata),
                "Use the stable isolated H2 database");
        require(url.equals(environment.getRequiredProperty("spring.datasource.url")), "Database URL must be stable");
        assertEmptyDatabase();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        System.out.println("T017_CONTEXT context=" + expectedContextIdentity() + " path="
                + (expectedContextPath().isEmpty() ? "ROOT" : expectedContextPath()) + " port=" + port
                + " database=" + metadata + " mockMvcDispatches=0");
    }

    @BeforeEach
    void prepareOwnedCommittedRowsAndRealHttpLogin() {
        noCallerTransaction();
        assertEmptyDatabase();
        fixture = new OwnOrderQueryTestFixture(context);
        buyer = fixture.prepareBuyer();
        var roles = database.snapshot().roles().stream().filter(role -> role.accountId() == buyer.id())
                .map(OwnOrderQueryDatabaseProbe.AccountRole::role).collect(java.util.stream.Collectors.toSet());
        require(roles.equals(buyer.roles()), "Persisted setup profiles must match the manifest");
        token = realHttpLogin(buyer);
    }

    @AfterEach
    void cleanOnlyThisScenariosRowsOutsideReadWindows() {
        try {
            if (fixture != null) { fixture.close(); }
        } finally {
            fixture = null;
            buyer = null;
            token = null;
        }
        assertEmptyDatabase();
    }

    @AfterAll
    void closeTransportAndReportActualCounts() {
        if (http != null) { http.close(); }
        System.out.println("T017_COUNTS context=" + expectedContextIdentity() + " httpLogins=" + logins
                + " listHttpRequests=" + listRequests + " detailHttpRequests=" + detailRequests
                + " mockMvcDispatches=0");
    }

    @Test
    void detailUs21ReturnsExactFieldsAndRecordedBAOrderWithHistoricalMoney() {
        var a = fixture.createProduct(" Produto A atual ", new BigDecimal("99.00"));
        var b = fixture.createProduct("Produto B atual", new BigDecimal("12.00"));
        assertThat(b.id()).isGreaterThan(a.id());
        var order = fixture.createOrder(buyer, FIRST_INSTANT,
                List.of(new ItemInput(b.id(), 2, "10.00", "20.00"),
                        new ItemInput(a.id(), 3, "7.50", "22.50")), "42.50");
        assertThat(order.items()).extracting(item -> item.position()).containsExactly(0, 1);
        assertListedDetail(order, List.of(order), List.of(b.name(), a.name()));
    }

    @Test
    void detailUs22ReadsOwnOrderOutsideThePreviouslyRequestedPage() {
        var product = fixture.createProduct("Compra fora da página", new BigDecimal("99.00"));
        var oldest = fixture.createOrder(buyer, FIRST_INSTANT,
                List.of(new ItemInput(product.id(), 2, "10.00", "20.00")), "20.00");
        var middle = fixture.createOrder(buyer, FIRST_INSTANT.plusSeconds(1),
                List.of(new ItemInput(product.id(), 3, "7.50", "22.50")), "22.50");
        var newest = fixture.createOrder(buyer, FIRST_INSTANT.plusSeconds(2),
                List.of(new ItemInput(product.id(), 1, "0.01", "0.01")), "0.01");
        var page = assertPage(0, 1, 3, 3, List.of(newest));
        assertThat(page.get("content").get(0).get("id").longValue()).isNotEqualTo(oldest.id());
        var detail = assertDetail(oldest, List.of(product.name()));
        var oldestPage = assertPage(2, 1, 3, 3, List.of(oldest));
        assertSameHeader(oldestPage.get("content").get(0), detail);
        // A later list request must not change which directly addressed purchase is returned.
        assertPage(1, 1, 3, 3, List.of(middle));
        assertDetail(oldest, List.of(product.name()));
    }

    @Test
    void detailUs23UsesRenamedCurrentProductWithoutReplacingHistoricalPrice() {
        var product = fixture.createProduct("Nome antigo", new BigDecimal("10.00"));
        var order = fixture.createOrder(buyer, FIRST_INSTANT,
                List.of(new ItemInput(product.id(), 2, "10.00", "20.00")), "20.00");
        assertListedDetail(order, List.of(order), List.of("Nome antigo"));
        changeOwnedCurrentProduct(product.id(), "Nome atual", "12.00");
        assertListedDetail(order, List.of(order), List.of("Nome atual"));
    }

    @Test
    void detailUs24KeepsDistinctProductIdsAndPositionsAfterNamesBecomeEqual() {
        var a = fixture.createProduct("Produto A", new BigDecimal("99.00"));
        var b = fixture.createProduct("Produto B", new BigDecimal("12.00"));
        assertThat(b.id()).isGreaterThan(a.id());
        var order = fixture.createOrder(buyer, FIRST_INSTANT,
                List.of(new ItemInput(b.id(), 2, "10.00", "20.00"),
                        new ItemInput(a.id(), 3, "7.50", "22.50")), "42.50");
        assertListedDetail(order, List.of(order), List.of(b.name(), a.name()));
        changeOwnedCurrentProduct(a.id(), " Nome igual ", "99.00");
        changeOwnedCurrentProduct(b.id(), " Nome igual ", "12.00");
        var detail = assertListedDetail(order, List.of(order), List.of(" Nome igual ", " Nome igual "));
        assertThat(detail.get("items").get(0).get("productId").longValue()).isEqualTo(b.id());
        assertThat(detail.get("items").get(1).get("productId").longValue()).isEqualTo(a.id());
    }

    @ParameterizedTest(name = "legacy current name [{index}]")
    @MethodSource("com.devsuperior.dscommerce.controllers.OwnOrderQueryDetailHttpTestSupport#legacyNames")
    void detailUs25ReadsLegacyNameZeroCurrentPriceAndNoCategoriesWithoutRepair(String currentName) {
        var product = fixture.createProduct("Produto antes do legado", new BigDecimal("10.00"));
        var order = fixture.createOrder(buyer, FIRST_INSTANT,
                List.of(new ItemInput(product.id(), 2, "10.00", "20.00")), "20.00");
        changeOwnedCurrentProduct(product.id(), currentName, "0.00");
        assertThat(jdbc.queryForObject("select count(*) from tb_product_category where product_id = ?",
                Long.class, product.id())).isZero();
        assertListedDetail(order, List.of(order), List.of(currentName));
        assertCurrentProduct(product.id(), currentName, "0.00");
    }

    @ParameterizedTest(name = "exact historical money [{index}] {0}")
    @ValueSource(strings = {"0.01", "9007199254740993.01"})
    void detailUs26PreservesExactSmallAndLargeTextualMoneyInListAndDetail(String amount) {
        var product = fixture.createProduct("Preço atual diferente", new BigDecimal("99.00"));
        var order = fixture.createOrder(buyer, FIRST_INSTANT,
                List.of(new ItemInput(product.id(), 1, amount, amount)), amount);
        assertListedDetail(order, List.of(order), List.of(product.name()));
    }

    @Test
    void detailUs27KeepsEachPurchasesOwnQuantitiesPricesSubtotalsAndTotal() {
        var a = fixture.createProduct("Mesmo produto A", new BigDecimal("99.00"));
        var b = fixture.createProduct("Mesmo produto B", new BigDecimal("100.00"));
        var first = fixture.createOrder(buyer, FIRST_INSTANT,
                List.of(new ItemInput(b.id(), 2, "10.00", "20.00"),
                        new ItemInput(a.id(), 3, "7.50", "22.50")), "42.50");
        var second = fixture.createOrder(buyer, FIRST_INSTANT.plusSeconds(1),
                List.of(new ItemInput(b.id(), 3, "12.00", "36.00"),
                        new ItemInput(a.id(), 2, "8.00", "16.00")), "52.00");
        assertThat(first.id()).isNotEqualTo(second.id());
        var page = assertPage(0, 12, 2, 1, List.of(second, first));
        var names = List.of(b.name(), a.name());
        assertSameHeader(page.get("content").get(0), assertDetail(second, names));
        assertSameHeader(page.get("content").get(1), assertDetail(first, names));
        assertDetail(second, names);
    }

    private JsonNode assertListedDetail(OrderManifest order, List<OrderManifest> expectedPage, List<String> names) {
        var page = assertPage(0, 12, expectedPage.size(), 1, expectedPage);
        var detail = assertDetail(order, names);
        var index = expectedPage.indexOf(order);
        require(index >= 0, "Expected manifest must contain the detailed order");
        assertSameHeader(page.get("content").get(index), detail);
        return detail;
    }

    private JsonNode assertPage(int page, int size, long total, int pages, List<OrderManifest> expected) {
        var actual = get("/orders?page=" + page + "&size=" + size, false);
        assertFields(actual, PAGE_FIELDS);
        assertInteger(actual.get("page"), page);
        assertInteger(actual.get("size"), size);
        assertInteger(actual.get("totalElements"), total);
        assertInteger(actual.get("totalPages"), pages);
        var content = actual.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(expected.size());
        for (int index = 0; index < expected.size(); index++) {
            assertFields(content.get(index), SUMMARY_FIELDS);
            assertHeader(content.get(index), expected.get(index));
        }
        return actual;
    }

    private JsonNode assertDetail(OrderManifest order, List<String> names) {
        var actual = get("/orders/" + order.id(), true);
        assertFields(actual, DETAIL_FIELDS);
        assertHeader(actual, order);
        var items = actual.get("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isEqualTo(order.items().size());
        assertThat(names).hasSize(order.items().size());
        for (int index = 0; index < order.items().size(); index++) {
            var item = items.get(index);
            var expected = order.items().get(index);
            assertFields(item, ITEM_FIELDS);
            assertInteger(item.get("productId"), expected.productId());
            assertText(item.get("productName"), names.get(index));
            assertInteger(item.get("quantity"), expected.quantity());
            assertMoney(item.get("unitPrice"), expected.unitPrice());
            assertMoney(item.get("subtotal"), expected.subtotal());
        }
        return actual;
    }

    private static void assertHeader(JsonNode actual, OrderManifest order) {
        assertInteger(actual.get("id"), order.id());
        assertText(actual.get("createdAt"), INSTANT.format(order.createdAt()));
        assertText(actual.get("status"), order.status());
        assertMoney(actual.get("total"), order.total());
    }

    private static void assertSameHeader(JsonNode summary, JsonNode detail) {
        for (var field : SUMMARY_FIELDS) { assertThat(detail.get(field)).isEqualTo(summary.get(field)); }
    }

    /** Explicit test preparation only, after purchase and outside every observed GET window. */
    private void changeOwnedCurrentProduct(long productId, String name, String price) {
        noCallerTransaction();
        var committed = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        committed.executeWithoutResult(status -> assertThat(jdbc.update(
                "update tb_product set name = ?, price = ? where id = ?", name, new BigDecimal(price), productId))
                .isEqualTo(1));
        assertCurrentProduct(productId, name, price);
    }

    private void assertCurrentProduct(long id, String name, String price) {
        assertThat(jdbc.queryForObject("select name from tb_product where id = ?", String.class, id)).isEqualTo(name);
        assertThat(jdbc.queryForObject("select price from tb_product where id = ?", BigDecimal.class, id))
                .isEqualByComparingTo(price);
    }

    private JsonNode get(String path, boolean detail) {
        noCallerTransaction();
        require(token != null, "Use a prepared real login token");
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").header("Authorization", "Bearer " + token).GET().build();
        var observation = database.observeRead(sql, () -> send(request));
        var response = observation.result();
        if (detail) { detailRequests++; } else { listRequests++; }
        System.out.println("T017_HTTP context=" + expectedContextIdentity() + " method=GET path=" + path
                + " expected=200 actual=" + response.statusCode() + " bodyBytes=" + response.body().length);
        observation.assertNoWrites();
        assertThat(response.statusCode()).as("own order detail scenario HTTP status").isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        try {
            return mapper.readTree(response.body());
        } catch (IOException failure) {
            throw new AssertionError("Order query response must be valid JSON");
        }
    }

    private String realHttpLogin(Account account) {
        noCallerTransaction();
        try {
            var request = HttpRequest.newBuilder(uri("/login")).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(
                            mapper.writeValueAsBytes(Map.of("name", account.name(), "password", account.password())))).build();
            var response = send(request);
            logins++;
            require(response.statusCode() == 200, "Real detail fixture login must succeed");
            var credential = mapper.readValue(response.body(), TokenResponseDTO.class);
            require("Bearer".equals(credential.tokenType()) && credential.accessToken() != null,
                    "Expected a real bearer token");
            require(Long.toString(account.id()).equals(decoder.decode(credential.accessToken()).getSubject()),
                    "Login identity must match the prepared account");
            return credential.accessToken();
        } catch (IOException | JwtException failure) {
            throw new IllegalStateException("Real detail fixture login could not be verified");
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Detail HTTP transport interrupted");
        } catch (IOException failure) {
            throw new IllegalStateException("Detail HTTP transport did not complete");
        }
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + expectedContextPath() + path); }

    private void assertEmptyDatabase() {
        var empty = database.snapshot();
        require(empty.accountIds().isEmpty() && empty.roles().isEmpty() && empty.data().orders().isEmpty()
                        && empty.data().items().isEmpty() && empty.data().products().isEmpty()
                        && empty.data().categories().isEmpty() && empty.data().associations().isEmpty(),
                "Detail context must contain only explicitly owned scenario data");
    }

    private static void assertFields(JsonNode node, Set<String> expected) {
        assertThat(node).isNotNull();
        assertThat(node.isObject()).isTrue();
        var fields = new HashSet<String>();
        node.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static void assertInteger(JsonNode node, long expected) {
        assertThat(node).isNotNull();
        assertThat(node.isIntegralNumber()).isTrue();
        assertThat(node.canConvertToLong()).isTrue();
        assertThat(node.longValue()).isEqualTo(expected);
    }

    private static void assertText(JsonNode node, String expected) {
        assertThat(node).isNotNull();
        assertThat(node.isTextual()).isTrue();
        assertThat(node.textValue()).isEqualTo(expected);
    }

    private static void assertMoney(JsonNode node, String expected) {
        assertText(node, expected);
        assertThat(node.textValue()).matches("^(0\\.(0[1-9]|[1-9][0-9])|[1-9][0-9]{0,16}\\.[0-9]{2})$");
    }

    private static void noCallerTransaction() {
        require(!TransactionSynchronizationManager.isActualTransactionActive(), "HTTP must not have a caller transaction");
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    static Stream<String> legacyNames() {
        // Administrative write limits are 3..80; both names fit the existing varchar(255).
        return Stream.of("Á", " " + "Café ".repeat(17) + " ");
    }
}
