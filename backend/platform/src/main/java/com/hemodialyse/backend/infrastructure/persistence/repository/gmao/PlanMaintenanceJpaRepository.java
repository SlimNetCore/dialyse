package com.hemodialyse.backend.infrastructure.persistence.repository.gmao;

import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.PlanMaintenanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository pour PlanMaintenanceEntity
 */
@Repository
public interface PlanMaintenanceJpaRepository extends JpaRepository<PlanMaintenanceEntity, UUID> {

    /**
     * Récupère les plans de maintenance d'un équipement
     */
    @Query("SELECT p FROM PlanMaintenanceEntity p WHERE p.equipementId = :equipementId AND p.deletedAt IS NULL")
    List<PlanMaintenanceEntity> findByEquipementId(@Param("equipementId") UUID equipementId);

    /**
     * Récupère les plans de maintenance d'un centre
     */
    @Query("SELECT p FROM PlanMaintenanceEntity p WHERE p.centreId = :centreId AND p.deletedAt IS NULL")
    List<PlanMaintenanceEntity> findByCentreId(@Param("centreId") UUID centreId);

    /**
     * Récupère les plans actifs d'un centre
     */
    @Query("SELECT p FROM PlanMaintenanceEntity p WHERE p.centreId = :centreId AND p.statut = 'ACTIF' AND p.deletedAt IS NULL")
    List<PlanMaintenanceEntity> findActiveByCentreId(@Param("centreId") UUID centreId);

    /**
     * Récupère les plans en retard d'un centre
     */
    @Query("SELECT p FROM PlanMaintenanceEntity p WHERE p.centreId = :centreId AND p.statut = 'ACTIF' AND p.prochaineDatePrevue <= :dateLimit AND p.deletedAt IS NULL")
    List<PlanMaintenanceEntity> findOverdueByCentreId(@Param("centreId") UUID centreId, @Param("dateLimit") OffsetDateTime dateLimit);

    /**
     * Récupère les plans actifs d'un équipement
     */
    @Query("SELECT p FROM PlanMaintenanceEntity p WHERE p.equipementId = :equipementId AND p.statut = 'ACTIF' AND p.deletedAt IS NULL")
    List<PlanMaintenanceEntity> findActiveByEquipementId(@Param("equipementId") UUID equipementId);

    /**
     * Compte les plans d'un centre
     */
    @Query("SELECT COUNT(p) FROM PlanMaintenanceEntity p WHERE p.centreId = :centreId AND p.deletedAt IS NULL")
    long countByCentreId(@Param("centreId") UUID centreId);

    /**
     * Compte les plans actifs d'un centre
     */
    @Query("SELECT COUNT(p) FROM PlanMaintenanceEntity p WHERE p.centreId = :centreId AND p.statut = 'ACTIF' AND p.deletedAt IS NULL")
    long countActiveByCentreId(@Param("centreId") UUID centreId);
}

