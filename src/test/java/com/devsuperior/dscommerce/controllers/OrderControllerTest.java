package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract first, with existing components only; no stand-in order controller. */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc(addFilters = true, print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import(OrderTestConfig.class)
class OrderControllerTest extends OrderHttpTestSupport {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @Test
    void shouldReturn201WithExactlyTheOrderAndItemContractFields() throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var second = fixture.createProduct("http-second", new BigDecimal("7.50"));
        var first = fixture.createProduct("http-first", new BigDecimal("10.00"));
        var body = """
                {"items":[{"productId":%d,"quantity":2},{"productId":%d,"quantity":3}]}
                """.formatted(first, second);

        assertConfirmation(account.token(), body, account.id(),
                List.of(new ExpectedLine(first, 2, "10.00", "20.00"),
                        new ExpectedLine(second, 3, "7.50", "22.50")), "42.50");
    }

    @Test
    void shouldReturnDecimalCapacityAsTextWithoutExponentOrNumericCoercion() throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var product = fixture.createProduct("http-decimal-capacity", new BigDecimal("99999999999999999.99"));
        var body = """
                {"items":[{"productId":%d,"quantity":1}]}
                """.formatted(product);

        assertConfirmation(account.token(), body, account.id(),
                List.of(new ExpectedLine(product, 1, "99999999999999999.99", "99999999999999999.99")),
                "99999999999999999.99");
    }

    @Test
    void shouldReturnTwoDecimalPlacesForUnitPriceSubtotalAndTotal() throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var product = fixture.createProduct("http-tenths", new BigDecimal("0.10"));
        var body = """
                {"items":[{"productId":%d,"quantity":3}]}
                """.formatted(product);

        assertConfirmation(account.token(), body, account.id(),
                List.of(new ExpectedLine(product, 3, "0.10", "0.30")), "0.30");
    }
}

/** Shared preparation/assertions for the two T015 classes; never registers an order component. */
abstract class OrderHttpTestSupport {

    private static final String PASSWORD = "order-http-test-password";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected OrderTestFixture fixture;
    @Autowired protected OrderTestConfig.ControlledClock clock;
    @Autowired protected JwtDecoder decoder;
    @Autowired protected FilterChainProxy securityFilters;
    @Autowired protected JdbcTemplate jdbc;
    protected final Set<Long> ownedAccounts = new LinkedHashSet<>();

