package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.FacturesEmises;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fournit à la Comptabilité les factures émises (lecture seule, aucune migration). Les factures de reprise des soldes
 * d'ouverture ({@code REPRISE-…}) sont exclues : la reprise est comptabilisée à part (KB-FAC-67, décision du 2026-10-21).
 * Un total absent vaut zéro.
 */
public class FacturesEmisesJdbc implements FacturesEmises {

    private static final String COLONNES = "SELECT f.id, f.numero_facture, f.centre_payeur_id_snapshot, f.total_ht, "
            + "f.total_tva, f.total_ttc, f.date_facturation, f.period_end, COALESCE(f.patient_full_name, 'Facture') AS libelle "
            + "FROM factures f WHERE f.center_id = ? AND f.numero_facture NOT LIKE ? ";
    private static final String ORDRE = " ORDER BY f.date_facturation, f.numero_facture";
    private static final String DE_REPRISE = RepriseDesSoldes.PREFIXE_DES_NUMEROS + "%";

    private static final RowMapper<FactureEmise> FACTURE = (rs, row) -> new FactureEmise(
            rs.getObject("id", UUID.class), rs.getString("numero_facture"),
            rs.getObject("centre_payeur_id_snapshot", UUID.class), ouZero(rs.getBigDecimal("total_ht")),
            ouZero(rs.getBigDecimal("total_tva")), ouZero(rs.getBigDecimal("total_ttc")),
            rs.getObject("date_facturation", LocalDate.class), rs.getObject("period_end", LocalDate.class),
            rs.getString("libelle"));

    private final JdbcTemplate jdbc;

    public FacturesEmisesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static BigDecimal ouZero(BigDecimal montant) {
        return montant != null ? montant : BigDecimal.ZERO;
    }

    @Override
    public List<FactureEmise> dontLaPeriodeEstIncluseDans(UUID centre, LocalDate debut, LocalDate fin) {
        return jdbc.query(COLONNES + "AND f.period_start >= ? AND f.period_end <= ?" + ORDRE, FACTURE, centre, DE_REPRISE,
                debut, fin);
    }

    @Override
    public List<FactureEmise> emisesEntre(UUID centre, LocalDate du, LocalDate au) {
        return jdbc.query(COLONNES + "AND f.date_facturation BETWEEN ? AND ?" + ORDRE, FACTURE, centre, DE_REPRISE, du, au);
    }
}
