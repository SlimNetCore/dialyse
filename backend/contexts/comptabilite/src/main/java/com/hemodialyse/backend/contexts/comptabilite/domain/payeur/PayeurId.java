package com.hemodialyse.backend.contexts.comptabilite.domain.payeur;

import java.util.UUID;

/**
 * Identité d'un payeur (caisse, mutuelle, entreprise) : le référentiel des payeurs est tenu hors de la comptabilité.
 */
public record PayeurId(UUID valeur) {
    public PayeurId {
        if (valeur == null) throw new IllegalArgumentException("Payeur requis");
    }
}
