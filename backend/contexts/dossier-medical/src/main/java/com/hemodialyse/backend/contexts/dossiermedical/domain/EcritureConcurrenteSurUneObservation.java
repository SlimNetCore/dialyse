package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Une autre écriture tient la même observation pendant la correction ou la suppression (délai de verrou de 5 s dépassé) : 409
 * {@code OBSERVATION_ECRITURE_CONCURRENTE}. Relire puis recommencer.
 */
public class EcritureConcurrenteSurUneObservation extends EcritureConcurrente {
}
