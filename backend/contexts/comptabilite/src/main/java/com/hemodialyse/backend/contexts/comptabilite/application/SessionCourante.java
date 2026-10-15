package com.hemodialyse.backend.contexts.comptabilite.application;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.util.UUID;

/**
 * Port sortant : le centre de la session de l'utilisateur authentifié (contexte Identité et accès, hors Comptabilité).
 * Implémenté par une ACL vers la session legacy.
 */
public interface SessionCourante {

    /**
     * Centre à utiliser pour la requête courante : celui de la session. Refuse (accès refusé, 403) un centre demandé qui
     * n'est pas le sien ; {@code null} signifie « celui de ma session ».
     */
    CentreId centre(UUID centreDemande);
}
