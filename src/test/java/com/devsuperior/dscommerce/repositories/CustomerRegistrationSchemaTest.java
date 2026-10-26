package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Column;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.ConstraintColumn;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.IndexColumn;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.TableMetadata;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * T014 authorship only; executable diagnosis belongs to T015 (selector M).
 * Uses existing constructors/getters and JPA metadata, never a future registration getter.
 * Assert missing columns/attributes before any SQL/JPQL that would require them.
 */
@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "feature009.customer-registration-schema-test-context=model")
@ActiveProfiles("test")
@ContextConfiguration(initializers = CustomerRegistrationTestConfig.class)
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationSchemaTest {

    private static final String ACCOUNT = "TB_USER_ACCOUNT";
    private static final String ROLE = "TB_USER_ACCOUNT_ROLE";
    private static final List<String> REGISTRATION_COLUMNS = List.of("FULL_NAME", "EMAIL", "EMAIL_KEY", "PHONE", "BIRTH_DATE");

    @Autowired private ConfigurableApplicationContext context;
    @Autowired private EntityManagerFactory entityManagers;

    @Test
    void jpaMetamodelIncludesTheFiveRegistrationAttributesWithApprovedJavaTypes() {
        requireRegistrationAttributes();
    }

    @ParameterizedTest(name = "nullable registration column {0}")
    @MethodSource("registrationColumns")
    void registrationColumnsHaveTheApprovedPhysicalTypeCapacityAndNullableFlag(String name, int jdbcType, Integer capacity) {
        var actual = column(accountMetadata(), name);
        assertThat(actual.jdbcType()).as("JDBC type of %s", name).isEqualTo(jdbcType);
        assertThat(actual.nullable()).as("legacy nullable column %s", name).isEqualTo(DatabaseMetaData.columnNullable);
        if (capacity != null) { assertThat(actual.size()).as("physical capacity of %s", name).isEqualTo(capacity.intValue()); }
    }

    static Stream<Arguments> registrationColumns() {
        return Stream.of(Arguments.of("FULL_NAME", Types.VARCHAR, 240), Arguments.of("EMAIL", Types.VARCHAR, 508),
                Arguments.of("EMAIL_KEY", Types.VARBINARY, 1016), Arguments.of("PHONE", Types.VARCHAR, 16),
                Arguments.of("BIRTH_DATE", Types.DATE, null));
    }

    @Test
    void legacyNamePasswordHashAndIdentityKeepTheirPhysicalContract() {
        var table = accountMetadata();
        for (var name : List.of("NAME", "PASSWORD_HASH")) {
            var actual = column(table, name);
            assertThat(actual.jdbcType()).isEqualTo(Types.VARCHAR);
            assertThat(actual.size()).as("preserved legacy capacity of %s", name).isEqualTo(255);
            assertThat(actual.nullable()).isEqualTo(DatabaseMetaData.columnNoNulls);
        }
        var id = column(table, "ID");
        assertThat(id.jdbcType()).isEqualTo(Types.BIGINT);
        assertThat(id.autoIncrement()).isEqualToIgnoringCase("YES");
        assertThat(table.primaryKeys()).extracting(CustomerRegistrationDatabaseProbe.PrimaryKeyColumn::column)
                .containsExactly("ID");
    }

    @Test
    void nameHasOneNamedUniqueConstraintAndNoEquivalentDuplicateIndex() {
        var table = accountMetadata();
        column(table, "NAME");
        assertUniqueDefinition(table, "UK_USER_ACCOUNT_NAME", List.of("NAME"));
    }

    @Test
    void emailKeyHasOneNamedUniqueConstraintAndNoEquivalentDuplicateIndex() {
        var table = accountMetadata();
        column(table, "EMAIL_KEY");
        assertUniqueDefinition(table, "UK_USER_ACCOUNT_EMAIL_KEY", List.of("EMAIL_KEY"));
    }

    @Test
    void anaAndLowercaseAnaCoexistButAnExactDuplicateNameIsRejected() throws SQLException {
        column(accountMetadata(), "NAME");
        var database = database();
        try (var owned = new OwnedAccounts()) {
            long upper = owned.persist("Ana", Set.of(UserRole.CLIENT));
            long lower = owned.persist("ana", Set.of(UserRole.ADMIN));
            assertThat(upper).isPositive().isNotEqualTo(lower);
            var manager = entityManagers.createEntityManager();
            try {
                assertThat(manager.createQuery("select a.id from UserAccount a where a.name = :name", Long.class)
                        .setParameter("name", "Ana").getResultList()).containsExactly(upper);
                assertThat(manager.createQuery("select a.id from UserAccount a where a.name = :name", Long.class)
                        .setParameter("name", "ana").getResultList()).containsExactly(lower);
            } finally { manager.close(); }
            var before = database.snapshot();
            assertRejected("23505", "insert into tb_user_account (name, password_hash) values (?, ?)",
                    "Ana", LegacyCredential.SHORT.passwordHash());
            assertUnchanged(before, database.snapshot());
        }
    }

    @ParameterizedTest(name = "legacy NULL registration fields with roles {0}")
    @MethodSource("roleSets")
    void severalLegacyAccountsKeepNullRegistrationValuesAndTheirJpaRoles(Set<UserRole> roles) throws SQLException {
        requireRegistrationColumns();
        try (var owned = new OwnedAccounts()) {
            String legacyName = uniqueName();
            legacyName += "L".repeat(255 - legacyName.length());
            long first = owned.persist(legacyName, roles);
            long second = owned.persist(uniqueName(), Set.of());
            assertThat(first).isPositive().isNotEqualTo(second);
            try (var connection = database().openConnection(); var statement = connection.prepareStatement(
                    "select full_name, email, email_key, phone, birth_date from tb_user_account where id in (?, ?)")) {
                statement.setLong(1, first);
                statement.setLong(2, second);
                int rowsRead = 0;
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        rowsRead++;
                        for (int index = 1; index <= 5; index++) {
                            assertThat(rows.getObject(index) == null).as("legacy registration field remains NULL").isTrue();
                        }
                    }
                }
                assertThat(rowsRead).isEqualTo(2); // Multiple NULL keys must actually commit, not just flush.
            }
            var manager = entityManagers.createEntityManager();
            try {
                var loaded = manager.find(UserAccount.class, first);
                assertThat(loaded).isNotNull();
                assertThat(legacyName.equals(loaded.getName())).as("legacy name is not truncated or normalized").isTrue();
                assertThat(LegacyCredential.SHORT.passwordHash().equals(loaded.getPasswordHash()))
                        .as("legacy BCrypt bytes are preserved").isTrue();
                assertThat(entityManagers.getPersistenceUnitUtil().isLoaded(loaded, "roles")).isFalse();
                assertThat(loaded.getRoles()).containsExactlyInAnyOrderElementsOf(roles);
            } finally { manager.close(); }
        }
    }

    static Stream<Set<UserRole>> roleSets() {
        return Stream.of(Set.of(), Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN), Set.of(UserRole.CLIENT, UserRole.ADMIN));
    }

    @Test
    void binaryEmailKeyRejectsEqualBytesAndAllowsDistinctBytes() throws SQLException {
        column(accountMetadata(), "EMAIL_KEY"); // No update of a missing column as test preparation.
        byte[] firstKey = {0, 65, (byte) 128};
        byte[] differentKey = {0, 97, (byte) 128};
        var database = database();
        try (var owned = new OwnedAccounts()) {
            long first = owned.persist(uniqueName(), Set.of(UserRole.CLIENT));
            long second = owned.persist(uniqueName(), Set.of(UserRole.CLIENT));
            assertThat(write("update tb_user_account set email_key = ? where id = ?", firstKey, first)).isEqualTo(1);
            var before = database.snapshot();
            assertRejected("23505", "update tb_user_account set email_key = ? where id = ?", firstKey, second);
            assertUnchanged(before, database.snapshot());
            assertThat(write("update tb_user_account set email_key = ? where id = ?", differentKey, second)).isEqualTo(1);
            try (var connection = database.openConnection(); var statement = connection.prepareStatement(
                    "select email_key from tb_user_account where id = ?")) {
                statement.setLong(1, first);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(Arrays.equals(firstKey, rows.getBytes(1))).as("first binary key is unchanged").isTrue();
                }
                statement.setLong(1, second);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(Arrays.equals(differentKey, rows.getBytes(1))).as("different bytes remain distinct").isTrue();
                }
            }
        }
    }

    @Test
    void jpaReadsRegistrationValuesFromTheirRealColumnsWithoutTruncation() throws SQLException {
        requireRegistrationColumns();
        requireRegistrationAttributes(); // Guard before JPQL containing future attribute names.
        String fullName = "\uD83D\uDE00".repeat(120); // 120 code points, 240 UTF-16 code units.
        String email = "schema009@example.test";
        String phone = "+123456789012345";
        byte[] binaryCapacityControl = new byte[1016];
        binaryCapacityControl[0] = 1;
        binaryCapacityControl[1015] = (byte) 255;
        var birthDate = LocalDate.of(2000, 2, 29);
        try (var owned = new OwnedAccounts()) {
            long id = owned.persist(uniqueName(), Set.of(UserRole.CLIENT));
            assertThat(write("update tb_user_account set full_name = ?, email = ?, email_key = ?, phone = ?, birth_date = ? where id = ?",
                    fullName, email, binaryCapacityControl, phone, birthDate, id)).isEqualTo(1);
            var manager = entityManagers.createEntityManager();
            try {
                var values = manager.createQuery("select a.fullName, a.email, a.emailKey, a.phone, a.birthDate "
                        + "from UserAccount a where a.id = :id", Object[].class).setParameter("id", id).getSingleResult();
                assertThat(fullName.equals(values[0])).as("mapped fullName retains its complete sequence").isTrue();
                assertThat(email.equals(values[1])).as("mapped email retains its original spelling").isTrue();
                assertThat(values[2] instanceof byte[]).isTrue();
                assertThat(Arrays.equals(binaryCapacityControl, (byte[]) values[2])).as("mapped binary capacity control is intact").isTrue();
                assertThat(phone.equals(values[3])).isTrue();
                assertThat(birthDate.equals(values[4])).isTrue();
            } finally { manager.close(); }
        }
    }

    @Test
    void existingRoleCollectionKeepsItsStringMappingNamedForeignKeyAndUniquePair() {
        var roleAttribute = entityManagers.getMetamodel().entity(UserAccount.class).getAttribute("roles");
        assertThat(roleAttribute.getPersistentAttributeType()).isEqualTo(Attribute.PersistentAttributeType.ELEMENT_COLLECTION);
        assertThat(roleAttribute instanceof PluralAttribute<?, ?, ?>).isTrue();
        var plural = (PluralAttribute<?, ?, ?>) roleAttribute;
        assertThat(plural.getCollectionType()).isEqualTo(PluralAttribute.CollectionType.SET);
        assertThat(plural.getElementType().getJavaType()).isEqualTo(UserRole.class);
        var tables = database().metadata().tables();
        var table = tables.get(ROLE);
        var accountId = column(table, "USER_ACCOUNT_ID");
        var role = column(table, "ROLE");
        assertThat(accountId.jdbcType()).isEqualTo(Types.BIGINT);
        assertThat(accountId.nullable()).isEqualTo(DatabaseMetaData.columnNoNulls);
        assertThat(role.nullable()).isEqualTo(DatabaseMetaData.columnNoNulls);
        // Same existing contract: Hibernate 6.5 may use H2 native ENUM for EnumType.STRING.
        assertThat(role.jdbcType() == Types.VARCHAR || role.typeName().toUpperCase(Locale.ROOT).startsWith("ENUM"))
                .as("role is stored by string value, never by ordinal").isTrue();
        var foreignKeys = table.foreignKeys().stream().filter(key -> upper(key.name()).equals("FK_USER_ACCOUNT_ROLE_ACCOUNT")).toList();
        assertThat(foreignKeys).hasSize(1);
        assertThat(foreignKeys.getFirst().column()).isEqualTo("USER_ACCOUNT_ID");
        assertThat(foreignKeys.getFirst().referencedTable()).isEqualTo(ACCOUNT);
        assertThat(foreignKeys.getFirst().referencedColumn()).isEqualTo("ID");
        // Hibernate materializes this Set's named pair constraint as its composite primary key.
        assertKeyDefinition(table, "UK_USER_ACCOUNT_ROLE", List.of("USER_ACCOUNT_ID", "ROLE"), "PRIMARY KEY");
        var orders = tables.get("TB_ORDER");
        column(orders, "BUYER_ID");
        assertThat(orders.foreignKeys()).filteredOn(key -> key.column().equals("BUYER_ID")
                && key.referencedTable().equals(ACCOUNT) && key.referencedColumn().equals("ID")).hasSize(1);
        var payments = tables.get("TB_PAYMENT");
        column(payments, "ORDER_ID");
        assertThat(payments.foreignKeys()).filteredOn(key -> key.column().equals("ORDER_ID")
                && key.referencedTable().equals("TB_ORDER") && key.referencedColumn().equals("ID")
                && upper(key.name()).equals("FK_PAYMENT_ORDER")).hasSize(1);
    }

    @Test
    void existingRoleForeignKeyUniquePairAndRequiredColumnsAreEnforcedByTheDatabase() throws SQLException {
        var table = database().metadata().tables().get(ROLE);
        column(table, "USER_ACCOUNT_ID");
        column(table, "ROLE");
        try (var owned = new OwnedAccounts()) {
            long id = owned.persist(uniqueName(), Set.of(UserRole.CLIENT));
            var before = database().snapshot();
            assertRejected("23505", "insert into tb_user_account_role (user_account_id, role) values (?, ?)", id, "CLIENT");
            assertRejected("23506", "insert into tb_user_account_role (user_account_id, role) values (?, ?)", Long.MAX_VALUE, "CLIENT");
            assertRejected("23502", "insert into tb_user_account_role (user_account_id, role) values (?, ?)", id, null);
            assertRejected("23502", "insert into tb_user_account_role (user_account_id, role) values (?, ?)", null, "CLIENT");
            assertUnchanged(before, database().snapshot());
        }
    }

    private CustomerRegistrationDatabaseProbe database() {
        return new CustomerRegistrationDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
    }

    private TableMetadata accountMetadata() { return database().metadata().tables().get(ACCOUNT); }

    private static Column column(TableMetadata table, String name) {
        assertThat(table).as("required table metadata").isNotNull();
        var found = table.columns().stream().filter(value -> value.name().equals(name)).toList();
        assertThat(found).as("required physical column %s must exist before dependent SQL", name).hasSize(1);
        return found.getFirst();
    }

    private void requireRegistrationColumns() {
        var table = accountMetadata();
        for (var name : REGISTRATION_COLUMNS) { column(table, name); }
    }

    private void requireRegistrationAttributes() {
        var types = new LinkedHashMap<String, Class<?>>();
        for (var attribute : entityManagers.getMetamodel().entity(UserAccount.class).getAttributes()) {
            types.put(attribute.getName(), attribute.getJavaType());
        }
        Map<String, Class<?>> expected = Map.of("fullName", String.class, "email", String.class,
                "emailKey", byte[].class, "phone", String.class, "birthDate", LocalDate.class);
        assertThat(types).as("JPA registration attributes must exist before dependent JPQL").containsAllEntriesOf(expected);
    }

    private static void assertUniqueDefinition(TableMetadata table, String expectedName, List<String> expectedColumns) {
        assertKeyDefinition(table, expectedName, expectedColumns, "UNIQUE");
    }

    private static void assertKeyDefinition(TableMetadata table, String expectedName, List<String> expectedColumns,
                                            String expectedType) {
        var constraints = table.constraints().stream().filter(row -> row.type().equals(expectedType))
                .collect(Collectors.groupingBy(ConstraintColumn::name));
        var equivalent = constraints.entrySet().stream().filter(entry -> entry.getValue().stream()
                .sorted(Comparator.comparingInt(ConstraintColumn::position)).map(ConstraintColumn::column)
                .toList().equals(expectedColumns)).toList();
        assertThat(equivalent).as("exactly one %s definition for %s", expectedType, expectedColumns).hasSize(1);
        assertThat(upper(equivalent.getFirst().getKey())).isEqualTo(expectedName);
        var indexes = table.indexes().stream().filter(index -> !index.nonUnique())
                .collect(Collectors.groupingBy(IndexColumn::name));
        long backingIndexes = indexes.values().stream().filter(entries -> entries.stream()
                .sorted(Comparator.comparingInt(IndexColumn::position)).map(IndexColumn::column)
                .toList().equals(expectedColumns)).count();
        assertThat(backingIndexes).as("no equivalent duplicate physical unique index for %s", expectedColumns).isEqualTo(1);
    }

    private int write(String sql, Object... parameters) throws SQLException {
        try (var connection = database().openConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                int changed = statement.executeUpdate();
                connection.commit();
                return changed;
            } catch (SQLException | RuntimeException | Error failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    /** Negative probes always roll back, even if a missing constraint unexpectedly accepts the SQL. */
    private void assertRejected(String expectedState, String sql, Object... parameters) throws SQLException {
        SqlAttempt attempt;
        try (var connection = database().openConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                try {
                    statement.executeUpdate();
                    attempt = new SqlAttempt(false, null);
                } catch (SQLException failure) {
                    attempt = new SqlAttempt(true, failure.getSQLState()); // Never retain SQL messages/bind values.
                }
            } finally { connection.rollback(); }
        }
        assertThat(attempt.rejected()).as("database must reject the controlled invalid write").isTrue();
        assertThat(attempt.sqlState()).isEqualTo(expectedState);
    }

    private static void bind(java.sql.PreparedStatement statement, Object[] values) throws SQLException {
        for (int index = 0; index < values.length; index++) { statement.setObject(index + 1, values[index]); }
    }

    private static String uniqueName() { return "schema_" + UUID.randomUUID().toString().replace("-", ""); }
    private static String upper(String identifier) { return identifier.toUpperCase(Locale.ROOT); }
    private record SqlAttempt(boolean rejected, String sqlState) { }

    /** Own only generated IDs from this test; no whole-table deletion or legacy-helper mutation. */
    private final class OwnedAccounts implements AutoCloseable {
        private final Set<Long> ids = new LinkedHashSet<>();

        long persist(String name, Set<UserRole> roles) {
            var manager = entityManagers.createEntityManager();
            try {
                var transaction = manager.getTransaction();
                transaction.begin();
                try {
                    var account = new UserAccount(null, name, LegacyCredential.SHORT.passwordHash());
                    account.getRoles().addAll(roles);
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
            if (ids.isEmpty()) { return; } // Missing-metadata assertions cannot trigger cleanup against future columns.
            try (var connection = database().openConnection()) {
                connection.setAutoCommit(false);
                try {
                    for (long id : ids) {
                        try (var roles = connection.prepareStatement("delete from tb_user_account_role where user_account_id = ?");
                             var account = connection.prepareStatement("delete from tb_user_account where id = ?")) {
                            roles.setLong(1, id);
                            roles.executeUpdate();
                            account.setLong(1, id);
                            account.executeUpdate();
                        }
                    }
                    connection.commit();
                    ids.clear();
                } catch (SQLException | RuntimeException | Error failure) {
                    connection.rollback();
                    throw failure;
                }
            }
        }
    }
}
