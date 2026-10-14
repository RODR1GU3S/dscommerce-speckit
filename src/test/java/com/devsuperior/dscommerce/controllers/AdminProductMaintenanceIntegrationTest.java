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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** T024: one real POST, committed independent reads and unique existing-category associations. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductMaintenanceIntegrationTest.SqlConfig.class)
class AdminProductMaintenanceIntegrationTest {
    private static final String ROUTE = "/admin/products";
    private static final String NAME = "Fone Azul";
    private static final String DESCRIPTION = "Descricao completa do produto";
    private static final String IMAGE = "https://example.invalid/new-product.jpg";
    private static final BigDecimal PRICE = new BigDecimal("10.00");

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;
    private String adminToken;
    private List<CategoryData> categories;
    private final Set<Long> requestOwnedProducts = new LinkedHashSet<>();

    @BeforeEach
    void prepareOnlyCommittedOwnedRowsInTheSeedFreeContext() {
        assertNoCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        requestOwnedProducts.clear();
        fixture = new AdminProductTestFixture(context);
        var admin = fixture.createAccount("T024 Admin", "Disposable-T024", Set.of(UserRole.ADMIN));
        adminToken = fixture.login(admin);
        assertThat(context.getBean(JwtDecoder.class).decode(adminToken).getSubject()).isEqualTo(Long.toString(admin.id()));
        assertThat(database.roles(admin.id())).containsExactly(UserRole.ADMIN);
        var selection = fixture.prepareCategorySelection();
        categories = selection.sameName();
        assertThat(categories).extracting(CategoryData::id).doesNotHaveDuplicates();
        assertThat(categories).extracting(CategoryData::name).containsExactly("Categoria repetida", "Categoria repetida");
        var existing = fixture.createProduct("Produto preexistente", "Descricao preexistente",
                "https://example.invalid/existing-product.jpg", new BigDecimal("0.00"));
        fixture.associate(existing.id(), selection.unused().id());
        var before = database.snapshot();
        assertThat(before.products()).containsExactly(new ProductRow(existing.id(), existing.name(),
                existing.description(), existing.image(), existing.price()));
        assertThat(before.categories()).hasSize(3);
        assertThat(before.associations()).containsExactly(new ProductCategory(existing.id(), selection.unused().id()));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        sql.clear();
    }

