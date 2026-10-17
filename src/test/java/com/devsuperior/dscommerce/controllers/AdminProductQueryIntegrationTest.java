package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.CategoryData;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.V03Manifest;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.assertCompletePages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** T014 HTTP RED: real authentication and isolated committed fixtures; no future query components. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductQueryIntegrationTest.SqlProbeConfig.class)
class AdminProductQueryIntegrationTest {

    private static final String LIST = "/admin/products";
    private static final String CATEGORIES = "/admin/categories";
    private static final String PRIVATE_PARAMETER = "T014_PRIVATE_PARAMETER";
    private static final String PRIVATE_VALUE = "T014_PRIVATE_VALUE";
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;
    @Autowired private ObjectMapper mapper;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    @Autowired private ProductRepository repository;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private String token;
    private MockMvc mvc;

    @BeforeEach
    void prepareOnlyAnAdminInTheExplicitSeedFreeContext() {
        assertNoCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        var account = fixture.createAccount("t014-" + UUID.randomUUID(), "t014-disposable-password", Set.of(UserRole.ADMIN));
        token = fixture.login(account);
        assertThat(context.getBean(JwtDecoder.class).decode(token).getSubject()).isEqualTo(Long.toString(account.id()));
        assertThat(database.accountIds()).containsExactly(account.id());
        assertThat(database.roles(account.id())).containsExactly(UserRole.ADMIN);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        sql.clear();
    }

    @AfterEach
    void cleanOnlyOwnedRowsAfterIndependentAssertions() {
        assertNoCallerTransaction();
        if (fixture != null) {
            fixture.close();
            assertEmptyDatabase();
        }
        sql.clear();
    }

    @ParameterizedTest(name = "V03 context={0}, filtered={1}")
    @MethodSource("navigationCases")
    void shouldNavigateTheExactSixProductFixtureWithoutDuplicatesOrLostProducts(String externalContext, boolean filtered)
            throws Exception {
        var manifest = v03();
        var expectedIds = filtered ? manifest.filteredOrder() : manifest.unfilteredOrder();
        var responses = new ArrayList<MvcResult>();
        // Request every page, including the later empty page, before asserting any response status.
        for (int page = 0; page <= 3; page++) {
            var query = query("page", Integer.toString(page), "size", "2");
            if (filtered) query.add("name", "fone");
            responses.add(adminGet(externalContext, LIST, query, "V03-" + filtered + "-page-" + page, 200));
        }
        var pages = new ArrayList<PageResponseDTO<JsonNode>>();
        for (int page = 0; page < responses.size(); page++) {
            pages.add(assertPage(responses.get(page), page, 2, expectedIds.size(),
                    productsAt(manifest, expectedIds, page, 2), true));
        }
        assertThat(pages).extracting(value -> value.content().size())
                .containsExactly(2, 2, filtered ? 1 : 2, 0);
        assertThat(assertCompletePages(pages, 2, expectedIds, value -> value.get("id").asLong()))
                .containsExactlyElementsOf(expectedIds);
        long multipleCategoryProduct = manifest.insertedProducts().get(4).id();
        assertThat(pages.stream().flatMap(page -> page.content().stream())
                .filter(item -> item.get("id").asLong() == multipleCategoryProduct).count()).isEqualTo(1);
        // T020 adds a second real path against the same committed data and independent oracle.
        var repositoryPages = new ArrayList<PageResponseDTO<Product>>();
        for (int page = 0; page <= 3; page++) {
            var repositoryPage = repositoryPage(filtered ? "fone" : null, page, 2);
            assertMatchingPage(repositoryPage, responses.get(page), expectedIds.stream().skip((long) page * 2).limit(2).toList());
            repositoryPages.add(repositoryPage);
        }
        var repositoryIds = assertCompletePages(repositoryPages, 2, expectedIds, Product::getId);
        var httpIds = pages.stream().flatMap(page -> page.content().stream()).map(item -> item.get("id").asLong()).toList();
        assertThat(repositoryIds).containsExactlyElementsOf(expectedIds).containsExactlyElementsOf(httpIds);
        System.out.printf("T020_V03_COMPARE context=%s filtered=%s httpIds=%s repositoryIds=%s oracleIds=%s totalElements=%d totalPages=%d%n",
                externalContext, filtered, httpIds, repositoryIds, expectedIds,
                repositoryPages.getFirst().totalElements(), repositoryPages.getFirst().totalPages());
    }

