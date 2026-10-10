package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
import com.nimbusds.jose.JWSObject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.text.ParseException;
import java.util.regex.Pattern;

/** Adapts an already decoded JWT to the current database identity, without trusting name/roles claims. */
public class OrderJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Pattern SUBJECT = Pattern.compile("[1-9][0-9]*");
    private final AuthenticatedAccountService accounts;
    private final Clock clock;

    public OrderJwtAuthenticationConverter(AuthenticatedAccountService accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var expiresAt = requiredInstant(jwt, "exp");
        var issuedAt = requiredInstant(jwt, "iat");
        var notBefore = jwt.getClaims().containsKey("nbf") ? requiredInstant(jwt, "nbf") : null;
        var now = clock.instant();
        if (!expiresAt.isAfter(now) || !expiresAt.isAfter(issuedAt) || issuedAt.isAfter(now)
                || (notBefore != null && notBefore.isAfter(now))) {
            throw invalidCredential();
        }
        Object subject = jwt.getClaims().get("sub");
        if (!(subject instanceof String text) || !SUBJECT.matcher(text).matches()) {
            throw invalidCredential();
        }
        // Nimbus normalizes a numeric sub to String while decoding. Inspect the original payload
        // only after the decoder has verified it; do not accept that lossy type conversion.
        try {
            var originalClaims = JWSObject.parse(jwt.getTokenValue()).getPayload().toJSONObject();
            if (originalClaims == null || !text.equals(originalClaims.get("sub"))) {
                throw invalidCredential();
            }
        } catch (ParseException failure) {
            throw invalidCredential();
        }
        Long accountId;
        try {
            accountId = Long.valueOf(text);
        } catch (NumberFormatException failure) {
            throw invalidCredential();
        }
        // Preserve invalid-credential and internal-resolution exception types from this service.
        var resolved = accounts.resolve(accountId);
        return UsernamePasswordAuthenticationToken.authenticated(resolved.principal(), null, resolved.authorities());
    }

    private static Instant requiredInstant(Jwt jwt, String claim) {
        Object value = jwt.getClaims().get(claim);
        if (!(value instanceof Instant instant)) {
            throw invalidCredential();
        }
        return instant;
    }

    private static BadCredentialsException invalidCredential() {
        return new BadCredentialsException("Authentication required or invalid");
    }
}
