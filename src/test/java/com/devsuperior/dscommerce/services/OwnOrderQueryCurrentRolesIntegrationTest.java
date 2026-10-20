package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryTestObservationConfig;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JPA-only stale managed collection controls; no HTTP dispatch takes place inside these transactions. */
@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"spring.jpa.open-in-view=false", "spring.h2.console.enabled=false",
                "feature007.own-order-current-roles-test-context=scalar"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(OwnOrderQueryTestObservationConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnOrderQueryCurrentRolesIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserAccountRepository accounts;
    @Autowired private OwnOrderQueryAccessService access;
    @Autowired private OwnOrderQueryDatabaseProbe database;
    @Autowired private SqlObservation sql;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager entityManager;
    private String stableUrl;

    @BeforeAll
    void verifyDedicatedStableSeedFreeJpaContext() {
        outsideTransaction();
        var environment = context.getEnvironment();
        stableUrl = environment.getRequiredProperty("spring.datasource.url");
        assertThat(stableUrl).startsWith("jdbc:h2:mem:admin-product-test-");
        assertThat(jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL()))
                .isEqualTo(stableUrl.split(";", 2)[0]);
        assertThat(environment.getRequiredProperty("feature007.own-order-current-roles-test-context")).isEqualTo("scalar");
        assertThat(environment.getRequiredProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(environment.getRequiredProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(environment.getRequiredProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")).isEmpty();
        assertEmpty();
        System.out.println("T023_JPA_CONTEXT database=" + stableUrl.split(";", 2)[0]
                + " mockMvcDispatches=0 realHttpRequests=0");
    }

    @AfterEach
    void verifyOwnedCleanupAndStableContext() {
        outsideTransaction();
        assertEmpty();
        assertThat(context.getEnvironment().getRequiredProperty("spring.datasource.url")).isEqualTo(stableUrl);
        sql.clear();
    }

    @ParameterizedTest(name = "[{index}] managed {0}, committed {1}")
    @MethodSource("roleTransitions")
    void scalarRolesReflectCommittedGrantRevocationOrReplacementWhileTheManagedCollectionStaysStale(
            Set<UserRole> initial, Set<UserRole> current) {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var prepared = fixture.prepareAccessAccounts();
            var account = initial.isEmpty() ? prepared.none() : initial.size() == 2 ? prepared.both()
                    : initial.contains(UserRole.ADMIN) ? prepared.admin() : prepared.client();
            var outer = new TransactionTemplate(transactions);
            outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            try {
                outer.executeWithoutResult(status -> {
                    try {
                        var managed = accounts.findByIdWithRoles(account.id()).orElseThrow();
                        var collection = managed.getRoles();
                        assertThat(entityManager.contains(managed)).isTrue();
                        assertThat(collection).containsExactlyInAnyOrderElementsOf(initial);
                        replaceCommittedRoles(account.id(), current);
                        assertThat(entityManager.find(UserAccount.class, account.id())).isSameAs(managed);
                        assertThat(managed.getRoles()).isSameAs(collection).containsExactlyInAnyOrderElementsOf(initial);
                        var snapshot = database.snapshot();
                        sql.clear();
                        assertScalarProjectionAndGuard(account.id(), current);
                        assertSelectOnlyWithoutOrders();
                        OwnOrderQueryDatabaseProbe.assertUnchanged(snapshot, database.snapshot());
                        assertThat(managed.getRoles()).isSameAs(collection).containsExactlyInAnyOrderElementsOf(initial);
                    } finally {
                        // Never flush the stale managed collection back over the independently committed preparation.
                        status.setRollbackOnly();
                    }
                });
                outsideTransaction();
                assertCommittedRoles(account.id(), current);
                System.out.println("T023_JPA transition=stale-collection scalarCurrent=true callerRolledBack=true");
            } finally {
                // The outer transaction has ended; restore only this account's original fixture roles before close.
                replaceCommittedRoles(account.id(), initial);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"grant", "revoke"})
    void scalarLookupDoesNotAutoFlushAnUnsavedManagedRoleChangeOverCommittedPermissions(String kind) {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var prepared = fixture.prepareAccessAccounts();
            var account = prepared.client();
            var current = kind.equals("grant") ? Set.of(UserRole.ADMIN) : Set.<UserRole>of();
            var outer = new TransactionTemplate(transactions);
            outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            try {
                outer.executeWithoutResult(status -> {
                    try {
                        var managed = accounts.findByIdWithRoles(account.id()).orElseThrow();
                        assertThat(managed.getRoles()).containsExactly(UserRole.CLIENT);
                        replaceCommittedRoles(account.id(), current);
                        // Make the already initialized collection dirty, opposite to the committed permission.
                        managed.getRoles().clear();
                        if (current.isEmpty()) { managed.getRoles().add(UserRole.ADMIN); }
                        var snapshot = database.snapshot();
                        sql.clear();
                        assertScalarProjectionAndGuard(account.id(), current);
                        assertSelectOnlyWithoutOrders();
                        OwnOrderQueryDatabaseProbe.assertUnchanged(snapshot, database.snapshot());
                        assertThat(entityManager.find(UserAccount.class, account.id())).isSameAs(managed);
                        if (current.isEmpty()) { assertThat(managed.getRoles()).containsExactly(UserRole.ADMIN); }
                        else { assertThat(managed.getRoles()).isEmpty(); }
                    } finally { status.setRollbackOnly(); }
                });
                assertCommittedRoles(account.id(), current);
                System.out.println("T023_JPA transition=dirty-collection scalarCurrent=true noAutoFlush=true");
            } finally {
                replaceCommittedRoles(account.id(), Set.of(UserRole.CLIENT));
            }
        }
    }

    private void assertScalarProjectionAndGuard(long id, Set<UserRole> current) {
        var rows = accounts.findEffectiveAccountRolesById(id);
        assertThat(rows).hasSize(Math.max(1, current.size()));
        assertThat(rows).allSatisfy(row -> assertThat(row.getId()).isEqualTo(id));
        if (current.isEmpty()) { assertThat(rows.getFirst().getRole()).isNull(); }
        else { assertThat(rows).extracting(UserAccountRepository.EffectiveAccountRole::getRole)
                .containsExactlyInAnyOrderElementsOf(current); }
        var principal = new AuthenticatedAccountPrincipal(id);
        // Deliberately misleading token authorities; the guard must use the scalar database rows.
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        if (current.isEmpty()) {
            assertThatThrownBy(() -> access.requireAccountId(authentication)).isInstanceOf(AccessDeniedException.class);
        } else {
            var resolved = access.resolve(principal);
            assertThat(resolved.principal()).isEqualTo(principal);
            assertThat(resolved.authorities()).extracting(authority -> authority.getAuthority())
                    .containsExactlyInAnyOrderElementsOf(current.stream().map(role -> "ROLE_" + role.name()).toList());
            assertThat(access.requireAccountId(authentication)).isEqualTo(id);
        }
    }

    private void replaceCommittedRoles(long id, Set<UserRole> roles) {
        var independent = new TransactionTemplate(transactions);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        independent.executeWithoutResult(status -> {
            jdbc.update("delete from tb_user_account_role where user_account_id = ?", id);
            for (var role : roles) {
                assertThat(jdbc.update("insert into tb_user_account_role (user_account_id, role) values (?, ?)", id, role.name()))
                        .isEqualTo(1);
            }
        });
        assertCommittedRoles(id, roles);
    }

    private void assertCommittedRoles(long id, Set<UserRole> roles) {
        var actual = database.snapshot().roles().stream().filter(row -> row.accountId() == id)
                .map(OwnOrderQueryDatabaseProbe.AccountRole::role).collect(java.util.stream.Collectors.toSet());
        assertThat(actual).containsExactlyInAnyOrderElementsOf(roles);
    }

    private void assertSelectOnlyWithoutOrders() {
        assertThat(sql.events()).isNotEmpty().allSatisfy(event -> {
            assertThat(event.sql()).startsWith("select").contains("tb_user_account").doesNotContain("tb_order");
            assertThat(event.transactionActive()).isTrue();
        });
    }

    private void assertEmpty() {
        var snapshot = database.snapshot();
        assertThat(snapshot.accountIds()).isEmpty();
        assertThat(snapshot.roles()).isEmpty();
        assertThat(snapshot.data().products()).isEmpty();
        assertThat(snapshot.data().categories()).isEmpty();
        assertThat(snapshot.data().associations()).isEmpty();
        assertThat(snapshot.data().orders()).isEmpty();
        assertThat(snapshot.data().items()).isEmpty();
    }

    static Stream<Arguments> roleTransitions() {
        return Stream.of(
                Arguments.of(Set.of(), Set.of(UserRole.CLIENT)),
                Arguments.of(Set.of(), Set.of(UserRole.ADMIN)),
                Arguments.of(Set.of(UserRole.CLIENT), Set.of()),
                Arguments.of(Set.of(UserRole.ADMIN), Set.of()),
                Arguments.of(Set.of(UserRole.CLIENT, UserRole.ADMIN), Set.of(UserRole.CLIENT)),
                Arguments.of(Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN)));
    }

    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }
}
