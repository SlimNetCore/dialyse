package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

import java.time.Instant;
import java.time.ZoneOffset;

/** Périodicité d'un plan de maintenance préventive ; calcule la prochaine date prévue (arithmétique calendaire UTC). */
public enum FrequenceMaintenance {
    HEBDOMADAIRE,
    BIMENSUELLE,
    MENSUELLE,
    TRIMESTRIELLE,
    SEMESTRIELLE,
    ANNUELLE;

    /** Prochaine date prévue à partir de {@code depuis} (legacy : plusWeeks, plusDays(15), plusMonths, plusYears). */
    public Instant prochaineDate(Instant depuis) {
        var date = depuis.atOffset(ZoneOffset.UTC);
        var suivante = switch (this) {
            case HEBDOMADAIRE -> date.plusWeeks(1);
            case BIMENSUELLE -> date.plusDays(15);
            case MENSUELLE -> date.plusMonths(1);
            case TRIMESTRIELLE -> date.plusMonths(3);
            case SEMESTRIELLE -> date.plusMonths(6);
            case ANNUELLE -> date.plusYears(1);
        };
        return suivante.toInstant();
    }
}
