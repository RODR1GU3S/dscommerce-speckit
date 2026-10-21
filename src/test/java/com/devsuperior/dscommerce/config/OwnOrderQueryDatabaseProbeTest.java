package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe.AccountRole;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** T004 infrastructure proofs: H2/JDBC and actual Hibernate; one setup MockMvc login, no network HTTP. */
class OwnOrderQueryDatabaseProbeTest {

    private final WebApplicationContextRunner base = new WebApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=test", "spring.h2.console.enabled=false",
                    "spring.jpa.open-in-view=false")
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withInitializer(new TestComponentExclusion())
            .withInitializer(new AdminProductTestConfig())
            .withUserConfiguration(DscommerceApplication.class);
    private final WebApplicationContextRunner observed = base
            .withUserConfiguration(OwnOrderQueryTestObservationConfig.class);

    @Test
    void capturesEveryDomainTableAndCompleteAccountsIncludingAccountsWithoutRoles() {
        observed.run(context -> {
            assertThat(context).hasNotFailed();
            var probe = context.getBean(OwnOrderQueryDatabaseProbe.class);
            try (var fixture = new OwnOrderQueryTestFixture(context)) {
                var scenario = fixture.preparePaginationScenario();
                var snapshot = probe.snapshot();
                assertThat(snapshot.data()).isEqualTo(new AdminProductDatabaseProbe(
                        context.getEnvironment().getRequiredProperty("spring.datasource.url")).snapshot());
                assertThat(snapshot.data().products()).hasSize(2);
                assertThat(snapshot.data().categories()).hasSize(1);
                assertThat(snapshot.data().associations()).hasSize(2);
                assertThat(snapshot.data().orders()).hasSize(9);
                assertThat(snapshot.data().items()).hasSize(10);
                var access = scenario.accounts();
                assertThat(snapshot.accountIds()).containsExactlyInAnyOrder(access.client().id(), access.admin().id(),
                        access.both().id(), access.none().id(), scenario.otherBuyer().id(), scenario.emptyBuyer().id());
                assertThat(snapshot.roles()).containsExactlyInAnyOrder(
                        new AccountRole(access.client().id(), UserRole.CLIENT),
                        new AccountRole(access.admin().id(), UserRole.ADMIN),
                        new AccountRole(access.both().id(), UserRole.CLIENT),
                        new AccountRole(access.both().id(), UserRole.ADMIN),
                        new AccountRole(scenario.otherBuyer().id(), UserRole.CLIENT),
                        new AccountRole(scenario.emptyBuyer().id(), UserRole.CLIENT));
                assertThatThrownBy(() -> snapshot.roles().clear()).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> snapshot.accountIds().clear()).isInstanceOf(UnsupportedOperationException.class);
                OwnOrderQueryDatabaseProbe.assertUnchanged(snapshot, probe.snapshot());
            }
        });
    }

    @Test
    void detectsChangesInAllSevenTablesAndRedactsAccountComparisonFailures() {
        observed.run(context -> {
            assertThat(context).hasNotFailed();
            var probe = context.getBean(OwnOrderQueryDatabaseProbe.class);
            var jdbc = independent(context);
            try (var fixture = new OwnOrderQueryTestFixture(context)) {
                var scenario = fixture.preparePaginationScenario();
                var before = probe.snapshot();
                var buyer = scenario.accounts().client();
                var passwordHash = jdbc.queryForObject("select password_hash from tb_user_account where id = ?",
                        String.class, buyer.id());
                var category = before.data().categories().get(0);
                var association = before.data().associations().iterator().next();
                var order = scenario.expectedClientOrders().get(0);
                var item = order.items().get(0);
                var changes = List.of(
                        new Change("tb_user_account", "update tb_user_account set name = ? where id = ?",
                                new Object[]{"T004_NAME_CANARY", buyer.id()}, new Object[]{buyer.name(), buyer.id()}),
                        new Change("tb_user_account", "update tb_user_account set password_hash = ? where id = ?",
                                new Object[]{"T004_HASH_CANARY", buyer.id()}, new Object[]{passwordHash, buyer.id()}),
                        new Change("tb_product", "update tb_product set description = ? where id = ?",
                                new Object[]{"Descricao alterada", scenario.productA().id()},
                                new Object[]{scenario.productA().description(), scenario.productA().id()}),
                        new Change("tb_category", "update tb_category set name = ? where id = ?",
                                new Object[]{"Categoria alterada", category.id()}, new Object[]{category.name(), category.id()}),
                        new Change("tb_order", "update tb_order set buyer_id = ? where id = ?",
                                new Object[]{scenario.otherBuyer().id(), order.id()}, new Object[]{buyer.id(), order.id()}),
                        new Change("tb_order_item", "update tb_order_item set quantity = ? where id = ?",
                                new Object[]{99, item.id()}, new Object[]{item.quantity(), item.id()}));
                for (var change : changes) {
                    try {
                        jdbc.update(change.sql(), change.changed());
                        var after = probe.snapshot();
                        var failure = catchThrowable(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before, after));
                        requireSafeAssertion(failure, buyer.name(), passwordHash, "T004_NAME_CANARY", "T004_HASH_CANARY");
                        assertThat(failure.getMessage()).contains(change.table());
                        var ordinaryAssertion = catchThrowable(() -> assertThat(after).isEqualTo(before));
                        requireSafeAssertion(ordinaryAssertion, buyer.name(), passwordHash,
                                "T004_NAME_CANARY", "T004_HASH_CANARY");
                        requireRedacted(after.toString(), buyer.name(), passwordHash, "T004_NAME_CANARY", "T004_HASH_CANARY");
                    } finally {
                        jdbc.update(change.sql(), change.restored());
                    }
                    OwnOrderQueryDatabaseProbe.assertUnchanged(before, probe.snapshot());
                }
                jdbc.update("delete from tb_product_category where product_id = ? and category_id = ?",
                        association.productId(), association.categoryId());
                assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before, probe.snapshot()))
                        .isInstanceOf(AssertionError.class).hasMessageContaining("tb_product_category");
                jdbc.update("insert into tb_product_category(product_id, category_id) values (?, ?)",
                        association.productId(), association.categoryId());
                jdbc.update("delete from tb_user_account_role where user_account_id = ?", buyer.id());
                assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before, probe.snapshot()))
                        .isInstanceOf(AssertionError.class).hasMessageContaining("tb_user_account_role");
                jdbc.update("insert into tb_user_account_role(user_account_id, role) values (?, 'CLIENT')", buyer.id());
                OwnOrderQueryDatabaseProbe.assertUnchanged(before, probe.snapshot());
            }
        });
    }

    @Test
    void readsCommittedStateOnFreshConnectionsDuringCallerRollback() {
        observed.run(context -> {
            assertThat(context).hasNotFailed();
            var probe = context.getBean(OwnOrderQueryDatabaseProbe.class);
            try (var fixture = new OwnOrderQueryTestFixture(context)) {
                var buyer = fixture.prepareBuyer();
                var before = probe.snapshot();
                try (var first = probe.openConnection(); var second = probe.openConnection()) {
                    assertThat(first).isNotSameAs(second);
                }
                var outer = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                outer.executeWithoutResult(status -> {
                    context.getBean(JdbcTemplate.class).update("update tb_user_account set name = ? where id = ?",
                            "T004_UNCOMMITTED_CANARY", buyer.id());
                    OwnOrderQueryDatabaseProbe.assertUnchanged(before, probe.snapshot());
                    status.setRollbackOnly();
                });
                OwnOrderQueryDatabaseProbe.assertUnchanged(before, probe.snapshot());
            }
        });
    }

    @Test
    void restrictsObservationToReadAfterSetupLoginAndBeforeCleanup() {
        observed.run(context -> {
            assertThat(context).hasNotFailed();
            var probe = context.getBean(OwnOrderQueryDatabaseProbe.class);
            var sql = context.getBean(SqlObservation.class);
            try (var fixture = new OwnOrderQueryTestFixture(context)) {
                var buyer = fixture.prepareBuyer();
                var product = fixture.createProduct("Janela explicita", new BigDecimal("10.00"));
                ormUpdate(context, product.id()); // Deliberate setup noise, before the observation window.
                var token = fixture.login(buyer); // One real internal MockMvc login; no network request.
                assertThat(sql.events()).isNotEmpty();
                var result = probe.observeRead(sql, () -> {
                    ormRead(context, product.id());
                    return token;
                });
                result.assertNoWrites();
                assertThat(result.events()).hasSize(1).allSatisfy(event -> {
                    assertThat(event.sql()).startsWith("select ");
                    assertThat(event.transactionActive()).isTrue();
                });
                requireRedacted(result.toString(), token, buyer.name(), buyer.password());
                assertThatThrownBy(() -> result.events().clear()).isInstanceOf(UnsupportedOperationException.class);
                ormUpdate(context, product.id()); // Cleanup-side noise must not change the captured event list.
                assertThat(sql.events()).hasSize(2);
                assertThat(result.events()).hasSize(1);
                result.assertNoWrites();
                assertThatThrownBy(() -> probe.observeRead(sql, () -> {
                    throw new IllegalStateException("Operation failure must propagate");
                })).isInstanceOf(IllegalStateException.class).hasMessage("Operation failure must propagate");
            }
        });
    }

    @Test
    void detectsHibernateDmlEvenWhenCommittedSnapshotsAreEqual() {
        observed.run(context -> {
            assertThat(context).hasNotFailed();
            var probe = context.getBean(OwnOrderQueryDatabaseProbe.class);
            try (var fixture = new OwnOrderQueryTestFixture(context)) {
                var product = fixture.createProduct("DML sem mudanca final", BigDecimal.ONE);
                var result = probe.observeRead(context.getBean(SqlObservation.class), () -> {
                    ormUpdate(context, product.id());
                    return null;
                });
                assertThat(result.before()).isEqualTo(result.after());
                assertThat(result.events()).hasSize(1);
                assertThatThrownBy(result::assertNoWrites).isInstanceOf(AssertionError.class)
                        .hasMessage("Non-SELECT Hibernate SQL observed in the read window");
            }
        });
    }

    @Test
    void detectsDirectJdbcChangesWhichStatementInspectorCannotObserve() {
        observed.run(context -> {
            assertThat(context).hasNotFailed();
            var probe = context.getBean(OwnOrderQueryDatabaseProbe.class);
            try (var fixture = new OwnOrderQueryTestFixture(context)) {
                var product = fixture.createProduct("JDBC direto", BigDecimal.ONE);
                var result = probe.observeRead(context.getBean(SqlObservation.class), () ->
                        independent(context).update("update tb_product set price = 2.00 where id = ?", product.id()));
                assertThat(result.events()).isEmpty();
                assertThatThrownBy(result::assertNoWrites).isInstanceOf(AssertionError.class)
                        .hasMessage("Observed database state changed: tb_product");
            }
        });
    }

    @Test
    void activatesOnlyByExplicitTestImportAndKeepsOtherContextsIndependent() {
        observed.run(first -> {
            assertThat(first).hasNotFailed();
            assertThat(first.getBeansOfType(SqlObservation.class)).containsOnlyKeys("ownOrderQuerySqlObservation");
            var probe = first.getBean(OwnOrderQueryDatabaseProbe.class);
            try (var fixture = new OwnOrderQueryTestFixture(first)) {
                fixture.prepareBuyer();
                var before = probe.snapshot();
                base.run(second -> {
                    assertThat(second).hasNotFailed();
                    assertThat(second.getBeansOfType(SqlObservation.class)).isEmpty();
                    assertThat(second.getBeansOfType(OwnOrderQueryDatabaseProbe.class)).isEmpty();
                    assertThat(second.getEnvironment().getRequiredProperty("spring.datasource.url"))
                            .isNotEqualTo(first.getEnvironment().getRequiredProperty("spring.datasource.url"));
                    var otherProbe = new OwnOrderQueryDatabaseProbe(
                            second.getEnvironment().getRequiredProperty("spring.datasource.url"));
                    assertThat(otherProbe.snapshot().accountIds()).isEmpty();
                    OwnOrderQueryDatabaseProbe.assertUnchanged(before, probe.snapshot());
                });
                OwnOrderQueryDatabaseProbe.assertUnchanged(before, probe.snapshot());
            }
        });
        new ApplicationContextRunner().withUserConfiguration(OwnOrderQueryTestObservationConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(SqlObservation.class)).isEmpty();
                    assertThat(context.getBeansOfType(OwnOrderQueryDatabaseProbe.class)).isEmpty();
                });
        assertThatThrownBy(() -> new OwnOrderQueryDatabaseProbe("jdbc:h2:mem:dscommerce-test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static JdbcTemplate independent(WebApplicationContext context) {
        return new JdbcTemplate(new DriverManagerDataSource(
                context.getEnvironment().getRequiredProperty("spring.datasource.url"), "sa", ""));
    }

    private static void ormUpdate(WebApplicationContext context, long productId) {
        var entityManager = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status ->
                entityManager.createQuery("update Product p set p.description = p.description where p.id = :id")
                        .setParameter("id", productId).executeUpdate());
    }

    private static void ormRead(WebApplicationContext context, long productId) {
        var entityManager = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
        var readOnly = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        readOnly.setReadOnly(true);
        readOnly.executeWithoutResult(status -> entityManager.createQuery("select p from Product p where p.id = :id", Product.class)
                .setParameter("id", productId).getSingleResult());
    }

    private static void requireSafeAssertion(Throwable failure, String... secrets) {
        if (!(failure instanceof AssertionError)) { throw new AssertionError("Expected a comparison assertion"); }
        requireRedacted(failure.toString(), secrets);
    }

    private static void requireRedacted(String diagnostic, String... secrets) {
        for (var secret : secrets) {
            if (secret != null && !secret.isEmpty() && diagnostic.contains(secret)) {
                throw new AssertionError("Sensitive fixture data leaked in comparison diagnostics");
            }
        }
    }

    private record Change(String table, String sql, Object[] changed, Object[] restored) { }
}
