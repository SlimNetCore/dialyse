package com.hemodialyse.backend.domain.stock.exception;

/**
 * Raised when attempting to change stock exit date for a seance-linked exit.
 */
public class SeanceStockExitDateImmutableException extends IllegalStateException {

    public SeanceStockExitDateImmutableException() {
        super("La date de sortie d'une seance ne peut pas etre modifiee");
    }
}

