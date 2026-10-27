package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.PlaceSeance;
import com.hemodialyse.backend.domain.seance.port.SeancePlacePort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacesDesPatients;
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
    /** La place habituelle vient de la lecture publiée par Planification (P6), plus des colonnes de {@code patients}. */
    private final PlacesDesPatients places;

    public SeancePlaceJdbcAdapter(JdbcTemplate jdbc, PlacesDesPatients places) {
        this.jdbc = jdbc;
        this.places = places;
    }

    @Override
    public Optional<PlaceSeance> placeLe(CenterId centerId, UUID patientId, LocalDate date) {
        Optional<PlaceSeance> temporaire = jdbc.query("SELECT salle_id, creneau_id, generateur_id FROM "
                        + "deplacement_temporaire WHERE center_id = ? AND patient_id = ? AND date_seance = ?",
                (rs, i) -> place(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)),
                centerId.value(), patientId, Date.valueOf(date)).stream().flatMap(Optional::stream).findFirst();
        if (temporaire.isPresent()) return temporaire;
        return places.deLaFiche(centerId.value(), patientId).flatMap(p -> place(p.salle(), p.creneau(), p.generateur()));
    }

    private static Optional<PlaceSeance> place(UUID salleId, UUID creneauId, UUID generateurId) {
        return salleId == null || creneauId == null ? Optional.empty()
                : Optional.of(new PlaceSeance(salleId, creneauId, generateurId));
    }
}
