package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** U1: observe HTTP and committed SQL before assertions or inherited owned-row cleanup. */
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "logging.level.root=WARN"})
@AutoConfigureMockMvc(addFilters = true, print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import(OrderTestConfig.class)
@Execution(ExecutionMode.SAME_THREAD)
class OrderTrailingContentIntegrationTest extends OrderHttpTestSupport {

    private JdbcTemplate independent;
    private long firstProduct;
    private long secondProduct;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @BeforeEach
    void prepareIndependentObservationAndExplicitProducts() {
        assertOutsideTransaction();
        var url = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        assertThat(url).startsWith("jdbc:h2:mem:order-test-");
        independent = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        assertThat(independent.getDataSource()).isNotSameAs(jdbc.getDataSource());
        // Reverse id order guards against an accidental sort rather than request order.
        secondProduct = fixture.createProduct("U1 second item", new BigDecimal("7.50"));
        firstProduct = fixture.createProduct("U1 first item", new BigDecimal("10.00"));
        assertThat(firstProduct).isGreaterThan(secondProduct);
    }

    static Stream<Arguments> trailingBodies() {
        return Stream.of(Suffix.values()).flatMap(suffix -> Stream.of("", "/api")
                .map(contextPath -> Arguments.of(suffix, contextPath)));
    }

    @ParameterizedTest(name = "U1 {0}, context={1}")
    @MethodSource("trailingBodies")
    void shouldRejectAdditionalContentButAcceptOnlyWhitespace(Suffix suffix, String contextPath) throws Exception {
        var account = login(Set.of(UserRole.CLIENT)); // Existing helper performs real login and JWT decoding.
        assertThat(account.decoded().getHeaders().get("alg")).isEqualTo("HS256");
        var before = snapshot();
        assertThat(before.autoCommit()).isTrue();
        assertThat(before.boundToApplicationTransaction()).isFalse();
        assertThat(before.orders()).isEmpty();
        assertThat(before.items()).isEmpty();
        assertOutsideTransaction();
        var body = "{\"items\":[{\"productId\":" + firstProduct + ",\"quantity\":2},"
                + "{\"productId\":" + secondProduct + ",\"quantity\":3}]}" + suffix.raw;
        var path = contextPath + "/orders";
        MvcResult result = null;
        Exception requestFailure = null;
        try {
            result = mvc.perform(post(path).contextPath(contextPath).servletPath("/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + account.token())
                    .contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8)))
                    .andReturn(); // No HTTP assertion before independent database observation.
        } catch (Exception failure) {
            requestFailure = failure;
        }
        var after = snapshot(); // Even if request processing throws, collect committed state before cleanup.
        boolean externalTransaction = TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive();
        int httpStatus = result == null ? -1 : result.getResponse().getStatus();
        System.out.printf("U1_CASE case=%s path=%s http=%d expected=%d ordersBefore=%d itemsBefore=%d "
                        + "ordersAfter=%d itemsAfter=%d autoCommit=%s bound=%s externalTx=%s requestFailure=%s%n",
                suffix.name(), path, httpStatus, suffix.whitespaceOnly ? 201 : 400,
                before.orders().size(), before.items().size(), after.orders().size(), after.items().size(),
                after.autoCommit(), after.boundToApplicationTransaction(), externalTransaction,
                requestFailure == null ? "none" : requestFailure.getClass().getSimpleName());
        assertThat(requestFailure == null).as("real HTTP processing completed; see sanitized U1_CASE").isTrue();
        var response = mapper.readTree(result.getResponse().getContentAsString());
        var actualResult = result;
        if (!suffix.whitespaceOnly) {
            assertAll("U1 rejection and independent database state",
                    () -> assertThat(httpStatus).isEqualTo(400),
                    () -> assertThat(fields(response)).containsExactlyInAnyOrder("status", "error", "message", "path"),
                    () -> assertThat(response.path("status").isIntegralNumber()
                            && response.path("status").intValue() == 400).isTrue(),
                    () -> assertThat(response.path("error").asText().equals("Bad Request")).isTrue(),
                    () -> assertThat(response.path("message").asText().equals("Invalid order data")).isTrue(),
                    () -> assertThat(response.path("path").asText().equals(path)).isTrue(),
                    () -> assertThat(actualResult.getResponse().getContentType()).startsWith("application/json"),
                    () -> assertThat(after).as("no committed order/item changes before cleanup").isEqualTo(before),
                    () -> assertThat(externalTransaction).isFalse());
        } else {
            assertAll("U1 whitespace positive control and complete committed aggregate",
                    () -> assertThat(httpStatus).isEqualTo(201),
                    () -> assertThat(externalTransaction).isFalse(),
                    () -> assertThat(after.autoCommit()).isTrue(),
                    () -> assertThat(after.boundToApplicationTransaction()).isFalse(),
                    () -> assertThat(actualResult.getResponse().getHeader(HttpHeaders.LOCATION)).isNull(),
                    () -> assertCompleteConfirmationAndCommit(response, account.id(), after));
        }
    }

    private void assertCompleteConfirmationAndCommit(JsonNode response, long buyer, DatabaseState after) {
        assertThat(fields(response)).containsExactlyInAnyOrder("id", "accountId", "createdAt", "status", "items", "total");
        assertThat(response.path("id").isIntegralNumber() && response.path("id").asLong() > 0).isTrue();
        assertThat(response.path("accountId").isIntegralNumber() && response.path("accountId").asLong() == buyer).isTrue();
        assertThat(response.path("status").asText()).isEqualTo("WAITING_PAYMENT");
        var created = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        assertThat(Instant.parse(response.path("createdAt").asText())).isEqualTo(created);
        assertThat(response.path("createdAt").asText()).endsWith("Z");
        assertThat(response.path("total").isTextual() && response.path("total").asText().equals("42.50")).isTrue();
        assertThat(response.path("items").isArray()).isTrue();
        assertThat(response.path("items").size()).isEqualTo(2);
        for (int i = 0; i < 2; i++) {
            var item = response.path("items").get(i);
            assertThat(fields(item)).containsExactlyInAnyOrder("productId", "quantity", "unitPrice", "subtotal");
            assertThat(item.path("productId").isIntegralNumber()
                    && item.path("productId").asLong() == (i == 0 ? firstProduct : secondProduct)).isTrue();
            assertThat(item.path("quantity").isIntegralNumber() && item.path("quantity").asInt() == (i == 0 ? 2 : 3)).isTrue();
            assertThat(item.path("unitPrice").isTextual()
                    && item.path("unitPrice").asText().equals(i == 0 ? "10.00" : "7.50")).isTrue();
            assertThat(item.path("subtotal").isTextual()
                    && item.path("subtotal").asText().equals(i == 0 ? "20.00" : "22.50")).isTrue();
        }
        long orderId = response.path("id").asLong();
        assertThat(after.orders()).containsExactly(new OrderRow(orderId, buyer, created, "WAITING_PAYMENT"));
        assertThat(after.items()).hasSize(2);
        var total = BigDecimal.ZERO;
        for (int i = 0; i < 2; i++) {
            var item = after.items().get(i);
            assertThat(item.id()).isPositive();
            assertThat(item.orderId()).isEqualTo(orderId);
            assertThat(item.productId()).isEqualTo(i == 0 ? firstProduct : secondProduct);
            assertThat(item.position()).isEqualTo(i);
            assertThat(item.quantity()).isEqualTo(i == 0 ? 2 : 3);
            assertThat(item.unitPrice()).isEqualByComparingTo(i == 0 ? "10.00" : "7.50");
            total = total.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        assertThat(total).isEqualByComparingTo("42.50");
    }

    private DatabaseState snapshot() {
        return independent.execute((ConnectionCallback<DatabaseState>) connection -> {
            var orders = new ArrayList<OrderRow>();
            var items = new ArrayList<ItemRow>();
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "select id, buyer_id, created_at, status from tb_order order by id")) {
                while (rows.next()) orders.add(new OrderRow(rows.getLong("id"), rows.getLong("buyer_id"),
                        rows.getObject("created_at", OffsetDateTime.class).toInstant(), rows.getString("status")));
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "select id, order_id, product_id, position, quantity, unit_price from tb_order_item order by order_id, position")) {
                while (rows.next()) items.add(new ItemRow(rows.getLong("id"), rows.getLong("order_id"),
                        rows.getLong("product_id"), rows.getInt("position"), rows.getInt("quantity"), rows.getBigDecimal("unit_price")));
            }
            return new DatabaseState(List.copyOf(orders), List.copyOf(items), connection.getAutoCommit(),
                    DataSourceUtils.isConnectionTransactional(connection, jdbc.getDataSource()));
        });
    }

    private static Set<String> fields(JsonNode value) {
        assertThat(value.isObject()).isTrue();
        var fields = new HashSet<String>();
        value.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static void assertOutsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    record OrderRow(long id, long buyerId, Instant createdAt, String status) { }
    record ItemRow(long id, long orderId, long productId, int position, int quantity, BigDecimal unitPrice) { }
    record DatabaseState(List<OrderRow> orders, List<ItemRow> items, boolean autoCommit,
                         boolean boundToApplicationTransaction) { }

    enum Suffix {
        OBJECT(" {}", false), ARRAY(" []", false), NULL(" null", false),
        SCALAR(" 42", false), INVALID_TEXT(" not-json", false),
        SPACES("   ", true), TABS("\t\t", true), LINE_BREAKS("\r\n\n", true), MIXED_WHITESPACE(" \t\r\n ", true);

        final String raw;
        final boolean whitespaceOnly;

        Suffix(String raw, boolean whitespaceOnly) {
            this.raw = raw;
            this.whitespaceOnly = whitespaceOnly;
        }
    }
}
