package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;


import java.util.UUID;

/**
 * Port sortant : le centre de la session de l'utilisateur authentifié (contexte Identité et accès, hors Absences).
 * Implémenté par une ACL vers la session legacy.
 */
public interface SessionCourante {

    /**
     * Centre à utiliser pour la requête courante : celui de la session. Refuse (accès refusé, 403) un centre demandé qui
     * n'est pas le sien (RG-ABS-023) ; {@code null} signifie « celui de ma session ».
     */
    CentreId centre(UUID centreDemande);

    /**
     * Celui qui écrit : l'utilisateur de la session et son droit de correction (administrateur ou médecin).
     */
    Intervenant intervenant();
}
