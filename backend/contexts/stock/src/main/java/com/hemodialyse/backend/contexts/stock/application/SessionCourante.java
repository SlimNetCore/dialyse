package com.hemodialyse.backend.contexts.stock.application;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;

import java.util.UUID;

/**
 * Port sortant : le centre de la session de l'utilisateur authentifié (contexte Identité et accès, hors Stock). Implémenté par
 * une ACL vers la session legacy.
 */
public interface SessionCourante {

    /**
     * Centre à utiliser pour la requête : celui de la session ; un centre demandé qui n'est pas le sien est refusé (403,
     * KB-STK-04 corrigé) ; {@code null} signifie « celui de ma session » (KB-STK-05 corrigé).
     */
    CentreId centre(UUID centreDemande);
}

