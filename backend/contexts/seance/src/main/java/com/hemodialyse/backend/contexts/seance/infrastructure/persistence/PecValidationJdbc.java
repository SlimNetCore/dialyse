package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.domain.PecId;
import com.hemodialyse.backend.contexts.seance.domain.PecValidationPort;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

public class PecValidationJdbc implements PecValidationPort {
    private final JdbcTemplate jdbc;

    public PecValidationJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean estValide(PecId pecId, CentreId centreId, PatientId patientId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM prise_en_charge WHERE id = ? AND center_id = ? AND patient_id = ? AND statut = 'VALIDEE'",
                Integer.class, pecId.valeur(), centreId.valeur(), patientId.valeur());
        return n != null && n > 0;
    }
}