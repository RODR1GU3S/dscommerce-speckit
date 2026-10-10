package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.controllers.OrderController;
import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;

/** Real login/JWT/database/filter selection; observes authentication without adding an order endpoint. */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc(addFilters = true, print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({OrderTestConfig.class, OrderSecurityChainTest.ObservationConfig.class})
class OrderSecurityChainTest {

    private static final String PASSWORD = "chain-test-password";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtDecoder decoder;
    @Autowired private FilterChainProxy filters;
    @Autowired private OrderTestFixture fixture;
    @Autowired private OrderTestConfig.ControlledClock clock;
    @Autowired private Observation observation;
    @Autowired private JdbcTemplate jdbc;
    private Long accountId;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @BeforeEach
    void clearObservation() {
        observation.authentication = null;
    }

    @AfterEach
    void cleanOwnedFixtures() {
        if (accountId != null) {
            jdbc.update("delete from tb_order_item where order_id in "
                    + "(select id from tb_order where buyer_id = ?)", accountId);
            jdbc.update("delete from tb_order where buyer_id = ?", accountId);
        }
        fixture.cleanUp();
    }

    @ParameterizedTest
    @CsvSource({"POST,/orders,true", "GET,/orders,false", "PUT,/orders,false", "PATCH,/orders,false",
            "DELETE,/orders,false", "HEAD,/orders,false", "OPTIONS,/orders,false", "POST,/orders/,false",
            "POST,/orders/1,false", "POST,/login,false", "GET,/products,false", "GET,/products/1,false",
            "GET,/h2-console/,false", "POST,/h2-console/login.do,false", "POST,/api/orders,false"})
    void shouldSelectBearerOnlyForTheExactPostRouteEvenWithAContextPath(String method, String path, boolean bearer) {
        var request = new MockHttpServletRequest(method, "/api" + path);
        request.setContextPath("/api");
        request.setServletPath(path);
        var chain = filters.getFilterChains().stream().filter(candidate -> candidate.matches(request)).findFirst().orElseThrow();
        assertThat(chain.getFilters().stream().anyMatch(BearerTokenAuthenticationFilter.class::isInstance)).isEqualTo(bearer);
        assertThat(chain.getFilters()).noneMatch(CsrfFilter.class::isInstance);
    }

    @ParameterizedTest
    @MethodSource("allowedProfiles")
    void shouldAuthorizeDatabaseProfilesAndExposeOnlyTheSubjectIdentityAtTheOrderController(Set<UserRole> roles) throws Exception {
        var token = login(roles, true);
        var productId = fixture.createProduct("chain-valid-product", new BigDecimal("10.00"));
        var body = "{\"items\":[{\"productId\":%d,\"quantity\":2}]}".formatted(productId);

        var result = mvc.perform(post("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(handler().handlerType(OrderController.class))
                .andExpect(status().isCreated()).andReturn();

        assertThat(observation.authentication).isNotNull();
        assertThat(observation.authentication.principal()).isEqualTo(new AuthenticatedAccountPrincipal(accountId));
        assertThat(observation.authentication.authorities()).containsExactlyInAnyOrderElementsOf(
                roles.stream().map(role -> "ROLE_" + role.name()).toList());
        assertThat(observation.authentication.credentialsAbsent()).isTrue();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void shouldDenyMissingBearerAndDatabaseProfilesWithoutGrantingForgedTokenRoles() throws Exception {
        mvc.perform(post("/orders")).andExpect(status().isUnauthorized());
        assertThat(observation.authentication).isNull();
        var token = login(Set.of(), true);
        observation.authentication = null;
        mvc.perform(post("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        assertThat(observation.authentication).isNull();
    }

    @Test
    void shouldPreservePublicFallbackWithAnIrrelevantMalformedBearer() throws Exception {
        mvc.perform(get("/products").header(HttpHeaders.AUTHORIZATION, "Bearer irrelevant.invalid.token"))
                .andExpect(status().isOk());
        mvc.perform(get("/products/1").header(HttpHeaders.AUTHORIZATION, "Bearer irrelevant.invalid.token"))
                .andExpect(status().isOk());
        login(Set.of(), false);
    }

    private String login(Set<UserRole> roles, boolean forgedClaims) throws Exception {
        var name = "chain-" + UUID.randomUUID();
        accountId = fixture.createAccount(name, PASSWORD, roles);
        var body = mapper.writeValueAsString(java.util.Map.of("name", name, "password", PASSWORD));
        var result = mvc.perform(post("/login").header(HttpHeaders.AUTHORIZATION, "Bearer irrelevant.invalid.token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        var token = mapper.readTree(result.getResponse().getContentAsString()).path("accessToken").textValue();
        var decoded = decoder.decode(token);
        // Login uses real time; keep the operation clock consistent with this valid token.
        clock.setInstant(Instant.now());
        assertThat(decoded.getSubject()).isEqualTo(accountId.toString());
        assertThat(decoded.getHeaders().get("alg")).hasToString("HS256");
        if (!forgedClaims) { return token; }
        return fixture.signedToken(JwtClaimsSet.builder().claims(claims -> claims.putAll(decoded.getClaims()))
                .claim("name", "another-owner").claim("roles", List.of("ROLE_ADMIN", "ROLE_CLIENT", "ROLE_AUDITOR")).build());
    }

    static Stream<Set<UserRole>> allowedProfiles() {
        return Stream.of(Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN), Set.of(UserRole.CLIENT, UserRole.ADMIN));
    }

    record CapturedAuthentication(AuthenticatedAccountPrincipal principal, Set<String> authorities, boolean credentialsAbsent) { }

    static class Observation {
        volatile CapturedAuthentication authentication;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationConfig {
        @Bean
        Observation orderChainObservation() { return new Observation(); }

        @Bean
        @Order(Ordered.LOWEST_PRECEDENCE)
        GenericFilterBean observeAuthenticationAfterSecurity(Observation observation) {
            return new GenericFilterBean() {
                @Override
                public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal principal) {
                        observation.authentication = new CapturedAuthentication(principal,
                                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()),
                                authentication.getCredentials() == null);
                    }
                    chain.doFilter(request, response);
                }
            };
        }
    }
}
