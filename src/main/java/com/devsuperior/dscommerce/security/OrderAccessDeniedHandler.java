package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.dto.ErrorResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** Writes the order permission error without exposing account or exception details. */
public class OrderAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper mapper;

    public OrderAccessDeniedHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        var status = HttpStatus.FORBIDDEN;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ErrorResponseDTO(status.value(), status.getReasonPhrase(),
                "Order registration not permitted", request.getRequestURI()));
    }
}