    static Stream<Arguments> navigationCases() {
        return contexts().flatMap(context -> Stream.of(false, true).map(filtered -> Arguments.of(context, filtered)));
    }

    @ParameterizedTest(name = "default context={0}, filter={1}, restricted={2}")
    @MethodSource("defaultFilters")
    void shouldUseDefaultsAndNormalizeOnlyExternalFilterWhitespace(String externalContext, String filter, boolean restricted)
            throws Exception {
        var manifest = v03();
        var query = new LinkedMultiValueMap<String, String>();
        if (filter != null) query.add("name", filter);
        var result = adminGet(externalContext, LIST, query, "defaults-and-external-whitespace", 200);
        var expectedIds = restricted ? manifest.filteredOrder() : manifest.unfilteredOrder();
        assertPage(result, 0, 12, expectedIds.size(), productsAt(manifest, expectedIds, 0, 12), true);
    }

    static Stream<Arguments> defaultFilters() {
        return contexts().flatMap(context -> Stream.of(Arguments.of(context, null, false),
                Arguments.of(context, "", false), Arguments.of(context, " \t\u00a0\u2003 ", false),
                Arguments.of(context, " FONE ", true), Arguments.of(context, "\u00a0FONE\u2003", true)));
    }

    @ParameterizedTest(name = "bounds context={0}, page={1}, size={2}")
    @MethodSource("validPageBounds")
    void shouldAcceptPageAndSizeBoundariesWithoutChangingTotals(String externalContext, int page, int size) throws Exception {
        var manifest = v03();
        var result = adminGet(externalContext, LIST, query("page", Integer.toString(page), "size", Integer.toString(size)),
                "valid-pagination-boundary", 200);
        assertPage(result, page, size, 6, productsAt(manifest, manifest.unfilteredOrder(), page, size), true);
    }

    static Stream<Arguments> validPageBounds() {
        return contexts().flatMap(context -> Stream.of(Arguments.of(context, 0, 1), Arguments.of(context, 0, 50),
                Arguments.of(context, Integer.MAX_VALUE, 2)));
    }

    @ParameterizedTest(name = "literal context={0}, scenario={1}, filter={2}")
    @MethodSource("literalSearchCases")
    void shouldDistinguishAccentsAndInternalSpacesAndTreatSpecialCharactersLiterally(
            String externalContext, SearchScenario scenario, String filter, String oracleKey) throws Exception {
        var manifest = switch (scenario) {
            case ACCENTS -> fixture.prepareAccentScenario();
            case SPACES -> fixture.prepareSpacingScenario();
            case SPECIAL -> fixture.prepareSpecialCharactersScenario();
        };
        var expectedIds = manifest.expectedMatches().getOrDefault(oracleKey, List.of());
        var expectedProducts = expectedIds.stream().map(id -> manifest.products().stream()
                .filter(product -> product.id() == id).findFirst().orElseThrow()).toList();
        var result = adminGet(externalContext, LIST, query("name", filter), "literal-" + scenario, 200);
        assertPage(result, 0, 12, expectedIds.size(), expectedProducts, true);
        assertMatchingPage(repositoryPage(filter, 0, 12), result, expectedIds);
        System.out.printf("T020_LITERAL_COMPARE context=%s scenario=%s filter=%s oracleIds=%s%n",
                externalContext, scenario, filter, expectedIds);
    }

