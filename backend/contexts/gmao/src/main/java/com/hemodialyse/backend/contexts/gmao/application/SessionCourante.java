package com.hemodialyse.backend.contexts.gmao.application;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

/**
 * Port sortant : l'utilisateur authentifié et son centre de session (contexte Identité et accès, hors GMAO).
 * Implémenté par une ACL vers la session legacy.
 */
public interface SessionCourante {
    CentreId centre();

    UtilisateurId utilisateur();

    /** L'identifiant de connexion de l'utilisateur (et non son nom complet) : « édité par » du bon d'intervention. */
    String identifiant();
}
