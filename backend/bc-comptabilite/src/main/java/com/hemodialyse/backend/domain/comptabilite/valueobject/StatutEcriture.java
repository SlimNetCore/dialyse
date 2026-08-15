package com.hemodialyse.backend.domain.comptabilite.valueobject;

/**
 * Statut d'une écriture comptable.
 * EXPORTEE est immuable : toute correction passe par une extourne.
 */
public enum StatutEcriture {
    BROUILLON,
    VALIDEE,
    EXPORTEE
}

