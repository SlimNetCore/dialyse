package com.hemodialyse.backend.application.erreur;

/**
 * Base unchecked exception for business rule violations of the platform (HTTP 422 via {@code ApiExceptionHandler}).
 * <p>
 * Contains no framework dependency. Moved from the former {@code shared-kernel} module (ADR 0136).
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
