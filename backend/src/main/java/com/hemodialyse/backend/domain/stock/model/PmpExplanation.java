package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Full PMP (weighted average cost) explanation for an article: the methodology,
 * the chronological steps, and the resulting current state.
 */
public record PmpExplanation(
        UUID articleId,
        String code,
        String libelle,
        String methode,
        List<PmpExplanationStep> etapes,
        BigDecimal quantiteFinale,
        BigDecimal valeurFinale,
        BigDecimal pmpFinal
) {
}

