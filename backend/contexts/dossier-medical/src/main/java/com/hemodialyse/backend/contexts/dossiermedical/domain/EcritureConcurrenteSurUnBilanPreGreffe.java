package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Une autre écriture tient le même bilan pré-greffe (délai de verrou de 5 s dépassé) : 409 {@code BILAN_PRE_GREFFE_ECRITURE_CONCURRENTE}
 * (avant : dernier écrivain gagnant, une décision de RCP simultanée pouvait être perdue, KB-MED-91). Relire puis recommencer.
 */
public class EcritureConcurrenteSurUnBilanPreGreffe extends EcritureConcurrente {
}
