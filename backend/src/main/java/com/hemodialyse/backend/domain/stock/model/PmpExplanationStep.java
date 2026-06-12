package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One step of the PMP (weighted average cost) explanation timeline.
 */
public record PmpExplanationStep(
        OffsetDateTime date,
        String type,                 // ENTREE | SORTIE | AJUSTEMENT
        BigDecimal quantite,
        BigDecimal prixUnitaire,     // null for SORTIE
        BigDecimal quantiteAvant,
        BigDecimal valeurAvant,
        BigDecimal pmpAvant,
        BigDecimal quantiteApres,
        BigDecimal valeurApres,
        BigDecimal pmpApres,
        String formule               // human-readable formula for this step
) {
}

