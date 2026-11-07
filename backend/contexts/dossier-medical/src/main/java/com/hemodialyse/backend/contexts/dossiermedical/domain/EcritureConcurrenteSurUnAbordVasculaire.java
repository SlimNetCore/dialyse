package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Une autre écriture tient le même abord vasculaire pendant la modification ou la suppression (délai de verrou de 5 s dépassé) : 409
 * {@code ABORD_VASCULAIRE_ECRITURE_CONCURRENTE}. Relire puis recommencer.
 */
public class EcritureConcurrenteSurUnAbordVasculaire extends EcritureConcurrente {
}
