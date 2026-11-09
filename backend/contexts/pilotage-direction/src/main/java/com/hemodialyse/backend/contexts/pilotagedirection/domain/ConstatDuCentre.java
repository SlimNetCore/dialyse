package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ce que la politique d'alertes sait d'un centre : uniquement des agrégats déjà anonymisés. Un taux masqué par l'anonymat est {@code null}.
 */
public record ConstatDuCentre(UUID centreId, String centre,
                              BigDecimal ktVDansCiblePct, BigDecimal hbDansCiblePct,
                              boolean patientsEnRetardObservance,
                              long articlesSousSeuil, long lotsPerimes, long lotsPeremptionProche) {
}
