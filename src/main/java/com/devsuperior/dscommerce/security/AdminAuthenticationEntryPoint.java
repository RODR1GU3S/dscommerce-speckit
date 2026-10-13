package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.dto.AdminErrorResponseDTO;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.util.List;

/** Writes administrative authentication errors without exposing credential or lookup details. */
public class AdminAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper;

    public AdminAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        boolean resolutionFailure = exception.getCause() instanceof OrderAccountResolutionException;
        var status = resolutionFailure ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.UNAUTHORIZED;
        var message = resolutionFailure ? "Product maintenance could not be completed"
                : "Authentication required or invalid";
        if (!resolutionFailure) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new AdminErrorResponseDTO(status.value(),
                status.getReasonPhrase(), message, request.getRequestURI(), List.of()));
    }
}
