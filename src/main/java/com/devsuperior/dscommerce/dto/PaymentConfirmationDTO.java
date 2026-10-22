package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.entities.OrderStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/** Immutable confirmation data; construction alone does not prove a committed settlement. */
public record PaymentConfirmationDTO(Long orderId,
                                     @JsonFormat(shape = JsonFormat.Shape.STRING,
                                             pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
                                     Instant occurredAt, OrderStatus status) {
    private static final Instant MIN_OCCURRENCE = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_OCCURRENCE = Instant.parse("9999-12-31T23:59:59.999Z");

    public PaymentConfirmationDTO {
        // A malformed internal result must fail before formatting can truncate or misrepresent it.
        if (orderId == null || orderId <= 0 || status != OrderStatus.PAID || occurredAt == null
                || occurredAt.getNano() % 1_000_000 != 0
                || occurredAt.isBefore(MIN_OCCURRENCE) || occurredAt.isAfter(MAX_OCCURRENCE)) {
            throw new IllegalArgumentException("Invalid payment confirmation");
        }
    }
}
