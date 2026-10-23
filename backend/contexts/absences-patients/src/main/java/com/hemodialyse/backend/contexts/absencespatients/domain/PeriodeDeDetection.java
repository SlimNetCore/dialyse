package com.hemodialyse.backend.contexts.absencespatients.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Période de jours passés que la détection contrôle (RG-ABS-040) : jamais le jour courant (il n'est pas terminé), au plus
 * {@value #JOURS_MAXIMUM} jours. Le jour courant est celui du centre. Une fin demandée postérieure à hier est ramenée à hier ;
 * {@link #finRamenee()} le signale à l'appelant (le legacy le faisait en silence, KB-ABS-29).
 */
public record PeriodeDeDetection(LocalDate du, LocalDate au, boolean finRamenee) {

    /**
     * Durée maximale d'un rattrapage manuel : un an, bissextile compris.
     */
    public static final int JOURS_MAXIMUM = 366;

    public PeriodeDeDetection {
        if (du == null || au == null) {
            throw new IllegalArgumentException("Une période a un début et une fin");
        }
        if (du.isAfter(au)) {
            throw new IllegalArgumentException("Le début d'une période précède sa fin");
        }
    }

    /**
     * La période demandée par l'administrateur pour rattraper une détection manquée.
     *
     * @throws RegleDAbsenceViolee {@code ABSENCE_PERIODE_INVALIDE} (début après la fin ramenée à hier) ou
     *                             {@code ABSENCE_PERIODE_TROP_LONGUE} (plus de {@value #JOURS_MAXIMUM} jours)
     */
    public static PeriodeDeDetection demandee(LocalDate du, LocalDate au, LocalDate aujourdhui) {
        LocalDate hier = aujourdhui.minusDays(1);
        boolean ramenee = au.isAfter(hier);
        LocalDate fin = ramenee ? hier : au;
        if (du.isAfter(fin)) {
            throw new RegleDAbsenceViolee("ABSENCE_PERIODE_INVALIDE",
                    "Période invalide : le début doit précéder la fin (hier au plus tard)");
        }
        if (ChronoUnit.DAYS.between(du, fin) >= JOURS_MAXIMUM) {
            throw new RegleDAbsenceViolee("ABSENCE_PERIODE_TROP_LONGUE",
                    "Période trop longue : " + JOURS_MAXIMUM + " jours au maximum");
        }
        return new PeriodeDeDetection(du, fin, ramenee);
    }

    /**
     * Les {@code jours} derniers jours terminés : de J-jours à hier (la nuit : une semaine ; au démarrage : un mois).
     */
    public static PeriodeDeDetection recente(LocalDate aujourdhui, int jours) {
        if (jours < 1 || jours > JOURS_MAXIMUM) {
            throw new IllegalArgumentException("Une fenêtre récente compte de 1 à " + JOURS_MAXIMUM + " jours");
        }
        LocalDate hier = aujourdhui.minusDays(1);
        return new PeriodeDeDetection(hier.minusDays(jours - 1L), hier, false);
    }

    /**
     * Les jours de la période, du plus ancien au plus récent.
     */
    public List<LocalDate> jours() {
        List<LocalDate> jours = new ArrayList<>();
        for (LocalDate jour = du; !jour.isAfter(au); jour = jour.plusDays(1)) {
            jours.add(jour);
        }
        return jours;
    }
}
