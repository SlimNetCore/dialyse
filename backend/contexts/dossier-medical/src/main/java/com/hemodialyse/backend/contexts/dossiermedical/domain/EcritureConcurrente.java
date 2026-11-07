package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Une autre écriture tient la même fiche : 409 {@code ECRITURE_CONCURRENTE} (délai de verrou de 5 s dépassé). Relire puis recommencer. */
public class EcritureConcurrente extends RuntimeException {
    public EcritureConcurrente() {
        super("Cette fiche vient d'être modifiée par une autre écriture : relire puis recommencer");
    }
}
