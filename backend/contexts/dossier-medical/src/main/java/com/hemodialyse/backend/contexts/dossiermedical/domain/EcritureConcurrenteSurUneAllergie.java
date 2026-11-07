package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Une autre écriture tient la même allergie pendant la correction ou la suppression (délai de verrou de 5 s dépassé) : 409
 * {@code ALLERGIE_ECRITURE_CONCURRENTE}. Relire puis recommencer.
 */
public class EcritureConcurrenteSurUneAllergie extends EcritureConcurrente {
}
