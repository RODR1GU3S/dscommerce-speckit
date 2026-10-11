package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.entities.OrderStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

/** Immutable confirmation data, mapped from the aggregate by the service in T020. */
public record OrderResponseDTO(Long id, Long accountId,
                               @JsonFormat(shape = JsonFormat.Shape.STRING,
                                       pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
                               Instant createdAt, OrderStatus status,
                               List<OrderItemResponseDTO> items, String total) {

    public OrderResponseDTO {
        items = List.copyOf(items);
        OrderItemResponseDTO.requireMoney(total);
    }
}
