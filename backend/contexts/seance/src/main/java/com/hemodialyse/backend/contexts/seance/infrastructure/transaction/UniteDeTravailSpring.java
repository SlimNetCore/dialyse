package com.hemodialyse.backend.contexts.seance.infrastructure.transaction;

import com.hemodialyse.backend.contexts.seance.application.UniteDeTravail;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Une transaction Spring par exécution (annulée si l'action échoue, rejointe si elle existe). Sur PostgreSQL, {@code SET LOCAL
 * lock_timeout} borne l'attente d'un verrou (SQLSTATE 55P03 : 409 au lieu d'une attente sans limite) ; le délai n'est posé que si cette
 * unité OUVRE la transaction. Sans {@code JdbcTemplate}, aucun délai (tests).
 */
public class UniteDeTravailSpring implements UniteDeTravail {

    private final TransactionTemplate transaction;
    private final JdbcTemplate jdbc;
    private final Duration delaiDeVerrou;
    private volatile Boolean postgreSql;

    public UniteDeTravailSpring(TransactionTemplate transaction, JdbcTemplate jdbc, Duration delaiDeVerrou) {
        this.transaction = transaction;
        this.jdbc = jdbc;
        this.delaiDeVerrou = delaiDeVerrou;
    }

    @Override
    public <T> T executer(Supplier<T> action) {
        return transaction.execute(statut -> {
            if (statut.isNewTransaction() && jdbc != null && delaiDeVerrou != null && estPostgreSql()) {
                jdbc.execute("SET LOCAL lock_timeout = " + delaiDeVerrou.toMillis());
            }
            return action.get();
        });
    }

    private boolean estPostgreSql() {
        Boolean lu = postgreSql;
        if (lu == null) {
            lu = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                    "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
            postgreSql = lu;
        }
        return lu;
    }
}
