package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacycouverture;

import com.hemodialyse.backend.contexts.absencespatients.application.ForfaitsDesPatients;
import com.hemodialyse.backend.contexts.absencespatients.domain.ForfaitApplicable;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.ForfaitId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL vers les tables {@code prise_en_charge} et {@code forfait} du contexte Patient et couverture (SQL direct, ADR 0024).
 * Règle du legacy reproduite à l'identique (KB-ABS-16, décision du 22/10) : forfait effectif d'abord, sinon demandé, de la
 * prise en charge dont la période effective ou demandée couvre la date (celle portant un forfait effectif, puis la plus
 * récemment créée) ; à défaut, la plus récente portant un forfait, sans condition de date.
 */
public class ForfaitsDesPatientsJdbc implements ForfaitsDesPatients {

    private static final String SELECT_FORFAIT = "SELECT f.id, f.libelle, f.prix FROM prise_en_charge p "
            + "JOIN forfait f ON f.id = COALESCE(p.forfait_effectif_id, p.forfait_demande_id) AND f.center_id = p.center_id "
            + "WHERE p.center_id = ? AND p.patient_id = ? ";
    private static final String ORDRE = "ORDER BY CASE WHEN p.forfait_effectif_id IS NOT NULL THEN 0 ELSE 1 END, "
            + "p.created_at DESC LIMIT 1";

    private final JdbcTemplate jdbc;

    public ForfaitsDesPatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static ForfaitApplicable forfait(UUID id, String libelle, BigDecimal prix) {
        return new ForfaitApplicable(new ForfaitId(id), libelle, prix);
    }

    @Override
    public Optional<ForfaitApplicable> applicable(CentreId centre, PatientId patient, LocalDate date) {
        Date jour = Date.valueOf(date);
        Optional<ForfaitApplicable> couvrant = jdbc.query(SELECT_FORFAIT
                        + "AND ((p.date_debut_effectif IS NOT NULL AND ? >= p.date_debut_effectif "
                        + "AND (? <= p.date_fin_effectif OR p.date_fin_effectif IS NULL)) "
                        + "OR (p.date_debut_demande IS NOT NULL AND ? >= p.date_debut_demande "
                        + "AND (? <= p.date_fin_demande OR p.date_fin_demande IS NULL))) " + ORDRE,
                (rs, i) -> forfait(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3)),
                centre.valeur(), patient.valeur(), jour, jour, jour, jour).stream().findFirst();
        if (couvrant.isPresent()) return couvrant;
        return jdbc.query(SELECT_FORFAIT + "AND COALESCE(p.forfait_effectif_id, p.forfait_demande_id) IS NOT NULL " + ORDRE,
                (rs, i) -> forfait(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3)),
                centre.valeur(), patient.valeur()).stream().findFirst();
    }
}
