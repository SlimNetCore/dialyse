package com.hemodialyse.backend.domain.gmao.port;

import com.hemodialyse.backend.domain.gmao.model.EquipementStatutHistorique;

import java.util.List;
import java.util.UUID;

/**
 * Port (interface) de persistance pour le journal de statut des équipements.
 * Contrat hexagonal : le domaine ne connaît pas l'implémentation.
 */
public interface EquipementStatutHistoriqueRepositoryPort {

    void save(EquipementStatutHistorique entree);

    /**
     * Historique d'un équipement, trié du plus ancien au plus récent (nécessaire au calcul d'indisponibilité).
     */
    List<EquipementStatutHistorique> findByEquipementIdOrderByChangedAtAsc(UUID equipementId);
}
