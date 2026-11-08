package com.hemodialyse.backend.contexts.seance.domain;

/** Une autre écriture tient la même ligne : 409 {@code ECRITURE_CONCURRENTE} (délai de verrou de 5 s dépassé). Relire puis recommencer. */
public class EcritureConcurrente extends RuntimeException {
    public EcritureConcurrente() {
        super("Cette séance vient d'être modifiée par une autre écriture : relire puis recommencer");
    }

    /** Autre ressource que la séance (SE3 : les raccourcis de consommables du centre). */
    public EcritureConcurrente(String message) {
        super(message);
    }
}
