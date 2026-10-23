package com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence;

import com.hemodialyse.backend.contexts.absencespatients.domain.EcritureConcurrente;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.util.function.Supplier;

/**
 * Un verrou de ligne qu'on ne peut pas prendre (délai dépassé, interblocage détecté) est une écriture concurrente : 409
 * {@code ABSENCE_ECRITURE_CONCURRENTE}. Partagé par les dépôts des absences et des mois d'absences.
 */
final class RefusDeVerrou {

    private RefusDeVerrou() {
    }

    static <T> T verrouillant(Supplier<T> lecture) {
        try {
            return lecture.get();
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            siVerrouImpossibleSinonRelancer(e);
            throw new EcritureConcurrente();
        }
    }

    /**
     * Spring ne classe pas toujours le refus de verrou : PostgreSQL 55P03 (lock_timeout) arrive en exception SQL non catégorisée.
     * Tout autre échec est relancé tel quel.
     */
    static void siVerrouImpossibleSinonRelancer(RuntimeException e) {
        if (e instanceof UncategorizedSQLException technique && !"55P03".equals(technique.getSQLException().getSQLState())) {
            throw e;
        }
    }
}
