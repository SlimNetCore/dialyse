package com.hemodialyse.backend.contexts.facturation.infrastructure.transaction;

import com.hemodialyse.backend.contexts.facturation.application.EcritureConcurrenteDeFacturation;
import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Adapter du port {@link UniteDeTravail} : une transaction Spring par exécution (rollback sur exception), rejointe si une
 * transaction est déjà ouverte.
 * <p>
 * Politique de verrou (ADR 0043, comme les Absences) : seule l'unité de travail qui <b>ouvre</b> la transaction pose, sur PostgreSQL,
 * {@code SET LOCAL lock_timeout} ; une transaction rejointe garde le délai de son propriétaire. Un verrou non obtenu dans le délai
 * (SQLSTATE 55P03, ou délai propre de H2) ou un interblocage détecté devient {@link EcritureConcurrenteDeFacturation} (409
 * {@code FACTURATION_ECRITURE_CONCURRENTE}) au lieu d'une attente sans limite. Sans {@code JdbcTemplate}, aucun délai n'est posé.
 */
public class UniteDeTravailSpring implements UniteDeTravail {

    private final TransactionTemplate transaction;
    private final JdbcTemplate jdbc;
    private final Duration delaiDeVerrou;
    /**
     * Produit de la base, lu une seule fois : {@code null} tant qu'il n'a pas été lu.
     */
    private volatile Boolean postgreSql;

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
        try {
            return transaction.execute(statut -> {
                if (statut.isNewTransaction()) {
                    poserLeDelaiDeVerrou();
                }
                return action.get();
            });
        } catch (PessimisticLockingFailureException | QueryTimeoutException e) {
            throw new EcritureConcurrenteDeFacturation(e);
        } catch (UncategorizedSQLException e) {
            if (e.getSQLException() != null && "55P03".equals(e.getSQLException().getSQLState())) {
                throw new EcritureConcurrenteDeFacturation(e);
            }
            throw e;
        }
    }

    private void poserLeDelaiDeVerrou() {
        if (jdbc == null || delaiDeVerrou == null || !estPostgreSql()) {
            return;
        }
        jdbc.execute("SET LOCAL lock_timeout = " + delaiDeVerrou.toMillis());
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
