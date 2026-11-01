package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement publié à la génération d'un export de synthèse (langage publié
 * {@code pilotagedirection.v1}, slice PD4). Le format est une chaîne pour ne pas exposer
 * le domaine (règle ArchUnit {@code le_langage_publie_ne_depend_de_rien_du_contexte}).
 */
public record ExportSyntheseGenere(UUID eventId, UUID exportId, UUID centreId,
                                   String periode, String format, Instant dateGeneration) {
}
