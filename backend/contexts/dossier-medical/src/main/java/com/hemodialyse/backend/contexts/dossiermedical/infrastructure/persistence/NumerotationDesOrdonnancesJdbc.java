package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.application.NumerotationDesOrdonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Séquence {@code ORD-nnnnn} par centre, sur la ligne {@code app_settings (center_id, cle = 'SEQ_ORD')} (même ligne que le
 * legacy). La ligne est créée à la première signature (insertion conditionnelle) puis verrouillée ({@code FOR UPDATE}) jusqu'à
 * la fin de la transaction de la signature : un refus de la signature, qui annule la transaction, ne consomme aucun numéro.
 */
public class NumerotationDesOrdonnancesJdbc implements NumerotationDesOrdonnances {

    private static final String CLE = "SEQ_ORD";
    private static final String PREFIXE = "ORD-";

    private final JdbcTemplate jdbc;

    public NumerotationDesOrdonnancesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String suivant(CentreId centre) {
        try {
            jdbc.update("INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur) SELECT ?, ?, ?, 0 WHERE NOT EXISTS "
                    + "(SELECT 1 FROM app_settings WHERE center_id = ? AND cle = ?)", centre.valeur(), CLE, PREFIXE, centre.valeur(), CLE);
            Long courant = jdbc.queryForObject("SELECT dernier_compteur FROM app_settings WHERE center_id = ? AND cle = ? FOR UPDATE",
                    Long.class, centre.valeur(), CLE);
            long suivant = (courant == null ? 0L : courant) + 1L;
            jdbc.update("UPDATE app_settings SET dernier_compteur = ? WHERE center_id = ? AND cle = ?", suivant, centre.valeur(), CLE);
            return PREFIXE + String.format("%05d", suivant);
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            if (e instanceof UncategorizedSQLException technique && !"55P03".equals(technique.getSQLException().getSQLState())) {
                throw e;
            }
            throw new EcritureConcurrente();
        }
    }
}
