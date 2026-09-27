package com.devsuperior.dscommerce.services.exceptions;

public class AuthenticationProcessingException extends RuntimeException {

    public AuthenticationProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
