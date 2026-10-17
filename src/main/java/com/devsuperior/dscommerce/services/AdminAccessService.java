package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/** Rechecks the account and current database authorities before administrative access. */
@Service
public class AdminAccessService {

    private final AuthenticatedAccountService accounts;

    public AdminAccessService(AuthenticatedAccountService accounts) {
        this.accounts = accounts;
    }

    /** Resolves the current account on every call; authentication and lookup failures propagate unchanged. */
    public void requireAdmin(AuthenticatedAccountPrincipal principal) {
        if (principal == null || principal.accountId() == null || principal.accountId() <= 0) {
            throw new BadCredentialsException("Authentication required or invalid");
        }
        var current = accounts.resolve(principal.accountId());
        if (current.authorities().stream().noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Product maintenance not permitted");
        }
    }
}
