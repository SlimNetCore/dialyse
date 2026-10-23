package com.hemodialyse.backend.contexts.stock.domain.sortie;

/**
 * La date de sortie d'un bon rattaché à une séance ne se modifie pas (RG-STK-032) : 422
 * {@code SEANCE_STOCK_EXIT_DATE_IMMUTABLE}.
 */
public class DateDeSortieDeSeanceImmuable extends RuntimeException {

    public DateDeSortieDeSeanceImmuable() {
        super("La date de sortie d'une seance ne peut pas etre modifiee");
    }
}

