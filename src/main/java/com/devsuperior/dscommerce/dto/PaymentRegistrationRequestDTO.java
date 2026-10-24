package com.devsuperior.dscommerce.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Local input only; the order/reference window belongs to the payment service. */
public record PaymentRegistrationRequestDTO(@NotNull Instant occurredAt) { }
