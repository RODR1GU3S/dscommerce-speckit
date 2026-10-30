package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.CustomerRegistrationConfirmationDTO;
import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.devsuperior.dscommerce.repositories.UserAccountInsertRepository.RegistrationIdentityMetadata;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.RegistrationPasswordHasher;
import com.devsuperior.dscommerce.services.exceptions.CustomerRegistrationException;
import com.devsuperior.dscommerce.validation.RegistrationInputRules;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

import static com.devsuperior.dscommerce.services.exceptions.CustomerRegistrationException.Reason.CONFLICT;
import static com.devsuperior.dscommerce.services.exceptions.CustomerRegistrationException.Reason.INVALID_INPUT;
import static com.devsuperior.dscommerce.services.exceptions.CustomerRegistrationException.Reason.PROCESSING_FAILURE;

/** Prepares registration outside the write transaction and returns only after the worker proxy completes. */
@Service
public class CustomerRegistrationService {

    private final Validator validator;
    private final RegistrationAttemptReference reference;
    private final RegistrationPasswordHasher hasher;
    private final UserAccountRepository accounts;
    private final CustomerRegistrationTransaction transaction;

    public CustomerRegistrationService(Validator validator, RegistrationAttemptReference reference,
                                       RegistrationPasswordHasher hasher, UserAccountRepository accounts,
                                       CustomerRegistrationTransaction transaction) {
        this.validator = validator;
        this.reference = reference;
        this.hasher = hasher;
        this.accounts = accounts;
        this.transaction = transaction;
    }

    public CustomerRegistrationConfirmationDTO register(CustomerRegistrationRequestDTO request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CustomerRegistrationException(PROCESSING_FAILURE);
        }

        RegistrationAttemptReference.Snapshot attempt;
        boolean valid;
        try {
            attempt = reference.snapshot();
            valid = request != null && validator.validate(request).isEmpty();
        } catch (RuntimeException preparationFailure) {
            throw new CustomerRegistrationException(PROCESSING_FAILURE);
        }
        if (!valid) {
            throw new CustomerRegistrationException(INVALID_INPUT);
        }

        CustomerRegistrationCommand command;
        try {
            String fullName = RegistrationInputRules.trimExternalSpaces(request.fullName());
            String email = RegistrationInputRules.trimExternalSpaces(request.email());
            byte[] emailKey = RegistrationInputRules.emailKey(email);
            var birthDate = RegistrationInputRules.parseBirthDate(request.birthDate());
            String passwordHash = hasher.hash(request.password());
            command = new CustomerRegistrationCommand(request.name(), fullName, email, emailKey,
                    request.phone(), birthDate, passwordHash, attempt);
        } catch (RuntimeException preparationFailure) {
            throw new CustomerRegistrationException(PROCESSING_FAILURE);
        }

        boolean nameTaken;
        boolean emailTaken;
        try {
            nameTaken = accounts.findByName(command.name()).isPresent();
            emailTaken = accounts.findByEmailKey(command.emailKey()).isPresent();
        } catch (RuntimeException lookupFailure) {
            throw new CustomerRegistrationException(PROCESSING_FAILURE);
        }
        if (nameTaken || emailTaken) {
            // A positive lookup diagnoses an existing identity; a free lookup never guarantees uniqueness.
            throw new CustomerRegistrationException(CONFLICT);
        }

        RegistrationIdentityMetadata metadata;
        try {
            // Capture while healthy, before entering the write transaction; never recover after failure.
            metadata = accounts.registrationIdentityMetadata();
        } catch (RuntimeException metadataFailure) {
            throw new CustomerRegistrationException(PROCESSING_FAILURE);
        }
        if (!CustomerRegistrationPersistenceFailureClassifier.hasValidIdentityMetadata(metadata)) {
            throw new CustomerRegistrationException(PROCESSING_FAILURE);
        }

        try {
            // The injected worker is a separate Spring bean: this call includes transaction completion.
            return Objects.requireNonNull(transaction.register(command), "Registration confirmation is required");
        } catch (RuntimeException transactionFailure) {
            // The proxy has finished: classify only its proven identity diagnosis, using the earlier snapshot.
            throw new CustomerRegistrationException(
                    CustomerRegistrationPersistenceFailureClassifier.isRecognizedConflict(transactionFailure, metadata)
                            ? CONFLICT : PROCESSING_FAILURE);
        }
    }
}
