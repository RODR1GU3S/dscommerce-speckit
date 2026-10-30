package com.devsuperior.dscommerce.dto;

/** Confirmation data only; constructing it does not establish a completed commit or HTTP delivery. */
public record CustomerRegistrationConfirmationDTO(Long id, String name) {

    public CustomerRegistrationConfirmationDTO {
        if (id == null || id <= 0 || name == null) {
            throw new IllegalArgumentException("Invalid customer registration confirmation");
        }
    }
}
