package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.CategoryData;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductCategory;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** T045 / US3.6: preserve GREEN legacy reads; replacement stays RED until the real PUT exists. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductLegacyReadIntegrationTest.SqlConfig.class)
class AdminProductLegacyReadIntegrationTest {
    private static final String DESCRIPTION = "Descricao  completa do produto";
    private static final String IMAGE = "https://example.invalid/legacy-control.jpg";
    private static final String NEW_NAME = "Produto  regularizado";
    private static final String NEW_DESCRIPTION = "Descricao  regularizada completa";
    private static final String NEW_IMAGE = "https://example.invalid/regularized.jpg";
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;
    private String token;
    private ProductData target;
    private ProductData other;
    private List<CategoryData> categories;
    private List<CategoryData> oldSelection;
    private final Set<Long> requestOwnedProducts = new HashSet<>();

    @BeforeEach
    void prepareIsolatedContextAndRealAdminLogin() {
        noCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        var account = fixture.createAccount("T045-" + UUID.randomUUID(), "Disposable-T045", Set.of(UserRole.ADMIN));
        assertThat(database.roles(account.id())).containsExactly(UserRole.ADMIN);
        token = fixture.login(account);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        requestOwnedProducts.clear();
    }

    private void prepareLegacy(String variant) {
        noCallerTransaction();
        categories = List.of(fixture.createCategory("Categoria igual"), fixture.createCategory("Categoria igual"));
        String name = switch (variant) {
            case "short", "combined" -> "ab";
            case "long" -> "x".repeat(81);
            case "zero", "unassociated" -> "Produto legado";
            default -> throw new IllegalArgumentException(variant);
        };
        boolean combined = variant.equals("combined");
        target = fixture.createProduct(name, combined ? "Legado" : DESCRIPTION,
                combined ? "imagem-legada" : IMAGE,
                new BigDecimal(variant.equals("zero") || combined ? "0.00" : "10.00"));
        other = fixture.createProduct("Produto independente", DESCRIPTION, IMAGE, new BigDecimal("20.00"));
        oldSelection = variant.equals("unassociated") || combined ? List.of() : List.of(categories.get(0));
        oldSelection.forEach(category -> fixture.associate(target.id(), category.id()));
        fixture.associate(other.id(), categories.get(0).id());
        var snapshot = database.snapshot();
        assertThat(snapshot.products()).containsExactly(row(target), row(other));
        assertThat(snapshot.categories()).containsExactlyElementsOf(categories.stream()
                .map(category -> new AdminProductDatabaseProbe.CategoryRow(category.id(), category.name())).toList());
        var expectedPairs = new HashSet<ProductCategory>();
        oldSelection.forEach(category -> expectedPairs.add(new ProductCategory(target.id(), category.id())));
        expectedPairs.add(new ProductCategory(other.id(), categories.get(0).id()));
        assertThat(snapshot.associations()).isEqualTo(expectedPairs);
        assertThat(snapshot.orders()).isEmpty();
        assertThat(snapshot.items()).isEmpty();
        assertThat(database.accountIds()).hasSize(1);
    }

    @ParameterizedTest(name = "legacy read {0}, context={1}")
    @CsvSource({"zero,ROOT", "zero,/api", "short,ROOT", "short,/api",
            "long,ROOT", "long,/api", "unassociated,ROOT", "unassociated,/api"})
    void legacyRemainsReadableInAdminAndPublicWithoutAutomaticCorrection(String variant, String external) throws Exception {
        prepareLegacy(variant);
        String prefix = prefix(external);
        var baseline = database.snapshot();
        assertDetail(read(prefix, true, false, true, "admin-detail-" + variant), row(target), oldSelection, true);
        assertListContainsLegacy(read(prefix, true, false, false, "admin-list-" + variant), true);
        for (boolean invalidBearer : List.of(false, true)) {
            assertDetail(read(prefix, false, invalidBearer, true, "public-detail-" + variant),
                    row(target), oldSelection, false);
            assertListContainsLegacy(read(prefix, false, invalidBearer, false, "public-list-" + variant), false);
        }
        assertThat(database.snapshot()).isEqualTo(baseline);
    }

