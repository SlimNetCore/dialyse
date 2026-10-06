package com.hemodialyse.backend.infrastructure.persistence.adapter.optimisation;

import com.hemodialyse.backend.domain.planning.model.DeplacementTemporaire;
import com.hemodialyse.backend.domain.planning.port.DeplacementTemporairePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Déplacements temporaires de séances (table {@code deplacement_temporaire}) : un par patient et par date au plus.
 * Toutes les requêtes sont bornées au centre.
 */
@Component
public class DeplacementTemporaireJdbcAdapter implements DeplacementTemporairePort {

    private final JdbcTemplate jdbc;

    public DeplacementTemporaireJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DeplacementTemporaire> entre(UUID centerId, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT id, center_id, patient_id, date_seance, salle_id, creneau_id, generateur_id, motif "
                        + "FROM deplacement_temporaire WHERE center_id = ? AND date_seance BETWEEN ? AND ? "
                        + "ORDER BY date_seance, patient_id",
                (rs, i) -> new DeplacementTemporaire(rs.getObject("id", UUID.class),
                        rs.getObject("center_id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getDate("date_seance").toLocalDate(), rs.getObject("salle_id", UUID.class),
                        rs.getObject("creneau_id", UUID.class), rs.getObject("generateur_id", UUID.class),
                        rs.getString("motif")),
                centerId, Date.valueOf(du), Date.valueOf(au));
    }

    @Override
    public void enregistrer(List<DeplacementTemporaire> deplacements) {
        Timestamp maintenant = Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC));
        for (DeplacementTemporaire d : deplacements) {
            jdbc.update("DELETE FROM deplacement_temporaire WHERE center_id = ? AND patient_id = ? AND date_seance = ?",
                    d.centerId(), d.patientId(), Date.valueOf(d.date()));
            jdbc.update("INSERT INTO deplacement_temporaire (id, center_id, patient_id, date_seance, salle_id, creneau_id, "
                            + "generateur_id, motif, cree_le) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    d.id(), d.centerId(), d.patientId(), Date.valueOf(d.date()), d.salleId(), d.creneauId(),
                    d.generateurId(), d.motif(), maintenant);
        }
    }
}
