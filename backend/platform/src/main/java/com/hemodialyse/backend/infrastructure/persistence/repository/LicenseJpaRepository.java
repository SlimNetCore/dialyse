package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.LicenseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LicenseJpaRepository extends JpaRepository<LicenseJpaEntity, UUID> {

    List<LicenseJpaEntity> findAllByOrderByCreatedAtDesc();

    List<LicenseJpaEntity> findByCenterIdOrderByCreatedAtDesc(UUID centerId);

    Optional<LicenseJpaEntity> findFirstByCenterIdAndStatusOrderByCreatedAtDesc(UUID centerId, String status);

    Optional<LicenseJpaEntity> findByJti(String jti);
}
