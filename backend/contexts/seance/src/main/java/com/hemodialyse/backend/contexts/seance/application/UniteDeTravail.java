package com.hemodialyse.backend.contexts.seance.application;

import java.util.function.Supplier;

/**
 * Port sortant : frontière transactionnelle d'un cas d'usage (une transaction, annulée si le cas d'usage échoue : un scan qui crée puis
 * valide ne laisse jamais une séance créée à moitié). Sur PostgreSQL l'attente d'un verrou est bornée à 5 s : au-delà,
 * {@code EcritureConcurrente} (409).
 */
public interface UniteDeTravail {
    <T> T executer(Supplier<T> action);
}
