package com.hemodialyse.backend.contexts.gmao.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

/**
 * Port sortant : l'utilisateur authentifié et son centre de session (contexte Identité et accès, hors GMAO).
 * Implémenté par une ACL vers la session legacy.
 */
public interface SessionCourante {
    CentreId centre();

    /**
     * Le centre de session pour une impression (PL6, KB-PL6-1, décidé le 2026-11-12) : un jeton sans centre est refusé
     * ({@link SessionSansCentre}, 403), un centre illisible est rejeté ({@link CentreIllisible}, 400) sans message technique.
     */
    default CentreId centrePourImpression() {
        return centre();
    }

    UtilisateurId utilisateur();

    /** L'identifiant de connexion de l'utilisateur (et non son nom complet) : « édité par » du bon d'intervention. */
    String identifiant();
}
