package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.LigneDemandeExamenJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LigneDemandeExamenJpaRepository extends JpaRepository<LigneDemandeExamenJpaEntity, UUID> {

    List<LigneDemandeExamenJpaEntity> findByDemandeId(UUID demandeId);

    void deleteByDemandeId(UUID demandeId);
}
