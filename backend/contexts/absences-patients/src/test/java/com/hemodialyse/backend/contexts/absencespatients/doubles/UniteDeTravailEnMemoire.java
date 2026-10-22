package com.hemodialyse.backend.contexts.absencespatients.doubles;

import com.hemodialyse.backend.contexts.absencespatients.application.UniteDeTravail;

import java.util.function.Supplier;

/**
 * Transaction en mémoire : si l'action échoue, les absences retrouvent l'état d'avant (rollback), comme la base.
 */
public class UniteDeTravailEnMemoire implements UniteDeTravail {

    private final AbsencesEnMemoire absences;
    private int executions;

    public UniteDeTravailEnMemoire(AbsencesEnMemoire absences) {
        this.absences = absences;
    }

    public int executions() {
        return executions;
    }

    @Override
    public <T> T executer(Supplier<T> action) {
        executions++;
        var photo = absences.instantane();
        try {
            return action.get();
        } catch (RuntimeException e) {
            absences.restaurer(photo);
            throw e;
        }
    }
}
