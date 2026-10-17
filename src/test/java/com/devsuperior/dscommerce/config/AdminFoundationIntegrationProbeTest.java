package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.AdminAccessService;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** T013 probes only: real login/filter/database/guard/advice; no future product endpoint or diagnosis. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminFoundationIntegrationProbeTest.ProbeConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class AdminFoundationIntegrationProbeTest {

    private static final String ROOT = "/admin/test-only-foundation/";
    private static final String MARKER = "T013_DISPOSABLE_BODY_QUERY_SQL";
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAccountRepository accounts;
    @Autowired private ProbeState state;
    @Autowired private ProbeService service;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MethodInterceptor lookupObservation;
    private MockMvc mvc;

    @BeforeEach
    void prepareOnlyCommittedOwnedDataAndRealComponents() {
        assertNoCallerTransaction();
        state.reset();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        assertThat(AopUtils.isAopProxy(accounts)).isTrue();
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        assertThat(context.getBeansOfType(ProbeController.class)).hasSize(1);
        fixture = new AdminProductTestFixture(context);
        var product = fixture.createProduct("Foundation probe", "Committed foundation probe product",
                "https://example.invalid/foundation-probe.jpg", new BigDecimal("10.00"));
        var category = fixture.createCategory("Foundation probe category");
        fixture.associate(product.id(), category.id());
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @AfterEach
    void removeOnlyLocalAdviceAndOwnedFixturesAfterAssertions() {
        if (lookupObservation != null) {
            assertThat(((Advised) accounts).removeAdvice(lookupObservation)).isTrue();
        }
        if (fixture != null) {
            fixture.close();
            assertEmptyDatabase();
        }
        state.reset();
        sql.clear();
        assertNoCallerTransaction();
    }

    @ParameterizedTest(name = "point={0}, change={1}, profiles={2}, context={3}")
    @MethodSource("foundationCases")
    void shouldPreserveCurrentGuardSemanticsAfterRealFilterAuthorization(
            Point point, Change change, Set<UserRole> roles, String contextPath, CapturedOutput output) throws Exception {
        var account = fixture.createAccount("t013-" + UUID.randomUUID(), "t013-disposable-password", roles);
        var token = fixture.login(account);
        assertThat(context.getBean(JwtDecoder.class).decode(token).getSubject()).isEqualTo(Long.toString(account.id()));
        assertThat(database.accountIds()).containsExactly(account.id());
        assertThat(database.roles(account.id())).containsExactlyInAnyOrderElementsOf(roles);
        var before = database.snapshot();
        assertThat(before.products()).hasSize(1);
        assertThat(before.categories()).hasSize(1);
        assertThat(before.associations()).hasSize(1);
        state.arm(account.id(), point, change);
        lookupObservation = invocation -> {
            if (invocation.getMethod().getName().equals("findByIdWithRoles")
                    && account.id() == (Long) invocation.getArguments()[0]) {
                state.lookupCalls++;
                state.lookupTransactions.add(TransactionSynchronizationManager.isActualTransactionActive());
                if (state.changeApplied && state.change == Change.LOOKUP_FAILURE) {
                    throw new DataAccessResourceFailureException(MARKER);
                }
            }
            return invocation.proceed();
        };
        ((Advised) accounts).addAdvice(0, lookupObservation);
        sql.clear();
        var logicalPath = ROOT + point.path;
        var externalPath = contextPath + logicalPath;
        var body = "{\"marker\":\"" + MARKER + "\"}";
        int outputStart = output.getAll().length();

        var result = mvc.perform(post(externalPath).contextPath(contextPath).servletPath(logicalPath)
                .queryParam("private", MARKER).contentType(MediaType.APPLICATION_JSON)
                .content(body).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();

        var normalLogs = output.getAll().substring(outputStart);
        var response = result.getResponse();
        var after = database.snapshot();
        var actualJson = mapper.readTree(response.getContentAsByteArray());
        var expectedJson = mapper.createObjectNode();
        if (change == Change.NONE) {
            expectedJson.put("probeAccepted", true);
        } else {
            expectedJson.put("status", change.status);
            expectedJson.put("error", HttpStatus.valueOf(change.status).getReasonPhrase());
            expectedJson.put("message", change.message);
            expectedJson.put("path", externalPath);
            expectedJson.putArray("errors");
        }
        boolean diagnosticRejection = point == Point.BEFORE_DIAGNOSIS && change != Change.NONE;
        int expectedLookups = diagnosticRejection ? 2 : 3;
        var resolved = result.getResolvedException();
        boolean wrapper = resolved instanceof ValidationException;
        System.out.printf("T013_PROOF point=%s change=%s profiles=%s context=%s expected=%d actual=%d "
                        + "lookups=%d diagnostic=%d service=%d restrictedReads=%d wrapper=%s cause=%s snapshotEqual=%s%n",
                point, change, roles, contextPath.isEmpty() ? "root" : "api", change.status, response.getStatus(),
                state.lookupCalls, state.diagnosticCalls, state.serviceCalls, state.restrictedReads, wrapper,
                state.guardFailure == null ? "NONE" : state.guardFailure.getClass().getSimpleName(), before.equals(after));
        assertAll("foundation " + point + " " + change + " " + roles + " " + contextPath,
                () -> assertThat(response.getStatus()).as("contractual status after current guard").isEqualTo(change.status),
                () -> assertThat(actualJson).as("exact safe administrative envelope or local control").isEqualTo(expectedJson),
                () -> assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE),
                () -> assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                        .isEqualTo(change.status == 401 ? "Bearer" : null),
                () -> assertThat(result.getHandler()).as("real filter authorized a registered local MVC probe").isNotNull(),
                () -> assertThat(state.interceptorCalls).isEqualTo(1),
                () -> assertThat(state.filterPrincipal).isEqualTo(new AuthenticatedAccountPrincipal(account.id())),
                () -> assertThat(state.filterAuthorities).containsExactlyInAnyOrderElementsOf(roles.stream()
                        .map(role -> "ROLE_" + role.name()).collect(Collectors.toSet())),
                () -> assertThat(state.changeApplied).isTrue(),
                () -> assertThat(state.diagnosticCalls).isEqualTo(1),
                () -> assertThat(state.diagnosticMarker).isEqualTo(MARKER),
                () -> assertThat(state.diagnosticPassed).isEqualTo(diagnosticRejection ? 0 : 1),
                () -> assertThat(state.controllerCalls).isEqualTo(diagnosticRejection ? 0 : 1),
                () -> assertThat(state.serviceCalls).isEqualTo(diagnosticRejection ? 0 : 1),
                () -> assertThat(state.restrictedReads).as("restricted JDBC read occurs only after all guards pass")
                        .isEqualTo(change == Change.NONE ? 1 : 0),
                () -> assertThat(state.lookupCalls).isEqualTo(expectedLookups),
                () -> assertThat(state.lookupTransactions).hasSize(expectedLookups).containsOnly(true),
                () -> assertThat(state.serviceTransaction).isEqualTo(!diagnosticRejection),
                () -> assertThat(wrapper).as("real Hibernate Validator runtime exception wrapper").isEqualTo(diagnosticRejection),
                () -> assertGuardCause(change, resolved),
                () -> assertThat(after).as("independent five-table snapshot before cleanup").isEqualTo(before),
                () -> assertAccountMutation(change, account.id(), roles),
                () -> assertThat(sql.events()).isNotEmpty().noneMatch(event -> isDml(event.sql())),
                () -> assertThat((response.getContentAsString() + normalLogs).contains(token)).as("token confidentiality").isFalse(),
                () -> assertThat((response.getContentAsString() + normalLogs).contains(account.password()))
                        .as("password confidentiality").isFalse(),
                () -> assertThat(response.getContentAsString() + normalLogs).doesNotContain(MARKER),
                AdminFoundationIntegrationProbeTest::assertNoCallerTransaction);
    }

    static Stream<Arguments> foundationCases() {
        return Stream.of(Point.values()).flatMap(point -> Stream.of(Change.values())
                .flatMap(change -> Stream.of(Set.of(UserRole.ADMIN), Set.of(UserRole.ADMIN, UserRole.CLIENT))
                        .flatMap(roles -> Stream.of("", "/api")
                                .map(context -> Arguments.of(point, change, roles, context)))));
    }

    private void assertGuardCause(Change change, Exception resolved) {
        if (change == Change.NONE) {
            assertThat(resolved).isNull();
            assertThat(state.guardFailure).isNull();
            return;
        }
        Class<? extends RuntimeException> type = switch (change) {
            case REMOVE_ACCOUNT -> BadCredentialsException.class;
            case REVOKE_ADMIN -> AccessDeniedException.class;
            case LOOKUP_FAILURE -> OrderAccountResolutionException.class;
            default -> throw new IllegalArgumentException("Unexpected probe change");
        };
        assertThat(state.guardFailure).isInstanceOf(type);
        Throwable current = resolved;
        while (current != null && !type.isInstance(current)) current = current.getCause();
        assertThat(current).as("known guard failure remains in the exception chain").isSameAs(state.guardFailure);
    }

    private void assertAccountMutation(Change change, long id, Set<UserRole> original) {
        if (change == Change.REMOVE_ACCOUNT) {
            assertThat(database.accountIds()).isEmpty();
            assertThat(database.roles(id)).isEmpty();
        } else {
            assertThat(database.accountIds()).containsExactly(id);
            var expectedRoles = original.stream().filter(role -> change != Change.REVOKE_ADMIN || role != UserRole.ADMIN)
                    .collect(Collectors.toSet());
            assertThat(database.roles(id)).containsExactlyInAnyOrderElementsOf(expectedRoles);
        }
    }

    private void assertEmptyDatabase() {
        var snapshot = database.snapshot();
        assertThat(snapshot.products()).isEmpty();
        assertThat(snapshot.categories()).isEmpty();
        assertThat(snapshot.associations()).isEmpty();
        assertThat(snapshot.orders()).isEmpty();
        assertThat(snapshot.items()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }

    private static boolean isDml(String sql) {
        var normalized = sql.stripLeading().toLowerCase(Locale.ROOT);
        return Stream.of("insert ", "update ", "delete ", "merge ").anyMatch(normalized::startsWith);
    }

    private static void assertNoCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    enum Point {
        BEFORE_DIAGNOSIS("diagnostic"), AFTER_BOUNDARY("service");
        final String path;
        Point(String path) { this.path = path; }
    }

    enum Change {
        NONE(200, ""), REVOKE_ADMIN(403, "Product maintenance not permitted"),
        REMOVE_ACCOUNT(401, "Authentication required or invalid"),
        LOOKUP_FAILURE(500, "Product maintenance could not be completed");
        final int status;
        final String message;
        Change(int status, String message) { this.status = status; this.message = message; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig {
        @Bean ProbeState foundationProbeState(JdbcTemplate jdbc, PlatformTransactionManager manager) {
            return new ProbeState(jdbc, manager);
        }
        @Bean ProbeService foundationProbeService(AdminAccessService guard, ProbeState state, JdbcTemplate jdbc) {
            return new ProbeService(guard, state, jdbc);
        }
        @Bean ProbeController foundationProbeController(ProbeService service, ProbeState state) {
            return new ProbeController(service, state);
        }
        @Bean AdminProductDatabaseProbe.SqlObservation foundationSqlObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }
        @Bean HibernatePropertiesCustomizer foundationSqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
        @Bean WebMvcConfigurer foundationInterceptor(ProbeState state) {
            return new WebMvcConfigurer() {
                @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(state); }
            };
        }
    }

    static class ProbeState implements HandlerInterceptor {
        private final JdbcTemplate jdbc;
        private final PlatformTransactionManager manager;
        long account;
        Point point;
        Change change;
        boolean changeApplied;
        int interceptorCalls, lookupCalls, diagnosticCalls, diagnosticPassed, controllerCalls, serviceCalls, restrictedReads;
        boolean serviceTransaction;
        AuthenticatedAccountPrincipal filterPrincipal;
        Set<String> filterAuthorities;
        String diagnosticMarker;
        RuntimeException guardFailure;
        final List<Boolean> lookupTransactions = new ArrayList<>();

        ProbeState(JdbcTemplate jdbc, PlatformTransactionManager manager) { this.jdbc = jdbc; this.manager = manager; }

        void reset() {
            account = 0;
            point = null;
            change = null;
            changeApplied = serviceTransaction = false;
            interceptorCalls = lookupCalls = diagnosticCalls = diagnosticPassed = controllerCalls = serviceCalls = restrictedReads = 0;
            filterPrincipal = null;
            filterAuthorities = Set.of();
            diagnosticMarker = null;
            guardFailure = null;
            lookupTransactions.clear();
        }

        void arm(long account, Point point, Change change) { this.account = account; this.point = point; this.change = change; }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            var path = request.getRequestURI().substring(request.getContextPath().length());
            if (!path.startsWith(ROOT)) return true;
            interceptorCalls++;
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            filterPrincipal = (AuthenticatedAccountPrincipal) authentication.getPrincipal();
            filterAuthorities = authentication.getAuthorities().stream().map(authority -> authority.getAuthority())
                    .collect(Collectors.toSet());
            if (point == Point.BEFORE_DIAGNOSIS) applyChange();
            return true;
        }

        void applyChange() {
            assertNoCallerTransaction();
            if (change == Change.REVOKE_ADMIN || change == Change.REMOVE_ACCOUNT) {
                var transaction = new TransactionTemplate(manager);
                transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transaction.executeWithoutResult(status -> {
                    if (change == Change.REVOKE_ADMIN) {
                        assertThat(jdbc.update("delete from tb_user_account_role where user_account_id = ? and role = ?",
                                account, UserRole.ADMIN.name())).isEqualTo(1);
                    } else {
                        jdbc.update("delete from tb_user_account_role where user_account_id = ?", account);
                        assertThat(jdbc.update("delete from tb_user_account where id = ?", account)).isEqualTo(1);
                    }
                });
            }
            changeApplied = true;
            assertNoCallerTransaction();
        }
    }

    // The inactive profile excludes component scanning; only the explicit @Bean above creates this probe.
    @RestController
    @Profile("t013-probe-no-component-scan")
    static class ProbeController {
        private final ProbeService service;
        private final ProbeState state;
        ProbeController(ProbeService service, ProbeState state) { this.service = service; this.state = state; }

        @PostMapping({ROOT + "diagnostic", ROOT + "service"})
        public Map<String, Boolean> probe(@Valid @RequestBody ProbeRequest request) {
            state.controllerCalls++;
            if (state.point == Point.AFTER_BOUNDARY) state.applyChange();
            return service.read(principal());
        }
    }

    public static class ProbeService {
        private final AdminAccessService guard;
        private final ProbeState state;
        private final JdbcTemplate jdbc;
        ProbeService(AdminAccessService guard, ProbeState state, JdbcTemplate jdbc) {
            this.guard = guard; this.state = state; this.jdbc = jdbc;
        }

        @Transactional(readOnly = true)
        public Map<String, Boolean> read(AuthenticatedAccountPrincipal principal) {
            state.serviceCalls++;
            state.serviceTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            try { guard.requireAdmin(principal); }
            catch (RuntimeException failure) { state.guardFailure = failure; throw failure; }
            state.restrictedReads++;
            assertThat(jdbc.queryForObject("select count(*) from tb_product", Integer.class)).isEqualTo(1);
            return Map.of("probeAccepted", true);
        }
    }

    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = ProbeValidator.class)
    public @interface GuardedProbe {
        String message() default "Foundation guard rejected";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @GuardedProbe
    public record ProbeRequest(String marker) { }

    public static class ProbeValidator implements ConstraintValidator<GuardedProbe, ProbeRequest> {
        @Autowired private AdminAccessService guard;
        @Autowired private ProbeState state;

        @Override
        public boolean isValid(ProbeRequest value, ConstraintValidatorContext context) {
            state.diagnosticCalls++;
            state.diagnosticMarker = value.marker();
            try { guard.requireAdmin(principal()); }
            catch (RuntimeException failure) { state.guardFailure = failure; throw failure; }
            state.diagnosticPassed++;
            return true;
        }
    }

    private static AuthenticatedAccountPrincipal principal() {
        return (AuthenticatedAccountPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
