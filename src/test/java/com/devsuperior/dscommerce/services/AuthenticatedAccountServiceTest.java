package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.services.exceptions.InvalidCredentialsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Unit contract only: mocks do not prove JPA fetching or a Spring transaction proxy. */
class AuthenticatedAccountServiceTest {

    private static final Long ACCOUNT_ID = 42L;
    private static final String ACCOUNT_NAME = "private-account-name";
    private static final String PASSWORD_HASH = "private-password-hash";

    private UserAccountRepository repository;
    private AuthenticatedAccountService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        // Direct construction is deliberate: T008 must not register a Spring service.
        service = new AuthenticatedAccountService(repository);
    }

    @ParameterizedTest(name = "database roles {0}")
    @MethodSource("roleSets")
    void shouldResolveIdentityAndOnlyAuthoritiesFromLoadedAccountRoles(Set<UserRole> roles) {
        var account = accountWithRoles(roles);
        when(repository.findByIdWithRoles(ACCOUNT_ID)).thenReturn(Optional.of(account));

        var result = service.resolve(ACCOUNT_ID);

        assertThat(result).as("account resolution must return a value").isNotNull();
        assertThat(result.principal()).isNotNull();
        assertThat(result.principal().accountId()).isEqualTo(ACCOUNT_ID);
        var expected = roles.stream().map(role -> "ROLE_" + role.name()).toList();
        assertThat(result.authorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrderElementsOf(expected);
        // An existing account without roles is resolved; later authorization denies orders.
        account.getRoles().clear();
        assertThat(result.authorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrderElementsOf(expected);
        verify(repository).findByIdWithRoles(ACCOUNT_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldUseOnlyTheExplicitRoleFetchingQueryByAccountId() {
        when(repository.findByIdWithRoles(ACCOUNT_ID))
                .thenReturn(Optional.of(accountWithRoles(Set.of(UserRole.CLIENT))));

        service.resolve(ACCOUNT_ID);

        // Reject plain findById/findByName, writes and any other repository operation.
        // This verifies the selected query API, not its actual JPA loading behavior.
        verify(repository).findByIdWithRoles(ACCOUNT_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldRejectAnAbsentAccountAsAnAuthenticationFailure() {
        when(repository.findByIdWithRoles(ACCOUNT_ID)).thenReturn(Optional.empty());

        var failure = catchThrowable(() -> service.resolve(ACCOUNT_ID));

        assertThat(failure).as("an absent account must fail authentication")
                .isInstanceOf(AuthenticationException.class);
        assertThat(failure.getMessage()).doesNotContain(ACCOUNT_NAME, PASSWORD_HASH);
        verify(repository).findByIdWithRoles(ACCOUNT_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldDistinguishAnInternalLookupFailureFromInvalidCredentials() {
        var cause = new DataAccessResourceFailureException("private SQL connection detail");
        when(repository.findByIdWithRoles(ACCOUNT_ID)).thenThrow(cause);

        var failure = catchThrowable(() -> service.resolve(ACCOUNT_ID));

        assertThat(failure).as("lookup unavailability must produce an internal failure")
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(AuthenticationException.class)
                .isNotInstanceOf(InvalidCredentialsException.class)
                .hasCause(cause);
        // T009 owns this exception. Check its runtime type without adding that class in T008.
        assertThat(failure.getClass().getName()).isEqualTo(
                "com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException");
        assertThat(failure.getMessage()).doesNotContain(
                cause.getMessage(), ACCOUNT_NAME, PASSWORD_HASH);
        verify(repository).findByIdWithRoles(ACCOUNT_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldReturnOnlyTheMinimalPrincipalAndAuthoritiesWithoutAccountData() {
        when(repository.findByIdWithRoles(ACCOUNT_ID))
                .thenReturn(Optional.of(accountWithRoles(Set.of(UserRole.CLIENT, UserRole.ADMIN))));

        var result = service.resolve(ACCOUNT_ID);

        assertThat(result).as("the resolution output must exist").isNotNull();
        var output = new ObjectMapper().valueToTree(result);
        var fields = new HashSet<String>();
        output.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("principal", "authorities");
        assertThat(output.path("principal").size()).isEqualTo(1);
        assertThat(output.path("principal").path("accountId").longValue()).isEqualTo(ACCOUNT_ID);
        assertThat(output.path("authorities").size()).isEqualTo(2);
        assertThat(output.toString()).doesNotContain(
                ACCOUNT_NAME, PASSWORD_HASH, "name", "passwordHash", "secret", "roles");
        verify(repository).findByIdWithRoles(ACCOUNT_ID);
        verifyNoMoreInteractions(repository);
    }

    private static UserAccount accountWithRoles(Set<UserRole> roles) {
        var account = new UserAccount(ACCOUNT_ID, ACCOUNT_NAME, PASSWORD_HASH);
        account.getRoles().addAll(roles);
        return account;
    }

    private static Stream<Set<UserRole>> roleSets() {
        return Stream.of(Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN),
                Set.of(UserRole.CLIENT, UserRole.ADMIN), Set.of());
    }
}
