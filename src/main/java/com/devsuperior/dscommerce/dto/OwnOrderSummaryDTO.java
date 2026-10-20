package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.entities.OrderStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/** Immutable own-order summary; historical money is calculated and formatted by the query service. */
public record OwnOrderSummaryDTO(Long id,
                                 @JsonFormat(shape = JsonFormat.Shape.STRING,
                                         pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
                                 Instant createdAt, OrderStatus status, String total) {

    public OwnOrderSummaryDTO {
        OrderItemResponseDTO.requireMoney(total);
    }
}
