package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T048: actual repository row lock in H2; the transactional probe only reads, never replaces a product. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductLockRepositoryTest.ProbeConfig.class)
class AdminProductLockRepositoryTest {
    @Autowired private WebApplicationContext context;
    @Autowired private LookupProbe proxy;
    @Autowired private ProductRepository repository;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private ProductData target;
    private ProductData other;

    @BeforeEach
    void prepareCommittedPrivateDataWithoutCallerTransaction() {
        outsideTransaction();
        assertThat(AopUtils.isAopProxy(proxy)).isTrue();
        assertThat(AopUtils.isAopProxy(repository)).isTrue();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        var a = fixture.createCategory("Categoria igual");
        var b = fixture.createCategory("Categoria igual");
        target = fixture.createProduct("Produto alvo", "Descricao completa do alvo",
                "https://example.invalid/lock.jpg", new BigDecimal("10.00"));
        other = fixture.createProduct("Outro produto", "Descricao completa independente",
                "https://example.invalid/other-lock.jpg", new BigDecimal("20.00"));
        fixture.associate(target.id(), a.id());
        fixture.associate(target.id(), b.id());
        var prepared = database.snapshot();
        assertThat(prepared.products()).containsExactly(row(target), row(other));
        assertThat(prepared.categories()).containsExactly(
                new AdminProductDatabaseProbe.CategoryRow(a.id(), a.name()),
                new AdminProductDatabaseProbe.CategoryRow(b.id(), b.name()));
        assertThat(prepared.associations()).containsExactlyInAnyOrder(
                new AdminProductTestFixture.ProductCategory(target.id(), a.id()),
                new AdminProductTestFixture.ProductCategory(target.id(), b.id()));
        assertThat(prepared.orders()).isEmpty();
        assertThat(prepared.items()).isEmpty();
        sql.clear();
    }

    @ParameterizedTest(name = "target row locked until completion, rollback={0}")
    @ValueSource(booleans = {false, true})
    void locksOnlyTargetWithoutFetchingCategoriesAndReleasesAtTransactionCompletion(boolean rollback) {
        var before = database.snapshot();
        var completion = new AtomicInteger(-1);
        var contender = new AtomicReference<LockAttempt>();
        Consumer<Optional<Product>> inspect = found -> {
            var product = found.orElseThrow();
            assertThat(new ProductRow(product.getId(), product.getName(), product.getDescription(), product.getImage(), product.getPrice()))
                    .isEqualTo(row(target));
            assertThat(Hibernate.isInitialized(product.getCategories())).as("lock query leaves collection lazy").isFalse();
            assertSingleProductSelectWithoutCollectionFetch();
            assertThat(attemptIndependentLock(other.id()).acquired()).as("another product remains available").isTrue();
            contender.set(attemptIndependentLock(target.id()));
            if (rollback) throw new RollbackSignal();
        };
        try {
            if (rollback) {
                assertThatThrownBy(() -> proxy.lockedRead(target.id(), inspect, completion)).isExactlyInstanceOf(RollbackSignal.class);
            } else {
                proxy.lockedRead(target.id(), inspect, completion);
            }
        } finally {
            outsideTransaction();
            // Fresh DriverManager transaction after the Spring proxy has completed, before cleanup.
            assertThat(database.snapshot()).isEqualTo(before);
        }
        int expectedCompletion = rollback ? TransactionSynchronization.STATUS_ROLLED_BACK : TransactionSynchronization.STATUS_COMMITTED;
        assertThat(completion.get()).isEqualTo(expectedCompletion);
        var released = attemptIndependentLock(target.id());
        assertThat(released.acquired()).as("row lock released after proxy completion").isTrue();
        System.out.printf("T048_LOCK rollback=%s targetBlocked=%s sqlState=%s errorCode=%d completion=%d lockReleased=%s snapshotEqual=true%n",
                rollback, !contender.get().acquired(), contender.get().sqlState(), contender.get().errorCode(), completion.get(), released.acquired());
        assertThat(contender.get().acquired()).as("independent contender must be blocked while lookup transaction holds target").isFalse();
        assertThat(contender.get().errorCode()).isEqualTo(50200);
        assertThat(sql.events().get(0).sql().toLowerCase(Locale.ROOT)).contains("for update");
    }

