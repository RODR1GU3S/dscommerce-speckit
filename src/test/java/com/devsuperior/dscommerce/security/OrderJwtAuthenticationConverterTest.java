package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Conversion only: JWT verification and HTTP filters are covered separately with real components. */
class OrderJwtAuthenticationConverterTest {

    private AuthenticatedAccountService accounts;
    private OrderJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        accounts = mock(AuthenticatedAccountService.class);
        converter = new OrderJwtAuthenticationConverter(accounts);
    }

    @ParameterizedTest
    @MethodSource("databaseAuthorities")
    void shouldUseOnlyTheResolvedIdentityAndDatabaseAuthorities(Set<String> roles) {
        Set<GrantedAuthority> authorities = roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
        var principal = new AuthenticatedAccountPrincipal(42L);
        when(accounts.resolve(42L)).thenReturn(new AuthenticatedAccountService.ResolvedAccount(principal, authorities));

        var result = converter.convert(jwt("42"));

        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isSameAs(principal).isInstanceOf(AuthenticatedAccountPrincipal.class);
        assertThat(result.getCredentials()).isNull();
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrderElementsOf(roles);
        verify(accounts).resolve(42L);
        verifyNoMoreInteractions(accounts);
    }

    @Test
    void shouldPreserveInvalidCredentialsFromAccountResolution() {
        var failure = new BadCredentialsException("Authentication required or invalid");
        when(accounts.resolve(42L)).thenThrow(failure);

        assertThatThrownBy(() -> converter.convert(jwt("42"))).isSameAs(failure);
    }

    @Test
    void shouldPreserveAnInternalResolutionFailureWithoutConvertingItToInvalidCredentials() {
        var failure = new OrderAccountResolutionException("Account resolution could not be completed", new IllegalStateException());
        when(accounts.resolve(42L)).thenThrow(failure);

        assertThatThrownBy(() -> converter.convert(jwt("42"))).isSameAs(failure);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"not-an-id", "0", "-1", "9223372036854775808"})
    void shouldRejectAnUnusableAccountIdBeforeCallingResolution(String subject) {
        assertThatThrownBy(() -> converter.convert(jwt(subject))).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(accounts);
    }

    static Stream<Set<String>> databaseAuthorities() {
        return Stream.of(Set.of("ROLE_CLIENT"), Set.of("ROLE_ADMIN"), Set.of("ROLE_CLIENT", "ROLE_ADMIN"), Set.of());
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("unit-conversion-only").header("alg", "HS256")
                .subject(subject).claim("name", "another-account")
                .claim("roles", List.of("ROLE_ADMIN", "ROLE_CLIENT", "ROLE_AUDITOR")).build();
    }
}
