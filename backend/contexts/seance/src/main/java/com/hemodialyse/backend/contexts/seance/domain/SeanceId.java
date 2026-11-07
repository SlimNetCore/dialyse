package com.hemodialyse.backend.contexts.seance.domain;

import java.util.UUID;

/** Identité d'une séance (colonne {@code seances.id}), tirée par l'application, jamais par le domaine. */
public record SeanceId(UUID valeur) {
    public SeanceId {
        if (valeur == null) throw new SaisieInvalide("Séance requise");
    }
}
