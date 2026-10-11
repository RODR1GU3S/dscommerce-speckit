package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.ParameterizedType;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@Import(OrderTestConfig.class)
class UserAccountRoleRepositoryTest {

    private static final String TABLE = "TB_USER_ACCOUNT_ROLE";
    private final Set<Long> ownedAccounts = new LinkedHashSet<>();

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private OrderTestFixture fixture;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @AfterEach
    void cleanOnlyOwnedFixtures() {
        // Failed schema assertions must not be followed by SQL against an absent table.
        if (roleTableExists()) {
            for (var accountId : ownedAccounts) {
                jdbc.update("delete from tb_user_account_role where user_account_id = ?", accountId);
            }
        }
        fixture.cleanUp();
    }

    @Test
    void shouldExposeStringRolesWithNotNullColumnsForeignKeyAndUniquePair() {
        var columns = requireRoleColumns();
        assertThat(columns).allSatisfy(column ->
                assertThat(column.nullable()).isEqualTo(DatabaseMetaData.columnNoNulls));
        var accountColumn = columns.stream().filter(column -> column.name().equals("USER_ACCOUNT_ID"))
                .findFirst().orElseThrow();
        assertThat(accountColumn.jdbcType()).isEqualTo(Types.BIGINT);
        var roleColumn = columns.stream().filter(column -> column.name().equals("ROLE"))
                .findFirst().orElseThrow();
        // Hibernate 6.5 can map EnumType.STRING to H2's native ENUM instead of VARCHAR.
        assertThat(roleColumn.jdbcType() == Types.VARCHAR
                || roleColumn.typeName().toUpperCase(Locale.ROOT).startsWith("ENUM"))
                .as("role must use a string or native enum column, never an ordinal integer").isTrue();

        var foreignKeys = jdbc.execute((ConnectionCallback<List<String>>) connection -> {
            var result = new ArrayList<String>();
            try (var keys = connection.getMetaData().getImportedKeys(null, "PUBLIC", TABLE)) {
                while (keys.next()) {
                    result.add(keys.getString("FKCOLUMN_NAME") + "->"
                            + keys.getString("PKTABLE_NAME") + "." + keys.getString("PKCOLUMN_NAME"));
                }
            }
            return result;
        });
        assertThat(foreignKeys).contains("USER_ACCOUNT_ID->TB_USER_ACCOUNT.ID");

        var uniqueIndexes = jdbc.execute((ConnectionCallback<Map<String, Set<String>>>) connection -> {
            var result = new LinkedHashMap<String, Set<String>>();
            try (var indexes = connection.getMetaData().getIndexInfo(null, "PUBLIC", TABLE, true, false)) {
                while (indexes.next()) {
                    var name = indexes.getString("INDEX_NAME");
                    var column = indexes.getString("COLUMN_NAME");
                    if (name != null && column != null) {
                        result.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(column);
                    }
                }
            }
            return result;
        });
        assertThat(uniqueIndexes).containsValue(Set.of("USER_ACCOUNT_ID", "ROLE"));
    }

    @Test
    void shouldFindAnAccountWithoutRoleRowsAndReturnALoadedEmptySet() {
        // This fixture uses only the existing account schema, so lookup can reach its own RED.
        var accountId = createAccount();
        var account = findDetachedWithRoles(accountId);
        assertThat(roleNames(account)).isEmpty();
        requireRoleColumns();
        assertThat(jdbc.queryForObject(
                "select count(*) from tb_user_account_role where user_account_id = ?", Integer.class, accountId))
                .isZero();
    }

    @Test
    void shouldReturnEmptyForAnAccountThatDoesNotExist() {
        assertThat(repository.findByIdWithRoles(Long.MAX_VALUE)).isEmpty();
    }

    @ParameterizedTest(name = "round-trip and detached lookup: {0}")
    @MethodSource("roleSets")
    void shouldRoundTripEachRoleSetAndLoadItDuringLookupById(Set<String> expectedRoles) {
        requireRoleColumns();
        var accountId = createAccount();
        newTransaction().executeWithoutResult(status -> {
            var account = repository.findById(accountId).orElseThrow();
            replaceRoles(account, expectedRoles);
            repository.saveAndFlush(account);
        });

        assertThat(jdbc.queryForList(
                "select role from tb_user_account_role where user_account_id = ?", String.class, accountId))
                .containsExactlyInAnyOrderElementsOf(expectedRoles);
        var detached = findDetachedWithRoles(accountId);
        assertThat(detached.getId()).isEqualTo(accountId);
        assertThat(roleNames(detached)).containsExactlyInAnyOrderElementsOf(expectedRoles);
    }

    @Test
    void shouldRejectAnUnknownAccountByJdbcForeignKey() {
        requireRoleColumns();
        assertSqlConstraint("23506", () -> insertRole(Long.MAX_VALUE, "CLIENT"));
    }

