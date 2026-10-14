package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.util.Optional;

/**
 * Port sortant : le nom sous lequel un utilisateur est connu (contexte Identité et accès, hors GMAO) : son nom complet, à
 * défaut son identifiant de connexion ; vide si l'utilisateur est inconnu. Implémenté par une ACL vers le legacy.
 */
public interface NomsDesUtilisateurs {

    Optional<String> de(UtilisateurId utilisateur);
}
