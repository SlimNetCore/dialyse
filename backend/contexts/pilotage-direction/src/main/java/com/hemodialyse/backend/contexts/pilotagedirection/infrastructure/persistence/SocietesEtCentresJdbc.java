package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.application.SocietesSuivies;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.CentreDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SocieteSuivie;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Lecteur TRANSITOIRE des tables de l'Organisation ({@code societes}, {@code centers}) : jalon inscrit dans {@code TableDeLOrganisationTest}
 * (2026-11-09, ADR 0102), à remplacer par {@code organisation.v1} (jalon O4, butoir 2027-03-31). Lecture seule.
 * <p>
 * SQL repris MOT POUR MOT de {@code DirectionDashboardQueryService.societe} (retiré, {@code :80} et {@code :81-83} de l'original, commit
 * {@code 9c21d51a}). Un centre sans colonne {@code actif} est actif.
 */
public final class SocietesEtCentresJdbc implements SocietesSuivies {

    private final JdbcTemplate jdbc;

    public SocietesEtCentresJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SocieteSuivie societe(UUID societeId) {
        String nom;
        try {
            nom = jdbc.queryForObject("SELECT raison_sociale FROM societes WHERE id = ?", String.class, societeId);
        } catch (EmptyResultDataAccessException e) {
            throw new SocieteInconnue(societeId);
        }
        List<CentreDeLaSociete> centres = jdbc.query(
                "SELECT id, name, COALESCE(actif, TRUE) AS actif FROM centers WHERE societe_id = ? ORDER BY name",
                (rs, i) -> new CentreDeLaSociete(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("actif")),
                societeId);
        return new SocieteSuivie(societeId, nom, centres);
    }
}
