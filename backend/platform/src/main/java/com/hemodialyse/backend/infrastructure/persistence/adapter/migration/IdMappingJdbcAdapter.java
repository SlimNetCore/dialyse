package com.hemodialyse.backend.infrastructure.persistence.adapter.migration;

import com.hemodialyse.backend.infrastructure.migration.model.IdMapping;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationEntity;
import com.hemodialyse.backend.infrastructure.migration.port.IdMappingPort;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Correspondances d'identifiants de la reprise (JDBC), uniques par centre, donnée et identifiant d'origine.
 */
@Component
public class IdMappingJdbcAdapter implements IdMappingPort {

    private final JdbcTemplate jdbc;

    public IdMappingJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> findTarget(CentreId centerId, MigrationEntity entity, String legacyId) {
        return jdbc.queryForList("SELECT target_id FROM migration_id_map WHERE center_id = ? AND entity = ? AND legacy_id = ?",
                String.class, centerId.valeur(), entity.name(), legacyId).stream().findFirst();
    }

    @Override
    public Map<String, String> findAll(CentreId centerId, MigrationEntity entity) {
        Map<String, String> out = new HashMap<>();
        jdbc.query("SELECT legacy_id, target_id FROM migration_id_map WHERE center_id = ? AND entity = ?",
                rs -> {
                    out.put(rs.getString("legacy_id"), rs.getString("target_id"));
                }, centerId.valeur(), entity.name());
        return out;
    }

    @Override
    public void save(CentreId centerId, UUID batchId, IdMapping mapping) {
        int updated = jdbc.update("UPDATE migration_id_map SET target_id = ? WHERE center_id = ? AND entity = ? AND legacy_id = ?",
                mapping.targetId(), centerId.valeur(), mapping.entity().name(), mapping.legacyId());
        if (updated == 0) {
            // Une correspondance déjà connue garde son lot, son opération et son état d'origine (celui d'AVANT la première reprise).
            jdbc.update("INSERT INTO migration_id_map (id, center_id, batch_id, entity, legacy_id, target_id, operation, created_at, "
                            + "etat_origine) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), centerId.valeur(), batchId, mapping.entity().name(), mapping.legacyId(),
                    mapping.targetId(), mapping.operation().name(), OffsetDateTime.now(), mapping.etatOrigine());
        }
    }

    @Override
    public List<IdMapping> findByBatch(CentreId centerId, UUID batchId) {
        return jdbc.query("SELECT entity, legacy_id, target_id, operation, etat_origine FROM migration_id_map "
                        + "WHERE center_id = ? AND batch_id = ? ORDER BY created_at",
                (rs, i) -> new IdMapping(MigrationEntity.valueOf(rs.getString("entity")), rs.getString("legacy_id"),
                        rs.getString("target_id"), IdMapping.Operation.valueOf(rs.getString("operation")),
                        rs.getString("etat_origine")),
                centerId.valeur(), batchId);
    }

    @Override
    public void deleteByBatch(CentreId centerId, UUID batchId) {
        jdbc.update("DELETE FROM migration_id_map WHERE center_id = ? AND batch_id = ?", centerId.valeur(), batchId);
    }
}

