package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** Adapts an already decoded JWT to the current database identity, without trusting name/roles claims. */
public class OrderJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AuthenticatedAccountService accounts;

    public OrderJwtAuthenticationConverter(AuthenticatedAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long accountId;
        try {
            accountId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException failure) {
            throw new BadCredentialsException("Authentication required or invalid");
        }
        if (accountId <= 0) {
            throw new BadCredentialsException("Authentication required or invalid");
        }
        // Preserve invalid-credential and internal-resolution exception types from this service.
        var resolved = accounts.resolve(accountId);
        return UsernamePasswordAuthenticationToken.authenticated(resolved.principal(), null, resolved.authorities());
    }
}
