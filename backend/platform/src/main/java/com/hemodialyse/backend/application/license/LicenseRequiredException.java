package com.hemodialyse.backend.application.license;

/**
 * Raised when an operation cannot proceed because the caller's center has no valid license, or is over quota.
 */
public class LicenseRequiredException extends RuntimeException {

    public LicenseRequiredException(String message) {
        super(message);
    }
}
