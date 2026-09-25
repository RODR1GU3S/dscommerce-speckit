package com.devsuperior.dscommerce.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicCatalogSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
}
