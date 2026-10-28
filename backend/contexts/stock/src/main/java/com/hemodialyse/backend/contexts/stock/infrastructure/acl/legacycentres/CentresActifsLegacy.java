package com.hemodialyse.backend.contexts.stock.infrastructure.acl.legacycentres;

import com.hemodialyse.backend.contexts.stock.application.peremptions.CentresActifs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * ACL vers les centres (contexte Identité et accès, encore legacy) : les centres dont {@code centers.actif} est vrai, par
 * identifiant (ordre stable).
 */
public class CentresActifsLegacy implements CentresActifs {

    private final JdbcTemplate jdbc;

    public CentresActifsLegacy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CentreId> liste() {
        return jdbc.query("SELECT id FROM centers WHERE actif = TRUE ORDER BY id",
                (rs, i) -> new CentreId(rs.getObject(1, UUID.class)));
    }
}
