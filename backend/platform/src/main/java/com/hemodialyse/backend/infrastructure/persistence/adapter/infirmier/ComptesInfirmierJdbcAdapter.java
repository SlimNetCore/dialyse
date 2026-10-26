package com.hemodialyse.backend.infrastructure.persistence.adapter.infirmier;

import com.hemodialyse.backend.domain.infirmier.port.ComptesInfirmierPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Comptes utilisateurs vus depuis « Mon planning » (slice P3), sur la table {@code app_user}. Les écritures de comptes sont
 * servies par le contexte Planification (slice P2) ; cet adaptateur disparaît avec P3.
 */
@Component
public class ComptesInfirmierJdbcAdapter implements ComptesInfirmierPort {

    private static final RowMapper<CompteRef> MAPPER = (rs, i) -> new CompteRef(
            rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("full_name"), rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public ComptesInfirmierJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CompteRef> trouverParIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) return List.of();
        String marques = userIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        return jdbc.query("SELECT id, username, full_name, active FROM app_user WHERE id IN (" + marques + ")",
                MAPPER, userIds.toArray());
    }
}
