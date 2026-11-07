package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/** Port sortant : la session de l'utilisateur authentifié (Identité et accès, hors contexte). */
public interface SessionCourante {

    /**
     * Centre de la requête : celui du jeton. Refuse ({@link AccesRefuse}, 403) un centre demandé qui n'est pas le sien, un rôle
     * inconnu ou absent, une session sans centre. Nul : « celui de ma session ».
     */
    CentreId centre(UUID centreDemande);

    /** Nom d'utilisateur de la session : auteur des diffusions et de la validation par scan. */
    String utilisateur();

    /** L'utilisateur est administrateur : seul habilité à franchir un jour de fermeture du centre. */
    boolean estAdministrateur();

    /** L'utilisateur valide la séance au scan (administrateur ou infirmier) ; sinon (secrétaire) le scan crée sans valider. */
    boolean valideAuScan();
}
