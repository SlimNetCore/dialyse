package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.PlaceSeance;
import com.hemodialyse.backend.domain.seance.port.SeancePlacePort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Place d'un patient à une date, bornée au centre : le déplacement temporaire enregistré pour ce jour (maintenance d'un
 * générateur) prime sur la place habituelle de la fiche.
 */
@Component
public class SeancePlaceJdbcAdapter implements SeancePlacePort {

    private final JdbcTemplate jdbc;

    public SeancePlaceJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PlaceSeance> placeLe(CenterId centerId, UUID patientId, LocalDate date) {
        Optional<PlaceSeance> temporaire = jdbc.query("SELECT salle_id, creneau_id, generateur_id FROM "
                        + "deplacement_temporaire WHERE center_id = ? AND patient_id = ? AND date_seance = ?",
                (rs, i) -> place(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)),
                centerId.value(), patientId, Date.valueOf(date)).stream().flatMap(Optional::stream).findFirst();
        if (temporaire.isPresent()) return temporaire;
        return jdbc.query("SELECT salle_id, position_id, generateur_id FROM patients WHERE center_id = ? AND id = ?",
                (rs, i) -> place(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)),
                centerId.value(), patientId).stream().flatMap(Optional::stream).findFirst();
    }

    private static Optional<PlaceSeance> place(UUID salleId, UUID creneauId, UUID generateurId) {
        return salleId == null || creneauId == null ? Optional.empty()
                : Optional.of(new PlaceSeance(salleId, creneauId, generateurId));
    }
}
