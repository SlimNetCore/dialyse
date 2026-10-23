package com.hemodialyse.backend.contexts.facturation.infrastructure.transaction;

import com.hemodialyse.backend.contexts.facturation.application.EcritureConcurrenteDeFacturation;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Traduction des refus de verrou par l'unité de travail (ADR 0043) : délai dépassé ou interblocage = écriture concurrente (409) ;
 * toute autre erreur est relancée telle quelle. Sans base : un gestionnaire de transactions qui ne fait rien.
 */
class UniteDeTravailSpringTest {

    private final UniteDeTravailSpring unite = new UniteDeTravailSpring(new TransactionTemplate(new GestionnaireSansRessource()));

    @Test
    void should_return_the_result_of_the_action() {
        assertThat(unite.executer(() -> 42)).isEqualTo(42);
    }

    @Test
    void should_turn_a_lock_failure_or_a_deadlock_into_a_concurrent_write() {
        assertThatThrownBy(() -> unite.executer(() -> {
            throw new CannotAcquireLockException("verrou");
        })).isInstanceOf(EcritureConcurrenteDeFacturation.class).hasCauseInstanceOf(CannotAcquireLockException.class);
        assertThatThrownBy(() -> unite.executer(() -> {
            throw new PessimisticLockingFailureException("interblocage");
        })).isInstanceOf(EcritureConcurrenteDeFacturation.class);
    }

    @Test
    void should_turn_an_uncategorized_postgresql_lock_timeout_into_a_concurrent_write() {
        assertThatThrownBy(() -> unite.executer(() -> {
            throw new UncategorizedSQLException("verrou", "SELECT", new SQLException("lock timeout", "55P03"));
        })).isInstanceOf(EcritureConcurrenteDeFacturation.class);
    }

    @Test
    void should_rethrow_any_other_failure_unchanged() {
        assertThatThrownBy(() -> unite.executer(() -> {
            throw new UncategorizedSQLException("autre", "SELECT", new SQLException("syntaxe", "42601"));
        })).isInstanceOf(UncategorizedSQLException.class);
        assertThatThrownBy(() -> unite.executer(() -> {
            throw new DataIntegrityViolationException("doublon");
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> unite.executer(() -> {
            throw new IllegalStateException("metier");
        })).isInstanceOf(IllegalStateException.class);
    }

    private static final class GestionnaireSansRessource extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}

