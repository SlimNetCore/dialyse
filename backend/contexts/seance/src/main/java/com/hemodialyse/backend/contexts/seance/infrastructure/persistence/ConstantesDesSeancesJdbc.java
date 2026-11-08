package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ConstantesDesSeancesPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lecture publiée des constantes des séances pour le Dossier médical (ADR 0098, SE7d), sur {@code seances} et {@code volet_paramedical}
 * (tables de la Séance), lecture seule. Requêtes reprises MOT POUR MOT de {@code dossiermedical/.../HistoriqueDesConstantesJdbc} (D1 total,
 * D2 page) : volet joint par {@code seance_id} ET {@code center_id} ; ordre {@code date_seance DESC, created_at DESC, id DESC} sans
 * {@code NULLS FIRST/LAST} (KB-SEA-79, reproduit : la création nulle se classe selon le SGBD). Aucune jointure de la fiche du patient.
 */
public class ConstantesDesSeancesJdbc implements ConstantesDesSeancesPubliees {

    private static final String PAGE = """
            SELECT s.id, s.date_seance, v.poids_avant_kg, v.poids_apres_kg, v.ta_avant, v.ta_apres, v.debit_sang_ml_min,
                   v.ultrafiltration_ml, v.duree_minutes
            FROM seances s
            LEFT JOIN volet_paramedical v ON v.seance_id = s.id AND v.center_id = s.center_id
            WHERE s.center_id = ? AND s.patient_id = ?
            ORDER BY s.date_seance DESC, s.created_at DESC, s.id DESC
            LIMIT ? OFFSET ?
            """;
    private static final String TOTAL = "SELECT count(*) FROM seances WHERE center_id = ? AND patient_id = ?";

    private final JdbcTemplate jdbc;

    public ConstantesDesSeancesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nombreDeSeances(UUID centre, UUID patient) {
        Long total = jdbc.queryForObject(TOTAL, Long.class, centre, patient);
        return total == null ? 0 : total;
    }

    @Override
    public List<ConstantesDeSeancePubliees> page(UUID centre, UUID patient, int taille, long rang) {
        return jdbc.query(PAGE, (rs, i) -> new ConstantesDeSeancePubliees(rs.getObject("id", UUID.class),
                        rs.getObject("date_seance", LocalDate.class), rs.getBigDecimal("poids_avant_kg"), rs.getBigDecimal("poids_apres_kg"),
                        rs.getString("ta_avant"), rs.getString("ta_apres"), rs.getObject("debit_sang_ml_min", Integer.class),
                        rs.getBigDecimal("ultrafiltration_ml"), rs.getObject("duree_minutes", Integer.class)),
                centre, patient, taille, rang);
    }
}
