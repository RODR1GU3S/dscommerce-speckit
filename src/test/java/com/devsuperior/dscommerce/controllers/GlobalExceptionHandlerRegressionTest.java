package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.SecurityConfig;
import com.devsuperior.dscommerce.config.OrderClockConfig;
import com.devsuperior.dscommerce.config.OrderSecuritySliceTestConfig;
import com.devsuperior.dscommerce.services.AuthenticationService;
import com.devsuperior.dscommerce.services.ProductCatalogService;
import com.devsuperior.dscommerce.services.exceptions.AuthenticationProcessingException;
import com.devsuperior.dscommerce.services.exceptions.InvalidCredentialsException;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(properties = "test.order-security.public-mvc-slice=true", controllers = {
        ProductController.class,
        LoginController.class,
        GlobalExceptionHandlerRegressionTest.ValidationProbeController.class
})
@Import({
        SecurityConfig.class,
        OrderClockConfig.class,
        OrderSecuritySliceTestConfig.class,
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

    @Autowired
    private FilterChainProxy filters;

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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void productMessageAndExternalPathRemainUnchanged(boolean contextPath) throws Exception {
        String message = "Existing product error message";
        when(productCatalogService.findProductDetails(99999L)).thenThrow(new ProductNotFoundException(message));
        String path = contextPath ? "/api/products/99999" : "/products/99999";
        var request = get(path).servletPath("/products/99999");
        if (contextPath) request.contextPath("/api");
        expectFourFieldError(mockMvc.perform(request), 404, "Not Found", message, path);
    }

    @Test
    void unexpectedPublicFailureIsNotTranslatedAsAnOrderRegistrationError() {
        var failure = new IllegalStateException("test-only unrelated processing failure");
        when(productCatalogService.findProductDetails(99999L)).thenThrow(failure);
        assertThatThrownBy(() -> mockMvc.perform(get("/products/99999")))
                .rootCause().isSameAs(failure);
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

    @ParameterizedTest(name = "detail error={2}, context={0}, invalid Bearer={1}")
    @MethodSource("detailErrors")
    void shouldPreserveTheDetailMessageAndExternalPathAcrossPublicRequestModes(
            String context, boolean invalidBearer, String message) throws Exception {
        when(productCatalogService.findProductDetails(99999L)).thenThrow(new ProductNotFoundException(message));
        var request = publicRequest(get(context + "/products/99999"), context, "/products/99999", invalidBearer);
        expectFourFieldError(performPublic(request), 404, "Not Found", message, context + "/products/99999");
    }

    static Stream<Arguments> detailErrors() {
        return publicModes().flatMap(mode -> Stream.of("Product not found", "Existing product error message")
                .map(message -> Arguments.of(mode.get()[0], mode.get()[1], message)));
    }

    @ParameterizedTest(name = "catalog page={2}/size={3}, context={0}, invalid Bearer={1}")
    @MethodSource("catalogErrors")
    void shouldPreserveCatalogConstraintMessagesAndExternalPaths(
            String context, boolean invalidBearer, String page, String size, String message) throws Exception {
        var request = publicRequest(get(context + "/products"), context, "/products", invalidBearer)
                .param("page", page).param("size", size);
        expectFourFieldError(performPublic(request), 400, "Bad Request", message, context + "/products");
        verifyNoInteractions(productCatalogService, authenticationService);
    }

    static Stream<Arguments> catalogErrors() {
        return publicModes().flatMap(mode -> Stream.of(
                Arguments.of(mode.get()[0], mode.get()[1], "-1", "12", "page must be greater than or equal to 0"),
                Arguments.of(mode.get()[0], mode.get()[1], "0", "0", "size must be between 1 and 50"),
                Arguments.of(mode.get()[0], mode.get()[1], "0", "51", "size must be between 1 and 50")));
    }

    @ParameterizedTest(name = "generic validation, context={0}, invalid Bearer={1}")
    @MethodSource("publicModes")
    void shouldPreserveGenericValidationOutsideLoginAndOrders(String context, boolean invalidBearer)
            throws Exception {
        var logicalPath = "/test-only/validation-probe";
        var request = publicRequest(post(context + logicalPath), context, logicalPath, invalidBearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"\"}");
        expectFourFieldError(performPublic(request), 400, "Bad Request", "Validation failed", context + logicalPath);
        verifyNoInteractions(productCatalogService, authenticationService);
    }

    @ParameterizedTest(name = "login input={2}, context={0}, invalid Bearer={1}")
    @MethodSource("loginInputErrors")
    void shouldPreserveSafeLoginValidationAndUnreadableMessageContracts(
            String context, boolean invalidBearer, String body) throws Exception {
        var request = publicRequest(post(context + "/login"), context, "/login", invalidBearer)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        var result = expectFourFieldError(performPublic(request), 400, "Bad Request",
                "Invalid login data", context + "/login");
        assertConfidentialLoginError(result);
        verifyNoInteractions(authenticationService, productCatalogService);
    }

    static Stream<Arguments> loginInputErrors() {
        return publicModes().flatMap(mode -> Stream.of(
                "{\"name\":\" \",\"password\":\"" + RAW_PASSWORD_SENTINEL + "\"}",
                "{\"name\":", "{\"name\":7,\"password\":\"" + RAW_PASSWORD_SENTINEL + "\"}",
                "{\"name\":\"ExistingName\",\"password\":\"" + RAW_PASSWORD_SENTINEL + "\",\"extra\":true}", "")
                .map(body -> Arguments.of(mode.get()[0], mode.get()[1], body)));
    }

    @ParameterizedTest(name = "login name={2}, context={0}, invalid Bearer={1}")
    @MethodSource("invalidCredentials")
    void shouldPreserveTheUniformLoginUnauthorizedEnvelopeWithoutAnOrderChallenge(
            String context, boolean invalidBearer, String name) throws Exception {
        when(authenticationService.login(name, RAW_PASSWORD_SENTINEL)).thenThrow(new InvalidCredentialsException(
                "invalid-credential-sensitive-sentinel " + RAW_PASSWORD_SENTINEL));
        var request = publicRequest(post(context + "/login"), context, "/login", invalidBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"password\":\"" + RAW_PASSWORD_SENTINEL + "\"}");
        var result = expectFourFieldError(performPublic(request), 401, "Unauthorized",
                "Invalid credentials", context + "/login");
        assertConfidentialLoginError(result);
    }

    static Stream<Arguments> invalidCredentials() {
        return publicModes().flatMap(mode -> Stream.of("UnknownName", "ExistingNameWithWrongPassword")
                .map(name -> Arguments.of(mode.get()[0], mode.get()[1], name)));
    }

    @ParameterizedTest(name = "login internal failure, context={0}, invalid Bearer={1}")
    @MethodSource("publicModes")
    void shouldPreserveTheSafeLoginInternalFailureMessageAndExternalPath(String context, boolean invalidBearer)
            throws Exception {
        when(authenticationService.login("ExistingName", RAW_PASSWORD_SENTINEL))
                .thenThrow(new AuthenticationProcessingException("Authentication could not be completed",
                        new RuntimeException(SENSITIVE_CAUSE_SENTINEL)));
        var request = publicRequest(post(context + "/login"), context, "/login", invalidBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ExistingName\",\"password\":\"" + RAW_PASSWORD_SENTINEL + "\"}");
        var result = expectFourFieldError(performPublic(request), 500, "Internal Server Error",
                "Authentication could not be completed", context + "/login");
        assertConfidentialLoginError(result);
    }

    @ParameterizedTest(name = "public processing failure, context={0}, invalid Bearer={1}")
    @MethodSource("publicModes")
    void shouldLeaveUnrelatedPublicProcessingFailuresOutsideTheOrderHandler(String context, boolean invalidBearer) {
        var failure = new IllegalStateException("test-only unrelated processing failure");
        when(productCatalogService.findProductDetails(99999L)).thenThrow(failure);
        var request = publicRequest(get(context + "/products/99999"), context, "/products/99999", invalidBearer);
        assertThatThrownBy(() -> performPublic(request)).rootCause().isSameAs(failure);
    }

    @ParameterizedTest(name = "MVC conversion={2}, context={0}, invalid Bearer={1}")
    @MethodSource("conversionErrors")
    void shouldPreserveMvcConversionRejectionOutsideTheOrderEnvelope(
            String context, boolean invalidBearer, String logicalPath) throws Exception {
        var request = publicRequest(get(context + logicalPath), context,
                logicalPath.split("\\?")[0], invalidBearer);
        var result = performPublic(request).andExpect(status().isBadRequest()).andReturn();
        assertThat(result.getResolvedException())
                .isInstanceOf(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class);
        assertThat(result.getRequest().getRequestURI()).isEqualTo(context + logicalPath.split("\\?")[0]);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Invalid order data",
                "Order registration could not be completed", "Authentication required or invalid");
        verifyNoInteractions(productCatalogService, authenticationService);
    }

    static Stream<Arguments> conversionErrors() {
        return publicModes().flatMap(mode -> Stream.of("/products?page=abc", "/products?size=abc", "/products/abc")
                .map(path -> Arguments.of(mode.get()[0], mode.get()[1], path)));
    }

    static Stream<Arguments> publicModes() {
        return Stream.of("", "/api").flatMap(context -> Stream.of(false, true)
                .map(bearer -> Arguments.of(context, bearer)));
    }

    private static MockHttpServletRequestBuilder publicRequest(MockHttpServletRequestBuilder request,
                                                               String context, String servletPath,
                                                               boolean invalidBearer) {
        request.contextPath(context).servletPath(servletPath);
        if (invalidBearer) request.header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt");
        return request;
    }

    private ResultActions performPublic(MockHttpServletRequestBuilder request) throws Exception {
        var selected = filters.getFilterChains().stream()
                .filter(chain -> chain.matches(request.buildRequest(mockMvc.getDispatcherServlet().getServletContext())))
                .findFirst().orElseThrow();
        assertThat(selected.getFilters()).anyMatch(AuthorizationFilter.class::isInstance)
                .noneMatch(BearerTokenAuthenticationFilter.class::isInstance);
        return mockMvc.perform(request).andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }
}
