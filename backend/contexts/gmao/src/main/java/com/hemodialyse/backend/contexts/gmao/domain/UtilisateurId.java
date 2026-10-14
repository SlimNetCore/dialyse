package com.hemodialyse.backend.contexts.gmao.domain;

import java.util.UUID;

/** Identité d'un utilisateur (contexte Identité et accès), opaque pour GMAO. */
public record UtilisateurId(UUID valeur) {
    public UtilisateurId {
        if (valeur == null) throw new IllegalArgumentException("Utilisateur requis");
    }
}
