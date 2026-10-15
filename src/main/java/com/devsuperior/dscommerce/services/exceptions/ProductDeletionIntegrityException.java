package com.devsuperior.dscommerce.services.exceptions;

import org.springframework.dao.DataIntegrityViolationException;

/** A positive OrderItem precheck, distinct from an unclassified persistence failure. */
public final class ProductDeletionIntegrityException extends DataIntegrityViolationException {
    public ProductDeletionIntegrityException() {
        super("Product deletion violates referential integrity");
    }
}
