package com.devsuperior.dscommerce.services.exceptions;

/** Business rejection with a fixed, explicit reason; HTTP translation belongs to the order advice. */
public class InvalidOrderException extends RuntimeException {

    public enum Reason {
        DUPLICATE_PRODUCT("Duplicate product in order"),
        INVALID_PRODUCT_PRICE("Product price is not valid for order"),
        UNREPRESENTABLE_VALUE("Order value cannot be represented");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    public InvalidOrderException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
