package com.hemodialyse.backend.infrastructure.persistence.adapter.optimisation;

import com.hemodialyse.backend.domain.patient.service.FinOccupation;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.PreferencePatient;
import com.hemodialyse.backend.domain.planning.optimisation.port.PreferencePatientPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Préférences de planification des patients (table {@code planning_preference_patient}). La liste couvre les patients
 * du centre qui ne sont pas sortis (transfert, décès, greffe, guérison). Toutes les requêtes sont bornées au centre.
 */
@Component
public class PreferencePatientJdbcAdapter implements PreferencePatientPort {

    private static final String JOURS = "p.jour_dimanche, p.jour_lundi, p.jour_mardi, p.jour_mercredi, p.jour_jeudi, "
            + "p.jour_vendredi, p.jour_samedi";
    private static final String ACTIFS = "p.center_id = ? AND (p.etat_patient IS NULL OR p.etat_patient NOT IN ("
            + String.join(",", FinOccupation.ETATS_DE_SORTIE.stream().sorted().map(e -> "'" + e + "'").toList()) + "))";

    private final JdbcTemplate jdbc;

    public PreferencePatientJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static PreferencePatient preference(ResultSet rs, UUID patientId) throws SQLException {
        Object seances = rs.getObject("seances_par_semaine");
        return new PreferencePatient(patientId, rs.getObject("creneau_prefere_id", UUID.class),
                seances == null ? null : ((Number) seances).intValue(), rs.getBoolean("jours_a_choisir"));
    }

    @Override
    public Map<UUID, PreferencePatient> preferences(UUID centerId) {
        Map<UUID, PreferencePatient> preferences = new HashMap<>();
        jdbc.query("SELECT patient_id, creneau_prefere_id, seances_par_semaine, jours_a_choisir "
                + "FROM planning_preference_patient WHERE center_id = ?", rs -> {
            UUID patientId = rs.getObject("patient_id", UUID.class);
            preferences.put(patientId, preference(rs, patientId));
        }, centerId);
        return preferences;
    }

    @Override
    public PagedResult<Ligne> lister(UUID centerId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM patients p WHERE " + ACTIFS, Long.class, centerId);
        List<Ligne> items = jdbc.query("SELECT p.id, p.nom, p.prenom, p.position_id, " + JOURS
                        + ", pp.creneau_prefere_id, pp.seances_par_semaine, COALESCE(pp.jours_a_choisir, FALSE) AS jours_a_choisir "
                        + "FROM patients p LEFT JOIN planning_preference_patient pp "
                        + "ON pp.center_id = p.center_id AND pp.patient_id = p.id WHERE " + ACTIFS
                        + " ORDER BY p.nom, p.prenom, p.id LIMIT ? OFFSET ?",
                (rs, i) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    Set<JourSemaine> jours = EnumSet.noneOf(JourSemaine.class);
                    for (JourSemaine jour : JourSemaine.values()) {
                        if (rs.getBoolean("jour_" + jour.name().toLowerCase(Locale.ROOT))) jours.add(jour);
                    }
                    String nom = ((rs.getString("prenom") == null ? "" : rs.getString("prenom")) + " "
                            + (rs.getString("nom") == null ? "" : rs.getString("nom"))).trim();
                    return new Ligne(id, nom, jours, rs.getObject("position_id", UUID.class), preference(rs, id));
                }, centerId, size, (long) page * size);
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public boolean enregistrer(UUID centerId, PreferencePatient p) {
        Long existe = jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE center_id = ? AND id = ?", Long.class,
                centerId, p.patientId());
        if (existe == null || existe == 0) return false;
        int maj = jdbc.update("UPDATE planning_preference_patient SET creneau_prefere_id = ?, seances_par_semaine = ?, "
                        + "jours_a_choisir = ? WHERE center_id = ? AND patient_id = ?",
                p.creneauPrefereId(), p.seancesParSemaine(), p.joursAChoisir(), centerId, p.patientId());
        if (maj == 0) {
            jdbc.update("INSERT INTO planning_preference_patient (center_id, patient_id, creneau_prefere_id, "
                            + "seances_par_semaine, jours_a_choisir) VALUES (?, ?, ?, ?, ?)",
                    centerId, p.patientId(), p.creneauPrefereId(), p.seancesParSemaine(), p.joursAChoisir());
        }
        return true;
    }
}
