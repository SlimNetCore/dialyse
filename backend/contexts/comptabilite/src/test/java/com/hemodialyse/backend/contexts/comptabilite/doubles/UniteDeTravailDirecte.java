package com.hemodialyse.backend.contexts.comptabilite.doubles;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;

import java.util.function.Supplier;

/**
 * Exécute l'action sans transaction : les doubles en mémoire n'ont rien à annuler.
 */
public class UniteDeTravailDirecte implements UniteDeTravail {
    @Override
    public <T> T executer(Supplier<T> action) {
        return action.get();
    }
}
