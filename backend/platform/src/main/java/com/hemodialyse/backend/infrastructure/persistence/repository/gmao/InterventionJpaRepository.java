package com.hemodialyse.backend.infrastructure.persistence.repository.gmao;

import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.InterventionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository pour InterventionEntity
 */
@Repository
public interface InterventionJpaRepository extends JpaRepository<InterventionEntity, UUID> {

    /**
     * Récupère les interventions d'un équipement
     */
    @Query("SELECT i FROM InterventionEntity i WHERE i.equipementId = :equipementId AND i.deletedAt IS NULL")
    List<InterventionEntity> findByEquipementId(@Param("equipementId") UUID equipementId);

    /**
     * Récupère les interventions d'un centre
     */
    @Query("SELECT i FROM InterventionEntity i WHERE i.centreId = :centreId AND i.deletedAt IS NULL")
    List<InterventionEntity> findByCentreId(@Param("centreId") UUID centreId);

    /**
     * Récupère les interventions d'un centre par statut
     */
    @Query("SELECT i FROM InterventionEntity i WHERE i.centreId = :centreId AND i.statut = :statut AND i.deletedAt IS NULL")
    List<InterventionEntity> findByCentreIdAndStatut(@Param("centreId") UUID centreId, @Param("statut") String statut);

    /**
     * Récupère les interventions dans une période
     */
    @Query("SELECT i FROM InterventionEntity i WHERE i.centreId = :centreId AND i.dateDebut >= :debut AND i.dateDebut <= :fin AND i.deletedAt IS NULL")
    List<InterventionEntity> findByCentreIdAndDateRange(@Param("centreId") UUID centreId, @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Récupère les interventions d'un intervenant
     */
    @Query("SELECT i FROM InterventionEntity i WHERE i.intervenantId = :intervenantId AND i.deletedAt IS NULL")
    List<InterventionEntity> findByIntervenantId(@Param("intervenantId") UUID intervenantId);

    /**
     * Récupère les interventions en attente (planifiées ou en cours)
     */
    @Query("SELECT i FROM InterventionEntity i WHERE i.equipementId = :equipementId AND i.statut IN ('PLANIFIEE', 'EN_COURS') AND i.deletedAt IS NULL")
    List<InterventionEntity> findPendingByEquipementId(@Param("equipementId") UUID equipementId);

    /**
     * Compte les interventions d'un centre
     */
    @Query("SELECT COUNT(i) FROM InterventionEntity i WHERE i.centreId = :centreId AND i.deletedAt IS NULL")
    long countByCentreId(@Param("centreId") UUID centreId);

    /**
     * Compte les interventions en cours
     */
    @Query("SELECT COUNT(i) FROM InterventionEntity i WHERE i.centreId = :centreId AND i.statut = 'EN_COURS' AND i.deletedAt IS NULL")
    long countByCentreIdAndStatutEnCours(@Param("centreId") UUID centreId);

    /**
     * Compte les interventions d'un centre par statut (statistiques du dashboard GMAO)
     */
    @Query("SELECT COUNT(i) FROM InterventionEntity i WHERE i.centreId = :centreId AND i.statut = :statut AND i.deletedAt IS NULL")
    long countByCentreIdAndStatut(@Param("centreId") UUID centreId, @Param("statut") String statut);

    /**
     * Page des interventions d'un centre, filtrées par équipement si fourni (liste paginée — AGENTS.md §9)
     */
    @Query(value = "SELECT i FROM InterventionEntity i WHERE i.centreId = :centreId AND i.deletedAt IS NULL",
            countQuery = "SELECT COUNT(i) FROM InterventionEntity i WHERE i.centreId = :centreId AND i.deletedAt IS NULL")
    Page<InterventionEntity> findPageByCentreId(@Param("centreId") UUID centreId, Pageable pageable);

    /**
     * Page des interventions d'un centre filtrées par statut (liste paginée — AGENTS.md §9)
     */
    @Query(value = "SELECT i FROM InterventionEntity i WHERE i.centreId = :centreId AND i.statut = :statut AND i.deletedAt IS NULL",
            countQuery = "SELECT COUNT(i) FROM InterventionEntity i WHERE i.centreId = :centreId AND i.statut = :statut AND i.deletedAt IS NULL")
    Page<InterventionEntity> findPageByCentreIdAndStatut(@Param("centreId") UUID centreId, @Param("statut") String statut, Pageable pageable);

    /**
     * Page des interventions d'un équipement (liste paginée — AGENTS.md §9)
     */
    @Query(value = "SELECT i FROM InterventionEntity i WHERE i.equipementId = :equipementId AND i.deletedAt IS NULL",
            countQuery = "SELECT COUNT(i) FROM InterventionEntity i WHERE i.equipementId = :equipementId AND i.deletedAt IS NULL")
    Page<InterventionEntity> findPageByEquipementId(@Param("equipementId") UUID equipementId, Pageable pageable);

    /**
     * Compte les interventions d'un équipement (fiche équipement — aide à la décision)
     */
    @Query("SELECT COUNT(i) FROM InterventionEntity i WHERE i.equipementId = :equipementId AND i.deletedAt IS NULL")
    long countByEquipementId(@Param("equipementId") UUID equipementId);

    /**
     * Dernière intervention (par date de début) d'un équipement
     */
    @Query("SELECT i FROM InterventionEntity i WHERE i.equipementId = :equipementId AND i.deletedAt IS NULL ORDER BY i.dateDebut DESC")
    List<InterventionEntity> findByEquipementIdOrderByDateDebutDesc(@Param("equipementId") UUID equipementId, Pageable pageable);
}

