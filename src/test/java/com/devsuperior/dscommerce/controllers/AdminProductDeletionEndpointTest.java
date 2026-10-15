package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.controllers.handlers.GlobalExceptionHandler;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.AdminProductQueryService;
import com.devsuperior.dscommerce.services.ProductMaintenanceService;
import com.devsuperior.dscommerce.services.exceptions.ProductDeletionIntegrityException;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionSystemException;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** T061 adapter only: real MVC/controller/advice, mocked service boundary; no filters, database or commit proof. */
class AdminProductDeletionEndpointTest {
    private final ProductMaintenanceService maintenance = mock(ProductMaintenanceService.class);
    private final AdminProductQueryService queries = mock(AdminProductQueryService.class);
    private final AuthenticatedAccountPrincipal principal = new AuthenticatedAccountPrincipal(61L);
    private MockMvc mvc;

    @BeforeEach
    void configureRealHttpAdapter() {
        mvc = standaloneSetup(new AdminProductController(queries, maintenance))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/api"})
    void returnsEmpty204OnlyAfterServiceReturns(String context) throws Exception {
        boolean[] returned = {false};
        doAnswer(call -> { returned[0] = true; return null; }).when(maintenance).deleteProduct(9L, principal);

        var result = perform(context, "9").andExpect(status().isNoContent())
                .andExpect(content().bytes(new byte[0]))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION)).andReturn();

        assertThat(returned[0]).as("service must return before HTTP success").isTrue();
        assertThat(result.getHandler()).isInstanceOf(org.springframework.web.method.HandlerMethod.class);
        verify(maintenance).deleteProduct(9L, principal);
        verifyNoMoreInteractions(maintenance);
        verifyNoInteractions(queries);
    }

    static Stream<Arguments> serviceFailures() {
        return Stream.of("", "/api").flatMap(context -> Stream.of(
                Arguments.of(context, new ProductNotFoundException("private"), 404, "Product not found"),
                Arguments.of(context, new ProductDeletionIntegrityException(), 409,
                        "Product deletion violates referential integrity"),
                Arguments.of(context, new TransactionSystemException("private commit failure"), 500,
                        "Product maintenance could not be completed"),
                Arguments.of(context, new BadCredentialsException("private"), 401,
                        "Authentication required or invalid"),
                Arguments.of(context, new AccessDeniedException("private"), 403,
                        "Product maintenance not permitted")));
    }

    @ParameterizedTest
    @MethodSource("serviceFailures")
    void neverConfirmsSuccessWhenServiceBoundaryThrows(String context, RuntimeException failure,
                                                      int expected, String message) throws Exception {
        doThrow(failure).when(maintenance).deleteProduct(9L, principal);

        var result = perform(context, "9").andExpect(status().is(expected))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"status":%d,"error":"%s","message":"%s","path":"%s/admin/products/9","errors":[]}
                        """.formatted(expected, HttpStatus.valueOf(expected).getReasonPhrase(), message, context), true))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION)).andReturn();

        assertThat(result.getResolvedException()).isSameAs(failure);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo(expected == 401 ? "Bearer" : null);
        verify(maintenance).deleteProduct(9L, principal);
        verifyNoMoreInteractions(maintenance);
        verifyNoInteractions(queries);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidPathBeforeCallingMaintenance(String id) throws Exception {
        perform("", id).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PARAMETER"));
        verifyNoInteractions(maintenance, queries);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?name=private", "?id=9&id=10"})
    void rejectsQueryBeforeCallingMaintenance(String query) throws Exception {
        perform("", "9" + query).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/admin/products/9"))
                .andExpect(jsonPath("$.errors[0].field").value("body"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PARAMETER"));
        verifyNoInteractions(maintenance, queries);
    }

    private org.springframework.test.web.servlet.ResultActions perform(String context, String id) throws Exception {
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        SecurityContextHolder.setContext(security);
        try {
            return mvc.perform(delete(context + "/admin/products/" + id).contextPath(context)
                    .accept(MediaType.APPLICATION_JSON));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
