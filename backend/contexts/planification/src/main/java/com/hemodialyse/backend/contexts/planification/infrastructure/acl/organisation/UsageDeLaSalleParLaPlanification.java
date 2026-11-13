package com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDeLaSalle;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Ce que la Planification retient d'une salle de l'Organisation (PL11-4, D3, ADR 0160) : les affectations et remplacements
 * d'infirmiers, les cases des calendriers proposés et les déplacements temporaires. Toute erreur technique est propagée : l'Organisation
 * refuse alors la suppression (D10).
 */
public class UsageDeLaSalleParLaPlanification implements UsageDeLaSalle {

    private static final String[] TABLES = {"infirmier_affectation", "infirmier_remplacement", "planification_calendrier_case",
            "deplacement_temporaire"};

    private final JdbcTemplate jdbc;

    public UsageDeLaSalleParLaPlanification(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nombreDeReferences(UUID salle) {
        long total = 0;
        for (String table : TABLES) {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE salle_id = ?", Long.class, salle);
            total += n == null ? 0 : n;
        }
        return total;
    }
}
