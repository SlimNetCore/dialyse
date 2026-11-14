package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.BonsDeSortieDeLaReprise;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Contrôle des bons de sortie d'un patient repris (RP3b, ADR 0172). Requête reprise MOT POUR MOT de {@code MigrationRollbackJdbcAdapter} :
 * {@code SELECT CAST(id AS VARCHAR(36)) FROM bons_sortie WHERE patient_id = ?} (aucun centre, prudence voulue). Aucune transaction propre :
 * participe à celle de l'annulation (même {@code JdbcTemplate}).
 */
public class BonsDeSortieDeLaRepriseJdbc implements BonsDeSortieDeLaReprise {

    private final JdbcTemplate jdbc;

    public BonsDeSortieDeLaRepriseJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> bonsDeSortieDuPatient(UUID patient) {
        return jdbc.queryForList("SELECT CAST(id AS VARCHAR(36)) FROM bons_sortie WHERE patient_id = ?", String.class, patient);
    }
}
