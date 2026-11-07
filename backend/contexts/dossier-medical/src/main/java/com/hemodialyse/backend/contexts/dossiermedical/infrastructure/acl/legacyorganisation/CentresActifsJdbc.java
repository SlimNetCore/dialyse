package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.legacyorganisation;

import com.hemodialyse.backend.contexts.dossiermedical.application.CentresActifs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/** ACL vers la table des centres ({@code centers}, tenue par l'Organisation), en lecture. */
public class CentresActifsJdbc implements CentresActifs {

    private final JdbcTemplate jdbc;

    public CentresActifsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CentreId> tous() {
        return jdbc.query("SELECT id FROM centers", (rs, i) -> new CentreId(UUID.fromString(rs.getString(1))));
    }
}
