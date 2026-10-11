package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US1 contract first: real login and filters; order SQL assertions follow HTTP 201 only. */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc(addFilters = true, print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import(OrderTestConfig.class)
class OrderRegistrationIntegrationTest {

    private static final String PASSWORD = "order-registration-test-password";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private OrderTestFixture fixture;
    @Autowired private OrderTestConfig.ControlledClock clock;
    @Autowired private JwtDecoder decoder;
    @Autowired private FilterChainProxy securityFilters;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private final Set<Long> ownedAccounts = new LinkedHashSet<>();
    private JdbcTemplate independent;
    private long firstProduct;
    private long secondProduct;
    private boolean successfulPostSeen;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @BeforeEach
    void prepareOnlyExistingSchema() {
        assertOutsideTransaction();
        assertThat(securityFilters.getFilters("/login")).isNotEmpty();
        assertThat(securityFilters.getFilters("/orders")).isNotEmpty();
        var url = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        independent = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        successfulPostSeen = false;
        // Create in reverse request order so sorting by product id cannot satisfy the contract.
        secondProduct = fixture.createProduct("second-line-product", new BigDecimal("7.50"));
        firstProduct = fixture.createProduct("first-line-product", new BigDecimal("10.00"));
        assertThat(firstProduct).isGreaterThan(secondProduct);
    }

    @AfterEach
    void cleanOnlyOwnedRowsWithoutRequiringOrderTables() {
        // Metadata guards keep the initial RED free of missing-table cleanup errors.
        if (tableExists("TB_ORDER")) {
            for (var accountId : ownedAccounts) {
                if (tableExists("TB_ORDER_ITEM")) {
                    independent.update("delete from tb_order_item where order_id in "
                            + "(select id from tb_order where buyer_id = ?)", accountId);
                }
                independent.update("delete from tb_order where buyer_id = ?", accountId);
            }
        }
        fixture.cleanUp();
        ownedAccounts.clear();
    }

    // US1.1–4: CLIENT, ADMIN alone and both; each confirmation creates one complete order.
    @ParameterizedTest(name = "authorized database profiles {0}")
    @MethodSource("authorizedRoles")
    void shouldRegisterExactlyOneCompleteOrderForTheAuthenticatedAccount(Set<UserRole> roles) throws Exception {
        var otherAccount = createAccount(Set.of(UserRole.ADMIN));
        var account = login(roles);
        var expectedTime = creationTime();

        var response = assertConfirmation(submit(account.token(), usualBody()), account.id(),
                expectedTime, usualItems(), "42.50");

        assertStoredOrder(response.path("id").longValue(), account.id(), expectedTime, usualItems(), "42.50");
        assertThat(readOrders(account.id())).hasSize(1);
        assertThat(readOrders(otherAccount.id())).isEmpty();
    }

    // US1.5: the consumer sends no price; use the positive price committed before POST.
    @Test
    void shouldUseTheCurrentPositivePriceChangedBeforePurchase() throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var consumerBody = usualBody();
        fixture.setProductPrice(firstProduct, new BigDecimal("11.25"));
        assertThat(independent.queryForObject("select price from tb_product where id = ?",
                BigDecimal.class, firstProduct)).isEqualByComparingTo("11.25");
        var expected = List.of(item(firstProduct, 2, "11.25", "22.50"),
                item(secondProduct, 3, "7.50", "22.50"));
        var expectedTime = creationTime();

        var response = assertConfirmation(submit(account.token(), consumerBody), account.id(),
                expectedTime, expected, "45.00");

        assertStoredOrder(response.path("id").longValue(), account.id(), expectedTime, expected, "45.00");
    }

    // US1.6: check the stored snapshot directly, without introducing GET /orders.
    @Test
    void shouldKeepStoredPricesSubtotalsAndTotalAfterProductPriceChanges() throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var expectedTime = creationTime();
        var response = assertConfirmation(submit(account.token(), usualBody()), account.id(),
                expectedTime, usualItems(), "42.50");
        var orderId = response.path("id").longValue();
        assertStoredOrder(orderId, account.id(), expectedTime, usualItems(), "42.50");

        fixture.setProductPrice(firstProduct, new BigDecimal("99.99"));
        fixture.setProductPrice(secondProduct, new BigDecimal("25.00"));
        assertThat(independent.queryForObject("select price from tb_product where id = ?",
                BigDecimal.class, firstProduct)).isEqualByComparingTo("99.99");
        assertThat(independent.queryForObject("select price from tb_product where id = ?",
                BigDecimal.class, secondProduct)).isEqualByComparingTo("25.00");

        assertStoredOrder(orderId, account.id(), expectedTime, usualItems(), "42.50");
        assertThat(readOrders(account.id())).hasSize(1);
    }

    // US1.7: exact confirmation shape excludes cart state or a clearing promise.
    @Test
    void shouldConfirmOnlyOrderDataWithoutLocationOrCartClearingPromise() throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var expectedTime = creationTime();
        var response = assertConfirmation(submit(account.token(), usualBody()), account.id(),
                expectedTime, usualItems(), "42.50");

        assertThat(response.toString()).doesNotContain("cart", "carrinho", "cleared", "emptied");
        assertStoredOrder(response.path("id").longValue(), account.id(), expectedTime, usualItems(), "42.50");
    }

    // US1.8: each purchase has its own id and creation timestamp; timestamps may coincide.
    @Test
    void shouldGiveDistinctPurchasesTheirOwnIdsAndCreationInstants() throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var firstTime = creationTime();
        var first = assertConfirmation(submit(account.token(), usualBody()), account.id(),
                firstTime, usualItems(), "42.50");
        var secondTime = creationTime();
        var secondItems = List.of(item(secondProduct, 1, "7.50", "7.50"));
        var second = assertConfirmation(submit(account.token(), singleItemBody()), account.id(),
                secondTime, secondItems, "7.50");

        assertThat(second.path("id").longValue()).isNotEqualTo(first.path("id").longValue());
        assertStoredOrder(first.path("id").longValue(), account.id(), firstTime, usualItems(), "42.50");
        assertStoredOrder(second.path("id").longValue(), account.id(), secondTime, secondItems, "7.50");
        assertThat(readOrders(account.id())).hasSize(2);
    }

    // US1.9: discard the first response body; independent SQL is the observation of its commit.
    @Test
    void shouldCreateAnotherOrderAfterTheFirstConfirmationIsDiscarded() throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var body = usualBody();
        var firstTime = creationTime();
        submit(account.token(), body); // Deliberately do not consume the returned confirmation.
        var beforeRetry = readOrders(account.id());
        assertThat(beforeRetry).hasSize(1);
        var first = beforeRetry.getFirst();
        assertStoredOrder(first.id(), account.id(), firstTime, usualItems(), "42.50");
        var retryTime = creationTime();

        var retry = assertConfirmation(submit(account.token(), body), account.id(),
                retryTime, usualItems(), "42.50");

        assertThat(retry.path("id").longValue()).isNotEqualTo(first.id());
        assertThat(readOrders(account.id())).hasSize(2).contains(first);
        assertStoredOrder(first.id(), account.id(), firstTime, usualItems(), "42.50");
        assertStoredOrder(retry.path("id").longValue(), account.id(), retryTime, usualItems(), "42.50");
    }

    // US1.10: an identical POST creates a new order even when confirmation was consumed.
    @Test
    void shouldCreateAnotherOrderForAnIdenticalPostAfterReceivingConfirmation() throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var body = usualBody();
        var firstTime = creationTime();
        var first = assertConfirmation(submit(account.token(), body), account.id(),
                firstTime, usualItems(), "42.50");
        assertStoredOrder(first.path("id").longValue(), account.id(), firstTime, usualItems(), "42.50");
        var secondTime = creationTime();

        var second = assertConfirmation(submit(account.token(), body), account.id(),
                secondTime, usualItems(), "42.50");

        assertThat(second.path("id").longValue()).isNotEqualTo(first.path("id").longValue());
        assertStoredOrder(first.path("id").longValue(), account.id(), firstTime, usualItems(), "42.50");
        assertStoredOrder(second.path("id").longValue(), account.id(), secondTime, usualItems(), "42.50");
        assertThat(readOrders(account.id())).hasSize(2);
    }

    private TestAccount createAccount(Set<UserRole> roles) {
        var name = "registration-" + UUID.randomUUID();
        var id = fixture.createAccount(name, PASSWORD, roles);
        ownedAccounts.add(id);
        assertThat(independent.queryForList("select role from tb_user_account_role where user_account_id = ?",
                String.class, id)).containsExactlyInAnyOrderElementsOf(roles.stream().map(Enum::name).toList());
        return new TestAccount(id, name, null);
    }

    private TestAccount login(Set<UserRole> roles) throws Exception {
        var account = createAccount(roles);
        var result = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","password":"%s"}
                                """.formatted(account.name(), PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        var payload = mapper.readTree(result.getResponse().getContentAsString());
        assertFields(payload, "accessToken", "tokenType");
        assertThat(payload.path("tokenType").textValue()).isEqualTo("Bearer");
        var token = payload.path("accessToken").textValue();
        assertThat(token).isNotBlank();
        var jwt = decoder.decode(token);
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(jwt.getSubject()).isEqualTo(Long.toString(account.id()));
        assertThat(jwt.getClaims()).doesNotContainKeys("roles", "passwordHash", "secret");
        // Keep login's real wall-clock JWT valid; exercise order timestamp truncation separately.
        clock.setInstant(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusMillis(123).plusNanos(456789));
        assertOutsideTransaction();
        return new TestAccount(account.id(), account.name(), token);
    }

    private MvcResult submit(String token, String rawBody) throws Exception {
        assertOutsideTransaction();
        var result = mvc.perform(post("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(rawBody))
                .andExpect(status().isCreated()).andReturn();
        successfulPostSeen = true;
        assertOutsideTransaction();
        return result;
    }

    private JsonNode assertConfirmation(MvcResult result, long accountId, Instant expectedTime,
                                        List<ExpectedItem> expectedItems, String total) throws Exception {
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        var response = mapper.readTree(result.getResponse().getContentAsString());
        assertFields(response, "id", "accountId", "createdAt", "status", "items", "total");
        assertPositiveId(response.path("id"));
        assertPositiveId(response.path("accountId"));
        assertThat(response.path("accountId").longValue()).isEqualTo(accountId);
        assertThat(response.path("status").isTextual()).isTrue();
        assertThat(response.path("status").textValue()).isEqualTo("WAITING_PAYMENT");
        var timestamp = response.path("createdAt");
        assertThat(timestamp.isTextual()).isTrue();
        assertThat(timestamp.textValue()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?Z");
        var createdAt = Instant.parse(timestamp.textValue());
        assertThat(createdAt).isEqualTo(expectedTime);
        assertThat(createdAt.getNano() % 1_000_000).isZero();
        var items = response.path("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isEqualTo(expectedItems.size());
        for (var position = 0; position < expectedItems.size(); position++) {
            var actual = items.get(position);
            var expected = expectedItems.get(position);
            assertFields(actual, "productId", "quantity", "unitPrice", "subtotal");
            assertPositiveId(actual.path("productId"));
            assertThat(actual.path("productId").longValue()).isEqualTo(expected.productId());
            assertThat(actual.path("quantity").isIntegralNumber()).isTrue();
            assertThat(actual.path("quantity").canConvertToInt()).isTrue();
            assertThat(actual.path("quantity").intValue()).isEqualTo(expected.quantity());
            assertMoney(actual.path("unitPrice"), expected.unitPrice().toPlainString());
            assertMoney(actual.path("subtotal"), expected.subtotal().toPlainString());
        }
        assertMoney(response.path("total"), total);
        assertThat(response.toString()).doesNotContain("password", "hash", "secret", "roles", "name");
        return response;
    }

    private List<StoredOrder> readOrders(long accountId) {
        assertThat(successfulPostSeen).as("order SQL must follow a successful HTTP 201").isTrue();
        assertOutsideTransaction();
        // DriverManagerDataSource creates fresh connections, outside the application's persistence context.
        return independent.query("select id, buyer_id, created_at, status from tb_order where buyer_id = ? order by id",
                (rs, row) -> new StoredOrder(rs.getLong("id"), rs.getLong("buyer_id"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getString("status")), accountId);
    }

    private void assertStoredOrder(long orderId, long accountId, Instant time,
                                   List<ExpectedItem> expectedItems, String total) {
        assertThat(readOrders(accountId)).contains(new StoredOrder(orderId, accountId, time, "WAITING_PAYMENT"));
        var items = independent.query("select product_id, position, quantity, unit_price from tb_order_item "
                        + "where order_id = ? order by position",
                (rs, row) -> new StoredItem(rs.getLong("product_id"), rs.getInt("position"),
                        rs.getInt("quantity"), rs.getBigDecimal("unit_price")), orderId);
        assertThat(items).hasSize(expectedItems.size());
        var storedTotal = BigDecimal.ZERO;
        for (var position = 0; position < items.size(); position++) {
            var actual = items.get(position);
            var expected = expectedItems.get(position);
            assertThat(actual.position()).isEqualTo(position);
            assertThat(actual.productId()).isEqualTo(expected.productId());
            assertThat(actual.quantity()).isEqualTo(expected.quantity());
            assertThat(actual.unitPrice()).isEqualByComparingTo(expected.unitPrice());
            var subtotal = actual.unitPrice().multiply(BigDecimal.valueOf(actual.quantity()));
            assertThat(subtotal).isEqualByComparingTo(expected.subtotal());
            storedTotal = storedTotal.add(subtotal);
        }
        assertThat(storedTotal).isEqualByComparingTo(total);
        assertOutsideTransaction();
    }

    private boolean tableExists(String name) {
        return Integer.valueOf(1).equals(independent.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema = 'PUBLIC' and table_name = ?", Integer.class, name));
    }

    private void assertOutsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.hasResource(entityManagerFactory)).isFalse();
    }

    private static void assertFields(JsonNode value, String... expected) {
        assertThat(value.isObject()).isTrue();
        var fields = new HashSet<String>();
        value.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(expected);
    }

    private static void assertPositiveId(JsonNode id) {
        assertThat(id.isIntegralNumber()).isTrue();
        assertThat(id.canConvertToLong()).isTrue();
        assertThat(id.longValue()).isPositive();
    }

    private static void assertMoney(JsonNode amount, String expected) {
        assertThat(amount.isTextual()).isTrue();
        assertThat(amount.textValue()).matches("(?:0|[1-9][0-9]*)\\.[0-9]{2}").isEqualTo(expected);
    }

    private Instant creationTime() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private String usualBody() {
        return """
                {"items":[{"productId":%d,"quantity":2},{"productId":%d,"quantity":3}]}
                """.formatted(firstProduct, secondProduct);
    }

    private String singleItemBody() {
        return """
                {"items":[{"productId":%d,"quantity":1}]}
                """.formatted(secondProduct);
    }

    private List<ExpectedItem> usualItems() {
        return List.of(item(firstProduct, 2, "10.00", "20.00"), item(secondProduct, 3, "7.50", "22.50"));
    }

    private static ExpectedItem item(long productId, int quantity, String price, String subtotal) {
        return new ExpectedItem(productId, quantity, new BigDecimal(price), new BigDecimal(subtotal));
    }

    static Stream<Set<UserRole>> authorizedRoles() {
        return Stream.of(Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN), Set.of(UserRole.CLIENT, UserRole.ADMIN));
    }

    private record TestAccount(long id, String name, String token) { }
    private record ExpectedItem(long productId, int quantity, BigDecimal unitPrice, BigDecimal subtotal) { }
    private record StoredOrder(long id, long accountId, Instant createdAt, String status) { }
    private record StoredItem(long productId, int position, int quantity, BigDecimal unitPrice) { }
}
