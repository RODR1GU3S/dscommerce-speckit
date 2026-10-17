package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** T011 guard contract: real guard instance, simulated current account resolution only. */
class AdminAccessServiceTest {

    private static final long ACCOUNT_ID = 42L;
    private static final AuthenticatedAccountPrincipal PRINCIPAL = new AuthenticatedAccountPrincipal(ACCOUNT_ID);
    private static final String AUTHENTICATION_MESSAGE = "Authentication required or invalid";
    private static final String PERMISSION_MESSAGE = "Product maintenance not permitted";
    private AuthenticatedAccountService accounts;
    private AdminAccessService guard;

    @BeforeEach
    void createGuardWithTheExistingIdentityResolverBoundary() {
        accounts = mock(AuthenticatedAccountService.class);
        guard = new AdminAccessService(accounts);
    }

    @ParameterizedTest(name = "accept current profiles {0}")
    @MethodSource("adminProfiles")
    void shouldAcceptCurrentAdminWithoutRequiringClient(Set<UserRole> roles) {
        when(accounts.resolve(ACCOUNT_ID)).thenReturn(resolved(roles));

        assertAll(
                () -> assertThatCode(() -> guard.requireAdmin(PRINCIPAL)).doesNotThrowAnyException(),
                () -> verify(accounts).resolve(PRINCIPAL.accountId()),
                () -> verifyNoMoreInteractions(accounts));
    }

    static Stream<Set<UserRole>> adminProfiles() {
        return Stream.of(Set.of(UserRole.ADMIN), Set.of(UserRole.ADMIN, UserRole.CLIENT));
    }

    @ParameterizedTest(name = "deny current profiles {0}")
    @MethodSource("nonAdminProfiles")
    void shouldRejectAnExistingAccountWithoutCurrentAdmin(Set<UserRole> roles) {
        when(accounts.resolve(ACCOUNT_ID)).thenReturn(resolved(roles));

        assertAll(
                () -> assertThatThrownBy(() -> guard.requireAdmin(PRINCIPAL))
                        .isInstanceOf(AccessDeniedException.class).hasMessage(PERMISSION_MESSAGE),
                () -> verify(accounts).resolve(PRINCIPAL.accountId()),
                () -> verifyNoMoreInteractions(accounts));
    }

    static Stream<Set<UserRole>> nonAdminProfiles() {
        return Stream.of(Set.of(UserRole.CLIENT), Set.of());
    }

    @ParameterizedTest(name = "invalid identity {0}")
    @MethodSource("invalidPrincipals")
    void shouldRejectInvalidIdentityBeforeLookingUpAnAccount(String label, AuthenticatedAccountPrincipal principal) {
        assertAll(
                () -> assertThatThrownBy(() -> guard.requireAdmin(principal))
                        .isInstanceOf(BadCredentialsException.class).hasMessage(AUTHENTICATION_MESSAGE),
                () -> verifyNoInteractions(accounts));
    }

    static Stream<Arguments> invalidPrincipals() {
        return Stream.of(Arguments.of("no principal", null),
                Arguments.of("null account id", new AuthenticatedAccountPrincipal(null)),
                Arguments.of("zero account id", new AuthenticatedAccountPrincipal(0L)),
                Arguments.of("negative account id", new AuthenticatedAccountPrincipal(-1L)));
    }

    @Test
    void shouldPreserveInvalidCredentialsWhenTheCurrentAccountDoesNotExist() {
        var failure = new BadCredentialsException(AUTHENTICATION_MESSAGE);
        when(accounts.resolve(ACCOUNT_ID)).thenThrow(failure);

        assertAll(
                () -> assertThatThrownBy(() -> guard.requireAdmin(PRINCIPAL))
                        .isSameAs(failure).isInstanceOf(BadCredentialsException.class).hasMessage(AUTHENTICATION_MESSAGE),
                () -> verify(accounts).resolve(PRINCIPAL.accountId()),
                () -> verifyNoMoreInteractions(accounts));
    }

    @ParameterizedTest(name = "revocation: {0} becomes {1}")
    @MethodSource("revokedProfiles")
    void shouldResolveCurrentProfilesAgainAfterAdminIsRevoked(Set<UserRole> initial, Set<UserRole> current) {
        when(accounts.resolve(ACCOUNT_ID)).thenReturn(resolved(initial), resolved(current));

        assertAll(
                () -> assertThatCode(() -> guard.requireAdmin(PRINCIPAL)).doesNotThrowAnyException(),
                () -> assertThatThrownBy(() -> guard.requireAdmin(PRINCIPAL))
                        .isInstanceOf(AccessDeniedException.class).hasMessage(PERMISSION_MESSAGE),
                () -> verify(accounts, times(2)).resolve(PRINCIPAL.accountId()),
                () -> verifyNoMoreInteractions(accounts));
    }

    static Stream<Arguments> revokedProfiles() {
        return Stream.of(Arguments.of(Set.of(UserRole.ADMIN), Set.of()),
                Arguments.of(Set.of(UserRole.ADMIN, UserRole.CLIENT), Set.of(UserRole.CLIENT)));
    }

    @Test
    void shouldResolveTheAccountAgainAndRejectItsRemovalAfterAnEarlierAuthorization() {
        var missing = new BadCredentialsException(AUTHENTICATION_MESSAGE);
        when(accounts.resolve(ACCOUNT_ID)).thenReturn(resolved(Set.of(UserRole.ADMIN))).thenThrow(missing);

        assertAll(
                () -> assertThatCode(() -> guard.requireAdmin(PRINCIPAL)).doesNotThrowAnyException(),
                () -> assertThatThrownBy(() -> guard.requireAdmin(PRINCIPAL))
                        .isSameAs(missing).isInstanceOf(BadCredentialsException.class).hasMessage(AUTHENTICATION_MESSAGE),
                () -> verify(accounts, times(2)).resolve(PRINCIPAL.accountId()),
                () -> verifyNoMoreInteractions(accounts));
    }

    @Test
    void shouldKeepInternalAccountResolutionFailureDistinctFromInvalidCredentialsAndPermissions() {
        var cause = new DataAccessResourceFailureException("Disposable T011 lookup failure");
        var failure = new OrderAccountResolutionException("Account resolution could not be completed", cause);
        when(accounts.resolve(ACCOUNT_ID)).thenThrow(failure);

        assertAll(
                () -> assertThatThrownBy(() -> guard.requireAdmin(PRINCIPAL))
                        .isSameAs(failure).isInstanceOf(OrderAccountResolutionException.class).hasCause(cause),
                () -> verify(accounts).resolve(PRINCIPAL.accountId()),
                () -> verifyNoMoreInteractions(accounts));
    }

    private static AuthenticatedAccountService.ResolvedAccount resolved(Set<UserRole> roles) {
        Set<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedAccountService.ResolvedAccount(PRINCIPAL, authorities);
    }
}
