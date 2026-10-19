package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.FactureAComptabiliser;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.FacturesAComptabiliser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lecture de la table {@code factures} de la Facturation (aucune écriture, aucune migration). Un total absent vaut zéro.
 */
public final class FacturesAComptabiliserJdbc implements FacturesAComptabiliser {

    private static final String COLONNES = "SELECT f.id, f.numero_facture, f.centre_payeur_id_snapshot, f.total_ht, "
            + "f.total_tva, f.total_ttc, f.date_facturation, f.period_end, COALESCE(f.patient_full_name, 'Facture') AS libelle "
            + "FROM factures f WHERE f.center_id = ? ";
    private static final String ORDRE = " ORDER BY f.date_facturation, f.numero_facture";

    private static final RowMapper<FactureAComptabiliser> FACTURE = (rs, row) -> new FactureAComptabiliser(
            rs.getObject("id", UUID.class), rs.getString("numero_facture"),
            rs.getObject("centre_payeur_id_snapshot", UUID.class), ouZero(rs.getBigDecimal("total_ht")),
            ouZero(rs.getBigDecimal("total_tva")), ouZero(rs.getBigDecimal("total_ttc")),
            rs.getObject("date_facturation", LocalDate.class), rs.getObject("period_end", LocalDate.class),
            rs.getString("libelle"));

    private final JdbcTemplate jdbc;

    public FacturesAComptabiliserJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static BigDecimal ouZero(BigDecimal montant) {
        return montant != null ? montant : BigDecimal.ZERO;
    }

    @Override
    public List<FactureAComptabiliser> dontLaPeriodeEstIncluseDans(CentreId centre, LocalDate debut, LocalDate fin) {
        return jdbc.query(COLONNES + "AND f.period_start >= ? AND f.period_end <= ?" + ORDRE, FACTURE,
                centre.valeur(), debut, fin);
    }

    @Override
    public List<FactureAComptabiliser> factureesEntre(CentreId centre, LocalDate du, LocalDate au) {
        return jdbc.query(COLONNES + "AND f.date_facturation BETWEEN ? AND ?" + ORDRE, FACTURE, centre.valeur(), du, au);
    }

    @Override
    public List<FactureAComptabiliser> sansEcritureEntre(CentreId centre, LocalDate du, LocalDate au) {
        return jdbc.query(COLONNES + "AND f.date_facturation BETWEEN ? AND ? AND NOT EXISTS (SELECT 1 FROM "
                        + "ecritures_comptables e WHERE e.center_id = f.center_id AND e.source_id = f.id)" + ORDRE,
                FACTURE, centre.valeur(), du, au);
    }
}

