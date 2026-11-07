package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Une autre écriture tient la même sérologie, ou le même couple (patient, marqueur, date de prélèvement) pendant l'enregistrement
 * (délai de verrou de 5 s dépassé) : 409 {@code SEROLOGIE_ECRITURE_CONCURRENTE}. Ce verrou protège l'unicité applicative sans contrainte
 * en base (KB-MED-56). Relire puis recommencer.
 */
public class EcritureConcurrenteSurUneSerologie extends EcritureConcurrente {
}
