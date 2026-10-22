package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.repositories.PaymentPersistenceIntegrationTest.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** T007: sensitivity of the composed eight-table probe and selective cleanup, not HTTP settlement. */
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.h2.console.enabled=false",
        "feature008.payment-test-context=probe-t007"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(PaymentRegistrationTestConfig.class)
class PaymentRegistrationDatabaseProbeTest {

    @Autowired private WebApplicationContext context;
    private Database database;

    @BeforeEach
    void verifyIsolatedDatabaseOutsideCallerTransaction() throws SQLException {
        database = new Database(context);
    }

    @Test
    void missingSchemaNegativeControlFailsClosedInsteadOfReturningAnEmptyPaymentSnapshot() {
        // Separate empty H2 database, no DDL; this control never approves the application schema.
        var empty = new PaymentRegistrationDatabaseProbe("jdbc:h2:mem:admin-product-test-t007-empty-" + UUID.randomUUID());
        assertThatThrownBy(empty::snapshot).isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment snapshot requires the readable real tb_payment(order_id, occurred_at) schema")
                .hasNoCause();
    }

    @Test
    void realSnapshotIncludesCommittedPaymentAndTheUnchangedSevenHistoricalTables() throws SQLException {
        database.requirePaymentSchema();
        try (var fixture = database.fixture()) {
            var scenario = database.prepare(fixture);
            var before = database.previous().snapshot();
            database.insertPayment(scenario.order().id(), Database.OCCURRED_AT);
            var after = database.probe().snapshot();
            assertThat(after.payments()).containsExactly(new PaymentRow(scenario.order().id(), Database.OCCURRED_AT));
            OwnOrderQueryDatabaseProbe.assertUnchanged(before, after.previous());
            assertThatThrownBy(() -> after.payments().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(after.toString()).doesNotContain(scenario.buyer().name(), scenario.buyer().password());
            PaymentRegistrationDatabaseProbe.assertUnchanged(after, database.probe().snapshot());
        }
    }

    @Test
    void detectsAControlledPaymentOccurrenceChangeWithoutAttributingItToTheOtherTables() throws SQLException {
        database.requirePaymentSchema();
        try (var fixture = database.fixture()) {
            var scenario = database.prepare(fixture);
            database.insertPayment(scenario.order().id(), Database.OCCURRED_AT);
            var before = database.probe().snapshot();
            try {
                assertThat(database.update("update tb_payment set occurred_at = ? where order_id = ?",
                        Database.OCCURRED_AT.plusMillis(1).atOffset(ZoneOffset.UTC), scenario.order().id())).isEqualTo(1);
                var after = database.probe().snapshot();
                OwnOrderQueryDatabaseProbe.assertUnchanged(before.previous(), after.previous());
                assertThatThrownBy(() -> PaymentRegistrationDatabaseProbe.assertUnchanged(before, after))
                        .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_payment");
            } finally {
                assertThat(database.update("update tb_payment set occurred_at = ? where order_id = ?",
                        Database.OCCURRED_AT.atOffset(ZoneOffset.UTC), scenario.order().id())).isEqualTo(1);
            }
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.probe().snapshot());
        }
    }

