package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyplanification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacesDesPatients;
import com.hemodialyse.backend.contexts.seance.application.PlaceDuPatient;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PlaceDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Place d'un patient à une date, bornée au centre (SE7f, ADR 0100) : repris MOT POUR MOT de {@code SeancePlaceJdbcAdapter} (plateforme,
 * retiré). Le déplacement temporaire enregistré pour ce jour (maintenance d'un générateur, table {@code deplacement_temporaire} de la
 * Planification, lecture transitoire inscrite dans {@code TableDeLaPlanificationTest}) prime sur la place habituelle de la fiche, lue par le
 * langage publié {@link PlacesDesPatients}. Sans salle ou sans créneau : vide (P6, golden {@code LecturesDePlaceConsommeesParLaSeance}).
 */
public class PlaceDuPatientJdbc implements PlaceDuPatient {

    private final JdbcTemplate jdbc;
    private final PlacesDesPatients places;

    public PlaceDuPatientJdbc(JdbcTemplate jdbc, PlacesDesPatients places) {
        this.jdbc = jdbc;
        this.places = places;
    }

    @Override
    public Optional<PlaceDeSeance> placeLe(CentreId centre, PatientId patient, LocalDate date) {
        Optional<PlaceDeSeance> temporaire = jdbc.query("SELECT salle_id, creneau_id, generateur_id FROM deplacement_temporaire "
                        + "WHERE center_id = ? AND patient_id = ? AND date_seance = ?",
                (rs, i) -> place(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)),
                centre.valeur(), patient.valeur(), Date.valueOf(date)).stream().flatMap(Optional::stream).findFirst();
        if (temporaire.isPresent()) return temporaire;
        return places.deLaFiche(centre.valeur(), patient.valeur()).flatMap(p -> place(p.salle(), p.creneau(), p.generateur()));
    }

    static Optional<PlaceDeSeance> place(UUID salle, UUID creneau, UUID generateur) {
        return salle == null || creneau == null ? Optional.empty() : Optional.of(new PlaceDeSeance(salle, creneau, generateur));
    }
}
