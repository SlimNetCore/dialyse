package com.hemodialyse.backend.infrastructure.persistence.repository.gmao;

import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.EquipementStatutHistoriqueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EquipementStatutHistoriqueJpaRepository extends JpaRepository<EquipementStatutHistoriqueEntity, UUID> {

    @Query("SELECT h FROM EquipementStatutHistoriqueEntity h WHERE h.equipementId = :equipementId ORDER BY h.changedAt ASC")
    List<EquipementStatutHistoriqueEntity> findByEquipementIdOrderByChangedAtAsc(@Param("equipementId") UUID equipementId);
}
