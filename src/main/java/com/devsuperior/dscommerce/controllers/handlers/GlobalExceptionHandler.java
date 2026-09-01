package com.devsuperior.dscommerce.controllers.handlers;

import com.devsuperior.dscommerce.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var status = HttpStatus.BAD_REQUEST;
        var body = new ErrorResponseDTO(status.value(), status.getReasonPhrase(), "Validation failed", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
