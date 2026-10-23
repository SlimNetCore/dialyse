package com.hemodialyse.backend.contexts.facturation.application;

import java.util.function.Supplier;

/**
 * Port sortant : frontière transactionnelle d'un cas d'usage (une transaction, annulée si le cas d'usage échoue).
 * <p>
 * Politique de verrou (ADR 0043) : l'attente d'un verrou de ligne est bornée par l'unité de travail qui <b>ouvre</b> la transaction ;
 * au-delà, ou en cas d'interblocage, l'exécution échoue avec {@link EcritureConcurrenteDeFacturation}.
 */
public interface UniteDeTravail {
    <T> T executer(Supplier<T> action);
}
