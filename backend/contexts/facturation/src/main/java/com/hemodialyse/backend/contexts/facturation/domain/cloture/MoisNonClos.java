package com.hemodialyse.backend.contexts.facturation.domain.cloture;

import java.time.YearMonth;

/** Refus 409 {@code PERIODE_NON_CLOTUREE} : on ne rouvre pas un mois qui n'est pas clos. */
public final class MoisNonClos extends RuntimeException {
    public MoisNonClos(YearMonth mois) {
        super("La periode de facturation " + mois + " n'est pas cloturee");
    }
}
