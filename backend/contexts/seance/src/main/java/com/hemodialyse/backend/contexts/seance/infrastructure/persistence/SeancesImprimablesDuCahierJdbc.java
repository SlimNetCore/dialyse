package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.application.SeancesImprimablesDuCahier;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PeriodeDuCahier;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.util.List;
import java.util.UUID;

/**
 * Séances imprimables du cahier de dialyse, avec leur forfait imposé et leurs deux volets (DOC5a, ADR 0193) : les trois tables sont celles de
 * la Séance ({@code seances}, {@code volet_paramedical}, {@code volet_medical}). Reprend MOT POUR MOT la partie « séance » de l'ancienne
 * requête du gabarit : statuts à la casse exacte, bornes incluses, volets cherchés sous le centre de la séance, volet médical joint
 * seulement s'il est visible, ordre date décroissante puis identifiant.
 */
public class SeancesImprimablesDuCahierJdbc implements SeancesImprimablesDuCahier {

    private final JdbcTemplate jdbc;

    public SeancesImprimablesDuCahierJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SeanceDuCahier> lister(CentreId centre, PatientId patient, PeriodeDuCahier periode, boolean voletMedicalVisible) {
        return jdbc.query("""
                        SELECT s.id AS seance_id, s.date_seance AS date_seance, s.statut AS statut,
                               s.forfait_override_id AS impose_id, s.forfait_override_code AS impose_code,
                               s.forfait_override_nom AS impose_nom, s.forfait_override_prix AS impose_prix,
                               v.id AS para_id, v.poids_avant_kg, v.poids_apres_kg, v.ta_avant, v.ta_apres, v.duree_minutes,
                               v.debit_sang_ml_min, v.ultrafiltration_ml, v.type_dialysat, v.anticoagulant, v.incidents,
                               vm.id AS med_id, vm.prescription, vm.tolerance_seance, vm.examen_clinique, vm.resultats_biologiques,
                               vm.ajustements_therapeutiques, vm.conclusion_medicale
                        FROM seances s
                        LEFT JOIN volet_paramedical v ON v.seance_id = s.id AND v.center_id = s.center_id
                        LEFT JOIN volet_medical vm ON vm.seance_id = s.id AND vm.center_id = s.center_id AND CAST(? AS BOOLEAN) = TRUE
                        WHERE s.center_id = ? AND s.patient_id = ? AND s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')
                          AND s.date_seance BETWEEN ? AND ?
                        ORDER BY s.date_seance DESC, s.id
                        """,
                (rs, n) -> seance(rs), voletMedicalVisible, centre.valeur(), patient.valeur(), Date.valueOf(periode.du()),
                Date.valueOf(periode.au()));
    }

    private static SeanceDuCahier seance(ResultSet rs) throws SQLException {
        ForfaitImpose impose = rs.getObject("impose_id", UUID.class) == null ? null
                : new ForfaitImpose(rs.getObject("impose_id", UUID.class), rs.getString("impose_code"), rs.getString("impose_nom"),
                rs.getBigDecimal("impose_prix"));
        VoletParamedicalBrut paramedical = rs.getObject("para_id", UUID.class) == null ? null
                : new VoletParamedicalBrut(rs.getBigDecimal("poids_avant_kg"), rs.getBigDecimal("poids_apres_kg"), rs.getString("ta_avant"),
                rs.getString("ta_apres"), rs.getObject("duree_minutes", Integer.class), rs.getObject("debit_sang_ml_min", Integer.class),
                rs.getBigDecimal("ultrafiltration_ml"), rs.getString("type_dialysat"), rs.getString("anticoagulant"),
                rs.getString("incidents"));
        VoletMedicalBrut medical = rs.getObject("med_id", UUID.class) == null ? null
                : new VoletMedicalBrut(rs.getString("prescription"), rs.getString("tolerance_seance"), rs.getString("examen_clinique"),
                rs.getString("resultats_biologiques"), rs.getString("ajustements_therapeutiques"), rs.getString("conclusion_medicale"));
        return new SeanceDuCahier(new SeanceId(rs.getObject("seance_id", UUID.class)), rs.getDate("date_seance").toLocalDate(),
                rs.getString("statut"), impose, paramedical, medical);
    }
}
