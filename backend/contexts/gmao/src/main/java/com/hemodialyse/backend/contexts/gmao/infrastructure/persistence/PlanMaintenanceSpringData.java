package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Accès Spring Data au modèle de persistance ; détail d'infrastructure, non exposé aux autres couches. */
public interface PlanMaintenanceSpringData extends JpaRepository<PlanMaintenanceJpa, UUID> {

    @Query("SELECT p FROM PlanMaintenanceJpa p WHERE p.centreId = :centreId AND p.deletedAt IS NULL")
    List<PlanMaintenanceJpa> duCentre(@Param("centreId") UUID centreId);

    @Query("SELECT p FROM PlanMaintenanceJpa p WHERE p.centreId = :centreId AND p.statut = 'ACTIF' AND p.deletedAt IS NULL")
    List<PlanMaintenanceJpa> actifsDuCentre(@Param("centreId") UUID centreId);

    @Query("SELECT p FROM PlanMaintenanceJpa p WHERE p.centreId = :centreId AND p.statut = 'ACTIF' "
            + "AND p.prochaineDatePrevue <= :limite AND p.deletedAt IS NULL")
    List<PlanMaintenanceJpa> enRetardDuCentre(@Param("centreId") UUID centreId, @Param("limite") Instant limite);

    @Query("SELECT COUNT(p) FROM PlanMaintenanceJpa p WHERE p.centreId = :centreId AND p.statut = 'ACTIF' AND p.deletedAt IS NULL")
    long compterActifs(@Param("centreId") UUID centreId);

    @Query("SELECT COUNT(p) FROM PlanMaintenanceJpa p WHERE p.centreId = :centreId AND p.statut = 'ACTIF' "
            + "AND p.prochaineDatePrevue <= :limite AND p.deletedAt IS NULL")
    long compterEnRetard(@Param("centreId") UUID centreId, @Param("limite") Instant limite);

    @Query("SELECT p FROM PlanMaintenanceJpa p WHERE p.equipementId = :equipementId AND p.deletedAt IS NULL")
    List<PlanMaintenanceJpa> deEquipement(@Param("equipementId") UUID equipementId);
}
