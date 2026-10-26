package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Amorçage des tests du legacy : depuis la slice P1, le paramétrage est écrit par le contexte Planification (jamais par le
 * legacy). Les tests des autres slices (placement, présence, affectation) posent ici l'état dont ils ont besoin, directement
 * dans les tables, comme le faisait l'ancien adaptateur.
 */
public final class ParametrageDuPlanningPourLesTests {

    private ParametrageDuPlanningPourLesTests() {
    }

    public static void enregistrer(JdbcTemplate jdbc, UUID centre, PlanningParametres p) {
        String jours = p.joursOuverts().stream().sorted().map(Enum::name).collect(Collectors.joining(","));
        Timestamp maintenant = Timestamp.from(Instant.now());
        int modifiees = jdbc.update("UPDATE planning_parametres SET jours_ouverts = ?, patients_par_infirmier = ?, "
                + "patients_par_poste_serie = ?, updated_at = ? WHERE center_id = ?", jours, p.patientsParInfirmier(),
                p.patientsParPosteEtSerie(), maintenant, centre);
        if (modifiees == 0) {
            jdbc.update("INSERT INTO planning_parametres (center_id, jours_ouverts, patients_par_infirmier, "
                    + "patients_par_poste_serie, updated_at) VALUES (?, ?, ?, ?, ?)", centre, jours, p.patientsParInfirmier(),
                    p.patientsParPosteEtSerie(), maintenant);
        }
        jdbc.update("UPDATE salle SET isolement = 'NON' WHERE center_id = ?", centre);
        for (UUID salle : p.sallesIsolement()) {
            jdbc.update("UPDATE salle SET isolement = 'OUI' WHERE center_id = ? AND id = ?", centre, salle);
        }
    }
}
