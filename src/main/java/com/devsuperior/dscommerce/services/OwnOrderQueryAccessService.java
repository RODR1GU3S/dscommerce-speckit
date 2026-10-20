package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.AuthenticatedAccountService.ResolvedAccount;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/** Current scalar account permissions, exclusively for querying one's own orders. */
@Service
public class OwnOrderQueryAccessService {

    private final UserAccountRepository repository;

    public OwnOrderQueryAccessService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ResolvedAccount resolve(AuthenticatedAccountPrincipal principal) {
        if (principal == null || principal.accountId() == null || principal.accountId() <= 0) {
            throw invalidCredential();
        }
        boolean exists;
        Set<GrantedAuthority> authorities = new HashSet<>();
        try {
            var rows = repository.findEffectiveAccountRolesById(principal.accountId());
            exists = !rows.isEmpty();
            for (var row : rows) {
                if (!principal.accountId().equals(row.getId())) {
                    throw new IllegalStateException("Inconsistent account projection");
                }
                var role = row.getRole();
                if (role == UserRole.CLIENT || role == UserRole.ADMIN) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
                }
            }
        } catch (RuntimeException cause) {
            throw new OrderAccountResolutionException("Account resolution could not be completed", cause);
        }
        if (!exists) {
            throw invalidCredential();
        }
        if (authorities.isEmpty()) {
            throw new AccessDeniedException("Access denied");
        }
        return new ResolvedAccount(principal, authorities);
    }

    /** Recheck at the start of the query service, before reading any order data. */
    @Transactional(readOnly = true)
    public Long requireAccountId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal principal)) {
            throw invalidCredential();
        }
        return resolve(principal).principal().accountId();
    }

    private static BadCredentialsException invalidCredential() {
        return new BadCredentialsException("Authentication required or invalid");
    }
}
