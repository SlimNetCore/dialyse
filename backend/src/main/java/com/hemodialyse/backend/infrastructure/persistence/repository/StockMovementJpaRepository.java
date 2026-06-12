package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.StockMovementJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface StockMovementJpaRepository extends JpaRepository<StockMovementJpaEntity, UUID> {

    List<StockMovementJpaEntity> findByCenterIdAndArticleIdOrderByCreatedAtAscIdAsc(UUID centerId, UUID articleId);

    List<StockMovementJpaEntity> findByCenterIdAndArticleIdAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(
            UUID centerId, UUID articleId, OffsetDateTime before);
}


