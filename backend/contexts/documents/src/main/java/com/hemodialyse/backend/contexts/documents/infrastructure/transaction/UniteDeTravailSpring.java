package com.hemodialyse.backend.contexts.documents.infrastructure.transaction;

import com.hemodialyse.backend.contexts.documents.application.UniteDeTravail;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Adapter du port {@link UniteDeTravail} : une transaction Spring par exécution (annulée sur exception), rejointe si elle existe déjà. */
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
