package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import java.util.UUID;

/** Identité d'une ligne de coût, interne à l'intervention (exposée dans les réponses, utilisée pour la retirer). */
public record LigneDeCoutId(UUID valeur) {
    public LigneDeCoutId {
        if (valeur == null) throw new IllegalArgumentException("Ligne de coût requise");
    }
}
