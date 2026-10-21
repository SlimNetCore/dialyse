package com.hemodialyse.backend.contexts.facturation.domain.cloture;

import java.time.YearMonth;

/** Refus 409 {@code PERIODE_DEJA_CLOTUREE} : on ne clôt pas deux fois le même mois. */
public final class MoisDejaClos extends RuntimeException {
    public MoisDejaClos(YearMonth mois) {
        super("La periode de facturation " + mois + " est deja cloturee");
    }
}
