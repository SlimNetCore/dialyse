package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement publié à la capture d'un instantané mensuel (langage publié
 * {@code pilotagedirection.v1}, slice PD2). Le centre et le mois sont des valeurs brutes
 * pour ne pas exposer le shared-kernel (règle ArchUnit
 * {@code le_langage_publie_ne_depend_de_rien_du_contexte}).
 */
public record InstantaneMensuelCapture(UUID eventId, UUID instantaneId, UUID centreId,
                                       int annee, int mois, Instant dateCapture) {
}
