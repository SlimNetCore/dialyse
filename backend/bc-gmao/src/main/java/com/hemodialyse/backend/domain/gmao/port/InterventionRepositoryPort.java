package com.hemodialyse.backend.domain.gmao.port;

import com.hemodialyse.backend.domain.gmao.model.Intervention;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (interface) de persistance pour Intervention
 * Contrat hexagonal : le domaine ne connaît pas l'implémentation
 */
public interface InterventionRepositoryPort {

    /**
     * Sauvegarde une intervention (création ou mise à jour)
     */
    void save(Intervention intervention);

    /**
     * Récupère une intervention par son ID
     */
    Optional<Intervention> findById(UUID id);

    /**
     * Récupère toutes les interventions d'un équipement
     */
    List<Intervention> findByEquipementId(UUID equipementId);

    /**
     * Récupère toutes les interventions d'un centre
     */
    List<Intervention> findByCentreId(UUID centreId);

    /**
     * Récupère les interventions d'un centre par statut
     */
    List<Intervention> findByCentreIdAndStatut(UUID centreId, String statut);

    /**
     * Récupère les interventions d'un centre dans une période donnée
     */
    List<Intervention> findByCentreIdAndDateRange(UUID centreId, LocalDateTime debut, LocalDateTime fin);

    /**
     * Récupère les interventions d'un technicien
     */
    List<Intervention> findByTechnicien(UUID technicienId);

    /**
     * Récupère les interventions planifiées ou en cours d'un équipement
     */
    List<Intervention> findPendingByEquipementId(UUID equipementId);

    /**
     * Supprime une intervention (soft delete recommandé)
     */
    void delete(UUID id);

    /**
     * Compte les interventions d'un centre
     */
    long countByCentreId(UUID centreId);

    /**
     * Compte les interventions en cours
     */
    long countByCentreIdAndStatutEnCours(UUID centreId);

    /**
     * Compte les interventions d'un centre par statut (statistiques du dashboard GMAO)
     */
    long countByCentreIdAndStatut(UUID centreId, String statut);

    /**
     * Page des interventions d'un centre, filtrée par statut si fourni (pagination obligatoire — AGENTS.md §9)
     */
    PagedResult<Intervention> findPaged(UUID centreId, String statut, int page, int size);

    /**
     * Page des interventions d'un équipement (pagination obligatoire — AGENTS.md §9)
     */
    PagedResult<Intervention> findPagedByEquipementId(UUID equipementId, int page, int size);
}

