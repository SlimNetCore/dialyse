package com.hemodialyse.backend.contexts.dossiermedical.application;

import java.util.function.Supplier;

/**
 * Port sortant : frontière transactionnelle d'un cas d'usage (une transaction, annulée si le cas d'usage échoue). Sur
 * PostgreSQL l'attente d'un verrou est bornée à 5 s : au-delà, {@code EcritureConcurrente} (409).
 */
public interface UniteDeTravail {
    <T> T executer(Supplier<T> action);

    /**
     * Action dans sa PROPRE transaction, validée même si la transaction appelante échoue ensuite (réservation d'un numéro de la
     * séquence légale : le legacy incrémentait le compteur dans sa propre transaction, KB-MED-20 reproduit).
     */
    <T> T independante(Supplier<T> action);
}
