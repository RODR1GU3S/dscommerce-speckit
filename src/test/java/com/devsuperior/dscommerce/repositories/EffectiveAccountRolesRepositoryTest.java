package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.controllers.AdminProductReferenceRaceIntegrationTest;
import com.devsuperior.dscommerce.controllers.AdminProductReferenceRaceIntegrationTest.Point;
import com.devsuperior.dscommerce.controllers.AdminProductReferenceRaceIntegrationTest.Recorder;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** T041: scalar guard query, independent committed role changes and preservation of a caller's managed state. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductReferenceRaceIntegrationTest.ProbeConfig.class)
class EffectiveAccountRolesRepositoryTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserAccountRepository repository;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private Recorder recorder;
    private AdminProductTestFixture fixture;
    private AdminProductDatabaseProbe database;
    private Long concurrentRoleAccount;

    @BeforeEach
    void isolatedCommittedFixtures() {
        recorder.active = false;
        concurrentRoleAccount = null;
        fixture = new AdminProductTestFixture(context);
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertThat(database.accountIds()).isEmpty();
    }

    @AfterEach
    void cleanupAfterAssertions() throws Exception {
        recorder.active = false;
        if (concurrentRoleAccount != null) {
            AdminProductReferenceRaceIntegrationTest.executeIndependent(database,
                    "delete from tb_user_account_role where user_account_id = ? and role = 'CLIENT'", concurrentRoleAccount);
        }
        fixture.close();
        assertThat(database.accountIds()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("profiles")
    void shouldDistinguishAnExistingAccountIncludingNoProfiles(Set<UserRole> roles) {
        var account = fixture.createAccount("T041 scalar", "Disposable-T041", roles);
        var read = new TransactionTemplate(transactions);
        read.setReadOnly(true);
        var rows = read.execute(status -> repository.findEffectiveAccountRolesById(account.id()));
        assertThat(rows).hasSize(Math.max(1, roles.size())).allSatisfy(row -> assertThat(row.getId()).isEqualTo(account.id()));
        assertThat(rows.stream().map(UserAccountRepository.EffectiveAccountRole::getRole).filter(Objects::nonNull).toList())
                .containsExactlyInAnyOrderElementsOf(roles);
        if (roles.isEmpty()) assertThat(rows).singleElement().satisfies(row -> assertThat(row.getRole()).isNull());
        assertThat(database.roles(account.id())).isEqualTo(roles);
    }

    static Stream<Set<UserRole>> profiles() {
        return Stream.of(Set.of(UserRole.ADMIN), Set.of(UserRole.ADMIN, UserRole.CLIENT), Set.of(UserRole.CLIENT), Set.of());
    }

    @Test
    void shouldReturnNoRowsForAnAbsentAccount() {
        var read = new TransactionTemplate(transactions);
        read.setReadOnly(true);
        var rows = read.execute(status -> repository.findEffectiveAccountRolesById(Long.MAX_VALUE));
        assertThat(rows).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }

    @Test
    void shouldReadEffectiveRolesWithoutRefreshingOrFlushingTheCallersManagedChanges() {
        var account = fixture.createAccount("T041 managed", "Disposable-T041", Set.of(UserRole.ADMIN));
        concurrentRoleAccount = account.id();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var managed = repository.findByIdWithRoles(account.id()).orElseThrow();
            assertThat(managed.getRoles()).containsExactly(UserRole.ADMIN);
            ReflectionTestUtils.setField(managed, "name", "T041 pending name");
            try {
                assertThat(AdminProductReferenceRaceIntegrationTest.executeIndependent(database,
                        "delete from tb_user_account_role where user_account_id = ?", account.id())).isEqualTo(1);
                assertThat(AdminProductReferenceRaceIntegrationTest.executeIndependent(database,
                        "insert into tb_user_account_role (user_account_id, role) values (?, 'CLIENT')", account.id())).isEqualTo(1);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            assertThat(database.roles(account.id())).containsExactly(UserRole.CLIENT);
            recorder.start(Point.NONE);
            var rows = repository.findEffectiveAccountRolesById(account.id());
            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.getId()).isEqualTo(account.id());
                assertThat(row.getRole()).isEqualTo(UserRole.CLIENT);
            });
            assertThat(managed.getName()).isEqualTo("T041 pending name");
            assertThat(managed.getRoles()).containsExactly(UserRole.ADMIN);
            assertThat(recorder.sql).hasSize(1).allSatisfy(event -> {
                assertThat(event.succeeded()).isTrue();
                assertThat(event.sql()).startsWith("select ").contains("tb_user_account_role");
            });
            assertThat(independentName(account.id())).isEqualTo("T041 managed");
            status.setRollbackOnly();
        });
        assertThat(independentName(account.id())).isEqualTo("T041 managed");
        assertThat(database.roles(account.id())).containsExactly(UserRole.CLIENT);
        System.out.println("T041_EFFECTIVE_ROLES scalar=CLIENT managed=ADMIN pendingNameRetained=true dml=0 independentNameUnchanged=true concurrentRoleChangeRetained=true");
    }

    private String independentName(long id) {
        try (var connection = database.openConnection(); var statement = connection.prepareStatement("select name from tb_user_account where id=?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        } catch (SQLException failure) {
            throw new AssertionError(failure);
        }
    }
}
