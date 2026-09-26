package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.ModeleDocumentVersionJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModeleDocumentVersionJpaRepository extends JpaRepository<ModeleDocumentVersionJpaEntity, UUID> {

    Page<ModeleDocumentVersionJpaEntity> findByModeleIdAndCenterIdOrderByVersionDesc(UUID modeleId, UUID centerId,
                                                                                     Pageable pageable);

    Optional<ModeleDocumentVersionJpaEntity> findByModeleIdAndCenterIdAndActifTrue(UUID modeleId, UUID centerId);

    Optional<ModeleDocumentVersionJpaEntity> findByModeleIdAndCenterIdAndVersion(UUID modeleId, UUID centerId,
                                                                                 int version);

    List<ModeleDocumentVersionJpaEntity> findByModeleIdAndCenterIdAndActifTrueOrderByVersionDesc(UUID modeleId,
                                                                                                 UUID centerId);

    @Query("select coalesce(max(v.version), 0) from ModeleDocumentVersionJpaEntity v "
            + "where v.modeleId = :modeleId and v.centerId = :centerId")
    int maxVersion(@Param("modeleId") UUID modeleId, @Param("centerId") UUID centerId);

    void deleteByModeleId(UUID modeleId);
}
