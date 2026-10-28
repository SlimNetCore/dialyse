package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacypatient;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ACL vers la table {@code patients} du contexte Patient et couverture (SQL direct tant qu'il n'est pas extrait, ADR 0024) :
 * un patient existe pour le centre s'il y est enregistré.
 */
public class PatientsDuCentreJdbc implements PatientsDuCentre {

    private final JdbcTemplate jdbc;

    public PatientsDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existe(CentreId centre, PatientId patient) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE center_id = ? AND id = ?", Integer.class,
                centre.valeur(), patient.valeur());
        return n != null && n > 0;
    }
}
