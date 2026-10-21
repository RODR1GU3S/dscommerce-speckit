package com.devsuperior.dscommerce.controllers.handlers;

import com.devsuperior.dscommerce.controllers.OwnOrderQueryController;
import com.devsuperior.dscommerce.dto.ErrorResponseDTO;
import com.devsuperior.dscommerce.services.exceptions.OwnOrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Query-only errors; registration and other controllers retain their existing advice. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OwnOrderQueryController.class)
public class OwnOrderQueryExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponseDTO> input(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid order query data", request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> methodValidation(HandlerMethodValidationException exception,
                                                            HttpServletRequest request) {
        return exception.isForReturnValue()
                ? error(HttpStatus.INTERNAL_SERVER_ERROR, "Order query could not be completed", request)
                : error(HttpStatus.BAD_REQUEST, "Invalid order query data", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> authentication(BadCredentialsException exception, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "Authentication required or invalid", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> permission(AccessDeniedException exception, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "Order query not permitted", request);
    }

    @ExceptionHandler(OwnOrderNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> notFound(OwnOrderNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "Order not found", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> processing(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse nativeError && nativeError.getStatusCode().is4xxClientError()) {
            // Preserve native method/negotiation status and headers without forcing an unacceptable body.
            return ResponseEntity.status(nativeError.getStatusCode()).headers(nativeError.getHeaders()).build();
        }
        // Includes service/persistence validation, mapping and transaction-completion failures.
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Order query could not be completed", request);
    }

    private static ResponseEntity<ErrorResponseDTO> error(HttpStatus status, String message, HttpServletRequest request) {
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        if ("HEAD".equals(request.getMethod())) { return response.build(); }
        return response.body(new ErrorResponseDTO(status.value(), status.getReasonPhrase(),
                message, request.getRequestURI()));
    }
}
