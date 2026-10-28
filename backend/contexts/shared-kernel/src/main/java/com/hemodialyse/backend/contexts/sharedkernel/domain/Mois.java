package com.hemodialyse.backend.contexts.sharedkernel.domain;

import java.time.YearMonth;
import java.util.Objects;

/**
 * Période mensuelle (mois civil) utilisée pour les clôtures (ADR 0009). Shared kernel minimal (ADR 0010).
 */
public record Mois(YearMonth valeur) {
    public Mois {
        Objects.requireNonNull(valeur, "Mois requis");
    }

    public static Mois de(int annee, int mois) {
        return new Mois(YearMonth.of(annee, mois));
    }

    public static Mois depuis(YearMonth valeur) {
        return new Mois(valeur);
    }
}
