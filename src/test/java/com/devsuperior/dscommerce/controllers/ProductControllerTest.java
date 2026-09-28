package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.SecurityConfig;
import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.dto.ProductCatalogItemDTO;
import com.devsuperior.dscommerce.dto.ProductDetailDTO;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.fixtures.ProductFactory;
import com.devsuperior.dscommerce.services.ProductCatalogService;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

        when(productCatalogService.listProducts(0, 12)).thenReturn(response);

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

        when(productCatalogService.listProducts(1, 10)).thenReturn(response);

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

        when(productCatalogService.listProducts(999, 12)).thenReturn(response);

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

        when(productCatalogService.listProducts(0, 12)).thenReturn(response);

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

        when(productCatalogService.listProducts(0, 12)).thenReturn(response);

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

        when(productCatalogService.listProducts(0, 2)).thenReturn(firstPage);
        when(productCatalogService.listProducts(1, 2)).thenReturn(secondPage);

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
}
