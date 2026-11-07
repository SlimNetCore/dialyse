package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Une autre écriture tient le même donneur vivant pendant la mise à jour (délai de verrou de 5 s dépassé) : 409 {@code DONNEUR_VIVANT_ECRITURE_CONCURRENTE}. */
public class EcritureConcurrenteSurUnDonneurVivant extends EcritureConcurrente {
}
