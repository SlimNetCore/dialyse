package com.hemodialyse.backend.domain.seance.model;

public enum SeanceStatus {
    CREE,
    VALIDEE,
    SIGNEE,
    FACTUREE,
    /**
     * Séance exclue de la facturation depuis la simulation (patient finalement absent) : elle n'est ni réalisée ni
     * facturable et ne se valide plus.
     */
    ABSENT
}

