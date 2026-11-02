package com.hemodialyse.backend.contexts.organisation.domain;

/** Une autre écriture tient le verrou de la société : réessayer (409). */
public class EcritureConcurrente extends RuntimeException {
    public EcritureConcurrente() {
        super("ORGANISATION_ECRITURE_CONCURRENTE");
    }
}
