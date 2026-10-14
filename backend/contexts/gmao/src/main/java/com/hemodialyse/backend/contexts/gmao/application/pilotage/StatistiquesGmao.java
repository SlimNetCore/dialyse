package com.hemodialyse.backend.contexts.gmao.application.pilotage;

/**
 * Statistiques du tableau de bord GMAO d'un centre. Le total d'équipements n'est pas la somme des trois compteurs par statut :
 * les autres statuts (en attente de pièce, à réformer, réformé, désactivé) ne sont comptés que dans le total (KB-GMAO-77).
 */
public record StatistiquesGmao(long totalEquipements, long equipementsEnService, long equipementsEnMaintenance,
                               long equipementsHorsService, long totalInterventions, long interventionsEnCours,
                               long interventionsTerminees, long plansMaintenanceActifs, long plansMaintenanceEnRetard,
                               long interventionsARelancer) {
}
