package com.hemodialyse.backend.application.license;

/**
 * Raised when a license token fails signature verification or is otherwise unusable.
 */
public class InvalidLicenseException extends RuntimeException {

    public InvalidLicenseException(String message) {
        super(message);
    }

    public InvalidLicenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
