package com.hemodialyse.backend.infrastructure.persistence.repository.gmao;

import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.EquipementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository pour EquipementEntity
 */
@Repository
public interface EquipementJpaRepository extends JpaRepository<EquipementEntity, UUID> {

    /**
     * Récupère les équipements d'un centre (non supprimés)
     */
    @Query("SELECT e FROM EquipementEntity e WHERE e.centreId = :centreId AND e.deletedAt IS NULL")
    List<EquipementEntity> findByCentreId(@Param("centreId") UUID centreId);

    /**
     * Récupère les équipements d'un centre par statut
     */
    @Query("SELECT e FROM EquipementEntity e WHERE e.centreId = :centreId AND e.statut = :statut AND e.deletedAt IS NULL")
    List<EquipementEntity> findByCentreIdAndStatut(@Param("centreId") UUID centreId, @Param("statut") String statut);

    /**
     * Récupère un équipement par code
     */
    @Query("SELECT e FROM EquipementEntity e WHERE e.code = :code AND e.deletedAt IS NULL")
    Optional<EquipementEntity> findByCode(@Param("code") String code);

    /**
     * Compte les équipements d'un centre
     */
    @Query("SELECT COUNT(e) FROM EquipementEntity e WHERE e.centreId = :centreId AND e.deletedAt IS NULL")
    long countByCentreId(@Param("centreId") UUID centreId);

    /**
     * Compte les équipements d'un centre par statut (pour les statistiques du dashboard GMAO)
     */
    @Query("SELECT COUNT(e) FROM EquipementEntity e WHERE e.centreId = :centreId AND e.statut = :statut AND e.deletedAt IS NULL")
    long countByCentreIdAndStatut(@Param("centreId") UUID centreId, @Param("statut") String statut);

    /**
     * Page des équipements d'un centre (liste paginée — AGENTS.md §9)
     */
    @Query(value = "SELECT e FROM EquipementEntity e WHERE e.centreId = :centreId AND e.deletedAt IS NULL",
            countQuery = "SELECT COUNT(e) FROM EquipementEntity e WHERE e.centreId = :centreId AND e.deletedAt IS NULL")
    Page<EquipementEntity> findPageByCentreId(@Param("centreId") UUID centreId, Pageable pageable);

    /**
     * Page des équipements d'un centre filtrés par statut (liste paginée — AGENTS.md §9)
     */
    @Query(value = "SELECT e FROM EquipementEntity e WHERE e.centreId = :centreId AND e.statut = :statut AND e.deletedAt IS NULL",
            countQuery = "SELECT COUNT(e) FROM EquipementEntity e WHERE e.centreId = :centreId AND e.statut = :statut AND e.deletedAt IS NULL")
    Page<EquipementEntity> findPageByCentreIdAndStatut(@Param("centreId") UUID centreId, @Param("statut") String statut, Pageable pageable);
}

