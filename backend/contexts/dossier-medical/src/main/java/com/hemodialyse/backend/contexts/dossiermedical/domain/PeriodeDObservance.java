package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Une période d'observance : la prescription la plus récente découpe le temps en périodes successives de la longueur de sa
 * fréquence (heure et jour = 1 jour, semaine = 7, mois = 30, année = 365 ; inconnue = 7), ancrées sur sa date. REPRODUIT (KB-MED-29) :
 * modifier la prescription déplace donc les périodes.
 */
public record PeriodeDObservance(LocalDate debut, LocalDate fin) {

    public static int fenetreEnJours(String uniteDeFrequence) {
        if (uniteDeFrequence == null) return 7;
        return switch (uniteDeFrequence) {
            case "HEURE", "JOUR" -> 1;
            case "SEMAINE" -> 7;
            case "MOIS" -> 30;
            case "ANNEE" -> 365;
            default -> 7;
        };
    }

    /** La période qui contient {@code jour} (avant la prescription : la toute première). */
    public static PeriodeDObservance courante(LocalDate datePrescription, int fenetre, LocalDate jour) {
        LocalDate ancre = datePrescription != null ? datePrescription : jour;
        if (jour.isBefore(ancre)) return new PeriodeDObservance(ancre, ancre.plusDays(fenetre - 1L));
        long ecoulees = ChronoUnit.DAYS.between(ancre, jour) / fenetre;
        LocalDate debut = ancre.plusDays(ecoulees * fenetre);
        return new PeriodeDObservance(debut, debut.plusDays(fenetre - 1L));
    }

    /** La période terminée juste avant la courante ; vide (nul) si elle commencerait avant la prescription. */
    public static PeriodeDObservance precedente(LocalDate datePrescription, int fenetre, LocalDate jour) {
        PeriodeDObservance courante = courante(datePrescription, fenetre, jour);
        LocalDate finPrecedente = courante.debut().minusDays(1);
        if (datePrescription != null && finPrecedente.isBefore(datePrescription)) return null;
        return new PeriodeDObservance(finPrecedente.minusDays(fenetre - 1L), finPrecedente);
    }

    /** Nombre de jours avant l'échéance sous lequel un rappel est émis. */
    public static int seuilDeRappelEnJours(int fenetre) {
        if (fenetre <= 2) return 1;
        return Math.max(1, fenetre / 3);
    }

    public long joursRestants(LocalDate jour) {
        if (jour.isAfter(fin)) return 0;
        return ChronoUnit.DAYS.between(jour, fin) + 1;
    }
}
