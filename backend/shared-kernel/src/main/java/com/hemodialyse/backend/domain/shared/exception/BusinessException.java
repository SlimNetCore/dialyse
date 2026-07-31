package com.hemodialyse.backend.domain.shared.exception;

/**
 * Base unchecked exception for domain (business rule) violations.
 * <p>
 * Belongs to the Shared Kernel (AGENTS.md §16): raised by aggregates, value
 * objects and domain services when an invariant is broken. Contains no
 * framework dependency so it stays usable from the pure {@code domain/} layer.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

