package com.hemodialyse.backend.contexts.seance.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDeLaSalle;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Ce que la Séance retient d'une salle de l'Organisation (PL11-4, D3, ADR 0160) : la salle de la place réalisée des séances. Toute erreur
 * technique est propagée : l'Organisation refuse alors la suppression (D10).
 */
public class UsageDeLaSalleParLesSeances implements UsageDeLaSalle {

    private final JdbcTemplate jdbc;

    public UsageDeLaSalleParLesSeances(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nombreDeReferences(UUID salle) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE salle_id = ?", Long.class, salle);
        return n == null ? 0 : n;
    }
}
