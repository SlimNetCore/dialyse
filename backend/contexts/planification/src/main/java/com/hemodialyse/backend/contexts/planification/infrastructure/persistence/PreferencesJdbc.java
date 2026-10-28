package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.PreferencesRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Préférences de planification des patients (table {@code planning_preference_patient}, clé centre + patient).
 */
public class PreferencesJdbc implements PreferencesRepository {

    private final JdbcTemplate jdbc;

    public PreferencesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static PreferenceDePlanification preference(ResultSet rs, PatientId patient) throws SQLException {
        UUID creneau = rs.getObject("creneau_prefere_id", UUID.class);
        Object seances = rs.getObject("seances_par_semaine");
        return new PreferenceDePlanification(patient, creneau == null ? null : new CreneauId(creneau),
                seances == null ? null : ((Number) seances).intValue(), rs.getBoolean("jours_a_choisir"));
    }

    @Override
    public Map<PatientId, PreferenceDePlanification> duCentre(CentreId centre) {
        Map<PatientId, PreferenceDePlanification> preferences = new HashMap<>();
        jdbc.query("SELECT patient_id, creneau_prefere_id, seances_par_semaine, jours_a_choisir "
                + "FROM planning_preference_patient WHERE center_id = ?", rs -> {
            PatientId patient = new PatientId(rs.getObject("patient_id", UUID.class));
            preferences.put(patient, preference(rs, patient));
        }, centre.valeur());
        return preferences;
    }

    @Override
    public void enregistrer(CentreId centre, PreferenceDePlanification p) {
        UUID creneau = p.creneauPrefere() == null ? null : p.creneauPrefere().valeur();
        RefusDeVerrou.verrouillant(() -> {
            int modifiees = jdbc.update("UPDATE planning_preference_patient SET creneau_prefere_id = ?, "
                            + "seances_par_semaine = ?, jours_a_choisir = ? WHERE center_id = ? AND patient_id = ?",
                    creneau, p.seancesParSemaine(), p.joursAChoisir(), centre.valeur(), p.patient().valeur());
            if (modifiees == 0) {
                jdbc.update("INSERT INTO planning_preference_patient (center_id, patient_id, creneau_prefere_id, "
                                + "seances_par_semaine, jours_a_choisir) VALUES (?, ?, ?, ?, ?)",
                        centre.valeur(), p.patient().valeur(), creneau, p.seancesParSemaine(), p.joursAChoisir());
            }
            return null;
        });
    }
}
