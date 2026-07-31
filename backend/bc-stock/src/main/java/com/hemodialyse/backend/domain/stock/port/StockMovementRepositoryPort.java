package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.StockMovement;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockMovementRepositoryPort {
    StockMovement save(StockMovement movement);

    /**
     * All movements of an article (within a center), ordered by date then id.
     * Used by the PMP cascade recalculation engine.
     */
    List<StockMovement> findByArticleOrdered(CenterId centerId, UUID articleId);

    /**
     * Movements of an article occurring strictly before the given timestamp,
     * ordered by date then id (used to compute the starting state of a cascade).
     */
    List<StockMovement> findByArticleBefore(CenterId centerId, UUID articleId, OffsetDateTime before);

    /**
     * Persist a PMP value recomputed for an existing movement.
     */
    void updatePmpApres(UUID movementId, BigDecimal pmpApres);

    Optional<StockMovement> findFirstEntreeByLot(CenterId centerId, UUID lotId);

    /**
     * Batch-persist a full recalculation pass: for each movement set its recomputed
     * {@code pmp_apres} and, when {@code valorisation} is provided (stock exits),
     * rewrite {@code prix_unitaire} with the PMP that precedes the movement.
     */
    void applyRecalc(List<MovementRecalc> updates);

    /**
     * Recalculation update line for a single movement.
     *
     * @param movementId   movement to update
     * @param pmpApres     recomputed PMP after the movement
     * @param valorisation if non-null, new {@code prix_unitaire} (used for exits)
     */
    record MovementRecalc(UUID movementId, BigDecimal pmpApres, BigDecimal valorisation) {
    }
}


