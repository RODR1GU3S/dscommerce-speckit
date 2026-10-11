package com.devsuperior.dscommerce.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Consumer-supplied positive identifiers and representable quantities. */
public record OrderItemRequestDTO(@NotNull @Positive Long productId,
                                  @NotNull @Positive @Max(2147483647) Integer quantity) {
}
