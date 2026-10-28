package com.hemodialyse.backend.contexts.stock.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

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

    /**
     * Identifiant de l'utilisateur authentifié, texte ; {@code null} si inconnu. L'auteur d'un bon vient de la session et non
     * du corps de la requête (KB-STK-22 corrigé).
     */
    String utilisateur();

    /**
     * Nom de l'utilisateur authentifié (celui que l'inventaire affiche comme « ouvert par », « compté par » ; le legacy le stocke
     * ainsi) ; par défaut, son identifiant.
     */
    default String nomUtilisateur() {
        return utilisateur();
    }
}
