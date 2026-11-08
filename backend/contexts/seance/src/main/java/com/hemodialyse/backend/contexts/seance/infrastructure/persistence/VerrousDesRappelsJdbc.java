package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.application.VerrousDesRappels;
import com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

/**
 * Verrou du rappel : {@code pg_advisory_xact_lock} sur (centre, jour du centre) sur PostgreSQL, gardé jusqu'à la fin de la transaction
 * (le rappel d'un centre attend celui d'une autre instance du même jour, puis voit l'alerte déjà journalisée) ; sans effet sur H2. Un
 * verrou non obtenu dans le délai (SQLSTATE 55P03) devient {@link EcritureConcurrente}. Clé littérale préfixée
 * {@code rappel-regularisation|} : tout autre verrou consultatif du dépôt doit avoir son propre préfixe.
 */
public class VerrousDesRappelsJdbc implements VerrousDesRappels {

    static final String PREFIXE = "rappel-regularisation|";

    private final JdbcTemplate jdbc;
    private volatile Boolean postgreSql;

    public VerrousDesRappelsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Clé du verrou consultatif (exposée au paquet pour les tests de contrat). */
    static String cle(CentreId centre, LocalDate jour) {
        return PREFIXE + centre.valeur() + "|" + jour;
    }

    @Override
    public void verrouiller(CentreId centre, LocalDate jour) {
        if (!estPostgreSql()) return;
        try {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {
            }, cle(centre, jour));
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            if (e instanceof UncategorizedSQLException technique && !"55P03".equals(technique.getSQLException().getSQLState())) throw e;
            throw new EcritureConcurrente("Le rappel de ce centre pour ce jour est en cours sur une autre instance");
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
