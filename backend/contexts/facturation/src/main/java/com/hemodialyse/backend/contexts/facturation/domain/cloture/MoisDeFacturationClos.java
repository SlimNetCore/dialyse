package com.hemodialyse.backend.contexts.facturation.domain.cloture;

import java.time.YearMonth;

/** Refus 422 {@code PERIODE_DE_FACTURATION_CLOTUREE} : le mois est clos, la facturation n'y change plus. */
public final class MoisDeFacturationClos extends RuntimeException {

    private final YearMonth mois;

    public MoisDeFacturationClos(YearMonth mois) {
        super("La periode de facturation " + mois + " est cloturee : plus aucune emission ni modification de facturation n'y est possible");
        this.mois = mois;
    }

    public YearMonth mois() {
        return mois;
    }
}
