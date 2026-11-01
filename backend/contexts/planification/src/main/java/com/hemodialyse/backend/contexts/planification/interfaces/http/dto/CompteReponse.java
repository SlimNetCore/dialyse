package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs.Compte;

import java.util.UUID;

public record CompteReponse(UUID id, String username, String nomComplet, boolean actif) {
    public static CompteReponse depuis(Compte c) {
        return c == null ? null : new CompteReponse(c.id().valeur(), c.identifiant(), c.nomComplet(), c.actif());
    }
}
