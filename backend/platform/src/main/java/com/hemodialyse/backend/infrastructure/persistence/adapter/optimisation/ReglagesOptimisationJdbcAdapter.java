package com.hemodialyse.backend.infrastructure.persistence.adapter.optimisation;

import com.hemodialyse.backend.domain.planning.optimisation.model.ReglagesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.ReglagesOptimisationPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Réglages de l'optimisation (table {@code planification_reglages}, une ligne par centre).
 */
@Component
public class ReglagesOptimisationJdbcAdapter implements ReglagesOptimisationPort {

    private final JdbcTemplate jdbc;

    public ReglagesOptimisationJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ReglagesOptimisation lire(UUID centerId) {
        return jdbc.query("SELECT replanification_auto, heures_par_vacation, heures_hebdo_temps_plein, repos_hebdo_min "
                        + "FROM planification_reglages WHERE center_id = ?",
                (rs, i) -> new ReglagesOptimisation(rs.getBoolean("replanification_auto"),
                        rs.getInt("heures_par_vacation"), rs.getInt("heures_hebdo_temps_plein"),
                        rs.getInt("repos_hebdo_min")), centerId)
                .stream().findFirst().orElseGet(ReglagesOptimisation::parDefaut);
    }

    @Override
    public void enregistrer(UUID centerId, ReglagesOptimisation r) {
        int maj = jdbc.update("UPDATE planification_reglages SET replanification_auto = ?, heures_par_vacation = ?, "
                        + "heures_hebdo_temps_plein = ?, repos_hebdo_min = ? WHERE center_id = ?",
                r.replanificationAuto(), r.heuresParVacation(), r.heuresHebdoTempsPlein(), r.reposHebdoMin(), centerId);
        if (maj == 0) {
            jdbc.update("INSERT INTO planification_reglages (center_id, replanification_auto, heures_par_vacation, "
                            + "heures_hebdo_temps_plein, repos_hebdo_min) VALUES (?, ?, ?, ?, ?)",
                    centerId, r.replanificationAuto(), r.heuresParVacation(), r.heuresHebdoTempsPlein(), r.reposHebdoMin());
        }
    }

    @Override
    public List<UUID> centresEnReplanificationAuto() {
        return jdbc.query("SELECT center_id FROM planification_reglages WHERE replanification_auto = TRUE ORDER BY center_id",
                (rs, i) -> rs.getObject("center_id", UUID.class));
    }
}
