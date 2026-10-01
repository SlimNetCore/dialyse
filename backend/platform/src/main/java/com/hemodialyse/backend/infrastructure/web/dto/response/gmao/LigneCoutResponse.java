package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

import com.hemodialyse.backend.domain.gmao.model.LigneCoutIntervention;
import com.hemodialyse.backend.domain.gmao.model.TypeLigneCout;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de réponse pour une ligne de coût d'intervention GMAO.
 */
public record LigneCoutResponse(
        UUID id,
        TypeLigneCout type,
        String libelle,
        BigDecimal quantite,
        BigDecimal prixUnitaire,
        BigDecimal montant,
        UUID articleStockId
) {
    public LigneCoutResponse(LigneCoutIntervention ligne) {
        this(ligne.getId(), ligne.getType(), ligne.getLibelle(), ligne.getQuantite(),
                ligne.getPrixUnitaire(), ligne.montant(), ligne.getArticleStockId());
    }
}
