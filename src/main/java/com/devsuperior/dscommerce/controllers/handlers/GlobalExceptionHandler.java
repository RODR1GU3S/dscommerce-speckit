package com.devsuperior.dscommerce.controllers.handlers;

import com.devsuperior.dscommerce.dto.ErrorResponseDTO;
import com.devsuperior.dscommerce.services.exceptions.AuthenticationProcessingException;
import com.devsuperior.dscommerce.services.exceptions.InvalidCredentialsException;
import com.devsuperior.dscommerce.services.exceptions.InvalidOrderException;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String LOGIN_PATH = "/login";
    private static final String ORDER_PATH = "/orders";
    private static final String INVALID_ORDER_DATA_MESSAGE = "Invalid order data";
    private static final String ORDER_AUTHENTICATION_MESSAGE = "Authentication required or invalid";
    private static final String ORDER_ACCESS_MESSAGE = "Order registration not permitted";
    private static final String ORDER_PROCESSING_MESSAGE = "Order registration could not be completed";
    private static final String INVALID_LOGIN_DATA_MESSAGE = "Invalid login data";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    private static final String AUTHENTICATION_PROCESSING_MESSAGE =
            "Authentication could not be completed";

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleProductNotFound(ProductNotFoundException exception, HttpServletRequest request) {
        var status = HttpStatus.NOT_FOUND;
        var message = isOrderRequest(request) ? "Product not found" : exception.getMessage();
        return errorResponse(status, message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var status = HttpStatus.BAD_REQUEST;
        var message = isOrderRequest(request) ? INVALID_ORDER_DATA_MESSAGE : isLoginRequest(request)
                ? INVALID_LOGIN_DATA_MESSAGE
                : "Validation failed";
        var body = new ErrorResponseDTO(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        if (isOrderRequest(request)) {
            return errorResponse(HttpStatus.BAD_REQUEST, INVALID_ORDER_DATA_MESSAGE, request);
        }
        if (!isLoginRequest(request)) {
            throw exception;
        }
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                INVALID_LOGIN_DATA_MESSAGE,
                request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                isOrderRequest(request) ? ORDER_AUTHENTICATION_MESSAGE : INVALID_CREDENTIALS_MESSAGE,
                request);
    }

    @ExceptionHandler(AuthenticationProcessingException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthenticationProcessing(
            AuthenticationProcessingException exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                isOrderRequest(request) ? ORDER_PROCESSING_MESSAGE : AUTHENTICATION_PROCESSING_MESSAGE,
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        if (isOrderRequest(request)) {
            // Incoming DTO validation is handled by handleValidation; service/persistence violations are internal.
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ORDER_PROCESSING_MESSAGE, request);
        }
        var status = HttpStatus.BAD_REQUEST;
        var message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("Validation failed");
        var body = new ErrorResponseDTO(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidOrder(
            InvalidOrderException exception, HttpServletRequest request) {
        if (!isOrderRequest(request)) {
            throw exception;
        }
        var message = switch (exception.getReason()) {
            case DUPLICATE_PRODUCT -> "Duplicate product in order";
            case INVALID_PRODUCT_PRICE -> "Product price is not valid for order";
            case UNREPRESENTABLE_VALUE -> "Order value cannot be represented";
        };
        return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, message, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleOrderAuthentication(
            BadCredentialsException exception, HttpServletRequest request) {
        if (!isOrderRequest(request)) {
            throw exception;
        }
        return errorResponse(HttpStatus.UNAUTHORIZED, ORDER_AUTHENTICATION_MESSAGE, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleOrderAccess(
            AccessDeniedException exception, HttpServletRequest request) {
        if (!isOrderRequest(request)) {
            throw exception;
        }
        return errorResponse(HttpStatus.FORBIDDEN, ORDER_ACCESS_MESSAGE, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleOrderProcessing(
            Exception exception, HttpServletRequest request) throws Exception {
        // Let MVC preserve client-error statuses and headers; keep internal failures safely sanitized.
        if (!isOrderRequest(request)
                || (exception instanceof ErrorResponse error && error.getStatusCode().is4xxClientError())) {
            throw exception;
        }
        // Includes failures raised by transaction completion after the service method returns.
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ORDER_PROCESSING_MESSAGE, request);
    }

    private static ResponseEntity<ErrorResponseDTO> errorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request) {
        var body = new ErrorResponseDTO(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        var response = ResponseEntity.status(status);
        if (status == HttpStatus.UNAUTHORIZED && isOrderRequest(request)) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(body);
    }

    private static boolean isOrderRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && ORDER_PATH.equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    private static boolean isLoginRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String applicationPath = requestUri.substring(contextPath.length());
        return LOGIN_PATH.equals(applicationPath);
    }
}
