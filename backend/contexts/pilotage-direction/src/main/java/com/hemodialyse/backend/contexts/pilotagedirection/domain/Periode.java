package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * La période d'un tableau de bord : bornes incluses, début avant la fin, au plus {@value #ANS_MAXIMUM} ans (RG-DIR : « la période ne peut
 * pas dépasser 5 ans » ; 5 ans pile est accepté, 5 ans et un jour est refusé).
 * <p>
 * Défauts du legacy, reproduits : fin absente = aujourd'hui, début absent = le 1er janvier de l'année de la fin. « Aujourd'hui » est la date
 * dans le fuseau de référence de l'application, Africa/Algiers (KB-DIR-03 corrigé, lot L6, ADR 0119 : plus celui de la JVM) ; il est
 * PASSÉ au constructeur, jamais lu ici.
 */
public record Periode(LocalDate debut, LocalDate fin) {

    public static final int ANS_MAXIMUM = 5;

    public Periode {
        if (debut.isAfter(fin)) {
            throw new RegleDuPilotageViolee("PERIODE_INVALIDE", "La date de début doit précéder la date de fin");
        }
    }

    /**
     * La période demandée, complétée par ses défauts ({@code aujourdhui} est la date du jour du serveur) et soumise à la limite de 5 ans.
     * La limite ne vaut que pour une période DEMANDÉE : la période précédente a la même durée en jours, sans être re-contrôlée (le legacy
     * ne la contrôlait pas, et 5 ans comptent 1 825 ou 1 826 jours selon les années bissextiles).
     */
    public static Periode demandee(LocalDate from, LocalDate to, LocalDate aujourdhui) {
        LocalDate fin = to != null ? to : aujourdhui;
        LocalDate debut = from != null ? from : LocalDate.of(fin.getYear(), 1, 1);
        Periode periode = new Periode(debut, fin);
        if (debut.plusYears(ANS_MAXIMUM).isBefore(fin)) {
            throw new RegleDuPilotageViolee("PERIODE_TROP_LONGUE", "La période ne peut pas dépasser " + ANS_MAXIMUM + " ans");
        }
        return periode;
    }

    /**
     * La période de même durée qui précède immédiatement celle-ci (pour les écarts affichés à côté de l'activité et des finances).
     * Elle n'est pas soumise à la limite de 5 ans : elle a la durée de la période, déjà bornée.
     */
    public Periode precedente() {
        long jours = ChronoUnit.DAYS.between(debut, fin) + 1;
        LocalDate finPrecedente = debut.minusDays(1);
        return new Periode(finPrecedente.minusDays(jours - 1), finPrecedente);
    }
}
