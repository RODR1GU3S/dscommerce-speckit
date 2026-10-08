package com.devsuperior.dscommerce.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable input data; strict JSON reading and Bean Validation belong to T033. */
public record OrderRequestDTO(List<OrderItemRequestDTO> items) {

    public OrderRequestDTO {
        // Preserve null lists/elements for the later input validation boundary.
        if (items != null) {
            items = Collections.unmodifiableList(new ArrayList<>(items));
        }
    }
}
