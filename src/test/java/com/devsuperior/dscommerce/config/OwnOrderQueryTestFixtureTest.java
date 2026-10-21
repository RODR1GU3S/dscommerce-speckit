package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.FIRST_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T003 support proofs only: real H2/JDBC, no endpoint scenarios, mocks or HTTP requests. */
class OwnOrderQueryTestFixtureTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=test", "spring.h2.console.enabled=false",
                    "spring.jpa.open-in-view=false")
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withInitializer(new TestComponentExclusion())
            .withInitializer(new AdminProductTestConfig())
            .withUserConfiguration(DscommerceApplication.class);

    @Test
    void preparesCommittedRolesOrdersTiesPositionsAndIndependentMoneyManifest() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var independent = independent(context);
            assertEmpty(independent);
            try (var fixture = new OwnOrderQueryTestFixture(context)) {
                assertEmpty(independent); // Construction must not populate any table.
                var scenario = fixture.preparePaginationScenario();
                var access = scenario.accounts();
                assertRoles(independent, access.client().id(), UserRole.CLIENT);
                assertRoles(independent, access.admin().id(), UserRole.ADMIN);
                assertRoles(independent, access.both().id(), UserRole.ADMIN, UserRole.CLIENT);
                assertRoles(independent, access.none().id());
                assertThat(counts(independent)).containsEntry("tb_user_account", 6L)
                        .containsEntry("tb_order", 9L).containsEntry("tb_order_item", 10L)
                        .containsEntry("tb_product", 2L).containsEntry("tb_category", 1L)
                        .containsEntry("tb_product_category", 2L);
                assertThat(independent.queryForObject("select count(*) from tb_order where buyer_id = ?",
                        Long.class, scenario.emptyBuyer().id())).isZero();
                for (var order : scenario.insertedOrders()) {
                    assertCommittedOrder(independent, order);
                }
                var own = scenario.expectedClientOrders();
                assertThat(own).hasSize(5).extracting(OrderManifest::total)
                        .containsExactly("15.25", "9007199254740993.01", "0.01", "20.00", "42.50");
                assertThat(independent.queryForList("select id from tb_order where buyer_id = ? "
                                + "order by created_at desc, id desc", Long.class, access.client().id()))
                        .containsExactlyElementsOf(own.stream().map(OrderManifest::id).toList());
                assertThat(own.subList(0, 3)).extracting(OrderManifest::createdAt)
                        .containsOnly(FIRST_INSTANT.plusSeconds(60));
                var twoItems = own.get(4).items();
                assertThat(twoItems).extracting(item -> item.productId())
                        .containsExactly(scenario.productB().id(), scenario.productA().id());
                assertThat(twoItems).extracting(item -> item.subtotal()).containsExactly("20.00", "22.50");
                assertThat(scenario.productB().price()).isEqualByComparingTo("12.00");
                assertThatThrownBy(() -> scenario.insertedOrders().clear())
                        .isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> own.clear()).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> own.get(4).items().clear()).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(fixture::preparePaginationScenario).isInstanceOf(IllegalStateException.class);
            }
            assertEmpty(independent);
        });
    }

    @Test
    void commitsSetupIndependentlyOfCallerRollback() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var independent = independent(context);
            try (var fixture = new OwnOrderQueryTestFixture(context)) {
                var buyer = fixture.prepareBuyer();
                var product = fixture.createProduct("Commit independente", new BigDecimal("12.00"));
                var outer = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                var order = outer.execute(status -> {
                    var prepared = fixture.createOrder(buyer, FIRST_INSTANT,
                            List.of(new ItemInput(product.id(), 2, "10.00", "20.00")), "20.00");
                    assertCommittedOrder(independent, prepared);
                    status.setRollbackOnly();
                    return prepared;
                });
                assertThat(order).isNotNull();
                assertCommittedOrder(independent, order);
            }
            assertEmpty(independent);
        });
    }

    @Test
    void cleansOnlyOwnedRowsBeforeCatalogAndRejectsForeignFixtureInputs() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var independent = independent(context);
            try (var fixture = new OwnOrderQueryTestFixture(context);
                 var other = new OwnOrderQueryTestFixture(context)) {
                var scenario = fixture.preparePaginationScenario();
                var otherBuyer = other.prepareBuyer();
                var otherProduct = other.createProduct("Outro proprietario", new BigDecimal("5.00"));
                var preserved = other.createOrder(otherBuyer, FIRST_INSTANT,
                        List.of(new ItemInput(otherProduct.id(), 1, "5.00", "5.00")), "5.00");
                var before = counts(independent);
                assertThatThrownBy(() -> fixture.createOrder(otherBuyer, FIRST_INSTANT,
                        List.of(new ItemInput(scenario.productA().id(), 1, "1.00", "1.00")), "1.00"))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> fixture.createOrder(scenario.accounts().client(), FIRST_INSTANT,
                        List.of(new ItemInput(otherProduct.id(), 1, "1.00", "1.00")), "1.00"))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThat(counts(independent)).isEqualTo(before);
                fixture.cleanUp();
                fixture.cleanUp(); // Repeat cleanup must not erase another fixture's rows.
                assertCommittedOrder(independent, preserved);
                assertThat(counts(independent)).containsEntry("tb_order", 1L).containsEntry("tb_order_item", 1L)
                        .containsEntry("tb_product", 1L).containsEntry("tb_user_account", 1L)
                        .containsEntry("tb_user_account_role", 1L).containsEntry("tb_category", 0L)
                        .containsEntry("tb_product_category", 0L);
            }
            assertEmpty(independent);
        });
    }

    @Test
    void rollsBackTheWholeOrderWhenALineViolatesExistingDatabaseConstraints() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var independent = independent(context);
            try (var fixture = new OwnOrderQueryTestFixture(context)) {
                var buyer = fixture.prepareBuyer();
                var first = fixture.createProduct("Primeiro", BigDecimal.ONE);
                var second = fixture.createProduct("Segundo", BigDecimal.ONE);
                var before = counts(independent);
                assertThatThrownBy(() -> fixture.createOrder(buyer, FIRST_INSTANT,
                        List.of(new ItemInput(first.id(), 1, "1.00", "1.00"),
                                new ItemInput(second.id(), 0, "1.00", "0.00")), "1.00"))
                        .isInstanceOf(DataIntegrityViolationException.class);
                assertThat(counts(independent)).isEqualTo(before);
                var valid = fixture.createOrder(buyer, FIRST_INSTANT,
                        List.of(new ItemInput(first.id(), 1, "1.00", "1.00")), "1.00");
                assertCommittedOrder(independent, valid);
            }
            assertEmpty(independent);
        });
    }

    @Test
    void preparesAllRequestedItemsWithoutBorrowingThePageSizeAsAnItemLimit() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var independent = independent(context);
            try (var fixture = new OwnOrderQueryTestFixture(context)) {
                var buyer = fixture.prepareBuyer();
                var inputs = new ArrayList<ItemInput>();
                for (int index = 0; index < 51; index++) {
                    var product = fixture.createProduct("Item " + index, new BigDecimal("0.01"));
                    inputs.add(new ItemInput(product.id(), 1, "0.01", "0.01"));
                }
                var order = fixture.createOrder(buyer, FIRST_INSTANT, inputs, "0.51");
                assertThat(order.items()).hasSize(51);
                assertCommittedOrder(independent, order);
            }
            assertEmpty(independent);
        });
    }

    @Test
    void keepsDatabaseUrlStableAndSurvivesAnotherContextCreateDrop() {
        runner.run(first -> {
            assertThat(first).hasNotFailed();
            var firstUrl = first.getEnvironment().getRequiredProperty("spring.datasource.url");
            var independent = independent(first);
            try (var fixture = new OwnOrderQueryTestFixture(first)) {
                var buyer = fixture.prepareBuyer();
                var product = fixture.createProduct("Contexto preservado", BigDecimal.ONE);
                var preserved = fixture.createOrder(buyer, FIRST_INSTANT,
                        List.of(new ItemInput(product.id(), 1, "1.00", "1.00")), "1.00");
                runner.run(second -> {
                    assertThat(second).hasNotFailed();
                    var secondUrl = second.getEnvironment().getRequiredProperty("spring.datasource.url");
                    assertThat(secondUrl).isNotEqualTo(firstUrl);
                    assertContextSettings(second, secondUrl);
                    var secondJdbc = independent(second);
                    assertEmpty(secondJdbc);
                    try (var other = new OwnOrderQueryTestFixture(second)) {
                        var otherBuyer = other.prepareBuyer();
                        var otherProduct = other.createProduct("Outro contexto", BigDecimal.ONE);
                        var order = other.createOrder(otherBuyer, FIRST_INSTANT,
                                List.of(new ItemInput(otherProduct.id(), 1, "1.00", "1.00")), "1.00");
                        assertCommittedOrder(secondJdbc, order);
                        assertCommittedOrder(independent, preserved);
                    }
                }); // Closing this context issues DROP only in its own H2 database.
                assertContextSettings(first, firstUrl);
                assertCommittedOrder(independent, preserved);
            }
            assertEmpty(independent);
        });
    }

    private static JdbcTemplate independent(WebApplicationContext context) {
        return new JdbcTemplate(new DriverManagerDataSource(
                context.getEnvironment().getRequiredProperty("spring.datasource.url"), "sa", ""));
    }

    private static void assertContextSettings(WebApplicationContext context, String stableUrl) {
        assertThat(context.getEnvironment().getRequiredProperty("spring.datasource.url")).isEqualTo(stableUrl);
        assertThat(context.getBean(JdbcTemplate.class).execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getURL())).isEqualTo(stableUrl.split(";", 2)[0]);
        assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(context.getEnvironment().getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(context.getEnvironment().getProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")).isEmpty();
    }

    private static void assertRoles(JdbcTemplate jdbc, long accountId, UserRole... expected) {
        assertThat(jdbc.queryForList("select role from tb_user_account_role where user_account_id = ? "
                        + "order by role", String.class, accountId))
                .containsExactly(java.util.Arrays.stream(expected).map(Enum::name).toArray(String[]::new));
    }

    private static void assertCommittedOrder(JdbcTemplate jdbc, OrderManifest expected) {
        var row = jdbc.queryForMap("select buyer_id, created_at, status from tb_order where id = ?", expected.id());
        assertThat(((Number) row.get("buyer_id")).longValue()).isEqualTo(expected.buyerId());
        var recordedAt = jdbc.queryForObject("select created_at from tb_order where id = ?",
                (rs, index) -> rs.getTimestamp(1).toInstant(), expected.id());
        assertThat(recordedAt).isEqualTo(expected.createdAt());
        assertThat(row.get("status")).isEqualTo(expected.status());
        var itemIds = jdbc.queryForList("select id from tb_order_item where order_id = ? order by position",
                Long.class, expected.id());
        assertThat(itemIds).containsExactlyElementsOf(expected.items().stream().map(item -> item.id()).toList());
        for (var item : expected.items()) {
            var actual = jdbc.queryForMap("select product_id, position, quantity, unit_price "
                    + "from tb_order_item where id = ? and order_id = ?", item.id(), expected.id());
            assertThat(((Number) actual.get("product_id")).longValue()).isEqualTo(item.productId());
            assertThat(((Number) actual.get("position")).intValue()).isEqualTo(item.position());
            assertThat(((Number) actual.get("quantity")).intValue()).isEqualTo(item.quantity());
            assertThat((BigDecimal) actual.get("unit_price")).isEqualByComparingTo(item.unitPrice());
        }
    }

    // Private fixture assertions only; the reusable observation/database probe belongs to T004.
    private static Map<String, Long> counts(JdbcTemplate jdbc) {
        var counts = new LinkedHashMap<String, Long>();
        for (var table : List.of("tb_user_account", "tb_user_account_role", "tb_product", "tb_category",
                "tb_product_category", "tb_order", "tb_order_item")) {
            counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class));
        }
        return counts;
    }

    private static void assertEmpty(JdbcTemplate jdbc) {
        assertThat(counts(jdbc).values()).containsOnly(0L);
    }
}
