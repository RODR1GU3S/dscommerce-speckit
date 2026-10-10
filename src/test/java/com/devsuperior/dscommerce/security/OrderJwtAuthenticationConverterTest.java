package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
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

    private static final Instant NOW = Instant.parse("2026-10-08T15:00:00.123Z");
    private AuthenticatedAccountService accounts;
    private OrderJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        accounts = mock(AuthenticatedAccountService.class);
        converter = new OrderJwtAuthenticationConverter(accounts, Clock.fixed(NOW, ZoneOffset.UTC));
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

    @ParameterizedTest(name = "invalid operation claim {0}")
    @MethodSource("invalidOperationClaims")
    void shouldRejectUnusableOrInvalidTimesBeforeAccountLookup(String label, Map<String, Object> claims) {
        assertThatThrownBy(() -> converter.convert(operationJwt(claims)))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Authentication required or invalid");
        verifyNoInteractions(accounts);
    }

    static Stream<Arguments> invalidOperationClaims() {
        var cases = new ArrayList<Arguments>();
        for (var claim : List.of("exp", "iat")) {
            var missing = validClaims();
            missing.remove(claim);
            cases.add(Arguments.of(claim + " absent", missing));
            for (Object value : Arrays.asList(null, "unusable", 1L)) {
                cases.add(Arguments.of(claim + " unusable " + value, withClaim(claim, value)));
            }
        }
        for (Object value : Arrays.asList(null, "unusable", 1L)) {
            cases.add(Arguments.of("nbf present unusable " + value, withClaim("nbf", value)));
        }
        cases.add(Arguments.of("exp equals now", withClaim("exp", NOW)));
        cases.add(Arguments.of("exp expired one nanosecond", withClaim("exp", NOW.minusNanos(1))));
        cases.add(Arguments.of("exp recently expired", withClaim("exp", NOW.minusSeconds(5))));
        cases.add(Arguments.of("exp equals iat", withClaim("exp", NOW.minusSeconds(60))));
        cases.add(Arguments.of("exp before iat", withClaim("exp", NOW.minusSeconds(61))));
        for (var claim : List.of("iat", "nbf")) {
            cases.add(Arguments.of(claim + " future one nanosecond", withClaim(claim, NOW.plusNanos(1))));
            cases.add(Arguments.of(claim + " future five seconds", withClaim(claim, NOW.plusSeconds(5))));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "noncanonical/textually invalid subject {0}")
    @MethodSource("noncanonicalSubjects")
    void shouldRejectNoncanonicalOrNontextualSubjectWithoutCoercion(Object subject) {
        assertThatThrownBy(() -> converter.convert(operationJwt(withClaim("sub", subject))))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Authentication required or invalid");
        verifyNoInteractions(accounts);
    }

    static Stream<Object> noncanonicalSubjects() {
        return Stream.of("", "01", "+1", " 1", "1 ", "1.0", "1e0", "1\n", "١", 1L, true,
                List.of("1"), Map.of("id", "1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "42", "9223372036854775807"})
    void shouldAcceptCanonicalIntegerLimitsAndExactTemporalBoundariesWithoutExtraClaims(String subject) {
        long id = Long.parseLong(subject);
        var principal = new AuthenticatedAccountPrincipal(id);
        when(accounts.resolve(id)).thenReturn(new AuthenticatedAccountService.ResolvedAccount(principal,
                Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        var claims = validClaims();
        claims.put("sub", subject);
        claims.put("iat", NOW);
        claims.put("exp", NOW.plusNanos(1));
        claims.put("nbf", NOW);

        var result = converter.convert(operationJwt(claims));

        assertThat(result.getPrincipal()).isSameAs(principal);
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        verify(accounts).resolve(id);
        verifyNoMoreInteractions(accounts);
    }

    private static Map<String, Object> validClaims() {
        var claims = new HashMap<String, Object>();
        claims.put("sub", "42");
        claims.put("iat", NOW.minusSeconds(60));
        claims.put("exp", NOW.plusSeconds(60));
        return claims;
    }

    private static Map<String, Object> withClaim(String name, Object value) {
        var claims = validClaims();
        claims.put(name, value);
        return claims;
    }

    private static Jwt operationJwt(Map<String, Object> claims) {
        // Decoded-claim fixture only: null token metadata bypasses Jwt's constructor interval check,
        // so invalid intervals reach this converter. Real signed tokens stay in the integration class.
        return new Jwt(signedUnitToken(claims), null, null, Map.of("alg", "HS256"), claims);
    }

    @Test
    void shouldRejectOriginalNumericSubjectEvenIfDecoderNormalizedItToString() {
        var decodedClaims = validClaims();
        var rawClaims = withClaim("sub", 42L);
        var decoded = new Jwt(signedUnitToken(rawClaims), null, null, Map.of("alg", "HS256"), decodedClaims);

        assertThatThrownBy(() -> converter.convert(decoded))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Authentication required or invalid");
        verifyNoInteractions(accounts);
    }

    private static String signedUnitToken(Map<String, Object> claims) {
        var rawClaims = new HashMap<>(claims);
        rawClaims.replaceAll((name, value) -> value instanceof Instant instant ? instant.getEpochSecond() : value);
        var signed = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(rawClaims));
        try {
            // Disposable unit key, never the login key. These tests still do not exercise a decoder.
            signed.sign(new MACSigner("unit-only-signing-key-32-bytes!!!?".getBytes(StandardCharsets.UTF_8)));
            return signed.serialize();
        } catch (JOSEException failure) {
            throw new AssertionError("Could not prepare the unit JWT", failure);
        }
    }

    private static Jwt jwt(String subject) {
        var claims = validClaims();
        claims.put("sub", subject);
        return Jwt.withTokenValue(signedUnitToken(claims)).header("alg", "HS256")
                .issuedAt(NOW.minusSeconds(60)).expiresAt(NOW.plusSeconds(60))
                .subject(subject).claim("name", "another-account")
                .claim("roles", List.of("ROLE_ADMIN", "ROLE_CLIENT", "ROLE_AUDITOR")).build();
    }
}
