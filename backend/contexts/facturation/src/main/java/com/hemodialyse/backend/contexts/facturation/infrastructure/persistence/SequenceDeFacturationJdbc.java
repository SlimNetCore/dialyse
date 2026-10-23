package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Séquence de numérotation des factures : table {@code facture_sequence} (une ligne par centre et par année).
 * <p>
 * La réservation est atomique et ne repose sur aucun verrou propre à un SGBD : {@code UPDATE ... SET seq_value = seq_value + 1}
 * prend le verrou de la ligne, qu'il garde jusqu'à la fin de la transaction ; une émission concurrente du même centre attend donc
 * le commit (ou le rollback) de la première, puis incrémente la valeur à jour. La ligne d'une année nouvelle est créée à 0 puis
 * incrémentée ; sur PostgreSQL, {@code ON CONFLICT DO NOTHING} évite l'erreur si une transaction concurrente la crée au même
 * instant (H2, qui ne l'accepte pas, sert aux tests et n'a pas de concurrence réelle à ce point).
 */
public class SequenceDeFacturationJdbc implements SequenceDeFacturation {

    private static final String CREER_SANS_CONFLIT = "INSERT INTO facture_sequence (center_id, seq_year, seq_value) VALUES (?, ?, 0) "
            + "ON CONFLICT (center_id, seq_year) DO NOTHING";
    private static final String CREER_SI_ABSENTE = "INSERT INTO facture_sequence (center_id, seq_year, seq_value) "
            + "SELECT ?, ?, 0 WHERE NOT EXISTS (SELECT 1 FROM facture_sequence WHERE center_id = ? AND seq_year = ?)";

    private final JdbcTemplate jdbc;
    /**
     * Produit de la base, lu une seule fois (revue F7, constat 1) : {@code null} tant qu'il n'a pas été lu.
     */
    private volatile Boolean postgreSql;

    public SequenceDeFacturationJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int courante(CentreId centre, int annee) {
        return jdbc.queryForList("SELECT seq_value FROM facture_sequence WHERE center_id = ? AND seq_year = ?", Integer.class,
                centre.valeur(), annee).stream().findFirst().orElse(0);
    }

    @Override
    public int reserverLaSuivante(CentreId centre, int annee) {
        if (incrementer(centre, annee) == 0) {
            if (estPostgreSql()) {
                jdbc.update(CREER_SANS_CONFLIT, centre.valeur(), annee);
            } else {
                jdbc.update(CREER_SI_ABSENTE, centre.valeur(), annee, centre.valeur(), annee);
            }
            incrementer(centre, annee);
        }
        return courante(centre, annee);
    }

    private int incrementer(CentreId centre, int annee) {
        return jdbc.update("UPDATE facture_sequence SET seq_value = seq_value + 1 WHERE center_id = ? AND seq_year = ?",
                centre.valeur(), annee);
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