    @Test
    void absentTargetReturnsEmptyWithoutCreatingRows() {
        var before = database.snapshot();
        var completion = new AtomicInteger(-1);
        try {
            proxy.lockedRead(Long.MAX_VALUE, found -> {
                assertThat(found).isEmpty();
                assertSingleProductSelectWithoutCollectionFetch();
            }, completion);
        } finally {
            outsideTransaction();
            assertThat(database.snapshot()).isEqualTo(before);
        }
        assertThat(completion.get()).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        System.out.println("T048_CONTROL case=absent optionalEmpty=true snapshotEqual=true completion=COMMITTED");
    }

    @Test
    void existingPublicDetailStillFetchesCategoriesWithoutAcquiringThisAdministrativeLock() {
        var before = database.snapshot();
        var completion = new AtomicInteger(-1);
        try {
            proxy.publicRead(target.id(), found -> {
                var product = found.orElseThrow();
                assertThat(product.getId()).isEqualTo(target.id());
                assertThat(Hibernate.isInitialized(product.getCategories())).isTrue();
                assertThat(product.getCategories()).hasSize(2);
                assertThat(attemptIndependentLock(target.id()).acquired()).isTrue();
            }, completion);
        } finally {
            outsideTransaction();
            assertThat(database.snapshot()).isEqualTo(before);
        }
        assertThat(completion.get()).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(sql.events()).hasSize(1);
        assertThat(sql.events().get(0).sql().toLowerCase(Locale.ROOT)).contains("join").doesNotContain("for update");
        System.out.println("T048_CONTROL case=public categories=2 independentLockAcquired=true snapshotEqual=true");
    }

    private void assertSingleProductSelectWithoutCollectionFetch() {
        assertThat(sql.events()).hasSize(1);
        var event = sql.events().get(0);
        assertThat(event.transactionActive()).isTrue();
        assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ")
                .contains("tb_product").doesNotContain(" join ", "tb_category", "tb_product_category");
        System.out.printf("T048_SQL %s%n", event.sql());
    }

    private LockAttempt attemptIndependentLock(long productId) {
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var options = connection.createStatement()) { options.execute("SET LOCK_TIMEOUT 200"); }
                try (var statement = connection.prepareStatement("SELECT id FROM tb_product WHERE id = ? FOR UPDATE NOWAIT")) {
                    statement.setLong(1, productId);
                    statement.setQueryTimeout(1);
                    try (var rows = statement.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getLong(1)).isEqualTo(productId);
                        assertThat(rows.next()).isFalse();
                    }
                }
                return new LockAttempt(true, "none", 0);
            } catch (SQLException failure) {
                if (failure.getErrorCode() != 50200) throw failure;
                return new LockAttempt(false, failure.getSQLState(), failure.getErrorCode());
            } finally {
                connection.rollback(); // This independent connection only acquired/read a lock, never wrote data.
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unexpected independent lock preparation failure", failure);
        }
    }

    @AfterEach
    void cleanOnlyOwnedFixtureAfterProxyAndIndependentAssertions() {
        outsideTransaction();
        if (fixture != null) {
            fixture.close();
            assertEmptyDatabase();
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
    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private static ProductRow row(ProductData product) {
        return new ProductRow(product.id(), product.name(), product.description(), product.image(), product.price());
    }
    private record LockAttempt(boolean acquired, String sqlState, int errorCode) { }
    private static class RollbackSignal extends RuntimeException { }

    /** Test-only read transaction boundary, not a maintenance implementation or an authorization bypass in production. */
    public static class LookupProbe {
        private final ProductRepository repository;
        public LookupProbe(ProductRepository repository) { this.repository = repository; }

        @Transactional
        public void lockedRead(long id, Consumer<Optional<Product>> inspect, AtomicInteger completion) {
            observeCompletion(completion);
            inspect.accept(repository.findAdminByIdForUpdate(id));
        }
        @Transactional(readOnly = true)
        public void publicRead(long id, Consumer<Optional<Product>> inspect, AtomicInteger completion) {
            observeCompletion(completion);
            inspect.accept(repository.findByIdWithCategories(id));
        }
        private void observeCompletion(AtomicInteger completion) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { completion.set(status); }
            });
        }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig {
        @Bean LookupProbe t048LookupProbe(ProductRepository repository) { return new LookupProbe(repository); }
        @Bean AdminProductDatabaseProbe.SqlObservation t048SqlObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }
        @Bean HibernatePropertiesCustomizer t048SqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
