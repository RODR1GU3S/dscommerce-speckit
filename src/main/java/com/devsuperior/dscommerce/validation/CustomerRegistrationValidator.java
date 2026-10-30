package com.devsuperior.dscommerce.validation;

import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.devsuperior.dscommerce.services.RegistrationAttemptReference;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Validator;

/** Local validation only; the fixed reference is supplied by the caller, never recaptured here. */
public class CustomerRegistrationValidator
        implements ConstraintValidator<ValidCustomerRegistration, CustomerRegistrationRequestDTO> {

    private final RegistrationAttemptReference reference;
    private final Validator validator;

    public CustomerRegistrationValidator(RegistrationAttemptReference reference, Validator validator) {
        this.reference = reference;
        this.validator = validator;
    }

    @Override
    public boolean isValid(CustomerRegistrationRequestDTO input, ConstraintValidatorContext context) {
        if (input == null) { return false; }
        // An absent/uninitialized scope/reference is a preparation failure, not invalid consumer input.
        var referenceDate = reference.snapshot().utcDate();
        return RegistrationInputRules.isValidName(input.name())
                && RegistrationInputRules.isValidFullName(input.fullName())
                && RegistrationInputRules.isValidEmail(input.email(), validator)
                && RegistrationInputRules.isValidPhone(input.phone())
                && RegistrationInputRules.isValidBirthDate(input.birthDate(), referenceDate)
                && RegistrationInputRules.isValidPassword(input.password());
    }
}
