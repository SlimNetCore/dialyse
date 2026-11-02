package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.util.function.Supplier;

/** Port sortant : frontière transactionnelle d'un cas d'usage (annulée si le cas d'usage échoue ; rejointe si déjà ouverte). */
public interface UniteDeTravail {

    <T> T executer(Supplier<T> action);
}
