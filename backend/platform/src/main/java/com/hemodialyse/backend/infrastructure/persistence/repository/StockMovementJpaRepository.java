package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.StockMovementJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockMovementJpaRepository extends JpaRepository<StockMovementJpaEntity, UUID> {

    List<StockMovementJpaEntity> findByCenterIdAndArticleIdOrderByCreatedAtAscIdAsc(UUID centerId, UUID articleId);

    List<StockMovementJpaEntity> findByCenterIdAndArticleIdAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(
            UUID centerId, UUID articleId, OffsetDateTime before);

    Optional<StockMovementJpaEntity> findFirstByCenterIdAndLotIdAndMouvementTypeOrderByCreatedAtAsc(
            UUID centerId, UUID lotId, String mouvementType);

    List<StockMovementJpaEntity> findByCenterIdAndSeanceIdAndArticleId(UUID centerId, UUID seanceId, UUID articleId);

    void deleteByCenterIdAndSeanceIdAndArticleId(UUID centerId, UUID seanceId, UUID articleId);

    /**
     * Mouvements ouverts (non clotures par inventaire), dans l'ordre chronologique : base du recalcul.
     */
    List<StockMovementJpaEntity> findByCenterIdAndArticleIdAndInventaireIdIsNullOrderByCreatedAtAscIdAsc(UUID centerId, UUID articleId);

    List<StockMovementJpaEntity> findByCenterIdAndArticleIdAndInventaireIdIsNullAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(
            UUID centerId, UUID articleId, OffsetDateTime before);
}


