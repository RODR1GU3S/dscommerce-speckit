package com.devsuperior.dscommerce.controllers.handlers;

import com.devsuperior.dscommerce.controllers.AdminPaymentController;
import com.devsuperior.dscommerce.dto.ErrorResponseDTO;
import com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Payment-only error boundary. Persistence/return validation is internal, never consumer input diagnosis. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdminPaymentController.class)
public class PaymentRegistrationExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponseDTO> input(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid payment data", request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> methodValidation(HandlerMethodValidationException exception,
                                                            HttpServletRequest request) {
        return exception.isForReturnValue()
                ? error(HttpStatus.INTERNAL_SERVER_ERROR, "Payment registration could not be completed", request)
                : error(HttpStatus.BAD_REQUEST, "Invalid payment data", request);
    }

    @ExceptionHandler(PaymentRegistrationException.class)
    public ResponseEntity<ErrorResponseDTO> payment(PaymentRegistrationException exception, HttpServletRequest request) {
        return error(HttpStatus.valueOf(exception.getStatus()), exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> processing(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse nativeError
                && java.util.List.of(405, 406, 415).contains(nativeError.getStatusCode().value())) {
            return ResponseEntity.status(nativeError.getStatusCode()).headers(nativeError.getHeaders()).build();
        }
        // Includes entity constraints, mapping and transaction-completion failures. No recovery queries.
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Payment registration could not be completed", request);
    }

    private static ResponseEntity<ErrorResponseDTO> error(HttpStatus status, String message, HttpServletRequest request) {
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
        if (status == HttpStatus.UNAUTHORIZED) { response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer"); }
        if ("HEAD".equals(request.getMethod())) { return response.build(); }
        return response.body(new ErrorResponseDTO(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
