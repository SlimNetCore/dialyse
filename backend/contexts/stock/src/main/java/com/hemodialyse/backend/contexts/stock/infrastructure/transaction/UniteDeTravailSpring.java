package com.hemodialyse.backend.contexts.stock.infrastructure.transaction;

import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Unité de travail Spring : une transaction (REQUIRED) autour du cas d'usage ; les adapters de persistence s'y joignent.
 */
public final class UniteDeTravailSpring implements UniteDeTravail {

    private final TransactionTemplate transaction;

    public UniteDeTravailSpring(TransactionTemplate transaction) {
        this.transaction = transaction;
    }

    @Override
    public <T> T executer(Supplier<T> travail) {
        return transaction.execute(statut -> travail.get());
    }
}

