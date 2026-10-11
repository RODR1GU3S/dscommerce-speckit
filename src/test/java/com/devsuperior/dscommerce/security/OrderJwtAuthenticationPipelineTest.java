package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.config.JwtConfig;
import com.devsuperior.dscommerce.config.JwtProperties;
import com.devsuperior.dscommerce.config.OrderTestConfig.ControlledClock;
import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Real production decoder/provider and converter; only account resolution is mocked. */
class OrderJwtAuthenticationPipelineTest {

    private AuthenticatedAccountService accounts;
    private JwtAuthenticationProvider provider;
    private ControlledClock clock;
    private Instant now;
    private byte[] key;

    @BeforeEach
    void setUp() {
        // Disposable key, independent of login/test-profile secrets.
        key = "pipeline-only-test-key-32-bytes!!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var properties = new JwtProperties();
        properties.setSecretBase64(Base64.getEncoder().encodeToString(key));
        var decoder = new JwtConfig(properties).jwtDecoder();
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        clock = new ControlledClock(now);
        accounts = mock(AuthenticatedAccountService.class);
        provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(new OrderJwtAuthenticationConverter(accounts, clock));
    }

    @ParameterizedTest
    @EnumSource(TokenFault.class)
    void shouldRejectInvalidSignedOrMalformedTokenBeforeAccountResolution(TokenFault fault) throws Exception {
        var claims = validClaims("42");
        switch (fault) {
            case MISSING_EXP -> claims.remove("exp");
            case MISSING_IAT -> claims.remove("iat");
            case UNUSABLE_EXP -> claims.put("exp", "unusable");
            case UNUSABLE_IAT -> claims.put("iat", "unusable");
            case UNUSABLE_NBF -> claims.put("nbf", "unusable");
            case EXPIRED -> claims.put("exp", now.minusSeconds(5).getEpochSecond());
            case EXP_EQUALS_NOW -> claims.put("exp", now.getEpochSecond());
            case EXP_EQUALS_IAT -> claims.put("exp", claims.get("iat"));
            case EXP_BEFORE_IAT -> claims.put("exp", now.minusSeconds(121).getEpochSecond());
            case FUTURE_IAT -> claims.put("iat", now.plusSeconds(5).getEpochSecond());
            case FUTURE_NBF -> claims.put("nbf", now.plusSeconds(5).getEpochSecond());
            case MISSING_SUB -> claims.remove("sub");
            case NUMERIC_SUB -> claims.put("sub", 42L);
            case LEADING_ZERO_SUB -> claims.put("sub", "042");
            case PLUS_SUB -> claims.put("sub", "+42");
            case SPACE_SUB -> claims.put("sub", " 42");
            case ZERO_SUB -> claims.put("sub", "0");
            case OVERFLOW_SUB -> claims.put("sub", "9223372036854775808");
            default -> { }
        }
        var signingKey = key.clone();
        if (fault == TokenFault.WRONG_KEY) signingKey[0] ^= 1;
        var token = sign(claims, signingKey);
        if (fault == TokenFault.TAMPERED) {
            var parts = token.split("\\.");
            // Change an ignored claim, keeping the subject and temporal claims valid.
            claims.put("name", "tampered");
            token = parts[0] + "." + new Payload(claims).toBase64URL() + "." + parts[2];
        }
        if (fault == TokenFault.MALFORMED) token = "not-a-jwt";
        var bearer = new BearerTokenAuthenticationToken(token);

        assertThatThrownBy(() -> provider.authenticate(bearer)).isInstanceOf(AuthenticationException.class);
        verifyNoInteractions(accounts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "42", "9223372036854775807"})
    void shouldResolveOnlyAfterRealVerificationWithoutNameRolesIssuerOrAudience(String subject) throws Exception {
        long id = Long.parseLong(subject);
        var principal = new AuthenticatedAccountPrincipal(id);
        when(accounts.resolve(id)).thenReturn(new AuthenticatedAccountService.ResolvedAccount(principal, Set.of()));
        var claims = validClaims(subject);
        claims.put("iat", now.getEpochSecond());
        claims.put("nbf", now.getEpochSecond());
        var result = provider.authenticate(new BearerTokenAuthenticationToken(sign(claims, key)));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isSameAs(principal);
        assertThat(result.getCredentials()).isNull();
        assertThat(result.getAuthorities()).isEmpty();
        verify(accounts).resolve(id);
        verifyNoMoreInteractions(accounts);
    }

    @Test
    void shouldRecheckTheInjectedClockForEveryAuthenticationWithoutDecoderSkew() throws Exception {
        var principal = new AuthenticatedAccountPrincipal(42L);
        when(accounts.resolve(42L)).thenReturn(new AuthenticatedAccountService.ResolvedAccount(principal, Set.of()));
        var bearer = new BearerTokenAuthenticationToken(sign(validClaims("42"), key));
        assertThat(provider.authenticate(bearer).getPrincipal()).isSameAs(principal);
        verify(accounts).resolve(42L);
        // The real decoder still accepts this token; the operation's clock reaches exp exactly.
        clock.advance(Duration.ofSeconds(120));

        assertThatThrownBy(() -> provider.authenticate(bearer)).isInstanceOf(AuthenticationException.class);
        verifyNoMoreInteractions(accounts);
    }

    private Map<String, Object> validClaims(String subject) {
        var claims = new HashMap<String, Object>();
        claims.put("sub", subject);
        claims.put("iat", now.minusSeconds(120).getEpochSecond());
        claims.put("exp", now.plusSeconds(120).getEpochSecond());
        return claims;
    }

    private static String sign(Map<String, Object> claims, byte[] signingKey) throws JOSEException {
        var token = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(claims));
        token.sign(new MACSigner(signingKey));
        return token.serialize();
    }

    private enum TokenFault {
        MALFORMED, TAMPERED, WRONG_KEY, MISSING_EXP, MISSING_IAT, UNUSABLE_EXP, UNUSABLE_IAT,
        UNUSABLE_NBF, EXPIRED, EXP_EQUALS_NOW, EXP_EQUALS_IAT, EXP_BEFORE_IAT, FUTURE_IAT,
        FUTURE_NBF, MISSING_SUB, NUMERIC_SUB, LEADING_ZERO_SUB, PLUS_SUB, SPACE_SUB, ZERO_SUB,
        OVERFLOW_SUB
    }
}