    static Stream<Arguments> literalSearchCases() {
        return contexts().flatMap(context -> Stream.of(
                Arguments.of(context, SearchScenario.ACCENTS, "cafe", "cafe"),
                Arguments.of(context, SearchScenario.ACCENTS, "CAFÉ", "café"),
                Arguments.of(context, SearchScenario.SPACES, "fone a", "fone a"),
                Arguments.of(context, SearchScenario.SPACES, "fone  a", "fone  a"),
                Arguments.of(context, SearchScenario.SPECIAL, "%", "%"),
                Arguments.of(context, SearchScenario.SPECIAL, "_", "_"),
                Arguments.of(context, SearchScenario.SPECIAL, "/", "/"),
                Arguments.of(context, SearchScenario.SPECIAL, "'", "'"),
                Arguments.of(context, SearchScenario.SPECIAL, "' OR 1=1 --", "no-match")));
    }

    @ParameterizedTest
    @MethodSource("contexts")
    void shouldReturnAnEmptySearchWithCoherentPagination(String externalContext) throws Exception {
        v03();
        var result = adminGet(externalContext, LIST, query("name", "no-match", "size", "2"), "empty-search", 200);
        assertPage(result, 0, 2, 0, List.of(), true);
    }

    @ParameterizedTest
    @MethodSource("contexts")
    void shouldReturnAnEmptyAdministrativeCatalogWithoutImportingSeeds(String externalContext) throws Exception {
        assertThat(database.snapshot().products()).isEmpty();
        var result = adminGet(externalContext, LIST, query(), "empty-catalog", 200);
        assertPage(result, 0, 12, 0, List.of(), true);
    }

    @ParameterizedTest
    @MethodSource("contexts")
    void shouldReadAllProductDataAndKeepBothCategoryIdsWhenTheirNamesAreEqual(String externalContext) throws Exception {
        var manifest = v03();
        var product = manifest.insertedProducts().get(4);
        var result = adminGet(externalContext, LIST + "/" + product.id(), query(), "administrative-detail", 200);
        assertJson(json(result, 200), detail(product, manifest.categories(), true));
    }

    @ParameterizedTest
    @MethodSource("contexts")
    void shouldListEveryCategoryInNameIdOrderIncludingUnusedAndEqualNameOptions(String externalContext) throws Exception {
        var selection = fixture.prepareCategorySelection();
        var alphabeticallyFirst = fixture.createCategory("AAA category");
        var product = fixture.createProduct("Category control", "Category selection control",
                "https://example.invalid/categories.jpg", new BigDecimal("10.00"));
        selection.sameName().forEach(category -> fixture.associate(product.id(), category.id()));
        var expected = new ArrayList<>(selection.sameName());
        expected.add(selection.unused());
        expected.add(alphabeticallyFirst);
        var result = adminGet(externalContext, CATEGORIES, query(), "category-options", 200);
        var actual = json(result, 200);
        assertJson(actual, categories(expected));
        assertThat(actual).hasSize(4);
        var ids = new ArrayList<Long>();
        actual.forEach(category -> ids.add(category.get("id").asLong()));
        assertThat(ids).doesNotHaveDuplicates().contains(selection.unused().id());
        assertThat(selection.sameName()).extracting(CategoryData::id).doesNotHaveDuplicates();
    }

    @ParameterizedTest(name = "legacy context={0}, resource={1}")
    @MethodSource("legacyReads")
    void shouldReadLegacyValuesAndEmptyCategoryOptionsWithoutApplyingWriteRules(String externalContext, LegacyResource resource)
            throws Exception {
        var legacy = fixture.prepareWithoutCategories();
        assertThat(database.snapshot().categories()).isEmpty();
        String path = switch (resource) {
            case LIST -> LIST;
            case DETAIL -> LIST + "/" + legacy.id();
            case CATEGORIES -> CATEGORIES;
        };
        var result = adminGet(externalContext, path, query(), "legacy-" + resource, 200);
        switch (resource) {
            case LIST -> assertPage(result, 0, 12, 1, List.of(legacy), true);
            case DETAIL -> assertJson(json(result, 200), detail(legacy, List.of(), true));
            case CATEGORIES -> assertJson(json(result, 200), mapper.createArrayNode());
        }
    }

    static Stream<Arguments> legacyReads() {
        return contexts().flatMap(context -> Stream.of(LegacyResource.values()).map(resource -> Arguments.of(context, resource)));
    }

