package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.entities.Category;
import com.devsuperior.dscommerce.entities.Product;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailDTO(
        Long id,
        String name,
        String description,
        String image,
        BigDecimal price,
        List<String> categories
) {

    public ProductDetailDTO {
        categories = List.copyOf(categories);
    }

    public ProductDetailDTO(Product product) {
        this(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getImage(),
                product.getPrice(),
                product.getCategories().stream()
                        .map(Category::getName)
                        .distinct()
                        .sorted()
                        .toList()
        );
    }
}
