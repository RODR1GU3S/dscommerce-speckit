package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.dto.ErrorResponseDTO;
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

/** Writes fixed order authentication errors directly from the security filters. */
public class OrderAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper;

    public OrderAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        boolean resolutionFailure = exception.getCause() instanceof OrderAccountResolutionException;
        var status = resolutionFailure ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.UNAUTHORIZED;
        var message = resolutionFailure ? "Order registration could not be completed"
                : "Authentication required or invalid";
        if (!resolutionFailure) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(),
                new ErrorResponseDTO(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