    @ParameterizedTest
    @MethodSource("contexts")
    void shouldReturnTheAdministrativeNotFoundEnvelopeForAValidAbsentProductId(String externalContext) throws Exception {
        var product = fixture.prepareLegacyProduct();
        long absent = Math.addExact(product.id(), 1L);
        assertThat(database.snapshot().products()).noneMatch(row -> row.id() == absent);
        String path = LIST + "/" + absent;
        var result = adminGet(externalContext, path, query(), "missing-product", 404);
        var expected = error(404, "Product not found", externalContext + path);
        expected.putArray("errors");
        assertJson(json(result, 404), expected);
    }

    @ParameterizedTest(name = "invalid {0}={1}")
    @MethodSource("invalidPagination")
    void shouldRejectInvalidPageOrSizeWithSafeFieldErrors(String parameter, String value) throws Exception {
        fixture.prepareLegacyProduct();
        var result = adminGet("/api", LIST, query(parameter, value), "invalid-" + parameter, 400);
        assertParameterError(result, "/api" + LIST, parameter);
    }

    static Stream<Arguments> invalidPagination() {
        return Stream.concat(Stream.of("-1", "2147483648", "1.5", "abc", "")
                        .map(value -> Arguments.of("page", value)),
                Stream.of("0", "51", "-1", "2147483648", "1.5", "abc", "")
                        .map(value -> Arguments.of("size", value)));
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void shouldRejectMalformedNonPositiveOrUnrepresentableProductIds(String id) throws Exception {
        fixture.prepareLegacyProduct();
        var result = adminGet("/api", LIST + "/" + id, query(), "invalid-product-id", 400);
        assertParameterError(result, "/api" + LIST + "/" + id, "id");
    }

    static Stream<String> invalidIds() {
        return Stream.of("0", "-1", "9223372036854775808", "1.5", "abc");
    }

    @ParameterizedTest(name = "query {0}")
    @MethodSource("rejectedQueries")
    void shouldRejectUnknownOrRepeatedListParametersAndAllQueriesOnDetailOrCategories(QueryCase input) throws Exception {
        var product = fixture.prepareLegacyProduct();
        String path = input.path().replace("{id}", Long.toString(product.id()));
        var result = adminGet("/api", path, input.parameters(), input.label(), 400);
        assertParameterError(result, "/api" + path, input.field());
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PRIVATE_VALUE, PRIVATE_PARAMETER);
    }

    static Stream<QueryCase> rejectedQueries() {
        var cases = new ArrayList<QueryCase>();
        cases.add(new QueryCase("unknown-list-parameter", LIST, query(PRIVATE_PARAMETER, PRIVATE_VALUE), null));
        cases.add(new QueryCase("detail-forbids-unknown", LIST + "/{id}", query(PRIVATE_PARAMETER, PRIVATE_VALUE), null));
        for (var parameter : List.of("name", "page", "size")) {
            var repeated = query(parameter, parameter.equals("name") ? "fone" : "2");
            repeated.add(parameter, parameter.equals("name") ? "fone" : "2");
            cases.add(new QueryCase("repeated-list-" + parameter, LIST, repeated, parameter));
            cases.add(new QueryCase("detail-forbids-" + parameter, LIST + "/{id}", query(parameter, PRIVATE_VALUE), null));
            cases.add(new QueryCase("categories-forbid-" + parameter, CATEGORIES, query(parameter, PRIVATE_VALUE), null));
        }
        cases.add(new QueryCase("categories-forbid-unknown", CATEGORIES, query(PRIVATE_PARAMETER, PRIVATE_VALUE), null));
        return cases.stream();
    }

