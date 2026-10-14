package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;

import java.util.function.Supplier;

/** Pas de transaction : exécute l'action telle quelle et compte les appels. */
public class UniteDeTravailDirecte implements UniteDeTravail {
    public int executions = 0;

    @Override
    public <T> T executer(Supplier<T> action) {
        executions++;
        return action.get();
    }
}
