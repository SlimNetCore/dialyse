package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesSocietes;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Annuaire des sociétés sur les tables {@code societes} et {@code centers}. SQL repris à l'identique de {@code SocietesEtCentresJdbc}
 * et {@code SocieteDUnCentreJdbc} (Pilotage direction, retirés au lot A de O4, ADR 0107).
 */
public class AnnuaireDesSocietesJdbc implements AnnuaireDesSocietes {

    private final JdbcTemplate jdbc;

    public AnnuaireDesSocietesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> societesActives() {
        return jdbc.queryForList("SELECT id FROM societes WHERE actif = TRUE", UUID.class);
    }

    @Override
    public Optional<UUID> societeDuCentre(UUID centre) {
        var lignes = jdbc.queryForList("SELECT societe_id FROM centers WHERE id = ?", UUID.class, centre);
        return lignes.isEmpty() ? Optional.empty() : Optional.ofNullable(lignes.get(0));
    }

    @Override
    public Optional<SocieteAvecSesCentres> societe(UUID societe) {
        String nom;
        try {
            nom = jdbc.queryForObject("SELECT raison_sociale FROM societes WHERE id = ?", String.class, societe);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
        List<CentreDeLaSociete> centres = jdbc.query(
                "SELECT id, name, COALESCE(actif, TRUE) AS actif FROM centers WHERE societe_id = ? ORDER BY name",
                (rs, i) -> new CentreDeLaSociete(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("actif")),
                societe);
        return Optional.of(new SocieteAvecSesCentres(societe, nom, centres));
    }
}
