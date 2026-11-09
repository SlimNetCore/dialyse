package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié du Pilotage direction pour les indicateurs de la direction ({@code pilotagedirection.v1}, PD6, ADR 0106) : indicateurs
 * cliniques et de stock par centre et au total, et alertes, sur une période, DÉJÀ MASQUÉS (seuil d'anonymat) : ce que la route
 * {@code GET /api/v1/direction/indicators} sert. Les enregistrements portent les NOMS DU JSON servi ({@code centerId}, {@code generatedAt},
 * {@code seuilAnonymat}, {@code pctDansCible}...) : valeurs de contrat, aussi gravées telles quelles dans les instantanés mensuels (RG-DIR-090),
 * pas des choix de nommage.
 */
public interface IndicateursDeLaDirection {

    /** @throws RuntimeException {@code PERIODE_INVALIDE} ou {@code PERIODE_TROP_LONGUE} (422) ; défaut : du 1er janvier à aujourd'hui */
    Indicateurs indicateurs(UUID societeId, LocalDate from, LocalDate to);

    /** Patients évalués (masqué sous le seuil) et répartition face à la cible KDIGO (masquée sous le seuil). */
    record Marqueur(Long evalues, BigDecimal pctDansCible, BigDecimal pctSousCible, BigDecimal pctAuDessus) {
    }

    record Clinique(Marqueur ktV, Marqueur hemoglobine, Marqueur phosphore, Marqueur pth, Marqueur albumine,
                    Long vhbPositifs, Long vhcPositifs, Long vihPositifs,
                    Long patientsObservanceEnRetard,
                    Long greffeListeAttente, Long greffeBilanEnCours, Long greffesPeriode) {
    }

    /** Valeur du stock arrondie au centime, évalué à la fin de la période. */
    record Stock(long articlesActifs, long articlesSousSeuil, long lotsPerimes, long lotsPeremptionProche, BigDecimal valeurStock) {
    }

    /** Un centre, ou les totaux de la société ({@code centerId} et {@code nom} nuls, {@code actif} vrai). */
    record IndicateursDuCentre(UUID centerId, String nom, boolean actif, Clinique clinique, Stock stock) {
    }

    record Indicateurs(UUID societeId, LocalDate from, LocalDate to, OffsetDateTime generatedAt, int seuilAnonymat,
                       List<IndicateursDuCentre> centres, IndicateursDuCentre totaux, List<AlertesDeLaDirection.Alerte> alertes) {
    }
}
