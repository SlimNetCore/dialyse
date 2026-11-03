package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.legacyattestation;

import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationsDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;

/** ACL de lecture sur {@code attestation_droit} (la table appartient à PC4) : validité à un jour, bornes incluses. */
public class AttestationsDuPatientJdbc implements AttestationsDuPatient {

    private final JdbcTemplate jdbc;

    public AttestationsDuPatientJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean valideLe(CentreId centre, PatientId patient, LocalDate jour) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM attestation_droit WHERE center_id = ? AND patient_id = ? "
                        + "AND ? BETWEEN date_debut AND date_fin", Integer.class, centre.valeur(), patient.valeur(), Date.valueOf(jour));
        return n != null && n > 0;
    }
}
