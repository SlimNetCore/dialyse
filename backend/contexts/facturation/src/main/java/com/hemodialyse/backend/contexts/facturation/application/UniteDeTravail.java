package com.hemodialyse.backend.contexts.facturation.application;

import java.util.function.Supplier;

/**
 * Port sortant : frontière transactionnelle d'un cas d'usage (une transaction, annulée si le cas d'usage échoue).
 */
public interface UniteDeTravail {
    <T> T executer(Supplier<T> action);
}
