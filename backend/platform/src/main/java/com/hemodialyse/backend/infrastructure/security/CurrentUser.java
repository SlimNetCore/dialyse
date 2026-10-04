package com.hemodialyse.backend.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilisateur authentifié de la requête courante, lu dans le contexte de sécurité (aucun utilisateur : « system »).
 */
public final class CurrentUser {

    public static final String SYSTEM = "system";

    private CurrentUser() {
    }

    /**
     * Nom d'utilisateur de l'auteur de la requête.
     */
    public static String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getName().isBlank())
            return SYSTEM;
        return auth.getName();
    }

    /**
     * L'auteur possède-t-il l'un de ces rôles (sans le préfixe {@code ROLE_}) ?
     */
    public static boolean hasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        for (String role : roles) {
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role))) return true;
        }
        return false;
    }
}
