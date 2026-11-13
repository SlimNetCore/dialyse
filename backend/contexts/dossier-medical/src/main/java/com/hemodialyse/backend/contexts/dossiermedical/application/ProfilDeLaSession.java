package com.hemodialyse.backend.contexts.dossiermedical.application;

import java.util.Collection;

/** Port sortant : les rôles de la session de l'utilisateur authentifié (Identité et accès, hors contexte). */
public interface ProfilDeLaSession {

    /**
     * Les rôles de la session (préfixe {@code ROLE_}, ex. {@code ROLE_MEDECIN}).
     *
     * @throws AccesRefuse (403) si aucune session authentifiée
     */
    Collection<String> roles();
}
