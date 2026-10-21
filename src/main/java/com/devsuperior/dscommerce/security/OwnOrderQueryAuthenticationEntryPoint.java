package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.dto.ErrorResponseDTO;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/** Fixed query errors, including lookup/permission failures raised during JWT conversion. */
public class OwnOrderQueryAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper;
    private final OwnOrderQueryAccessDeniedHandler deniedHandler;

    public OwnOrderQueryAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
        this.deniedHandler = new OwnOrderQueryAccessDeniedHandler(mapper);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        if (exception.getCause() instanceof AccessDeniedException denied) {
            deniedHandler.handle(request, response, denied);
            return;
        }
        boolean internal = exception.getCause() instanceof OrderAccountResolutionException;
        var status = internal ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.UNAUTHORIZED;
        var message = internal ? "Order query could not be completed" : "Authentication required or invalid";
        if (!internal) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (!"HEAD".equals(request.getMethod())) {
            mapper.writeValue(response.getOutputStream(), new ErrorResponseDTO(
                    status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
        }
    }
}
