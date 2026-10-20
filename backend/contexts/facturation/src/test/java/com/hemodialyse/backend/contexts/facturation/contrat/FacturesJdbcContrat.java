package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.domain.facture.Factures;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.FacturesJdbc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

/**
 * Le contrat des factures exécuté contre l'adaptateur JDBC : relecture en SQL des colonnes de {@code factures} et
 * {@code facture_lignes}. Les variantes H2 et PostgreSQL 16 ne diffèrent que par leur base.
 */
public abstract class FacturesJdbcContrat extends FacturesContrat {

    private static java.time.LocalDate jour(Date date) {
        return date.toLocalDate();
    }

    protected abstract JdbcTemplate jdbc();

    protected void viderLesLignesDeTest() {
        for (String table : new String[]{"facture_lignes", "factures"}) {
            jdbc().update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
        }
    }

    @Override
    protected Factures factures() {
        return new FacturesJdbc(jdbc());
    }

    @Override
    protected Enregistree relire(UUID id) {
        List<LigneEnregistree> lignes = jdbc().query("SELECT forfait_id, forfait_label, unit_price_ht, seance_count, line_ht "
                        + "FROM facture_lignes WHERE facture_id = ?",
                (rs, i) -> new LigneEnregistree(rs.getObject("forfait_id", UUID.class), rs.getString("forfait_label"),
                        rs.getBigDecimal("unit_price_ht"), rs.getInt("seance_count"), rs.getBigDecimal("line_ht")), id);
        return jdbc().queryForObject("SELECT numero_facture, patient_id, patient_code, patient_full_name, patient_status_snapshot, "
                        + "numero_immatriculation_snapshot, centre_payeur_id_snapshot, agence_id_snapshot, period_start, period_end, "
                        + "date_facturation, tva_rate, total_ht, total_tva, total_ttc, emise_par FROM factures WHERE id = ?",
                (rs, i) -> new Enregistree(rs.getString("numero_facture"), rs.getObject("patient_id", UUID.class),
                        rs.getString("patient_code"), rs.getString("patient_full_name"), rs.getString("patient_status_snapshot"),
                        rs.getString("numero_immatriculation_snapshot"), rs.getObject("centre_payeur_id_snapshot", UUID.class),
                        rs.getObject("agence_id_snapshot", UUID.class), jour(rs.getDate("period_start")),
                        jour(rs.getDate("period_end")), jour(rs.getDate("date_facturation")), rs.getBigDecimal("tva_rate"),
                        rs.getBigDecimal("total_ht"), rs.getBigDecimal("total_tva"), rs.getBigDecimal("total_ttc"),
                        rs.getObject("emise_par", UUID.class), lignes), id);
    }
}