    @ParameterizedTest(name = "public context={0}, invalid Bearer={1}")
    @MethodSource("publicControls")
    void shouldKeepThePublicCatalogNumericAndUnfilteredByTheAdministrativeNameParameter(
            String externalContext, boolean invalidBearer) throws Exception {
        var manifest = v03();
        var pages = new ArrayList<PageResponseDTO<JsonNode>>();
        for (int page = 0; page <= 3; page++) {
            var call = call(externalContext, "/products", query("name", "fone", "page", Integer.toString(page), "size", "2"));
            if (invalidBearer) call.header(HttpHeaders.AUTHORIZATION, "Bearer deliberately-invalid");
            var result = observe(call, "public-catalog-page-" + page, 200);
            pages.add(assertPage(result, page, 2, 6, productsAt(manifest, manifest.unfilteredOrder(), page, 2), false));
        }
        assertThat(assertCompletePages(pages, 2, manifest.unfilteredOrder(), item -> item.get("id").asLong()))
                .containsExactlyElementsOf(manifest.unfilteredOrder());
    }

    @ParameterizedTest(name = "public detail context={0}, invalid Bearer={1}")
    @MethodSource("publicControls")
    void shouldKeepPublicDetailCategoriesAsDistinctNames(String externalContext, boolean invalidBearer) throws Exception {
        var manifest = v03();
        var product = manifest.insertedProducts().get(4);
        var call = call(externalContext, "/products/" + product.id(), query());
        if (invalidBearer) call.header(HttpHeaders.AUTHORIZATION, "Bearer deliberately-invalid");
        var result = observe(call, "public-detail", 200);
        assertJson(json(result, 200), detail(product, manifest.categories(), false));
    }

    @ParameterizedTest(name = "public legacy context={0}, invalid Bearer={1}")
    @MethodSource("publicControls")
    void shouldKeepPublicLegacyZeroPriceAndUnassociatedDetail(String externalContext, boolean invalidBearer) throws Exception {
        var legacy = fixture.prepareWithoutCategories();
        var call = call(externalContext, "/products/" + legacy.id(), query());
        if (invalidBearer) call.header(HttpHeaders.AUTHORIZATION, "Bearer deliberately-invalid");
        var result = observe(call, "public-legacy-detail", 200);
        assertJson(json(result, 200), detail(legacy, List.of(), false));
    }

    static Stream<Arguments> publicControls() {
        return contexts().flatMap(context -> Stream.of(false, true).map(invalid -> Arguments.of(context, invalid)));
    }

    static Stream<String> contexts() {
        return Stream.of("", "/api");
    }

