package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Constantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ConstantesDeSeance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.HistoriqueDesConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Pagination;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

/**
 * Historique des constantes sur les VRAIES tables du legacy ({@code seances}, {@code volet_paramedical}), en lecture seule (aucune
 * table ajoutée, aucun {@code center_id} ajouté). Un seul parcours par page : séances du patient dans le centre, volet joint par
 * {@code seance_id} ET {@code center_id} (le volet d'un autre centre est ignoré, comme le legacy). Ordre : date de séance
 * décroissante, création décroissante, puis identifiant décroissant (départage ajouté, le legacy laissait l'égalité complète au
 * SGBD).
 */
public class HistoriqueDesConstantesJdbc implements HistoriqueDesConstantes {

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

    public HistoriqueDesConstantesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageDeConstantes dePatient(CentreId centre, PatientId patient, Pagination pagination) {
        Long total = jdbc.queryForObject(TOTAL, Long.class, centre.valeur(), patient.valeur());
        List<ConstantesDeSeance> items = jdbc.query(PAGE, (rs, i) -> new ConstantesDeSeance(
                new SeanceId(rs.getObject("id", java.util.UUID.class)),
                rs.getObject("date_seance", LocalDate.class),
                new Constantes(rs.getBigDecimal("poids_avant_kg"), rs.getBigDecimal("poids_apres_kg"), rs.getString("ta_avant"),
                        rs.getString("ta_apres"), rs.getObject("debit_sang_ml_min", Integer.class),
                        rs.getBigDecimal("ultrafiltration_ml"), rs.getObject("duree_minutes", Integer.class))),
                centre.valeur(), patient.valeur(), pagination.taille(), pagination.rang());
        return new PageDeConstantes(items, total == null ? 0 : total, pagination.page(), pagination.taille());
    }
}
