package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement publié à la complétion du bilan pré-greffe (langage publié {@code dossiermedical.v1},
 * slice DM7). Consommé par Patient &amp; couverture (greffe).
 * Le centre est un UUID brut pour ne pas exposer le shared-kernel (règle ArchUnit
 * {@code le_langage_publie_ne_depend_de_rien_du_contexte}).
 */
public record BilanPreGreffeComplete(UUID eventId, UUID bilanId, UUID patientId,
                                     UUID centreId, Instant dateCompletion) {
}