    @ParameterizedTest(name = "regularize {0}, context={1}")
    @CsvSource({"zero,ROOT", "zero,/api", "short,ROOT", "short,/api",
            "long,ROOT", "long,/api", "unassociated,ROOT", "unassociated,/api"})
    void completeValidReplacementRegularizesLegacyAndConservesIdentity(String variant, String external) throws Exception {
        prepareLegacy(variant);
        String prefix = prefix(external);
        var observation = observe(putRequest(prefix, validBody()), "regularize-" + variant, 200);
        assertUnrelatedStatePreserved(observation);
        var expected = new ProductRow(target.id(), NEW_NAME, NEW_DESCRIPTION, NEW_IMAGE, new BigDecimal("12.50"));
        // Status is intentionally 200, never relaxed to the absent-handler 405.
        assertDetail(observation.result(), expected, categories, true);
        var products = observation.before().products().stream()
                .map(product -> product.id() == target.id() ? expected : product).toList();
        var pairs = new HashSet<>(observation.before().associations());
        pairs.removeIf(pair -> pair.productId() == target.id());
        categories.forEach(category -> pairs.add(new ProductCategory(target.id(), category.id())));
        assertThat(observation.after()).isEqualTo(new Snapshot(products, observation.before().categories(), pairs,
                observation.before().orders(), observation.before().items()));
        assertDetail(read(prefix, true, false, true, "regularized-admin"), expected, categories, true);
        assertDetail(read(prefix, false, false, true, "regularized-public"), expected, categories, false);
        assertThat(database.snapshot()).isEqualTo(observation.after());
    }

