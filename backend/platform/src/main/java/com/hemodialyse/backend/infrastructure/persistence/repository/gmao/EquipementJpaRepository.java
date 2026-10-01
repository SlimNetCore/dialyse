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
     * Récupère un équipement par code (unique par centre — AGENTS.md §2, pas globalement)
     */
    @Query("SELECT e FROM EquipementEntity e WHERE e.centreId = :centreId AND e.code = :code AND e.deletedAt IS NULL")
    Optional<EquipementEntity> findByCentreIdAndCode(@Param("centreId") UUID centreId, @Param("code") String code);

    /**
     * Équipements d'un centre par type (utilisé pour retrouver les générateurs de dialyse — référentiel unifié)
     */
    @Query("SELECT e FROM EquipementEntity e WHERE e.centreId = :centreId AND e.type = :type AND e.deletedAt IS NULL ORDER BY e.code")
    List<EquipementEntity> findByCentreIdAndType(@Param("centreId") UUID centreId, @Param("type") String type);

    /**
     * Équipements d'un centre par type et salle
     */
    @Query("SELECT e FROM EquipementEntity e WHERE e.centreId = :centreId AND e.type = :type AND e.salleId = :salleId AND e.deletedAt IS NULL ORDER BY e.code")
    List<EquipementEntity> findByCentreIdAndTypeAndSalleId(@Param("centreId") UUID centreId, @Param("type") String type, @Param("salleId") UUID salleId);

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