    @ParameterizedTest(name = "eight-table probe retains sensitivity: {0}")
    @ValueSource(strings = {"tb_product", "tb_category", "tb_product_category", "tb_user_account",
            "tb_user_account_role", "tb_order", "tb_order_item"})
    void composedProbeStillDetectsEachOfTheSevenHistoricalTables(String table) throws SQLException {
        database.requirePaymentSchema();
        // Catalog fixture closes first, removing its own associations before either product cleanup.
        try (var fixture = database.fixture(); var catalog = new AdminProductTestFixture(context)) {
            var scenario = database.prepare(fixture);
            var product = catalog.createProduct("T007 catalog control", "Original", "image", new BigDecimal("12.00"));
            var category = catalog.createCategory("T007 category control");
            catalog.associate(product.id(), category.id());
            database.insertPayment(scenario.order().id(), Database.OCCURRED_AT);
            var hash = database.text("select password_hash from tb_user_account where id = ?", scenario.buyer().id());
            var before = database.probe().snapshot();
            var change = switch (table) {
                case "tb_product" -> new Mutation("update tb_product set price = ? where id = ?",
                        new Object[]{new BigDecimal("13.00"), product.id()}, "update tb_product set price = ? where id = ?",
                        new Object[]{product.price(), product.id()});
                case "tb_category" -> new Mutation("update tb_category set name = ? where id = ?",
                        new Object[]{"T007 changed category", category.id()}, "update tb_category set name = ? where id = ?",
                        new Object[]{category.name(), category.id()});
                case "tb_product_category" -> new Mutation("delete from tb_product_category where product_id = ? and category_id = ?",
                        new Object[]{product.id(), category.id()}, "insert into tb_product_category(product_id, category_id) values (?, ?)",
                        new Object[]{product.id(), category.id()});
                case "tb_user_account" -> new Mutation("update tb_user_account set password_hash = ? where id = ?",
                        new Object[]{"T007_HASH_CANARY", scenario.buyer().id()}, "update tb_user_account set password_hash = ? where id = ?",
                        new Object[]{hash, scenario.buyer().id()});
                case "tb_user_account_role" -> new Mutation("insert into tb_user_account_role(user_account_id, role) values (?, ?)",
                        new Object[]{scenario.buyer().id(), "ADMIN"}, "delete from tb_user_account_role where user_account_id = ? and role = ?",
                        new Object[]{scenario.buyer().id(), "ADMIN"});
                case "tb_order" -> new Mutation("update tb_order set created_at = ? where id = ?",
                        new Object[]{Database.CREATED_AT.plusSeconds(1).atOffset(ZoneOffset.UTC), scenario.order().id()},
                        "update tb_order set created_at = ? where id = ?",
                        new Object[]{Database.CREATED_AT.atOffset(ZoneOffset.UTC), scenario.order().id()});
                case "tb_order_item" -> new Mutation("update tb_order_item set quantity = ? where id = ?",
                        new Object[]{3, scenario.order().items().getFirst().id()}, "update tb_order_item set quantity = ? where id = ?",
                        new Object[]{2, scenario.order().items().getFirst().id()});
                default -> throw new IllegalArgumentException("Unsupported sensitivity case");
            };
            try {
                assertThat(database.update(change.sql(), change.values())).isEqualTo(1);
                var after = database.probe().snapshot();
                assertThat(after.payments()).isEqualTo(before.payments());
                var failure = catchThrowable(() -> PaymentRegistrationDatabaseProbe.assertUnchanged(before, after));
                assertThat(failure).isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: " + table);
                assertThat(failure.getMessage()).doesNotContain(hash, scenario.buyer().name(), scenario.buyer().password(), "T007_HASH_CANARY");
                assertThat(after.toString()).doesNotContain(hash, scenario.buyer().name(), scenario.buyer().password(), "T007_HASH_CANARY");
            } finally {
                assertThat(database.update(change.restoreSql(), change.original())).isEqualTo(1);
            }
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.probe().snapshot());
        }
    }

    @Test
    void cleanupAfterAnAssertionFailureWithoutPaymentPreservesUnrelatedHistoricalRows() {
        try (var unrelated = database.fixture(); var owned = database.fixture()) {
            database.prepare(unrelated);
            var before = database.previous().snapshot();
            var failure = catchThrowable(() -> {
                try (owned) {
                    database.prepare(owned);
                    throw new AssertionError("T007 deliberately failed scenario after committed setup");
                }
            });
            assertThat(failure).isInstanceOf(AssertionError.class)
                    .hasMessage("T007 deliberately failed scenario after committed setup");
            assertThat(failure.getSuppressed()).isEmpty();
            assertThat(owned.ownedOrderIds()).isEmpty();
            owned.close(); // A second cleanup is safe; the unrelated fixture remains committed.
            OwnOrderQueryDatabaseProbe.assertUnchanged(before, database.previous().snapshot());
        }
    }

    @Test
    void cleanupAfterAnAssertionFailureRemovesOnlyOwnedPaymentBeforeItsOrder() throws SQLException {
        database.requirePaymentSchema();
        try (var unrelated = database.fixture(); var owned = database.fixture()) {
            var canary = database.prepare(unrelated);
            database.insertPayment(canary.order().id(), Database.OCCURRED_AT);
            database.update("update tb_order set status = ? where id = ?", "PAID", canary.order().id());
            var before = database.probe().snapshot();
            var failure = catchThrowable(() -> {
                try (owned) {
                    var target = database.prepare(owned);
                    database.insertPayment(target.order().id(), Database.OCCURRED_AT.plusMillis(1));
                    database.update("update tb_order set status = ? where id = ?", "PAID", target.order().id());
                    throw new AssertionError("T007 deliberately failed scenario after committed payment setup");
                }
            });
            assertThat(failure).isInstanceOf(AssertionError.class)
                    .hasMessage("T007 deliberately failed scenario after committed payment setup");
            assertThat(failure.getSuppressed()).isEmpty();
            assertThat(owned.ownedOrderIds()).isEmpty();
            owned.close();
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.probe().snapshot());
            assertThat(database.probe().snapshot().payments())
                    .containsExactly(new PaymentRow(canary.order().id(), Database.OCCURRED_AT));
        }
    }

    private record Mutation(String sql, Object[] values, String restoreSql, Object[] original) { }
}