    private PageResponseDTO<Product> repositoryPage(String filter, int page, int size) {
        assertNoCallerTransaction();
        var before = database.snapshot();
        var accountIds = database.accountIds();
        sql.clear();
        var request = PageRequest.of(page, size);
        var result = filter == null ? repository.findAdminPage(request) : repository.findAdminPageByName(filter, request);
        assertNoCallerTransaction();
        assertThat(database.snapshot()).as("independent snapshot after direct repository comparison").isEqualTo(before);
        assertThat(database.accountIds()).isEqualTo(accountIds);
        assertThat(sql.events()).isNotEmpty().allSatisfy(event -> {
            assertThat(event.transactionActive()).isTrue();
            assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ")
                    .doesNotContain(" join ", "tb_category", "tb_product_category");
        });
        return new PageResponseDTO<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private void assertMatchingPage(PageResponseDTO<Product> repositoryPage, MvcResult response, List<Long> oracleIds)
            throws Exception {
        var http = json(response, 200);
        var httpIds = new ArrayList<Long>();
        http.get("content").forEach(item -> httpIds.add(item.get("id").asLong()));
        assertThat(repositoryPage.content()).extracting(Product::getId).containsExactlyElementsOf(oracleIds);
        assertThat(httpIds).containsExactlyElementsOf(oracleIds);
        assertThat(repositoryPage.page()).isEqualTo(http.get("page").asInt());
        assertThat(repositoryPage.size()).isEqualTo(http.get("size").asInt());
        assertThat(repositoryPage.totalElements()).isEqualTo(http.get("totalElements").asLong());
        assertThat(repositoryPage.totalPages()).isEqualTo(http.get("totalPages").asInt());
    }

    private V03Manifest v03() {
        var manifest = fixture.prepareV03();
        var persisted = database.snapshot();
        assertThat(persisted.products()).hasSize(6).containsExactlyElementsOf(manifest.insertedProducts().stream()
                .map(product -> new AdminProductDatabaseProbe.ProductRow(product.id(), product.name(), product.description(),
                        product.image(), product.price())).toList());
        assertThat(manifest.insertedProducts()).extracting(ProductData::name)
                .containsExactly("Telefone D", "Fone C", "Fone B", "Fone B", "Fone A", "Cabo");
        assertThat(persisted.categories()).hasSize(2).containsExactlyElementsOf(manifest.categories().stream()
                .map(category -> new AdminProductDatabaseProbe.CategoryRow(category.id(), category.name())).toList());
        assertThat(persisted.associations()).hasSize(2).isEqualTo(manifest.associations());
        assertThat(persisted.associations()).allSatisfy(pair ->
                assertThat(pair.productId()).isEqualTo(manifest.insertedProducts().get(4).id()));
        assertThat(manifest.filteredOrder()).hasSize(5).doesNotHaveDuplicates();
        assertThat(manifest.unfilteredOrder()).hasSize(6).doesNotHaveDuplicates();
        System.out.println("T014_V03 products=6 filtered=5 categories=2 associations=2 oracle=fixture-manifest");
        return manifest;
    }

    private static List<ProductData> productsAt(V03Manifest manifest, List<Long> ids, int page, int size) {
        return ids.stream().skip((long) page * size).limit(size)
                .map(id -> manifest.insertedProducts().stream().filter(product -> product.id() == id)
                        .findFirst().orElseThrow()).toList();
    }

    private MvcResult adminGet(String externalContext, String path, MultiValueMap<String, String> query,
                               String label, int expectedStatus) throws Exception {
        return observe(call(externalContext, path, query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token),
                label, expectedStatus);
    }

    private MockHttpServletRequestBuilder call(String externalContext, String path, MultiValueMap<String, String> query) {
        return get(externalContext + path).contextPath(externalContext).servletPath(path)
                .queryParams(query).accept(MediaType.APPLICATION_JSON);
    }

    private MvcResult observe(MockHttpServletRequestBuilder call, String label, int expectedStatus) throws Exception {
        assertNoCallerTransaction();
        var before = database.snapshot();
        var accountsBefore = database.accountIds();
        sql.clear();
        var result = mvc.perform(call).andReturn();
        var after = database.snapshot();
        assertThat(after).as("independent five-table snapshot before cleanup").isEqualTo(before);
        assertThat(database.accountIds()).isEqualTo(accountsBefore);
        assertThat(sql.events()).as("real filter or public query produces observable Hibernate SQL")
                .isNotEmpty().noneMatch(event -> isDml(event.sql()));
        assertThat(result.getHandler()).as("authorized/public request reaches MVC, not a security rejection").isNotNull();
        assertNoCallerTransaction();
        System.out.printf("T014_HTTP case=%s expected=%d actual=%d handler=%s cause=%s snapshotEqual=%s hibernateDml=0%n",
                label, expectedStatus, result.getResponse().getStatus(), result.getHandler().getClass().getSimpleName(),
                result.getResolvedException() == null ? "NONE" : result.getResolvedException().getClass().getSimpleName(),
                before.equals(after));
        return result;
    }

    private PageResponseDTO<JsonNode> assertPage(MvcResult result, int page, int size, long total,
                                               List<ProductData> products, boolean admin) {
        var actual = json(result, 200);
        var expected = mapper.createObjectNode();
        expected.put("page", page).put("size", size).put("totalElements", total)
                .put("totalPages", (total + size - 1) / size);
        var content = expected.putArray("content");
        products.forEach(product -> content.add(item(product, admin)));
        assertJson(actual, expected);
        var items = new ArrayList<JsonNode>();
        actual.get("content").forEach(items::add);
        return new PageResponseDTO<>(items, actual.get("page").asInt(), actual.get("size").asInt(),
                actual.get("totalElements").asLong(), actual.get("totalPages").asInt());
    }

    private ObjectNode item(ProductData product, boolean admin) {
        var item = mapper.createObjectNode();
        item.put("id", product.id()).put("name", product.name()).put("image", product.image());
        if (admin) item.put("price", product.price().setScale(2).toPlainString());
        else item.put("price", product.price());
        return item;
    }

    private ObjectNode detail(ProductData product, List<CategoryData> associated, boolean admin) {
        var expected = item(product, admin);
        expected.put("description", product.description());
        if (admin) expected.set("categories", categories(associated));
        else {
            var names = expected.putArray("categories");
            associated.stream().map(CategoryData::name).distinct().sorted().forEach(names::add);
        }
        return expected;
    }

    private JsonNode categories(List<CategoryData> values) {
        var expected = mapper.createArrayNode();
        values.stream().sorted(Comparator.comparing(CategoryData::name).thenComparingLong(CategoryData::id))
                .forEach(category -> expected.addObject().put("id", category.id()).put("name", category.name()));
        return expected;
    }

    private JsonNode json(MvcResult result, int status) {
        assertThat(result.getResponse().getStatus()).as("contractual HTTP status").isEqualTo(status);
        assertThat(result.getResponse().getContentType()).as("contractual JSON response").startsWith(MediaType.APPLICATION_JSON_VALUE);
        var json = assertDoesNotThrow(() -> mapper.readTree(result.getResponse().getContentAsByteArray()), "response must be JSON");
        assertThat(json).as("contractual JSON body").isNotNull();
        return json;
    }

    private void assertJson(JsonNode actual, JsonNode expected) {
        // Canonical JSON parsing avoids comparing Jackson's Java int/long node implementation choices.
        var canonical = assertDoesNotThrow(() -> mapper.readTree(expected.toString()));
        assertThat(actual).as("exact contract fields and independent fixture values").isEqualTo(canonical);
    }

    private ObjectNode error(int status, String message, String path) {
        return mapper.createObjectNode().put("status", status).put("error", HttpStatus.valueOf(status).getReasonPhrase())
                .put("message", message).put("path", path);
    }

    private void assertParameterError(MvcResult result, String path, String field) {
        var actual = json(result, 400);
        assertThat(actual.isObject()).isTrue();
        var fields = new HashSet<String>();
        actual.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("status", "error", "message", "path", "errors");
        var expected = error(400, "Invalid product maintenance data", path);
        expected.fieldNames().forEachRemaining(name -> assertThat(actual.get(name)).isEqualTo(expected.get(name)));
        var errors = actual.get("errors");
        assertThat(errors.isArray()).isTrue();
        assertThat(errors).isNotEmpty();
        errors.forEach(diagnosis -> {
            var diagnosisFields = new HashSet<String>();
            diagnosis.fieldNames().forEachRemaining(diagnosisFields::add);
            assertThat(diagnosisFields).containsExactlyInAnyOrder("field", "code", "message");
            assertThat(diagnosis.get("code").asText()).isEqualTo("INVALID_PARAMETER");
            assertThat(diagnosis.get("message").isTextual()).isTrue();
            assertThat(diagnosis.get("message").asText()).isNotBlank();
            if (field != null) assertThat(diagnosis.get("field").asText()).isEqualTo(field);
            else assertThat(diagnosis.get("field").asText()).isIn("body", "id", "page", "size", "name");
        });
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    private static MultiValueMap<String, String> query(String... entries) {
        var result = new LinkedMultiValueMap<String, String>();
        for (int i = 0; i < entries.length; i += 2) result.add(entries[i], entries[i + 1]);
        return result;
    }

    private static boolean isDml(String statement) {
        var sql = statement.stripLeading().toLowerCase(Locale.ROOT);
        return Stream.of("insert ", "update ", "delete ", "merge ").anyMatch(sql::startsWith);
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

    enum SearchScenario { ACCENTS, SPACES, SPECIAL }
    enum LegacyResource { LIST, DETAIL, CATEGORIES }
    record QueryCase(String label, String path, MultiValueMap<String, String> parameters, String field) {
        @Override public String toString() { return label; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlProbeConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation querySqlObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }
        @Bean HibernatePropertiesCustomizer querySqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
