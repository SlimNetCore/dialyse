package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement de domaine publié : un résultat sérologique positif a été détecté pour un patient.
 * Produit par la couche application après exécution de {@code EnregistrerSerologie}.
 * Consommé par le contexte Planification (port publié {@code RisquesInfectieux}).
 */
public record SerologiePositiveDetectee(UUID eventId, UUID serologieId, UUID patientId,
                                        CentreId centreId, String typeSerologie, Instant dateDetection) {
}
