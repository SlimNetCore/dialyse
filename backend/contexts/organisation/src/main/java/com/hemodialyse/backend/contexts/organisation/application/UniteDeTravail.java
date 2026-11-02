package com.hemodialyse.backend.contexts.organisation.application;

import java.util.function.Supplier;

/**
 * Port sortant : exécute une action dans une seule transaction (rollback sur exception ; rejoint la transaction en cours). Les
 * verrous pris par l'action sont tenus jusqu'à la fin de la transaction.
 */
public interface UniteDeTravail {

    <T> T executer(Supplier<T> action);

    /** Sans transaction : exécution directe (tests d'application avec des fakes en mémoire). */
    UniteDeTravail DIRECTE = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> action) {
            return action.get();
        }
    };
}
