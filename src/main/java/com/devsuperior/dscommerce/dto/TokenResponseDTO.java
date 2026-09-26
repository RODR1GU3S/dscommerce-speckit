package com.devsuperior.dscommerce.dto;

public record TokenResponseDTO(
        String accessToken,
        String tokenType
) {
}
