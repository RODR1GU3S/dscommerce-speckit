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
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** T044: executable RED contract for full replacement; no PUT implementation or mocked authentication. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductReplacementIntegrationTest.SqlConfig.class)
class AdminProductReplacementIntegrationTest {
    private static final long ABSENT_ID = Long.MAX_VALUE;
    private static final String NEW_NAME = "Fone  Novo";
    private static final String NEW_DESCRIPTION = "Descricao  nova completa";
    private static final String NEW_IMAGE = "https://example.invalid/replacement.jpg";
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;
    private String adminToken;
    private ProductData target;
    private ProductData other;
    private List<CategoryData> oldCategories;
    private List<CategoryData> newCategories;
    private final Set<Long> requestOwnedProducts = new HashSet<>();

    @BeforeEach
    void prepareCommittedPrivateDataAndRealAdminLogin(TestInfo test) {
        noCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        requestOwnedProducts.clear();
        var account = fixture.createAccount("T044-" + UUID.randomUUID(), "Disposable-T044", Set.of(UserRole.ADMIN));
        assertThat(database.roles(account.id())).containsExactly(UserRole.ADMIN);
        adminToken = fixture.login(account);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        // US1.8 owns a separate seed-free, zero-category fixture; legacy regularization is T045.
        boolean withoutCategories = test.getTestMethod().orElseThrow().getName()
                .equals("withoutCatalogCategoriesRejectsEmptyOrMissingSelection");
        oldCategories = withoutCategories ? List.of() : List.of(
                fixture.createCategory("Antiga A"), fixture.createCategory("Antiga B"));
        newCategories = withoutCategories ? List.of() : List.of(
                fixture.createCategory("Nova repetida"), fixture.createCategory("Nova repetida"));
        target = fixture.createProduct("Produto original", "Descricao original completa",
                "https://example.invalid/original.jpg", new BigDecimal("10.00"));
        other = fixture.createProduct("Produto independente", "Descricao independente completa",
                "https://example.invalid/other.jpg", new BigDecimal("20.00"));
        oldCategories.forEach(category -> fixture.associate(target.id(), category.id()));
        if (!withoutCategories) {
            fixture.associate(other.id(), oldCategories.get(0).id());
            fixture.createCategory("Opcao sem produto");
        }
        var prepared = database.snapshot();
        assertThat(prepared.products()).containsExactly(row(target), row(other));
        assertThat(prepared.categories()).hasSize(withoutCategories ? 0 : 5);
        assertThat(prepared.associations()).hasSize(withoutCategories ? 0 : 3);
        assertThat(prepared.orders()).isEmpty();
        assertThat(prepared.items()).isEmpty();
        assertThat(prepared.products()).noneMatch(product -> product.id() == ABSENT_ID);
        assertThat(prepared.categories()).noneMatch(category -> category.id() == ABSENT_ID);
    }

