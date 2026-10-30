package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.validation.ValidLoginPassword;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank String name,
        @ValidLoginPassword String password
) {
}
