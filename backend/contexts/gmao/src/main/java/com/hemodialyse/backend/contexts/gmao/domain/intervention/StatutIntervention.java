package com.hemodialyse.backend.contexts.gmao.domain.intervention;

/**
 * Statut d'une intervention (R10). {@code EN_ATTENTE_VALIDATION} n'est atteint par aucune commande (KB-GMAO-53) : il ne se
 * lit qu'en base ; une telle intervention peut être annulée et recevoir des lignes de coût, mais ni démarrer ni se clôturer.
 */
public enum StatutIntervention {
    PLANIFIEE,
    EN_COURS,
    TERMINEE,
    ANNULEE,
    EN_ATTENTE_VALIDATION
}
