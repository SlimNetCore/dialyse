package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacycentres;

import com.hemodialyse.backend.contexts.planification.application.CentresActifs;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * ACL vers les centres (Identité et accès, encore legacy) : les centres actifs, par identifiant. Même définition que les Absences
 * ({@code COALESCE(actif, TRUE)}) : un centre dont {@code actif} est NULL est actif (la colonne accepte NULL et le legacy libérait tous
 * les centres) ; seul {@code actif = FALSE} désactive. Revue P7, constat « centre actif divergent ».
 */
public class CentresActifsLegacy implements CentresActifs {
    private final JdbcTemplate jdbc;

    public CentresActifsLegacy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CentreId> liste() {
        return jdbc.query("SELECT id FROM centers WHERE COALESCE(actif, TRUE) = TRUE ORDER BY id", (rs, i) -> new CentreId(rs.getObject(1, UUID.class)));
    }
}
