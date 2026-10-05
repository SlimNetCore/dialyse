package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.ArticleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArticleJpaRepository extends JpaRepository<ArticleJpaEntity, UUID>,
        JpaSpecificationExecutor<ArticleJpaEntity> {
    Optional<ArticleJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);

    Optional<ArticleJpaEntity> findFirstByCenterIdAndCodeIgnoreCase(UUID centerId, String code);

    List<ArticleJpaEntity> findByCenterIdOrderByCode(UUID centerId);

    List<ArticleJpaEntity> findByCenterIdAndTypeTraitementAnemieOrderByCode(UUID centerId, String typeTraitementAnemie);
}
