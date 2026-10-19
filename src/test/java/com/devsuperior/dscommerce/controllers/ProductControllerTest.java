package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.SecurityConfig;
import com.devsuperior.dscommerce.config.OrderClockConfig;
import com.devsuperior.dscommerce.config.OrderSecuritySliceTestConfig;
import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.dto.ProductCatalogItemDTO;
import com.devsuperior.dscommerce.dto.ProductDetailDTO;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.fixtures.ProductFactory;
import com.devsuperior.dscommerce.services.ProductCatalogService;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class, properties = "test.order-security.public-mvc-slice=true")
@Import({SecurityConfig.class, OrderClockConfig.class, OrderSecuritySliceTestConfig.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private ProductCatalogService productCatalogService;

    @Test
    void getProductDetailsShouldReturnSelectedProductWithCompleteDetailJson() throws Exception {
        ProductDetailDTO response = new ProductDetailDTO(
                42L,
                "Mechanical Keyboard",
                "Hot-swappable mechanical keyboard",
                "https://cdn.example.com/products/42.jpg",
                new BigDecimal("349.90"),
                List.of("Computers", "Electronics")
        );
        when(productCatalogService.findProductDetails(42L)).thenReturn(response);

        mockMvc.perform(get("/products/{id}", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(
                        "id", "name", "description", "image", "price", "categories")))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.name").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.description").value("Hot-swappable mechanical keyboard"))
                .andExpect(jsonPath("$.image").value("https://cdn.example.com/products/42.jpg"))
                .andExpect(jsonPath("$.price").value(349.90))
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.categories[0]").value("Computers"))
                .andExpect(jsonPath("$.categories[1]").value("Electronics"));
    }

    @Test
    void getProductDetailsShouldReturnNotFoundErrorWhenProductDoesNotExist() throws Exception {
        Long id = 99999L;
        String message = "Product not found";
        when(productCatalogService.findProductDetails(id))
                .thenThrow(new ProductNotFoundException(message));

        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(
                        "status", "error", "message", "path")))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value("/products/99999"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.image").doesNotExist())
                .andExpect(jsonPath("$.price").doesNotExist())
                .andExpect(jsonPath("$.categories").doesNotExist());
    }

    @Test
    void listProductsShouldUseDefaultPaginationWhenQueryParametersAreMissing() throws Exception {
        Product product = ProductFactory.createProduct(1L, "Notebook");
        PageResponseDTO<ProductCatalogItemDTO> response = new PageResponseDTO<>(
                List.of(new ProductCatalogItemDTO(product)),
                0,
                12,
                1,
                1
        );

        when(productCatalogService.listProducts(null, 0, 12)).thenReturn(response);

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void listProductsShouldUseExplicitPaginationWhenQueryParametersAreProvided() throws Exception {
        PageResponseDTO<ProductCatalogItemDTO> response = new PageResponseDTO<>(
                List.of(),
                1,
                10,
                52,
                6
        );

        when(productCatalogService.listProducts(null, 1, 10)).thenReturn(response);

        mockMvc.perform(get("/products")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(52))
                .andExpect(jsonPath("$.totalPages").value(6))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void listProductsShouldReturnEmptyContentForOutOfRangePage() throws Exception {
        PageResponseDTO<ProductCatalogItemDTO> response = new PageResponseDTO<>(
                List.of(),
                999,
                12,
                52,
                5
        );

        when(productCatalogService.listProducts(null, 999, 12)).thenReturn(response);

        mockMvc.perform(get("/products")
                        .param("page", "999")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(999))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalElements").value(52))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void listProductsShouldExposeOnlyCatalogItemFields() throws Exception {
        ProductCatalogItemDTO product = new ProductCatalogItemDTO(
                7L,
                "Mechanical Keyboard",
                "https://cdn.example.com/products/7.jpg",
                new BigDecimal("349.90")
        );
        PageResponseDTO<ProductCatalogItemDTO> response = new PageResponseDTO<>(
                List.of(product),
                0,
                12,
                1,
                1
        );

        when(productCatalogService.listProducts(null, 0, 12)).thenReturn(response);

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].*", hasSize(4)))
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].name").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.content[0].image").value("https://cdn.example.com/products/7.jpg"))
                .andExpect(jsonPath("$.content[0].price").value(349.90))
                .andExpect(jsonPath("$.content[0].description").doesNotExist())
                .andExpect(jsonPath("$.content[0].categories").doesNotExist());
    }

    @Test
    void listProductsShouldExposeOnlyCatalogItemFieldsForEveryProduct() throws Exception {
        PageResponseDTO<ProductCatalogItemDTO> response = new PageResponseDTO<>(
                List.of(
                        new ProductCatalogItemDTO(1L, "Notebook", "https://cdn.example.com/products/1.jpg", new BigDecimal("10.00")),
                        new ProductCatalogItemDTO(2L, "Mouse", "https://cdn.example.com/products/2.jpg", new BigDecimal("20.00"))
                ),
                0,
                12,
                2,
                1
        );

        when(productCatalogService.listProducts(null, 0, 12)).thenReturn(response);

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].keys()", containsInAnyOrder("id", "name", "image", "price")))
                .andExpect(jsonPath("$.content[1].keys()", containsInAnyOrder("id", "name", "image", "price")));
    }

    @Test
    void listProductsShouldPreserveNameAscThenIdAscOrderAcrossPageBoundaries() throws Exception {
        PageResponseDTO<ProductCatalogItemDTO> firstPage = new PageResponseDTO<>(
                List.of(
                        new ProductCatalogItemDTO(51L, "Duplicate", "https://cdn.example.com/products/duplicate-a.jpg", new BigDecimal("99.90")),
                        new ProductCatalogItemDTO(52L, "Duplicate", "https://cdn.example.com/products/duplicate-b.jpg", new BigDecimal("109.90"))
                ),
                0,
                2,
                52,
                26
        );
        PageResponseDTO<ProductCatalogItemDTO> secondPage = new PageResponseDTO<>(
                List.of(
                        new ProductCatalogItemDTO(50L, "Product 01", "https://cdn.example.com/products/01.jpg", new BigDecimal("10.00")),
                        new ProductCatalogItemDTO(49L, "Product 02", "https://cdn.example.com/products/02.jpg", new BigDecimal("20.00"))
                ),
                1,
                2,
                52,
                26
        );

        when(productCatalogService.listProducts(null, 0, 2)).thenReturn(firstPage);
        when(productCatalogService.listProducts(null, 1, 2)).thenReturn(secondPage);

        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Duplicate"))
                .andExpect(jsonPath("$.content[0].id").value(51))
                .andExpect(jsonPath("$.content[1].name").value("Duplicate"))
                .andExpect(jsonPath("$.content[1].id").value(52));

        mockMvc.perform(get("/products")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Product 01"))
                .andExpect(jsonPath("$.content[0].id").value(50))
                .andExpect(jsonPath("$.content[1].name").value("Product 02"))
                .andExpect(jsonPath("$.content[1].id").value(49));
    }

    @Test
    void listProductsShouldForwardNameUnchangedWithPagination() throws Exception {
        String name = " FONE ";
        var response = new PageResponseDTO<>(List.of(new ProductCatalogItemDTO(
                42L, " Fone Azul ", "fone.jpg", new BigDecimal("123.45"))), 1, 2, 5, 3);
        when(productCatalogService.listProducts(name, 1, 2)).thenReturn(response);

        mockMvc.perform(get("/products").param("name", name).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(
                        "content", "page", "size", "totalElements", "totalPages")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].keys()", containsInAnyOrder("id", "name", "image", "price")))
                .andExpect(jsonPath("$.content[0].id").value(42))
                .andExpect(jsonPath("$.content[0].name").value(" Fone Azul "))
                .andExpect(jsonPath("$.content[0].image").value("fone.jpg"))
                .andExpect(jsonPath("$.content[0].price").isNumber())
                .andExpect(jsonPath("$.content[0].price").value(123.45));
        verify(productCatalogService).listProducts(name, 1, 2);
    }

    @Test
    void listProductsShouldReturnBadRequestWhenPageIsNegative() throws Exception {
        mockMvc.perform(get("/products")
                        .param("page", "-1")
                        .param("size", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("page must be greater than or equal to 0"))
                .andExpect(jsonPath("$.path").value("/products"));
    }

    @Test
    void listProductsShouldReturnBadRequestWhenSizeIsBelowMinimum() throws Exception {
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("size must be between 1 and 50"))
                .andExpect(jsonPath("$.path").value("/products"));
    }

    @Test
    void listProductsShouldReturnBadRequestWhenSizeIsAboveMaximum() throws Exception {
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("size must be between 1 and 50"))
                .andExpect(jsonPath("$.path").value("/products"));
    }
    @ParameterizedTest(name = "{0}")
    @MethodSource("paginationDefaultScenarios")
    @Tag("feature006-t021")
    void listProductsShouldBindNameAndIndependentDefaultsWithExactPublicTypes(
            String label, String name, PaginationDefaults pagination) throws Exception {
        var response = new PageResponseDTO<>(List.of(
                new ProductCatalogItemDTO(42L, "  Fone  Azul  ", "fone.jpg", new BigDecimal("123.45")),
                new ProductCatalogItemDTO(43L, "Fone Zero", "zero.jpg", new BigDecimal("0.00"))),
                pagination.page(), pagination.size(), pagination.page() * pagination.size() + 2,
                pagination.page() + 1);
        when(productCatalogService.listProducts(name, pagination.page(), pagination.size())).thenReturn(response);
        var request = get("/products").accept(MediaType.APPLICATION_JSON);
        if (name != null) request.param("name", name);
        if (pagination.pageParameter() != null) request.param("page", pagination.pageParameter());
        if (pagination.sizeParameter() != null) request.param("size", pagination.sizeParameter());

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(
                        "content", "page", "size", "totalElements", "totalPages")))
                .andExpect(jsonPath("$.page").isNumber())
                .andExpect(jsonPath("$.page").value(pagination.page()))
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.size").value(pagination.size()))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalElements").value(pagination.page() * pagination.size() + 2))
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.totalPages").value(pagination.page() + 1))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].keys()", containsInAnyOrder("id", "name", "image", "price")))
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].id").value(42))
                .andExpect(jsonPath("$.content[0].name").isString())
                .andExpect(jsonPath("$.content[0].name").value("  Fone  Azul  "))
                .andExpect(jsonPath("$.content[0].image").isString())
                .andExpect(jsonPath("$.content[0].image").value("fone.jpg"))
                .andExpect(jsonPath("$.content[0].price").isNumber())
                .andExpect(jsonPath("$.content[0].price").value(123.45))
                .andExpect(jsonPath("$.content[1].keys()", containsInAnyOrder("id", "name", "image", "price")))
                .andExpect(jsonPath("$.content[1].id").isNumber())
                .andExpect(jsonPath("$.content[1].id").value(43))
                .andExpect(jsonPath("$.content[1].name").isString())
                .andExpect(jsonPath("$.content[1].name").value("Fone Zero"))
                .andExpect(jsonPath("$.content[1].image").isString())
                .andExpect(jsonPath("$.content[1].image").value("zero.jpg"))
                .andExpect(jsonPath("$.content[1].price").isNumber())
                .andExpect(jsonPath("$.content[1].price").value(0.0))
                .andDo(result -> recordMvcObservation(label, name, result));
        verify(productCatalogService).listProducts(name, pagination.page(), pagination.size());
        verifyNoMoreInteractions(productCatalogService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPageSizeScenarios")
    @Tag("feature006-t021")
    void listProductsShouldAcceptPublicPageSizeBoundsForEachNameVariant(
            String label, String name, int size) throws Exception {
        var response = new PageResponseDTO<ProductCatalogItemDTO>(List.of(), 2, size, 0, 0);
        when(productCatalogService.listProducts(name, 2, size)).thenReturn(response);
        var request = get("/products").accept(MediaType.APPLICATION_JSON)
                .param("page", "2").param("size", Integer.toString(size));
        if (name != null) request.param("name", name);

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(
                        "content", "page", "size", "totalElements", "totalPages")))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").isNumber())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.totalPages").value(0))
                .andDo(result -> recordMvcObservation(label, name, result));
        verify(productCatalogService).listProducts(name, 2, size);
        verifyNoMoreInteractions(productCatalogService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPageBoundScenarios")
    @Tag("feature006-t021")
    void listProductsShouldRejectPublicNumericBoundsBeforeCallingService(
            String label, String name, InvalidPageBound pagination) throws Exception {
        var request = get("/products").accept(MediaType.APPLICATION_JSON)
                .param("page", pagination.page()).param("size", pagination.size());
        if (name != null) request.param("name", name);

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("status", "error", "message", "path")))
                .andExpect(jsonPath("$.status").isNumber())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.message").value(pagination.message()))
                .andExpect(jsonPath("$.path").isString())
                .andExpect(jsonPath("$.path").value("/products"))
                .andDo(result -> recordMvcObservation(label, name, result));
        verifyNoInteractions(productCatalogService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nativeConversionScenarios")
    @Tag("feature006-t021")
    void listProductsShouldPreserveNativeConversionRejectionBeforeCallingService(
            String label, String name, String parameter) throws Exception {
        var request = get("/products").accept(MediaType.APPLICATION_JSON)
                .param("page", "page".equals(parameter) ? "abc" : "0")
                .param("size", "size".equals(parameter) ? "abc" : "12");
        if (name != null) request.param("name", name);

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(result -> {
                    assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentTypeMismatchException.class);
                    var mismatch = (MethodArgumentTypeMismatchException) result.getResolvedException();
                    assertThat(mismatch.getName()).isEqualTo(parameter);
                    assertThat(mismatch.getValue()).isEqualTo("abc");
                    assertThat(mismatch.getRequiredType()).isEqualTo(int.class);
                })
                // MockMvc does not dispatch the servlet /error response characterized in T004.
                .andDo(result -> recordMvcObservation(label, name, result));
        verifyNoInteractions(productCatalogService);
    }

    private void recordMvcObservation(String scenario, String name, MvcResult result) throws Exception {
        var rawBody = result.getResponse().getContentAsString();
        var observation = mapper.createObjectNode().put("scenario", scenario).put("name", name)
                .put("status", result.getResponse().getStatus()).put("contentType", result.getResponse().getContentType())
                .put("rawBody", rawBody).put("resolvedException", result.getResolvedException() == null
                        ? null : result.getResolvedException().getClass().getName());
        observation.set("parameters", mapper.valueToTree(result.getRequest().getParameterMap()));
        observation.set("body", rawBody.isEmpty() ? mapper.getNodeFactory().nullNode() : mapper.readTree(rawBody));
        System.out.println("T021_MVC " + observation);
    }

    private static List<NameVariant> nameVariants() {
        return List.of(new NameVariant("omitted", null), new NameVariant("empty", ""),
                new NameVariant("filled", " \tFONE\u00a0 "));
    }

    private static Stream<Arguments> paginationDefaultScenarios() {
        var cases = List.of(new PaginationDefaults("both-omitted", null, null, 0, 12),
                new PaginationDefaults("page-only", "1", null, 1, 12),
                new PaginationDefaults("size-only", null, "2", 0, 2),
                new PaginationDefaults("empty-page", "", "2", 0, 2),
                new PaginationDefaults("empty-size", "1", "", 1, 12),
                new PaginationDefaults("both-empty", "", "", 0, 12));
        return nameVariants().stream().flatMap(name -> cases.stream().map(pagination -> Arguments.of(
                "defaults-" + name.label() + "-" + pagination.label(), name.value(), pagination)));
    }

    private static Stream<Arguments> validPageSizeScenarios() {
        return nameVariants().stream().flatMap(name -> Stream.of(1, 50).map(size -> Arguments.of(
                "valid-" + name.label() + "-size-" + size, name.value(), size)));
    }

    private static Stream<Arguments> invalidPageBoundScenarios() {
        var cases = List.of(new InvalidPageBound("negative-page", "-1", "12", "page must be greater than or equal to 0"),
                new InvalidPageBound("zero-size", "0", "0", "size must be between 1 and 50"),
                new InvalidPageBound("excessive-size", "0", "51", "size must be between 1 and 50"));
        return nameVariants().stream().flatMap(name -> cases.stream().map(pagination -> Arguments.of(
                "bounds-" + name.label() + "-" + pagination.label(), name.value(), pagination)));
    }

    private static Stream<Arguments> nativeConversionScenarios() {
        return nameVariants().stream().flatMap(name -> Stream.of("page", "size").map(parameter -> Arguments.of(
                "native-" + name.label() + "-" + parameter, name.value(), parameter)));
    }

    private record NameVariant(String label, String value) { }
    private record PaginationDefaults(String label, String pageParameter, String sizeParameter, int page, int size) { }
    private record InvalidPageBound(String label, String page, String size, String message) { }
}
