package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Une autre écriture tient le même antécédent, ou le même diagnostic du même patient, pendant l'écriture (délai de verrou de 5 s
 * dépassé) : 409 {@code ANTECEDENT_ECRITURE_CONCURRENTE}. Ce verrou protège l'unicité applicative du diagnostic actif sans contrainte
 * en base (KB-MED-64). Relire puis recommencer.
 */
public class EcritureConcurrenteSurUnAntecedent extends EcritureConcurrente {
}
