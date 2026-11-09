package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.UUID;

/** Une ligne de l'historique non encore résolue : son identifiant technique et sa clé (centre, code). */
public record AlerteOuverte(UUID id, CleDAlerte cle) {
}
