package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDuCreneau;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Ce que Patient et couverture retient d'un créneau de l'Organisation (PL11-2, D3, ADR 0158) : la position des patients et celle de
 * leurs mouvements. Toute erreur technique est propagée : l'Organisation refuse alors la suppression (D10).
 */
public class UsageDuCreneauParLesPatients implements UsageDuCreneau {

    private final JdbcTemplate jdbc;

    public UsageDuCreneauParLesPatients(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nombreDeReferences(UUID creneau) {
        return compter("patients", creneau) + compter("mouvement_patient", creneau);
    }

    private long compter(String table, UUID creneau) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE position_id = ?", Long.class, creneau);
        return n == null ? 0 : n;
    }
}
