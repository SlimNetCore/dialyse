package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Une autre écriture tient la même étape pendant la mise à jour (délai de verrou de 5 s dépassé) : 409 {@code ETAPE_BILAN_ECRITURE_CONCURRENTE}. */
public class EcritureConcurrenteSurUneEtapeDeBilan extends EcritureConcurrente {
}
