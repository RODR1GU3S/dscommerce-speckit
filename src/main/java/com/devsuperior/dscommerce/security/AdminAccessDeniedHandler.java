package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.dto.AdminErrorResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.List;

/** Writes the administrative permission error without exposing account or exception details. */
public class AdminAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper mapper;

    public AdminAccessDeniedHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        var status = HttpStatus.FORBIDDEN;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new AdminErrorResponseDTO(status.value(),
                status.getReasonPhrase(), "Product maintenance not permitted", request.getRequestURI(), List.of()));
    }
}
