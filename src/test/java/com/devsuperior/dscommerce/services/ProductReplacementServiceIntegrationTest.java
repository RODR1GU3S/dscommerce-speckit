package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.AdminProductDetailDTO;
import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.dto.ProductWriteRequestDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductCategory;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.InvalidProductInputException;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import jakarta.persistence.EntityManagerFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** T049: service proxy, completed JDBC DML and independent post-return state; no PUT endpoint. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(ProductReplacementServiceIntegrationTest.ProbeConfig.class)
class ProductReplacementServiceIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ProductMaintenanceService maintenance;
    @Autowired private OrderService orderService;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private Probe probe;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private AuthenticatedAccountPrincipal admin;
    private AuthenticatedAccountPrincipal buyer;
    private ProductData target;
    private ProductData other;
    private long oldCategory;
    private long b;
    private long c;
    private final Set<Long> ownedOrders = new HashSet<>();

    @BeforeEach
    void prepareCommittedPrivateDataAndRealHistoricalOrderThroughProxy() {
        outsideTransaction();
        assertThat(AopUtils.isAopProxy(maintenance)).isTrue();
        assertThat(AopUtils.isAopProxy(orderService)).isTrue();
        probe.stop();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        var account = fixture.createAccount("T049 Admin", "Disposable-T049", Set.of(UserRole.ADMIN));
        var client = fixture.prepareBuyer();
        admin = new AuthenticatedAccountPrincipal(account.id());
        buyer = new AuthenticatedAccountPrincipal(client.id());
        assertThat(database.roles(account.id())).containsExactly(UserRole.ADMIN);
        oldCategory = fixture.createCategory("Antiga").id();
        b = fixture.createCategory("Nova igual").id();
        c = fixture.createCategory("Nova igual").id();
        target = fixture.createProduct("Produto original", "Descricao original completa",
                "https://example.invalid/original.jpg", new BigDecimal("10.00"));
        other = fixture.createProduct("Produto independente", "Descricao independente completa",
                "https://example.invalid/other.jpg", new BigDecimal("20.00"));
        fixture.associate(target.id(), oldCategory);
        fixture.associate(other.id(), oldCategory);
        ownedOrders.clear();
        try {
            var created = orderService.register(new OrderRequestDTO(List.of(new OrderItemRequestDTO(target.id(), 2))), buyer);
            assertThat(created.total()).isEqualTo("20.00");
        } finally {
            database.snapshot().orders().forEach(order -> ownedOrders.add(order.id()));
        }
        var prepared = database.snapshot();
        assertThat(prepared.products()).containsExactly(row(target), row(other));
        assertThat(prepared.categories()).hasSize(3);
        assertThat(prepared.associations()).containsExactlyInAnyOrder(new ProductCategory(target.id(), oldCategory),
                new ProductCategory(other.id(), oldCategory));
        assertHistory(prepared);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replacesManagedTargetWithCommitAfterFlushAndPreservesHistoricalRows(boolean twoCategories) {
        var before = database.snapshot();
        probe.start();
        probe.afterFlush = () -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(probe.commits).isZero();
            assertThat(probe.succeededDml()).isNotEmpty();
            assertThat(database.snapshot()).as("flush is not yet visible on another connection").isEqualTo(before);
            probe.flushWasInvisible = true;
        };
        var result = invoke(target.id(), valid(twoCategories), admin);
        assertThat(result.failure()).isNull();
        assertThat(result.dto()).isNotNull();
        var dto = result.dto();
        assertThat(dto.id()).isEqualTo(target.id());
        assertThat(dto.name()).isEqualTo("Produto  novo");
        assertThat(dto.description()).isEqualTo("Descricao  nova completa");
        assertThat(dto.image()).isEqualTo("https://example.invalid/replaced.jpg");
        assertThat(dto.price()).isEqualTo("12.00");
        assertThat(dto.categories().stream().map(option -> option.id())).containsExactlyElementsOf(twoCategories ? List.of(b, c) : List.of(b));
        assertThat(dto.categories()).allSatisfy(option -> assertThat(option.name()).isEqualTo("Nova igual"));
        var expectedProduct = new ProductRow(target.id(), dto.name(), dto.description(), dto.image(), new BigDecimal("12.00"));
        var products = before.products().stream().map(product -> product.id() == target.id() ? expectedProduct : product).toList();
        var pairs = new HashSet<>(before.associations());
        pairs.removeIf(pair -> pair.productId() == target.id());
        pairs.add(new ProductCategory(target.id(), b));
        if (twoCategories) pairs.add(new ProductCategory(target.id(), c));
        assertThat(result.after()).isEqualTo(new Snapshot(products, before.categories(), pairs, before.orders(), before.items()));
        assertHistory(result.after());
        assertThat(probe.flushWasInvisible).isTrue();
        assertThat(probe.flushReturns).isEqualTo(1);
        assertThat(probe.commits).isEqualTo(1);
        assertThat(probe.rollbacks).isZero();
        assertThat(probe.completion).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(probe.calls).containsSubsequence("guard", "effectiveRoles", "target", "local", "diagnose", "flush");
        assertThat(probe.sql).anyMatch(event -> event.sql().contains("for update"));
        assertThat(probe.succeededDml()).noneMatch(event -> event.sql().contains("tb_order") || event.sql().contains("tb_category "));
    }

    @Test
    void absentTargetRejectsWithoutUpsertOrDml() {
        var before = database.snapshot();
        probe.start();
        var result = invoke(Long.MAX_VALUE, valid(true), admin);
        assertThat(result.failure()).isInstanceOf(ProductNotFoundException.class).hasMessage("Product not found");
        assertRejectedBeforeDml(result, before);
        assertThat(probe.calls).containsSubsequence("guard", "effectiveRoles", "target").doesNotContain("local", "diagnose", "flush");
    }

    @ParameterizedTest
    @ValueSource(strings = {"aggregate", "empty", "null"})
    void revalidatesCompleteInputAndReferencesInsideTransactionBeforeMutation(String kind) {
        ProductWriteRequestDTO input = switch (kind) {
            case "aggregate" -> new ProductWriteRequestDTO("ab", "123456789", "0", "/relative", List.of(b, Long.MAX_VALUE));
            case "empty" -> new ProductWriteRequestDTO("Produto novo", "Descricao completa", "12.00", "https://example.invalid/new.jpg", List.of());
            default -> null;
        };
        var before = database.snapshot();
        probe.start();
        var result = invoke(target.id(), input, admin);
        assertThat(result.failure()).isInstanceOf(InvalidProductInputException.class);
        var expected = kind.equals("aggregate") ? List.of("name/INVALID_LENGTH", "description/INVALID_LENGTH", "price/NOT_POSITIVE",
                "image/INVALID_IMAGE_URL", "categoryIds[1]/CATEGORY_NOT_FOUND")
                : kind.equals("empty") ? List.of("categoryIds/EMPTY_CATEGORIES") : List.of("body/INVALID_BODY");
        assertThat(((InvalidProductInputException) result.failure()).getErrors().stream().map(error -> error.field() + "/" + error.code()))
                .containsExactlyInAnyOrderElementsOf(expected);
        assertRejectedBeforeDml(result, before);
        assertThat(probe.calls).containsSubsequence("guard", "effectiveRoles", "target").doesNotContain("flush");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "client", "absent", "revoked-cached"})
    void rechecksEffectiveAdminBeforeTargetLookupOrInvalidInput(String kind) throws Exception {
        var principal = kind.equals("null") ? null : kind.equals("client") ? buyer
                : kind.equals("absent") ? new AuthenticatedAccountPrincipal(Long.MAX_VALUE) : admin;
        var em = kind.equals("revoked-cached") ? entityManagerFactory.createEntityManager() : null;
        try {
            if (em != null) {
                assertThat(em.find(UserAccount.class, admin.accountId()).getRoles()).contains(UserRole.ADMIN);
                TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(em));
                try (var connection = database.openConnection(); var delete = connection.prepareStatement(
                        "delete from tb_user_account_role where user_account_id = ? and role = 'ADMIN'")) {
                    delete.setLong(1, admin.accountId());
                    assertThat(delete.executeUpdate()).isEqualTo(1);
                }
                assertThat(database.roles(admin.accountId())).isEmpty();
                assertThat(em.find(UserAccount.class, admin.accountId()).getRoles()).contains(UserRole.ADMIN);
            }
            var before = database.snapshot();
            probe.start();
            var result = invoke(target.id(), null, principal);
            assertThat(result.failure()).isInstanceOf(kind.equals("client") || kind.equals("revoked-cached")
                    ? AccessDeniedException.class : BadCredentialsException.class);
            assertRejectedBeforeDml(result, before);
            assertThat(probe.calls).doesNotContain("target", "local", "diagnose", "flush");
            assertThat(probe.sql).noneMatch(event -> event.sql().contains("from tb_product") || event.sql().contains("from tb_category"));
            if (em != null) assertThat(probe.calls).contains("effectiveRoles");
        } finally {
            probe.stop();
            if (em != null) {
                if (TransactionSynchronizationManager.hasResource(entityManagerFactory)) TransactionSynchronizationManager.unbindResource(entityManagerFactory);
                em.close();
            }
        }
    }

    @Test
    void realConstraintFailureRollsBackExecutedProductUpdateAndAssociationChanges() throws Exception {
        try (var connection = database.openConnection(); var statement = connection.createStatement()) {
            statement.execute("alter table tb_product_category add constraint ck_t049_pair check (category_id <> " + c + ")");
        }
        try {
            var before = database.snapshot();
            probe.start();
            var result = invoke(target.id(), valid(true), admin);
            assertThat(result.failure()).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(result.dto()).isNull();
            assertThat(result.after()).isEqualTo(before);
            assertHistory(result.after());
            assertThat(probe.succeededDml()).anyMatch(event -> event.sql().startsWith("update tb_product "));
            assertThat(probe.sql).anyMatch(event -> !event.succeeded() && "23513".equals(event.state()) && event.sql().contains("tb_product_category"));
            assertThat(probe.flushReturns).isZero();
            assertThat(probe.commits).isZero();
            assertThat(probe.rollbacks).isEqualTo(1);
            assertThat(probe.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        } finally {
            probe.stop();
            dropConstraint();
        }
    }

    private Invocation invoke(Long id, ProductWriteRequestDTO input, AuthenticatedAccountPrincipal principal) {
        outsideTransaction();
        AdminProductDetailDTO dto = null;
        Throwable failure = null;
        Snapshot after;
        try { dto = maintenance.replaceProduct(id, input, principal); }
        catch (Throwable cause) { failure = cause; }
        finally {
            outsideTransaction();
            after = database.snapshot();
        }
        System.out.printf("T049_PROXY failure=%s dmlSucceeded=%d dmlFailed=%d flushReturns=%d commits=%d rollbacks=%d completion=%d flushInvisible=%s%n",
                failure == null ? "none" : failure.getClass().getSimpleName(), probe.succeededDml().size(),
                probe.sql.stream().filter(event -> event.dml() && !event.succeeded()).count(), probe.flushReturns,
                probe.commits, probe.rollbacks, probe.completion, probe.flushWasInvisible);
        return new Invocation(dto, failure, after);
    }
    private void assertRejectedBeforeDml(Invocation result, Snapshot before) {
        assertThat(result.dto()).isNull();
        assertThat(result.after()).isEqualTo(before);
        assertThat(probe.sql).noneMatch(Event::dml);
        assertThat(probe.commits).isZero();
        assertThat(probe.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
    }
    private void assertHistory(Snapshot snapshot) {
        assertThat(snapshot.orders()).hasSize(1);
        assertThat(snapshot.orders().get(0).buyerId()).isEqualTo(buyer.accountId());
        assertThat(snapshot.items()).hasSize(1);
        var item = snapshot.items().get(0);
        assertThat(item.orderId()).isEqualTo(snapshot.orders().get(0).id());
        assertThat(item.productId()).isEqualTo(target.id());
        assertThat(item.position()).isZero();
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.unitPrice()).isEqualTo(new BigDecimal("10.00"));
        assertThat(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))).isEqualTo(new BigDecimal("20.00"));
    }
    private ProductWriteRequestDTO valid(boolean twoCategories) {
        return new ProductWriteRequestDTO("\u2003Produto  novo\u00a0", "\u00a0Descricao  nova completa\u2003", "12.000",
                "https://example.invalid/replaced.jpg", twoCategories ? List.of(b, b, c) : List.of(b, b));
    }
    private void dropConstraint() throws SQLException {
        try (var connection = database.openConnection(); var statement = connection.createStatement()) {
            statement.execute("alter table tb_product_category drop constraint if exists ck_t049_pair");
        }
    }
    @AfterEach
    void cleanOnlyOwnedRowsAfterIndependentAssertions() throws Exception {
        probe.stop();
        outsideTransaction();
        if (fixture == null) return;
        dropConstraint();
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var items = connection.prepareStatement("delete from tb_order_item where order_id = ?");
                 var orders = connection.prepareStatement("delete from tb_order where id = ?");
                 var pairs = connection.prepareStatement("delete from tb_product_category where product_id = ?")) {
                for (long id : ownedOrders) { items.setLong(1, id); items.executeUpdate(); orders.setLong(1, id); orders.executeUpdate(); }
                for (var product : List.of(target, other)) { pairs.setLong(1, product.id()); pairs.executeUpdate(); }
                connection.commit();
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
        fixture.close();
        assertEmptyDatabase();
    }
    private void assertEmptyDatabase() {
        var snapshot = database.snapshot();
        assertThat(snapshot.products()).isEmpty(); assertThat(snapshot.categories()).isEmpty();
        assertThat(snapshot.associations()).isEmpty(); assertThat(snapshot.orders()).isEmpty();
        assertThat(snapshot.items()).isEmpty(); assertThat(database.accountIds()).isEmpty();
    }
    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private static ProductRow row(ProductData product) {
        return new ProductRow(product.id(), product.name(), product.description(), product.image(), product.price());
    }
    private record Invocation(AdminProductDetailDTO dto, Throwable failure, Snapshot after) { }
    private record Event(String sql, boolean succeeded, String state) {
        boolean dml() { return List.of("insert ", "update ", "delete ", "merge ").stream().anyMatch(sql::startsWith); }
    }

    /** Transparent test-only observation; no replacement of business collaborators or fault injection. */
    public static class Probe implements BeanPostProcessor {
        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
        private final List<Event> sql = new ArrayList<>();
        private final List<String> calls = new ArrayList<>();
        private int commits;
        private int rollbacks;
        private int flushReturns;
        private int completion;
        private boolean registered;
        private boolean flushWasInvisible;
        private Runnable afterFlush;
        void start() { sql.clear(); calls.clear(); commits = 0; rollbacks = 0; flushReturns = 0; completion = -1;
            registered = false; flushWasInvisible = false; afterFlush = null; active.set(true); }
        void stop() { active.remove(); afterFlush = null; }
        List<Event> succeededDml() { return sql.stream().filter(event -> event.succeeded() && event.dml()).toList(); }

        @Override public Object postProcessAfterInitialization(Object bean, String name) {
            if (bean instanceof DataSource source) {
                return Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    var result = delegate(source, method, args);
                    return result instanceof Connection connection ? connection(connection) : result;
                });
            }
            if (!(bean instanceof AdminAccessService || bean instanceof ProductInputValidationService
                    || bean instanceof ProductCategoryDiagnosticService || bean instanceof ProductRepository || bean instanceof UserAccountRepository)) return bean;
            MethodInterceptor advice = invocation -> {
                if (!active.get()) return invocation.proceed();
                String method = invocation.getMethod().getName();
                if (bean instanceof AdminAccessService && method.equals("requireAdmin")) {
                    calls.add("guard");
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
                    if (!registered) {
                        registered = true;
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override public void afterCompletion(int status) { completion = status; }
                        });
                    }
                }
                if (bean instanceof UserAccountRepository && method.equals("findEffectiveAccountRolesById")) calls.add("effectiveRoles");
                if (bean instanceof ProductRepository && method.equals("findAdminByIdForUpdate")) calls.add("target");
                if (bean instanceof ProductInputValidationService && method.equals("validate")) calls.add("local");
                if (bean instanceof ProductCategoryDiagnosticService && method.equals("diagnose")) calls.add("diagnose");
                if (bean instanceof ProductRepository && method.equals("flush")) {
                    calls.add("flush");
                    var result = invocation.proceed();
                    flushReturns++;
                    if (afterFlush != null) afterFlush.run();
                    return result;
                }
                return invocation.proceed();
            };
            if (bean instanceof Advised advised) { advised.addAdvice(0, advice); return bean; }
            var proxy = new ProxyFactory(bean); proxy.setProxyTargetClass(true); proxy.addAdvice(advice); return proxy.getProxy();
        }
        private Connection connection(Connection target) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                var result = delegate(target, method, args);
                if (active.get() && method.getName().equals("commit")) commits++;
                if (active.get() && method.getName().equals("rollback")) rollbacks++;
                if (result instanceof Statement statement) {
                    String prepared = args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                    return statement(statement, prepared);
                }
                return result;
            });
        }
        private Statement statement(Statement target, String prepared) {
            Class<?> type = target instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            return (Statement) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                String query = prepared != null ? prepared : args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                boolean observe = active.get() && query != null && method.getName().startsWith("execute");
                try {
                    var result = delegate(target, method, args);
                    if (observe) sql.add(new Event(normalized(query), true, null));
                    return result;
                } catch (SQLException failure) {
                    if (observe) sql.add(new Event(normalized(query), false, failure.getSQLState()));
                    throw failure;
                }
            });
        }
        private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); }
            catch (InvocationTargetException cause) { throw cause.getCause(); }
        }
        private static String normalized(String sql) { return sql.stripLeading().toLowerCase(Locale.ROOT); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig { @Bean public static Probe t049Probe() { return new Probe(); } }
}
