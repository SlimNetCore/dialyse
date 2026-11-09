package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.application.ForfaitsDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Forfait courant d'une séance (SE7f, ADR 0100) : requêtes reprises MOT POUR MOT de {@code ForfaitsFournisLegacy} (plateforme, retiré). Le
 * forfait imposé sur la séance (colonnes {@code forfait_override_*}, SE5) prime ; sinon celui de la prise en charge qui couvre le jour
 * (effective d'abord, sinon demandée, la plus récente d'abord) ; sinon la plus récente prise en charge qui porte un forfait. Lecture de
 * {@code prise_en_charge} et {@code forfait} (Patient et couverture, référentiel) : transitoire, inscrite dans {@code TableDuPatientCouvertureTest}.
 */
public class ForfaitsDeSeanceJdbc implements ForfaitsDeSeance {

    private static final String SELECTION = """
            SELECT f.id AS forfait_id, f.code AS forfait_code, f.libelle AS forfait_nom, f.prix AS forfait_prix
            FROM prise_en_charge p
            INNER JOIN forfait f ON f.id = COALESCE(p.forfait_effectif_id, p.forfait_demande_id) AND f.center_id = p.center_id
            WHERE p.center_id = ? AND p.patient_id = ?
            """;
    private static final String ORDRE = """
            ORDER BY CASE WHEN p.forfait_effectif_id IS NOT NULL THEN 0 ELSE 1 END, p.created_at DESC
            """;

    private static final RowMapper<ForfaitCourant> DE_LA_PRISE_EN_CHARGE = (rs, i) -> new ForfaitCourant(rs.getObject("forfait_id", UUID.class),
            rs.getString("forfait_code"), rs.getString("forfait_nom"), rs.getBigDecimal("forfait_prix"), 0, null, null, false);

    private final JdbcTemplate jdbc;

    public ForfaitsDeSeanceJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ForfaitCourant> courant(CentreId centre, SeanceId seance, PatientId patient, LocalDate jour) {
        Optional<ForfaitCourant> impose = jdbc.query("""
                SELECT forfait_override_id, forfait_override_code, forfait_override_nom, forfait_override_prix,
                       forfait_override_updated_at, forfait_override_updated_by
                FROM seances WHERE id = ? AND center_id = ? AND forfait_override_id IS NOT NULL
                """, rs -> rs.next() ? Optional.of(new ForfaitCourant(rs.getObject("forfait_override_id", UUID.class),
                rs.getString("forfait_override_code"), rs.getString("forfait_override_nom"), rs.getBigDecimal("forfait_override_prix"),
                null, rs.getObject("forfait_override_updated_at", OffsetDateTime.class), rs.getString("forfait_override_updated_by"),
                true)) : Optional.<ForfaitCourant>empty(), seance.valeur(), centre.valeur());
        if (impose.isPresent()) return impose;

        Date date = Date.valueOf(jour);
        Optional<ForfaitCourant> couvrant = jdbc.query(SELECTION + """
                  AND ((p.date_debut_effectif IS NOT NULL AND ? >= p.date_debut_effectif
                        AND (? <= p.date_fin_effectif OR p.date_fin_effectif IS NULL))
                    OR (p.date_debut_demande IS NOT NULL AND ? >= p.date_debut_demande
                        AND (? <= p.date_fin_demande OR p.date_fin_demande IS NULL)))
                """ + ORDRE, DE_LA_PRISE_EN_CHARGE, centre.valeur(), patient.valeur(), date, date, date, date).stream().findFirst();
        if (couvrant.isPresent()) return couvrant;

        return jdbc.query(SELECTION + " AND COALESCE(p.forfait_effectif_id, p.forfait_demande_id) IS NOT NULL\n" + ORDRE,
                DE_LA_PRISE_EN_CHARGE, centre.valeur(), patient.valeur()).stream().findFirst();
    }
}
