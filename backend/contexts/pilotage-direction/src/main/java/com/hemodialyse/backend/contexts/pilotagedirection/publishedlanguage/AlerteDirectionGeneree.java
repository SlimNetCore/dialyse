package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Événement publié à la génération d'une alerte direction (langage publié
 * {@code pilotagedirection.v1}, slice PD3). Le type est une chaîne pour ne pas exposer
 * le domaine (règle ArchUnit {@code le_langage_publie_ne_depend_de_rien_du_contexte}).
 */
public record AlerteDirectionGeneree(UUID eventId, UUID alerteId, UUID centreId,
                                     String type, LocalDate date, Instant dateGeneration) {
}
