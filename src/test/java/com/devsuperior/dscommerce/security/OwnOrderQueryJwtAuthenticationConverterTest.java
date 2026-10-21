package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
import com.devsuperior.dscommerce.services.OwnOrderQueryAccessService;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Real existing strict converter, mocked account lookups. No decoder/filter/HTTP proof. */
class OwnOrderQueryJwtAuthenticationConverterTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private final AuthenticatedAccountPrincipal principal = new AuthenticatedAccountPrincipal(42L);
    private AuthenticatedAccountService accounts;
    private OwnOrderQueryAccessService access;
    private OwnOrderQueryJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        accounts = mock(AuthenticatedAccountService.class);
        access = mock(OwnOrderQueryAccessService.class);
        converter = new OwnOrderQueryJwtAuthenticationConverter(
                new OrderJwtAuthenticationConverter(accounts, Clock.fixed(NOW, ZoneOffset.UTC)), access);
    }

    @ParameterizedTest
    @MethodSource("currentAuthorities")
    void replacesManagedAuthoritiesAndIgnoresClaims(Set<String> roles) {
        // Even a previously empty managed collection cannot override newly granted scalar roles.
        when(accounts.resolve(42L)).thenReturn(new AuthenticatedAccountService.ResolvedAccount(principal, Set.of()));
        Set<GrantedAuthority> authorities = roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
        when(access.resolve(principal)).thenReturn(new AuthenticatedAccountService.ResolvedAccount(principal, authorities));

        var result = converter.convert(jwt(claims(), claims()));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isSameAs(principal);
        assertThat(result.getCredentials()).isNull();
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrderElementsOf(roles);
        var order = inOrder(accounts, access);
        order.verify(accounts).resolve(42L);
        order.verify(access).resolve(principal);
        verifyNoMoreInteractions(accounts, access);
    }

    @ParameterizedTest
    @MethodSource("resolutionFailures")
    void preservesScalarFailureTypesDespiteStaleAdminAuthority(RuntimeException failure) {
        when(accounts.resolve(42L)).thenReturn(new AuthenticatedAccountService.ResolvedAccount(principal,
                Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(access.resolve(principal)).thenThrow(failure);
        assertThatThrownBy(() -> converter.convert(jwt(claims(), claims()))).isSameAs(failure);
    }

    @ParameterizedTest
    @MethodSource("initialFailures")
    void preservesInitialLookupFailureAndNeverCallsScalarLookup(RuntimeException failure) {
        when(accounts.resolve(42L)).thenThrow(failure);
        assertThatThrownBy(() -> converter.convert(jwt(claims(), claims()))).isSameAs(failure);
        verifyNoInteractions(access);
    }

    @ParameterizedTest
    @MethodSource("invalidClaims")
    void existingStrictConverterRejectsInvalidClaimsBeforeEitherLookup(Map<String, Object> claims) {
        assertThatThrownBy(() -> converter.convert(jwt(claims, claims))).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(accounts, access);
    }

    @Test
    void existingConverterRejectsOriginalNumericSubjectAfterDecoderNormalization() {
        var raw = claims();
        raw.put("sub", 42L);
        assertThatThrownBy(() -> converter.convert(jwt(claims(), raw))).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(accounts, access);
    }

    static Stream<Set<String>> currentAuthorities() {
        return Stream.of(Set.of("ROLE_CLIENT"), Set.of("ROLE_ADMIN"), Set.of("ROLE_CLIENT", "ROLE_ADMIN"));
    }

    static Stream<RuntimeException> initialFailures() {
        return Stream.of(new BadCredentialsException("Authentication required or invalid"),
                new OrderAccountResolutionException("Account resolution could not be completed", new IllegalStateException()));
    }

    static Stream<RuntimeException> resolutionFailures() {
        return Stream.concat(initialFailures(), Stream.of(new AccessDeniedException("Access denied")));
    }

    static Stream<Map<String, Object>> invalidClaims() {
        var invalid = new java.util.ArrayList<Map<String, Object>>();
        for (Object subject : List.of("0", "01", "+42", "-1", " 42", "9223372036854775808", 42L, true)) {
            var claims = claims();
            claims.put("sub", subject);
            invalid.add(claims);
        }
        for (String claim : List.of("sub", "exp", "iat")) {
            var claims = claims();
            claims.remove(claim);
            invalid.add(claims);
        }
        for (String claim : List.of("exp", "iat", "nbf")) {
            var claims = claims();
            claims.put(claim, "not-an-instant");
            invalid.add(claims);
        }
        var expired = claims();
        expired.put("exp", NOW);
        invalid.add(expired);
        for (String claim : List.of("iat", "nbf")) {
            var future = claims();
            future.put(claim, NOW.plusSeconds(1));
            invalid.add(future);
        }
        return invalid.stream();
    }

    private static Map<String, Object> claims() {
        var claims = new HashMap<String, Object>();
        claims.put("sub", "42");
        claims.put("iat", NOW.minusSeconds(60));
        claims.put("exp", NOW.plusSeconds(60));
        claims.put("name", "untrusted-name");
        claims.put("roles", List.of("ROLE_ADMIN", "ROLE_AUDITOR"));
        return claims;
    }

    private static Jwt jwt(Map<String, Object> decoded, Map<String, Object> raw) {
        var payload = new HashMap<>(raw);
        payload.replaceAll((name, value) -> value instanceof Instant instant ? instant.getEpochSecond() : value);
        var signed = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(payload));
        try {
            // Disposable unit key; signing makes a payload fixture, not decoder validation.
            signed.sign(new MACSigner("unit-only-signing-key-32-bytes!!!?"));
            return new Jwt(signed.serialize(), null, null, Map.of("alg", "HS256"), decoded);
        } catch (JOSEException failure) {
            throw new AssertionError("Could not prepare unit JWT", failure);
        }
    }
}
