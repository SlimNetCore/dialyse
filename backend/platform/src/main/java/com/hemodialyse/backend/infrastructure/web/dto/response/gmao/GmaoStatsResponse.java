package com.hemodialyse.backend.infrastructure.web.dto.response.gmao;

/**
 * Statistiques agrégées du module GMAO pour le centre courant (dashboard).
 * Construit à partir de comptages serveur — ne charge jamais les listes complètes
 * (AGENTS.md §9 : les listes/dashboards ne doivent jamais charger toutes les données).
 */
public record GmaoStatsResponse(
        long totalEquipements,
        long equipementsEnService,
        long equipementsEnMaintenance,
        long equipementsHorsService,
        long totalInterventions,
        long interventionsEnCours,
        long interventionsTerminees,
        long plansMaintenanceActifs,
        long plansMaintenanceEnRetard,
        long interventionsARelancer
) {
}
