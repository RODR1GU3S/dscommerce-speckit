package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.SecurityConfig;
import com.devsuperior.dscommerce.services.AuthenticationService;
import com.devsuperior.dscommerce.services.ProductCatalogService;
import com.devsuperior.dscommerce.services.exceptions.AuthenticationProcessingException;
import com.devsuperior.dscommerce.services.exceptions.InvalidCredentialsException;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        ProductController.class,
        LoginController.class,
        GlobalExceptionHandlerRegressionTest.ValidationProbeController.class
})
@Import({
        SecurityConfig.class,
        GlobalExceptionHandlerRegressionTest.ValidationProbeController.class
})
class GlobalExceptionHandlerRegressionTest {

    private static final String RAW_PASSWORD_SENTINEL =
            "raw-password-sensitive-sentinel";
    private static final String SENSITIVE_CAUSE_SENTINEL =
            "cause-secret-jwt-key-decoded-key-$2a$10$bcrypt-sensitive-sentinel";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductCatalogService productCatalogService;

    @MockBean
    private AuthenticationService authenticationService;

    @Test
    void productBaselineNotFoundPreservesExistingErrorContract() throws Exception {
        String message = "Product not found";
        when(productCatalogService.findProductDetails(99999L))
                .thenThrow(new ProductNotFoundException(message));

        expectFourFieldError(
                mockMvc.perform(get("/products/{id}", 99999L)),
                404,
                "Not Found",
                message,
                "/products/99999");
    }

    @Test
    void productBaselineNegativePagePreservesExistingConstraintViolationContract()
            throws Exception {
        expectFourFieldError(
                mockMvc.perform(get("/products")
                        .param("page", "-1")
                        .param("size", "12")),
                400,
                "Bad Request",
                "page must be greater than or equal to 0",
                "/products");
    }

    @Test
    void productBaselineSizeBelowMinimumPreservesExistingConstraintViolationContract()
            throws Exception {
        expectFourFieldError(
                mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "0")),
                400,
                "Bad Request",
                "size must be between 1 and 50",
                "/products");
    }

    @Test
    void productBaselineSizeAboveMaximumPreservesExistingConstraintViolationContract()
            throws Exception {
        expectFourFieldError(
                mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "51")),
                400,
                "Bad Request",
                "size must be between 1 and 50",
                "/products");
    }

    @Test
    void productBaselineMethodArgumentNotValidPreservesExistingValidationContract()
            throws Exception {
        expectFourFieldError(
                mockMvc.perform(post("/test-only/validation-probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":""}
                                """)),
                400,
                "Bad Request",
                "Validation failed",
                "/test-only/validation-probe");
    }

    @Test
    void loginContractInvalidInputReturnsGenericBadRequest() throws Exception {
        MvcResult result = expectFourFieldError(
                mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","password":"raw-password-sensitive-sentinel"}
                                """)),
                400,
                "Bad Request",
                "Invalid login data",
                "/login");

        assertConfidentialLoginError(result);
        verifyNoInteractions(authenticationService);
    }

    @Test
    void loginContractInvalidInputPreservesGenericBadRequestUnderServletContextPath()
            throws Exception {
        MvcResult result = expectFourFieldError(
                mockMvc.perform(post("/api/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","password":"raw-password-sensitive-sentinel"}
                                """)),
                400,
                "Bad Request",
                "Invalid login data",
                "/api/login");

        assertConfidentialLoginError(result);
        verifyNoInteractions(authenticationService);
    }

    @Test
    void loginContractInvalidCredentialsReturnsGenericUnauthorized() throws Exception {
        when(authenticationService.login("UnknownName", RAW_PASSWORD_SENTINEL))
                .thenThrow(new InvalidCredentialsException(
                        "invalid-credential-sensitive-sentinel " + RAW_PASSWORD_SENTINEL));

        MvcResult result = expectFourFieldError(
                mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"UnknownName",
                                  "password":"raw-password-sensitive-sentinel"
                                }
                                """)),
                401,
                "Unauthorized",
                "Invalid credentials",
                "/login");

        assertConfidentialLoginError(result);
    }

    @Test
    void loginContractInternalFailureReturnsGenericServerError() throws Exception {
        RuntimeException originalCause = new RuntimeException(SENSITIVE_CAUSE_SENTINEL);
        when(authenticationService.login("ExistingName", RAW_PASSWORD_SENTINEL))
                .thenThrow(new AuthenticationProcessingException(
                        "Authentication could not be completed",
                        originalCause));

        MvcResult result = expectFourFieldError(
                mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"ExistingName",
                                  "password":"raw-password-sensitive-sentinel"
                                }
                                """)),
                500,
                "Internal Server Error",
                "Authentication could not be completed",
                "/login");

        assertConfidentialLoginError(result);
    }

    private MvcResult expectFourFieldError(
            ResultActions request,
            int expectedStatus,
            String expectedError,
            String expectedMessage,
            String expectedPath) throws Exception {
        return request
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(
                        "status",
                        "error",
                        "message",
                        "path")))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.error").value(expectedError))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.path").value(expectedPath))
                .andReturn();
    }

    private static void assertConfidentialLoginError(MvcResult result) throws Exception {
        String responseBody = result.getResponse()
                .getContentAsString()
                .toLowerCase(Locale.ROOT);

        assertThat(responseBody)
                .doesNotContain(
                        "password",
                        "passwordhash",
                        "accesstoken",
                        "tokentype",
                        "jwt secret",
                        "jwt-secret",
                        "jwt-key",
                        "decoded-key",
                        "bcrypt",
                        "$2a$",
                        RAW_PASSWORD_SENTINEL.toLowerCase(Locale.ROOT),
                        SENSITIVE_CAUSE_SENTINEL.toLowerCase(Locale.ROOT),
                        "invalid-credential-sensitive-sentinel");
    }

    @RestController
    static class ValidationProbeController {

        @PostMapping("/test-only/validation-probe")
        void validate(@Valid @RequestBody ValidationProbeRequest request) {
        }
    }

    record ValidationProbeRequest(@NotBlank String value) {
    }
}
