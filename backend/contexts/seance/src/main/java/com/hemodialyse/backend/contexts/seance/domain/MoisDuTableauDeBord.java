package com.hemodialyse.backend.contexts.seance.domain;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Mois lu par le tableau de bord des séances : année et mois (1 à 12). Hors bornes (mois 0, 13, négatif, année au-delà de
 * ±999 999 999) : {@link SaisieInvalide} (KB-SEA-63 corrigé : le legacy répondait 500). Les années 0, 1 et 9999 restent acceptées
 * (reproduit). Porte la règle des « jours échus » : le futur n'est ni présence ni absence.
 */
public record MoisDuTableauDeBord(int annee, int mois) {

    public MoisDuTableauDeBord {
        try {
            YearMonth.of(annee, mois);
        } catch (DateTimeException e) {
            throw new SaisieInvalide("Mois du tableau de bord hors bornes");
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

    /** Dernier jour échu du mois : le dernier jour du mois, ou {@code aujourdhui} si le mois n'est pas terminé. */
    public LocalDate dernierJourEchu(LocalDate aujourdhui) {
        LocalDate fin = dernierJour();
        return fin.isAfter(aujourdhui) ? aujourdhui : fin;
    }

    /** Le mois a-t-il au moins un jour échu ? Un mois futur n'a ni séance prévue ni absence (KB-ABS-35). */
    public boolean aUnJourEchu(LocalDate aujourdhui) {
        return !dernierJourEchu(aujourdhui).isBefore(premierJour());
    }
}
