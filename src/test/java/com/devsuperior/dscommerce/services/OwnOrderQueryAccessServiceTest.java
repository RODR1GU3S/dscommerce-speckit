package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Scalar projection contract with mocks; the proxy test uses its own H2 transaction, not JPA. */
class OwnOrderQueryAccessServiceTest {

    private final AuthenticatedAccountPrincipal principal = new AuthenticatedAccountPrincipal(42L);
    private UserAccountRepository repository;
    private OwnOrderQueryAccessService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        service = new OwnOrderQueryAccessService(repository);
    }

    @ParameterizedTest
    @MethodSource("allowedRoles")
    void resolvesOnlyScalarCurrentRoles(Set<UserRole> roles) {
        var rows = new ArrayList<>(roles.stream().map(OwnOrderQueryAccessServiceTest::row).toList());
        when(repository.findEffectiveAccountRolesById(42L)).thenReturn(rows);

        var resolved = service.resolve(principal);

        assertThat(resolved.principal()).isEqualTo(principal);
        assertThat(resolved.authorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrderElementsOf(roles.stream().map(role -> "ROLE_" + role.name()).toList());
        rows.clear();
        assertThat(resolved.authorities()).hasSize(roles.size());
        assertThatThrownBy(() -> resolved.authorities().clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(repository).findEffectiveAccountRolesById(42L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void absentAccountIsAuthenticationFailure() {
        when(repository.findEffectiveAccountRolesById(42L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.resolve(principal))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Authentication required or invalid");
    }

    @Test
    void leftJoinRowWithoutRoleIsDeniedRatherThanAbsent() {
        var noRole = row(null);
        when(repository.findEffectiveAccountRolesById(42L)).thenReturn(List.of(noRole));
        assertThatThrownBy(() -> service.resolve(principal))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Access denied");
    }

    @Test
    void lookupFailureRemainsInternalAndHasSafeMessage() {
        var cause = new DataAccessResourceFailureException("private SQL and connection detail");
        when(repository.findEffectiveAccountRolesById(42L)).thenThrow(cause);
        assertThatThrownBy(() -> service.resolve(principal))
                .isInstanceOf(OrderAccountResolutionException.class).hasCause(cause)
                .hasMessage("Account resolution could not be completed");
    }

    @Test
    void inconsistentProjectionFailsInternallyInsteadOfGrantingOrPretendingAbsence() {
        var row = row(UserRole.CLIENT);
        when(row.getId()).thenReturn(99L);
        when(repository.findEffectiveAccountRolesById(42L)).thenReturn(List.of(row));
        assertThatThrownBy(() -> service.resolve(principal)).isInstanceOf(OrderAccountResolutionException.class);
    }

    @Test
    void guardUsesPrincipalAndRechecksEachCallDespiteTokenAuthorities() {
        var authentication = authenticated(principal);
        var client = row(UserRole.CLIENT);
        var noRole = row(null);
        when(repository.findEffectiveAccountRolesById(42L))
                .thenReturn(List.of(client), List.of(noRole), List.of());
        assertThat(service.requireAccountId(authentication)).isEqualTo(42L);
        assertThatThrownBy(() -> service.requireAccountId(authentication)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.requireAccountId(authentication)).isInstanceOf(BadCredentialsException.class);
        verify(repository, times(3)).findEffectiveAccountRolesById(42L);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @MethodSource("invalidAuthentications")
    void guardRejectsUnusableIdentityBeforeLookup(Authentication authentication) {
        assertThatThrownBy(() -> service.requireAccountId(authentication)).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void bothPublicEntryPointsRunInsideReadOnlySpringTransactions() {
        var database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("own-order-access-" + UUID.randomUUID()).build();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(TransactionConfiguration.class);
            context.registerBean(DataSource.class, () -> database);
            context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(database));
            context.registerBean(UserAccountRepository.class, () -> repository);
            context.registerBean(OwnOrderQueryAccessService.class);
            context.refresh();
            when(repository.findEffectiveAccountRolesById(42L)).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                return List.of(row(UserRole.ADMIN));
            });
            var proxied = context.getBean(OwnOrderQueryAccessService.class);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(proxied.resolve(principal).principal()).isEqualTo(principal);
            assertThat(proxied.requireAccountId(authenticated(principal))).isEqualTo(42L);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            verify(repository, times(2)).findEffectiveAccountRolesById(42L);
        } finally {
            database.shutdown();
        }
    }

    static Stream<Set<UserRole>> allowedRoles() {
        return Stream.of(Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN), Set.of(UserRole.CLIENT, UserRole.ADMIN));
    }

    static Stream<Authentication> invalidAuthentications() {
        return Stream.of(null, authenticated("42"), authenticated(new AuthenticatedAccountPrincipal(null)),
                authenticated(new AuthenticatedAccountPrincipal(0L)), authenticated(new AuthenticatedAccountPrincipal(-1L)),
                new AnonymousAuthenticationToken("unit-key", new AuthenticatedAccountPrincipal(42L),
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))),
                UsernamePasswordAuthenticationToken.unauthenticated(new AuthenticatedAccountPrincipal(42L), null));
    }

    private static Authentication authenticated(Object principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static UserAccountRepository.EffectiveAccountRole row(UserRole role) {
        var row = mock(UserAccountRepository.EffectiveAccountRole.class);
        when(row.getId()).thenReturn(42L);
        when(row.getRole()).thenReturn(role);
        return row;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
    }
}