    @Test
    void shouldRejectOnlyDuplicateAccountRolePairsByJdbc() {
        requireRoleColumns();
        var first = createAccount();
        var second = createAccount();
        insertRole(first, "CLIENT");
        insertRole(first, "ADMIN");
        insertRole(second, "CLIENT");

        assertSqlConstraint("23505", () -> insertRole(first, "CLIENT"));
        assertThat(jdbc.queryForObject("select count(*) from tb_user_account_role", Integer.class)).isEqualTo(3);
    }

    @Test
    void shouldRejectNullAccountIdByJdbc() {
        requireRoleColumns();
        assertSqlConstraint("23502", () -> insertRole(null, "CLIENT"));
    }

    @Test
    void shouldRejectNullRoleByJdbc() {
        requireRoleColumns();
        var accountId = createAccount();
        assertSqlConstraint("23502", () -> insertRole(accountId, null));
    }

    static Stream<Set<String>> roleSets() {
        return Stream.of(Set.of(), Set.of("CLIENT"), Set.of("ADMIN"), Set.of("CLIENT", "ADMIN"));
    }

    private long createAccount() {
        var id = fixture.createAccount("role-test-" + java.util.UUID.randomUUID(), "role-test-password");
        ownedAccounts.add(id);
        return id;
    }

    private UserAccount findDetachedWithRoles(long accountId) {
        var read = newTransaction();
        read.setReadOnly(true);
        var account = read.execute(status -> {
            var result = repository.findByIdWithRoles(accountId);
            assertThat(result).as("findByIdWithRoles must find an existing account, including one with no roles")
                    .isPresent();
            var found = result.orElseThrow();
            // Check before accessing the collection; a lazy load by the test cannot satisfy this assertion.
            assertThat(Persistence.getPersistenceUtil().isLoaded(found, "roles"))
                    .as("roles must already be loaded by the repository lookup").isTrue();
            return found;
        });
        assertThat(account).isNotNull();
        return account;
    }

    private TransactionTemplate newTransaction() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction;
    }

    private boolean roleTableExists() {
        return Integer.valueOf(1).equals(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'PUBLIC' and table_name = ?",
                Integer.class, TABLE));
    }

    private List<Column> requireRoleColumns() {
        assertThat(roleTableExists()).as("T006 must map the missing table PUBLIC.TB_USER_ACCOUNT_ROLE").isTrue();
        var columns = jdbc.execute((ConnectionCallback<List<Column>>) connection -> {
            var result = new ArrayList<Column>();
            try (var metadata = connection.getMetaData().getColumns(null, "PUBLIC", TABLE, null)) {
                while (metadata.next()) {
                    result.add(new Column(metadata.getString("COLUMN_NAME"), metadata.getInt("DATA_TYPE"),
                            metadata.getInt("NULLABLE"), metadata.getString("TYPE_NAME")));
                }
            }
            return result;
        });
        assertThat(columns).extracting(Column::name).containsExactlyInAnyOrder("USER_ACCOUNT_ID", "ROLE");
        return columns;
    }

    private void insertRole(Long accountId, String role) {
        jdbc.update("insert into tb_user_account_role (user_account_id, role) values (?, ?)", accountId, role);
    }

    private static void assertSqlConstraint(String sqlState, Runnable statement) {
        assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class).satisfies(error -> {
            var cause = ((DataIntegrityViolationException) error).getMostSpecificCause();
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo(sqlState);
        });
    }

    private static Set<String> roleNames(UserAccount account) {
        var field = ReflectionUtils.findField(UserAccount.class, "roles");
        assertThat(field).as("UserAccount must expose its mapped roles collection").isNotNull();
        var value = ReflectionTestUtils.getField(account, "roles");
        assertThat(value).isInstanceOf(Set.class);
        var result = new LinkedHashSet<String>();
        for (var role : (Set<?>) value) {
            assertThat(role).isInstanceOf(Enum.class);
            result.add(((Enum<?>) role).name());
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void replaceRoles(UserAccount account, Set<String> names) {
        var field = ReflectionUtils.findField(UserAccount.class, "roles");
        assertThat(field).as("T006 must map Set<UserRole> roles on UserAccount").isNotNull();
        assertThat(field.getGenericType()).isInstanceOf(ParameterizedType.class);
        var type = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
        assertThat(type).isInstanceOf(Class.class);
        var enumType = (Class<?>) type;
        assertThat(enumType.isEnum()).isTrue();
        assertThat(Stream.of(enumType.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList())
                .containsExactlyInAnyOrder("CLIENT", "ADMIN");
        var roles = new LinkedHashSet<Enum>();
        for (var name : names) {
            roles.add(Enum.valueOf((Class) enumType, name));
        }
        ReflectionTestUtils.setField(account, "roles", roles);
    }

    private record Column(String name, int jdbcType, int nullable, String typeName) {
    }
}
