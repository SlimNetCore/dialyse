package com.hemodialyse.backend.contexts.absencespatients.infrastructure.transaction;

import com.hemodialyse.backend.contexts.absencespatients.application.UniteDeTravail;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Adapter du port {@link UniteDeTravail} : une transaction Spring par exécution (rollback sur exception), rejointe si une
 * transaction est déjà ouverte. Sur PostgreSQL, {@code SET LOCAL lock_timeout} borne l'attente d'un verrou de ligne : au-delà, la
 * base refuse (SQLSTATE 55P03) et l'écriture reçoit 409 {@code ABSENCE_ECRITURE_CONCURRENTE} au lieu d'attendre sans limite ; H2 a
 * son propre délai de verrou. Sans {@code JdbcTemplate}, aucun délai n'est posé (tests unitaires).
 */
public class UniteDeTravailSpring implements UniteDeTravail {

    private final TransactionTemplate transaction;
    private final JdbcTemplate jdbc;
    private final Duration delaiDeVerrou;

    public UniteDeTravailSpring(TransactionTemplate transaction) {
        this(transaction, null, null);
    }

    public UniteDeTravailSpring(TransactionTemplate transaction, JdbcTemplate jdbc, Duration delaiDeVerrou) {
        this.transaction = transaction;
        this.jdbc = jdbc;
        this.delaiDeVerrou = delaiDeVerrou;
    }

    @Override
    public <T> T executer(Supplier<T> action) {
        return transaction.execute(statut -> {
            poserLeDelaiDeVerrou();
            return action.get();
        });
    }

    private void poserLeDelaiDeVerrou() {
        if (jdbc == null || delaiDeVerrou == null || !estPostgreSql()) {
            return;
        }
        jdbc.execute("SET LOCAL lock_timeout = " + delaiDeVerrou.toMillis());
    }

    private boolean estPostgreSql() {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
    }
}
