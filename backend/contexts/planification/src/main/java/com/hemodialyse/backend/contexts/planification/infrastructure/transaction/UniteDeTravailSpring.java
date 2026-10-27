package com.hemodialyse.backend.contexts.planification.infrastructure.transaction;

import com.hemodialyse.backend.contexts.planification.application.UniteDeTravail;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Adapter du port {@link UniteDeTravail} : une transaction Spring par exécution (rollback sur exception), rejointe si une
 * transaction est déjà ouverte. Sur PostgreSQL, {@code SET LOCAL lock_timeout} borne l'attente d'un verrou de ligne : au-delà, la
 * base refuse (SQLSTATE 55P03) et l'écriture reçoit 409 {@code PLANIFICATION_ECRITURE_CONCURRENTE} ; H2 a son propre délai de
 * verrou. Le délai est posé au premier passage de la transaction par cette unité, même si une autre en a ouvert la transaction (ACL de
 * la fiche et de la sérologie, {@code @Transactional} du legacy) ; ADR 0043, revue P5.
 */
public class UniteDeTravailSpring implements UniteDeTravail {

    private final TransactionTemplate transaction;
    private final JdbcTemplate jdbc;
    private final Duration delaiDeVerrou;
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
        return transaction.execute(statut -> {
            // Revue P5, constat 1 : le délai est posé au PREMIER passage de la transaction par une unité de travail, QUELLE QUE SOIT son
            // origine (cette unité, ou l'ACL de la fiche / de la sérologie, ou le @Transactional du legacy) : sinon les verrous pris
            // dans une transaction rejointe attendraient sans limite sous PostgreSQL.
            if (!delaiDejaPose()) {
                poserLeDelaiDeVerrou();
            }
            return action.get();
        });
    }

    /** Vrai si cette unité a déjà posé le délai dans la transaction en cours (le marqueur disparaît à sa fin). */
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
