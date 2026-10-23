package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;

import java.time.YearMonth;
import java.util.List;

/**
 * Port sortant du domaine : un mois d'absences est ouvert tant que le centre ne l'a pas clos (clôture mensuelle, A6). Un mois clos
 * n'est plus touché par la détection ni la réconciliation de nuit (décision du 21/10) ; une absence manquante s'y rattrape en
 * rouvrant le mois. Adaptateur : la lecture de {@code mois_absences}.
 */
public interface MoisDAbsencesOuverts {

    /**
     * Lecture sans verrou (filtre rapide hors transaction).
     */
    boolean estOuvert(CentreId centre, YearMonth mois);

    /**
     * Lecture <b>en verrouillant la ligne du mois jusqu'à la fin de la transaction</b> : à appeler dans la transaction d'une
     * écriture, pour qu'une clôture concurrente attende (ou soit attendue) et ne laisse jamais une absence écrite dans un mois clos.
     *
     * @throws EcritureConcurrente le verrou n'a pas pu être pris (délai dépassé)
     */
    boolean estOuvertSousVerrou(CentreId centre, YearMonth mois);

    /** Les mois clos du centre (la notification ne les compte pas). */
    List<YearMonth> moisClos(CentreId centre);
}
