package com.hemodialyse.backend.contexts.gmao.infrastructure.transaction;

import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Adapter du port {@link UniteDeTravail} : une transaction Spring par exécution (rollback sur exception). */
public class UniteDeTravailSpring implements UniteDeTravail {

    private final TransactionTemplate transaction;

    public UniteDeTravailSpring(TransactionTemplate transaction) {
        this.transaction = transaction;
    }

    @Override
    public <T> T executer(Supplier<T> action) {
        return transaction.execute(statut -> action.get());
    }
}
