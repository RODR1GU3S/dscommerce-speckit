package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.validation.ValidCustomerRegistration;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw input only: preserve nulls and every character until local validation/conversion. */
@ValidCustomerRegistration
public record CustomerRegistrationRequestDTO(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String name,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String fullName,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String phone,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String birthDate,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {

    @Override
    public String toString() {
        return "CustomerRegistrationRequestDTO[redacted]";
    }
}
