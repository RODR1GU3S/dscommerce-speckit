package com.devsuperior.dscommerce.services.exceptions;

import java.util.Objects;

/** Registration-only categories: fixed public messages, without input or persistence diagnostics. */
public final class CustomerRegistrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        INVALID_INPUT(400, "Invalid customer registration data"),
        CONFLICT(409, "Customer registration not available"),
        PROCESSING_FAILURE(500, "Customer registration could not be completed");

        private final int status;
        private final String message;

        Reason(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public int getStatus() { return status; }
    }

    private final Reason reason;

    public CustomerRegistrationException(Reason reason) {
        // Fix the cause to null and disable suppression so raw parser/SQL failures cannot be attached.
        super(Objects.requireNonNull(reason, "Registration failure reason is required").message, null, false, true);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
    public int getStatus() { return reason.status; }
}
