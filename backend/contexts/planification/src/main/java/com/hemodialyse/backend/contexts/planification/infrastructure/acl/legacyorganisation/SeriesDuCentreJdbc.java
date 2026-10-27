package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation;

import com.hemodialyse.backend.contexts.planification.application.SeriesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

/** ACL vers les créneaux d'Organisation (table {@code position_creneau}) : le nombre de séries du centre. Borné au centre. */
public class SeriesDuCentreJdbc implements SeriesDuCentre {

    private final JdbcTemplate jdbc;

    public SeriesDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int nombre(CentreId centre) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM position_creneau WHERE center_id = ?", Integer.class, centre.valeur());
        return n == null ? 0 : n;
    }
}
