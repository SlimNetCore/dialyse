package com.hemodialyse.backend.contexts.facturation.domain.cloture;

import java.time.YearMonth;

/**
 * Refus 422 {@code PERIODE_NON_TERMINEE} : le mois en cours et les mois futurs ne se clôturent pas, au fuseau du centre et sans jours
 * de grâce (le mois précédent se clôture dès le premier jour du mois). Même règle que la clôture des absences (ADR 0044, ADR 0042).
 */
public final class MoisDeFacturationNonTermine extends RuntimeException {

    private final YearMonth mois;

    public MoisDeFacturationNonTermine(YearMonth mois) {
        super("La periode de facturation " + mois + " n'est pas terminee : seul un mois passe peut etre cloture");
        this.mois = mois;
    }

    public YearMonth mois() {
        return mois;
    }
}
