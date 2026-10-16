package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.services.AdminAccessService;
import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
import com.devsuperior.dscommerce.services.ProductInputValidationService;
import com.devsuperior.dscommerce.services.ProductMaintenanceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ValidationException;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/** Real servlet OSIV and JDBC. EARLY_FILTER supplies a test-only shared view before JWT resolution. */
@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.open-in-view=true", "server.address=127.0.0.1", "server.servlet.context-path=/api"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductOsivDiagnosticGuardIntegrationTest.ObservationConfig.class)
class AdminProductOsivDiagnosticGuardIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private Probe probe;
    @Autowired private JdbcTemplate jdbc;
    @LocalServerPort private int port;
    private AdminProductTestFixture fixture;
    private AdminProductDatabaseProbe database;
    private HttpClient client;
    private ExecutorService caller;
    private Future<HttpResponse<String>> pending;
    private Run run;
    private boolean renamed;
    private long account, product, category;
    private String token;

    enum View { MVC_INTERCEPTOR, EARLY_FILTER }
    enum Change { NONE, REVOKE_ADMIN, REMOVE_ACCOUNT, LOOKUP_FAILURE }

    @BeforeEach
    void committedFixtureAndRealLogin() {
        outsideTransaction();
        assertThat(context.getEnvironment().getProperty("spring.jpa.open-in-view")).isEqualTo("true");
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertThat(database.snapshot().products()).isEmpty(); assertThat(database.accountIds()).isEmpty();
        fixture = new AdminProductTestFixture(context);
        var user = fixture.createAccount("OSIV owned admin", "Disposable-OSIV-password", Set.of(UserRole.ADMIN, UserRole.CLIENT));
        account = user.id(); token = fixture.login(user);
        category = fixture.createCategory("OSIV owned category").id();
        product = fixture.createProduct("OSIV owned product", "OSIV original description", "https://example.invalid/osiv.jpg", new BigDecimal("10.00")).id();
        fixture.associate(product, category);
        assertThat(database.roles(account)).containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.CLIENT);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        caller = Executors.newSingleThreadExecutor();
    }

    static Stream<Arguments> sharedViewCases() {
        return Stream.of(View.values()).flatMap(view -> Stream.of("POST", "PUT")
                .flatMap(method -> Stream.of(Change.values()).map(change -> Arguments.of(view, method, change))));
    }

    @ParameterizedTest(name = "OSIV={0}, method={1}, change={2}")
    @MethodSource("sharedViewCases")
    void currentDiagnosisGuardPrecedesReferenceReadsWithARealViewContext(View view, String method, Change change) throws Exception {
        var before = database.snapshot();
        run = new Run(view, change, account); probe.armed = run;
        String route = "/admin/products" + (method.equals("PUT") ? "/" + product : "");
        var body = mapper.createObjectNode().put("name", "OSIV requested product").put("description", "OSIV requested description")
                .put("image", "https://example.invalid/osiv-new.jpg").put("price", "0.00");
        body.putArray("categoryIds").add(category);
        // Invalid price keeps even the authorized control in diagnosis: no maintenance write is needed to prove this guard.
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + route))
                .timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("X-OSIV-Proof", run.id).header("X-OSIV-View", view.name())
                .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        pending = caller.submit(() -> client.send(request, HttpResponse.BodyHandlers.ofString()));
        HttpResponse<String> response;
        try {
            assertThat(run.reached.await(30, TimeUnit.SECONDS)).as("preparation: boundary reached after real JWT authorization").isTrue();
            assertThat(run.preparationFailure).as("preparation/instrumentation is not functional RED").isNull();
            assertThat(run.filterAuthorized).isTrue(); assertThat(run.securityAuthorized).isTrue();
            assertThat(run.filterEntityManager).isNotNull(); assertThat(run.viewEntityManager).isNotNull();
            assertThat(run.viewEntityManager.isOpen()).isTrue();
            assertThat(run.sameViewAsFilter).isEqualTo(view == View.EARLY_FILTER);
            assertThat(run.cachedAccountInView).isEqualTo(view == View.EARLY_FILTER);
            assertThat(run.events).doesNotContain("DIAG_ACCOUNT_READ", "DIAG_CATEGORY_READ", "MAINTENANCE");
            assertThat(run.dml).isEmpty(); assertThat(database.snapshot()).isEqualTo(before);
            if (change == Change.REVOKE_ADMIN) {
                assertThat(jdbc.update("delete from tb_user_account_role where user_account_id=? and role='ADMIN'", account)).isEqualTo(1);
                assertThat(database.roles(account)).containsExactly(UserRole.CLIENT);
            } else if (change == Change.REMOVE_ACCOUNT) {
                jdbc.update("delete from tb_user_account_role where user_account_id=?", account);
                assertThat(jdbc.update("delete from tb_user_account where id=?", account)).isEqualTo(1);
                assertThat(database.accountIds()).doesNotContain(account);
            } else if (change == Change.LOOKUP_FAILURE) {
                jdbc.execute("alter table tb_user_account rename to osiv_account_unavailable"); renamed = true;
                assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name='TB_USER_ACCOUNT'", Integer.class)).isZero();
            }
            assertThat(database.snapshot()).isEqualTo(before);
            run.resume.countDown(); response = pending.get(40, TimeUnit.SECONDS);
            assertThat(database.snapshot()).as("independent snapshot after HTTP, before restoration/cleanup").isEqualTo(before);
        } finally {
            run.resume.countDown();
        }
        assertThat(run.preparationFailure).isNull(); outsideTransaction();
        int status = switch (change) { case NONE -> 400; case REVOKE_ADMIN -> 403; case REMOVE_ACCOUNT -> 401; case LOOKUP_FAILURE -> 500; };
        assertAll("functional guard after completed preparation and real independent change",
                () -> assertThat(response.statusCode()).isEqualTo(status),
                () -> assertThat(run.categoryCalls).isEqualTo(change == Change.NONE ? 1 : 0),
                () -> assertThat(run.events.stream().filter("DIAG_CATEGORY_READ"::equals).count()).isEqualTo(change == Change.NONE ? 1 : 0),
                () -> assertThat(run.dml).isEmpty(),
                () -> assertThat(run.maintenanceEntries).isZero());
        assertThat(run.guardCalls).isEqualTo(1); assertThat(run.guardUsedView).isTrue();
        assertThat(run.guardTransaction).isTrue(); assertThat(run.guardReadOnly).isTrue();
        assertThat(run.events).contains("DIAG_ACCOUNT_PREPARE");
        if (change == Change.NONE) {
            assertThat(run.events.indexOf("GUARD_RETURN")).isLessThan(run.events.indexOf("DIAG_CATEGORY_READ"));
            assertThat(run.events.indexOf("DIAG_ACCOUNT_READ")).isLessThan(run.events.indexOf("DIAG_CATEGORY_READ"));
        } else {
            assertThat(run.mvcFailure).isInstanceOf(ValidationException.class);
            if (change == Change.LOOKUP_FAILURE) assertThat(run.failedStates).contains("42S02");
        }
        // Fresh scalar authorization must not refresh/clear or rewrite the old initialized role collection.
        assertThat(run.filterAccount.getRoles()).contains(UserRole.ADMIN);
        var error = mapper.readTree(response.body());
        assertThat(error.size()).isEqualTo(5);
        assertThat(error.path("status").intValue()).isEqualTo(status);
        assertThat(error.path("path").asText()).isEqualTo("/api" + route);
        assertThat(error.path("message").asText()).isEqualTo(switch (status) {
            case 400 -> "Invalid product maintenance data"; case 401 -> "Authentication required or invalid";
            case 403 -> "Product maintenance not permitted"; default -> "Product maintenance could not be completed";
        });
        if (change == Change.NONE) {
            assertThat(error.path("errors").size()).isEqualTo(1);
            assertThat(error.path("errors").get(0).path("field").asText()).isEqualTo("price");
            assertThat(error.path("errors").get(0).path("code").asText()).isEqualTo("NOT_POSITIVE");
        } else assertThat(error.path("errors")).isEmpty();
        assertThat(response.headers().firstValue("WWW-Authenticate").orElse(null)).isEqualTo(status == 401 ? "Bearer" : null);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.body().contains(token)).as("credential is not echoed").isFalse();
        System.out.printf("OSIV_DIAG view=%s method=%s change=%s status=%d filterAuthorized=true sharedFilterView=%s cachedAccount=%s guardBeforeReferences=true categoryReads=%d dml=0 maintenance=0 independentSnapshot=true%n",
                view, method, change, response.statusCode(), run.sameViewAsFilter, run.cachedAccountInView, run.categoryCalls);
    }

    @AfterEach
    void releaseRequestRestoreOnlyOwnedFixtureAndClearProbe() throws Exception {
        if (run != null) run.resume.countDown();
        try { if (pending != null) pending.get(40, TimeUnit.SECONDS); }
        finally {
            probe.armed = null;
            if (renamed) { jdbc.execute("alter table osiv_account_unavailable rename to tb_user_account"); renamed = false; }
            if (client != null) client.close();
            if (caller != null) { caller.shutdown(); assertThat(caller.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
            if (fixture != null) fixture.close();
        }
        outsideTransaction(); assertThat(database.snapshot().products()).isEmpty(); assertThat(database.accountIds()).isEmpty();
    }

    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    static final class Run {
        final String id = UUID.randomUUID().toString(); final View view; final Change change; final long account;
        final CountDownLatch reached = new CountDownLatch(1), resume = new CountDownLatch(1);
        final AtomicBoolean gated = new AtomicBoolean();
        final List<String> events = new CopyOnWriteArrayList<>(), dml = new CopyOnWriteArrayList<>(), failedStates = new CopyOnWriteArrayList<>();
        volatile String stage = "FILTER";
        volatile EntityManager filterEntityManager, viewEntityManager;
        volatile UserAccount filterAccount;
        volatile boolean filterAuthorized, securityAuthorized, sameViewAsFilter, cachedAccountInView;
        volatile boolean guardUsedView, guardTransaction, guardReadOnly;
        volatile int guardCalls, categoryCalls, maintenanceEntries;
        volatile Throwable preparationFailure, mvcFailure;
        Run(View view, Change change, long account) { this.view = view; this.change = change; this.account = account; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationConfig {
        @Bean static Probe osivDiagnosisProbe() { return new Probe(); }
        @Bean ErrorObserver osivErrorObserver(Probe probe) { return new ErrorObserver(probe); }

        @Bean WebMvcConfigurer lateMvcViewForThisTest(EntityManagerFactory factory) {
            // A registered view filter makes Boot back off its interceptor, even for requests skipped by that filter.
            // Restore the standard Spring MVC view interceptor explicitly in this test context only.
            var interceptor = new OpenEntityManagerInViewInterceptor();
            interceptor.setEntityManagerFactory(factory);
            return new WebMvcConfigurer() {
                @Override public void addInterceptors(InterceptorRegistry registry) {
                    registry.addWebRequestInterceptor(interceptor);
                }
            };
        }

        @Bean FilterRegistrationBean<OncePerRequestFilter> osivProofScope(Probe probe) {
            var filter = new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain chain)
                        throws jakarta.servlet.ServletException, IOException {
                    var run = probe.armed;
                    if (run == null || !run.id.equals(request.getHeader("X-OSIV-Proof"))) { chain.doFilter(request, response); return; }
                    probe.current.set(run);
                    try { chain.doFilter(request, response); } finally { probe.current.remove(); }
                }
            };
            var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 11); return registration;
        }

        @Bean FilterRegistrationBean<OpenEntityManagerInViewFilter> sharedEarlyOsivOnlyForThisTest() {
            var filter = new OpenEntityManagerInViewFilter() {
                @Override protected boolean shouldNotFilter(HttpServletRequest request) {
                    return !View.EARLY_FILTER.name().equals(request.getHeader("X-OSIV-View"));
                }
            };
            var registration = new FilterRegistrationBean<OpenEntityManagerInViewFilter>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 12); return registration;
        }
    }

    static final class ErrorObserver implements HandlerExceptionResolver, Ordered {
        private final Probe probe;
        ErrorObserver(Probe probe) { this.probe = probe; }
        @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
        @Override public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
            var run = probe.current.get(); if (run != null) run.mvcFailure = exception; return null;
        }
    }

    static final class Probe implements BeanPostProcessor, BeanFactoryAware {
        volatile Run armed;
        final ThreadLocal<Run> current = new ThreadLocal<>();
        private BeanFactory factory;
        @Override public void setBeanFactory(BeanFactory beanFactory) throws BeansException { factory = beanFactory; }
        private EntityManager entityManager() {
            var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(factory.getBean(EntityManagerFactory.class));
            return holder == null ? null : holder.getEntityManager();
        }

        @Override public Object postProcessAfterInitialization(Object bean, String name) {
            if (bean instanceof DataSource source) return Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class}, (p, method, args) -> {
                var result = delegate(source, method, args); return result instanceof Connection connection ? connection(connection) : result;
            });
            if (bean instanceof AuthenticatedAccountService) {
                addAdvice(bean, invocation -> {
                    var result = invocation.proceed(); var run = current.get();
                    if (run != null && run.stage.equals("FILTER") && invocation.getMethod().getName().equals("resolve")) {
                        run.filterEntityManager = entityManager();
                        int sqlEvents = run.events.size();
                        run.filterAccount = run.filterEntityManager.find(UserAccount.class, run.account);
                        if (run.events.size() != sqlEvents) run.preparationFailure = new IllegalStateException("Expected already managed authentication account");
                        run.filterAuthorized = ((AuthenticatedAccountService.ResolvedAccount) result).authorities().stream()
                                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
                    }
                    return result;
                }, false); return bean;
            }
            if (bean instanceof ProductInputValidationService || bean instanceof AdminAccessService || bean instanceof ProductMaintenanceService
                    || bean instanceof com.devsuperior.dscommerce.repositories.CategoryRepository) {
                boolean local = bean instanceof ProductInputValidationService, guard = bean instanceof AdminAccessService;
                boolean maintenance = bean instanceof ProductMaintenanceService;
                MethodInterceptor trace = invocation -> {
                    var run = current.get(); if (run == null) return invocation.proceed();
                    if (local && invocation.getMethod().getName().equals("validate") && run.gated.compareAndSet(false, true)) {
                        run.stage = "DIAG"; run.viewEntityManager = entityManager();
                        run.sameViewAsFilter = run.viewEntityManager == run.filterEntityManager;
                        run.cachedAccountInView = run.viewEntityManager != null && run.viewEntityManager.contains(run.filterAccount);
                        var authentication = SecurityContextHolder.getContext().getAuthentication();
                        run.securityAuthorized = authentication != null && authentication.getAuthorities().stream()
                                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
                        run.reached.countDown();
                        if (!run.resume.await(30, TimeUnit.SECONDS)) {
                            run.preparationFailure = new IllegalStateException("OSIV observation barrier timed out"); throw run.preparationFailure;
                        }
                    }
                    if (guard && run.stage.equals("DIAG") && invocation.getMethod().getName().equals("requireAdmin")) {
                        run.guardCalls++; run.guardUsedView = entityManager() == run.viewEntityManager;
                        run.guardTransaction = TransactionSynchronizationManager.isActualTransactionActive();
                        run.guardReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                        var result = invocation.proceed(); run.events.add("GUARD_RETURN"); return result;
                    }
                    if (maintenance) { run.maintenanceEntries++; run.events.add("MAINTENANCE"); }
                    if (!local && !guard && !maintenance && invocation.getMethod().getName().equals("findAllByIdIn")) run.categoryCalls++;
                    return invocation.proceed();
                };
                if (bean instanceof Advised advised) { advised.addAdvice(0, trace); return bean; }
                var proxy = new ProxyFactory(bean); proxy.setProxyTargetClass(true); proxy.addAdvice(trace); return proxy.getProxy();
            }
            return bean;
        }

        private static void addAdvice(Object bean, MethodInterceptor advice, boolean outside) {
            if (!(bean instanceof Advised advised)) throw new IllegalStateException("OSIV proof requires original transactional proxy");
            if (outside) advised.addAdvice(0, advice); else advised.addAdvice(advice);
        }
        private Connection connection(Connection target) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (p, method, args) -> {
                var run = current.get();
                boolean preparing = run != null && method.getName().startsWith("prepare")
                        && args != null && args.length > 0 && args[0] instanceof String;
                if (preparing && ((String) args[0]).toLowerCase(java.util.Locale.ROOT).contains("tb_user_account")) {
                    run.events.add(run.stage + "_ACCOUNT_PREPARE");
                }
                Object result;
                try { result = delegate(target, method, args); }
                catch (SQLException failure) { if (preparing) run.failedStates.add(failure.getSQLState()); throw failure; }
                if (result instanceof Statement statement) {
                    String prepared = args != null && args.length > 0 && args[0] instanceof String sql ? sql : null;
                    return statement(statement, prepared);
                }
                return result;
            });
        }
        private Statement statement(Statement target, String prepared) {
            var type = target instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            return (Statement) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) -> {
                var run = current.get();
                String raw = prepared != null ? prepared : args != null && args.length > 0 && args[0] instanceof String sql ? sql : "";
                String sql = raw.replaceAll("(?s)/\\*.*?\\*/", " ").stripLeading().toLowerCase(java.util.Locale.ROOT);
                boolean execute = run != null && method.getName().startsWith("execute");
                if (execute && sql.contains("tb_user_account")) run.events.add(run.stage + "_ACCOUNT_ATTEMPT");
                Object result;
                try { result = delegate(target, method, args); }
                catch (SQLException failure) { if (execute) run.failedStates.add(failure.getSQLState()); throw failure; }
                if (execute) {
                    if (Stream.of("insert ", "update ", "delete ", "merge ").anyMatch(sql::startsWith)) run.dml.add(sql);
                    if (sql.contains("tb_user_account")) run.events.add(run.stage + "_ACCOUNT_READ");
                    if (sql.contains("tb_category")) run.events.add(run.stage + "_CATEGORY_READ");
                }
                return result;
            });
        }
        private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); } catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
    }
}
