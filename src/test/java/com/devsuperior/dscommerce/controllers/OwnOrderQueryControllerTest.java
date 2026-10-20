package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.OwnOrderQueryWebMvcConfig;
import com.devsuperior.dscommerce.controllers.handlers.GlobalExceptionHandler;
import com.devsuperior.dscommerce.controllers.handlers.OwnOrderQueryExceptionHandler;
import com.devsuperior.dscommerce.dto.OwnOrderPageRequestDTO;
import com.devsuperior.dscommerce.dto.OwnOrderSummaryDTO;
import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.OrderService;
import com.devsuperior.dscommerce.services.OwnOrderQueryService;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** MVC boundary only: production resolver/controllers/advices, mocked services, no filters or database. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnOrderQueryControllerTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ObjectMapper mapper;
    private OwnOrderQueryService service;
    private OrderService registration;
    private int dispatches;
    private final UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
            new AuthenticatedAccountPrincipal(42L), null, List.of());

    @BeforeAll
    void startOnlyTheMvcBoundaryWithBothProductionAdvices() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles("feature007-t015-mvc-probe");
        context.register(MvcConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        service = context.getBean(OwnOrderQueryService.class);
        registration = context.getBean(OrderService.class);
        mapper = context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast).findFirst().orElseThrow().getObjectMapper();
        assertThat(context.containsBean("dataSource")).isFalse();
        assertThat(context.containsBean("springSecurityFilterChain")).isFalse();
    }

    @BeforeEach
    void resetOnlyLocalServiceMocks() { reset(service, registration); }

    @AfterAll
    void closeMvcContext() {
        if (context != null) { context.close(); }
        System.out.println("T015_COUNTS mockMvcDispatches=" + dispatches
                + " realHttpRequests=0 databaseContexts=0 mockedServices=2");
    }

    @ParameterizedTest
    @CsvSource({"ROOT,0,12", "API,1,2"})
    void listDelegatesTheValidatedInputAndSameAuthenticationAndSerializesOnlyThePage(String path, int page, int size)
            throws Exception {
        var summary = new OwnOrderSummaryDTO(9L, Instant.parse("2026-10-19T12:00:00.123Z"),
                OrderStatus.WAITING_PAYMENT, "9007199254740993.01");
        var response = new PageResponseDTO<>(List.of(summary), page, size, 5, 3);
        when(service.list(any(), same(authentication))).thenReturn(response);
        var request = route(HttpMethod.GET, path).param("accountId", "99").param("buyerId", "99").param("sort", "id,asc");
        if (page != 0) { request.param("page", Integer.toString(page)).param("size", Integer.toString(size)); }
        var result = perform(request);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(result.getResponse().getContentAsByteArray())).isEqualTo(mapper.readTree("""
                {"content":[{"id":9,"createdAt":"2026-10-19T12:00:00.123Z","status":"WAITING_PAYMENT",
                "total":"9007199254740993.01"}],"page":%d,"size":%d,"totalElements":5,"totalPages":3}
                """.formatted(page, size)));
        verify(service).list(argThat(input -> input.getPage() == page && input.getSize() == size), same(authentication));
        verifyNoMoreInteractions(service);
        verifyNoInteractions(registration);
    }

    @ParameterizedTest
    @ValueSource(strings = {"negative", "zeroSize", "text", "empty", "integerOverflow", "offsetOverflow", "repeatPage", "repeatSize"})
    void listRejectsBindingAndBeanValidationErrorsBeforeCallingTheService(String invalid) throws Exception {
        var request = route(HttpMethod.GET, "API").queryParam("private", "must-not-appear");
        switch (invalid) {
            case "negative" -> request.param("page", "-1");
            case "zeroSize" -> request.param("size", "0");
            case "text" -> request.param("size", "secret-invalid");
            case "empty" -> request.param("page", "");
            case "integerOverflow" -> request.param("page", "2147483648");
            case "offsetOverflow" -> request.param("page", "2147483647").param("size", "2");
            case "repeatPage" -> request.param("page", "0", "1");
            case "repeatSize" -> request.param("size", "2", "2");
            default -> throw new IllegalArgumentException();
        }
        assertError(perform(request), 400, "Invalid order query data", "/api/orders");
        verifyNoInteractions(service, registration);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROOT", "API"})
    void listUsesTheDedicatedResolverWithoutBindingAliasesAgain(String path) throws Exception {
        when(service.list(any(), same(authentication))).thenAnswer(call -> {
            OwnOrderPageRequestDTO input = call.getArgument(0);
            assertThat(input.getPage()).isEqualTo(1); assertThat(input.getSize()).isEqualTo(2);
            return new PageResponseDTO<>(List.of(), 1, 2, 0, 0);
        });
        var result = perform(route(HttpMethod.GET, path).param("page", "1").param("size", "2")
                .param("_page", "").param("!page", "3").param("size[]", "invalid", "51"));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(result.getResponse().getContentAsByteArray()).get("page").intValue()).isEqualTo(1);
        verify(service).list(any(), same(authentication)); verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"credential,401,Authentication required or invalid", "permission,403,Order query not permitted",
            "lookup,500,Order query could not be completed", "persistence,500,Order query could not be completed",
            "transaction,500,Order query could not be completed", "financial,500,Order query could not be completed",
            "validation,500,Order query could not be completed", "constraint,500,Order query could not be completed",
            "returnValidation,500,Order query could not be completed", "mapping,500,Order query could not be completed"})
    void listTranslatesServiceFailuresWithSafeExactEnvelopeAndBearerChallenge(String failure, int status, String message)
            throws Exception {
        var cause = switch (failure) {
            case "credential" -> new BadCredentialsException("private credential");
            case "permission" -> new AccessDeniedException("private account");
            case "lookup" -> new OrderAccountResolutionException("private lookup", new IllegalStateException());
            case "persistence" -> new DataAccessResourceFailureException("private SQL");
            case "transaction" -> new TransactionSystemException("private commit");
            case "financial" -> new IllegalArgumentException("private money");
            case "validation" -> new ValidationException("private validation", new AccessDeniedException("nested permission"));
            case "constraint" -> new ConstraintViolationException("private constraint", Set.of());
            case "returnValidation" -> returnValidationFailure();
            case "mapping" -> new HttpMessageNotWritableException("private mapping");
            default -> throw new IllegalArgumentException();
        };
        when(service.list(any(), same(authentication))).thenThrow(cause);
        var result = perform(route(HttpMethod.GET, "API").queryParam("private", "must-not-appear"));
        assertError(result, status, message, "/api/orders");
        assertThat(result.getResponse().getHeader("WWW-Authenticate")).isEqualTo(status == 401 ? "Bearer" : null);
        verify(service).list(any(), same(authentication)); verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "invalid", "credential", "permission", "internal"})
    void headFollowsTheListAndHasNoBodyIncludingFailures(String outcome) throws Exception {
        var request = route(HttpMethod.HEAD, "API");
        int status = switch (outcome) {
            case "success" -> { when(service.list(any(), any())).thenReturn(new PageResponseDTO<>(List.of(), 0, 12, 0, 0)); yield 200; }
            case "invalid" -> { request.param("page", "-1"); yield 400; }
            case "credential" -> { when(service.list(any(), any())).thenThrow(new BadCredentialsException("private")); yield 401; }
            case "permission" -> { when(service.list(any(), any())).thenThrow(new AccessDeniedException("private")); yield 403; }
            default -> { when(service.list(any(), any())).thenThrow(new IllegalStateException("private")); yield 500; }
        };
        var result = perform(request);
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(result.getResponse().getHeader("WWW-Authenticate")).isEqualTo(status == 401 ? "Bearer" : null);
        if (outcome.equals("invalid")) { verifyNoInteractions(service); }
        else { verify(service).list(any(), same(authentication)); verifyNoMoreInteractions(service); }
    }

    @Test
    void nativeMethodAndNegotiationFailuresKeepStatusesAndHeadersWithoutServiceCalls() throws Exception {
        var method = perform(route(HttpMethod.PUT, "ROOT"));
        assertThat(method.getResponse().getStatus()).isEqualTo(405);
        var nativeFailure = (HttpRequestMethodNotSupportedException) method.getResolvedException();
        assertThat(nativeFailure.getSupportedMethods()).containsExactlyInAnyOrder("GET", "POST");
        nativeFailure.getHeaders().forEach((name, values) ->
                assertThat(method.getResponse().getHeaders(name)).containsExactlyElementsOf(values));
        var negotiation = perform(route(HttpMethod.GET, "ROOT").accept(MediaType.TEXT_PLAIN));
        assertThat(negotiation.getResponse().getStatus()).isEqualTo(406);
        assertThat(negotiation.getResponse().getContentAsByteArray()).isEmpty();
        verifyNoInteractions(service, registration);
    }

    @ParameterizedTest
    @ValueSource(strings = {"method", "negotiation"})
    void nativeErrorsRaisedWithinTheSelectedHandlerAreNotChangedIntoInternalFailures(String kind) throws Exception {
        Exception failure = kind.equals("method") ? new HttpRequestMethodNotSupportedException("PUT", List.of("GET", "HEAD"))
                : new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));
        when(service.list(any(), any())).thenAnswer(call -> { throw failure; });
        var result = perform(route(HttpMethod.GET, "ROOT"));
        assertThat(result.getResponse().getStatus()).isEqualTo(kind.equals("method") ? 405 : 406);
        failureHeaders(failure).forEach((name, values) -> assertThat(result.getResponse().getHeaders(name)).containsExactlyElementsOf(values));
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void scopedAdviceLeavesRegistrationAndPublicErrorsToTheExistingGlobalAdvice() throws Exception {
        assertError(perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}")),
                400, "Invalid order data", "/orders");
        verifyNoInteractions(registration);
        when(registration.register(any(), any())).thenThrow(new AccessDeniedException("private registration"));
        assertError(perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":1,\"quantity\":1}]}")), 403, "Order registration not permitted", "/orders");
        doThrow(new IllegalStateException("private registration")).when(registration).register(any(), any());
        assertError(perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":1,\"quantity\":1}]}")), 500, "Order registration could not be completed", "/orders");
        assertError(perform(get("/t015/public-product")), 404, "Product not found", "/t015/public-product");
        verifyNoInteractions(service);
    }

    private static org.springframework.http.HttpHeaders failureHeaders(Exception failure) {
        return ((org.springframework.web.ErrorResponse) failure).getHeaders();
    }

    private HandlerMethodValidationException returnValidationFailure() throws Exception {
        var method = OwnOrderQueryController.class.getMethod("list", OwnOrderPageRequestDTO.class,
                org.springframework.security.core.Authentication.class, jakarta.servlet.http.HttpServletRequest.class);
        var result = new ParameterValidationResult(new MethodParameter(method, -1), null,
                List.of(new org.springframework.context.support.DefaultMessageSourceResolvable("private return")), null, null, null);
        return new HandlerMethodValidationException(MethodValidationResult.create(context.getBean(OwnOrderQueryController.class),
                method, List.of(result)));
    }

    private MockHttpServletRequestBuilder route(HttpMethod method, String path) {
        var prefix = path.equals("API") ? "/api" : "";
        return request(method, prefix + "/orders").contextPath(prefix).principal(authentication).accept(MediaType.APPLICATION_JSON);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        var result = mvc.perform(request).andReturn(); dispatches++; return result;
    }

    private void assertError(MvcResult result, int status, String message, String path) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        var expected = mapper.createObjectNode().put("status", status)
                .put("error", org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase())
                .put("message", message).put("path", path);
        assertThat(mapper.readTree(result.getResponse().getContentAsByteArray())).isEqualTo(expected);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({OwnOrderQueryController.class, OwnOrderQueryWebMvcConfig.class, OwnOrderQueryExceptionHandler.class,
            GlobalExceptionHandler.class, OrderController.class, PublicProbeController.class})
    static class MvcConfiguration {
        @Bean OwnOrderQueryService ownOrderQueryService() { return mock(OwnOrderQueryService.class); }
        @Bean OrderService orderService() { return mock(OrderService.class); }
    }

    @RestController
    @Profile("feature007-t015-mvc-probe")
    static class PublicProbeController {
        @GetMapping("/t015/public-product")
        public String product() { throw new ProductNotFoundException("Product not found"); }
    }
}
