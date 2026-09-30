package com.hemodialyse.backend.infrastructure.persistence.adapter.migration;

import com.hemodialyse.backend.domain.migration.port.ValueMappingPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Correspondances de valeurs de la reprise saisies par le centre (JDBC).
 */
@Component
public class ValueMappingJdbcAdapter implements ValueMappingPort {

    private final JdbcTemplate jdbc;

    public ValueMappingJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Map<String, String>> findAll(CenterId centerId) {
        Map<String, Map<String, String>> out = new HashMap<>();
        jdbc.query("SELECT column_key, source_value, target_value FROM migration_value_map WHERE center_id = ?",
                rs -> {
                    out.computeIfAbsent(rs.getString("column_key"), k -> new HashMap<>())
                            .put(rs.getString("source_value"), rs.getString("target_value"));
                }, centerId.value());
        return out;
    }

    @Override
    public void save(CenterId centerId, String column, String normalizedSource, String target) {
        int updated = jdbc.update("UPDATE migration_value_map SET target_value = ? WHERE center_id = ? AND column_key = ? AND source_value = ?",
                target, centerId.value(), column, normalizedSource);
        if (updated == 0) {
            jdbc.update("INSERT INTO migration_value_map (id, center_id, column_key, source_value, target_value) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), centerId.value(), column, normalizedSource, target);
        }
    }

    @Override
    public void delete(CenterId centerId, String column, String normalizedSource) {
        jdbc.update("DELETE FROM migration_value_map WHERE center_id = ? AND column_key = ? AND source_value = ?",
                centerId.value(), column, normalizedSource);
    }
}

