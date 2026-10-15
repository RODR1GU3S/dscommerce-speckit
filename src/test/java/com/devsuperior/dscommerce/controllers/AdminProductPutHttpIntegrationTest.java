package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.AdminProductDetailDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductCategory;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.services.ProductMaintenanceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
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
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** T050: HTTP wiring only; late guards, races and the consolidated US3 checkpoint remain separate. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
class AdminProductPutHttpIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private ProductMaintenanceService maintenance;
    @Autowired private ProductRepository products;
    private AdminProductTestFixture fixture;
    private AdminProductDatabaseProbe database;
    private MockMvc mvc;
    private String token;
    private long target;
    private long oldCategory;
    private long newCategory;
    private Advised serviceProxy;
    private Advised repositoryProxy;
    private MethodInterceptor serviceObserver;
    private MethodInterceptor flushObserver;
    private int entries;
    private int returns;
    private int flushes;
    private int completion;
    private Snapshot before;
    private Snapshot atFlush;
    private Snapshot afterProxy;

    @BeforeEach
    void prepareIsolatedCommittedDataAndObserveTheRealProxy() {
        outsideTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmpty();
        fixture = new AdminProductTestFixture(context);
        token = fixture.login(fixture.createAccount("T050-admin", "Disposable-T050", Set.of(UserRole.ADMIN)));
        oldCategory = fixture.createCategory("Antiga").id();
        newCategory = fixture.createCategory("Nova").id();
        target = fixture.createProduct("Produto original", "Descricao original completa",
                "https://example.invalid/original.jpg", new BigDecimal("10.00")).id();
        fixture.associate(target, oldCategory);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        assertThat(maintenance).isInstanceOf(Advised.class);
        assertThat(products).isInstanceOf(Advised.class);
        serviceProxy = (Advised) maintenance;
        repositoryProxy = (Advised) products;
        assertThat(serviceProxy.getAdvisors()).anyMatch(a -> a.getAdvice() instanceof TransactionInterceptor);
        entries = returns = flushes = 0;
        completion = -1;
        before = atFlush = afterProxy = null;
        serviceObserver = invocation -> {
            if (!invocation.getMethod().getName().equals("replaceProduct")) return invocation.proceed();
            entries++;
            outsideTransaction();
            var detail = (AdminProductDetailDTO) invocation.proceed();
            outsideTransaction();
            // This runs outside TransactionInterceptor, before control returns to the HTTP controller.
            afterProxy = database.snapshot();
            assertThat(completion).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
            assertThat(afterProxy.products()).anySatisfy(row -> {
                assertThat(row.id()).isEqualTo(detail.id());
                assertThat(row.name()).isEqualTo(detail.name());
                assertThat(row.description()).isEqualTo(detail.description());
                assertThat(row.image()).isEqualTo(detail.image());
                assertThat(row.price()).isEqualTo(new BigDecimal(detail.price()));
            });
            returns++;
            return detail;
        };
        flushObserver = invocation -> {
            if (!invocation.getMethod().getName().equals("flush")) return invocation.proceed();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { completion = status; }
            });
            var result = invocation.proceed();
            flushes++;
            atFlush = database.snapshot();
            return result;
        };
        serviceProxy.addAdvice(0, serviceObserver);
        repositoryProxy.addAdvice(0, flushObserver);
    }

    @AfterEach
    void cleanOwnedRowsAfterIndependentAssertions() throws Exception {
        outsideTransaction();
        if (serviceProxy != null && serviceObserver != null) serviceProxy.removeAdvice(serviceObserver);
        if (repositoryProxy != null && flushObserver != null) repositoryProxy.removeAdvice(flushObserver);
        if (fixture == null) return;
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var items = connection.prepareStatement("delete from tb_order_item where order_id = ?");
                 var orders = connection.prepareStatement("delete from tb_order where id = ?");
                 var pairs = connection.prepareStatement("delete from tb_product_category where product_id = ?")) {
                // Only this isolated scenario's buyer can own orders; no shared rows are removed.
                for (var order : database.snapshot().orders()) {
                    items.setLong(1, order.id()); items.executeUpdate();
                    orders.setLong(1, order.id()); orders.executeUpdate();
                }
                pairs.setLong(1, target); pairs.executeUpdate();
                connection.commit();
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
        fixture.close();
        assertEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/api"})
    void completeReplacementIsCommittedBeforeControllerReceivesTheDetail(String prefix) throws Exception {
        var buyer = fixture.prepareBuyer();
        var order = mvc.perform(post("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.login(buyer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"productId\":" + target + ",\"quantity\":2}]}"))
                .andReturn();
        assertThat(order.getResponse().getStatus()).isEqualTo(201);
        var history = database.snapshot();
        assertThat(history.orders()).hasSize(1);
        assertThat(history.orders().get(0).buyerId()).isEqualTo(buyer.id());
        assertThat(history.items()).hasSize(1);
        var item = history.items().get(0);
        assertThat(item.orderId()).isEqualTo(history.orders().get(0).id());
        assertThat(item.productId()).isEqualTo(target);
        assertThat(item.position()).isZero();
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.unitPrice()).isEqualTo(new BigDecimal("10.00"));
        assertThat(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))).isEqualTo(new BigDecimal("20.00"));
        var result = perform(request(prefix, Long.toString(target), body()), "success", 200);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var expected = new ProductRow(target, "Produto  novo", "Descricao  nova completa",
                "https://example.invalid/new.jpg", new BigDecimal("12.00"));
        var expectedState = new Snapshot(List.of(expected), before.categories(),
                Set.of(new ProductCategory(target, newCategory)), before.orders(), before.items());
        assertThat(database.snapshot()).isEqualTo(expectedState);
        assertThat(afterProxy).isEqualTo(expectedState);
        assertThat(atFlush).as("flush is not commit: independent connection still sees original data").isEqualTo(before);
        assertThat(entries).isEqualTo(1); assertThat(returns).isEqualTo(1); assertThat(flushes).isEqualTo(1);
        assertThat(completion).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        var detail = json(result);
        fields(detail, "id", "name", "description", "image", "price", "categories");
        assertThat(detail.path("id").isIntegralNumber()).isTrue();
        assertThat(detail.path("id").longValue()).isEqualTo(target);
        assertThat(detail.path("name").asText()).isEqualTo(expected.name());
        assertThat(detail.path("description").asText()).isEqualTo(expected.description());
        assertThat(detail.path("image").asText()).isEqualTo(expected.image());
        assertThat(detail.path("price").isTextual()).isTrue();
        assertThat(detail.path("price").asText()).isEqualTo("12.00");
        assertThat(detail.path("categories")).hasSize(1);
        fields(detail.path("categories").get(0), "id", "name");
        assertThat(detail.path("categories").get(0).path("id").longValue()).isEqualTo(newCategory);
        assertThat(detail.path("categories").get(0).path("name").asText()).isEqualTo("Nova");
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "description", "price", "image", "categoryIds"})
    void everyFieldIsRequiredRatherThanImplicitlyRetained(String field) throws Exception {
        var input = body(); input.remove(field);
        var result = reject(request("", Long.toString(target), input), "omitted-" + field, 400, 0);
        diagnostics(result, field + ":REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/api"})
    void validationPrecedesMissingTargetAndValidBodyDoesNotUpsert(String prefix) throws Exception {
        var input = body().put("name", "ab").put("description", "short").put("price", "0").put("image", "/relative");
        input.putArray("categoryIds").add(Long.MAX_VALUE);
        var invalid = reject(request(prefix, Long.toString(Long.MAX_VALUE), input), "missing-invalid", 400, 0);
        diagnostics(invalid, "name:INVALID_LENGTH", "description:INVALID_LENGTH", "price:NOT_POSITIVE",
                "image:INVALID_IMAGE_URL", "categoryIds[0]:CATEGORY_NOT_FOUND");
        var missing = reject(request(prefix, Long.toString(Long.MAX_VALUE), body()), "missing-valid", 404, 1);
        diagnostics(missing);
    }

    @ParameterizedTest
    @CsvSource({"id,abc", "id,0", "id,9223372036854775808", "query,extra"})
    void existingParameterDiagnosticsAreReused(String kind, String value) throws Exception {
        var request = request("/api", kind.equals("id") ? value : Long.toString(target), body());
        if (kind.equals("query")) request.queryParam(value, "x", "y");
        var result = reject(request, "parameter-" + value, 400, 0);
        diagnostics(result, (kind.equals("id") ? "id" : "body") + ":INVALID_PARAMETER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "invalid", "client"})
    void realSecurityPrecedesMalformedBody(String kind) throws Exception {
        var request = put("/api/admin/products/" + target).contextPath("/api").servletPath("/admin/products/" + target)
                .contentType(MediaType.APPLICATION_JSON).content("{");
        if (kind.equals("invalid")) request.header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        if (kind.equals("client")) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.login(fixture.prepareBuyer()));
        var result = reject(request, "security-" + kind, kind.equals("client") ? 403 : 401, 0);
        diagnostics(result);
        if (!kind.equals("client")) assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"media", "accept", "patch", "broken"})
    void nativeHttpAndBodyErrorsRetainAdministrativeRepresentation(String kind) throws Exception {
        var request = request("/api", Long.toString(target), body());
        int expected = switch (kind) { case "media" -> 415; case "accept" -> 406; case "patch" -> 405; default -> 400; };
        if (kind.equals("media")) request.contentType(MediaType.TEXT_PLAIN);
        if (kind.equals("accept")) request.accept(MediaType.TEXT_PLAIN);
        if (kind.equals("broken")) request.content("{");
        if (kind.equals("patch")) request = patch("/api/admin/products/" + target).contextPath("/api")
                .servletPath("/admin/products/" + target).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body()));
        if (kind.equals("accept")) {
            var result = perform(request, kind, expected);
            assertThat(result.getResponse().getStatus()).isEqualTo(406);
            assertThat(database.snapshot()).isEqualTo(before);
            assertThat(entries).isZero(); assertThat(returns).isZero(); assertThat(flushes).isZero();
            // The contract qualifies the envelope when negotiation prevents writing JSON.
            assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
            assertThat(result.getResponse().getContentType()).isNull();
            return;
        }
        var result = reject(request, kind, expected, 0);
        diagnostics(result, kind.equals("broken") ? new String[]{"body:INVALID_BODY"} : new String[]{});
        if (kind.equals("patch")) assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).contains("PUT").doesNotContain("PATCH", "DELETE");
        if (kind.equals("media")) assertThat(result.getResponse().getHeader(HttpHeaders.ACCEPT)).contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private MvcResult reject(MockHttpServletRequestBuilder request, String label, int status, int expectedEntries) throws Exception {
        var result = perform(request, label, status);
        assertThat(database.snapshot()).isEqualTo(before);
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(entries).isEqualTo(expectedEntries); assertThat(returns).isZero(); assertThat(flushes).isZero();
        var error = json(result);
        fields(error, "status", "error", "message", "path", "errors");
        assertThat(error.path("status").asInt()).isEqualTo(status);
        assertThat(error.path("error").asText()).isEqualTo(HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(error.path("path").asText()).isEqualTo(result.getRequest().getRequestURI());
        assertThat(error.path("errors").isArray()).isTrue();
        assertThat(error.path("message").asText()).isEqualTo(switch (status) {
            case 400 -> "Invalid product maintenance data"; case 401 -> "Authentication required or invalid";
            case 403 -> "Product maintenance not permitted"; case 404 -> "Product not found";
            default -> "Product maintenance request not supported";
        });
        return result;
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String label, int expected) throws Exception {
        outsideTransaction();
        before = database.snapshot();
        var accounts = database.accountIds();
        var result = mvc.perform(request).andReturn();
        outsideTransaction();
        var after = database.snapshot();
        assertThat(database.accountIds()).isEqualTo(accounts);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        System.out.printf("T050_HTTP case=%s expected=%d actual=%d entries=%d returns=%d flushes=%d completion=%d snapshotEqual=%s cause=%s%n",
                label, expected, result.getResponse().getStatus(), entries, returns, flushes, completion, before.equals(after),
                result.getResolvedException() == null ? "none" : result.getResolvedException().getClass().getSimpleName());
        return result;
    }
    private MockHttpServletRequestBuilder request(String prefix, String id, ObjectNode input) throws Exception {
        String route = "/admin/products/" + id;
        return put(prefix + route).contextPath(prefix).servletPath(route).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(input));
    }
    private ObjectNode body() {
        var input = mapper.createObjectNode().put("name", "\u2003Produto  novo\u00a0").put("description", " Descricao  nova completa ")
                .put("price", "12.000").put("image", "https://example.invalid/new.jpg");
        input.putArray("categoryIds").add(newCategory).add(newCategory);
        return input;
    }
    private JsonNode json(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
    private void diagnostics(MvcResult result, String... expected) throws Exception {
        var actual = new ArrayList<String>();
        for (var error : json(result).path("errors")) {
            fields(error, "field", "code", "message");
            assertThat(error.path("message").asText()).isNotBlank().doesNotContain(Long.toString(Long.MAX_VALUE));
            actual.add(error.path("field").asText() + ":" + error.path("code").asText());
        }
        assertThat(actual).containsExactly(expected);
    }
    private static void fields(JsonNode node, String... expected) {
        var actual = new ArrayList<String>(); node.fieldNames().forEachRemaining(actual::add);
        assertThat(actual).containsExactlyInAnyOrder(expected);
    }
    private void assertEmpty() {
        var state = database.snapshot();
        assertThat(state.products()).isEmpty(); assertThat(state.categories()).isEmpty();
        assertThat(state.associations()).isEmpty(); assertThat(state.orders()).isEmpty();
        assertThat(state.items()).isEmpty(); assertThat(database.accountIds()).isEmpty();
    }
    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
}
