package com.devsuperior.dscommerce.services.exceptions;

/** Internal account lookup/mapping failure, distinct from invalid authentication. */
public class OrderAccountResolutionException extends RuntimeException {

    public OrderAccountResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
