package com.devsuperior.dscommerce.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreType;

import java.time.LocalDate;

/** Internal values after validation/conversion and hashing; never a request or response DTO. */
@JsonIgnoreType
public record CustomerRegistrationCommand(
        @JsonIgnore String name,
        @JsonIgnore String fullName,
        @JsonIgnore String email,
        @JsonIgnore byte[] emailKey,
        @JsonIgnore String phone,
        @JsonIgnore LocalDate birthDate,
        @JsonIgnore String passwordHash,
        @JsonIgnore RegistrationAttemptReference.Snapshot reference) {

    public CustomerRegistrationCommand {
        if (name == null || fullName == null || email == null || emailKey == null || phone == null
                || birthDate == null || passwordHash == null || reference == null) {
            throw new IllegalArgumentException("Invalid internal customer registration command");
        }
        emailKey = emailKey.clone();
    }

    @Override
    @JsonIgnore
    public byte[] emailKey() {
        return emailKey.clone();
    }

    @Override
    public String toString() {
        return "CustomerRegistrationCommand[redacted]";
    }
}
