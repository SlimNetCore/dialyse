package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié du Dossier médical (DM9, ADR 0101) : les indicateurs cliniques des centres d'une société pour le tableau de bord de la
 * Direction ({@code DirectionIndicatorsQueryService}). Aucune donnée nominative : des effectifs et des taux, DÉJÀ MASQUÉS par la politique
 * d'anonymat du contexte ({@code EffectifMasque}, seuil 5 : un effectif de 1 à 4 est rendu {@code null}, zéro reste zéro ; un taux sur
 * moins de 5 patients est {@code null}). Reproduit SANS correction (à trancher par le DPO au plus tard le 2026-12-15) : les consolidés
 * sont calculés sur les valeurs BRUTES de tous les centres puis masqués, de sorte que la différence révèle un effectif masqué (KB-MED-104,
 * RISQUE DE CONFIDENTIALITÉ) ; un patient qui a deux bilans à sa dernière date compte deux fois (KB-MED-109) ; seul le dernier résultat
 * d'un marqueur compte, douteux ou en cours compris (KB-MED-105).
 */
public interface IndicateursCliniquesPublies {

    /** Indicateurs de chaque centre demandé (tous présents, à zéro sans donnée) et consolidés ; période bornée incluse. */
    IndicateursCliniquesDesCentres desCentres(List<UUID> centres, LocalDate du, LocalDate au);

    /** Patients évalués (masqué sous le seuil) et répartition face à la cible KDIGO (masquée sous le seuil). */
    record RepartitionFaceALaCible(Long evalues, BigDecimal pctDansCible, BigDecimal pctSousCible, BigDecimal pctAuDessus) {
    }

    /**
     * @param observanceEnRetard vrai si au moins un patient du centre a une alerte de retard ouverte (valeur BRUTE, pour l'alerte de la
     *                           Direction : l'effectif {@code patientsObservanceEnRetard} est, lui, masqué)
     */
    record IndicateursCliniques(RepartitionFaceALaCible ktV, RepartitionFaceALaCible hemoglobine, RepartitionFaceALaCible phosphore,
                                RepartitionFaceALaCible pth, RepartitionFaceALaCible albumine,
                                Long vhbPositifs, Long vhcPositifs, Long vihPositifs,
                                Long patientsObservanceEnRetard, boolean observanceEnRetard,
                                Long greffeListeAttente, Long greffeBilanEnCours, Long greffesPeriode) {
    }

    record IndicateursCliniquesDesCentres(Map<UUID, IndicateursCliniques> parCentre, IndicateursCliniques consolides) {
    }
}
