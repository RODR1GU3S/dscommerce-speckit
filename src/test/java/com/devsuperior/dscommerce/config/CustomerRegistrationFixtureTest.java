package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.devsuperior.dscommerce.config.CustomerRegistrationSupportTest.assertEmpty;
import static com.devsuperior.dscommerce.config.CustomerRegistrationSupportTest.isolatedRunner;
import static com.devsuperior.dscommerce.config.CustomerRegistrationSupportTest.probe;
import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T010 authorship: committed fixture data and selective cleanup, not signup/login endpoint tests. */
class CustomerRegistrationFixtureTest {

    // Independent fixed crypt_blowfish vectors; assertions compare secrets as booleans to avoid printing them.
    private static final String SHORT_PASSWORD = "U*U";
    private static final String SHORT_HASH = "$2a$05$CCCCCCCCCCCCCCCCCCCCC.E5YPO9kmyuRGyh0XouQYb4YMJKvyOeW";
    private static final String LONG_PASSWORD = "0123456789abcdefghijklmnopqrstuvwxyz"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789chars after 72 are ignored";
    private static final String LONG_HASH = "$2a$05$abcdefghijklmnopqrstuu5s2v8.iXieOjg/.AySBTTZIIVFJeBui";

    @Test
    void scenarioIsCommittedOnIndependentConnectionsWithFixedCredentialsAndCoherentHistory() {
        isolatedRunner().run(context -> {
            assertThat(context).hasNotFailed();
            var database = probe(context);
            assertEmpty(database.snapshot());
            assertOutsideTransaction();
            try (var fixture = new CustomerRegistrationTestFixture(context)) {
                var scenario = fixture.prepareScenario();
                assertOutsideTransaction();
                var ownership = fixture.ownership();
                assertThat(ownership.accountIds()).hasSize(4);
                assertThat(ownership.categoryIds()).containsExactly(scenario.category().id());
                assertThat(ownership.productIds()).hasSize(2);
                assertThat(ownership.productCategories()).hasSize(2);
                assertThat(ownership.orderIds()).containsExactlyInAnyOrder(scenario.waiting().id(), scenario.paid().id());
                assertThat(ownership.itemIds()).hasSize(3);
                var encoder = context.getBean(PasswordEncoder.class);
                try (var connection = database.openConnection()) {
                    assertAccount(connection, encoder, scenario.access().client(), SHORT_PASSWORD, SHORT_HASH, Set.of("CLIENT"));
                    assertAccount(connection, encoder, scenario.access().admin(), LONG_PASSWORD, LONG_HASH, Set.of("ADMIN"));
                    assertAccount(connection, encoder, scenario.access().both(), LONG_PASSWORD, LONG_HASH, Set.of("ADMIN", "CLIENT"));
                    assertAccount(connection, encoder, scenario.access().none(), SHORT_PASSWORD, SHORT_HASH, Set.of());
                    assertThat(scenario.access().client().password().codePointCount(0,
                            scenario.access().client().password().length())).isEqualTo(3);
                    assertThat(scenario.access().admin().password().getBytes(StandardCharsets.UTF_8).length).isEqualTo(98);
                    assertOrder(connection, scenario.waiting().id(), scenario.access().client().id(), "WAITING_PAYMENT", "42.50");
                    assertOrder(connection, scenario.paid().id(), scenario.access().both().id(), "PAID", "7.50");
                    try (var statement = connection.prepareStatement("select order_id, occurred_at from tb_payment")) {
                        try (var rows = statement.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getLong("order_id")).isEqualTo(scenario.paid().id());
                            assertThat(rows.getTimestamp("occurred_at").toInstant())
                                    .isEqualTo(Instant.parse("2026-01-03T10:00:00.456Z"));
                            assertThat(rows.next()).isFalse();
                        }
                    }
                    try (var statement = connection.prepareStatement("select price from tb_product where id = ?")) {
                        statement.setLong(1, scenario.products().getFirst().id());
                        try (var rows = statement.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("99.00");
                            assertThat(rows.next()).isFalse();
                        }
                    }
                }
                // Columns introduced later are checked only if actually present; no absent-column SQL preparation.
                var committed = database.snapshot();
                var accounts = committed.tables().get("TB_USER_ACCOUNT");
                Map.of("TB_USER_ACCOUNT", 4, "TB_USER_ACCOUNT_ROLE", 4, "TB_CATEGORY", 1,
                        "TB_PRODUCT", 2, "TB_PRODUCT_CATEGORY", 2, "TB_ORDER", 2,
                        "TB_ORDER_ITEM", 3, "TB_PAYMENT", 1).forEach((table, count) ->
                        assertThat(committed.tables().get(table).rows()).as("committed fixture rows in %s", table).hasSize(count));
                for (var row : accounts.rows()) {
                    for (var column : List.of("FULL_NAME", "EMAIL", "EMAIL_KEY", "PHONE", "BIRTH_DATE")) {
                        if (row.values().containsKey(column)) {
                            assertThat(row.values().get(column).isNull()).as("legacy fixture leaves %s absent", column).isTrue();
                        }
                    }
                }
            }
            assertOutsideTransaction();
            assertEmpty(database.snapshot());
        });
    }

    @Test
    void cleanupRemovesOnlyOwnedIdsAndKeepsAnotherCompleteScenarioWithTheSameCatalogNames() {
        isolatedRunner().run(context -> {
            assertThat(context).hasNotFailed();
            var database = probe(context);
            try (var survivor = new CustomerRegistrationTestFixture(context)) {
                survivor.prepareScenario();
                var preserved = database.snapshot();
                try (var owned = new CustomerRegistrationTestFixture(context)) {
                    var disposable = owned.prepareScenario();
                    var captured = owned.ownership();
                    assertThat(captured.accountIds()).hasSize(4);
                    assertThat(disposable.products()).hasSize(2);
                    assertThat(database.snapshot().tables().get("TB_USER_ACCOUNT").rows()).hasSize(8);
                    assertThatThrownBy(() -> captured.accountIds().clear()).isInstanceOf(UnsupportedOperationException.class);
                    owned.cleanUp();
                    assertOutsideTransaction();
                    assertUnchanged(preserved, database.snapshot());
                    assertThat(owned.ownership().accountIds()).isEmpty();
                    assertThat(owned.ownership().categoryIds()).isEmpty();
                    assertThat(owned.ownership().productIds()).isEmpty();
                    assertThat(owned.ownership().productCategories()).isEmpty();
                    assertThat(owned.ownership().orderIds()).isEmpty();
                    assertThat(owned.ownership().itemIds()).isEmpty();
                    assertThat(captured.accountIds()).hasSize(4); // Previously returned manifest remains immutable.
                    owned.cleanUp();
                    assertUnchanged(preserved, database.snapshot());
                }
                assertUnchanged(preserved, database.snapshot());
            }
            assertEmpty(database.snapshot());
        });
    }

    @Test
    void fixtureConstructionSetupAndCleanupRejectAnEnclosingCallerTransaction() {
        isolatedRunner().run(context -> {
            assertThat(context).hasNotFailed();
            var database = probe(context);
            var before = database.snapshot();
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            try (var fixture = new CustomerRegistrationTestFixture(context)) {
                transaction.executeWithoutResult(status -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThatThrownBy(() -> new CustomerRegistrationTestFixture(context))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Customer fixture setup/cleanup requires no caller transaction");
                    assertThatThrownBy(() -> fixture.createAccount("T010MustNotBeWritten", LegacyCredential.SHORT,
                            Set.of(UserRole.CLIENT))).isInstanceOf(IllegalStateException.class)
                            .hasMessage("Customer fixture setup/cleanup requires no caller transaction");
                    assertThatThrownBy(fixture::cleanUp).isInstanceOf(IllegalStateException.class)
                            .hasMessage("Customer fixture setup/cleanup requires no caller transaction");
                });
                assertOutsideTransaction();
                assertUnchanged(before, database.snapshot());
                assertThat(context.getBean(JdbcTemplate.class).queryForObject(
                        "select count(*) from tb_user_account", Long.class)).isZero();
            }
            assertUnchanged(before, database.snapshot());
        });
    }

    private static void assertAccount(Connection connection, PasswordEncoder encoder, Account account,
                                      String expectedPassword, String expectedHash, Set<String> expectedRoles) throws SQLException {
        assertThat(expectedPassword.equals(account.password())).as("fixture preserves the fixed credential").isTrue();
        assertThat(expectedHash.equals(account.passwordHash())).as("fixture preserves the fixed BCrypt bytes").isTrue();
        try (var statement = connection.prepareStatement("select name, password_hash from tb_user_account where id = ?")) {
            statement.setLong(1, account.id());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(account.name().equals(rows.getString("name"))).as("committed account name is unchanged").isTrue();
                var storedHash = rows.getString("password_hash");
                assertThat(expectedHash.equals(storedHash)).as("stored hash matches the independent fixed vector").isTrue();
                assertThat(encoder.matches(expectedPassword, storedHash)).as("fixed BCrypt credential verifies").isTrue();
                assertThat(rows.next()).isFalse();
            }
        }
        try (var statement = connection.prepareStatement(
                "select role from tb_user_account_role where user_account_id = ? order by role")) {
            statement.setLong(1, account.id());
            var observed = new ArrayList<String>();
            try (var rows = statement.executeQuery()) { while (rows.next()) { observed.add(rows.getString(1)); } }
            assertThat(observed).containsExactlyInAnyOrderElementsOf(expectedRoles);
        }
    }

    private static void assertOrder(Connection connection, long orderId, long buyerId,
                                    String expectedStatus, String expectedTotal) throws SQLException {
        try (var statement = connection.prepareStatement("select buyer_id, status, created_at from tb_order where id = ?")) {
            statement.setLong(1, orderId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("buyer_id")).isEqualTo(buyerId);
                assertThat(rows.getString("status")).isEqualTo(expectedStatus);
                assertThat(rows.getTimestamp("created_at").toInstant()).isEqualTo(Instant.parse("2026-01-02T10:00:00.123Z"));
                assertThat(rows.next()).isFalse();
            }
        }
        try (var statement = connection.prepareStatement(
                "select sum(quantity * unit_price) from tb_order_item where order_id = ?")) {
            statement.setLong(1, orderId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo(new BigDecimal(expectedTotal));
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private static void assertOutsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
}
