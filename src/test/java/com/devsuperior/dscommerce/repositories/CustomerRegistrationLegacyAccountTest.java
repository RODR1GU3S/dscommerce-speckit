package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import com.devsuperior.dscommerce.security.AccountPasswordVerifier;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * T088 authorship only; T092 owns compilation, provider discovery and executable diagnosis.
 * Direct JPA/repository/JDBC observations, no HTTP server or registration-flow replacement.
 */
@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "feature009.customer-registration-legacy-test-context=accounts")
@ActiveProfiles("test")
@ContextConfiguration(initializers = CustomerRegistrationTestConfig.class)
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationLegacyAccountTest {

    private static final String PRESENT_EMAIL = "Legacy.Reserved@Example.test";
    // Independent literals: neither the fixture nor an expected key calls the production normalizer.
    private static final byte[] PRESENT_KEY = "legacy.reserved@example.test".getBytes(StandardCharsets.UTF_8);
    private static final String DISTINCT_EMAIL = "Other.Legacy@Example.test";
    private static final byte[] DISTINCT_KEY = "other.legacy@example.test".getBytes(StandardCharsets.UTF_8);

    @Autowired private ConfigurableApplicationContext context;
    @Autowired private EntityManagerFactory entityManagers;
    @Autowired private UserAccountRepository accounts;
    @Autowired private AccountPasswordVerifier verifier;

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("legacyAccounts")
    void legacyConstructorKeepsSuppliedIdExactNameHashRolesAndNullPersonalData(LegacyVector vector) {
        long suppliedId = 9123L;
        String name = legacyName();
        var account = new UserAccount(suppliedId, name, vector.credential().passwordHash());
        assertThat(account.getId()).isEqualTo(suppliedId);
        assertThat(account.getRoles()).as("legacy constructor assigns no new default profile").isEmpty();
        account.getRoles().addAll(vector.roles());
        assertAccount(account, suppliedId, name, vector, false);
        assertCredentialOutsideRegistrationLimits(vector.credential());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("legacyAccounts")
    void committedLegacyAccountsKeepGeneratedIdsNamesHashesRolesAndTheirOriginalCredentials(LegacyVector vector)
            throws SQLException {
        assertOutsideTransaction();
        var database = database();
        try (var fixture = new CustomerRegistrationTestFixture(context); var owned = new OwnedAccounts()) {
            fixture.prepareScenario();
            var before = database.snapshot();
            String name = legacyName();
            long id = owned.persist(name, vector);
            assertThat(id).isPositive();
            var committed = database.snapshot();
            assertOnlyOwnedAccountsAdded(before, committed, Set.of(id), vector.roles().size());
            assertStoredAccount(id, name, vector, false);
            assertThat(accounts.findByName(name).orElseThrow().getId()).isEqualTo(id);
            assertThat(accounts.findByName(name.toLowerCase(java.util.Locale.ROOT))).isEmpty();
            assertCredentialOutsideRegistrationLimits(vector.credential());

            var loaded = accounts.findByIdWithRoles(id).orElseThrow();
            assertThat(verifier.matches(vector.credential().password(), loaded.getPasswordHash()))
                    .as("fixed historical credential remains usable without signup limits or a new hash").isTrue();
            assertThat(verifier.matches("Wrong-legacy-credential-009", loaded.getPasswordHash()))
                    .as("an unrelated password does not authenticate").isFalse();
            assertStoredAccount(id, name, vector, false);
            assertUnchanged(committed, database.snapshot());
        }
    }

    @Test
    void severalCommittedNullEmailsRemainAbsentAcrossAllFourExistingRoleSets() throws SQLException {
        assertOutsideTransaction();
        var database = database();
        try (var fixture = new CustomerRegistrationTestFixture(context); var owned = new OwnedAccounts()) {
            fixture.prepareScenario();
            var before = database.snapshot();
            var ids = new LinkedHashSet<Long>();
            for (var vector : roleControls()) {
                String name = legacyName();
                long id = owned.persist(name, vector);
                assertThat(ids.add(id)).as("separate generated identity for every legacy account").isTrue();
                assertStoredAccount(id, name, vector, false);
            }
            assertThat(ids).hasSize(4);
            assertOnlyOwnedAccountsAdded(before, database.snapshot(), ids, 4);
        }
    }

    @Test
    void exactCaseNamesKeepDifferentPrimaryKeysWithoutApplyingSignupNormalization() throws SQLException {
        assertOutsideTransaction();
        var database = database();
        try (var fixture = new CustomerRegistrationTestFixture(context); var owned = new OwnedAccounts()) {
            fixture.prepareScenario();
            var before = database.snapshot();
            var upperVector = new LegacyVector("ADMIN", LegacyCredential.LONG, Set.of(UserRole.ADMIN));
            var lowerVector = new LegacyVector("NONE", LegacyCredential.SHORT, Set.of());
            long upper = owned.persist("Ana", upperVector);
            long lower = owned.persist("ana", lowerVector);
            assertThat(upper).isPositive().isNotEqualTo(lower);
            assertThat(accounts.findByName("Ana").orElseThrow().getId()).isEqualTo(upper);
            assertThat(accounts.findByName("ana").orElseThrow().getId()).isEqualTo(lower);
            assertThat(accounts.findByName("ANA")).isEmpty();
            assertStoredAccount(upper, "Ana", upperVector, false);
            assertStoredAccount(lower, "ana", lowerVector, false);
            assertOnlyOwnedAccountsAdded(before, database.snapshot(), Set.of(upper, lower), 1);
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("legacyAccounts")
    void presentLegacyEmailRejectsACompetingKeyWithoutChangingEitherAccount(LegacyVector vector)
            throws SQLException {
        assertOutsideTransaction();
        var database = database();
        try (var fixture = new CustomerRegistrationTestFixture(context); var owned = new OwnedAccounts()) {
            fixture.prepareScenario();
            var before = database.snapshot();
            String reservedName = legacyName();
            String otherName = legacyName();
            long reservedId = owned.persist(reservedName, vector);
            long otherId = owned.persist(otherName, vector);
            writeEmail(reservedId, PRESENT_EMAIL, PRESENT_KEY);
            assertStoredAccount(reservedId, reservedName, vector, true);
            assertStoredAccount(otherId, otherName, vector, false);
            assertThat(accounts.findByEmailKey(PRESENT_KEY.clone()).orElseThrow().getId()).isEqualTo(reservedId);
            var reserved = database.snapshot();

            assertDuplicateKeyRolledBack(otherId);
            assertUnchanged(reserved, database.snapshot());
            assertStoredAccount(reservedId, reservedName, vector, true);
            assertStoredAccount(otherId, otherName, vector, false);
            assertThat(accounts.findByEmailKey(PRESENT_KEY.clone()).orElseThrow().getId()).isEqualTo(reservedId);

            // Same UPDATE with a distinct key must succeed: failure is not a blanket ban on legacy email.
            writeEmail(otherId, DISTINCT_EMAIL, DISTINCT_KEY);
            assertThat(accounts.findByEmailKey(DISTINCT_KEY.clone()).orElseThrow().getId()).isEqualTo(otherId);
            assertStoredAccount(reservedId, reservedName, vector, true);
            assertAccount(accounts.findByIdWithRoles(otherId).orElseThrow(), otherId, otherName, vector, false, true);
            assertOnlyOwnedAccountsAdded(before, database.snapshot(), Set.of(reservedId, otherId),
                    vector.roles().size() * 2);
        }
    }

    @Test
    void existingPrimaryKeysAndBuyerReferencesRemainAttachedToCommittedLegacyAccounts() throws SQLException {
        assertOutsideTransaction();
        var database = database();
        try (var fixture = new CustomerRegistrationTestFixture(context)) {
            var scenario = fixture.prepareScenario();
            var before = database.snapshot();
            var table = database.metadata().tables().get("TB_USER_ACCOUNT");
            assertThat(table.primaryKeys().stream().map(key -> key.column()).toList()).containsExactly("ID");
            var idColumn = table.columns().stream().filter(column -> column.name().equals("ID")).findFirst().orElseThrow();
            assertThat(idColumn.jdbcType()).isEqualTo(Types.BIGINT);
            assertThat(idColumn.autoIncrement()).isEqualToIgnoringCase("YES");
            for (var legacy : List.of(scenario.access().client(), scenario.access().admin(),
                    scenario.access().both(), scenario.access().none())) {
                assertStoredAccount(legacy.id(), legacy.name(),
                        new LegacyVector("GUARD", legacy.credential(), legacy.roles()), false);
            }
            try (var connection = database.openConnection(); var statement = connection.prepareStatement(
                    "select buyer_id from tb_order where id = ?")) {
                for (var order : List.of(scenario.waiting(), scenario.paid())) {
                    statement.setLong(1, order.id());
                    try (var rows = statement.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getLong(1)).as("existing order still references its original buyer ID")
                                .isEqualTo(order.buyerId());
                        assertThat(rows.next()).isFalse();
                    }
                }
            }
            assertUnchanged(before, database.snapshot());
        }
    }

    static Stream<LegacyVector> legacyAccounts() {
        return Stream.of(LegacyCredential.SHORT, LegacyCredential.LONG).flatMap(credential ->
                roleControls().stream().map(vector -> new LegacyVector(vector.label(), credential, vector.roles())));
    }

    private static List<LegacyVector> roleControls() {
        return List.of(new LegacyVector("NONE", LegacyCredential.SHORT, Set.of()),
                new LegacyVector("CLIENT", LegacyCredential.SHORT, Set.of(UserRole.CLIENT)),
                new LegacyVector("ADMIN", LegacyCredential.LONG, Set.of(UserRole.ADMIN)),
                new LegacyVector("BOTH", LegacyCredential.LONG, Set.of(UserRole.CLIENT, UserRole.ADMIN)));
    }

    private static void assertCredentialOutsideRegistrationLimits(LegacyCredential credential) {
        int points = credential.password().codePointCount(0, credential.password().length());
        int bytes = credential.password().getBytes(StandardCharsets.UTF_8).length;
        assertThat(credential == LegacyCredential.SHORT ? points == 3 && bytes == 3 : points == 98 && bytes == 98)
                .as("independent historical vectors below 12 points or above 64 UTF-8 bytes").isTrue();
    }

    private void assertStoredAccount(long id, String name, LegacyVector vector, boolean presentEmail)
            throws SQLException {
        assertAccount(accounts.findByIdWithRoles(id).orElseThrow(), id, name, vector, presentEmail);
        try (var connection = database().openConnection(); var statement = connection.prepareStatement(
                "select id, name, password_hash, full_name, email, email_key, phone, birth_date "
                        + "from tb_user_account where id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                String hash = rows.getString("PASSWORD_HASH");
                assertThat(rows.getLong("ID") == id && name.equals(rows.getString("NAME"))
                        && vector.credential().passwordHash().equals(hash)
                        && Arrays.equals(vector.credential().passwordHash().getBytes(StandardCharsets.UTF_8),
                        hash == null ? null : hash.getBytes(StandardCharsets.UTF_8)))
                        .as("independent JDBC identity/name/hash bytes, without printing secret values").isTrue();
                assertThat(rows.getObject("FULL_NAME") == null && rows.getObject("PHONE") == null
                        && rows.getObject("BIRTH_DATE") == null).as("no invented legacy personal data").isTrue();
                assertThat(presentEmail
                        ? PRESENT_EMAIL.equals(rows.getString("EMAIL")) && Arrays.equals(PRESENT_KEY, rows.getBytes("EMAIL_KEY"))
                        : rows.getObject("EMAIL") == null && rows.getObject("EMAIL_KEY") == null)
                        .as("explicit email fixture or genuine NULL absence").isTrue();
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private static void assertAccount(UserAccount account, long id, String name, LegacyVector vector, boolean presentEmail) {
        assertAccount(account, id, name, vector, presentEmail, false);
    }

    private static void assertAccount(UserAccount account, long id, String name, LegacyVector vector,
                                      boolean presentEmail, boolean distinctEmail) {
        assertThat(account.getId()).isEqualTo(id);
        assertThat(name.equals(account.getName()) && vector.credential().passwordHash().equals(account.getPasswordHash()))
                .as("legacy constructor and JPA round-trip preserve exact name and fixed hash").isTrue();
        assertThat(account.getRoles()).containsExactlyInAnyOrderElementsOf(vector.roles());
        assertThat(account.getFullName() == null && account.getPhone() == null && account.getBirthDate() == null)
                .as("new signup requirements never fill legacy personal fields").isTrue();
        String email = distinctEmail ? DISTINCT_EMAIL : PRESENT_EMAIL;
        byte[] key = distinctEmail ? DISTINCT_KEY : PRESENT_KEY;
        assertThat(presentEmail || distinctEmail
                ? email.equals(account.getEmail()) && Arrays.equals(key, account.getEmailKey())
                : account.getEmail() == null && account.getEmailKey() == null)
                .as("only the explicitly prepared email is present").isTrue();
    }

    private void writeEmail(long id, String email, byte[] key) throws SQLException {
        try (var connection = database().openConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("update tb_user_account set email = ?, email_key = ? where id = ?")) {
                statement.setString(1, email);
                statement.setBytes(2, key);
                statement.setLong(3, id);
                assertThat(statement.executeUpdate()).isEqualTo(1);
                connection.commit();
            } catch (SQLException | RuntimeException | Error failure) {
                connection.rollback();
                throw new AssertionError("Explicit legacy email fixture preparation failed");
            }
        }
    }

    private void assertDuplicateKeyRolledBack(long otherId) throws SQLException {
        boolean rejected = false;
        try (var connection = database().openConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("update tb_user_account set email = ?, email_key = ? where id = ?")) {
                statement.setString(1, "LEGACY.RESERVED@EXAMPLE.TEST");
                statement.setBytes(2, PRESENT_KEY.clone());
                statement.setLong(3, otherId);
                try { statement.executeUpdate(); }
                catch (SQLException failure) {
                    rejected = "23505".equals(failure.getSQLState()) && failure.getErrorCode() == 23505;
                }
            } finally { connection.rollback(); } // Even unexpected acceptance never contaminates the comparison.
        }
        assertThat(rejected).as("real email-key constraint rejects the competing literal key").isTrue();
    }

    private static void assertOnlyOwnedAccountsAdded(Snapshot before, Snapshot after, Set<Long> ids, int roleRows) {
        before.tables().forEach((table, previous) -> {
            var current = after.tables().get(table);
            if (!Set.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE").contains(table)) {
                assertThat(previous.equals(current)).as("unchanged legacy catalog/financial table %s", table).isTrue();
            } else {
                String column = table.equals("TB_USER_ACCOUNT") ? "ID" : "USER_ACCOUNT_ID";
                int added = table.equals("TB_USER_ACCOUNT") ? ids.size() : roleRows;
                assertThat(previous.columns().equals(current.columns())).isTrue();
                assertThat(current.rows().size()).isEqualTo(previous.rows().size() + added);
                assertThat(current.rows().stream().filter(row -> !ids.contains(id(row, column))).toList()
                        .equals(previous.rows())).as("all previous IDs, fields, hashes and roles remain unchanged").isTrue();
            }
        });
    }

    private CustomerRegistrationDatabaseProbe database() {
        return new CustomerRegistrationDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
    }

    private static long id(Row row, String column) { return ((Number) row.values().get(column).value()).longValue(); }

    private static String legacyName() {
        String prefix = "_Legacy." + UUID.randomUUID().toString().replace("-", "") + ".";
        return prefix + "L".repeat(255 - prefix.length());
    }

    private static void assertOutsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive())
                .as("no enclosing test transaction hides the actual commit or rollback").isFalse();
    }

    record LegacyVector(String label, LegacyCredential credential, Set<UserRole> roles) {
        @Override public String toString() { return label + "-" + credential.name(); }
    }

    /** Track only this test's generated accounts; fixture guards have their own FK-ordered owner. */
    private final class OwnedAccounts implements AutoCloseable {
        private final Set<Long> ids = new LinkedHashSet<>();

        long persist(String name, LegacyVector vector) {
            var manager = entityManagers.createEntityManager();
            try {
                var transaction = manager.getTransaction();
                transaction.begin();
                try {
                    var account = new UserAccount(null, name, vector.credential().passwordHash());
                    account.getRoles().addAll(vector.roles());
                    manager.persist(account);
                    if (account.getId() != null) { ids.add(account.getId()); }
                    manager.flush();
                    transaction.commit();
                    assertThat(account.getId()).isNotNull().isPositive();
                    return account.getId();
                } finally { if (transaction.isActive()) { transaction.rollback(); } }
            } finally { manager.close(); }
        }

        @Override
        public void close() throws SQLException {
            if (ids.isEmpty()) { return; }
            assertOutsideTransaction();
            try (var connection = database().openConnection()) {
                connection.setAutoCommit(false);
                try {
                    for (long id : ids) {
                        for (String sql : List.of("delete from tb_user_account_role where user_account_id = ?",
                                "delete from tb_user_account where id = ?")) {
                            try (var statement = connection.prepareStatement(sql)) {
                                statement.setLong(1, id);
                                statement.executeUpdate();
                            }
                        }
                    }
                    connection.commit();
                    ids.clear();
                } catch (SQLException | RuntimeException | Error failure) {
                    connection.rollback();
                    throw new AssertionError("Cleanup of legacy-test-owned accounts failed");
                }
            }
        }
    }
}