    @AfterEach
    void cleanOwnedRowsOnlyAfterIndependentPostOperationAssertions() throws Exception {
        noCallerTransaction();
        if (fixture == null) return;
        // Future successful replacement may create pairs unknown to the original fixture manifest.
        // Remove only pairs of owned products and any products introduced by this request, not all table rows.
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

    @ParameterizedTest
    @ValueSource(strings = {"ROOT", "/api"})
    void readForEditingIsAnAlreadyImplementedControl(String external) throws Exception {
        String prefix = prefix(external);
        var observation = perform(getRequest(prefix, target.id()), "read-control", 200);
        assertThat(observation.after()).isEqualTo(observation.before());
        assertThat(observation.hibernateDml()).isZero();
        assertDetail(observation.result(), row(target), oldCategories);
    }

    @ParameterizedTest
    @CsvSource({"ROOT,complete", "/api,complete", "ROOT,normalized", "/api,normalized",
            "ROOT,repeatedTwo", "/api,repeatedTwo", "ROOT,repeatedOne", "/api,repeatedOne"})
    void replacesAllValuesSameIdentityAndExactlyTheNewDistinctSelection(String external, String kind) throws Exception {
        String prefix = prefix(external);
        var body = validBody();
        var selected = kind.equals("repeatedOne") ? List.of(newCategories.get(0)) : newCategories;
        if (kind.equals("repeatedTwo")) {
            selection(body, newCategories.get(0).id(), newCategories.get(0).id(), newCategories.get(1).id());
        } else if (kind.equals("repeatedOne")) {
            selection(body, newCategories.get(0).id(), newCategories.get(0).id());
        }
        String price = "12.50";
        if (kind.equals("normalized")) {
            // Unicode padding is removed; internal double spaces and decimal value remain exact.
            body.put("name", "\u00a0\u2003 " + NEW_NAME + " \u2003\u00a0");
            body.put("description", "\u00a0\u2003 " + NEW_DESCRIPTION + " \u2003\u00a0");
            body.put("price", "1.000");
            price = "1.00";
        }
        var observation = perform(putRequest(prefix, target.id(), body), kind, 200);
        assertUnrelatedStatePreserved(observation);
        var expected = new ProductRow(target.id(), NEW_NAME, NEW_DESCRIPTION, NEW_IMAGE, new BigDecimal(price));
        // These remain RED until a real PUT reaches and completes the transactional maintenance proxy.
        assertDetail(observation.result(), expected, selected);
        var expectedProducts = observation.before().products().stream()
                .map(product -> product.id() == target.id() ? expected : product).toList();
        var expectedPairs = new HashSet<>(observation.before().associations());
        expectedPairs.removeIf(pair -> pair.productId() == target.id());
        selected.forEach(category -> expectedPairs.add(new ProductCategory(target.id(), category.id())));
        assertThat(observation.after()).isEqualTo(new Snapshot(expectedProducts, observation.before().categories(),
                expectedPairs, observation.before().orders(), observation.before().items()));
        assertThat(observation.after().categories()).containsAll(oldCategories.stream()
                .map(category -> new AdminProductDatabaseProbe.CategoryRow(category.id(), category.name())).toList());
        // SQL is the persistence oracle; HTTP GET independently checks the editing representation afterward.
        var reread = perform(getRequest(prefix, target.id()), "read-after-" + kind, 200);
        assertDetail(reread.result(), expected, selected);
        assertThat(reread.after()).isEqualTo(observation.after());
        assertThat(reread.hibernateDml()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"ROOT,name", "/api,name", "ROOT,description", "/api,description",
            "ROOT,price", "/api,price", "ROOT,image", "/api,image",
            "ROOT,categoryIds", "/api,categoryIds"})
    void omissionRejectsTheWholeReplacementInsteadOfRetainingOldFields(String external, String field) throws Exception {
        var body = validBody();
        body.remove(field);
        assertInvalid(prefix(external), target.id(), body, "omitted-" + field, List.of(new Diagnostic(field, "REQUIRED")));
    }

    @ParameterizedTest
    @CsvSource({"ROOT,aggregate", "/api,aggregate", "ROOT,empty", "/api,empty",
            "ROOT,mixedRepeatedMissing", "/api,mixedRepeatedMissing", "ROOT,tooLong", "/api,tooLong",
            "ROOT,rounding", "/api,rounding", "ROOT,overflow", "/api,overflow"})
    void invalidLimitsImageAndReferencesPreserveEveryOldValueAndPair(String external, String kind) throws Exception {
        var body = validBody();
        List<Diagnostic> expected;
        switch (kind) {
            case "aggregate" -> {
                aggregateInvalid(body);
                expected = aggregateDiagnostics();
            }
            case "empty" -> {
                selection(body);
                expected = List.of(new Diagnostic("categoryIds", "EMPTY_CATEGORIES"));
            }
            case "mixedRepeatedMissing" -> {
                selection(body, newCategories.get(0).id(), ABSENT_ID, ABSENT_ID, newCategories.get(1).id());
                expected = List.of(new Diagnostic("categoryIds[1]", "CATEGORY_NOT_FOUND"),
                        new Diagnostic("categoryIds[2]", "CATEGORY_NOT_FOUND"));
            }
            case "tooLong" -> {
                body.put("name", "x".repeat(81));
                expected = List.of(new Diagnostic("name", "INVALID_LENGTH"));
            }
            case "rounding", "overflow" -> {
                body.put("price", kind.equals("rounding") ? "1.001" : "100000000000000000.00");
                expected = List.of(new Diagnostic("price", "UNREPRESENTABLE_MONEY"));
            }
            default -> throw new IllegalArgumentException(kind);
        }
        assertInvalid(prefix(external), target.id(), body, kind, expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROOT", "/api"})
    void validBodyForAbsentTargetIs404WithoutUpsertOrChangingAnotherProduct(String external) throws Exception {
        String prefix = prefix(external);
        var observation = perform(putRequest(prefix, ABSENT_ID, validBody()), "missing-valid", 404);
        assertThat(observation.after()).isEqualTo(observation.before());
        assertThat(observation.hibernateDml()).isZero();
        assertEnvelope(observation.result(), 404, "Product not found", prefix, ABSENT_ID);
        assertThat(json(observation.result()).path("errors")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROOT", "/api"})
    void invalidBodyForTheSameAbsentTargetIsAggregated400BeforeTargetExistence(String external) throws Exception {
        var body = validBody();
        aggregateInvalid(body);
        assertInvalid(prefix(external), ABSENT_ID, body, "missing-invalid", aggregateDiagnostics());
    }

    @ParameterizedTest
    @CsvSource({"ROOT,empty", "/api,empty", "ROOT,missing", "/api,missing"})
    void withoutCatalogCategoriesRejectsEmptyOrMissingSelection(String external, String kind) throws Exception {
        assertThat(database.snapshot().categories()).isEmpty();
        var body = validBody();
        if (kind.equals("missing")) selection(body, ABSENT_ID);
        assertInvalid(prefix(external), target.id(), body, "zero-categories-" + kind,
                List.of(kind.equals("empty") ? new Diagnostic("categoryIds", "EMPTY_CATEGORIES")
                        : new Diagnostic("categoryIds[0]", "CATEGORY_NOT_FOUND")));
    }

    private void assertInvalid(String prefix, long id, ObjectNode body, String label,
                               List<Diagnostic> expected) throws Exception {
        var observation = perform(putRequest(prefix, id, body), label, 400);
        // Always reached before the status assertion, including the initial absent-PUT RED.
        assertThat(observation.after()).isEqualTo(observation.before());
        assertThat(observation.hibernateDml()).isZero();
        assertEnvelope(observation.result(), 400, "Invalid product maintenance data", prefix, id);
        var errors = json(observation.result()).path("errors");
        assertThat(errors.isArray()).isTrue();
        var actual = new ArrayList<Diagnostic>();
        for (var error : errors) {
            assertFields(error, "field", "code", "message");
            assertThat(error.path("message").isTextual()).isTrue();
            assertThat(error.path("message").asText()).isNotBlank().doesNotContain(Long.toString(ABSENT_ID));
            actual.add(new Diagnostic(error.path("field").asText(), error.path("code").asText()));
        }
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    private Observation perform(MockHttpServletRequestBuilder request, String label, int expectedStatus) throws Exception {
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
            // DriverManager connection, outside the caller and JPA context, before fixture cleanup.
            after = database.snapshot();
            var beforeIds = before.products().stream().map(ProductRow::id).collect(Collectors.toSet());
            after.products().stream().filter(product -> !beforeIds.contains(product.id()))
                    .forEach(product -> requestOwnedProducts.add(product.id()));
        }
        long dml = sql.events().stream().filter(event -> dml(event.sql())).count();
        assertThat(database.accountIds()).isEqualTo(accounts);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        System.out.printf("T044_HTTP case=%s context=%s expected=%d actual=%d snapshotEqual=%s hibernateDml=%d cause=%s%n",
                label, result.getRequest().getContextPath().isEmpty() ? "ROOT" : "/api",
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
        assertThat(observation.after().associations().stream().filter(pair -> pair.productId() != target.id())
                .collect(Collectors.toSet())).isEqualTo(observation.before().associations().stream()
                        .filter(pair -> pair.productId() != target.id()).collect(Collectors.toSet()));
    }

    private void assertDetail(MvcResult result, ProductRow expected, List<CategoryData> categories) throws Exception {
        assertThat(result.getResponse().getStatus()).as("full replacement / editing HTTP contract").isEqualTo(200);
        var body = json(result);
        assertFields(body, "id", "name", "description", "image", "price", "categories");
        assertThat(body.path("id").isIntegralNumber()).isTrue();
        assertThat(body.path("id").longValue()).isEqualTo(expected.id());
        for (String field : List.of("name", "description", "image", "price")) {
            assertThat(body.path(field).isTextual()).as(field).isTrue();
        }
        assertThat(body.path("name").asText()).isEqualTo(expected.name());
        assertThat(body.path("description").asText()).isEqualTo(expected.description());
        assertThat(body.path("image").asText()).isEqualTo(expected.image());
        assertThat(body.path("price").asText()).isEqualTo(expected.price().toPlainString());
        var ordered = categories.stream().sorted(Comparator.comparing(CategoryData::name).thenComparingLong(CategoryData::id)).toList();
        var actual = body.path("categories");
        assertThat(actual.isArray()).isTrue();
        assertThat(actual.size()).isEqualTo(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            var category = actual.get(index);
            assertFields(category, "id", "name");
            assertThat(category.path("id").isIntegralNumber()).isTrue();
            assertThat(category.path("id").longValue()).isEqualTo(ordered.get(index).id());
            assertThat(category.path("name").isTextual()).isTrue();
            assertThat(category.path("name").asText()).isEqualTo(ordered.get(index).name());
        }
    }

    private void assertEnvelope(MvcResult result, int status, String message, String prefix, long id) throws Exception {
        assertThat(result.getResponse().getStatus()).as("replacement rejection HTTP contract").isEqualTo(status);
        var body = json(result);
        assertFields(body, "status", "error", "message", "path", "errors");
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("error").asText()).isEqualTo(HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(body.path("message").asText()).isEqualTo(message);
        assertThat(body.path("path").asText()).isEqualTo(prefix + route(id));
        assertThat(body.path("errors").isArray()).isTrue();
    }

    private static void assertFields(JsonNode object, String... expected) {
        var names = new ArrayList<String>();
        object.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder(expected);
    }

    private JsonNode json(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private ObjectNode validBody() {
        var body = mapper.createObjectNode().put("name", NEW_NAME).put("description", NEW_DESCRIPTION)
                .put("price", "12.50").put("image", NEW_IMAGE);
        selection(body, newCategories.stream().mapToLong(CategoryData::id).toArray());
        return body;
    }

    private static void selection(ObjectNode body, long... ids) {
        var array = body.putArray("categoryIds");
        for (long id : ids) array.add(id);
    }

    private void aggregateInvalid(ObjectNode body) {
        body.put("name", "ab").put("description", "123456789").put("price", "0").put("image", "/relative.jpg");
        selection(body, newCategories.get(0).id(), ABSENT_ID, ABSENT_ID, newCategories.get(1).id());
    }

    private static List<Diagnostic> aggregateDiagnostics() {
        return List.of(new Diagnostic("name", "INVALID_LENGTH"), new Diagnostic("description", "INVALID_LENGTH"),
                new Diagnostic("price", "NOT_POSITIVE"), new Diagnostic("image", "INVALID_IMAGE_URL"),
                new Diagnostic("categoryIds[1]", "CATEGORY_NOT_FOUND"), new Diagnostic("categoryIds[2]", "CATEGORY_NOT_FOUND"));
    }

    private MockHttpServletRequestBuilder getRequest(String prefix, long id) {
        return get(prefix + route(id)).contextPath(prefix).servletPath(route(id))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).accept(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder putRequest(String prefix, long id, ObjectNode body) throws Exception {
        return put(prefix + route(id)).contextPath(prefix).servletPath(route(id))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
    }

    private static String prefix(String external) { return external.equals("ROOT") ? "" : external; }
    private static String route(long id) { return "/admin/products/" + id; }
    private static ProductRow row(ProductData product) {
        return new ProductRow(product.id(), product.name(), product.description(), product.image(), product.price());
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

    private static boolean dml(String statement) {
        String normalized = statement.stripLeading().toLowerCase(Locale.ROOT);
        return List.of("insert ", "update ", "delete ", "merge ").stream().anyMatch(normalized::startsWith);
    }

    private static void noCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    private record Diagnostic(String field, String code) { }
    private record Observation(MvcResult result, Snapshot before, Snapshot after, long hibernateDml) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation t044SqlObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }
        @Bean HibernatePropertiesCustomizer t044SqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
