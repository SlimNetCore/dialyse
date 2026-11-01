package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement de domaine publié : un résultat sérologique positif a été détecté pour un patient.
 * Produit par la couche application après exécution de {@code EnregistrerSerologie}.
 * Consommé par le contexte Planification (port publié {@code RisquesInfectieux}).
 * Le centre est un UUID brut pour ne pas exposer le shared-kernel (règle ArchUnit
 * {@code le_langage_publie_ne_depend_de_rien_du_contexte}).
 */
public record SerologiePositiveDetectee(UUID eventId, UUID serologieId, UUID patientId,
                                        UUID centreId, String typeSerologie, Instant dateDetection) {
}
