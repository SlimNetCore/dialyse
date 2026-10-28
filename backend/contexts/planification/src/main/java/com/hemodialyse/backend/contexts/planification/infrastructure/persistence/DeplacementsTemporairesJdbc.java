package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.DeplacementTemporaire;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementsTemporairesRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.DeplacementTemporaireId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Déplacements temporaires de séances (table {@code deplacement_temporaire}, désormais écrite par le seul contexte Planification) :
 * un par centre, patient et date. Écrire remplace l'existant du même jour (DELETE puis INSERT dans l'ordre, dans la transaction de
 * l'appelant). Toutes les requêtes sont bornées au centre ; compatible H2 et PostgreSQL.
 */
public class DeplacementsTemporairesJdbc implements DeplacementsTemporairesRepository {

    private final JdbcTemplate jdbc;

    public DeplacementsTemporairesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(List<DeplacementTemporaire> deplacements) {
        Timestamp maintenant = Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC));
        for (DeplacementTemporaire d : deplacements) {
            jdbc.update("DELETE FROM deplacement_temporaire WHERE center_id = ? AND patient_id = ? AND date_seance = ?",
                    d.centre().valeur(), d.patient().valeur(), Date.valueOf(d.date()));
            jdbc.update("INSERT INTO deplacement_temporaire (id, center_id, patient_id, date_seance, salle_id, creneau_id, "
                            + "generateur_id, motif, cree_le) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    d.id().valeur(), d.centre().valeur(), d.patient().valeur(), Date.valueOf(d.date()), d.salle().valeur(),
                    d.creneau().valeur(), d.generateur().valeur(), d.motif().orElse(null), maintenant);
        }
    }

    @Override
    public List<DeplacementTemporaire> entre(CentreId centre, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT id, patient_id, date_seance, salle_id, creneau_id, generateur_id, motif "
                        + "FROM deplacement_temporaire WHERE center_id = ? AND date_seance BETWEEN ? AND ? "
                        + "ORDER BY date_seance, patient_id",
                (rs, i) -> DeplacementTemporaire.reconstituer(new DeplacementTemporaireId(rs.getObject("id", UUID.class)), centre,
                        new PatientId(rs.getObject("patient_id", UUID.class)), rs.getDate("date_seance").toLocalDate(),
                        new SalleId(rs.getObject("salle_id", UUID.class)), new CreneauId(rs.getObject("creneau_id", UUID.class)),
                        new GenerateurId(rs.getObject("generateur_id", UUID.class)), rs.getString("motif")),
                centre.valeur(), Date.valueOf(du), Date.valueOf(au));
    }
}
