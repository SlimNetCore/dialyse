package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;

import java.util.UUID;

/**
 * Port sortant : le centre de la session de l'utilisateur authentifié (contexte Identité et accès, hors Planification).
 * Implémenté par une ACL vers la session legacy.
 */
public interface SessionCourante {

    /**
     * Centre à utiliser pour la requête courante : celui de la session. Refuse (accès refusé, 403) un centre demandé qui
     * n'est pas le sien ; {@code null} signifie « celui de ma session ».
     */
    CentreId centre(UUID centreDemande);

    /**
     * Le compte de l'utilisateur authentifié (« Mon planning », auteur des absences).
     */
    CompteId utilisateur();

    /**
     * L'utilisateur authentifié a-t-il le rôle d'administrateur ? (le secrétariat prévient aussi l'administrateur)
     */
    boolean administrateur();
}
