package com.hemodialyse.backend.contexts.absencespatients.application;

import java.util.function.Supplier;

/**
 * Port sortant : frontière transactionnelle d'un cas d'usage (une transaction, annulée si le cas d'usage échoue ; rejointe si
 * une transaction est déjà ouverte, comme celle de l'exclusion d'une séance par la Facturation).
 */
public interface UniteDeTravail {
    <T> T executer(Supplier<T> action);
}
