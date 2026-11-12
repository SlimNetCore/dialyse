package com.hemodialyse.backend.contexts.notifications.application;

import java.util.UUID;

/** Port sortant vers la session de l'utilisateur (Identité et accès, hors contexte). */
public interface SessionCourante {

    /**
     * Ouvre la session pour une requête sur ce centre ({@code null} : celui de la session).
     *
     * @throws AccesRefuse session invalide, sans centre, ou centre demandé qui n'est pas celui de la session
     */
    Session ouvrir(UUID centreDemande);
}
