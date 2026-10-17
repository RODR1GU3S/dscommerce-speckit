package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** T006 RED: full application and real security; no administrative implementation or mocked JWT. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
class AdminProductSecurityChainTest {

    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;

    private final BodyAccessProbe body = new BodyAccessProbe();
    private final List<Long> ownedOrders = new ArrayList<>();
    private AdminProductDatabaseProbe probe;
    private AdminProductTestFixture fixture;
    private AdminProductTestFixture.Account client;
    private String clientToken;
    private MockMvc mvc;

    @BeforeEach
    void prepareCommittedClientInTheExplicitSeedFreeContext() {
        assertNoCallerTransaction();
        probe = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        client = fixture.prepareBuyer();
        assertThat(probe.accountIds()).containsExactly(client.id());
        assertThat(probe.roles(client.id())).containsExactly(UserRole.CLIENT);
        clientToken = fixture.login(client);
        assertThat(context.getBean(JwtDecoder.class).decode(clientToken).getSubject())
                .isEqualTo(Long.toString(client.id()));
        // The observer wraps the request before the actual production FilterChainProxy.
        // No printer, replacement security chain or automatic component registration is used.
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(body, security).build();
        body.reset();
    }

    @AfterEach
    void cleanOnlyTheFixtureAndOrdersOwnedByThisCaseAfterAssertions() {
        assertNoCallerTransaction();
        if (fixture != null) {
            var cleanup = new TransactionTemplate(transactions);
            cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            cleanup.executeWithoutResult(status -> ownedOrders.forEach(id -> {
                jdbc.update("delete from tb_order_item where order_id = ?", id);
                jdbc.update("delete from tb_order where id = ?", id);
            }));
            fixture.close();
            assertEmptyDatabase();
        }
    }

    @ParameterizedTest(name = "admin {0}: {1} {2}, malformed JSON")
    @MethodSource("administrativeRequests")
    void shouldRejectAdministrativeRequestBeforeReadingItsMalformedBody(
            Identity identity, HttpMethod method, String path) throws Exception {
        assertAdministrativeRejection(identity, method, path, BodyCase.MALFORMED_JSON);
    }

