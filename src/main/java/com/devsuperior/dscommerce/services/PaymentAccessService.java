package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException.Reason.*;

/** Current scalar ADMIN permission for payment only; never consults a managed roles collection. */
@Service
public class PaymentAccessService {
    private final UserAccountRepository accounts;

    public PaymentAccessService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public void requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal principal)
                || principal.accountId() == null || principal.accountId() <= 0) {
            throw new PaymentRegistrationException(UNAUTHENTICATED);
        }
        final boolean exists;
        boolean admin = false;
        try {
            var rows = accounts.findEffectiveAccountRolesById(principal.accountId());
            exists = !rows.isEmpty();
            for (var row : rows) {
                if (!principal.accountId().equals(row.getId())) {
                    throw new IllegalStateException("Inconsistent account projection");
                }
                admin |= row.getRole() == UserRole.ADMIN;
            }
        } catch (RuntimeException cause) {
            throw new PaymentRegistrationException(PROCESSING_FAILURE, cause);
        }
        if (!exists) { throw new PaymentRegistrationException(UNAUTHENTICATED); }
        if (!admin) { throw new PaymentRegistrationException(FORBIDDEN); }
    }
}
