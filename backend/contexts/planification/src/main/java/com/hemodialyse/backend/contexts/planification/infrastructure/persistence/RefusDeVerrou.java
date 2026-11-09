package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.util.function.Supplier;

/**
 * Un verrou de ligne qu'on ne peut pas prendre (délai dépassé, interblocage) ou une clé créée entre-temps par une autre écriture
 * est une écriture concurrente : 409 {@code PLANIFICATION_ECRITURE_CONCURRENTE}.
 */
public final class RefusDeVerrou {

    private RefusDeVerrou() {
    }

    public static <T> T verrouillant(Supplier<T> action) {
        try {
            return action.get();
        } catch (PessimisticLockingFailureException | QueryTimeoutException | DuplicateKeyException e) {
            throw new EcritureConcurrente();
        } catch (UncategorizedSQLException e) {
            // PostgreSQL 55P03 (lock_timeout) arrive en exception SQL non catégorisée ; tout autre échec est relancé.
            if (!"55P03".equals(e.getSQLException().getSQLState())) {
                throw e;
            }
            throw new EcritureConcurrente();
        }
    }
}
