package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.entities.OrderStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

/** Immutable own-order detail; preserves the item sequence supplied by the query service. */
public record OwnOrderDetailDTO(Long id,
                                @JsonFormat(shape = JsonFormat.Shape.STRING,
                                        pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
                                Instant createdAt, OrderStatus status, String total, List<OwnOrderItemDTO> items) {

    public OwnOrderDetailDTO {
        items = List.copyOf(items);
        OrderItemResponseDTO.requireMoney(total);
    }
}
