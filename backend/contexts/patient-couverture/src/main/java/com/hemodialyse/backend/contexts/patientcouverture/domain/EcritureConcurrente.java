package com.hemodialyse.backend.contexts.patientcouverture.domain;

/**
 * Une autre écriture tient le verrou du centre ou de la fiche (délai dépassé) : 409 {@code PATIENT_ECRITURE_CONCURRENTE}. Le
 * client relit puis recommence.
 */
public class EcritureConcurrente extends RuntimeException {
    public EcritureConcurrente() {
        super("Cette fiche vient d'être modifiée par une autre écriture : relire puis recommencer");
    }
}
