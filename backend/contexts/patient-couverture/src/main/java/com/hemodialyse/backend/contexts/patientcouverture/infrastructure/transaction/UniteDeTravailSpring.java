package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.transaction;

import com.hemodialyse.backend.contexts.patientcouverture.application.UniteDeTravail;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Adapter du port {@link UniteDeTravail} : une transaction Spring par exécution (rollback sur exception), rejointe si une
 * transaction est déjà ouverte. Sur PostgreSQL, {@code SET LOCAL lock_timeout} est posé À L'ENTRÉE de l'unité, avant le premier
 * verrou (celui du centre, ordre global centre, fiches, app_user) : au-delà du délai, la base refuse (SQLSTATE 55P03) et
 * l'écriture reçoit 409 {@code PATIENT_ECRITURE_CONCURRENTE}. H2 a son propre délai de verrou.
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
        try {
            return transaction.execute(statut -> {
                if (!delaiDejaPose()) {
                    poserLeDelaiDeVerrou();
                }
                return action.get();
            });
        } catch (org.springframework.dao.PessimisticLockingFailureException e) {
            throw new com.hemodialyse.backend.contexts.patientcouverture.domain.EcritureConcurrente();
        }
    }

    private boolean delaiDejaPose() {
        return TransactionSynchronizationManager.hasResource(this);
    }

    private void poserLeDelaiDeVerrou() {
        if (jdbc == null || delaiDeVerrou == null || !estPostgreSql()) {
            return;
        }
        jdbc.execute("SET LOCAL lock_timeout = " + delaiDeVerrou.toMillis());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.bindResource(this, Boolean.TRUE);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(UniteDeTravailSpring.this);
                }
            });
        }
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
