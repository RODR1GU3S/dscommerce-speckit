package com.devsuperior.dscommerce.services.exceptions;

/** The owner-restricted lookup has no result; never distinguishes absent from foreign orders. */
public class OwnOrderNotFoundException extends RuntimeException {

    public OwnOrderNotFoundException() {
        super("Order not found");
    }
}
