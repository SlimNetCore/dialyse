package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyoptimisation;

import com.hemodialyse.backend.contexts.planification.application.DeplacementsTemporaires;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.DeplacementTemporaire;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ACL vers l'optimisation (table {@code deplacement_temporaire}, écrite par le legacy jusqu'à P8) : lecture seule des déplacements
 * d'une période, triés par date puis patient. Bornée au centre.
 */
public class DeplacementsTemporairesJdbc implements DeplacementsTemporaires {

    private final JdbcTemplate jdbc;

    public DeplacementsTemporairesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DeplacementTemporaire> duCentre(CentreId centre, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT patient_id, date_seance, salle_id, creneau_id, generateur_id FROM deplacement_temporaire "
                        + "WHERE center_id = ? AND date_seance BETWEEN ? AND ? ORDER BY date_seance, patient_id",
                (rs, i) -> {
                    UUID salle = rs.getObject("salle_id", UUID.class);
                    UUID creneau = rs.getObject("creneau_id", UUID.class);
                    UUID generateur = rs.getObject("generateur_id", UUID.class);
                    return new DeplacementTemporaire(new PatientId(rs.getObject("patient_id", UUID.class)),
                            rs.getDate("date_seance").toLocalDate(), salle == null ? null : new SalleId(salle),
                            creneau == null ? null : new CreneauId(creneau), generateur == null ? null : new GenerateurId(generateur));
                }, centre.valeur(), Date.valueOf(du), Date.valueOf(au));
    }
}
