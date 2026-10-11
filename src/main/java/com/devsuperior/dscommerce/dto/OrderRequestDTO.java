package com.devsuperior.dscommerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable input data, validated at the controller boundary. */
public record OrderRequestDTO(@NotEmpty @Valid List<@NotNull OrderItemRequestDTO> items) {

    public OrderRequestDTO {
        // Keep null lists/elements available to Bean Validation at the boundary.
        if (items != null) {
            items = Collections.unmodifiableList(new ArrayList<>(items));
        }
    }
}
