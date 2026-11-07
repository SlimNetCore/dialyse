package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.identifiants;

import com.hemodialyse.backend.contexts.dossiermedical.application.GenerateurDIdentifiants;

import java.util.UUID;

/** Identifiants aléatoires (UUID v4), comme le legacy. */
public class GenerateurDIdentifiantsAleatoires implements GenerateurDIdentifiants {
    @Override
    public UUID suivant() {
        return UUID.randomUUID();
    }
}
