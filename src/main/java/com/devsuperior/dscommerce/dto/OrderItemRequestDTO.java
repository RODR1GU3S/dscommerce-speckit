package com.devsuperior.dscommerce.dto;

/** Consumer-supplied identifiers and quantities; input validation belongs to T033. */
public record OrderItemRequestDTO(Long productId, Integer quantity) {
}
