package com.hemodialyse.backend.contexts.seance.infrastructure.identifiants;

import com.hemodialyse.backend.contexts.seance.application.GenerateurDIdentifiants;

import java.util.UUID;

/** Identifiants aléatoires (UUID v4), comme le legacy. */
public class GenerateurDIdentifiantsAleatoires implements GenerateurDIdentifiants {
    @Override
    public UUID suivant() {
        return UUID.randomUUID();
    }
}
