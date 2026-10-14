package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

/**
 * Statut d'un plan de maintenance. {@code INACTIF} = plan suspendu. {@code ARCHIVE} n'est fixé par aucune commande
 * (KB-GMAO-06) : conservé pour les plans hérités du legacy.
 */
public enum StatutPlan {
    ACTIF,
    INACTIF,
    ARCHIVE
}
