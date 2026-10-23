package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;

import java.time.YearMonth;
import java.util.List;

/**
 * Port sortant du domaine : un mois d'absences est ouvert tant que le centre ne l'a pas clos. Un mois clos n'est plus touché par la
 * détection ni la réconciliation de nuit (décision du 21/10) ; une absence manquante s'y rattrape en rouvrant le mois. Point
 * d'extension de la clôture mensuelle (A6) : tant qu'elle n'existe pas, l'adaptateur déclare tous les mois ouverts.
 */
public interface MoisDAbsencesOuverts {

    boolean estOuvert(CentreId centre, YearMonth mois);

    /** Les mois clos du centre (la notification ne les compte pas). */
    List<YearMonth> moisClos(CentreId centre);
}
