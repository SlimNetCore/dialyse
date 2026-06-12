package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.ArticleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ArticleJpaRepository extends JpaRepository<ArticleJpaEntity, UUID> {
    Optional<ArticleJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);
}