    static Stream<Arguments> administrativeRequests() {
        return Stream.of(Identity.values()).flatMap(identity -> Stream.of(
                "/admin/products", "/admin/products/1", "/admin/categories",
                "/admin/products/1/unsupported", "/admin/categories/1", "/admin/unsupported")
                .flatMap(path -> Stream.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                        HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.HEAD, HttpMethod.OPTIONS)
                        .map(method -> Arguments.of(identity, method, path))));
    }

    @ParameterizedTest(name = "admin {0}: {1} {2}, body={3}")
    @MethodSource("otherWriteBodies")
    void shouldKeepSecurityPrecedenceForOtherWriteBodies(
            Identity identity, HttpMethod method, String path, BodyCase input) throws Exception {
        assertAdministrativeRejection(identity, method, path, input);
    }

    static Stream<Arguments> otherWriteBodies() {
        return Stream.of(Identity.values()).flatMap(identity -> Stream.of(
                BodyCase.WELL_FORMED_JSON, BodyCase.EMPTY, BodyCase.UNSUPPORTED_MEDIA)
                .flatMap(input -> Stream.of(
                        Arguments.of(identity, HttpMethod.POST, "/admin/products", input),
                        Arguments.of(identity, HttpMethod.PUT, "/admin/products/1", input))));
    }

    private void assertAdministrativeRejection(Identity identity, HttpMethod method, String path,
                                               BodyCase input) throws Exception {
        assertNoCallerTransaction();
        var before = probe.snapshot();
        var accountsBefore = probe.accountIds();
        var call = request(method, path).servletPath(path).contentType(input.mediaType).content(input.content);
        if (identity == Identity.CLIENT) {
            call.header("Authorization", "Bearer " + clientToken);
        }
        body.reset();
        var result = mvc.perform(call).andReturn();
        assertNoCallerTransaction();
        assertAll(identity + " " + method + " " + path + " " + input,
                () -> assertThat(body.invoked).as("observer must wrap the real request").isTrue(),
                () -> assertThat(result.getResponse().getStatus()).as("administrative authorization status")
                        .isEqualTo(identity.status),
                () -> assertThat(body.accesses.get()).as("security must precede body access").isZero(),
                () -> assertThat(result.getHandler()).as("security rejection must precede MVC dispatch").isNull(),
                () -> assertThat(probe.snapshot()).as("independent five-table snapshot after response").isEqualTo(before),
                () -> assertThat(probe.accountIds()).isEqualTo(accountsBefore),
                () -> assertThat(probe.roles(client.id())).containsExactly(UserRole.CLIENT));
    }

    @ParameterizedTest(name = "public detail={0}, invalid Bearer={1}")
    @MethodSource("publicRequests")
    void shouldKeepPublicGetsAnonymousAndIgnoreAnIrrelevantInvalidBearer(
            boolean detail, boolean invalidBearer) throws Exception {
        var product = fixture.createProduct("Public control", "Public contract control",
                "https://example.invalid/public-control.jpg", new BigDecimal("10.00"));
        var category = fixture.createCategory("Public category");
        fixture.associate(product.id(), category.id());
        var before = probe.snapshot();
        var path = detail ? "/products/" + product.id() : "/products";
        var call = get(path).servletPath(path);
        if (invalidBearer) {
            call.header("Authorization", "Bearer deliberately-invalid");
        }
        body.reset();
        var result = mvc.perform(call).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var response = mapper.readTree(result.getResponse().getContentAsByteArray());
        var item = detail ? response : response.get("content").get(0);
        assertThat(item.get("id").asLong()).isEqualTo(product.id());
        assertThat(item.get("price").isNumber()).isTrue();
        assertThat(item.get("price").decimalValue()).isEqualByComparingTo("10.00");
        if (detail) {
            assertThat(item.get("categories").get(0).asText()).isEqualTo(category.name());
        } else {
            assertThat(response.get("totalElements").asLong()).isEqualTo(1);
        }
        assertThat(selectedFilters("GET", path)).noneMatch(BearerTokenAuthenticationFilter.class::isInstance);
        assertThat(body.invoked).isTrue();
        assertThat(body.accesses.get()).isZero();
        assertThat(probe.snapshot()).isEqualTo(before);
        assertNoCallerTransaction();
    }

    static Stream<Arguments> publicRequests() {
        return Stream.of(false, true).flatMap(detail -> Stream.of(false, true)
                .map(invalid -> Arguments.of(detail, invalid)));
    }

    @Test
    void shouldKeepAnonymousOrderRejectionBeforeBodyAccess() throws Exception {
        var before = probe.snapshot();
        body.reset();
        var result = mvc.perform(post("/orders").servletPath("/orders")
                .contentType(MediaType.APPLICATION_JSON).content("{")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(body.invoked).isTrue();
        assertThat(body.accesses.get()).isZero();
        assertThat(result.getHandler()).isNull();
        assertThat(selectedFilters("POST", "/orders")).anyMatch(BearerTokenAuthenticationFilter.class::isInstance);
        assertThat(probe.snapshot()).isEqualTo(before);
        assertNoCallerTransaction();
    }

    @Test
    void shouldKeepRealClientOrderConfirmationVisibleIndependently() throws Exception {
        var product = fixture.createProduct("Order control", "Existing order contract control",
                "https://example.invalid/order-control.jpg", new BigDecimal("10.00"));
        body.reset();
        var result = mvc.perform(post("/orders").servletPath("/orders")
                .header("Authorization", "Bearer " + clientToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + product.id() + ",\"quantity\":2}]}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var orderId = mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asLong();
        ownedOrders.add(orderId);
        assertNoCallerTransaction();
        assertThat(selectedFilters("POST", "/orders")).anyMatch(BearerTokenAuthenticationFilter.class::isInstance);
        assertThat(body.invoked).isTrue();
        assertThat(body.accesses.get()).isPositive();
        var snapshot = probe.snapshot();
        assertThat(snapshot.orders()).hasSize(1);
        assertThat(snapshot.orders().get(0).id()).isEqualTo(orderId);
        assertThat(snapshot.orders().get(0).buyerId()).isEqualTo(client.id());
        assertThat(snapshot.items()).hasSize(1);
        var item = snapshot.items().get(0);
        assertThat(item.orderId()).isEqualTo(orderId);
        assertThat(item.productId()).isEqualTo(product.id());
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.unitPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldObserveBodyAccessWhenAuthenticatedOrderReachesExistingMvcValidation() throws Exception {
        var before = probe.snapshot();
        body.reset();
        var result = mvc.perform(post("/orders").servletPath("/orders")
                .header("Authorization", "Bearer " + clientToken)
                .contentType(MediaType.APPLICATION_JSON).content("{")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body.invoked).isTrue();
        assertThat(body.accesses.get()).isPositive();
        assertThat(result.getHandler()).isNotNull();
        assertThat(probe.snapshot()).isEqualTo(before);
        assertNoCallerTransaction();
    }

    private List<Filter> selectedFilters(String method, String path) {
        var call = new MockHttpServletRequest(method, path);
        call.setServletPath(path);
        return security.getFilterChains().stream().filter(chain -> chain.matches(call)).findFirst()
                .orElseThrow().getFilters();
    }

    private static void assertNoCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    private void assertEmptyDatabase() {
        var snapshot = probe.snapshot();
        assertThat(snapshot.products()).isEmpty();
        assertThat(snapshot.categories()).isEmpty();
        assertThat(snapshot.associations()).isEmpty();
        assertThat(snapshot.orders()).isEmpty();
        assertThat(snapshot.items()).isEmpty();
        assertThat(probe.accountIds()).isEmpty();
    }

    enum Identity {
        ANONYMOUS(401), CLIENT(403);
        final int status;
        Identity(int status) { this.status = status; }
    }

    enum BodyCase {
        MALFORMED_JSON(MediaType.APPLICATION_JSON_VALUE, "{"),
        WELL_FORMED_JSON(MediaType.APPLICATION_JSON_VALUE,
                "{\"name\":\"Product\",\"description\":\"Product description\",\"price\":\"10.00\","
                        + "\"image\":\"https://example.invalid/product.jpg\",\"categoryIds\":[1]}"),
        EMPTY(MediaType.APPLICATION_JSON_VALUE, ""),
        UNSUPPORTED_MEDIA(MediaType.TEXT_PLAIN_VALUE, "unexamined product body");
        final String mediaType;
        final String content;
        BodyCase(String mediaType, String content) { this.mediaType = mediaType; this.content = content; }
    }

    /** Counts access to body APIs without reading/logging bytes or changing their behavior. */
    private static final class BodyAccessProbe implements Filter {
        private final AtomicInteger accesses = new AtomicInteger();
        private boolean invoked;

        void reset() { accesses.set(0); invoked = false; }

        @Override
        public void doFilter(ServletRequest input, ServletResponse output, FilterChain chain)
                throws IOException, ServletException {
            invoked = true;
            chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest) input) {
                @Override
                public ServletInputStream getInputStream() throws IOException {
                    accesses.incrementAndGet();
                    return super.getInputStream();
                }

                @Override
                public BufferedReader getReader() throws IOException {
                    accesses.incrementAndGet();
                    return super.getReader();
                }
            }, output);
        }
    }
}
