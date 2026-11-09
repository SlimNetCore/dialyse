package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.application.SocieteDUnCentre;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

/** La société d'un centre, lue sur {@code centers.societe_id} (SQL repris mot pour mot de l'ancien {@code DirectionRealtimeService}). */
public final class SocieteDUnCentreJdbc implements SocieteDUnCentre {

    private final JdbcTemplate jdbc;

    public SocieteDUnCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> de(UUID centreId) {
        var lignes = jdbc.queryForList("SELECT societe_id FROM centers WHERE id = ?", UUID.class, centreId);
        return lignes.isEmpty() ? Optional.empty() : Optional.ofNullable(lignes.get(0));
    }
}
