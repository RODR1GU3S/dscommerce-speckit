package com.devsuperior.dscommerce.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
class PublicProductDetailSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired private ObjectMapper mapper;
    @Autowired private FilterChainProxy filters;
    @Autowired private JdbcTemplate jdbc;

    private static final String INVALID_BEARER = "Bearer not-a-valid-jwt";

    @Test
    void anonymousRequestShouldReturnExistingProductDetails() throws Exception {
        mockMvc.perform(get("/products/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(
                        "id", "name", "description", "image", "price", "categories")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Product 50"))
                .andExpect(jsonPath("$.description").value("Description for Product 50"))
                .andExpect(jsonPath("$.image").value("https://cdn.example.com/products/50.jpg"))
                .andExpect(jsonPath("$.price").value(500.00))
                .andExpect(jsonPath("$.categories", contains("Computers", "Electronics")));
    }

    @Test
    void anonymousRequestShouldReturnNotFoundForMissingProduct() throws Exception {
        mockMvc.perform(get("/products/{id}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(
                        "status", "error", "message", "path")))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product not found"))
                .andExpect(jsonPath("$.path").value("/products/99999"));
    }

    @ParameterizedTest(name = "selected product={1}, invalid Bearer={0}")
    @MethodSource("existingProducts")
    void shouldPreserveExactDetailShapeAndValuesForTheSelectedProduct(boolean invalidBearer, long id)
            throws Exception {
        assertSeedUnchanged();
        assertSeedProduct(detail(invalidBearer, Long.toString(id)), id);
        assertSeedUnchanged();
    }

    static Stream<Arguments> existingProducts() {
        return Stream.of(false, true).flatMap(bearer -> Stream.of(1L, 2L, 3L, 50L, 51L, 52L)
                .map(id -> Arguments.of(bearer, id)));
    }

    @ParameterizedTest(name = "catalog selection to detail, invalid Bearer={0}")
    @ValueSource(booleans = {false, true})
    void shouldNavigateFromEveryCatalogIdToItsOwnDetailsWithoutChangingCatalogItems(boolean invalidBearer)
            throws Exception {
        var selectedIds = new ArrayList<Long>();
        for (int page = 0; page < 2; page++) {
            assertPublicFilterChain(filters, "/products");
            var result = mockMvc.perform(publicRequest("/products", invalidBearer)
                            .param("page", Integer.toString(page)).param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                    .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)).andReturn();
            var catalog = mapper.readTree(result.getResponse().getContentAsString());
            assertThat(catalog.path("content").isArray()).isTrue();
            assertThat(catalog.path("content").size()).isEqualTo(page == 0 ? 50 : 2);
            for (var item : catalog.path("content")) {
                assertFields(item, "id", "name", "image", "price");
                var id = item.path("id").longValue();
                selectedIds.add(id);
                var details = detail(invalidBearer, Long.toString(id));
                assertSeedProduct(details, id);
                for (var field : List.of("id", "name", "image")) {
                    assertThat(details.path(field)).as(field).isEqualTo(item.path(field));
                }
                assertThat(details.path("price").decimalValue())
                        .isEqualByComparingTo(item.path("price").decimalValue());
            }
        }
        assertThat(selectedIds).hasSize(52).doesNotHaveDuplicates();
        assertSeedUnchanged();
    }

    @ParameterizedTest(name = "missing id={1}, invalid Bearer={0}")
    @MethodSource("missingProducts")
    void shouldPreserveNotFoundEnvelopeWithoutReturningAnotherProductsData(boolean invalidBearer, long id)
            throws Exception {
        var path = "/products/" + id;
        assertPublicFilterChain(filters, path);
        var response = mockMvc.perform(publicRequest(path, invalidBearer))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)).andReturn().getResponse();
        var expected = mapper.createObjectNode().put("status", 404).put("error", "Not Found")
                .put("message", "Product not found").put("path", path);
        assertThat(mapper.readTree(response.getContentAsString())).isEqualTo(expected);
        assertSeedUnchanged();
    }

    static Stream<Arguments> missingProducts() {
        return Stream.of(false, true).flatMap(bearer -> Stream.of(99999L, 0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)
                .map(id -> Arguments.of(bearer, id)));
    }

    @ParameterizedTest(name = "Long representation={1}, invalid Bearer={0}")
    @MethodSource("equivalentIdentifiers")
    void shouldPreserveExistingLongConversionForEquivalentIdentifiers(boolean invalidBearer, String id)
            throws Exception {
        assertSeedProduct(detail(invalidBearer, id), 1L);
        assertSeedUnchanged();
    }

    static Stream<Arguments> equivalentIdentifiers() {
        return Stream.of(false, true).flatMap(bearer -> Stream.of("01", "+1")
                .map(id -> Arguments.of(bearer, id)));
    }

    @ParameterizedTest(name = "unconvertible id={1}, invalid Bearer={0}")
    @MethodSource("unconvertibleIdentifiers")
    void shouldPreserveBadRequestForIdentifiersOutsideLongConversion(boolean invalidBearer, String id)
            throws Exception {
        var path = "/products/" + id;
        assertPublicFilterChain(filters, path);
        mockMvc.perform(publicRequest(path, invalidBearer))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
        assertSeedUnchanged();
    }

    static Stream<Arguments> unconvertibleIdentifiers() {
        return Stream.of(false, true).flatMap(bearer -> Stream.of("abc", "1.5", "9223372036854775808")
                .map(id -> Arguments.of(bearer, id)));
    }

    private JsonNode detail(boolean invalidBearer, String id) throws Exception {
        return detail(mockMvc, mapper, filters, invalidBearer, id);
    }

    private static JsonNode detail(MockMvc mvc, ObjectMapper mapper, FilterChainProxy filters,
                                   boolean invalidBearer, String id) throws Exception {
        var path = "/products/" + id;
        assertPublicFilterChain(filters, path);
        var response = mvc.perform(publicRequest(path, invalidBearer))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)).andReturn().getResponse();
        return mapper.readTree(response.getContentAsString());
    }

    private static MockHttpServletRequestBuilder publicRequest(String path, boolean invalidBearer) {
        var request = get(path);
        if (invalidBearer) request.header(HttpHeaders.AUTHORIZATION, INVALID_BEARER);
        return request;
    }

    private static void assertPublicFilterChain(FilterChainProxy filters, String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        var selected = filters.getFilterChains().stream().filter(chain -> chain.matches(request))
                .findFirst().orElseThrow();
        assertThat(selected.getFilters()).anyMatch(AuthorizationFilter.class::isInstance)
                .noneMatch(BearerTokenAuthenticationFilter.class::isInstance);
    }

    private void assertSeedUnchanged() {
        assertThat(jdbc.queryForObject("select count(*) from tb_product", Long.class)).isEqualTo(52L);
        assertThat(jdbc.queryForObject("select count(*) from tb_category", Long.class)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("select count(*) from tb_product_category", Long.class)).isEqualTo(3L);
    }

    private static void assertSeedProduct(JsonNode details, long id) {
        assertFields(details, "id", "name", "description", "image", "price", "categories");
        assertThat(details.path("id").isIntegralNumber()).isTrue();
        assertThat(details.path("id").longValue()).isEqualTo(id);
        var number = String.format("%02d", 51 - id);
        var name = id <= 50 ? "Product " + number : "Duplicate";
        var image = id <= 50 ? number : id == 51 ? "duplicate-a" : "duplicate-b";
        var price = id <= 50 ? BigDecimal.valueOf((51 - id) * 10)
                : new BigDecimal(id == 51 ? "99.90" : "109.90");
        for (var field : List.of("name", "description", "image")) {
            assertThat(details.path(field).isTextual()).as(field).isTrue();
            assertThat(details.path(field).textValue()).as(field).isNotBlank();
        }
        assertThat(details.path("name").textValue()).isEqualTo(name);
        assertThat(details.path("description").textValue()).isEqualTo("Description for " + name);
        assertThat(details.path("image").textValue()).isEqualTo("https://cdn.example.com/products/" + image + ".jpg");
        assertThat(details.path("price").isNumber()).isTrue();
        assertThat(details.path("price").decimalValue()).isEqualByComparingTo(price)
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(details.path("categories").isArray()).isTrue();
        var names = new ArrayList<String>();
        for (var category : details.path("categories")) {
            assertThat(category.isTextual()).isTrue();
            assertThat(category.textValue()).isNotBlank();
            names.add(category.textValue());
        }
        var expected = id == 1 ? List.of("Computers", "Electronics")
                : id == 2 ? List.of("Accessories") : List.<String>of();
        assertThat(names).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    }

    private static void assertFields(JsonNode object, String... expected) {
        assertThat(object.isObject()).isTrue();
        var names = new ArrayList<String>();
        object.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder(expected);
    }

    /** Duplicate-name fixture lives only in its own database; the shared seed is read-only. */
    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest
    @AutoConfigureMockMvc(addFilters = true)
    @ActiveProfiles("test")
    class DuplicateCategoryNames {
        private static final String DATABASE_URL = "jdbc:h2:mem:detail-duplicates-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

        @Autowired private MockMvc isolatedMvc;
        @Autowired private ObjectMapper isolatedMapper;
        @Autowired private FilterChainProxy isolatedFilters;
        @Autowired private JdbcTemplate isolatedJdbc;

        @DynamicPropertySource
        static void isolatedDatabase(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url", () -> DATABASE_URL);
        }

        @BeforeEach
        void addDistinctCategoryWithAnExistingName() {
            assertThat(isolatedJdbc.queryForObject("select count(*) from tb_product", Long.class)).isEqualTo(52L);
            assertThat(isolatedJdbc.queryForObject("select count(*) from tb_category", Long.class)).isEqualTo(3L);
            isolatedJdbc.update("insert into tb_category (id, name) values (4, 'Electronics')");
            isolatedJdbc.update("insert into tb_product_category (product_id, category_id) values (1, 4)");
        }

        @AfterEach
        void removeOnlyTheIsolatedFixture() {
            isolatedJdbc.update("delete from tb_product_category where product_id = 1 and category_id = 4");
            isolatedJdbc.update("delete from tb_category where id = 4");
            assertThat(isolatedJdbc.queryForObject("select count(*) from tb_category", Long.class)).isEqualTo(3L);
            assertThat(isolatedJdbc.queryForObject("select count(*) from tb_product_category", Long.class)).isEqualTo(3L);
        }

        @ParameterizedTest(name = "duplicate category names, invalid Bearer={0}")
        @ValueSource(booleans = {false, true})
        void shouldReturnAllCategoryNamesOnlyOnceDespiteDistinctCategoryIds(boolean invalidBearer) throws Exception {
            var persistedNames = isolatedJdbc.queryForList("select c.name from tb_category c "
                    + "join tb_product_category pc on pc.category_id = c.id where pc.product_id = 1 "
                    + "order by c.id", String.class);
            assertThat(persistedNames).containsExactly("Computers", "Electronics", "Electronics");
            assertThat(isolatedJdbc.queryForObject("select count(distinct category_id) from tb_product_category "
                    + "where product_id = 1", Long.class)).isEqualTo(3L);
            assertSeedProduct(detail(isolatedMvc, isolatedMapper, isolatedFilters, invalidBearer, "1"), 1L);
            assertSeedProduct(detail(isolatedMvc, isolatedMapper, isolatedFilters, invalidBearer, "2"), 2L);
            assertThat(isolatedJdbc.queryForObject("select count(*) from tb_product", Long.class)).isEqualTo(52L);
        }
    }
}
