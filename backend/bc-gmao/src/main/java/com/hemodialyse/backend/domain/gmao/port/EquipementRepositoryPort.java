package com.hemodialyse.backend.domain.gmao.port;

import com.hemodialyse.backend.domain.gmao.model.Equipement;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (interface) de persistance pour Equipement
 * Contrat hexagonal : le domaine ne connaît pas l'implémentation
 */
public interface EquipementRepositoryPort {

    /**
     * Sauvegarde un équipement (création ou mise à jour)
     */
    void save(Equipement equipement);

    /**
     * Récupère un équipement par son ID
     */
    Optional<Equipement> findById(UUID id);

    /**
     * Récupère tous les équipements d'un centre
     */
    List<Equipement> findByCentreId(UUID centreId);

    /**
     * Récupère les équipements d'un centre par statut
     */
    List<Equipement> findByCentreIdAndStatut(UUID centreId, String statut);

    /**
     * Page des équipements d'un centre, filtrée par statut si fourni (pagination obligatoire — AGENTS.md §9)
     */
    PagedResult<Equipement> findPaged(UUID centreId, String statut, int page, int size);

    /**
     * Compte les équipements d'un centre par statut (statistiques du dashboard GMAO)
     */
    long countByCentreIdAndStatut(UUID centreId, String statut);

    /**
     * Récupère un équipement par son code (unique par centre)
     */
    Optional<Equipement> findByCentreIdAndCode(UUID centreId, String code);

    /**
     * Équipements d'un centre pour un type donné (ex. GENERATEUR_DIALYSE pour le référentiel unifié)
     */
    List<Equipement> findByCentreIdAndType(UUID centreId, String type);

    /**
     * Équipements d'un centre pour un type et une salle donnés
     */
    List<Equipement> findByCentreIdAndTypeAndSalleId(UUID centreId, String type, UUID salleId);

    /**
     * Vrai si un équipement existe déjà avec cet id (migration idempotente depuis l'ancien référentiel).
     */
    boolean existsById(UUID id);

    /**
     * Supprime un équipement (soft delete recommandé)
     */
    void delete(UUID id);

    /**
     * Compte les équipements d'un centre
     */
    long countByCentreId(UUID centreId);
}

