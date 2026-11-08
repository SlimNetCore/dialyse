package com.hemodialyse.backend.contexts.seance.domain;

import java.util.UUID;

/** Identité d'un forfait du référentiel du centre (référence, jamais le forfait lui-même). */
public record ForfaitId(UUID valeur) {
    public ForfaitId {
        if (valeur == null) throw new SaisieInvalide("Forfait requis");
    }
}
