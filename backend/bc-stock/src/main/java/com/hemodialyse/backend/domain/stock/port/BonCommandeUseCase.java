package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonCommande;
import com.hemodialyse.backend.domain.stock.model.LigneBonCommande;

import java.util.List;
import java.util.UUID;

/**
 * Primary port: bon de commande (BL) workflow.
 */
public interface BonCommandeUseCase {
    BonCommande create(CenterId centerId, UUID fournisseurId, List<LigneBonCommande> lignes, String userId);

    BonCommande updateLignes(CenterId centerId, UUID bonId, List<LigneBonCommande> lignes);

    BonCommande valider(CenterId centerId, UUID bonId);

    BonCommande get(CenterId centerId, UUID bonId);

    List<BonCommande> list(CenterId centerId);
}

