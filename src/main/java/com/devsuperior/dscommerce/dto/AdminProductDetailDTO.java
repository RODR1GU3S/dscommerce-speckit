package com.devsuperior.dscommerce.dto;

import java.util.List;

/** Administrative read data, preserving legacy values and distinct category identifiers. */
public record AdminProductDetailDTO(Long id, String name, String description, String image, String price,
                                    List<CategoryOptionDTO> categories) {

    public AdminProductDetailDTO {
        categories = List.copyOf(categories);
    }
}
