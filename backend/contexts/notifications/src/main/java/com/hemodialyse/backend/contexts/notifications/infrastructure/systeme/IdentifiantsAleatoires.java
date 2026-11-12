package com.hemodialyse.backend.contexts.notifications.infrastructure.systeme;

import com.hemodialyse.backend.contexts.notifications.application.Identifiants;

import java.util.UUID;

/** Identifiants aléatoires des notifications sans identifiant déterministe. */
public class IdentifiantsAleatoires implements Identifiants {

    @Override
    public UUID suivant() {
        return UUID.randomUUID();
    }
}
