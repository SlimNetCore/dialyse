package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Réglages de planification (table {@code planification_reglages}, une ligne par centre).
 */
public class ReglagesJdbc implements ReglagesRepository {

    private final JdbcTemplate jdbc;

    public ReglagesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ReglagesDePlanification lire(CentreId centre) {
        return jdbc.query("SELECT replanification_auto, heures_par_vacation, heures_hebdo_temps_plein, repos_hebdo_min "
                                + "FROM planification_reglages WHERE center_id = ?",
                        (rs, i) -> new ReglagesDePlanification(rs.getBoolean("replanification_auto"),
                                rs.getInt("heures_par_vacation"), rs.getInt("heures_hebdo_temps_plein"),
                                rs.getInt("repos_hebdo_min")), centre.valeur())
                .stream().findFirst().orElseGet(ReglagesDePlanification::parDefaut);
    }

    @Override
    public void enregistrer(CentreId centre, ReglagesDePlanification r) {
        RefusDeVerrou.verrouillant(() -> {
            int modifiees = jdbc.update("UPDATE planification_reglages SET replanification_auto = ?, heures_par_vacation = ?, "
                            + "heures_hebdo_temps_plein = ?, repos_hebdo_min = ? WHERE center_id = ?",
                    r.replanificationNocturne(), r.heuresParVacation(), r.heuresHebdoTempsPlein(), r.reposHebdoMin(),
                    centre.valeur());
            if (modifiees == 0) {
                jdbc.update("INSERT INTO planification_reglages (center_id, replanification_auto, heures_par_vacation, "
                                + "heures_hebdo_temps_plein, repos_hebdo_min) VALUES (?, ?, ?, ?, ?)",
                        centre.valeur(), r.replanificationNocturne(), r.heuresParVacation(), r.heuresHebdoTempsPlein(),
                        r.reposHebdoMin());
            }
            return null;
        });
    }

    @Override
    public List<CentreId> centresEnReplanificationNocturne() {
        return jdbc.query("SELECT center_id FROM planification_reglages WHERE replanification_auto = TRUE ORDER BY center_id",
                (rs, i) -> new CentreId(rs.getObject("center_id", java.util.UUID.class)));
    }
}
