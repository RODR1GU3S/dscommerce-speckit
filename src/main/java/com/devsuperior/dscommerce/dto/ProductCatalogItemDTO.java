package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.entities.Product;

import java.math.BigDecimal;

public record ProductCatalogItemDTO(
        Long id,
        String name,
        String image,
        BigDecimal price)
{

    public ProductCatalogItemDTO(Product product) {
        this(
                product.getId(),
                product.getName(),
                product.getImage(),
                product.getPrice());
    }
}
