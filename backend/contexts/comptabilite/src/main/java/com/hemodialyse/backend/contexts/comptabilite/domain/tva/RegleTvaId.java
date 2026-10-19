package com.hemodialyse.backend.contexts.comptabilite.domain.tva;

import java.util.UUID;

/**
 * Identité d'une règle de TVA (colonne {@code id} de {@code tva_types}). Elle est fournie au domaine (ADR 0028) : tirée par
 * le cas d'usage à la création, lue en base ensuite.
 */
public record RegleTvaId(UUID valeur) {

    public RegleTvaId {
        if (valeur == null) throw new IllegalArgumentException("Identifiant de règle de TVA requis");
    }

    @Override
    public String toString() {
        return valeur.toString();
    }
}
