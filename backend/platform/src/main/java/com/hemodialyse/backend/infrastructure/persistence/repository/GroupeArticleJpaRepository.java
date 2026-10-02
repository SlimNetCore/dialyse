package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.GroupeArticleJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupeArticleJpaRepository extends JpaRepository<GroupeArticleJpaEntity, UUID> {

    Page<GroupeArticleJpaEntity> findByCenterId(UUID centerId, Pageable pageable);

    Optional<GroupeArticleJpaEntity> findByIdAndCenterId(UUID id, UUID centerId);

    boolean existsByCenterIdAndNomCle(UUID centerId, String nomCle);

    boolean existsByCenterIdAndNomCleAndIdNot(UUID centerId, String nomCle, UUID id);

    List<GroupeArticleJpaEntity> findByCenterIdIn(Collection<UUID> centerIds);
}
