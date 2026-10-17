package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.repositories.OrderItemRepository;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
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
import static org.assertj.core.api.Assertions.catchThrowable;

/** T059: real service transaction, completed SQL and independent reads; no DELETE HTTP/classification. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(ProductDeletionServiceIntegrationTest.ProbeConfig.class)
class ProductDeletionServiceIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ProductMaintenanceService maintenance;
    @Autowired private OrderService orders;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private Probe probe;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private AuthenticatedAccountPrincipal admin;
    private AuthenticatedAccountPrincipal buyer;
    private ProductData target;
    private ProductData other;
    private final Set<Long> ownedOrders = new HashSet<>();

    @BeforeEach
    void prepareCommittedOwnedCatalogAccountsAndIndependentHistory() {
        outsideTransaction();
        assertThat(AopUtils.isAopProxy(maintenance)).isTrue();
        assertThat(AopUtils.isAopProxy(orders)).isTrue();
        probe.stop();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmpty();
        fixture = new AdminProductTestFixture(context);
        admin = new AuthenticatedAccountPrincipal(fixture.createAccount("T059 Admin", "Disposable-T059", Set.of(UserRole.ADMIN)).id());
        buyer = new AuthenticatedAccountPrincipal(fixture.prepareBuyer().id());
        assertThat(database.roles(admin.accountId())).containsExactly(UserRole.ADMIN);
        assertThat(database.roles(buyer.accountId())).containsExactly(UserRole.CLIENT);
        var a = fixture.createCategory("Categoria igual");
        var b = fixture.createCategory("Categoria igual");
        fixture.createCategory("Categoria sem produto");
        target = fixture.createProduct("Produto livre", "Descricao completa do produto livre", "https://example.invalid/t059.jpg", new BigDecimal("10.00"));
        other = fixture.createProduct("Produto vendido", "Descricao completa do produto vendido", "https://example.invalid/t059-other.jpg", new BigDecimal("20.00"));
        fixture.associate(target.id(), a.id()); fixture.associate(target.id(), b.id()); fixture.associate(other.id(), a.id());
        var initial = database.snapshot();
        assertThat(initial.products()).hasSize(2);
        assertThat(initial.categories()).hasSize(3);
        assertThat(initial.associations()).hasSize(3);
        assertThat(initial.orders()).isEmpty(); assertThat(initial.items()).isEmpty();
        ownedOrders.clear();
        register(other, 2, new BigDecimal("40.00"));
    }

    @ParameterizedTest(name = "free product, with category links={0}")
    @ValueSource(booleans = {true, false})
    void deletesOnlyTheFreeTargetAndItsPairsAndCommitsBeforeProxyReturn(boolean paired) throws Exception {
        if (!paired) execute("delete from tb_product_category where product_id = " + target.id());
        var before = database.snapshot();
        var accountIds = database.accountIds();
        probe.start();
        probe.afterFlush = () -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(probe.commits).isZero();
            assertThat(probe.succeededDml()).anyMatch(event -> event.sql().startsWith("delete from tb_product "));
            assertThat(database.snapshot()).as("flush is not commit").isEqualTo(before);
            probe.flushInvisible = true;
        };
        var result = invoke(target.id(), admin);
        assertThat(result.failure()).isNull();
        var expected = new Snapshot(before.products().stream().filter(row -> row.id() != target.id()).toList(),
                before.categories(), before.associations().stream().filter(pair -> pair.productId() != target.id()).collect(java.util.stream.Collectors.toSet()),
                before.orders(), before.items());
        assertThat(result.after()).isEqualTo(expected);
        assertThat(database.accountIds()).isEqualTo(accountIds);
        assertThat(probe.flushInvisible).isTrue();
        assertThat(probe.flushReturns).isEqualTo(1);
        assertThat(probe.commits).isEqualTo(1); assertThat(probe.rollbacks).isZero();
        assertThat(probe.completion).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(probe.calls).containsExactly("guard", "effectiveRoles", "target", "precheck", "delete", "flush");
        assertThat(probe.precheckResults).containsExactly(false);
        assertLocked();
        assertThat(probe.succeededDml()).isNotEmpty().allSatisfy(event -> assertThat(event.sql())
                .matches("delete from tb_product(_category)? where .+"));
        assertThat(probe.sql).allMatch(Event::succeeded);
    }

    @Test
    void missingTargetRejectsBeforePrecheckOrDml() {
        var before = database.snapshot(); probe.start();
        var result = invoke(Long.MAX_VALUE, admin);
        assertThat(result.failure()).isInstanceOf(ProductNotFoundException.class).hasMessage("Product not found");
        assertBeforeDml(result, before);
        assertThat(probe.calls).containsExactly("guard", "effectiveRoles", "target");
    }

    @Test
    void soldTargetRejectsAtTheRealPrecheckWithoutMutatingAnyRows() {
        register(target, 3, new BigDecimal("30.00"));
        var before = database.snapshot(); probe.start();
        var result = invoke(target.id(), admin);
        assertThat(result.failure()).isInstanceOf(DataIntegrityViolationException.class);
        assertBeforeDml(result, before);
        assertThat(probe.calls).containsExactly("guard", "effectiveRoles", "target", "precheck");
        assertThat(probe.precheckResults).containsExactly(true);
        assertLocked();
        assertThat(result.failure().getCause()).as("precheck rejection is not a database FK failure").isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "client", "absent", "revoked-cached", "removed-cached"})
    void revalidatesEffectiveAdminBeforeAnyTargetLookup(String kind) throws Exception {
        var principal = kind.equals("null") ? null : kind.equals("client") ? buyer
                : kind.equals("absent") ? new AuthenticatedAccountPrincipal(Long.MAX_VALUE) : admin;
        var em = kind.endsWith("-cached") ? entityManagerFactory.createEntityManager() : null;
        try {
            if (em != null) {
                assertThat(em.find(UserAccount.class, admin.accountId()).getRoles()).contains(UserRole.ADMIN);
                TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(em));
                execute("delete from tb_user_account_role where user_account_id = " + admin.accountId());
                if (kind.equals("removed-cached")) execute("delete from tb_user_account where id = " + admin.accountId());
                assertThat(database.roles(admin.accountId())).isEmpty();
                assertThat(em.find(UserAccount.class, admin.accountId()).getRoles()).contains(UserRole.ADMIN);
            }
            var before = database.snapshot(); var accountIds = database.accountIds(); probe.start();
            var result = invoke(target.id(), principal);
            assertThat(result.failure()).isInstanceOf(kind.equals("client") || kind.equals("revoked-cached")
                    ? AccessDeniedException.class : BadCredentialsException.class);
            assertBeforeDml(result, before);
            assertThat(database.accountIds()).isEqualTo(accountIds);
            assertThat(probe.calls).doesNotContain("target", "precheck", "delete", "flush");
            assertThat(probe.sql).noneMatch(event -> event.sql().contains("from tb_product") || event.sql().contains("from tb_order_item"));
            if (kind.equals("revoked-cached")) assertThat(probe.calls).containsExactly("guard", "effectiveRoles");
            if (kind.equals("removed-cached")) assertThat(probe.calls).containsExactly("guard");
        } finally {
            probe.stop();
            if (em != null) {
                if (TransactionSynchronizationManager.hasResource(entityManagerFactory)) TransactionSynchronizationManager.unbindResource(entityManagerFactory);
                em.close();
            }
        }
    }

    @Test
    void anotherRealForeignKeyAbortsFlushAndRollsBackAlreadyDeletedPairs() throws Exception {
        execute("create table t059_extra (id bigint primary key, product_id bigint not null, "
                + "constraint fk_t059_extra_product foreign key(product_id) references tb_product(id))");
        execute("insert into t059_extra values (1," + target.id() + "),(2," + target.id() + ")");
        try (var connection = database.openConnection(); var keys = connection.getMetaData().getImportedKeys(null, "PUBLIC", "T059_EXTRA")) {
            assertThat(keys.next()).isTrue();
            assertThat(keys.getString("FK_NAME")).isEqualTo("FK_T059_EXTRA_PRODUCT");
            assertThat(keys.getString("PKTABLE_NAME")).isEqualTo("TB_PRODUCT");
            assertThat(keys.getShort("DELETE_RULE")).isEqualTo((short) java.sql.DatabaseMetaData.importedKeyRestrict);
            assertThat(keys.next()).isFalse();
        }
        var before = database.snapshot(); probe.start();
        var result = invoke(target.id(), admin);
        assertThat(result.failure()).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(result.after()).isEqualTo(before);
        assertThat(probe.precheckResults).containsExactly(false);
        assertThat(probe.calls).containsExactly("guard", "effectiveRoles", "target", "precheck", "delete", "flush");
        assertLocked();
        assertThat(probe.succeededDml()).hasSize(1).allSatisfy(event -> assertThat(event.sql()).startsWith("delete from tb_product_category "));
        assertThat(probe.sql.getLast().sql()).startsWith("delete from tb_product ");
        assertThat(probe.sql.getLast().succeeded()).isFalse();
        assertThat(probe.sql.getLast().state()).isEqualTo("23503");
        var cause = result.failure();
        while (!(cause instanceof SQLException) && cause.getCause() != null) cause = cause.getCause();
        assertThat(cause).isInstanceOf(SQLException.class).hasMessageContaining("FK_T059_EXTRA_PRODUCT");
        assertThat(probe.flushReturns).isZero(); assertThat(probe.commits).isZero(); assertThat(probe.rollbacks).isEqualTo(1);
        assertThat(probe.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        try (var connection = database.openConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("select id, product_id from t059_extra order by id")) {
            for (int i = 1; i <= 2; i++) { assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(i); assertThat(rows.getLong(2)).isEqualTo(target.id()); }
            assertThat(rows.next()).isFalse();
        }
        System.out.println("T059_FK fk=FK_T059_EXTRA_PRODUCT state=23503 precheck=false pairDmlSucceeded=true parentFailed=true extraRowsPreserved=2");
    }

    private Invocation invoke(Long id, AuthenticatedAccountPrincipal principal) {
        outsideTransaction();
        var failure = catchThrowable(() -> maintenance.deleteProduct(id, principal));
        outsideTransaction();
        var after = database.snapshot();
        for (var event : probe.sql) System.out.printf("T059_SQL succeeded=%s state=%s sql=%s%n", event.succeeded(), event.state(), event.sql());
        System.out.printf("T059_PROXY failure=%s calls=%s precheck=%s dmlSucceeded=%d dmlFailed=%d flushReturns=%d commits=%d rollbacks=%d completion=%d flushInvisible=%s%n",
                failure == null ? "none" : failure.getClass().getSimpleName(), probe.calls, probe.precheckResults, probe.succeededDml().size(),
                probe.sql.stream().filter(event -> event.dml() && !event.succeeded()).count(), probe.flushReturns,
                probe.commits, probe.rollbacks, probe.completion, probe.flushInvisible);
        return new Invocation(failure, after);
    }
    private void assertBeforeDml(Invocation result, Snapshot before) {
        assertThat(result.after()).isEqualTo(before); assertThat(probe.sql).noneMatch(Event::dml);
        assertThat(probe.commits).isZero(); assertThat(probe.rollbacks).isEqualTo(1);
        assertThat(probe.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
    }
    private void assertLocked() { assertThat(probe.sql).anyMatch(event -> event.sql().contains("from tb_product ") && event.sql().contains("for update")); }
    private void register(ProductData product, int quantity, BigDecimal total) {
        outsideTransaction();
        try { assertThat(orders.register(new OrderRequestDTO(List.of(new OrderItemRequestDTO(product.id(), quantity))), buyer).total()).isEqualTo(total.toPlainString()); }
        finally { database.snapshot().orders().forEach(order -> ownedOrders.add(order.id())); }
        var snapshot = database.snapshot(); var order = snapshot.orders().getLast(); var item = snapshot.items().getLast();
        assertThat(order.buyerId()).isEqualTo(buyer.accountId()); assertThat(order.status()).isEqualTo("WAITING_PAYMENT");
        assertThat(order.createdAt()).isNotNull(); assertThat(item.orderId()).isEqualTo(order.id());
        assertThat(item.productId()).isEqualTo(product.id()); assertThat(item.position()).isZero();
        assertThat(item.quantity()).isEqualTo(quantity); assertThat(item.unitPrice()).isEqualTo(product.price());
        assertThat(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))).isEqualTo(total);
        System.out.printf("T059_ORDER id=%d product=%d quantity=%d unitPrice=%s independentTotal=%s proxyReturned=true%n",
                order.id(), product.id(), quantity, item.unitPrice(), total);
    }
    private void execute(String sql) throws SQLException {
        try (var connection = database.openConnection(); var statement = connection.createStatement()) { statement.setQueryTimeout(2); statement.execute(sql); }
    }
    @AfterEach
    void cleanOwnedRowsOnlyAfterIndependentAssertions() throws Exception {
        probe.stop(); outsideTransaction(); if (fixture == null) return;
        execute("drop table if exists t059_extra");
        for (long id : ownedOrders) { execute("delete from tb_order_item where order_id = " + id); execute("delete from tb_order where id = " + id); }
        fixture.close(); assertEmpty();
    }
    private void assertEmpty() {
        var snapshot = database.snapshot(); assertThat(snapshot.products()).isEmpty(); assertThat(snapshot.categories()).isEmpty();
        assertThat(snapshot.associations()).isEmpty(); assertThat(snapshot.orders()).isEmpty(); assertThat(snapshot.items()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }
    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private record Invocation(Throwable failure, Snapshot after) { }
    private record Event(String sql, boolean succeeded, String state) {
        boolean dml() { return List.of("insert ", "update ", "delete ", "merge ").stream().anyMatch(sql::startsWith); }
    }

    /** Transparent observation of real collaborators and JDBC execution, active only during the tested call. */
    public static class Probe implements BeanPostProcessor {
        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
        private final List<Event> sql = new ArrayList<>();
        private final List<String> calls = new ArrayList<>();
        private final List<Boolean> precheckResults = new ArrayList<>();
        private int commits, rollbacks, flushReturns, completion;
        private boolean registered, flushInvisible;
        private Runnable afterFlush;
        void start() { sql.clear(); calls.clear(); precheckResults.clear(); commits = 0; rollbacks = 0; flushReturns = 0;
            completion = -1; registered = false; flushInvisible = false; afterFlush = null; active.set(true); }
        void stop() { active.remove(); afterFlush = null; }
        List<Event> succeededDml() { return sql.stream().filter(event -> event.succeeded() && event.dml()).toList(); }
        @Override public Object postProcessAfterInitialization(Object bean, String name) {
            if (bean instanceof DataSource source) return Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                var result = delegate(source, method, args); return result instanceof Connection connection ? connection(connection) : result;
            });
            if (!(bean instanceof AdminAccessService || bean instanceof ProductRepository || bean instanceof OrderItemRepository || bean instanceof UserAccountRepository)) return bean;
            MethodInterceptor advice = invocation -> {
                if (!active.get()) return invocation.proceed();
                String method = invocation.getMethod().getName();
                if (bean instanceof AdminAccessService && method.equals("requireAdmin")) {
                    calls.add("guard");
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
                    if (!registered) { registered = true; TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCompletion(int status) { completion = status; }
                    }); }
                }
                if (bean instanceof UserAccountRepository && method.equals("findEffectiveAccountRolesById")) calls.add("effectiveRoles");
                if (bean instanceof ProductRepository && method.equals("findAdminByIdForUpdate")) calls.add("target");
                if (bean instanceof ProductRepository && method.equals("delete")) calls.add("delete");
                if (bean instanceof OrderItemRepository && method.equals("existsByProductId")) {
                    calls.add("precheck"); var result = invocation.proceed(); precheckResults.add((Boolean) result); return result;
                }
                if (bean instanceof ProductRepository && method.equals("flush")) {
                    calls.add("flush"); var result = invocation.proceed(); flushReturns++; if (afterFlush != null) afterFlush.run(); return result;
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
                if (result instanceof Statement statement) return statement(statement, args != null && args.length > 0 && args[0] instanceof String text ? text : null);
                return result;
            });
        }
        private Statement statement(Statement target, String prepared) {
            return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{target instanceof PreparedStatement ? PreparedStatement.class : Statement.class}, (proxy, method, args) -> {
                String query = prepared != null ? prepared : args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                boolean observe = active.get() && query != null && method.getName().startsWith("execute");
                try { var result = delegate(target, method, args); if (observe) sql.add(new Event(query.stripLeading().toLowerCase(Locale.ROOT), true, null)); return result; }
                catch (SQLException failure) { if (observe) sql.add(new Event(query.stripLeading().toLowerCase(Locale.ROOT), false, failure.getSQLState())); throw failure; }
            });
        }
        private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); } catch (InvocationTargetException cause) { throw cause.getCause(); }
        }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig { @Bean public static Probe t059Probe() { return new Probe(); } }
}
