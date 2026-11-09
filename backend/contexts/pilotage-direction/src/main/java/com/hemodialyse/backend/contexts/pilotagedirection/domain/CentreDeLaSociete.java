package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.UUID;

/** Un centre de la société (actif ou non) : seule son identité compte pour la direction. */
public record CentreDeLaSociete(UUID id, String nom, boolean actif) {
}
