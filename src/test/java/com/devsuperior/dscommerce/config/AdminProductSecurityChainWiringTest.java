package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** T009 filter integration only: authorized requests may reach an unimplemented resource. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class AdminProductSecurityChainWiringTest {

    private static final String PRIVATE_DETAIL = "T009_PRIVATE_BODY_QUERY_SQL";
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAccountRepository accounts;
    @Autowired private JwtDecoder decoder;
    @Autowired private JwtEncoder encoder;
    private final BodyAccessProbe body = new BodyAccessProbe();
    private AdminProductTestFixture fixture;
    private AdminProductDatabaseProbe probe;
    private MockMvc mvc;
    private MethodInterceptor lookupFailure;

    @BeforeEach
    void prepareIsolatedRealFilters() {
        assertNoCallerTransaction();
        probe = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(body, security).build();
        body.accesses.set(0);
    }

    @AfterEach
    void restoreRepositoryAndCleanOnlyOwnedRows() {
        if (lookupFailure != null) {
            assertThat(((Advised) accounts).removeAdvice(lookupFailure)).isTrue();
        }
        if (fixture != null) {
            fixture.close();
            assertEmptyDatabase();
        }
        assertNoCallerTransaction();
    }

    @ParameterizedTest(name = "authorized profiles={0}, GET {1}")
    @MethodSource("authorizedRequests")
    void shouldAllowCurrentAdminToReachMvcWithoutInventingAnEndpoint(Set<UserRole> roles, String path)
            throws Exception {
        var account = account(roles);
        var token = fixture.login(account);
        assertThat(probe.roles(account.id())).containsExactlyInAnyOrderElementsOf(roles);
        var before = probe.snapshot();

        var result = mvc.perform(get("/api" + path).contextPath("/api").servletPath(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();

        assertThat(selectedChain("GET", path).getFilters())
                .anyMatch(BearerTokenAuthenticationFilter.class::isInstance);
        assertThat(result.getHandler()).isInstanceOf(HandlerMethod.class);
        if (path.equals("/admin/products/1")) {
            assertThat(result.getResolvedException()).isInstanceOf(ProductNotFoundException.class);
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
        } else {
            assertThat(result.getResolvedException()).isNull();
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
        }
        assertThat(probe.snapshot()).isEqualTo(before);
        assertNoCallerTransaction();
    }

    static Stream<Arguments> authorizedRequests() {
        return Stream.of(Set.of(UserRole.ADMIN), Set.of(UserRole.ADMIN, UserRole.CLIENT))
                .flatMap(roles -> Stream.of("/admin/products", "/admin/products/1", "/admin/categories")
                        .map(path -> Arguments.of(roles, path)));
    }

    @Test
    void shouldSelectFourScopedChainsInTheRequiredOrderForEveryAdministrativeMethod() {
        assertThat(security.getFilterChains()).hasSize(4);
        assertThat(security.getFilterChains()).containsExactly(
                context.getBean("ownOrderQuerySecurityFilterChain", SecurityFilterChain.class),
                context.getBean("orderSecurityFilterChain", SecurityFilterChain.class),
                context.getBean("adminProductSecurityFilterChain", SecurityFilterChain.class),
                context.getBean("securityFilterChain", SecurityFilterChain.class));
        for (var method : List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")) {
            for (var path : List.of("/admin/products", "/admin/products/1", "/admin/categories", "/admin/unsupported")) {
                assertThat(selectedChain(method, path)).isSameAs(security.getFilterChains().get(2));
            }
        }
        assertThat(selectedChain("POST", "/orders")).isSameAs(security.getFilterChains().get(1));
        for (var method : List.of("GET", "HEAD")) {
            for (var path : List.of("/orders", "/orders/1", "/orders/not-an-integer")) {
                assertThat(selectedChain(method, path)).isSameAs(security.getFilterChains().get(0));
            }
        }
        for (var path : List.of("/products", "/products/1", "/login")) {
            assertThat(selectedChain("GET", path)).isSameAs(security.getFilterChains().get(3));
        }
        assertThat(selectedChain("POST", "/login").getFilters())
                .noneMatch(BearerTokenAuthenticationFilter.class::isInstance);
    }

    @ParameterizedTest
    @EnumSource(RejectedIdentity.class)
    void shouldUseAdministrativeEnvelopeAndChallengeBeforeMvc(RejectedIdentity identity) throws Exception {
        String token = switch (identity) {
            case ANONYMOUS -> null;
            case INVALID_TOKEN -> "deliberately-invalid";
            case CLIENT -> fixture.login(account(Set.of(UserRole.CLIENT)));
            case NO_ROLES -> fixture.login(account(Set.of()));
        };
        var before = probe.snapshot();
        var accountsBefore = probe.accountIds();

        var result = rejectedRequest(token);

        assertFilterError(result, identity.status, identity.status == 401 ? "Unauthorized" : "Forbidden",
                identity.status == 401 ? "Authentication required or invalid" : "Product maintenance not permitted");
        assertThat(probe.snapshot()).isEqualTo(before);
        assertThat(probe.accountIds()).isEqualTo(accountsBefore);
    }

    @Test
    void shouldTranslateInternalLookupFailureThroughTheAdministrativeAdapter(CapturedOutput output) throws Exception {
        var account = account(Set.of(UserRole.ADMIN));
        var token = fixture.login(account);
        var before = probe.snapshot();
        var calls = new AtomicInteger();
        lookupFailure = invocation -> {
            if (invocation.getMethod().getName().equals("findByIdWithRoles")
                    && account.id() == (Long) invocation.getArguments()[0]) {
                calls.incrementAndGet();
                throw new DataAccessResourceFailureException(PRIVATE_DETAIL);
            }
            return invocation.proceed();
        };
        ((Advised) accounts).addAdvice(0, lookupFailure);
        int outputStart = output.getAll().length();

        var result = rejectedRequest(token);

        assertFilterError(result, 500, "Internal Server Error", "Product maintenance could not be completed");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(probe.snapshot()).isEqualTo(before);
        assertThat(output.getAll().substring(outputStart)).doesNotContain(PRIVATE_DETAIL, token, account.password());
    }

    @Test
    void shouldNotGrantAdminFromNameOrRolesClaimsOnACorrectlySignedClientToken() throws Exception {
        var client = account(Set.of(UserRole.CLIENT));
        var loginToken = fixture.login(client);
        var decoded = decoder.decode(loginToken);
        var claims = JwtClaimsSet.builder().claims(values -> values.putAll(decoded.getClaims()))
                .claim("name", "ADMIN").claim("roles", List.of("ADMIN")).build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        assertThat(decoder.decode(token).getSubject()).isEqualTo(Long.toString(client.id()));
        var before = probe.snapshot();

        var result = rejectedRequest(token);

        assertFilterError(result, 403, "Forbidden", "Product maintenance not permitted");
        assertThat(probe.roles(client.id())).containsExactly(UserRole.CLIENT);
        assertThat(probe.snapshot()).isEqualTo(before);
    }

    private AdminProductTestFixture.Account account(Set<UserRole> roles) {
        return fixture.createAccount("t009-" + UUID.randomUUID(), "t009-disposable-password", roles);
    }

    private MvcResult rejectedRequest(String token) throws Exception {
        var call = post("/api/admin/products/1/unsupported").contextPath("/api")
                .servletPath("/admin/products/1/unsupported").queryParam("private", PRIVATE_DETAIL)
                .contentType(MediaType.APPLICATION_JSON).content("{\"" + PRIVATE_DETAIL + "\":");
        if (token != null) {
            call.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        body.accesses.set(0);
        return mvc.perform(call).andReturn();
    }

    private void assertFilterError(MvcResult result, int status, String error, String message) throws Exception {
        var response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        var expected = mapper.createObjectNode();
        expected.put("status", status);
        expected.put("error", error);
        expected.put("message", message);
        expected.put("path", "/api/admin/products/1/unsupported");
        expected.putArray("errors");
        assertThat(mapper.readTree(response.getContentAsByteArray())).isEqualTo(expected);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(status == 401 ? "Bearer" : null);
        assertThat(result.getHandler()).isNull();
        assertThat(body.accesses.get()).isZero();
        assertNoCallerTransaction();
    }

    private SecurityFilterChain selectedChain(String method, String path) {
        var request = new MockHttpServletRequest(method, "/api" + path);
        request.setContextPath("/api");
        request.setServletPath(path);
        return security.getFilterChains().stream().filter(chain -> chain.matches(request)).findFirst().orElseThrow();
    }

    private void assertEmptyDatabase() {
        var snapshot = probe.snapshot();
        assertThat(snapshot.products()).isEmpty();
        assertThat(snapshot.categories()).isEmpty();
        assertThat(snapshot.associations()).isEmpty();
        assertThat(snapshot.orders()).isEmpty();
        assertThat(snapshot.items()).isEmpty();
        assertThat(probe.accountIds()).isEmpty();
    }

    private static void assertNoCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    enum RejectedIdentity {
        ANONYMOUS(401), INVALID_TOKEN(401), CLIENT(403), NO_ROLES(403);
        final int status;
        RejectedIdentity(int status) { this.status = status; }
    }

    private static final class BodyAccessProbe implements Filter {
        private final AtomicInteger accesses = new AtomicInteger();

        @Override
        public void doFilter(ServletRequest input, ServletResponse output, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest) input) {
                @Override
                public ServletInputStream getInputStream() throws IOException {
                    accesses.incrementAndGet();
                    return super.getInputStream();
                }

                @Override
                public BufferedReader getReader() throws IOException {
                    accesses.incrementAndGet();
                    return super.getReader();
                }
            }, output);
        }
    }
}
