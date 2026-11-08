package com.hemodialyse.backend.contexts.seance.domain;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Mois consulté dans le calendrier du centre : année et mois (1 à 12). Hors bornes (mois 0, 13, négatif, année au-delà de ±999 999 999) :
 * {@link SaisieInvalide} (KB-SEA-57 corrigé : le legacy répondait 500). L'année 0 et l'année 1900 restent acceptées (reproduit).
 */
public record MoisDuCalendrier(int annee, int mois) {

    public MoisDuCalendrier {
        try {
            YearMonth.of(annee, mois);
        } catch (DateTimeException e) {
            throw new SaisieInvalide("Mois du calendrier hors bornes");
        }
    }

    /** Premier jour du mois, inclus. */
    public LocalDate premierJour() {
        return YearMonth.of(annee, mois).atDay(1);
    }

    /** Dernier jour du mois, inclus. */
    public LocalDate dernierJour() {
        return YearMonth.of(annee, mois).atEndOfMonth();
    }
}
