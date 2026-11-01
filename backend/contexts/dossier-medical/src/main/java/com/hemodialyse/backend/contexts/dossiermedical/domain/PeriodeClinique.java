package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Période clinique : date de début et date de fin optionnelle (AGENTS.md §14 Value Object).
 * Immuable ; la cohérence des dates est validée à la construction.
 */
public record PeriodeClinique(LocalDate debut, LocalDate fin) {

    public PeriodeClinique {
        if (debut == null) {
            throw new IllegalArgumentException("dateDebut requise");
        }
        if (fin != null && fin.isBefore(debut)) {
            throw new IllegalArgumentException("dateFin antérieure à dateDebut");
        }
    }

    /**
     * Indique si la période est close (date de fin renseignée).
     */
    public boolean estClosee() {
        return fin != null;
    }

    @Override
    public String toString() {
        return "PeriodeClinique[debut=" + debut + ", fin=" + fin + "]";
    }
}
