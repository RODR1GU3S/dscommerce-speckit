package com.devsuperior.dscommerce.services.exceptions;

/** Payment-specific application categories with fixed, safe public messages. */
public class PaymentRegistrationException extends RuntimeException {
    public enum Reason {
        INVALID_INPUT(400, "Invalid payment data"),
        UNAUTHENTICATED(401, "Authentication required or invalid"),
        FORBIDDEN(403, "Payment registration not permitted"),
        ORDER_NOT_FOUND(404, "Order not found"),
        ALREADY_REGISTERED(409, "Payment already registered"),
        INCOMPATIBLE_STATE(409, "Order is not awaiting payment"),
        PROCESSING_FAILURE(500, "Payment registration could not be completed");

        private final int status;
        private final String message;

        Reason(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public int getStatus() { return status; }
    }

    private final Reason reason;

    public PaymentRegistrationException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public PaymentRegistrationException(Reason reason, Throwable cause) {
        super(reason.message, cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
    public int getStatus() { return reason.status; }
}
