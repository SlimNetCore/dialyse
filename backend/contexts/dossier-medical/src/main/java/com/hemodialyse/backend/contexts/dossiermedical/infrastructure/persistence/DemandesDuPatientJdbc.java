package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.application.DemandesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/** Existence d'une demande d'examen pour un patient d'un centre (table {@code demandes_examen}, lecture seule). */
public class DemandesDuPatientJdbc implements DemandesDuPatient {
    private final JdbcTemplate jdbc;

    public DemandesDuPatientJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existe(CentreId centre, PatientId patient, UUID demande) {
        return jdbc.queryForObject("SELECT count(*) FROM demandes_examen WHERE id = ? AND patient_id = ? AND center_id = ?", Long.class, demande,
                patient.valeur(), centre.valeur()) > 0;
    }
}
