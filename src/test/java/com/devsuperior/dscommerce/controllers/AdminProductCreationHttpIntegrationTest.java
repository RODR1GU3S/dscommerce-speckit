package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.AdminFieldErrorDTO;
import com.devsuperior.dscommerce.dto.AdminProductDetailDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.services.ProductMaintenanceService;
import com.devsuperior.dscommerce.services.exceptions.InvalidProductInputException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** T037 only: HTTP wiring, boundary validation and success after the real transactional proxy. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductCreationHttpIntegrationTest.SqlConfig.class)
class AdminProductCreationHttpIntegrationTest {
    private static final String ROUTE = "/admin/products";
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private ProductMaintenanceService maintenance;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;
    private String adminToken;
    private List<AdminProductTestFixture.CategoryData> categories;
    private Advised proxy;
    private MethodInterceptor observer;
    private int entries;
    private int committedReturns;
    private boolean rejectWithServiceDiagnostic;

    @BeforeEach
    void prepareRealLoginAndCommittedFixtureWithoutSeedsOrCallerTransaction() {
        noCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertThat(database.snapshot().products()).isEmpty();
        assertThat(database.snapshot().categories()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
        fixture = new AdminProductTestFixture(context);
        adminToken = login(Set.of(UserRole.ADMIN));
        categories = fixture.prepareCategorySelection().sameName();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        assertThat(maintenance).isInstanceOf(Advised.class);
        proxy = (Advised) maintenance;
        assertThat(proxy.getAdvisors()).anyMatch(advisor -> advisor.getAdvice() instanceof TransactionInterceptor);
        entries = 0;
        committedReturns = 0;
        rejectWithServiceDiagnostic = false;
        observer = invocation -> {
            if (!invocation.getMethod().getName().equals("createProduct")) return invocation.proceed();
            entries++;
            noCallerTransaction();
            // A test-only diagnostic checks HTTP exception mapping, without simulating a reference race.
            if (rejectWithServiceDiagnostic) throw new InvalidProductInputException(List.of(
                    new AdminFieldErrorDTO("categoryIds[1]", "CATEGORY_NOT_FOUND", "Category does not exist"),
                    new AdminFieldErrorDTO("name", "INVALID_LENGTH", "Invalid name length")));
            var response = (AdminProductDetailDTO) invocation.proceed();
            noCallerTransaction();
            assertThat(database.snapshot().products()).anySatisfy(product -> {
                assertThat(product.id()).isEqualTo(response.id());
                assertThat(product.name()).isEqualTo(response.name());
                assertThat(product.description()).isEqualTo(response.description());
                assertThat(product.image()).isEqualTo(response.image());
                assertThat(product.price()).isEqualTo(new BigDecimal(response.price()));
            });
            committedReturns++;
            return response;
        };
        // Run outside the original TransactionInterceptor, before the controller can build its response.
        proxy.addAdvice(0, observer);
        sql.clear();
    }

    @AfterEach
    void cleanOnlyOwnedRequestRowsAfterAssertions() throws Exception {
        noCallerTransaction();
        if (proxy != null && observer != null) proxy.removeAdvice(observer);
        if (fixture == null) return;
        // This isolated context starts without products; every product here belongs to this test's POST.
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var pairs = connection.prepareStatement("delete from tb_product_category where product_id = ?");
                 var products = connection.prepareStatement("delete from tb_product where id = ?")) {
                for (var product : database.snapshot().products()) {
                    pairs.setLong(1, product.id());
                    pairs.executeUpdate();
                    products.setLong(1, product.id());
                    products.executeUpdate();
                }
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
        fixture.close();
        assertThat(database.snapshot().products()).isEmpty();
        assertThat(database.snapshot().categories()).isEmpty();
        assertThat(database.snapshot().associations()).isEmpty();
        assertThat(database.snapshot().orders()).isEmpty();
        assertThat(database.snapshot().items()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"ROOT,false", "ROOT,true", "/api,false", "/api,true"})
    void successUsesCommittedServiceValuesAndLocationOfTheRealGet(String external, boolean both) throws Exception {
        String prefix = external.equals("ROOT") ? "" : external;
        String token = both ? login(Set.of(UserRole.ADMIN, UserRole.CLIENT)) : adminToken;
        var before = database.snapshot();
        var result = mvc.perform(request(prefix, validBody()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var body = json(result);
        long id = body.path("id").longValue();
        assertThat(id).isPositive();
        var expected = mapper.createObjectNode().put("id", id).put("name", "Fone  Azul")
                .put("description", "Descricao  completa").put("price", "1.00")
                .put("image", "https://example.invalid/new.jpg");
        var options = expected.putArray("categories");
        categories.forEach(category -> options.addObject().put("id", category.id()).put("name", category.name()));
        assertThat(body).isEqualTo(mapper.readTree(expected.toString()));
        var location = URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION));
        assertThat(location.getPath()).isEqualTo(prefix + ROUTE + "/" + id);
        assertThat(location.getQuery()).isNull();
        assertThat(location.getFragment()).isNull();
        assertThat(entries).isEqualTo(1);
        assertThat(committedReturns).isEqualTo(1);
        var after = database.snapshot();
        assertThat(after.products()).containsExactly(new AdminProductDatabaseProbe.ProductRow(id,
                "Fone  Azul", "Descricao  completa", "https://example.invalid/new.jpg", new BigDecimal("1.00")));
        assertThat(after.associations()).containsExactlyInAnyOrder(
                new AdminProductTestFixture.ProductCategory(id, categories.get(0).id()),
                new AdminProductTestFixture.ProductCategory(id, categories.get(1).id()));
        assertThat(after.categories()).isEqualTo(before.categories());
        assertThat(after.orders()).isEqualTo(before.orders());
        assertThat(after.items()).isEqualTo(before.items());
        assertThat(sql.events()).filteredOn(event -> dml(event.sql())).isNotEmpty()
                .allMatch(AdminProductDatabaseProbe.SqlEvent::transactionActive);
        var detailRoute = ROUTE + "/" + id;
        var read = mvc.perform(get(location.getPath()).contextPath(prefix).servletPath(detailRoute)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
        assertThat(read.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(read)).isEqualTo(body);
        assertThat(database.snapshot()).isEqualTo(after);
        System.out.printf("T037_COMMITTED context=%s both=%s productDelta=1 uniquePairs=2 proxyReturns=1 independentRead=true%n", prefix, both);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void securityPrecedesMalformedBodyAndUnsupportedMediaType(boolean client, boolean wrongMedia) throws Exception {
        var input = request("/api", "{");
        if (wrongMedia) input.contentType(MediaType.TEXT_PLAIN);
        if (client) input.header(HttpHeaders.AUTHORIZATION, "Bearer " + login(Set.of(UserRole.CLIENT)));
        int status = client ? 403 : 401;
        var result = rejected(input, status, 0);
        assertEnvelope(result, status, client ? "Product maintenance not permitted" : "Authentication required or invalid");
        assertThat(json(result).path("errors")).isEmpty();
        assertThat(result.getHandler()).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(client ? null : "Bearer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"aggregate", "type", "body"})
    void inputDiagnosticsStopBeforeMaintenance(String kind) throws Exception {
        String input = switch (kind) {
            case "aggregate" -> "{\"name\":\"ab\",\"description\":\"123456789\",\"price\":\"0\",\"image\":\"relative\",\"categoryIds\":[9223372036854775807]}";
            case "type" -> validBody().replace("\"1.000\"", "1");
            default -> "{";
        };
        var result = rejected(authorized(input), 400, 0);
        assertEnvelope(result, 400, "Invalid product maintenance data");
        var fields = new java.util.ArrayList<String>();
        json(result).path("errors").forEach(error -> {
            assertThat(error.size()).isEqualTo(3);
            fields.add(error.path("field").asText());
        });
        assertThat(fields).containsExactlyElementsOf(switch (kind) {
            case "aggregate" -> List.of("name", "description", "price", "image", "categoryIds[0]");
            case "type" -> List.of("price");
            default -> List.of("body");
        });
    }

    @Test
    void queryIsRejectedWithoutMaintenanceOrQueryInErrorPath() throws Exception {
        var result = rejected(authorized(validBody()).queryParam("extra", "private-value"), 400, 0);
        assertEnvelope(result, 400, "Invalid product maintenance data");
        assertThat(json(result).path("errors").get(0).path("code").asText()).isEqualTo("INVALID_PARAMETER");
    }

    @Test
    void knownServiceDiagnosticUsesTheExistingOrderedAdministrativeEnvelope() throws Exception {
        rejectWithServiceDiagnostic = true;
        var result = rejected(authorized(validBody()), 400, 1);
        assertEnvelope(result, 400, "Invalid product maintenance data");
        assertThat(json(result).path("errors").get(0).path("field").asText()).isEqualTo("name");
        assertThat(json(result).path("errors").get(1).path("field").asText()).isEqualTo("categoryIds[1]");
    }

    @ParameterizedTest
    @ValueSource(ints = {405, 406, 415})
    void nativeHttpErrorsKeepStatusAndApplicableHeadersWithoutWriting(int status) throws Exception {
        var input = status == 405 ? patch("/api" + ROUTE).contextPath("/api").servletPath(ROUTE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).accept(MediaType.APPLICATION_JSON)
                : authorized(validBody());
        if (status == 406) input.accept(MediaType.APPLICATION_XML);
        if (status == 415) input.contentType(MediaType.TEXT_PLAIN);
        var result = rejected(input, status, 0);
        if (status == 405) assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).contains("GET", "POST");
        if (status == 415) assertThat(result.getResponse().getHeader(HttpHeaders.ACCEPT)).contains(MediaType.APPLICATION_JSON_VALUE);
        if (status != 406 || !result.getResponse().getContentAsString().isEmpty()) {
            assertEnvelope(result, status, "Product maintenance request not supported");
            assertThat(json(result).path("errors")).isEmpty();
        }
    }

    private MvcResult rejected(MockHttpServletRequestBuilder input, int status, int expectedEntries) throws Exception {
        noCallerTransaction();
        var before = database.snapshot();
        sql.clear();
        var result = mvc.perform(input).andReturn();
        noCallerTransaction();
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(database.snapshot()).isEqualTo(before);
        assertThat(sql.events()).noneMatch(event -> dml(event.sql()));
        assertThat(entries).isEqualTo(expectedEntries);
        assertThat(committedReturns).isZero();
        return result;
    }

    private void assertEnvelope(MvcResult result, int status, String message) throws Exception {
        var body = json(result);
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.has("errors")).isTrue();
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("error").asText()).isEqualTo(org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(body.path("message").asText()).isEqualTo(message);
        assertThat(body.path("path").asText()).isEqualTo("/api" + ROUTE);
    }

    private JsonNode json(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String login(Set<UserRole> roles) {
        var account = fixture.createAccount("T037-" + java.util.UUID.randomUUID(), "Disposable-T037", roles);
        assertThat(database.roles(account.id())).isEqualTo(roles);
        return fixture.login(account);
    }

    private String validBody() {
        return "{\"name\":\"  Fone  Azul  \",\"description\":\"  Descricao  completa  \",\"price\":\"1.000\","
                + "\"image\":\"https://example.invalid/new.jpg\",\"categoryIds\":["
                + categories.get(0).id() + "," + categories.get(0).id() + "," + categories.get(1).id() + "]}";
    }

    private MockHttpServletRequestBuilder authorized(String body) {
        return request("/api", body).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    private MockHttpServletRequestBuilder request(String prefix, String body) {
        return post(prefix + ROUTE).contextPath(prefix).servletPath(ROUTE)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body);
    }

    private static boolean dml(String statement) {
        String normalized = statement.stripLeading().toLowerCase(Locale.ROOT);
        return List.of("insert ", "update ", "delete ", "merge ").stream().anyMatch(normalized::startsWith);
    }

    private static void noCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation t037SqlObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }
        @Bean HibernatePropertiesCustomizer t037SqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
