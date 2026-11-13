package com.hemodialyse.backend.contexts.seance.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDuCreneau;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Ce que la Séance retient d'un créneau de l'Organisation (PL11-2, D3, ADR 0158) : le créneau de la place réalisée des séances. Toute
 * erreur technique est propagée : l'Organisation refuse alors la suppression (D10).
 */
public class UsageDuCreneauParLesSeances implements UsageDuCreneau {

    private final JdbcTemplate jdbc;

    public UsageDuCreneauParLesSeances(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nombreDeReferences(UUID creneau) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE creneau_id = ?", Long.class, creneau);
        return n == null ? 0 : n;
    }
}
