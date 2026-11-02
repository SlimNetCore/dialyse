package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.organisation.domain.VerrouDeLaSociete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verrou de la société : {@code SELECT … FOR UPDATE} de sa ligne, à prendre dans une transaction ({@code UniteDeTravail}). Le
 * délai d'attente est posé par l'unité de travail ({@code SET LOCAL lock_timeout} sous PostgreSQL, 55P03 à l'expiration) : au-delà,
 * l'écriture reçoit 409. Ne crée rien : sans ligne de société, ne verrouille rien.
 */
public class VerrouDeLaSocieteJdbc implements VerrouDeLaSociete {

    private final JdbcTemplate jdbc;

    public VerrouDeLaSocieteJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void prendre(SocieteId societeId) {
        try {
            jdbc.queryForList("SELECT id FROM societes WHERE id = ? FOR UPDATE", societeId.valeur());
        } catch (PessimisticLockingFailureException | QueryTimeoutException | DuplicateKeyException e) {
            throw new EcritureConcurrente();
        } catch (UncategorizedSQLException e) {
            if (!"55P03".equals(e.getSQLException().getSQLState())) {
                throw e;
            }
            throw new EcritureConcurrente();
        }
    }
}
