package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.comptabilite.port.ParametrageFiscalPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.RegleTVA;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation de ParametrageFiscalPort utilisant la table tva_types partagée.
 * Garantit que facturation et comptabilité utilisent la même source de vérité TVA.
 */
@Component
public class ParametrageFiscalJdbcAdapter implements ParametrageFiscalPort {

    private final JdbcTemplate jdbc;

    public ParametrageFiscalJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RegleTVA> findActiveAt(UUID centerId, String typePrestation, LocalDate date) {
        List<RegleTVA> rows = jdbc.query(
                """
                        SELECT type_prestation, taux, exonere, date_debut_validite, date_fin_validite, texte_reference
                        FROM tva_types
                        WHERE center_id = ?
                          AND type_prestation = ?
                          AND actif = TRUE
                          AND date_debut_validite <= ?
                          AND (date_fin_validite IS NULL OR date_fin_validite >= ?)
                        ORDER BY date_debut_validite DESC
                        LIMIT 1
                        """,
                this::mapRow,
                centerId, typePrestation, date, date
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<RegleTVA> findAll(UUID centerId) {
        return jdbc.query(
                """
                        SELECT type_prestation, taux, exonere, date_debut_validite, date_fin_validite, texte_reference
                        FROM tva_types
                        WHERE center_id = ?
                        ORDER BY date_debut_validite DESC
                        """,
                this::mapRow,
                centerId
        );
    }

    @Override
    public void save(UUID centerId, RegleTVA regle) {
        jdbc.update(
                """
                        INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere,
                                               date_debut_validite, date_fin_validite, texte_reference,
                                               actif, created_at, created_by)
                        VALUES (RANDOM_UUID(), ?, ?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, 'comptabilite')
                        """,
                centerId,
                regle.typePrestation() + " " + regle.tauxApplique() + "%",
                regle.tauxApplique(),
                regle.typePrestation(),
                regle.exonere(),
                regle.dateDebutValidite(),
                regle.dateFinValidite(),
                regle.texteReference()
        );
    }

    private RegleTVA mapRow(ResultSet rs, int rowNum) throws SQLException {
        Date finDate = rs.getDate("date_fin_validite");
        return new RegleTVA(
                rs.getString("type_prestation"),
                rs.getBigDecimal("taux"),
                rs.getBoolean("exonere"),
                rs.getDate("date_debut_validite").toLocalDate(),
                finDate != null ? finDate.toLocalDate() : null,
                rs.getString("texte_reference")
        );
    }
}