    @AfterEach
    void cleanOnlyRequestAndFixtureOwnedRowsAfterIndependentAssertions() throws Exception {
        assertNoCallerTransaction();
        // Only products created by this test's POST in its private context are added to this ownership set.
        if (!requestOwnedProducts.isEmpty()) {
            try (var connection = database.openConnection()) {
                connection.setAutoCommit(false);
                try (var pairs = connection.prepareStatement("delete from tb_product_category where product_id = ?");
                     var products = connection.prepareStatement("delete from tb_product where id = ?")) {
                    for (long id : requestOwnedProducts) {
                        pairs.setLong(1, id);
                        pairs.executeUpdate();
                        products.setLong(1, id);
                        products.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception failure) {
                    connection.rollback();
                    throw failure;
                }
            }
            requestOwnedProducts.clear();
        }
        if (fixture != null) {
            fixture.close();
            assertEmptyDatabase();
        }
        sql.clear();
    }

    @ParameterizedTest(name = "T024 creation context={0}, selection={1}")
    @MethodSource("creationCases")
    void shouldCreateFromOneSubmissionAndReadTheCommittedProductWithoutDuplicatingCategoryPairs(
            String externalContext, Selection selection) throws Exception {
        var selected = selection == Selection.A_A ? List.of(categories.getFirst()) : categories;
        var observed = observePost(postRequest(externalContext, selection)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken), "create-" + selection, externalContext);
        var result = observed.result();

        assertThat(result.getResponse().getStatus()).as("one valid administrative POST must create with HTTP 201").isEqualTo(201);
        var actual = json(result, 201);
        assertThat(actual.path("id").isIntegralNumber()).isTrue();
        assertThat(actual.path("id").canConvertToLong()).isTrue();
        long id = actual.get("id").longValue();
        assertThat(id).isPositive();
        assertThat(observed.before().products()).noneMatch(product -> product.id() == id);
        assertJson(actual, detail(id, selected, true));

        var location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotBlank();
        var locationUri = URI.create(location);
        String detailRoute = ROUTE + "/" + id;
        assertThat(locationUri.getPath()).isEqualTo(externalContext + detailRoute);
        assertThat(locationUri.getRawQuery()).isNull();
        assertThat(locationUri.getRawFragment()).isNull();

        var expectedProducts = new ArrayList<>(observed.before().products());
        expectedProducts.add(new ProductRow(id, NAME, DESCRIPTION, IMAGE, PRICE));
        expectedProducts.sort(Comparator.comparingLong(ProductRow::id));
        assertThat(observed.after().products()).containsExactlyElementsOf(expectedProducts);
        var expectedPairs = new LinkedHashSet<>(observed.before().associations());
        selected.forEach(category -> expectedPairs.add(new ProductCategory(id, category.id())));
        assertThat(observed.after().associations()).isEqualTo(expectedPairs);
        // The independent probe rejects duplicate SQL rows before converting pairs to a Set.
        assertThat(observed.after().associations().stream().filter(pair -> pair.productId() == id))
                .hasSize(selected.size());
        assertThat(sql.events()).anyMatch(event -> isDml(event.sql()));

        var adminDetail = readBack(get(locationUri.getPath()).contextPath(externalContext).servletPath(detailRoute)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken), "admin-detail");
        assertJson(json(adminDetail, 200), actual);
        var publicDetail = readBack(get(externalContext + "/products/" + id).contextPath(externalContext)
                .servletPath("/products/" + id), "public-detail");
        assertJson(json(publicDetail, 200), detail(id, selected, false));
        assertThat(database.snapshot()).isEqualTo(observed.after());
        System.out.printf("T024_COMMITTED context=%s selection=%s newId=%d productDelta=1 selectedPairs=%d categoriesEqual=true%n",
                externalContext, selection, id, selected.size());
    }

    static Stream<Arguments> creationCases() {
        return Stream.of("", "/api").flatMap(context -> Stream.of(Selection.values())
                .map(selection -> Arguments.of(context, selection)));
    }

    @ParameterizedTest(name = "T024 rejected POST context={0}, client={1}")
    @MethodSource("rejectedCreationCases")
    void shouldKeepAnonymousAndClientCreationRejectedBeforeMvcWithoutMutation(String externalContext, boolean client)
            throws Exception {
        var request = postRequest(externalContext, Selection.A_A_B);
        if (client) {
            var account = fixture.createAccount("T024 Client", "Disposable-T024", Set.of(UserRole.CLIENT));
            assertThat(database.roles(account.id())).containsExactly(UserRole.CLIENT);
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.login(account));
        }
        var observed = observePost(request, client ? "client" : "anonymous", externalContext);
        var result = observed.result();
        int status = client ? 403 : 401;
        var expected = mapper.createObjectNode().put("status", status)
                .put("error", client ? "Forbidden" : "Unauthorized")
                .put("message", client ? "Product maintenance not permitted" : "Authentication required or invalid")
                .put("path", externalContext + ROUTE);
        expected.putArray("errors");
        assertJson(json(result, status), expected);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(client ? null : "Bearer");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getHandler()).isNull();
        assertThat(observed.after()).isEqualTo(observed.before());
        assertThat(sql.events()).noneMatch(event -> isDml(event.sql()));
    }

    static Stream<Arguments> rejectedCreationCases() {
        return Stream.of("", "/api").flatMap(context -> Stream.of(false, true).map(client -> Arguments.of(context, client)));
    }

    private MockHttpServletRequestBuilder postRequest(String externalContext, Selection selection) throws Exception {
        var body = mapper.createObjectNode().put("name", NAME).put("description", DESCRIPTION)
                .put("price", PRICE.toPlainString()).put("image", IMAGE);
        var ids = body.putArray("categoryIds");
        ids.add(categories.getFirst().id());
        if (selection != Selection.A_B) ids.add(categories.getFirst().id());
        if (selection != Selection.A_A) ids.add(categories.get(1).id());
        return post(externalContext + ROUTE).contextPath(externalContext).servletPath(ROUTE)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
    }

    private ObservedPost observePost(MockHttpServletRequestBuilder request, String label, String externalContext) throws Exception {
        assertNoCallerTransaction();
        var before = database.snapshot();
        var accountIds = database.accountIds();
        sql.clear();
        MvcResult result;
        Snapshot after;
        try {
            result = mvc.perform(request).andReturn();
        } finally {
            after = database.snapshot();
            after.products().stream().filter(product -> before.products().stream().noneMatch(old -> old.id() == product.id()))
                    .forEach(product -> requestOwnedProducts.add(product.id()));
        }
        assertNoCallerTransaction();
        assertThat(after.categories()).as("existing categories must not be created, renamed or removed").isEqualTo(before.categories());
        assertThat(after.orders()).isEqualTo(before.orders());
        assertThat(after.items()).isEqualTo(before.items());
        assertThat(database.accountIds()).isEqualTo(accountIds);
        if (result.getRequest().getHeader(HttpHeaders.AUTHORIZATION) != null) {
            assertThat(sql.events()).as("real security resolves the committed account").isNotEmpty();
        } else {
            assertThat(sql.events()).as("anonymous rejection precedes account lookup").isEmpty();
        }
        System.out.printf("T024_POST case=%s context=%s actual=%d handler=%s cause=%s productDelta=%d pairDelta=%d categoriesEqual=%s snapshotEqual=%s hibernateDml=%d%n",
                label, externalContext, result.getResponse().getStatus(),
                result.getHandler() == null ? "NONE" : result.getHandler().getClass().getSimpleName(),
                result.getResolvedException() == null ? "NONE" : result.getResolvedException().getClass().getSimpleName(),
                after.products().size() - before.products().size(), after.associations().size() - before.associations().size(),
                after.categories().equals(before.categories()), after.equals(before),
                sql.events().stream().filter(event -> isDml(event.sql())).count());
        return new ObservedPost(result, before, after);
    }

    private MvcResult readBack(MockHttpServletRequestBuilder request, String label) throws Exception {
        assertNoCallerTransaction();
        var before = database.snapshot();
        sql.clear();
        var result = mvc.perform(request.accept(MediaType.APPLICATION_JSON)).andReturn();
        assertThat(database.snapshot()).isEqualTo(before);
        assertThat(sql.events()).isNotEmpty().noneMatch(event -> isDml(event.sql()));
        assertNoCallerTransaction();
        System.out.printf("T024_READ case=%s actual=%d snapshotEqual=true hibernateDml=0%n", label, result.getResponse().getStatus());
        return result;
    }

    private JsonNode detail(long id, List<CategoryData> selected, boolean admin) {
        var expected = mapper.createObjectNode().put("id", id).put("name", NAME).put("description", DESCRIPTION).put("image", IMAGE);
        if (admin) expected.put("price", PRICE.toPlainString());
        else expected.put("price", PRICE);
        var options = expected.putArray("categories");
        if (admin) selected.stream().sorted(Comparator.comparing(CategoryData::name).thenComparingLong(CategoryData::id))
                .forEach(category -> options.addObject().put("id", category.id()).put("name", category.name()));
        else selected.stream().map(CategoryData::name).distinct().sorted().forEach(options::add);
        return expected;
    }

    private JsonNode json(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void assertJson(JsonNode actual, JsonNode expected) throws Exception {
        assertThat(actual).as("exact contract fields/types and independent fixture values").isEqualTo(mapper.readTree(expected.toString()));
    }

    private static boolean isDml(String statement) {
        var normalized = statement.stripLeading().toLowerCase(Locale.ROOT);
        return Stream.of("insert ", "update ", "delete ", "merge ").anyMatch(normalized::startsWith);
    }

    private static void assertNoCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
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

    enum Selection { A_B, A_A_B, A_A }
    private record ObservedPost(MvcResult result, Snapshot before, Snapshot after) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation maintenanceSqlObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }
        @Bean HibernatePropertiesCustomizer maintenanceSqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
