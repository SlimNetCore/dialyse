package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDeLaSalle;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Ce que Patient et couverture retient d'une salle de l'Organisation (PL11-4, D3, ADR 0160) : la salle des patients et celle de leurs
 * mouvements. Toute erreur technique est propagée : l'Organisation refuse alors la suppression (D10).
 */
public class UsageDeLaSalleParLesPatients implements UsageDeLaSalle {

    private final JdbcTemplate jdbc;

    public UsageDeLaSalleParLesPatients(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nombreDeReferences(UUID salle) {
        return compter("patients", salle) + compter("mouvement_patient", salle);
    }

    private long compter(String table, UUID salle) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE salle_id = ?", Long.class, salle);
        return n == null ? 0 : n;
    }
}
