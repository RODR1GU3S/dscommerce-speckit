package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.Scenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static com.devsuperior.dscommerce.config.CustomerRegistrationSupportTest.assertEmpty;
import static com.devsuperior.dscommerce.config.CustomerRegistrationSupportTest.assertRuntime;
import static com.devsuperior.dscommerce.config.CustomerRegistrationSupportTest.isolatedRunner;
import static com.devsuperior.dscommerce.config.CustomerRegistrationSupportTest.probe;
import static com.devsuperior.dscommerce.config.CustomerRegistrationSupportTest.sessionId;
import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T010 authorship: real JDBC oracles, with no future registration columns or normalization helper. */
class CustomerRegistrationProbeTest {

    private static final List<String> TABLES = List.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE", "TB_CATEGORY",
            "TB_PRODUCT", "TB_PRODUCT_CATEGORY", "TB_ORDER", "TB_ORDER_ITEM", "TB_PAYMENT");

    @Test
    void metadataComesFromNewPhysicalConnectionsAndListsTheEightRealTables() {
        isolatedRunner().run(context -> {
            assertThat(context).hasNotFailed();
            var database = probe(context);
            var url = context.getEnvironment().getRequiredProperty("spring.datasource.url");
            try (var first = database.openConnection(); var second = database.openConnection()) {
                assertRuntime(first, url);
                assertRuntime(second, url);
                long firstSession = sessionId(first);
                long secondSession = sessionId(second);
                assertThat(firstSession).isNotEqualTo(secondSession);
                var metadata = database.metadata();
                assertThat(metadata.connection().databaseProduct()).isEqualTo("H2");
                assertThat(metadata.connection().databaseVersion()).startsWith("2.2.224");
                assertThat(metadata.connection().driverVersion()).startsWith("2.2.224");
                assertThat(metadata.connection().isolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
                // metadata() deliberately reads inside its own transaction; openConnection() above observes true.
                assertThat(metadata.connection().autoCommit()).isFalse();
                assertThat(metadata.connection().sessionId()).isNotIn(firstSession, secondSession);
                assertThat(metadata.tables().keySet()).containsExactlyInAnyOrderElementsOf(TABLES);
                metadata.tables().forEach((table, details) -> assertThat(details.columns()).as("columns of %s", table).isNotEmpty());
                assertThat(metadata.tables().get("TB_USER_ACCOUNT").primaryKeys())
                        .extracting(CustomerRegistrationDatabaseProbe.PrimaryKeyColumn::column).contains("ID");
                assertThat(metadata.tables().get("TB_USER_ACCOUNT_ROLE").foreignKeys())
                        .extracting(CustomerRegistrationDatabaseProbe.ForeignKeyColumn::referencedTable).contains("TB_USER_ACCOUNT");
                assertThat(metadata.tables().get("TB_ORDER_ITEM").foreignKeys())
                        .extracting(CustomerRegistrationDatabaseProbe.ForeignKeyColumn::referencedTable)
                        .contains("TB_ORDER", "TB_PRODUCT");
            }
        });
    }

    @Test
    void snapshotInsideAnApplicationTransactionStillReadsIndependentlyCommittedState() {
        isolatedRunner().run(context -> {
            assertThat(context).hasNotFailed();
            var database = probe(context);
            try (var fixture = new CustomerRegistrationTestFixture(context)) {
                var scenario = fixture.prepareScenario();
                var before = database.snapshot();
                var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                transaction.executeWithoutResult(status -> {
                    assertThat(context.getBean(JdbcTemplate.class).update(
                            "update tb_category set name = ? where id = ?", "T010 uncommitted control", scenario.category().id()))
                            .isEqualTo(1);
                    assertUnchanged(before, database.snapshot()); // No DataSourceUtils/JPA cache reuse in the probe.
                    status.setRollbackOnly();
                });
                assertUnchanged(before, database.snapshot());
            }
            assertEmpty(database.snapshot());
        });
    }

    @ParameterizedTest(name = "snapshot sensitivity on {0}")
    @ValueSource(strings = {"TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE", "TB_CATEGORY", "TB_PRODUCT",
            "TB_PRODUCT_CATEGORY", "TB_ORDER", "TB_ORDER_ITEM", "TB_PAYMENT"})
    void snapshotDetectsOneControlledCommittedChangeInEachTableAndThenItsRestoration(String table) {
        isolatedRunner().run(context -> {
            assertThat(context).hasNotFailed();
            var database = probe(context);
            try (var fixture = new CustomerRegistrationTestFixture(context)) {
                var scenario = fixture.prepareScenario();
                var before = database.snapshot();
                var mutation = mutation(table, scenario);
                boolean changed = false;
                try {
                    int affected = update(database, mutation.changeSql(), mutation.changeValues());
                    changed = affected > 0;
                    assertThat(affected).isEqualTo(1);
                    var after = database.snapshot();
                    assertThatThrownBy(() -> assertUnchanged(before, after)).isInstanceOf(AssertionError.class)
                            .hasMessage("Observed database state changed: " + table).hasNoCause();
                    for (var other : TABLES) {
                        assertThat(before.tables().get(other).equals(after.tables().get(other)))
                                .as("only the selected table changes: %s", other).isEqualTo(!other.equals(table));
                    }
                } finally {
                    if (changed) {
                        assertThat(update(database, mutation.restoreSql(), mutation.restoreValues())).isEqualTo(1);
                    }
                }
                assertUnchanged(before, database.snapshot());
            }
            assertEmpty(database.snapshot());
        });
    }

    @Test
    void snapshotsAreDetachedImmutableAndDoNotPrintStoredCredentialsInDiagnostics() {
        isolatedRunner().run(context -> {
            assertThat(context).hasNotFailed();
            var database = probe(context);
            try (var fixture = new CustomerRegistrationTestFixture(context)) {
                var scenario = fixture.prepareScenario();
                var snapshot = database.snapshot();
                assertThat(snapshot.tables().keySet()).containsExactlyInAnyOrderElementsOf(TABLES);
                var accounts = snapshot.tables().get("TB_USER_ACCOUNT");
                assertThat(accounts.rows()).hasSize(4);
                var row = accounts.rows().stream().filter(candidate -> ((Number) candidate.values().get("ID").value())
                        .longValue() == scenario.access().client().id()).findFirst().orElseThrow();
                var hash = row.values().get("PASSWORD_HASH");
                assertThat(scenario.access().client().passwordHash().equals(hash.value()))
                        .as("snapshot retains the committed hash without printing it").isTrue();
                assertThatThrownBy(() -> snapshot.tables().clear()).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> accounts.columns().clear()).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> accounts.rows().clear()).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> row.values().clear()).isInstanceOf(UnsupportedOperationException.class);
                var diagnostics = snapshot + " " + accounts + " " + row + " " + hash;
                for (var account : List.of(scenario.access().client(), scenario.access().admin(),
                        scenario.access().both(), scenario.access().none())) {
                    assertThat(diagnostics.contains(account.password())).as("no plaintext in snapshot diagnostics").isFalse();
                    assertThat(diagnostics.contains(account.passwordHash())).as("no hash in snapshot diagnostics").isFalse();
                    assertThat(diagnostics.contains(account.name())).as("no account name in snapshot diagnostics").isFalse();
                }
                fixture.cleanUp();
                assertEmpty(database.snapshot());
                assertThat(snapshot.tables().get("TB_USER_ACCOUNT").rows()).hasSize(4); // No live result set/cache view.
                assertThat(hash.value().equals(scenario.access().client().passwordHash())).isTrue();
            }
        });
    }

    private static int update(CustomerRegistrationDatabaseProbe database, String sql, Object[] values) throws SQLException {
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) { statement.setObject(i + 1, values[i]); }
                int count = statement.executeUpdate();
                connection.commit();
                return count;
            } catch (SQLException | RuntimeException | Error failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static Mutation mutation(String table, Scenario scenario) {
        return switch (table) {
            case "TB_USER_ACCOUNT" -> new Mutation("update tb_user_account set password_hash = ? where id = ?",
                    new Object[]{"$2a$05$abcdefghijklmnopqrstuu5s2v8.iXieOjg/.AySBTTZIIVFJeBui", scenario.access().client().id()},
                    "update tb_user_account set password_hash = ? where id = ?",
                    new Object[]{"$2a$05$CCCCCCCCCCCCCCCCCCCCC.E5YPO9kmyuRGyh0XouQYb4YMJKvyOeW", scenario.access().client().id()});
            case "TB_USER_ACCOUNT_ROLE" -> new Mutation("insert into tb_user_account_role (user_account_id, role) values (?, ?)",
                    new Object[]{scenario.access().client().id(), "ADMIN"},
                    "delete from tb_user_account_role where user_account_id = ? and role = ?",
                    new Object[]{scenario.access().client().id(), "ADMIN"});
            case "TB_CATEGORY" -> new Mutation("update tb_category set name = ? where id = ?",
                    new Object[]{"T010 controlled category change", scenario.category().id()},
                    "update tb_category set name = ? where id = ?", new Object[]{scenario.category().name(), scenario.category().id()});
            case "TB_PRODUCT" -> new Mutation("update tb_product set price = ? where id = ?",
                    new Object[]{new BigDecimal("100.00"), scenario.products().getFirst().id()},
                    "update tb_product set price = ? where id = ?",
                    new Object[]{new BigDecimal("99.00"), scenario.products().getFirst().id()});
            case "TB_PRODUCT_CATEGORY" -> new Mutation("delete from tb_product_category where product_id = ? and category_id = ?",
                    new Object[]{scenario.products().getFirst().id(), scenario.category().id()},
                    "insert into tb_product_category (product_id, category_id) values (?, ?)",
                    new Object[]{scenario.products().getFirst().id(), scenario.category().id()});
            case "TB_ORDER" -> new Mutation("update tb_order set created_at = ? where id = ?",
                    new Object[]{Timestamp.from(Instant.parse("2026-01-02T10:00:00.124Z")), scenario.waiting().id()},
                    "update tb_order set created_at = ? where id = ?",
                    new Object[]{Timestamp.from(Instant.parse("2026-01-02T10:00:00.123Z")), scenario.waiting().id()});
            case "TB_ORDER_ITEM" -> new Mutation("update tb_order_item set quantity = ? where id = ?",
                    new Object[]{4, scenario.waiting().items().getFirst().id()},
                    "update tb_order_item set quantity = ? where id = ?", new Object[]{3, scenario.waiting().items().getFirst().id()});
            case "TB_PAYMENT" -> new Mutation("update tb_payment set occurred_at = ? where order_id = ?",
                    new Object[]{Timestamp.from(Instant.parse("2026-01-03T10:00:00.457Z")), scenario.paid().id()},
                    "update tb_payment set occurred_at = ? where order_id = ?",
                    new Object[]{Timestamp.from(Instant.parse("2026-01-03T10:00:00.456Z")), scenario.paid().id()});
            default -> throw new IllegalArgumentException("Unknown controlled table");
        };
    }

    private record Mutation(String changeSql, Object[] changeValues, String restoreSql, Object[] restoreValues) {
        @Override public String toString() { return "ControlledMutation[SQL/values=omitted]"; }
    }
}
