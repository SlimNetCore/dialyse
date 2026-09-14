package com.hemodialyse.backend.infrastructure.scheduling;

import java.time.LocalDate;

/**
 * Calcul des périodes d'observance d'une prescription EPO/fer, ancrées sur la date de
 * prescription plutôt que sur une fenêtre glissante se terminant "aujourd'hui" — ce qui permet
 * de distinguer une période déjà close (pour constater un retard) de la période en cours (pour
 * avertir avant son échéance). Utilisé à la fois par {@link ObservancePrescriptionScheduler}
 * (alerte quotidienne) et par le calcul à la demande exposé au volet séance (quantité restant à
 * administrer avant la fin de la période).
 * <p>
 * Classe utilitaire pure (pas de dépendance Spring/JPA) : testable indépendamment, mais placée
 * côté infrastructure plutôt que dans un module bc-medical/bc-seance pour éviter d'introduire une
 * dépendance entre bounded contexts (elle ne manipule que des primitives).
 */
public final class ObservancePeriodMath {

    private ObservancePeriodMath() {
    }

    public static int windowDaysFor(String unite) {
        if (unite == null) return 7;
        return switch (unite) {
            case "HEURE", "JOUR" -> 1;
            case "SEMAINE" -> 7;
            case "MOIS" -> 30;
            case "ANNEE" -> 365;
            default -> 7;
        };
    }

    /**
     * La période (de longueur {@code windowDays}) qui contient {@code date}, les périodes étant
     * découpées successivement à partir de {@code datePrescription}.
     */
    public static Periode periodeContenant(LocalDate datePrescription, int windowDays, LocalDate date) {
        LocalDate ancre = datePrescription != null ? datePrescription : date;
        if (date.isBefore(ancre)) {
            return new Periode(ancre, ancre.plusDays(windowDays - 1L));
        }
        long joursDepuisAncre = java.time.temporal.ChronoUnit.DAYS.between(ancre, date);
        long periodesEcoulees = joursDepuisAncre / windowDays;
        LocalDate debut = ancre.plusDays(periodesEcoulees * windowDays);
        return new Periode(debut, debut.plusDays(windowDays - 1L));
    }

    public static Periode periodeCourante(LocalDate datePrescription, int windowDays, LocalDate aujourdHui) {
        return periodeContenant(datePrescription, windowDays, aujourdHui);
    }

    /**
     * La période immédiatement précédente à celle contenant {@code aujourdHui} — {@code null} si elle serait antérieure à la prescription.
     */
    public static Periode periodePrecedente(LocalDate datePrescription, int windowDays, LocalDate aujourdHui) {
        Periode courante = periodeCourante(datePrescription, windowDays, aujourdHui);
        LocalDate finPrecedente = courante.debut().minusDays(1);
        if (datePrescription != null && finPrecedente.isBefore(datePrescription)) {
            return null;
        }
        return new Periode(finPrecedente.minusDays(windowDays - 1L), finPrecedente);
    }

    /**
     * Seuil (en jours restants) à partir duquel un rappel proactif est justifié avant la fin de la période.
     */
    public static int seuilRappelJours(int windowDays) {
        if (windowDays <= 2) return 1;
        return Math.max(1, windowDays / 3);
    }

    public record Periode(LocalDate debut, LocalDate fin) {
        public boolean contient(LocalDate date) {
            return !date.isBefore(debut) && !date.isAfter(fin);
        }

        /**
         * Nombre de jours restants avant la fin de la période, aujourd'hui inclus.
         */
        public long joursRestants(LocalDate aujourdHui) {
            if (aujourdHui.isAfter(fin)) return 0;
            return java.time.temporal.ChronoUnit.DAYS.between(aujourdHui, fin) + 1;
        }
    }
}
