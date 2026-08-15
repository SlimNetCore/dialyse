package com.hemodialyse.backend.domain.shared.exception;

/**
 * Base unchecked exception for domain (business rule) violations.
 * <p>
 * Belongs to the Shared Kernel (AGENTS.md §16): raised by aggregates, value
 * objects and domain services when an invariant is broken. Contains no
 * framework dependency so it stays usable from the pure {@code domain/} layer.
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String message) {
        super(message);
        this.code = "BUSINESS_ERROR";
    }

    /**
     * Constructeur avec code métier (utile pour i18n côté frontend).
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = "BUSINESS_ERROR";
    }

    public String getCode() {
        return code;
    }
}

