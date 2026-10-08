package com.devsuperior.dscommerce.dto;

import java.util.regex.Pattern;

/** Immutable historical line data; monetary strings are already formatted by the service. */
public record OrderItemResponseDTO(Long productId, Integer quantity, String unitPrice, String subtotal) {

    private static final Pattern MONEY = Pattern.compile("(0\\.(0[1-9]|[1-9][0-9])|[1-9][0-9]{0,16}\\.[0-9]{2})");

    public OrderItemResponseDTO {
        requireMoney(unitPrice);
        requireMoney(subtotal);
    }

    // Checks the wire format only; no calculation, normalization or rounding here.
    static void requireMoney(String value) {
        if (value == null || !MONEY.matcher(value).matches()) {
            throw new IllegalArgumentException("Money must be a positive decimal19,2 string with exactly two fraction digits");
        }
    }
}
