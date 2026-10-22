package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.absencespatients.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.SeanceRealisee;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ACL vers la table {@code seances} du contexte Séance (SQL direct tant que Séance n'est pas un contexte, ADR 0024) : une
 * séance est réalisée aux statuts validée, signée ou facturée.
 */
public class SeancesRealiseesJdbc implements SeancesRealisees {

    private static final String REALISEE = "s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')";

    private final JdbcTemplate jdbc;

    public SeancesRealiseesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SeanceRealisee> duCentre(CentreId centre, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT DISTINCT s.patient_id, s.date_seance FROM seances s WHERE s.center_id = ? "
                        + "AND s.date_seance BETWEEN ? AND ? AND " + REALISEE + " ORDER BY s.date_seance",
                (rs, i) -> new SeanceRealisee(new PatientId(rs.getObject(1, UUID.class)), rs.getDate(2).toLocalDate()),
                centre.valeur(), Date.valueOf(du), Date.valueOf(au));
    }

    @Override
    public List<LocalDate> joursDuPatient(CentreId centre, PatientId patient, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT DISTINCT s.date_seance FROM seances s WHERE s.center_id = ? AND s.patient_id = ? "
                        + "AND s.date_seance BETWEEN ? AND ? AND " + REALISEE + " ORDER BY s.date_seance",
                (rs, i) -> rs.getDate(1).toLocalDate(), centre.valeur(), patient.valeur(), Date.valueOf(du),
                Date.valueOf(au));
    }
}
