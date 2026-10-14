package com.hemodialyse.backend.contexts.gmao.infrastructure.systeme;

import com.hemodialyse.backend.contexts.gmao.application.GenerateurIdentifiants;

import java.util.UUID;

/** Adapter du port {@link GenerateurIdentifiants} : UUID aléatoires. */
public class IdentifiantsAleatoires implements GenerateurIdentifiants {
    @Override
    public UUID nouvelIdentifiant() {
        return UUID.randomUUID();
    }
}
