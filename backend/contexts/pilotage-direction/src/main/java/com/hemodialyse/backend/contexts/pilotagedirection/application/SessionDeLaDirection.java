package com.hemodialyse.backend.contexts.pilotagedirection.application;

import java.util.UUID;

/** Port sortant : la société de la direction connectée, revérifiée à chaque appel (jamais lue d'un paramètre de la requête). */
public interface SessionDeLaDirection {

    /** @throws RuntimeException accès refusé (403) : pas de session de direction, sans société, compte inactif ou détaché, société inactive */
    UUID societe();
}
