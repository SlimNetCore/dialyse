package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.application.ExclusionsDeSeances;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ACL vers la table {@code seances} (SQL direct, ADR 0024) : une séance exclue de la facturation garde le lien vers l'absence que
 * l'exclusion a déclarée ({@code exclusion_absence_id}, migration V39) ; ce lien disparaît à la réinclusion.
 */
public class ExclusionsDeSeancesJdbc implements ExclusionsDeSeances {

    private final JdbcTemplate jdbc;

    public ExclusionsDeSeancesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean absenceIssueDUneExclusion(CentreId centre, AbsenceId absence) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ? AND exclusion_absence_id = ?",
                Integer.class, centre.valeur(), absence.valeur());
        return n != null && n > 0;
    }
}
