package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyidentite;

import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.util.function.Supplier;

/**
 * Un verrou de ligne de compte qu'on ne peut pas prendre (délai dépassé, interblocage) est une écriture concurrente : 409
 * {@code PLANIFICATION_ECRITURE_CONCURRENTE}.
 */
final class RefusDeVerrouDuCompte {
    private RefusDeVerrouDuCompte() {
    }

    static <T> T verrouillant(Supplier<T> action) {
        try {
            return action.get();
        } catch (PessimisticLockingFailureException | QueryTimeoutException e) {
            throw new EcritureConcurrente();
        } catch (UncategorizedSQLException e) {
            if (e.getSQLException() == null || !"55P03".equals(e.getSQLException().getSQLState())) {
                throw e;
            }
            throw new EcritureConcurrente();
        }
    }
}
