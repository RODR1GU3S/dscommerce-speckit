package com.devsuperior.dscommerce.dto;

public record ErrorResponseDTO(int status, String error, String message, String path) {
}
