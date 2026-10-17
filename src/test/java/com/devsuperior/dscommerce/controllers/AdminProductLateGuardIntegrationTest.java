package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductCategory;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.repositories.CategoryRepository;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.AdminAccessService;
import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
import com.devsuperior.dscommerce.services.OrderService;
import com.devsuperior.dscommerce.services.ProductCategoryDiagnosticService;
import com.devsuperior.dscommerce.services.ProductMaintenanceService;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ValidationException;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** T067: real guards/validator wrappers and H2 SQL failure; barriers never substitute business outcomes. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductLateGuardIntegrationTest.ProbeConfig.class)
class AdminProductLateGuardIntegrationTest {
    private static final int WAIT_SECONDS = 30;
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private OrderService orders;
    @Autowired private ProductMaintenanceService maintenance;
    @Autowired private Probe probe;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private ProductData target, other;
    private long categoryA, categoryB;
    private final Set<Long> ownedOrders = new HashSet<>(), createdProducts = new HashSet<>();
    private ExecutorService worker;
    private Future<MvcResult> pending;
    private Run run;
    private boolean renamed;

    @BeforeEach
    void committedOwnedDataAndIndependentHistory() {
        outsideTransaction();
        assertThat(AopUtils.isAopProxy(maintenance)).isTrue(); assertThat(AopUtils.isAopProxy(orders)).isTrue();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmpty(); fixture = new AdminProductTestFixture(context);
        ownedOrders.clear(); createdProducts.clear(); renamed = false;
        categoryA = fixture.createCategory("T067 same category").id();
        categoryB = fixture.createCategory("T067 same category").id();
        fixture.createCategory("T067 unused category");
        target = fixture.createProduct("T067 target", "T067 target description", "https://example.invalid/t067.jpg", new BigDecimal("10.00"));
        other = fixture.createProduct("T067 other", "T067 other description", "https://example.invalid/t067-other.jpg", new BigDecimal("3.25"));
        fixture.associate(target.id(), categoryA); fixture.associate(target.id(), categoryB); fixture.associate(other.id(), categoryA);
        var buyer = fixture.prepareBuyer();
        try {
            assertThat(orders.register(new OrderRequestDTO(List.of(new OrderItemRequestDTO(other.id(), 3))),
                    new AuthenticatedAccountPrincipal(buyer.id())).total()).isEqualTo("9.75");
        } finally { database.snapshot().orders().forEach(order -> ownedOrders.add(order.id())); }
        var prepared = database.snapshot();
        assertThat(prepared.products()).containsExactly(row(target), row(other));
        assertThat(prepared.categories()).hasSize(3);
        assertThat(prepared.associations()).containsExactlyInAnyOrder(new ProductCategory(target.id(), categoryA),
                new ProductCategory(target.id(), categoryB), new ProductCategory(other.id(), categoryA));
        assertThat(prepared.orders()).hasSize(1); assertThat(prepared.items()).hasSize(1);
        var order = prepared.orders().getFirst(); var item = prepared.items().getFirst();
        assertThat(order.buyerId()).isEqualTo(buyer.id()); assertThat(order.createdAt()).isNotNull();
        assertThat(order.status()).isEqualTo("WAITING_PAYMENT");
        assertThat(item.orderId()).isEqualTo(order.id()); assertThat(item.productId()).isEqualTo(other.id());
        assertThat(item.position()).isZero(); assertThat(item.quantity()).isEqualTo(3);
        assertThat(item.unitPrice()).isEqualTo(new BigDecimal("3.25"));
        assertThat(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))).isEqualTo(new BigDecimal("9.75"));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        worker = Executors.newSingleThreadExecutor();
    }
    private MockMvc mvc;

    static Stream<Arguments> lateCases() {
        return Stream.of(Point.DIAGNOSIS, Point.MAINTENANCE).flatMap(point -> Stream.of("POST", "PUT")
                .flatMap(method -> Stream.of(false, true).flatMap(both -> Stream.of("", "/api")
                        .flatMap(prefix -> Stream.of(Change.values()).flatMap(change ->
                                (point == Point.DIAGNOSIS ? Stream.of(false, true) : Stream.of(false))
                                        .map(invalid -> Arguments.of(point, method, both, prefix, change, invalid)))))));
    }

    @ParameterizedTest(name = "point={0} method={1} both={2} context={3} change={4} invalid={5}")
    @MethodSource("lateCases")
    void currentGuardsPrecedeRestrictedLookupsAndKeepRealValidationCauses(Point point, String method, boolean both,
                                                                          String prefix, Change change, boolean invalid) throws Exception {
        scenario(point, method, both, prefix, change, invalid);
    }

    static Stream<Arguments> filterLookupCases() {
        return Stream.of(false, true).flatMap(both -> Stream.of("", "/api").map(prefix -> Arguments.of(both, prefix)));
    }

    @ParameterizedTest(name = "real filter lookup failure both={0} context={1}")
    @MethodSource("filterLookupCases")
    void realSqlLookupFailureAtTheFilterIsSafe500(boolean both, String prefix) throws Exception {
        scenario(Point.FILTER, "POST", both, prefix, Change.LOOKUP_FAILURE, false);
    }

    private void scenario(Point point, String method, boolean both, String prefix, Change change, boolean invalid) throws Exception {
        Set<UserRole> roles = both ? Set.of(UserRole.ADMIN, UserRole.CLIENT) : Set.of(UserRole.ADMIN);
        var admin = fixture.createAccount("T067 request account", "Disposable-T067-password", roles);
        assertThat(database.roles(admin.id())).containsExactlyInAnyOrderElementsOf(roles);
        String token = fixture.login(admin);
        var before = database.snapshot(); String expectedAccounts = accountFingerprint();
        run = new Run(point, admin.id());
        var body = mapper.createObjectNode().put("name", invalid ? "ab" : "T067 replacement")
                .put("description", invalid ? "123456789" : "T067 replacement description")
                .put("price", invalid ? "0" : "12.00")
                .put("image", invalid ? "/relative" : "https://example.invalid/t067-new.jpg");
        body.putArray("categoryIds").add(invalid ? Long.MAX_VALUE : categoryB);
        String route = method.equals("POST") ? "/admin/products" : "/admin/products/" + target.id();
        byte[] bytes = mapper.writeValueAsBytes(body);
        pending = worker.submit(() -> {
            outsideTransaction(); probe.current.set(run);
            try {
                var result = mvc.perform(request(HttpMethod.valueOf(method), prefix + route).contextPath(prefix).servletPath(route)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).content(bytes)).andReturn();
                outsideTransaction(); return result;
            } finally { probe.current.remove(); }
        });
        await(run.reached, "chosen production boundary reached");
        assertThat(run.barriers).isEqualTo(1); assertThat(pending.isDone()).isFalse();
        assertThat(run.filterResolutions).isEqualTo(point == Point.FILTER ? 0 : 1);
        assertThat(run.initialAdmin).isEqualTo(point != Point.FILTER);
        assertThat(run.resources).noneMatch(call -> call.kind().equals("PRODUCT"));
        assertThat(run.sql).noneMatch(SqlEvent::dml);
        if (point == Point.MAINTENANCE) {
            assertThat(run.validBoundaryReturns).isEqualTo(1);
            assertThat(run.guards).hasSize(1).allMatch(guard -> guard.stage().equals("DIAGNOSIS") && guard.readOnly());
        } else assertThat(run.guards).isEmpty();
        assertThat(database.snapshot()).isEqualTo(before);

        if (change == Change.REVOKE_ADMIN) {
            assertThat(execute("delete from tb_user_account_role where user_account_id=? and role='ADMIN'", admin.id())).isEqualTo(1);
            assertThat(database.roles(admin.id())).containsExactlyInAnyOrderElementsOf(both ? Set.of(UserRole.CLIENT) : Set.of());
        } else if (change == Change.REMOVE_ACCOUNT) {
            try (var c = database.openConnection()) {
                c.setAutoCommit(false);
                try (var deleteRoles = c.prepareStatement("delete from tb_user_account_role where user_account_id=?");
                     var deleteAccount = c.prepareStatement("delete from tb_user_account where id=?")) {
                    deleteRoles.setLong(1, admin.id()); assertThat(deleteRoles.executeUpdate()).isEqualTo(roles.size());
                    deleteAccount.setLong(1, admin.id()); assertThat(deleteAccount.executeUpdate()).isEqualTo(1); c.commit();
                } catch (Exception failure) { c.rollback(); throw failure; }
            }
            assertThat(database.accountIds()).doesNotContain(admin.id());
        } else if (change == Change.LOOKUP_FAILURE) {
            // H2 DDL is committed independently. Data remains in the renamed table; the actual ORM SELECT must fail.
            execute("alter table tb_user_account rename to t067_account_unavailable"); renamed = true;
        }
        if (!renamed) expectedAccounts = accountFingerprint();
        assertThat(database.snapshot()).isEqualTo(before);
        run.resume.countDown();
        var result = pending.get(WAIT_SECONDS, TimeUnit.SECONDS);
        outsideTransaction();
        // Check catalog/history while lookup is still unavailable, before restoring this test-only DDL.
        if (change != Change.NONE || invalid) assertThat(database.snapshot()).isEqualTo(before);
        if (change == Change.LOOKUP_FAILURE) {
            assertThat(run.failedSql).hasSize(1);
            var failure = run.failedSql.getFirst();
            assertThat(failure.getSQLState()).isEqualTo("42S02"); assertThat(failure.getErrorCode()).isEqualTo(42102);
            assertThat(run.sql).anyMatch(event -> !event.success() && event.sql().contains("tb_user_account"));
            assertThat(run.resolutionFailure).isInstanceOf(OrderAccountResolutionException.class);
            assertChainContains(run.resolutionFailure, failure);
            restoreTable();
        }
        assertThat(accountFingerprint()).isEqualTo(expectedAccounts);
        var after = database.snapshot();
        after.products().stream().filter(product -> before.products().stream().noneMatch(prior -> prior.id() == product.id()))
                .forEach(product -> createdProducts.add(product.id()));
        if (change == Change.NONE && !invalid) {
            assertSuccess(result, method, prefix, before, after);
        } else {
            int expectedStatus = change == Change.NONE ? 400 : change.status;
            assertError(result, expectedStatus, prefix + route, token);
            assertThat(after).isEqualTo(before); assertThat(run.sql).noneMatch(SqlEvent::dml);
            assertThat(run.resources).noneMatch(call -> call.kind().equals("PRODUCT"));
            assertThat(run.resources).noneMatch(call -> call.stage().equals("MAINTENANCE"));
            if (change != Change.NONE) {
                assertThat(run.resources).noneMatch(call -> call.stage().equals("DIAGNOSIS") && point != Point.MAINTENANCE);
                var expectedFailure = change == Change.REVOKE_ADMIN ? AccessDeniedException.class
                        : change == Change.REMOVE_ACCOUNT ? BadCredentialsException.class : OrderAccountResolutionException.class;
                if (point == Point.FILTER) {
                    assertThat(result.getHandler()).isNull(); assertThat(result.getResolvedException()).isNull();
                    assertThat(run.guards).isEmpty(); assertThat(run.maintenanceEntries).isZero();
                } else {
                    assertThat(run.guardFailure).isInstanceOf(expectedFailure);
                    assertChainContains(result.getResolvedException(), run.guardFailure);
                    assertThat(result.getResolvedException() instanceof ValidationException).isEqualTo(point == Point.DIAGNOSIS);
                    assertThat(run.guards).hasSize(point == Point.DIAGNOSIS ? 1 : 2);
                    assertThat(run.guards.getLast().readOnly()).isEqualTo(point == Point.DIAGNOSIS);
                    assertThat(run.maintenanceEntries).isEqualTo(point == Point.DIAGNOSIS ? 0 : 1);
                }
            } else {
                assertThat(run.maintenanceEntries).isZero();
                var errors = mapper.readTree(result.getResponse().getContentAsByteArray()).path("errors");
                assertThat(errors.findValues("field").stream().map(node -> node.asText()).toList())
                        .containsExactly("name", "description", "price", "image", "categoryIds[0]");
            }
        }
        if (point != Point.FILTER) assertThat(run.filterResolutions).isEqualTo(1);
        System.out.printf("T067_PROOF point=%s method=%s both=%s context=%s change=%s invalid=%s status=%d barrier=1 filterResolved=%d guards=%d wrapped=%s productCalls=%d jdbcDml=%d realSqlFailures=%d independentState=true history=9.75%n",
                point, method, both, prefix.isEmpty() ? "ROOT" : prefix, change, invalid, result.getResponse().getStatus(),
                run.filterResolutions, run.guards.size(), result.getResolvedException() instanceof ValidationException,
                run.resources.stream().filter(call -> call.kind().equals("PRODUCT")).count(), run.sql.stream().filter(SqlEvent::dml).count(), run.failedSql.size());
    }

    private void assertSuccess(MvcResult result, String method, String prefix, Snapshot before, Snapshot after) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(method.equals("POST") ? 201 : 200);
        assertThat(result.getResolvedException()).isNull();
        var body = mapper.readTree(result.getResponse().getContentAsByteArray()); long id = body.path("id").longValue();
        if (method.equals("POST")) {
            assertThat(id).isPositive().isNotIn(target.id(), other.id());
            var location = java.net.URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION));
            assertThat(location.getPath()).isEqualTo(prefix + "/admin/products/" + id);
        } else { assertThat(id).isEqualTo(target.id()); assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull(); }
        assertThat(body.path("price").asText()).isEqualTo("12.00");
        var products = new ArrayList<>(before.products());
        if (method.equals("PUT")) products.remove(row(target));
        products.add(new ProductRow(id, "T067 replacement", "T067 replacement description", "https://example.invalid/t067-new.jpg", new BigDecimal("12.00")));
        products.sort(java.util.Comparator.comparingLong(ProductRow::id));
        var pairs = new HashSet<>(before.associations());
        if (method.equals("PUT")) pairs.removeIf(pair -> pair.productId() == target.id());
        pairs.add(new ProductCategory(id, categoryB));
        assertThat(after).isEqualTo(new Snapshot(products, before.categories(), pairs, before.orders(), before.items()));
        assertThat(run.maintenanceEntries).isEqualTo(1); assertThat(run.validBoundaryReturns).isEqualTo(1);
        assertThat(run.sql).anyMatch(SqlEvent::dml);
    }
    private void assertError(MvcResult result, int status, String route, String token) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        var body = mapper.readTree(result.getResponse().getContentAsByteArray());
        var fields = new ArrayList<String>(); body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("status", "error", "message", "path", "errors");
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("error").asText()).isEqualTo(HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(body.path("message").asText()).isEqualTo(status == 401 ? "Authentication required or invalid"
                : status == 403 ? "Product maintenance not permitted" : status == 400 ? "Invalid product maintenance data" : "Product maintenance could not be completed");
        assertThat(body.path("path").asText()).isEqualTo(route); assertThat(body.path("errors").isArray()).isTrue();
        if (status != 400) assertThat(body.path("errors")).isEmpty();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(status == 401 ? "Bearer" : null);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(token, "Disposable-T067", "tb_user_account", "42102", "SQLException");
    }
    private static void assertChainContains(Throwable outer, Throwable expected) {
        var visited = new HashSet<Throwable>();
        while (outer != null && outer != expected && visited.add(outer)) outer = outer.getCause();
        assertThat(outer).as("original runtime/JDBC cause preserved").isSameAs(expected);
    }
    private int execute(String sql, Object... values) throws Exception {
        try (var c = database.openConnection(); var s = c.prepareStatement(sql)) {
            s.setQueryTimeout(5); for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]); return s.executeUpdate();
        }
    }
    private void restoreTable() throws Exception {
        if (renamed) { execute("alter table t067_account_unavailable rename to tb_user_account"); renamed = false; }
    }
    private String accountFingerprint() throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var c = database.openConnection(); var s = c.createStatement()) {
            for (String query : List.of("select id,name,password_hash from tb_user_account order by id",
                    "select user_account_id,role from tb_user_account_role order by user_account_id,role")) {
                try (var rows = s.executeQuery(query)) {
                    while (rows.next()) for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                        byte[] value = rows.getString(i).getBytes(StandardCharsets.UTF_8);
                        digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array()); digest.update(value);
                    }
                }
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    @AfterEach
    void releaseJoinRestoreThenCleanOnlyOwnedData() throws Exception {
        if (run != null) run.resume.countDown();
        if (worker != null) {
            worker.shutdown(); assertThat(worker.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
        outsideTransaction(); if (fixture == null) return; restoreTable();
        // Retain ownership even when an assertion failed before response parsing.
        if (target != null && other != null) database.snapshot().products().stream()
                .filter(product -> product.id() != target.id() && product.id() != other.id()).forEach(product -> createdProducts.add(product.id()));
        try (var c = database.openConnection()) {
            c.setAutoCommit(false);
            try {
                for (long id : ownedOrders) {
                    try (var item = c.prepareStatement("delete from tb_order_item where order_id=?"); var order = c.prepareStatement("delete from tb_order where id=?")) {
                        item.setLong(1, id); item.executeUpdate(); order.setLong(1, id); order.executeUpdate();
                    }
                }
                for (long id : createdProducts) {
                    try (var pair = c.prepareStatement("delete from tb_product_category where product_id=?"); var product = c.prepareStatement("delete from tb_product where id=?")) {
                        pair.setLong(1, id); pair.executeUpdate(); product.setLong(1, id); product.executeUpdate();
                    }
                }
                c.commit();
            } catch (Exception failure) { c.rollback(); throw failure; }
        }
        fixture.close(); assertEmpty();
    }
    private static ProductRow row(ProductData p) { return new ProductRow(p.id(), p.name(), p.description(), p.image(), p.price()); }
    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private void assertEmpty() {
        var s = database.snapshot(); assertThat(s.products()).isEmpty(); assertThat(s.categories()).isEmpty();
        assertThat(s.associations()).isEmpty(); assertThat(s.orders()).isEmpty(); assertThat(s.items()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }
    private static void await(CountDownLatch latch, String label) throws InterruptedException {
        assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)).as(label).isTrue();
    }
    enum Point { FILTER, DIAGNOSIS, MAINTENANCE }
    enum Change {
        NONE(200), REVOKE_ADMIN(403), REMOVE_ACCOUNT(401), LOOKUP_FAILURE(500);
        final int status; Change(int status) { this.status = status; }
    }
    private record ResourceCall(String stage, String kind) { }
    private record Guard(String stage, boolean readOnly) { }
    private record SqlEvent(String stage, String sql, boolean success) {
        boolean dml() { return Stream.of("insert ", "update ", "delete ", "merge ").anyMatch(sql::startsWith); }
    }
    private static final class Run {
        final Point point; final long account;
        final CountDownLatch reached = new CountDownLatch(1), resume = new CountDownLatch(1);
        final AtomicBoolean gated = new AtomicBoolean();
        final List<ResourceCall> resources = new CopyOnWriteArrayList<>();
        final List<Guard> guards = new CopyOnWriteArrayList<>();
        final List<SqlEvent> sql = new CopyOnWriteArrayList<>();
        final List<SQLException> failedSql = new CopyOnWriteArrayList<>();
        volatile String stage = "FILTER";
        volatile int filterResolutions, validBoundaryReturns, maintenanceEntries, barriers;
        volatile boolean initialAdmin;
        volatile RuntimeException guardFailure, resolutionFailure;
        Run(Point point, long account) { this.point = point; this.account = account; }
        void gate(Point candidate) throws InterruptedException {
            if (point != candidate || !gated.compareAndSet(false, true)) return;
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            initialAdmin = authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal principal && principal.accountId() == account
                    && authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
            barriers++; reached.countDown(); await(resume, "independent change completed before proceeding");
        }
    }
    /** Transparent, thread-scoped observation. No mocked results, injected Java failures or production hooks. */
    public static class Probe implements BeanPostProcessor {
        final ThreadLocal<Run> current = new ThreadLocal<>();
        @Override public Object postProcessAfterInitialization(Object bean, String name) {
            if (bean instanceof DataSource source) return Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                var result = delegate(source, method, args); return result instanceof Connection connection ? connection(connection) : result;
            });
            if (!(bean instanceof AuthenticatedAccountService || bean instanceof AdminAccessService
                    || bean instanceof ProductCategoryDiagnosticService || bean instanceof ProductMaintenanceService
                    || bean instanceof ProductRepository || bean instanceof CategoryRepository)) return bean;
            MethodInterceptor observer = invocation -> {
                var state = current.get(); if (state == null) return invocation.proceed();
                String method = invocation.getMethod().getName();
                if (bean instanceof AuthenticatedAccountService && method.equals("resolve")) {
                    if (state.stage.equals("FILTER")) state.gate(Point.FILTER);
                    try {
                        var result = invocation.proceed(); if (state.stage.equals("FILTER")) state.filterResolutions++; return result;
                    } catch (RuntimeException failure) { state.resolutionFailure = failure; throw failure; }
                }
                if (bean instanceof AdminAccessService && method.equals("requireAdmin")) {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    state.guards.add(new Guard(state.stage, TransactionSynchronizationManager.isCurrentTransactionReadOnly()));
                    try { return invocation.proceed(); }
                    catch (RuntimeException failure) { state.guardFailure = failure; throw failure; }
                }
                if (bean instanceof ProductCategoryDiagnosticService && method.equals("diagnose")) {
                    boolean boundary = !state.stage.equals("MAINTENANCE"); String previous = state.stage;
                    if (boundary) { state.stage = "DIAGNOSIS"; state.gate(Point.DIAGNOSIS); }
                    try {
                        var result = invocation.proceed();
                        if (boundary && ((ProductInputDiagnostic) result).errors().isEmpty()) state.validBoundaryReturns++;
                        return result;
                    } finally { state.stage = previous; }
                }
                if (bean instanceof ProductMaintenanceService && (method.equals("createProduct") || method.equals("replaceProduct"))) {
                    state.maintenanceEntries++; state.stage = "MAINTENANCE";
                    state.gate(Point.MAINTENANCE);
                    outsideTransaction();
                    try { return invocation.proceed(); }
                    finally { outsideTransaction(); }
                }
                if (bean instanceof ProductRepository) state.resources.add(new ResourceCall(state.stage, "PRODUCT"));
                if (bean instanceof CategoryRepository && method.equals("findAllByIdIn")) state.resources.add(new ResourceCall(state.stage, "CATEGORY"));
                return invocation.proceed();
            };
            if (bean instanceof Advised advised) { advised.addAdvice(0, observer); return bean; }
            var factory = new ProxyFactory(bean); factory.setProxyTargetClass(true); factory.addAdvice(observer); return factory.getProxy();
        }
        private Connection connection(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                Object result;
                try { result = delegate(connection, method, args); }
                catch (SQLException failure) {
                    // H2 resolves a missing table while preparing the statement, before executeQuery can run.
                    var state = current.get();
                    if (state != null && method.getName().startsWith("prepare") && args != null && args[0] instanceof String sql) {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                        state.failedSql.add(failure);
                        state.sql.add(new SqlEvent(state.stage, sql.stripLeading().toLowerCase(Locale.ROOT), false));
                    }
                    throw failure;
                }
                if (result instanceof Statement statement) return statement(statement, args != null && args.length > 0 && args[0] instanceof String sql ? sql : null);
                return result;
            });
        }
        private Statement statement(Statement statement, String prepared) {
            Class<?> type = statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            return (Statement) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                var state = current.get();
                String sql = prepared != null ? prepared : args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                boolean observed = state != null && sql != null && method.getName().startsWith("execute");
                String normalized = sql == null ? "" : sql.replaceAll("(?s)/\\*.*?\\*/", " ").stripLeading().toLowerCase(Locale.ROOT);
                try {
                    var result = delegate(statement, method, args);
                    if (observed) state.sql.add(new SqlEvent(state.stage, normalized, true));
                    return result;
                } catch (SQLException failure) {
                    if (observed) { state.failedSql.add(failure); state.sql.add(new SqlEvent(state.stage, normalized, false)); }
                    throw failure;
                }
            });
        }
        private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); } catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig { @Bean static Probe t067Probe() { return new Probe(); } }
}
