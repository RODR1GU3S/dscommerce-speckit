package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.dto.PaymentConfirmationDTO;
import com.devsuperior.dscommerce.dto.PaymentRegistrationRequestDTO;
import com.devsuperior.dscommerce.services.PaymentRegistrationService;
import com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** HTTP boundary only; the injected service proxy owns settlement and transaction completion. */
@RestController
public class AdminPaymentController {
    private final PaymentRegistrationService service;

    public AdminPaymentController(PaymentRegistrationService service) {
        this.service = service;
    }

    @PostMapping(value = "/admin/orders/{id}/payments", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentConfirmationDTO> register(@PathVariable("id") @Positive Long id,
                                                          @Valid @RequestBody PaymentRegistrationRequestDTO input,
                                                          Authentication authentication, HttpServletRequest request) {
        if (request.getQueryString() != null) {
            throw new PaymentRegistrationException(PaymentRegistrationException.Reason.INVALID_INPUT);
        }
        // No outer transaction: build 201 only after the service proxy returns successfully.
        var confirmation = Objects.requireNonNull(service.register(id, input, authentication));
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(confirmation);
    }
}
