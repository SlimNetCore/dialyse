package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Set;

/** Qui consulte : le centre retenu pour la requête, le compte de l'utilisateur et ses rôles (sans préfixe {@code ROLE_}). */
public record Session(CentreId centre, String utilisateur, Set<String> roles) {
    public Session {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
