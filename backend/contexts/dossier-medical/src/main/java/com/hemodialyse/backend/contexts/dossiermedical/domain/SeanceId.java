package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

/** Identité d'une séance de dialyse (colonne {@code seances.id}), propriété de la Séance ; ici une simple référence. */
public record SeanceId(UUID valeur) {
    public SeanceId {
        if (valeur == null) throw new SaisieInvalide("Séance requise");
    }
}
