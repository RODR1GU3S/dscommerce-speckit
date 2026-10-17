package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** Component contract only; JWT/ADMIN decisions and filter wiring belong to later tasks. */
class AdminSecurityErrorHandlersTest {

    private static final String PRIVATE_DETAIL = "private-marker token password SQL stack trace";
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @MethodSource("authenticationFailures")
    void shouldChallengeAuthenticationWithoutExposingFailureDetails(AuthenticationException failure) throws Exception {
        var request = request();
        var response = new MockHttpServletResponse();

        new AdminAuthenticationEntryPoint(mapper).commence(request, response, failure);

        assertResponse(response, 401, "Unauthorized", "Authentication required or invalid");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        verifyOnlyUriRead(request);
    }

    static Stream<AuthenticationException> authenticationFailures() {
        return Stream.of(new InsufficientAuthenticationException(PRIVATE_DETAIL),
                new BadCredentialsException(PRIVATE_DETAIL),
                new InvalidBearerTokenException(PRIVATE_DETAIL),
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), PRIVATE_DETAIL,
                        new IllegalArgumentException(PRIVATE_DETAIL)));
    }

    @Test
    void shouldKeepInternalAccountResolutionDistinctFromInvalidCredentials() throws Exception {
        var request = request();
        var response = new MockHttpServletResponse();
        var resolutionFailure = new OrderAccountResolutionException(PRIVATE_DETAIL,
                new IllegalStateException(PRIVATE_DETAIL));
        var failure = new OAuth2AuthenticationException(new OAuth2Error("server_error"),
                PRIVATE_DETAIL, resolutionFailure);

        new AdminAuthenticationEntryPoint(mapper).commence(request, response, failure);

        assertResponse(response, 500, "Internal Server Error", "Product maintenance could not be completed");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        verifyOnlyUriRead(request);
    }

    @Test
    void shouldDenyPermissionWithoutChallengeOrRestrictedAccountDetails() throws Exception {
        var request = request();
        var response = new MockHttpServletResponse();

        new AdminAccessDeniedHandler(mapper).handle(request, response, new AccessDeniedException(PRIVATE_DETAIL));

        assertResponse(response, 403, "Forbidden", "Product maintenance not permitted");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        verifyOnlyUriRead(request);
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/admin/products/9");
        request.setContextPath("/api");
        request.setQueryString("name=" + PRIVATE_DETAIL);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + PRIVATE_DETAIL);
        request.setContent(("{\"name\":\"" + PRIVATE_DETAIL + "\"}").getBytes(StandardCharsets.UTF_8));
        return spy(request);
    }

    private void assertResponse(MockHttpServletResponse response, int status, String error, String message)
            throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        var expected = mapper.createObjectNode();
        expected.put("status", status);
        expected.put("error", error);
        expected.put("message", message);
        expected.put("path", "/api/admin/products/9");
        expected.putArray("errors");
        assertThat(mapper.readTree(response.getContentAsByteArray())).isEqualTo(expected);
        assertThat(response.getContentAsString()).doesNotContain(PRIVATE_DETAIL);
    }

    private void verifyOnlyUriRead(MockHttpServletRequest request) {
        verify(request).getRequestURI();
        verifyNoMoreInteractions(request);
    }
}
