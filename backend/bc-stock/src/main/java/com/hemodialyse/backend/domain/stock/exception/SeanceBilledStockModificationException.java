package com.hemodialyse.backend.domain.stock.exception;

/**
 * Raised when a stock exit linked to a billed seance is modified.
 */
public class SeanceBilledStockModificationException extends IllegalStateException {

    public SeanceBilledStockModificationException() {
        super("La seance facturee ne peut plus etre modifiee");
    }
}

