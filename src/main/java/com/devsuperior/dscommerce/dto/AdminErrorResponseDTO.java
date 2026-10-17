package com.devsuperior.dscommerce.dto;

import java.util.List;

public record AdminErrorResponseDTO(int status, String error, String message, String path,
                                   List<AdminFieldErrorDTO> errors) {

    public AdminErrorResponseDTO {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
