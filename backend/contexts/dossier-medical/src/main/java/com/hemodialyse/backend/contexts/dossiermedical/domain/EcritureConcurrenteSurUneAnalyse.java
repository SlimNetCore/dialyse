package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Une autre écriture tient la même analyse pendant la modification ou la suppression (délai de verrou de 5 s dépassé) : 409
 * {@code ANALYSE_ECRITURE_CONCURRENTE}. Relire puis recommencer.
 */
public class EcritureConcurrenteSurUneAnalyse extends EcritureConcurrente {
}
