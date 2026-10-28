package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.CreneauxDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ACL vers les créneaux d'Organisation (table {@code position_creneau}, encore dans le legacy).
 */
public class CreneauxDuCentreJdbc implements CreneauxDuCentre {

    private final JdbcTemplate jdbc;

    public CreneauxDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean contient(CentreId centre, CreneauId creneau) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM position_creneau WHERE center_id = ? AND id = ?", Long.class,
                centre.valeur(), creneau.valeur());
        return n != null && n > 0;
    }
}
