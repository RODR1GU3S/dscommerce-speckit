package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.dto.ErrorResponseDTO;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.security.OwnOrderQueryAccessDeniedHandler;
import com.devsuperior.dscommerce.security.OwnOrderQueryAuthenticationEntryPoint;
import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
import com.devsuperior.dscommerce.services.OwnOrderQueryAccessService;
import com.devsuperior.dscommerce.services.PaymentAccessService;
import com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;

/** Configuration/provider/handler tests only: no servlet dispatch, MockMvc, HTTP, H2 or JPA. */
class OwnOrderQuerySecurityConfigurationTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final AuthenticatedAccountPrincipal PRINCIPAL = new AuthenticatedAccountPrincipal(42L);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityTestConfiguration.class);

    @ParameterizedTest
    @MethodSource("queryRequests")
    void selectsOwnQueryChainForOnlyQueryMethodsAndSingleSegment(String contextPath, String method, String path) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var proxy = context.getBean(FilterChainProxy.class);
            assertThat(proxy.getFilterChains()).hasSize(5);
            var chain = context.getBean("ownOrderQuerySecurityFilterChain", SecurityFilterChain.class);
            assertThat(proxy.getFilterChains().get(1)).isSameAs(chain);
            assertThat(selected(proxy, request(contextPath, method, path))).isSameAs(chain);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/api"})
    void preservesExistingChainSelectionAndPublicFallback(String contextPath) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var proxy = context.getBean(FilterChainProxy.class);
            assertThat(proxy.getFilterChains()).containsExactly(
                    context.getBean("paymentSecurityFilterChain", SecurityFilterChain.class),
                    context.getBean("ownOrderQuerySecurityFilterChain", SecurityFilterChain.class),
                    context.getBean("orderSecurityFilterChain", SecurityFilterChain.class),
                    context.getBean("adminProductSecurityFilterChain", SecurityFilterChain.class),
                    context.getBean("securityFilterChain", SecurityFilterChain.class));
            for (var method : List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")) {
                for (var path : List.of("/admin/orders/1/payments", "/admin/orders/invalid/payments")) {
                    assertThat(selected(proxy, request(contextPath, method, path)))
                            .isSameAs(context.getBean("paymentSecurityFilterChain"));
                }
                for (var path : List.of("/admin/orders/1/payments/extra", "/admin/orders/1/other")) {
                    assertThat(selected(proxy, request(contextPath, method, path)))
                            .isSameAs(context.getBean("adminProductSecurityFilterChain"));
                }
            }
            assertThat(selected(proxy, request(contextPath, "POST", "/orders")))
                    .isSameAs(context.getBean("orderSecurityFilterChain"));
            assertThat(selected(proxy, request(contextPath, "GET", "/admin/products")))
                    .isSameAs(context.getBean("adminProductSecurityFilterChain"));
            for (var path : List.of("/products", "/products/1", "/login", "/orders/1/items")) {
                for (var method : List.of("GET", "HEAD", "POST")) {
                    assertThat(selected(proxy, request(contextPath, method, path)))
                            .isSameAs(context.getBean("securityFilterChain"));
                }
            }
            for (var method : List.of("PUT", "PATCH", "DELETE", "OPTIONS")) {
                assertThat(selected(proxy, request(contextPath, method, "/orders")))
                        .isSameAs(context.getBean("securityFilterChain"));
            }
            assertThat(selected(proxy, request(contextPath, "POST", "/orders/1")))
                    .isSameAs(context.getBean("securityFilterChain"));
        });
    }

    @ParameterizedTest
    @MethodSource("authorityCases")
    void configuresQueryAndExistingAuthorizationPolicies(String contextPath, Set<String> roles) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var authentication = UsernamePasswordAuthenticationToken.authenticated(PRINCIPAL, null,
                    roles.stream().map(SimpleGrantedAuthority::new).toList());
            boolean queryAllowed = roles.contains("ROLE_CLIENT") || roles.contains("ROLE_ADMIN");
            for (var bean : List.of("paymentSecurityFilterChain", "ownOrderQuerySecurityFilterChain", "orderSecurityFilterChain",
                    "adminProductSecurityFilterChain", "securityFilterChain")) {
                var chain = context.getBean(bean, SecurityFilterChain.class);
                var filter = chain.getFilters().stream().filter(AuthorizationFilter.class::isInstance)
                        .map(AuthorizationFilter.class::cast).findFirst().orElseThrow();
                boolean expected = switch (bean) {
                    case "paymentSecurityFilterChain", "adminProductSecurityFilterChain" -> roles.contains("ROLE_ADMIN");
                    case "securityFilterChain" -> true;
                    default -> queryAllowed;
                };
                var decision = filter.getAuthorizationManager().check(() -> authentication,
                        request(contextPath, "GET", "/orders"));
                assertThat(decision).isNotNull();
                assertThat(decision.isGranted()).as(bean).isEqualTo(expected);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("providerFailures")
    void configuredProviderKeepsLookupFailuresDistinct(String contextPath, String phase, int status) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var accounts = context.getBean(AuthenticatedAccountService.class);
            var access = context.getBean(OwnOrderQueryAccessService.class);
            var resolved = new AuthenticatedAccountService.ResolvedAccount(PRINCIPAL,
                    Set.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
            RuntimeException failure = switch (status) {
                case 401 -> new BadCredentialsException("private credential diagnostic");
                case 403 -> new AccessDeniedException("private permission diagnostic");
                default -> new OrderAccountResolutionException("private lookup diagnostic",
                        new IllegalStateException("private SQL password JWT diagnostic"));
            };
            when(context.getBean(JwtDecoder.class).decode("configuration-token")).thenReturn(decodedFixture());
            if (phase.equals("initial")) {
                when(accounts.resolve(42L)).thenThrow(failure);
            } else {
                when(accounts.resolve(42L)).thenReturn(resolved);
                when(access.resolve(PRINCIPAL)).thenThrow(failure);
            }
            var chain = context.getBean("ownOrderQuerySecurityFilterChain", SecurityFilterChain.class);
            var request = request(contextPath, "GET", "/orders/not-an-integer");
            var thrown = catchThrowable(() -> manager(chain, request)
                    .authenticate(new BearerTokenAuthenticationToken("configuration-token")));
            assertThat(thrown).isInstanceOf(AuthenticationException.class);
            if (status == 401) {
                assertThat(thrown).isSameAs(failure);
            } else {
                assertThat(thrown).isInstanceOf(OAuth2AuthenticationException.class).hasCause(failure);
                assertThat(((OAuth2AuthenticationException) thrown).getError().getErrorCode())
                        .isEqualTo(status == 403 ? "insufficient_scope" : "server_error");
            }
            if (phase.equals("initial")) {
                verifyNoInteractions(access);
            }
            verifyNoInteractions(context.getBean(PaymentAccessService.class));
            var response = new MockHttpServletResponse();
            new OwnOrderQueryAuthenticationEntryPoint(MAPPER)
                    .commence(request, response, (AuthenticationException) thrown);
            assertResponse(request, response, status);
        });
    }

    @ParameterizedTest
    @MethodSource("handlerCases")
    void writesFixedErrorsAndSuppressesHeadBodyWithoutDispatch(String contextPath, String method,
                                                               String path, int status) throws Exception {
        var request = request(contextPath, method, path);
        var response = new MockHttpServletResponse();
        AuthenticationException failure = switch (status) {
            case 401 -> new BadCredentialsException("private SQL JWT password");
            case 403 -> new OAuth2AuthenticationException(new OAuth2Error("insufficient_scope"),
                    "private diagnostic", new AccessDeniedException("private permission"));
            default -> new OAuth2AuthenticationException(new OAuth2Error("server_error"),
                    "private diagnostic", new OrderAccountResolutionException("private SQL", new IllegalStateException()));
        };
        new OwnOrderQueryAuthenticationEntryPoint(MAPPER).commence(request, response, failure);
        assertResponse(request, response, status);
        if (status == 403) {
            var direct = new MockHttpServletResponse();
            new OwnOrderQueryAccessDeniedHandler(MAPPER).handle(request, direct, new AccessDeniedException("private"));
            assertResponse(request, direct, status);
        }
    }

    private static void assertResponse(MockHttpServletRequest request, MockHttpServletResponse response,
                                       int status) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo(status == 401 ? "Bearer" : null);
        assertThat(response.getContentType()).isEqualTo("application/json");
        if (request.getMethod().equals("HEAD")) {
            assertThat(response.getContentAsByteArray()).isEmpty();
        } else {
            String error = switch (status) { case 401 -> "Unauthorized"; case 403 -> "Forbidden"; default -> "Internal Server Error"; };
            String message = switch (status) {
                case 401 -> "Authentication required or invalid";
                case 403 -> "Order query not permitted";
                default -> "Order query could not be completed";
            };
            assertThat(MAPPER.readTree(response.getContentAsByteArray()))
                    .isEqualTo(MAPPER.valueToTree(new ErrorResponseDTO(status, error, message, request.getRequestURI())));
        }
    }

    private static MockHttpServletRequest request(String contextPath, String method, String path) {
        var request = new MockHttpServletRequest(method, contextPath + path);
        request.setContextPath(contextPath);
        request.setServletPath(path);
        request.setQueryString("page=not-an-integer&private=value");
        return request;
    }

    private static SecurityFilterChain selected(FilterChainProxy proxy, HttpServletRequest request) {
        return proxy.getFilterChains().stream().filter(chain -> chain.matches(request)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static AuthenticationManager manager(SecurityFilterChain chain, HttpServletRequest request) {
        var filter = chain.getFilters().stream().filter(BearerTokenAuthenticationFilter.class::isInstance)
                .findFirst().orElseThrow();
        var resolver = (AuthenticationManagerResolver<HttpServletRequest>) ReflectionTestUtils
                .getField(filter, "authenticationManagerResolver");
        assertThat(resolver).isNotNull();
        return resolver.resolve(request);
    }

    private static Jwt decodedFixture() {
        // Decoder is mocked. This compact payload is neither signed nor verified by this test.
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var compact = encoder.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString("{\"sub\":\"42\"}".getBytes(StandardCharsets.UTF_8)) + ".AA";
        return Jwt.withTokenValue(compact).header("alg", "HS256").subject("42")
                .issuedAt(NOW.minusSeconds(60)).expiresAt(NOW.plusSeconds(60)).build();
    }

    static Stream<Arguments> queryRequests() {
        return Stream.of("", "/api").flatMap(context -> Stream.of("GET", "HEAD").flatMap(method ->
                Stream.of("/orders", "/orders/1", "/orders/not-an-integer")
                        .map(path -> Arguments.of(context, method, path))));
    }

    static Stream<Arguments> authorityCases() {
        return Stream.of("", "/api").flatMap(context -> Stream.of(Set.of("ROLE_CLIENT"), Set.of("ROLE_ADMIN"),
                Set.of("ROLE_CLIENT", "ROLE_ADMIN"), Set.of(), Set.of("ROLE_AUDITOR"))
                .map(roles -> Arguments.of(context, roles)));
    }

    static Stream<Arguments> providerFailures() {
        return Stream.of("", "/api").flatMap(context -> Stream.of("initial", "scalar").flatMap(phase ->
                Stream.of(401, 403, 500).filter(status -> status != 403 || phase.equals("scalar"))
                        .map(status -> Arguments.of(context, phase, status))));
    }

    static Stream<Arguments> handlerCases() {
        return Stream.of("", "/api").flatMap(context -> Stream.of("GET", "HEAD").flatMap(method ->
                Stream.of("/orders", "/orders/not-an-integer").flatMap(path -> Stream.of(401, 403, 500)
                        .map(status -> Arguments.of(context, method, path, status)))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class SecurityTestConfiguration {
        @Bean ObjectMapper objectMapper() { return MAPPER; }
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
        @Bean AuthenticatedAccountService accounts() { return mock(AuthenticatedAccountService.class); }
        @Bean OwnOrderQueryAccessService access() { return mock(OwnOrderQueryAccessService.class); }
        @Bean PaymentAccessService paymentAccess() {
            var access = mock(PaymentAccessService.class);
            // Query-provider scenarios must never silently grant a payment permission.
            doThrow(new PaymentRegistrationException(PaymentRegistrationException.Reason.FORBIDDEN))
                    .when(access).requireAdmin(any());
            return access;
        }
    }
}
