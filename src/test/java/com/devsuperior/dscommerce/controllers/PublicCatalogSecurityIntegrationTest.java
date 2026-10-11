package com.devsuperior.dscommerce.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
class PublicCatalogSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired private ObjectMapper mapper;
    @Autowired private FilterChainProxy filters;
    @Autowired private JdbcTemplate jdbc;

    private static final String INVALID_BEARER = "Bearer not-a-valid-jwt";

    @Test
    void anonymousRequestShouldListProductsWithDefaultPagination() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalElements").value(52))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.content.length()").value(12));
    }

    @Test
    void anonymousRequestShouldListProductsWithExplicitPagination() throws Exception {
        mockMvc.perform(get("/products")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(52))
                .andExpect(jsonPath("$.totalPages").value(26))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void anonymousRequestShouldPreserveNameAscThenIdAscOrdering() throws Exception {
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", contains(
                        "Duplicate", "Duplicate", "Product 01", "Product 02")))
                .andExpect(jsonPath("$.content[*].id", contains(51, 52, 50, 49)));
    }

    @Test
    void anonymousRequestShouldReturnEmptyContentForOutOfRangePage() throws Exception {
        mockMvc.perform(get("/products")
                        .param("page", "999")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(999))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalElements").value(52))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.content", empty()));
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 12, 'page must be greater than or equal to 0'",
            "0, 0, 'size must be between 1 and 50'",
            "0, 51, 'size must be between 1 and 50'"
    })
    void anonymousRequestShouldPreservePaginationValidationErrors(
            String page,
            String size,
            String expectedMessage
    ) throws Exception {
        mockMvc.perform(get("/products")
                        .param("page", page)
                        .param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.path").value("/products"));
    }

    @ParameterizedTest(name = "default catalog, invalid Bearer={0}")
    @ValueSource(booleans = {false, true})
    void shouldPreserveDefaultPaginationAndExactCatalogShape(boolean invalidBearer) throws Exception {
        assertCatalogPage(catalogPage(invalidBearer, null, null), 0, 12, 52, 5, 12);
    }

    @ParameterizedTest(name = "catalog page={1}, size={2}, invalid Bearer={0}")
    @MethodSource("explicitPages")
    void shouldPreserveExplicitPaginationIncludingBothSizeLimits(
            boolean invalidBearer, int page, int size, int totalPages, int count) throws Exception {
        assertCatalogPage(catalogPage(invalidBearer, page, size), page, size, 52, totalPages, count);
    }

    static Stream<Arguments> explicitPages() {
        return Stream.of(false, true).flatMap(bearer -> Stream.of(
                Arguments.of(bearer, 1, 2, 26, 2),
                Arguments.of(bearer, 4, 12, 5, 4),
                Arguments.of(bearer, 0, 1, 52, 1),
                Arguments.of(bearer, 0, 50, 2, 50),
                Arguments.of(bearer, 1, 50, 2, 2)));
    }

    @ParameterizedTest(name = "independent defaults, invalid Bearer={0}")
    @ValueSource(booleans = {false, true})
    void shouldApplyEachMissingPaginationDefaultIndependently(boolean invalidBearer) throws Exception {
        assertCatalogPage(catalogPage(invalidBearer, 1, null), 1, 12, 52, 5, 12);
        assertCatalogPage(catalogPage(invalidBearer, null, 2), 0, 2, 52, 26, 2);
    }

    @ParameterizedTest(name = "all 52 products in order, invalid Bearer={0}")
    @ValueSource(booleans = {false, true})
    void shouldPreserveNameAscThenIdAscAcrossAllPagesWithoutMissingOrRepeatingProducts(
            boolean invalidBearer) throws Exception {
        var products = new ArrayList<JsonNode>();
        for (int page = 0; page < 5; page++) {
            var response = catalogPage(invalidBearer, page, 12);
            assertCatalogPage(response, page, 12, 52, 5, page == 4 ? 4 : 12);
            response.path("content").forEach(products::add);
        }
        assertThat(products).hasSize(52);
        var expectedIds = new ArrayList<Long>(List.of(51L, 52L));
        for (long id = 50; id >= 1; id--) expectedIds.add(id);
        assertThat(products).extracting(product -> product.path("id").longValue())
                .containsExactlyElementsOf(expectedIds).doesNotHaveDuplicates();
        assertThat(products.subList(0, 4)).extracting(product -> product.path("name").textValue())
                .containsExactly("Duplicate", "Duplicate", "Product 01", "Product 02");
        var sorted = new ArrayList<>(products);
        sorted.sort(Comparator.comparing((JsonNode product) -> product.path("name").textValue())
                .thenComparingLong(product -> product.path("id").longValue()));
        assertThat(products).containsExactlyElementsOf(sorted);
    }

    @ParameterizedTest(name = "out of range page, invalid Bearer={0}")
    @ValueSource(booleans = {false, true})
    void shouldPreserveEmptyPageAndMetadataWithOrWithoutInvalidBearer(boolean invalidBearer) throws Exception {
        assertCatalogPage(catalogPage(invalidBearer, 999, 12), 999, 12, 52, 5, 0);
    }

    @ParameterizedTest(name = "invalid page={1}, size={2}, invalid Bearer={0}")
    @MethodSource("invalidPages")
    void shouldPreserveExactPaginationErrorEnvelopeAndMessage(
            boolean invalidBearer, String page, String size, String message) throws Exception {
        assertPublicFilterChain(filters);
        var response = mockMvc.perform(catalogRequest(invalidBearer).param("page", page).param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andReturn().getResponse();
        var expected = mapper.createObjectNode().put("status", 400).put("error", "Bad Request")
                .put("message", message).put("path", "/products");
        assertThat(mapper.readTree(response.getContentAsString())).isEqualTo(expected);
        assertSeedCount();
    }

    static Stream<Arguments> invalidPages() {
        return Stream.of(false, true).flatMap(bearer -> Stream.of(
                Arguments.of(bearer, "-1", "12", "page must be greater than or equal to 0"),
                Arguments.of(bearer, "0", "0", "size must be between 1 and 50"),
                Arguments.of(bearer, "0", "51", "size must be between 1 and 50")));
    }

    private JsonNode catalogPage(boolean invalidBearer, Integer page, Integer size) throws Exception {
        assertPublicFilterChain(filters);
        var request = catalogRequest(invalidBearer);
        if (page != null) request.param("page", page.toString());
        if (size != null) request.param("size", size.toString());
        var result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andReturn();
        assertSeedCount();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertSeedCount() {
        assertThat(jdbc.queryForObject("select count(*) from tb_product", Long.class)).isEqualTo(52L);
    }

    private static MockHttpServletRequestBuilder catalogRequest(boolean invalidBearer) {
        var request = get("/products");
        if (invalidBearer) request.header(HttpHeaders.AUTHORIZATION, INVALID_BEARER);
        return request;
    }

    private static void assertPublicFilterChain(FilterChainProxy filters) {
        var request = new MockHttpServletRequest("GET", "/products");
        request.setServletPath("/products");
        var selected = filters.getFilterChains().stream().filter(chain -> chain.matches(request))
                .findFirst().orElseThrow();
        assertThat(selected.getFilters()).anyMatch(AuthorizationFilter.class::isInstance)
                .noneMatch(BearerTokenAuthenticationFilter.class::isInstance);
    }

    private static void assertCatalogPage(JsonNode response, int page, int size,
                                          long total, int totalPages, int count) {
        assertFields(response, "content", "page", "size", "totalElements", "totalPages");
        for (var field : List.of("page", "size", "totalElements", "totalPages")) {
            assertThat(response.path(field).isIntegralNumber()).as(field).isTrue();
        }
        assertThat(response.path("page").intValue()).isEqualTo(page);
        assertThat(response.path("size").intValue()).isEqualTo(size);
        assertThat(response.path("totalElements").longValue()).isEqualTo(total);
        assertThat(response.path("totalPages").intValue()).isEqualTo(totalPages);
        assertThat(response.path("content").isArray()).isTrue();
        assertThat(response.path("content").size()).isEqualTo(count);
        for (var product : response.path("content")) {
            assertFields(product, "id", "name", "image", "price");
            assertThat(product.path("id").isIntegralNumber()).isTrue();
            assertThat(product.path("id").longValue()).isPositive();
            for (var field : List.of("name", "image")) {
                assertThat(product.path(field).isTextual()).as(field).isTrue();
                assertThat(product.path(field).textValue()).isNotBlank();
            }
            assertThat(product.path("price").isNumber()).isTrue();
            assertThat(product.path("price").decimalValue()).isGreaterThanOrEqualTo(java.math.BigDecimal.ZERO);
        }
    }

    private static void assertFields(JsonNode object, String... expected) {
        assertThat(object.isObject()).isTrue();
        var names = new ArrayList<String>();
        object.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder(expected);
    }

    /** Empty-catalog edge case in a separate real context, without deleting or changing the public seed. */
    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest(properties = {
            "spring.sql.init.mode=never",
            "spring.jpa.properties.hibernate.hbm2ddl.import_files="
    })
    @AutoConfigureMockMvc(addFilters = true)
    @ActiveProfiles("test")
    class EmptyCatalog {
        @Autowired private MockMvc emptyMvc;
        @Autowired private ObjectMapper emptyMapper;
        @Autowired private FilterChainProxy emptyFilters;
        @Autowired private JdbcTemplate emptyJdbc;

        @DynamicPropertySource
        static void isolatedEmptyDatabase(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url", () -> "jdbc:h2:mem:catalog-empty-" + UUID.randomUUID()
                    + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        }

        @ParameterizedTest(name = "empty catalog, invalid Bearer={0}")
        @ValueSource(booleans = {false, true})
        void shouldReturnAnEmptyCatalogWithCoherentDefaultPagination(boolean invalidBearer) throws Exception {
            assertThat(emptyJdbc.queryForObject("select count(*) from tb_product", Long.class)).isZero();
            assertPublicFilterChain(emptyFilters);
            var result = emptyMvc.perform(catalogRequest(invalidBearer))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                    .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                    .andReturn();
            assertCatalogPage(emptyMapper.readTree(result.getResponse().getContentAsString()), 0, 12, 0, 0, 0);
            assertThat(emptyJdbc.queryForObject("select count(*) from tb_product", Long.class)).isZero();
        }
    }
}
