package com.hemodialyse.backend.infrastructure.persistence.adapter.migration;

import com.hemodialyse.backend.infrastructure.migration.model.EntityRun;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationBatch;
import com.hemodialyse.backend.infrastructure.migration.port.MigrationBatchRepositoryPort;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lots de reprise et comptes rendus (JDBC). Toutes les requêtes sont filtrées sur le centre.
 */
@Component
public class MigrationBatchJdbcAdapter implements MigrationBatchRepositoryPort {

    private static final String COLUMNS = "id, center_id, libelle, source_system, date_debut_reprise, status, created_by, created_at, closed_at";
    private static final RowMapper<MigrationBatch> BATCH = (rs, i) -> MigrationBatch.restore(
            rs.getObject("id", UUID.class),
            new CentreId(rs.getObject("center_id", UUID.class)),
            rs.getString("libelle"),
            rs.getString("source_system"),
            rs.getObject("date_debut_reprise", LocalDate.class),
            MigrationBatch.Status.valueOf(rs.getString("status")),
            rs.getString("created_by"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("closed_at", OffsetDateTime.class));
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public MigrationBatchJdbcAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private static Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    @Override
    public MigrationBatch save(MigrationBatch b) {
        int updated = jdbc.update("UPDATE migration_batch SET libelle = ?, source_system = ?, date_debut_reprise = ?, status = ?, "
                        + "closed_at = ? WHERE id = ? AND center_id = ?",
                b.getLibelle(), b.getSourceSystem(), date(b.getDateDebutReprise()), b.getStatus().name(), b.getClosedAt(),
                b.getId(), b.getCenterId().valeur());
        if (updated == 0) {
            jdbc.update("INSERT INTO migration_batch (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    b.getId(), b.getCenterId().valeur(), b.getLibelle(), b.getSourceSystem(), date(b.getDateDebutReprise()),
                    b.getStatus().name(), b.getCreatedBy(), b.getCreatedAt(), b.getClosedAt());
        }
        return b;
    }

    @Override
    public Optional<MigrationBatch> findById(CentreId centerId, UUID batchId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM migration_batch WHERE id = ? AND center_id = ?", BATCH,
                batchId, centerId.valeur()).stream().findFirst();
    }

    @Override
    public Optional<MigrationBatch> findActive(CentreId centerId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM migration_batch WHERE center_id = ? AND status = 'EN_COURS'", BATCH,
                centerId.valeur()).stream().findFirst();
    }

    @Override
    public PagedResult<MigrationBatch> findPaged(CentreId centerId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM migration_batch WHERE center_id = ?", Long.class, centerId.valeur());
        List<MigrationBatch> items = jdbc.query("SELECT " + COLUMNS + " FROM migration_batch WHERE center_id = ? "
                + "ORDER BY created_at DESC LIMIT ? OFFSET ?", BATCH, centerId.valeur(), size, (long) page * size);
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public void saveRun(CentreId centerId, UUID batchId, EntityRun run) {
        jdbc.update("INSERT INTO migration_entity_run (id, batch_id, center_id, entity, file_name, dry_run, applied, total_rows, "
                        + "created_count, updated_count, error_count, warning_count, report_json, executed_by, executed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), batchId, centerId.valeur(), run.entity(), run.fileName(), run.dryRun(), run.applied(),
                run.totalRows(), run.created(), run.updated(), run.errors().size(), run.warnings().size(),
                json.writeValueAsString(StoredRun.from(run)), run.executedBy(), run.executedAt());
    }

    @Override
    public List<EntityRun> findLatestRuns(CentreId centerId, UUID batchId) {
        return jdbc.query("SELECT r.report_json FROM migration_entity_run r WHERE r.batch_id = ? AND r.center_id = ? "
                        + "AND r.executed_at = (SELECT MAX(x.executed_at) FROM migration_entity_run x "
                        + "WHERE x.batch_id = r.batch_id AND x.entity = r.entity) ORDER BY r.executed_at",
                (rs, i) -> json.readValue(rs.getString("report_json"), StoredRun.class).toDomain(), batchId, centerId.valeur());
    }

    /**
     * Forme persistée (JSON) d'un compte rendu, découplée du modèle de domaine.
     */
    record StoredRun(String entity, String fileName, int totalRows, int created, int updated,
                     List<String> missingColumns, List<String> ignoredColumns,
                     List<ValidationIssue> errors, List<ValidationIssue> warnings,
                     boolean dryRun, boolean applied, String executedBy, OffsetDateTime executedAt) {

        static StoredRun from(EntityRun r) {
            return new StoredRun(r.entity(), r.fileName(), r.totalRows(), r.created(), r.updated(), r.missingColumns(),
                    r.ignoredColumns(), r.errors(), r.warnings(), r.dryRun(), r.applied(), r.executedBy(), r.executedAt());
        }

        EntityRun toDomain() {
            return new EntityRun(entity, fileName, totalRows, created, updated, missingColumns, ignoredColumns, errors,
                    warnings, dryRun, applied, executedBy, executedAt);
        }
    }
}





