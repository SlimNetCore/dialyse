package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import java.util.UUID;

/** Identité d'une tâche, interne à l'intervention (exposée dans les réponses, utilisée pour la démarrer ou l'achever). */
public record TacheId(UUID valeur) {
    public TacheId {
        if (valeur == null) throw new IllegalArgumentException("Tâche requise");
    }
}
