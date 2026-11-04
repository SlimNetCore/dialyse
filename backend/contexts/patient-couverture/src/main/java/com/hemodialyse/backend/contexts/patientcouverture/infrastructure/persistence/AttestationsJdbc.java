package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationsDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Attestations;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapter des ports {@link Attestations} et {@link AttestationsDuPatient} sur la table {@code attestation_droit} (colonnes du legacy). */
public class AttestationsJdbc implements Attestations, AttestationsDuPatient {

    private static final String COLONNES = "id, patient_id, center_id, date_debut, date_fin, created_at";

    private static final RowMapper<AttestationDroit> LIGNE = (rs, n) -> {
        OffsetDateTime cree = rs.getObject("created_at", OffsetDateTime.class);
        Date debut = rs.getDate("date_debut");
        Date fin = rs.getDate("date_fin");
        return AttestationDroit.reconstituer(rs.getObject("id", UUID.class), new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), debut == null ? null : debut.toLocalDate(),
                fin == null ? null : fin.toLocalDate(), cree == null ? null : cree.toInstant());
    };

    private final JdbcTemplate jdbc;

    public AttestationsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AttestationDroit> trouver(CentreId centre, UUID id) {
        return jdbc.query("SELECT " + COLONNES + " FROM attestation_droit WHERE id = ? AND center_id = ?", LIGNE, id,
                centre.valeur()).stream().findFirst();
    }

    @Override
    public void enregistrer(AttestationDroit a) {
        jdbc.update("INSERT INTO attestation_droit (" + COLONNES + ") VALUES (?,?,?,?,?,?)", a.id(), a.patient().valeur(),
                a.centre().valeur(), Date.valueOf(a.debut()), Date.valueOf(a.fin()),
                a.creeLe() == null ? null : OffsetDateTime.ofInstant(a.creeLe(), ZoneOffset.UTC));
    }

    @Override
    public void supprimer(UUID id) {
        jdbc.update("DELETE FROM attestation_droit WHERE id = ?", id);
    }

    @Override
    public List<AttestationDroit> duPatient(CentreId centre, PatientId patient) {
        return jdbc.query("SELECT " + COLONNES + " FROM attestation_droit WHERE center_id = ? AND patient_id = ?", LIGNE,
                centre.valeur(), patient.valeur());
    }

    @Override
    public boolean existe(CentreId centre, PatientId patient) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM attestation_droit WHERE center_id = ? AND patient_id = ?",
                Integer.class, centre.valeur(), patient.valeur());
        return n != null && n > 0;
    }

    @Override
    public boolean valideLe(CentreId centre, PatientId patient, LocalDate jour) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM attestation_droit WHERE center_id = ? AND patient_id = ? "
                + "AND ? BETWEEN date_debut AND date_fin", Integer.class, centre.valeur(), patient.valeur(), Date.valueOf(jour));
        return n != null && n > 0;
    }
}
