package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Port FOURNI au Pilotage direction par la plateforme (PD3, ADR 0104) : les indicateurs médicaux, de stock et les alertes d'une société sur la
 * PÉRIODE PAR DÉFAUT (du 1er janvier à aujourd'hui), déjà masqués par le seuil d'anonymat, tels que la route {@code /indicators} les sert.
 * Ils restent calculés par la plateforme (DM9, ADR 0101) ; le temps réel du Pilotage les lit à chaque recalcul. Une valeur absente
 * ({@code null}) est masquée ou inconnue.
 */
public interface IndicateursDuTempsReelFournis {

    IndicateursDeLaSociete pour(UUID societeId);

    /** {@code evalues} : patients évalués ; {@code pctDansCible} : part dans la cible KDIGO. Tous deux masqués sous le seuil. */
    record Marqueur(Long evalues, BigDecimal pctDansCible) {
    }

    record Clinique(Marqueur ktV, Marqueur hemoglobine, Marqueur phosphore, Marqueur pth, Marqueur albumine,
                    Long vhbPositifs, Long vhcPositifs, Long vihPositifs, Long patientsObservanceEnRetard,
                    Long greffeListeAttente, Long greffesPeriode) {
    }

    record Stock(long articlesSousSeuil, long lotsPerimes, long lotsPeremptionProche, BigDecimal valeurStock) {
    }

    record IndicateursDuCentre(UUID centerId, String nom, Clinique clinique, Stock stock) {
    }

    record IndicateursDeLaSociete(List<IndicateursDuCentre> centres, List<AlertesDeLaDirection.Alerte> alertes) {
    }
}
