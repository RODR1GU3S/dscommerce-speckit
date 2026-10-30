package com.devsuperior.dscommerce.controllers.handlers;

import com.devsuperior.dscommerce.controllers.CustomerRegistrationController;
import com.devsuperior.dscommerce.dto.ErrorResponseDTO;
import com.devsuperior.dscommerce.services.exceptions.CustomerRegistrationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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

/** Registration-only error boundary; persistence and return validation are internal failures. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CustomerRegistrationController.class)
public class CustomerRegistrationExceptionHandler {

    private static final String INVALID_INPUT_MESSAGE = "Invalid customer registration data";
    private static final String PROCESSING_FAILURE_MESSAGE = "Customer registration could not be completed";

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponseDTO> input(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, INVALID_INPUT_MESSAGE, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> methodValidation(HandlerMethodValidationException exception,
                                                            HttpServletRequest request) {
        return exception.isForReturnValue()
                ? error(HttpStatus.INTERNAL_SERVER_ERROR, PROCESSING_FAILURE_MESSAGE, request)
                : error(HttpStatus.BAD_REQUEST, INVALID_INPUT_MESSAGE, request);
    }

    @ExceptionHandler(CustomerRegistrationException.class)
    public ResponseEntity<ErrorResponseDTO> registration(CustomerRegistrationException exception,
                                                        HttpServletRequest request) {
        // This final exception carries only the closed categories and fixed messages of the contract.
        return error(HttpStatus.valueOf(exception.getStatus()), exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> processing(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse nativeError
                && java.util.List.of(405, 406, 415).contains(nativeError.getStatusCode().value())) {
            return ResponseEntity.status(nativeError.getStatusCode()).headers(nativeError.getHeaders()).build();
        }
        // Includes JPA constraints, mapping, flush and proxy commit failures; never infer a conflict or recover.
        return error(HttpStatus.INTERNAL_SERVER_ERROR, PROCESSING_FAILURE_MESSAGE, request);
    }

    private static ResponseEntity<ErrorResponseDTO> error(HttpStatus status, String message, HttpServletRequest request) {
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
        if ("HEAD".equals(request.getMethod())) { return response.build(); }
        return response.body(new ErrorResponseDTO(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
