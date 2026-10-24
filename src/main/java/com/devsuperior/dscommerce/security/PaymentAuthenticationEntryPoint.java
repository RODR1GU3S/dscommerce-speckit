package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.dto.ErrorResponseDTO;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException;
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

/** Adapts known conversion/guard failures without confusing internal lookups with invalid credentials. */
public class PaymentAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper mapper;
    private final PaymentAccessDeniedHandler denied;

    public PaymentAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
        this.denied = new PaymentAccessDeniedHandler(mapper);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        var rejection = exception.getCause() instanceof PaymentRegistrationException payment ? payment : null;
        if (rejection != null && rejection.getReason() == PaymentRegistrationException.Reason.FORBIDDEN) {
            denied.handle(request, response, new AccessDeniedException("Payment registration not permitted"));
            return;
        }
        boolean internal = exception.getCause() instanceof OrderAccountResolutionException
                || (rejection != null && rejection.getReason() != PaymentRegistrationException.Reason.UNAUTHENTICATED);
        var status = internal ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.UNAUTHORIZED;
        var message = internal ? "Payment registration could not be completed" : "Authentication required or invalid";
        if (!internal) { response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer"); }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (!"HEAD".equals(request.getMethod())) {
            mapper.writeValue(response.getOutputStream(), new ErrorResponseDTO(status.value(), status.getReasonPhrase(),
                    message, request.getRequestURI()));
        }
    }
}
