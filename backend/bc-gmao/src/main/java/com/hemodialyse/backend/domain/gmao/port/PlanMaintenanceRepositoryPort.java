package com.hemodialyse.backend.domain.gmao.port;

import com.hemodialyse.backend.domain.gmao.model.PlanMaintenance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (interface) de persistance pour PlanMaintenance
 * Contrat hexagonal : le domaine ne connaît pas l'implémentation
 */
public interface PlanMaintenanceRepositoryPort {

    /**
     * Sauvegarde un plan de maintenance (création ou mise à jour)
     */
    void save(PlanMaintenance plan);

    /**
     * Récupère un plan de maintenance par son ID
     */
    Optional<PlanMaintenance> findById(UUID id);

    /**
     * Récupère tous les plans de maintenance d'un équipement
     */
    List<PlanMaintenance> findByEquipementId(UUID equipementId);

    /**
     * Récupère tous les plans de maintenance d'un centre
     */
    List<PlanMaintenance> findByCentreId(UUID centreId);

    /**
     * Récupère les plans de maintenance actifs d'un centre
     */
    List<PlanMaintenance> findActiveByCentreId(UUID centreId);

    /**
     * Récupère les plans de maintenance dont la prochaine date est passée
     */
    List<PlanMaintenance> findOverdueByCentreId(UUID centreId, LocalDateTime dateLimit);

    /**
     * Récupère les plans de maintenance actifs d'un équipement
     */
    List<PlanMaintenance> findActiveByEquipementId(UUID equipementId);

    /**
     * Supprime un plan de maintenance (soft delete recommandé)
     */
    void delete(UUID id);

    /**
     * Compte les plans de maintenance d'un centre
     */
    long countByCentreId(UUID centreId);

    /**
     * Compte les plans de maintenance actifs d'un centre
     */
    long countActiveByCentreId(UUID centreId);
}

