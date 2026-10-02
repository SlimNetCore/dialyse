package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.StockMovement;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture de l'historique complet des mouvements (y compris ceux clôturés par un inventaire) — nécessaire à la
 * valorisation du stock à une date passée. Port distinct de {@link StockMovementRepositoryPort} (ségrégation des
 * interfaces) : lecture seule, jamais utilisé par les recalculs de PMP.
 */
public interface StockMovementHistoryPort {

    /**
     * Mouvements des articles donnés dans le centre, par article, triés par date puis identifiant.
     */
    Map<UUID, List<StockMovement>> mouvementsParArticle(CenterId centerId, Collection<UUID> articleIds);
}
