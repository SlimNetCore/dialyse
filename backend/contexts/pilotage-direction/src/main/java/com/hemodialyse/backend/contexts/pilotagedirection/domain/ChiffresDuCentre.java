package com.hemodialyse.backend.contexts.pilotagedirection.domain;

/** Les factures émises d'un centre sur une période (nombre) et leurs montants. */
public record ChiffresDuCentre(long factures, ChiffresFinanciers montants) {

    public static ChiffresDuCentre aucun() {
        return new ChiffresDuCentre(0, ChiffresFinanciers.zero());
    }
}