    @BeforeEach
    void prepareExistingContext() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(securityFilters.getFilters("/login")).isNotEmpty();
        assertThat(securityFilters.getFilters("/orders")).isNotEmpty();
        clock.setInstant(Instant.now());
    }

    @AfterEach
    void cleanOwnedFixturesWithoutRequiringOrderSchema() {
        if (tableExists("TB_ORDER")) {
            for (var accountId : ownedAccounts) {
                if (tableExists("TB_ORDER_ITEM")) {
                    jdbc.update("delete from tb_order_item where order_id in "
                            + "(select id from tb_order where buyer_id = ?)", accountId);
                }
                jdbc.update("delete from tb_order where buyer_id = ?", accountId);
            }
        }
        fixture.cleanUp();
        ownedAccounts.clear();
    }

    protected LoggedAccount login(Set<UserRole> roles) throws Exception {
        var name = "order-http-" + UUID.randomUUID();
        var id = fixture.createAccount(name, PASSWORD, roles);
        ownedAccounts.add(id);
        assertDatabaseRoles(id, roles);
        var body = """
                {"name":"%s","password":"%s"}
                """.formatted(name, PASSWORD);
        var result = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        var response = mapper.readTree(result.getResponse().getContentAsString());
        assertFields(response, "accessToken", "tokenType");
        assertThat(response.path("tokenType").textValue()).isEqualTo("Bearer");
        // Never assert/print a token value or a complete Jwt object on failure.
        assertThat(response.path("accessToken").isTextual()).isTrue();
        var token = response.path("accessToken").textValue();
        assertThat(token.isBlank()).isFalse();
        var decoded = decodeWithoutLeakingToken(token);
        assertThat(decoded.getSubject()).isEqualTo(Long.toString(id));
        assertThat(decoded.getClaims().containsKey("roles")).isFalse();
        clock.setInstant(decoded.getIssuedAt().plus(Duration.ofMillis(123)));
        return new LoggedAccount(id, name, token, decoded);
    }

    protected Jwt decodeWithoutLeakingToken(String token) {
        try {
            return decoder.decode(token);
        } catch (JwtException failure) {
            // Decoder failures are preparation failures, never RED; keep diagnostics token-free.
            throw new AssertionError("Real decoder rejected the test JWT: " + failure.getClass().getSimpleName());
        }
    }

    protected void assertDatabaseRoles(long id, Set<UserRole> expected) {
        assertThat(jdbc.queryForList("select role from tb_user_account_role where user_account_id = ?",
                String.class, id)).containsExactlyInAnyOrderElementsOf(expected.stream().map(Enum::name).toList());
    }

    protected JsonNode assertConfirmation(String token, String rawBody, long accountId,
                                           List<ExpectedLine> expectedItems, String total) throws Exception {
        var result = mvc.perform(post("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(rawBody))
                // Initial RED must occur here, before response mapping or any order SQL assertion.
                .andExpect(status().isCreated()).andReturn();
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        var response = mapper.readTree(result.getResponse().getContentAsString());
        assertFields(response, "id", "accountId", "createdAt", "status", "items", "total");
        assertPositiveInt64(response.path("id"));
        assertPositiveInt64(response.path("accountId"));
        assertThat(response.path("accountId").longValue()).isEqualTo(accountId);
        assertThat(response.path("createdAt").isTextual()).isTrue();
        assertThat(response.path("createdAt").textValue())
                .matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z");
        assertThat(Instant.parse(response.path("createdAt").textValue()))
                .isEqualTo(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThat(response.path("status").textValue()).isEqualTo("WAITING_PAYMENT");
        assertThat(response.path("items").isArray()).isTrue();
        assertThat(response.path("items").size()).isEqualTo(expectedItems.size());
        for (var position = 0; position < expectedItems.size(); position++) {
            var actual = response.path("items").get(position);
            var expected = expectedItems.get(position);
            assertFields(actual, "productId", "quantity", "unitPrice", "subtotal");
            assertPositiveInt64(actual.path("productId"));
            assertThat(actual.path("productId").longValue()).isEqualTo(expected.productId());
            assertThat(actual.path("quantity").isIntegralNumber()).isTrue();
            assertThat(actual.path("quantity").canConvertToInt()).isTrue();
            assertThat(actual.path("quantity").intValue()).isEqualTo(expected.quantity());
            assertMoney(actual.path("unitPrice"), expected.unitPrice());
            assertMoney(actual.path("subtotal"), expected.subtotal());
        }
        assertMoney(response.path("total"), total);
        return response;
    }

    private boolean tableExists(String name) {
        return Integer.valueOf(1).equals(jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema = 'PUBLIC' and table_name = ?", Integer.class, name));
    }

    private static void assertFields(JsonNode value, String... expected) {
        assertThat(value.isObject()).isTrue();
        var fields = new HashSet<String>();
        value.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(expected);
    }

    private static void assertPositiveInt64(JsonNode value) {
        assertThat(value.isIntegralNumber()).isTrue();
        assertThat(value.canConvertToLong()).isTrue();
        assertThat(value.longValue()).isPositive();
    }

    private static void assertMoney(JsonNode value, String expected) {
        assertThat(value.isTextual()).isTrue();
        assertThat(value.textValue()).matches("(?:0|[1-9][0-9]{0,16})\\.[0-9]{2}").isEqualTo(expected);
    }

    protected record ExpectedLine(long productId, int quantity, String unitPrice, String subtotal) { }

    protected record LoggedAccount(long id, String name, String token, Jwt decoded) {
        @Override
        public String toString() {
            return "LoggedAccount[id=" + id + ", token=<redacted>]";
        }
    }
}