    @ParameterizedTest(name = "invalid regularization {0}, context={1}")
    @CsvSource({"short,ROOT", "short,/api", "long,ROOT", "long,/api",
            "description,ROOT", "description,/api", "price,ROOT", "price,/api",
            "image,ROOT", "image,/api", "categories,ROOT", "categories,/api",
            "aggregate,ROOT", "aggregate,/api"})
    void invalidFullReplacementPreservesAllLegacyValuesAndAssociations(String invalid, String external) throws Exception {
        prepareLegacy("combined");
        String prefix = prefix(external);
        var body = validBody();
        var expected = new ArrayList<Diagnostic>();
        if (invalid.equals("short") || invalid.equals("long") || invalid.equals("aggregate")) {
            body.put("name", invalid.equals("long") ? "x".repeat(81) : target.name());
            expected.add(new Diagnostic("name", "INVALID_LENGTH"));
        }
        if (invalid.equals("description") || invalid.equals("aggregate")) {
            body.put("description", target.description());
            expected.add(new Diagnostic("description", "INVALID_LENGTH"));
        }
        if (invalid.equals("price") || invalid.equals("aggregate")) {
            body.put("price", target.price().toPlainString());
            expected.add(new Diagnostic("price", "NOT_POSITIVE"));
        }
        if (invalid.equals("image") || invalid.equals("aggregate")) {
            body.put("image", target.image());
            expected.add(new Diagnostic("image", "INVALID_IMAGE_URL"));
        }
        if (invalid.equals("categories") || invalid.equals("aggregate")) {
            body.putArray("categoryIds");
            expected.add(new Diagnostic("categoryIds", "EMPTY_CATEGORIES"));
        }
        var observation = observe(putRequest(prefix, body), "invalid-" + invalid, 400);
        // These assertions and both rereads run even when status is the initial 405.
        assertThat(observation.after()).isEqualTo(observation.before());
        assertThat(observation.hibernateDml()).isZero();
        assertDetail(read(prefix, true, false, true, "preserved-admin-" + invalid), row(target), oldSelection, true);
        assertDetail(read(prefix, false, false, true, "preserved-public-" + invalid), row(target), oldSelection, false);
        assertThat(database.snapshot()).isEqualTo(observation.before());
        assertThat(observation.result().getResponse().getStatus()).as("invalid legacy replacement HTTP contract").isEqualTo(400);
        var envelope = json(observation.result());
        assertFields(envelope, "status", "error", "message", "path", "errors");
        assertThat(envelope.path("status").asInt()).isEqualTo(400);
        assertThat(envelope.path("error").asText()).isEqualTo("Bad Request");
        assertThat(envelope.path("message").asText()).isEqualTo("Invalid product maintenance data");
        assertThat(envelope.path("path").asText()).isEqualTo(prefix + "/admin/products/" + target.id());
        assertThat(envelope.path("errors").isArray()).isTrue();
        var actual = new ArrayList<Diagnostic>();
        for (var error : envelope.path("errors")) {
            assertFields(error, "field", "code", "message");
            assertThat(error.path("message").isTextual()).isTrue();
            assertThat(error.path("message").asText()).isNotBlank();
            actual.add(new Diagnostic(error.path("field").asText(), error.path("code").asText()));
        }
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    private MvcResult read(String prefix, boolean admin, boolean invalidBearer, boolean detail, String label) throws Exception {
        String route = (admin ? "/admin" : "") + "/products" + (detail ? "/" + target.id() : "");
        var request = get(prefix + route).contextPath(prefix).servletPath(route).accept(MediaType.APPLICATION_JSON);
        if (admin) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        else if (invalidBearer) request.header(HttpHeaders.AUTHORIZATION, "Bearer irrelevant-invalid-token");
        var observation = observe(request, label + (invalidBearer ? "-invalid-bearer" : ""), 200);
        assertThat(observation.after()).isEqualTo(observation.before());
        assertThat(observation.hibernateDml()).isZero();
        assertThat(sql.events()).anyMatch(event -> event.sql().stripLeading().toLowerCase(Locale.ROOT).startsWith("select "));
        assertThat(observation.result().getResponse().getStatus()).isEqualTo(200);
        return observation.result();
    }

    private Observation observe(MockHttpServletRequestBuilder request, String label, int expectedStatus) throws Exception {
        noCallerTransaction();
        var before = database.snapshot();
        var accounts = database.accountIds();
        sql.clear();
        MvcResult result;
        Snapshot after;
        try {
            result = mvc.perform(request).andReturn();
        } finally {
            noCallerTransaction();
            after = database.snapshot();
            var beforeIds = before.products().stream().map(ProductRow::id).collect(Collectors.toSet());
            after.products().stream().filter(product -> !beforeIds.contains(product.id()))
                    .forEach(product -> requestOwnedProducts.add(product.id()));
        }
        long dml = sql.events().stream().filter(event -> {
            String statement = event.sql().stripLeading().toLowerCase(Locale.ROOT);
            return List.of("insert ", "update ", "delete ", "merge ").stream().anyMatch(statement::startsWith);
        }).count();
        assertThat(database.accountIds()).isEqualTo(accounts);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        System.out.printf("T045_HTTP case=%s context=%s method=%s expected=%d actual=%d snapshotEqual=%s hibernateDml=%d cause=%s%n",
                label, result.getRequest().getContextPath().isEmpty() ? "ROOT" : "/api", result.getRequest().getMethod(),
                expectedStatus, result.getResponse().getStatus(), before.equals(after), dml,
                result.getResolvedException() == null ? "none" : result.getResolvedException().getClass().getSimpleName());
        return new Observation(result, before, after, dml);
    }

    private void assertUnrelatedStatePreserved(Observation observation) {
        assertThat(observation.after().categories()).isEqualTo(observation.before().categories());
        assertThat(observation.after().orders()).isEqualTo(observation.before().orders());
        assertThat(observation.after().items()).isEqualTo(observation.before().items());
        assertThat(observation.after().products().stream().filter(product -> product.id() != target.id()).toList())
                .isEqualTo(observation.before().products().stream().filter(product -> product.id() != target.id()).toList());
        assertThat(observation.after().associations().stream().filter(pair -> pair.productId() != target.id()).collect(Collectors.toSet()))
                .isEqualTo(observation.before().associations().stream().filter(pair -> pair.productId() != target.id()).collect(Collectors.toSet()));
    }

    private void assertListContainsLegacy(MvcResult result, boolean admin) throws Exception {
        var page = json(result);
        assertFields(page, "content", "page", "size", "totalElements", "totalPages");
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(12);
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        assertThat(page.path("totalPages").asInt()).isEqualTo(1);
        assertThat(page.path("content").isArray()).isTrue();
        assertThat(page.path("content").size()).isEqualTo(2);
        var matches = new ArrayList<JsonNode>();
        for (var item : page.path("content")) if (item.path("id").longValue() == target.id()) matches.add(item);
        assertThat(matches).hasSize(1);
        var item = matches.get(0);
        assertFields(item, "id", "name", "image", "price");
        assertCommon(item, row(target), admin);
    }

    private void assertDetail(MvcResult result, ProductRow expected, List<CategoryData> selected, boolean admin) throws Exception {
        assertThat(result.getResponse().getStatus()).as("complete legacy replacement / read HTTP contract").isEqualTo(200);
        var body = json(result);
        assertFields(body, "id", "name", "description", "image", "price", "categories");
        assertCommon(body, expected, admin);
        assertThat(body.path("description").isTextual()).isTrue();
        assertThat(body.path("description").asText()).isEqualTo(expected.description());
        var actual = body.path("categories");
        assertThat(actual.isArray()).isTrue();
        assertThat((long) actual.size()).isEqualTo(admin ? selected.size() : selected.stream().map(CategoryData::name).distinct().count());
        for (int index = 0; index < actual.size(); index++) {
            if (admin) {
                assertFields(actual.get(index), "id", "name");
                assertThat(actual.get(index).path("id").isIntegralNumber()).isTrue();
                assertThat(actual.get(index).path("id").longValue()).isEqualTo(selected.get(index).id());
                assertThat(actual.get(index).path("name").asText()).isEqualTo(selected.get(index).name());
            } else {
                assertThat(actual.get(index).isTextual()).isTrue();
                assertThat(actual.get(index).asText()).isEqualTo(selected.get(0).name());
            }
        }
    }

    private static void assertCommon(JsonNode body, ProductRow expected, boolean admin) {
        assertThat(body.path("id").isIntegralNumber()).isTrue();
        assertThat(body.path("id").longValue()).isEqualTo(expected.id());
        assertThat(body.path("name").isTextual()).isTrue();
        assertThat(body.path("name").asText()).isEqualTo(expected.name());
        assertThat(body.path("image").isTextual()).isTrue();
        assertThat(body.path("image").asText()).isEqualTo(expected.image());
        if (admin) {
            assertThat(body.path("price").isTextual()).isTrue();
            assertThat(body.path("price").asText()).isEqualTo(expected.price().toPlainString());
        } else {
            assertThat(body.path("price").isNumber()).isTrue();
            assertThat(body.path("price").decimalValue()).isEqualByComparingTo(expected.price());
        }
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

    private ObjectNode validBody() {
        var body = mapper.createObjectNode().put("name", NEW_NAME).put("description", NEW_DESCRIPTION)
                .put("price", "12.50").put("image", NEW_IMAGE);
        var ids = body.putArray("categoryIds");
        categories.forEach(category -> ids.add(category.id()));
        return body;
    }

    private MockHttpServletRequestBuilder putRequest(String prefix, ObjectNode body) throws Exception {
        String route = "/admin/products/" + target.id();
        return put(prefix + route).contextPath(prefix).servletPath(route)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
    }

    @AfterEach
    void cleanOnlyOwnedRowsAfterExternalAssertions() throws Exception {
        noCallerTransaction();
        if (fixture == null) return;
        // A future successful PUT introduces pairs outside the fixture's original ownership manifest.
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var pairs = connection.prepareStatement("delete from tb_product_category where product_id = ?");
                 var products = connection.prepareStatement("delete from tb_product where id = ?")) {
                var ownedIds = new HashSet<>(requestOwnedProducts);
                if (target != null) ownedIds.add(target.id());
                if (other != null) ownedIds.add(other.id());
                for (long id : ownedIds) {
                    pairs.setLong(1, id);
                    pairs.executeUpdate();
                }
                for (long id : requestOwnedProducts) {
                    products.setLong(1, id);
                    products.executeUpdate();
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

    private static void noCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    private static String prefix(String external) { return external.equals("ROOT") ? "" : external; }
    private static ProductRow row(ProductData product) {
        return new ProductRow(product.id(), product.name(), product.description(), product.image(), product.price());
    }
    private record Diagnostic(String field, String code) { }
    private record Observation(MvcResult result, Snapshot before, Snapshot after, long hibernateDml) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation t045SqlObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }
        @Bean HibernatePropertiesCustomizer t045SqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
