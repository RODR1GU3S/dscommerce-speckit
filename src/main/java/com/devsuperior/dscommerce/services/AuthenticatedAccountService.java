package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/** Resolves the current account identity and database authorities without exposing persistence. */
@Service
public class AuthenticatedAccountService {

    private static final String AUTHENTICATION_FAILURE_MESSAGE = "Authentication required or invalid";
    private static final String RESOLUTION_FAILURE_MESSAGE = "Account resolution could not be completed";

    private final UserAccountRepository repository;

    public AuthenticatedAccountService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ResolvedAccount resolve(Long accountId) {
        ResolvedAccount resolved;
        try {
            resolved = repository.findByIdWithRoles(accountId)
                    .map(account -> new ResolvedAccount(
                            new AuthenticatedAccountPrincipal(account.getId()),
                            account.getRoles().stream()
                                    .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                                    .collect(Collectors.toUnmodifiableSet())))
                    .orElse(null);
        } catch (RuntimeException cause) {
            throw new OrderAccountResolutionException(RESOLUTION_FAILURE_MESSAGE, cause);
        }
        if (resolved == null) {
            throw new BadCredentialsException(AUTHENTICATION_FAILURE_MESSAGE);
        }
        return resolved;
    }

    public record ResolvedAccount(AuthenticatedAccountPrincipal principal, Set<GrantedAuthority> authorities) {
        public ResolvedAccount {
            authorities = Set.copyOf(authorities);
        }
    }
}
