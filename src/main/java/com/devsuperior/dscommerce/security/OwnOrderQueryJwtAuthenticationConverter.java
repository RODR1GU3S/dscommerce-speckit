package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.services.OwnOrderQueryAccessService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** Keeps strict JWT conversion and replaces managed authorities with current scalar permissions. */
public class OwnOrderQueryJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final OrderJwtAuthenticationConverter delegate;
    private final OwnOrderQueryAccessService access;

    public OwnOrderQueryJwtAuthenticationConverter(OrderJwtAuthenticationConverter delegate,
                                                   OwnOrderQueryAccessService access) {
        this.delegate = delegate;
        this.access = access;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var authenticated = delegate.convert(jwt);
        var resolved = access.resolve((AuthenticatedAccountPrincipal) authenticated.getPrincipal());
        return UsernamePasswordAuthenticationToken.authenticated(
                resolved.principal(), null, resolved.authorities());
    }
}
