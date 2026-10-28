package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

/**
 * ACL vers Patient et couverture (table {@code patients}, encore dans le legacy) : un patient est sorti quand son état est
 * {@code TRANSFERE}, {@code DECEDE}, {@code GREFFE} ou {@code GUERRI} (RG-PAT-032).
 */
public class PatientsDuCentreJdbc implements PatientsDuCentre {

    private static final Set<String> ETATS_DE_SORTIE = Set.of("TRANSFERE", "DECEDE", "GREFFE", "GUERRI");

    private final JdbcTemplate jdbc;

    public PatientsDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Situation situation(CentreId centre, PatientId patient) {
        List<String> etats = jdbc.query("SELECT COALESCE(etat_patient, '') AS etat FROM patients WHERE center_id = ? AND id = ?",
                (rs, i) -> rs.getString("etat"), centre.valeur(), patient.valeur());
        if (etats.isEmpty()) return Situation.INTROUVABLE;
        return ETATS_DE_SORTIE.contains(etats.getFirst()) ? Situation.SORTI : Situation.EN_SUIVI;
    }
}
