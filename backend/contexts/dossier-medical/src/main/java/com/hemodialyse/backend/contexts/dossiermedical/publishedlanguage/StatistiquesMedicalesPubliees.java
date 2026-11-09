package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié du Dossier médical (DM9, ADR 0101) : les statistiques médicales d'un patient d'un centre sur une période (bornes
 * incluses, nulles = sans borne), pour les écrans et exports de la plateforme ({@code PatientStatsQueryService} : JSON, CSV, PDF).
 * Reproduit SANS correction du legacy (KB-MED-103, 109, à confirmer) : moyennes à {@code 0.0} sans donnée ; lignes servies comme le SGBD
 * les rend (clés en MAJUSCULES, dates et horodatages du pilote), triées par la seule date (ordre non défini à date égale).
 */
public interface StatistiquesMedicalesPubliees {

    /** Moyennes d'hémoglobine, de Kt/V et de ferritine des bilans de la période ; {@code 0.0} quand aucune valeur. */
    Moyennes moyennes(UUID centre, UUID patient, LocalDate du, LocalDate au);

    /** Bilans de la période : {@code DATE_PRELEVEMENT}, {@code HB_G_DL}, {@code KT_V_MENSUEL}, {@code FERRITINE_NG_ML}, par date croissante. */
    List<Map<String, Object>> tendanceDesBilans(UUID centre, UUID patient, LocalDate du, LocalDate au);

    /**
     * Prescriptions de la période : {@code DATE_PRESCRIPTION}, {@code EPO_DOSE_UI}, {@code EPO_ARTICLE_ID}, {@code EPO_VOIE},
     * {@code EPO_FREQUENCE_VALEUR}, {@code EPO_FREQUENCE_UNITE}, par date croissante (une prescription sans EPO donne une ligne vide).
     */
    List<Map<String, Object>> tendanceDesPrescriptionsDEpo(UUID centre, UUID patient, LocalDate du, LocalDate au);

    /** Lignes d'export des bilans : date, Hb, Kt/V, ferritine, phosphore, calcium ({@code DATE_PRELEVEMENT}, {@code HB_G_DL}, ...). */
    List<Map<String, Object>> lignesDExport(UUID centre, UUID patient, LocalDate du, LocalDate au);

    /** Poids sec cible de la dernière prescription qui en porte un, datée au plus tard du jour donné ; vide si jamais prescrit. */
    Optional<Double> poidsSecCibleEnVigueur(UUID centre, UUID patient, LocalDate au);

    record Moyennes(Double hemoglobine, Double ktV, Double ferritine) {
    }
}
