package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyorganisation;

import com.hemodialyse.backend.contexts.seance.application.CentresARappeler;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * ACL vers la table des centres ({@code centers}, tenue par l'Organisation), en lecture seule : tous les centres, comme le legacy
 * ({@code SELECT id FROM centers}, sans filtre d'activité).
 */
public class CentresARappelerJdbc implements CentresARappeler {

    private final JdbcTemplate jdbc;

    public CentresARappelerJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CentreId> tous() {
        return jdbc.query("SELECT id FROM centers", (rs, i) -> new CentreId(UUID.fromString(rs.getString(1))));
    }
}
