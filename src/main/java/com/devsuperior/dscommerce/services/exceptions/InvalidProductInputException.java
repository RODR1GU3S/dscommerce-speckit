package com.devsuperior.dscommerce.services.exceptions;

import com.devsuperior.dscommerce.dto.AdminFieldErrorDTO;

import java.util.List;

/** Complete safe input diagnoses from the services; HTTP presentation belongs to the administrative advice. */
public final class InvalidProductInputException extends RuntimeException {
    private final List<AdminFieldErrorDTO> errors;

    public InvalidProductInputException(List<AdminFieldErrorDTO> errors) {
        super("Invalid product maintenance data");
        this.errors = List.copyOf(errors);
        if (this.errors.isEmpty()) throw new IllegalArgumentException("Input rejection requires a diagnosis");
    }

    public List<AdminFieldErrorDTO> getErrors() {
        return errors;
    }
}
