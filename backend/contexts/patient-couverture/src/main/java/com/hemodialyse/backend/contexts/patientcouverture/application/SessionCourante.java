package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/** Port sortant : le centre de la session de l'utilisateur authentifié (Identité et accès, hors contexte). */
public interface SessionCourante {

    /**
     * Centre de la requête : celui du jeton. Refuse ({@link AccesRefuse}, 403) un centre demandé qui n'est pas le sien, un
     * rôle inconnu ou absent, une session sans centre (décision du 02/11, KB-PAT-06/07). Nul : « celui de ma session ».
     */
    CentreId centre(UUID centreDemande);
}
