package com.devsuperior.dscommerce.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Additive login domain: the historical predicate A, or a valid registration password B. */
public class LoginPasswordValidator implements ConstraintValidator<ValidLoginPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // trim defines A only. Neither this decision nor B replaces the original password.
        return value != null && (value.trim().length() > 0 || RegistrationInputRules.isValidPassword(value));
    }
}
