package com.devsuperior.dscommerce.dto;

/** Current product name and historical line values, supplied by the query service. */
public record OwnOrderItemDTO(Long productId, String productName, Integer quantity,
                              String unitPrice, String subtotal) {

    public OwnOrderItemDTO {
        OrderItemResponseDTO.requireMoney(unitPrice);
        OrderItemResponseDTO.requireMoney(subtotal);
    }
}
