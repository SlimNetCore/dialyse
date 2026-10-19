package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.SequenceDesNumerosDePieces;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Verrou partagé par centre/journal/année pour sérialiser numéros et extournes, sans migration de schéma.
 */
public final class SequenceDesNumerosDePiecesJdbc implements SequenceDesNumerosDePieces {

    private final JdbcTemplate jdbc;

    public SequenceDesNumerosDePiecesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> verrouillerEtLireLesNumeros(CentreId centre, CodeJournal journal, int annee) {
        String cle = "SEQ_ECRITURE_" + journal.valeur() + "_" + annee;
        String prefixe = journal.valeur() + "-" + annee + "-";
        verrouiller(centre, cle, prefixe);
        return new ArrayList<>(jdbc.query("SELECT numero_piece FROM ecritures_comptables "
                        + "WHERE center_id = ? AND journal_code = ? AND numero_piece LIKE ?",
                (rs, row) -> rs.getString("numero_piece"), centre.valeur(), journal.valeur(), prefixe + "%"));
    }

    @Override
    public void verrouillerSource(CentreId centre, UUID source) {
        verrouiller(centre, "SEQ_SOURCE_" + source, "SOURCE-");
    }

    private void verrouiller(CentreId centre, String cle, String prefixe) {
        if (estPostgreSql()) {
            String cleGlobale = "comptabilite:" + centre.valeur() + ":" + cle;
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                    statement.setString(1, cleGlobale);
                    statement.execute();
                }
                return null;
            });
        } else {
            try {
                jdbc.update("INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur) "
                                + "SELECT ?, ?, ?, 0 WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE center_id = ? AND cle = ?)",
                        centre.valeur(), cle, prefixe, centre.valeur(), cle);
            } catch (DuplicateKeyException concurrente) {
                // L’autre transaction a créé la même ligne de verrou en premier.
            }
        }
        jdbc.queryForObject("SELECT dernier_compteur FROM app_settings WHERE center_id = ? AND cle = ? FOR UPDATE",
                Long.class, centre.valeur(), cle);
    }

    private boolean estPostgreSql() {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
    }
}
