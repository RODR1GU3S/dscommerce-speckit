package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.dto.CustomerRegistrationConfirmationDTO;
import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.devsuperior.dscommerce.services.CustomerRegistrationService;
import com.devsuperior.dscommerce.services.exceptions.CustomerRegistrationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static com.devsuperior.dscommerce.services.exceptions.CustomerRegistrationException.Reason.INVALID_INPUT;

/** HTTP boundary only; the facade returns after completion of the separate worker transaction proxy. */
@RestController
public class CustomerRegistrationController {

    private final CustomerRegistrationService service;

    public CustomerRegistrationController(CustomerRegistrationService service) {
        this.service = service;
    }

    @PostMapping(value = "/customers", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CustomerRegistrationConfirmationDTO> register(
            @Valid @RequestBody CustomerRegistrationRequestDTO input, HttpServletRequest request) {
        if (request.getQueryString() != null) {
            throw new CustomerRegistrationException(INVALID_INPUT);
        }
        // Build HTTP success only after the facade has returned from the worker proxy call.
        var confirmation = Objects.requireNonNull(service.register(input));
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(confirmation);
    }
}
