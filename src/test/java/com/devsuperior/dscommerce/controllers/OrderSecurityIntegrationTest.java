package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.config.JwtProperties;
import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.OrderService;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.filter.GenericFilterBean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real login/decoder/filters/persistence; test-only probes do not replace application beans. */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc(addFilters = true, print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({OrderTestConfig.class, OrderSecurityIntegrationTest.SecurityProbeConfig.class})
@ExtendWith(OutputCaptureExtension.class)
class OrderSecurityIntegrationTest extends OrderHttpTestSupport {

    private static final String INVALID_BODY = "{\"T027_BODY_CANARY\":";
    @Autowired private OrderService orderService;
    @Autowired private UserAccountRepository accounts;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private BetweenFilterAndService probe;
    private JdbcTemplate independent;
    private MethodInterceptor lookupFailure;
    private MethodInterceptor serviceObservation;
    private int serviceCalls;
    private boolean serviceTransaction;

    @BeforeEach
    void prepareIndependentReadsAndProbes() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(AopUtils.isAopProxy(orderService)).isTrue();
        assertThat(accounts).isInstanceOf(Advised.class);
        var url = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        independent = new JdbcTemplate(new DriverManagerDataSource(Objects.requireNonNull(url), "sa", ""));
        probe.clear();
        serviceCalls = 0;
        serviceTransaction = false;
        serviceObservation = invocation -> {
            if (invocation.getMethod().getName().equals("register")) {
                serviceCalls++;
                serviceTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            }
            return invocation.proceed();
        };
        // Append inside the existing transaction interceptor; observe, never replace, the real service.
        ((Advised) orderService).addAdvice(serviceObservation);
    }

    @AfterEach
    void clearOnlyTestInstrumentation() {
        probe.clear();
        if (lookupFailure != null) {
            assertThat(((Advised) accounts).removeAdvice(lookupFailure)).isTrue();
            lookupFailure = null;
        }
        assertThat(((Advised) orderService).removeAdvice(serviceObservation)).isTrue();
    }

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @ParameterizedTest(name = "database profiles {0}, token variant {1}")
    @MethodSource("authorizedScenarios")
    void shouldUseSubjectAndDatabaseProfilesRegardlessOfNameAndRolesClaims(
            Set<UserRole> roles, TokenVariant variant) throws Exception {
        var account = login(roles);
        var decoyName = "other-owner-" + UUID.randomUUID();
        var decoyId = fixture.createAccount(decoyName, "other-owner-test-password", Set.of());
        ownedAccounts.add(decoyId);
        assertThat(decoyId).isNotEqualTo(account.id());
        assertDatabaseRoles(decoyId, Set.of());
        var product = fixture.createProduct("security-positive-price", new BigDecimal("10.00"));
        var token = account.token();
        if (variant != TokenVariant.LOGIN) {
            var falseRoles = variant == TokenVariant.OTHER_NAME_EMPTY_ROLES
                    ? List.<String>of() : List.of("ROLE_AUDITOR");
            var claims = JwtClaimsSet.builder().claims(values -> values.putAll(account.decoded().getClaims()))
                    .claim("name", decoyName).claim("roles", falseRoles).build();
            // Existing real HS256 encoder and disposable test key, no token postprocessor/mock decoder.
            token = fixture.signedToken(claims);
            var decoded = decodeWithoutLeakingToken(token);
            assertThat(decoded.getSubject()).isEqualTo(account.decoded().getSubject());
            assertThat(decoded.getIssuedAt()).isEqualTo(account.decoded().getIssuedAt());
            assertThat(decoded.getExpiresAt()).isEqualTo(account.decoded().getExpiresAt());
            assertThat(decoded.getClaimAsString("name")).isEqualTo(decoyName);
            assertThat(decoded.getClaimAsStringList("roles")).containsExactlyElementsOf(falseRoles);
        }
        // No CLIENT/ADMIN claims are needed: permission must come from the existing account's DB roles.
        assertDatabaseRoles(account.id(), roles);
        var body = """
                {"items":[{"productId":%d,"quantity":2}]}
                """.formatted(product);

        var response = assertConfirmation(token, body, account.id(),
                List.of(new ExpectedLine(product, 2, "10.00", "20.00")), "20.00");

        assertThat(response.path("accountId").longValue()).isNotEqualTo(decoyId);
        // Order SQL is reached only after HTTP 201; fresh connections, no external test transaction.
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var url = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        var independent = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        var orderId = response.path("id").longValue();
        Long storedBuyer = independent.queryForObject("select buyer_id from tb_order where id = ?", Long.class, orderId);
        assertThat(storedBuyer).isEqualTo(account.id());
        assertThat(independent.queryForObject("select count(*) from tb_order where buyer_id = ?",
                Integer.class, account.id())).isEqualTo(1);
        assertThat(independent.queryForObject("select count(*) from tb_order where buyer_id = ?",
                Integer.class, decoyId)).isZero();
    }

    static Stream<Arguments> authorizedScenarios() {
        return Stream.of(Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN), Set.of(UserRole.CLIENT, UserRole.ADMIN))
                .flatMap(roles -> Stream.of(TokenVariant.values()).map(variant -> Arguments.of(roles, variant)));
    }

    @ParameterizedTest(name = "JWT {0}, request {1}")
    @MethodSource("invalidJwtScenarios")
    void shouldRejectInvalidJwtBeforeReadingBody(TokenFault fault, RequestVariant request,
                                                CapturedOutput output) throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var product = fixture.createProduct("jwt-rejection-price", new BigDecimal("10.00"));
        var baseline = counts();
        var sensitive = sensitiveValues(account);
        // Mint immediately before dispatch. +/-5 s intentionally falls inside the decoder's default skew.
        var reference = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        clock.setInstant(reference);
        var token = faultyToken(fault, account, reference);
        if (token != null) sensitive.add(token);
        var body = request.body().json(product);
        assertThat(Instant.now()).as("fixture leaves temporal claims in the intended interval")
                .isBefore(reference.plusSeconds(5));
        if (fault == TokenFault.ACCOUNT_ABSENT) {
            assertThat(independent.queryForObject("select count(*) from tb_user_account where id = ?",
                    Long.class, Long.MAX_VALUE)).isZero();
        }
        assertSecurityPrecedesOrderData(fault.name(), token, body, request, product, 401, baseline, sensitive, output);
    }

    static Stream<Arguments> invalidJwtScenarios() {
        return Stream.of(TokenFault.values()).flatMap(fault ->
                requestVariants().map(request -> Arguments.of(fault, request)));
    }

    @ParameterizedTest(name = "no database roles, request {0}")
    @MethodSource("requestVariants")
    void shouldForbidAccountWithoutProfilesEvenWithAdminClaim(RequestVariant request,
                                                             CapturedOutput output) throws Exception {
        var account = login(Set.of()); // The real login must still return 200.
        var product = fixture.createProduct("no-profile-price", new BigDecimal("10.00"));
        var claims = JwtClaimsSet.builder().claims(values -> values.putAll(account.decoded().getClaims()))
                .claim("roles", List.of("ADMIN", "ROLE_ADMIN")).build();
        var token = fixture.signedToken(claims);
        assertDatabaseRoles(account.id(), Set.of());
        var sensitive = sensitiveValues(account);
        sensitive.add(token);
        assertSecurityPrecedesOrderData("NO_PROFILES_FORGED_ADMIN", token,
                request.body().json(product), request, product,
                403, counts(), sensitive, output);
    }

    @ParameterizedTest(name = "internal lookup failure, request {0}")
    @MethodSource("requestVariants")
    void shouldReturnInternalErrorForResolutionFailureBeforeReadingBody(RequestVariant request,
                                                                       CapturedOutput output) throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var product = fixture.createProduct("lookup-failure-price", new BigDecimal("10.00"));
        var baseline = counts();
        var sensitive = sensitiveValues(account);
        var calls = new int[1];
        lookupFailure = invocation -> {
            if (invocation.getMethod().getName().equals("findByIdWithRoles")
                    && Objects.equals(account.id(), invocation.getArguments()[0])) {
                calls[0]++;
                // A fault on the real repository proxy, before SQL, scoped to this account/test only.
                throw new DataAccessResourceFailureException("SELECT T027_LOOKUP_CANARY "
                        + String.join(" ", sensitive));
            }
            return invocation.proceed();
        };
        ((Advised) accounts).addAdvice(0, lookupFailure);
        assertAll(
                () -> assertSecurityPrecedesOrderData("LOOKUP_FAILURE", account.token(),
                        request.body().json(product), request, product,
                        500, baseline, sensitive, output),
                () -> assertThat(calls[0]).as("real converter reached instrumented lookup once").isEqualTo(1));
    }

    static Stream<RequestVariant> requestVariants() {
        return Stream.of(
                // Preserve both original combinations before adding the missing body/context matrix.
                new RequestVariant(false, OrderDataBody.VALID),
                new RequestVariant(true, OrderDataBody.UNREADABLE),
                new RequestVariant(false, OrderDataBody.EMPTY_ITEMS),
                new RequestVariant(true, OrderDataBody.EMPTY_ITEMS),
                new RequestVariant(false, OrderDataBody.ZERO_QUANTITY),
                new RequestVariant(true, OrderDataBody.ZERO_QUANTITY));
    }

    @ParameterizedTest(name = "late {0}, database profiles {1}, context {2}")
    @MethodSource("lateGuardScenarios")
    void shouldRecheckAccountAndProfilesAfterFilterBeforeService(Mutation mutation, Set<UserRole> roles,
                                                                boolean contextPath,
                                                                CapturedOutput output) throws Exception {
        var account = login(roles);
        var product = fixture.createProduct("late-guard-price", new BigDecimal("10.00"));
        var sensitive = sensitiveValues(account);
        var baseline = counts();
        probe.arm(account.id(), mutation);
        assertAll(
                () -> assertRejected("LATE_" + mutation, account.token(), validBody(product), contextPath,
                        mutation == Mutation.DELETE_ACCOUNT ? 401 : 403, baseline, sensitive, output),
                () -> assertThat(probe.reached).as("mutation after real authentication/authorization").isTrue(),
                () -> assertThat(probe.committed).as("separate transaction completed before controller").isTrue(),
                () -> assertThat(probe.transactionActive).as("fixture mutation had its own transaction").isTrue(),
                () -> assertThat(serviceCalls).as("real service called after filter mutation").isEqualTo(1),
                () -> assertThat(serviceTransaction).as("service invoked inside Spring transaction").isTrue(),
                () -> assertThat(independent.queryForObject(
                        "select count(*) from tb_user_account_role where user_account_id = ?",
                        Long.class, account.id())).isZero(),
                () -> assertThat(independent.queryForObject("select count(*) from tb_user_account where id = ?",
                        Long.class, account.id())).isEqualTo(mutation == Mutation.DELETE_ACCOUNT ? 0L : 1L));
    }

    static Stream<Arguments> lateGuardScenarios() {
        return Stream.of(Mutation.DELETE_ACCOUNT, Mutation.REVOKE_PROFILES).flatMap(mutation ->
                Stream.of(Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN), Set.of(UserRole.CLIENT, UserRole.ADMIN))
                        .flatMap(roles -> Stream.of(false, true)
                                .map(context -> Arguments.of(mutation, roles, context))));
    }

    @ParameterizedTest(name = "direct Spring proxy invalid identity {0}")
    @MethodSource("invalidPrincipals")
    void shouldRejectInvalidIdentityThroughRealServiceProxy(String label, AuthenticatedAccountPrincipal principal,
                                                           CapturedOutput output) {
        var product = fixture.createProduct("direct-invalid-identity", new BigDecimal("10.00"));
        var before = counts();
        var start = output.getAll().length();
        Throwable observed = null;
        try {
            orderService.register(request(product), principal);
        } catch (BadCredentialsException | AccessDeniedException failure) {
            observed = failure;
        }
        var failure = observed;
        System.out.printf("T027_DIRECT case=%s exception=%s%n", label,
                failure == null ? "NONE" : failure.getClass().getSimpleName());
        assertAll(
                () -> assertThat(failure).isInstanceOf(BadCredentialsException.class)
                        .hasMessage("Authentication required or invalid"),
                () -> assertThat(counts()).isEqualTo(before),
                () -> assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(),
                () -> assertThat(serviceCalls).isEqualTo(1),
                () -> assertThat(serviceTransaction).isTrue(),
                () -> assertConfidential(output.getAll().substring(start), List.of()));
    }

    static Stream<Arguments> invalidPrincipals() {
        return Stream.of(Arguments.of("no principal", null),
                Arguments.of("null account id", new AuthenticatedAccountPrincipal(null)),
                Arguments.of("zero account id", new AuthenticatedAccountPrincipal(0L)),
                Arguments.of("negative account id", new AuthenticatedAccountPrincipal(-1L)),
                Arguments.of("missing account", new AuthenticatedAccountPrincipal(Long.MAX_VALUE)));
    }

    @ParameterizedTest(name = "direct Spring proxy {0}, original profiles {1}")
    @MethodSource("directAccountScenarios")
    void shouldRecheckCurrentAccountAndProfilesThroughRealServiceProxy(Mutation mutation, Set<UserRole> roles,
                                                                      CapturedOutput output) {
        var account = fixture.createAccount("direct-guard-" + UUID.randomUUID(), "direct-test-password", roles);
        ownedAccounts.add(account);
        var product = fixture.createProduct("direct-revoked-profile", new BigDecimal("10.00"));
        // Fixture commit, outside any external test transaction; the proxy must re-read this state.
        mutate(account, mutation, jdbc, transactions);
        var before = counts();
        var start = output.getAll().length();
        Throwable observed = null;
        try {
            orderService.register(request(product), new AuthenticatedAccountPrincipal(account));
        } catch (BadCredentialsException | AccessDeniedException failure) {
            observed = failure;
        }
        var failure = observed;
        System.out.printf("T027_DIRECT case=%s_%s exception=%s%n", mutation, roles,
                failure == null ? "NONE" : failure.getClass().getSimpleName());
        assertAll(
                () -> assertThat(failure).isInstanceOf(mutation == Mutation.DELETE_ACCOUNT
                        ? BadCredentialsException.class : AccessDeniedException.class)
                        .hasMessage(mutation == Mutation.DELETE_ACCOUNT
                                ? "Authentication required or invalid" : "Order registration not permitted"),
                () -> assertThat(counts()).isEqualTo(before),
                () -> assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(),
                () -> assertThat(serviceCalls).isEqualTo(1),
                () -> assertThat(serviceTransaction).isTrue(),
                () -> assertConfidential(output.getAll().substring(start), List.of()));
    }

    static Stream<Arguments> directAccountScenarios() {
        return Stream.of(Mutation.DELETE_ACCOUNT, Mutation.REVOKE_PROFILES).flatMap(mutation ->
                Stream.of(Set.<UserRole>of(), Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN),
                                Set.of(UserRole.CLIENT, UserRole.ADMIN))
                        .map(roles -> Arguments.of(mutation, roles)));
    }

    @Test
    void shouldKeepRealAuthenticationAndConfirmationWithServletContextPath(CapturedOutput output) throws Exception {
        var account = login(Set.of(UserRole.ADMIN));
        var product = fixture.createProduct("context-path-control", new BigDecimal("10.00"));
        var before = counts();
        var result = mvc.perform(post("/api/orders").contextPath("/api").servletPath("/orders")
                .header("Authorization", "Bearer " + account.token()).contentType(MediaType.APPLICATION_JSON)
                .content(validBody(product))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var response = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("accountId").longValue()).isEqualTo(account.id());
        assertThat(response.path("total").textValue()).isEqualTo("20.00");
        assertThat(result.getResponse().getHeader("Location")).isNull();
        assertThat(counts()).isEqualTo(new Counts(before.orders + 1, before.items + 1));
        assertThat(independent.queryForObject("select buyer_id from tb_order where id = ?", Long.class,
                response.path("id").longValue())).isEqualTo(account.id());
    }

    private String faultyToken(TokenFault fault, LoggedAccount account, Instant now) throws Exception {
        if (fault == TokenFault.ABSENT) return null;
        if (fault == TokenFault.MALFORMED) return "T027_malformed_not_a_JWT";
        if (fault == TokenFault.SUB_NUMERIC) {
            // Preserve the original numeric JSON type: JWTClaimsSet would stringify this claim.
            var signed = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(Map.of(
                    "sub", account.id(), "iat", now.minusSeconds(120).getEpochSecond(),
                    "exp", now.plusSeconds(120).getEpochSecond())));
            signed.sign(new MACSigner(jwtProperties.getSecret()));
            assertThat(signed.verify(new MACVerifier(jwtProperties.getSecret()))).isTrue();
            assertThat(signed.getPayload().toJSONObject().get("sub") instanceof Number).isTrue();
            assertThat(decodeWithoutLeakingToken(signed.serialize()).getSubject())
                    .isEqualTo(Long.toString(account.id()));
            return signed.serialize();
        }
        if (fault == TokenFault.EXP_EQUALS_IAT || fault == TokenFault.EXP_BEFORE_IAT) {
            // Spring's encoder creates a Jwt that rejects these claims while preparing the fixture.
            // Nimbus signs the intentionally invalid interval without normalizing it.
            var invalidInterval = new JWTClaimsSet.Builder().subject(Long.toString(account.id()))
                    .issueTime(Date.from(now.minusSeconds(10)))
                    .expirationTime(Date.from(now.minusSeconds(fault == TokenFault.EXP_EQUALS_IAT ? 10 : 11)))
                    .build();
            var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), invalidInterval);
            signed.sign(new MACSigner(jwtProperties.getSecret()));
            assertThat(signed.verify(new MACVerifier(jwtProperties.getSecret()))).isTrue();
            return signed.serialize();
        }
        var claims = JwtClaimsSet.builder().subject(Long.toString(account.id()))
                .issuedAt(now.minusSeconds(120)).expiresAt(now.plusSeconds(120))
                .claim("name", "T027_SIGNATURE_ORIGINAL");
        switch (fault) {
            case EXPIRED_RECENTLY -> claims.expiresAt(now.minusSeconds(5));
            case MISSING_EXP -> claims.claims(values -> values.remove("exp"));
            case MISSING_IAT -> claims.claims(values -> values.remove("iat"));
            case MISSING_SUB -> claims.claims(values -> values.remove("sub"));
            case SUB_LEADING_ZERO -> claims.subject("0" + account.id());
            case SUB_PLUS -> claims.subject("+" + account.id());
            case SUB_LEADING_SPACE -> claims.subject(" " + account.id());
            case SUB_TRAILING_SPACE -> claims.subject(account.id() + " ");
            case SUB_ZERO -> claims.subject("0");
            case SUB_NEGATIVE -> claims.subject("-1");
            case SUB_OVERFLOW -> claims.subject("9223372036854775808");
            case SUB_NOT_DECIMAL -> claims.subject("1.0");
            case ACCOUNT_ABSENT -> claims.subject(Long.toString(Long.MAX_VALUE));
            case FUTURE_IAT -> claims.issuedAt(now.plusSeconds(5));
            case FUTURE_NBF -> claims.notBefore(now.plusSeconds(5));
            default -> { }
        }
        var token = fixture.signedToken(claims.build());
        var parsed = SignedJWT.parse(token);
        assertThat(parsed.verify(new MACVerifier(jwtProperties.getSecret()))).isTrue();
        if (fault == TokenFault.TAMPERED) {
            // Change only an ignored name claim: subject/account/temporal data remain valid.
            var payload = Base64URL.encode(parsed.getJWTClaimsSet().toString().replace(
                    "T027_SIGNATURE_ORIGINAL", "T027_SIGNATURE_CHANGED"));
            assertThat(payload.equals(parsed.getPayload().toBase64URL())).isFalse();
            var tampered = SignedJWT.parse(parsed.getHeader().toBase64URL() + "." + payload + "." + parsed.getSignature());
            assertThat(tampered.getJWTClaimsSet().getSubject()).isEqualTo(Long.toString(account.id()));
            assertThat(tampered.getJWTClaimsSet().getIssueTime()).isEqualTo(parsed.getJWTClaimsSet().getIssueTime());
            assertThat(tampered.getJWTClaimsSet().getExpirationTime()).isEqualTo(parsed.getJWTClaimsSet().getExpirationTime());
            assertThat(tampered.verify(new MACVerifier(jwtProperties.getSecret()))).isFalse();
            return tampered.serialize();
        }
        if (fault == TokenFault.WRONG_KEY) {
            var wrong = jwtProperties.getSecret();
            wrong[0] ^= 1;
            var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), parsed.getJWTClaimsSet());
            signed.sign(new MACSigner(wrong));
            assertThat(signed.verify(new MACVerifier(jwtProperties.getSecret()))).isFalse();
            return signed.serialize();
        }
        if (fault == TokenFault.WRONG_ALGORITHM) {
            // JCA HMAC accepts the existing 256-bit key for HS384: only the algorithm changes.
            var header = new JWSHeader(JWSAlgorithm.HS384).toBase64URL();
            var payload = parsed.getPayload().toBase64URL();
            var mac = Mac.getInstance("HmacSHA384");
            mac.init(new SecretKeySpec(jwtProperties.getSecret(), "HmacSHA384"));
            var signature = Base64URL.encode(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.US_ASCII)));
            return header + "." + payload + "." + signature;
        }
        return token;
    }

    private void assertSecurityPrecedesOrderData(String label, String token, String body, RequestVariant request,
                                                long product, int expected, Counts baseline,
                                                List<String> sensitive, CapturedOutput output) throws Exception {
        if (request.body() == OrderDataBody.EMPTY_ITEMS || request.body() == OrderDataBody.ZERO_QUANTITY) {
            var parsed = mapper.readTree(body); // Verify fixture shape; dispatch the unchanged raw body below.
            assertThat(parsed.isObject()).isTrue();
            assertThat(parsed.path("items").isArray()).isTrue();
            if (request.body() == OrderDataBody.EMPTY_ITEMS) {
                assertThat(parsed.path("items").size()).isZero();
            } else {
                assertThat(parsed.path("items").size()).isEqualTo(1);
                assertThat(parsed.path("items").get(0).path("productId").longValue()).isEqualTo(product);
                assertThat(parsed.path("items").get(0).path("quantity").intValue()).isZero();
                assertThat(independent.queryForObject("select count(*) from tb_product where id = ?",
                        Long.class, product)).as("zero quantity must reference an existing fixture product").isEqualTo(1);
            }
        }
        assertRejected(label, token, body, request.contextPath(), expected, baseline, sensitive, output);
        assertThat(serviceCalls).as("security rejection must precede order service invocation").isZero();
        System.out.printf("T040_PRECEDENCE case=%s body=%s context=%s status=%d serviceCalls=%d%n",
                label, request.body(), request.contextPath(), expected, serviceCalls);
    }

    private void assertRejected(String label, String token, String body, boolean contextPath, int expected,
                                Counts baseline, List<String> sensitive, CapturedOutput output) throws Exception {
        var path = contextPath ? "/api/orders" : "/orders";
        sensitive = new ArrayList<>(sensitive);
        sensitive.add(body);
        var confidentialValues = List.copyOf(sensitive);
        var builder = post(path).servletPath("/orders").contentType(MediaType.APPLICATION_JSON).content(body);
        if (contextPath) builder.contextPath("/api");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        var start = output.getAll().length();
        MvcResult observed = null;
        boolean escapedResolutionFailure = false;
        try {
            observed = mvc.perform(builder).andReturn();
        } catch (RuntimeException | ServletException failure) {
            // Only this known application failure represents the missing filter-to-500 adapter.
            Throwable root = failure;
            while (root != null && !(root instanceof OrderAccountResolutionException)) root = root.getCause();
            if (expected != 500 || root == null) throw failure;
            escapedResolutionFailure = true;
        }
        var result = observed;
        var logs = output.getAll().substring(start);
        var response = result == null ? "" : result.getResponse().getContentAsString();
        var headers = result == null ? "" : result.getResponse().getHeaderNames().stream()
                .map(name -> name + ":" + result.getResponse().getHeaders(name)).toList().toString();
        var after = counts();
        System.out.printf("T027_HTTP case=%s context=%s invalidBody=%s expected=%d actual=%s escaped=%s ordersDelta=%d itemsDelta=%d%n",
                label, contextPath, body.equals(INVALID_BODY), expected,
                result == null ? "NONE" : result.getResponse().getStatus(), escapedResolutionFailure,
                after.orders - baseline.orders, after.items - baseline.items);
        // Read the existing real-service probe without changing any contractual assertion or fixture.
        System.out.printf("T035_SERVICE case=%s context=%s calls=%d%n", label, contextPath, serviceCalls);
        var expectedBody = mapper.createObjectNode().put("status", expected)
                .put("error", HttpStatus.valueOf(expected).getReasonPhrase())
                .put("message", expected == 401 ? "Authentication required or invalid"
                        : expected == 403 ? "Order registration not permitted" : "Order registration could not be completed")
                .put("path", path);
        assertAll(
                () -> assertThat(result).as("security failure must become an HTTP response, not escape MVC").isNotNull(),
                () -> { if (result != null) assertThat(result.getResponse().getStatus()).isEqualTo(expected); },
                () -> { if (result != null) {
                    assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
                    assertThat(mapper.readTree(response)).isEqualTo(expectedBody);
                } },
                () -> { if (result != null && expected == 401)
                    assertThat(result.getResponse().getHeader("WWW-Authenticate")).isEqualTo("Bearer"); },
                () -> assertThat(after).as("independent connection after synchronous request").isEqualTo(baseline),
                () -> assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(),
                () -> assertConfidential(response + headers, confidentialValues),
                () -> assertConfidential(logs, confidentialValues));
    }

    private List<String> sensitiveValues(LoggedAccount account) {
        var values = new ArrayList<String>();
        values.add(account.token());
        values.add(independent.queryForObject("select password_hash from tb_user_account where id = ?",
                String.class, account.id()));
        values.add("order-http-test-password");
        values.add(Base64.getEncoder().encodeToString(jwtProperties.getSecret()));
        values.add(new String(jwtProperties.getSecret(), StandardCharsets.UTF_8));
        values.add(INVALID_BODY);
        values.add("T027_BODY_CANARY");
        values.add("T027_LOOKUP_CANARY");
        return values;
    }

    private static void assertConfidential(String text, List<String> sensitive) {
        for (var value : sensitive) {
            // Assert booleans: failing diagnostics must not print the credentials under inspection.
            assertThat(text.contains(value)).as("credential or request/cause canary exposed").isFalse();
        }
        assertThat(text.matches("(?is).*\\b(select|insert|update|delete)\\s+.*"))
                .as("SQL must not be exposed").isFalse();
        assertThat(text.matches("(?s).*\\bat [\\w.$]+\\([^\\n]*\\.java:\\d+\\).*"))
                .as("Java stack trace must not be exposed").isFalse();
        assertThat(text.contains("password_hash") || text.contains("$2a$") || text.contains("$2b$"))
                .as("password hash must not be exposed").isFalse();
    }

    private Counts counts() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return new Counts(independent.queryForObject("select count(*) from tb_order", Long.class),
                independent.queryForObject("select count(*) from tb_order_item", Long.class));
    }

    private static String validBody(long product) {
        return "{\"items\":[{\"productId\":" + product + ",\"quantity\":2}]}";
    }

    private static OrderRequestDTO request(long product) {
        return new OrderRequestDTO(List.of(new OrderItemRequestDTO(product, 2)));
    }

    private static void mutate(long account, Mutation mutation, JdbcTemplate jdbc,
                               PlatformTransactionManager manager) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            jdbc.update("delete from tb_user_account_role where user_account_id = ?", account);
            if (mutation == Mutation.DELETE_ACCOUNT) {
                assertThat(jdbc.update("delete from tb_user_account where id = ?", account)).isEqualTo(1);
            }
        });
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    record Counts(long orders, long items) { }

    record RequestVariant(boolean contextPath, OrderDataBody body) { }

    enum OrderDataBody {
        VALID, UNREADABLE, EMPTY_ITEMS, ZERO_QUANTITY;

        String json(long product) {
            return switch (this) {
                case VALID -> validBody(product);
                case UNREADABLE -> INVALID_BODY;
                case EMPTY_ITEMS -> "{\"items\":[]}";
                case ZERO_QUANTITY -> "{\"items\":[{\"productId\":" + product + ",\"quantity\":0}]}";
            };
        }
    }

    enum TokenFault {
        ABSENT, MALFORMED, TAMPERED, WRONG_KEY, WRONG_ALGORITHM, EXPIRED_RECENTLY,
        MISSING_EXP, MISSING_IAT, MISSING_SUB, EXP_EQUALS_IAT, EXP_BEFORE_IAT,
        SUB_LEADING_ZERO, SUB_PLUS, SUB_LEADING_SPACE, SUB_TRAILING_SPACE,
        SUB_ZERO, SUB_NEGATIVE, SUB_OVERFLOW, SUB_NOT_DECIMAL, ACCOUNT_ABSENT, FUTURE_IAT, FUTURE_NBF, SUB_NUMERIC
    }

    enum Mutation { DELETE_ACCOUNT, REVOKE_PROFILES }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityProbeConfig {
        @Bean
        BetweenFilterAndService betweenFilterAndService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
            return new BetweenFilterAndService(jdbc, manager);
        }
    }

    /** Outer filter ordered after Spring Security: reached only after real authentication/authorization. */
    static class BetweenFilterAndService extends GenericFilterBean implements Ordered {
        private final JdbcTemplate jdbc;
        private final PlatformTransactionManager manager;
        private Long account;
        private Mutation mutation;
        private boolean reached;
        private boolean committed;
        private boolean transactionActive;

        BetweenFilterAndService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
            this.jdbc = jdbc;
            this.manager = manager;
        }

        void arm(long account, Mutation mutation) {
            this.account = account;
            this.mutation = mutation;
        }

        void clear() {
            account = null;
            mutation = null;
            reached = committed = transactionActive = false;
        }

        @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (account != null && request instanceof HttpServletRequest http && http.getMethod().equals("POST")
                    && http.getRequestURI().equals(http.getContextPath() + "/orders")) {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                assertThat(authentication).isNotNull();
                assertThat(authentication.isAuthenticated()).isTrue();
                assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedAccountPrincipal(account));
                assertThat(authentication.getAuthorities()).anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_CLIENT") || authority.getAuthority().equals("ROLE_ADMIN"));
                reached = true;
                var selectedAccount = account;
                account = null;
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                var tx = new TransactionTemplate(manager);
                tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx.executeWithoutResult(status -> {
                    transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
                    jdbc.update("delete from tb_user_account_role where user_account_id = ?", selectedAccount);
                    if (mutation == Mutation.DELETE_ACCOUNT) {
                        assertThat(jdbc.update("delete from tb_user_account where id = ?", selectedAccount)).isEqualTo(1);
                    }
                });
                committed = true;
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            }
            chain.doFilter(request, response);
        }
    }

    enum TokenVariant {
        LOGIN, OTHER_NAME_EMPTY_ROLES, OTHER_NAME_UNRELATED_ROLES
    }
}
