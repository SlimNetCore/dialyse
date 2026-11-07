package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Une autre écriture tient la même demande d'examen (délai de verrou de 5 s dépassé) : 409 {@code DEMANDE_EXAMEN_ECRITURE_CONCURRENTE}
 * (KB-MED-41 corrigé : le legacy laissait le dernier écrivain gagner, sans verrou). Relire puis recommencer.
 */
public class EcritureConcurrenteSurUneDemandeDExamen extends EcritureConcurrente {
}
