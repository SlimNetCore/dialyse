package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.LotJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LotJpaRepository extends JpaRepository<LotJpaEntity, UUID> {
    Optional<LotJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);

    List<LotJpaEntity> findByCenterIdAndArticleId(UUID centerId, UUID articleId);

    List<LotJpaEntity> findByCenterIdAndBonReceptionIdOrderByCreatedAtAsc(UUID centerId, UUID bonReceptionId);

    @Query("SELECT l FROM LotJpaEntity l WHERE l.centerId = :centerId AND l.articleId = :articleId "
            + "AND l.quantiteRestante > :zero ORDER BY l.datePeremption ASC NULLS LAST, l.createdAt ASC")
    List<LotJpaEntity> findAvailableFefo(@Param("centerId") UUID centerId,
                                         @Param("articleId") UUID articleId,
                                         @Param("zero") BigDecimal zero);

    @Query("SELECT l FROM LotJpaEntity l WHERE l.centerId = :centerId AND l.quantiteRestante > :zero "
            + "AND l.datePeremption IS NOT NULL AND l.datePeremption <= :threshold ORDER BY l.datePeremption ASC")
    List<LotJpaEntity> findExpiring(@Param("centerId") UUID centerId,
                                    @Param("threshold") LocalDate threshold,
                                    @Param("zero") BigDecimal zero);
}

