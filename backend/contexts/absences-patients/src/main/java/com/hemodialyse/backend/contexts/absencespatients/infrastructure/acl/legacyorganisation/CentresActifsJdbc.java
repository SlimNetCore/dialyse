package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyorganisation;

import com.hemodialyse.backend.contexts.absencespatients.application.CentresActifs;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * ACL vers la table {@code centers} (Organisation) : un centre est actif sauf s'il a été désactivé ({@code actif} nul = actif,
 * comme partout dans la plateforme).
 */
public class CentresActifsJdbc implements CentresActifs {

    private final JdbcTemplate jdbc;

    public CentresActifsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CentreId> liste() {
        return jdbc.query("SELECT id FROM centers WHERE COALESCE(actif, TRUE) = TRUE ORDER BY id",
                (rs, i) -> new CentreId(rs.getObject(1, UUID.class)));
    }
}
