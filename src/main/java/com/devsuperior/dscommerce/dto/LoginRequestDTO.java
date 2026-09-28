package com.devsuperior.dscommerce.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank String name,
        @NotBlank String password
) {
}
