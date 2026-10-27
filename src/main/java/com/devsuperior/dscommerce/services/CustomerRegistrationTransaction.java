package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.CustomerRegistrationConfirmationDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Owns the account/CLIENT write; callers must enter through the separate Spring transaction proxy. */
@Service
public class CustomerRegistrationTransaction {

    private final UserAccountRepository accounts;

    public CustomerRegistrationTransaction(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public CustomerRegistrationConfirmationDTO register(CustomerRegistrationCommand command) {
        var account = new UserAccount(command.name(), command.passwordHash(), command.fullName(), command.email(),
                command.emailKey(), command.phone(), command.birthDate());
        account.getRoles().add(UserRole.CLIENT);
        accounts.insert(account);
        accounts.flush();
        // Internal confirmation only: the proxy still has to complete the commit after this body returns.
        return new CustomerRegistrationConfirmationDTO(account.getId(), account.getName());
    }
}
