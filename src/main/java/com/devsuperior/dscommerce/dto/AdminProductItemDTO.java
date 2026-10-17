package com.devsuperior.dscommerce.dto;

/** Administrative read data; the mapping service supplies an exact two-decimal price string. */
public record AdminProductItemDTO(Long id, String name, String image, String price) {
}
