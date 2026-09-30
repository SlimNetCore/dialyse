package com.hemodialyse.backend.domain.migration.service;

import com.hemodialyse.backend.domain.migration.model.ColumnDef;
import com.hemodialyse.backend.domain.migration.model.ColumnType;
import com.hemodialyse.backend.domain.migration.model.EntityRun;
import com.hemodialyse.backend.domain.migration.model.IdMapping;
import com.hemodialyse.backend.domain.migration.model.MigrationBatch;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.port.IdMappingPort;
import com.hemodialyse.backend.domain.migration.port.MigrationBatchRepositoryPort;
import com.hemodialyse.backend.domain.migration.port.MigrationRollbackPort;
import com.hemodialyse.backend.domain.migration.port.MigrationUseCase;
import com.hemodialyse.backend.domain.migration.port.ValueMappingPort;
import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialField;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain Service — reprise des données d'un système existant.
 * <p>
 * Pure domain class (aucune dépendance Spring/JPA — AGENTS.md §3), câblée dans {@code DomainServiceConfig}.
 * La transaction (import tout-ou-rien d'un fichier) est portée par le service applicatif.
 */
public class MigrationDomainService implements MigrationUseCase {

    public static final int MAX_ROWS = 5000;
    private static final int MAX_REPORTED = 500;

    private final MigrationBatchRepositoryPort batches;
    private final IdMappingPort ids;
    private final ValueMappingPort valueMappings;
    private final MigrationRollbackPort rollback;
    private final Map<MigrationEntity, EntityMigrator> migrators = new EnumMap<>(MigrationEntity.class);
    private final Clock clock;
    private final LegacyValueParser parser = new LegacyValueParser();

    public MigrationDomainService(MigrationBatchRepositoryPort batches, IdMappingPort ids, ValueMappingPort valueMappings,
                                  MigrationRollbackPort rollback, List<EntityMigrator> migrators, Clock clock) {
        this.batches = batches;
        this.ids = ids;
        this.valueMappings = valueMappings;
        this.rollback = rollback;
        this.clock = clock;
        migrators.forEach(m -> this.migrators.put(m.entity(), m));
    }

    private static ColumnDef enumColumn(String column) {
        return Arrays.stream(MigrationEntity.values())
                .flatMap(e -> e.columns().stream())
                .filter(c -> c.key().equals(column) && c.type() == ColumnType.ENUM)
                .findFirst()
                .orElseThrow(() -> new BusinessException("MIGRATION_MAPPING_COLUMN_INVALID",
                        "Aucune correspondance de valeurs possible pour la colonne « " + column + " »."));
    }

    private static ValidationIssue fileIssue(String code, String message) {
        return new ValidationIssue(0, null, code, message, Map.of());
    }

    private static List<ValidationIssue> truncate(List<ValidationIssue> issues) {
        if (issues.size() <= MAX_REPORTED) return issues;
        List<ValidationIssue> head = new ArrayList<>(issues.subList(0, MAX_REPORTED));
        head.add(new ValidationIssue(0, null, "TOO_MANY_ERRORS", (issues.size() - MAX_REPORTED)
                + " autre(s) point(s) non affiché(s).", Map.of("count", String.valueOf(issues.size() - MAX_REPORTED))));
        return head;
    }

    @Override
    public MigrationBatch open(CenterId centerId, String libelle, String sourceSystem, LocalDate dateDebutReprise, String user) {
        batches.findActive(centerId).ifPresent(active -> {
            throw new BusinessException("MIGRATION_BATCH_ALREADY_OPEN",
                    "Un lot de reprise est déjà en cours pour ce centre (« " + active.getLibelle() + " ») : terminez-le ou annulez-le d'abord.");
        });
        return batches.save(MigrationBatch.ouvrir(centerId, libelle, sourceSystem, dateDebutReprise, user, now()));
    }

    @Override
    public PagedResult<MigrationBatch> list(CenterId centerId, int page, int size) {
        return batches.findPaged(centerId, Math.max(page, 0), size <= 0 ? 20 : Math.min(size, 100));
    }

    @Override
    public BatchDetail get(CenterId centerId, UUID batchId) {
        MigrationBatch batch = require(centerId, batchId);
        return new BatchDetail(batch, batches.findLatestRuns(centerId, batchId));
    }

    @Override
    public EntityRun importEntity(CenterId centerId, UUID batchId, MigrationEntity entity, String fileName,
                                  ImportTable table, boolean dryRun, String user) {
        MigrationBatch batch = require(centerId, batchId);
        batch.ensureOpen();
        EntityMigrator migrator = Optional.ofNullable(migrators.get(entity))
                .orElseThrow(() -> new BusinessException("MIGRATION_ENTITY_UNSUPPORTED",
                        "La reprise de « " + entity.label() + " » n'est pas encore disponible."));

        EntityRun run = check(centerId, batch, entity, migrator, fileName, table, dryRun, user);
        batches.saveRun(centerId, batchId, run);
        return run;
    }

    private EntityRun check(CenterId centerId, MigrationBatch batch, MigrationEntity entity, EntityMigrator migrator,
                            String fileName, ImportTable table, boolean dryRun, String user) {
        if (table.headers().stream().allMatch(h -> h == null || h.isBlank())) {
            return run(entity, fileName, 0, 0, 0, List.of(), List.of(),
                    List.of(fileIssue("EMPTY_FILE", "Le fichier est vide : la première ligne doit contenir les en-têtes.")),
                    List.of(), dryRun, false, user);
        }

        // 1. En-têtes → colonnes.
        Map<Integer, ColumnDef> columns = new LinkedHashMap<>();
        List<String> ignored = new ArrayList<>();
        for (int i = 0; i < table.headers().size(); i++) {
            String header = table.headers().get(i);
            if (header == null || header.isBlank()) continue;
            String normalized = ReferentialField.normalize(header);
            Optional<ColumnDef> column = entity.columns().stream()
                    .filter(c -> c.matchesHeader(normalized) && !columns.containsValue(c))
                    .findFirst();
            if (column.isPresent()) columns.put(i, column.get());
            else ignored.add(header.trim());
        }
        List<String> missing = entity.columns().stream()
                .filter(ColumnDef::requiredColumn).filter(c -> !columns.containsValue(c)).map(ColumnDef::key).toList();
        List<ImportTable.Row> dataRows = table.rows().stream().filter(r -> !r.isBlank()).toList();
        if (!missing.isEmpty()) {
            return run(entity, fileName, dataRows.size(), 0, 0, missing, ignored, List.of(), List.of(), dryRun, false, user);
        }
        if (dataRows.isEmpty()) {
            return run(entity, fileName, 0, 0, 0, List.of(), ignored,
                    List.of(fileIssue("NO_DATA", "Le fichier ne contient aucune ligne de données.")), List.of(), dryRun, false, user);
        }
        if (dataRows.size() > MAX_ROWS) {
            return run(entity, fileName, dataRows.size(), 0, 0, List.of(), ignored,
                    List.of(fileIssue("TOO_MANY_ROWS", "Le fichier contient " + dataRows.size() + " lignes : maximum "
                            + MAX_ROWS + " par fichier.")), List.of(), dryRun, false, user);
        }

        // 2. Valeurs normalisées ligne par ligne.
        Map<String, Map<String, String>> mappings = valueMappings.findAll(centerId);
        List<ValidationIssue> errors = new ArrayList<>();
        List<EntityMigrator.Row> parsed = new ArrayList<>();
        for (ImportTable.Row row : dataRows) {
            Map<String, String> raw = new HashMap<>();
            columns.forEach((index, column) -> raw.put(column.key(), row.cell(index)));
            LegacyValueParser.Result result = parser.parse(entity.columns(), raw, mappings);
            if (result.valid()) parsed.add(new EntityMigrator.Row(row.lineNumber(), result.values()));
            else result.issues().forEach(issue -> errors.add(issue.atRow(row.lineNumber())));
        }

        // 3. Contrôles propres à la donnée ; écriture seulement si tout le fichier est valide.
        boolean write = !dryRun && errors.isEmpty();
        EntityMigrator.Outcome outcome = migrator.migrate(new EntityMigrator.Context(centerId, batch, ids), parsed, write);
        errors.addAll(outcome.errors());
        errors.sort((a, b) -> Integer.compare(a.row(), b.row()));
        boolean applied = write && outcome.errors().isEmpty();
        return run(entity, fileName, dataRows.size(), outcome.created(), outcome.updated(), List.of(), ignored,
                truncate(errors), truncate(outcome.warnings()), dryRun, applied, user);
    }

    @Override
    public MigrationBatch close(CenterId centerId, UUID batchId) {
        MigrationBatch batch = require(centerId, batchId);
        batch.terminer(now());
        return batches.save(batch);
    }

    @Override
    public MigrationBatch cancel(CenterId centerId, UUID batchId) {
        MigrationBatch batch = require(centerId, batchId);
        batch.ensureOpen();
        List<IdMapping> created = ids.findByBatch(centerId, batchId).stream()
                .filter(m -> m.operation() == IdMapping.Operation.CREATED)
                .sorted((a, b) -> Integer.compare(b.entity().order(), a.entity().order()))
                .toList();
        List<String> blockers = rollback.blockers(centerId, created);
        if (!blockers.isEmpty()) {
            throw new BusinessException("MIGRATION_CANCEL_BLOCKED",
                    "Annulation impossible : des données reprises ont déjà été utilisées — " + String.join(" ; ", blockers));
        }
        rollback.delete(centerId, created);
        ids.deleteByBatch(centerId, batchId);
        batch.annuler(now());
        return batches.save(batch);
    }

    // ---- Utilitaires -------------------------------------------------------------------------------------------

    @Override
    public Map<String, Map<String, String>> valueMappings(CenterId centerId) {
        return valueMappings.findAll(centerId);
    }

    @Override
    public void saveValueMapping(CenterId centerId, String column, String sourceValue, String targetValue) {
        ColumnDef def = enumColumn(column);
        String source = LegacyValueParser.toCode(sourceValue);
        if (source.isEmpty()) {
            throw new BusinessException("MIGRATION_MAPPING_SOURCE_REQUIRED", "La valeur d'origine est obligatoire.");
        }
        if (!def.allowedValues().contains(targetValue)) {
            throw new BusinessException("MIGRATION_MAPPING_TARGET_INVALID",
                    "« " + targetValue + " » n'est pas une valeur possible de « " + def.label() + " » ("
                            + String.join(", ", def.allowedValues()) + ").");
        }
        valueMappings.save(centerId, column, source, targetValue);
    }

    @Override
    public void deleteValueMapping(CenterId centerId, String column, String sourceValue) {
        enumColumn(column);
        valueMappings.delete(centerId, column, LegacyValueParser.toCode(sourceValue));
    }

    private MigrationBatch require(CenterId centerId, UUID batchId) {
        return batches.findById(centerId, batchId).orElseThrow(() -> new BusinessException("MIGRATION_BATCH_NOT_FOUND",
                "Lot de reprise introuvable pour ce centre."));
    }

    private EntityRun run(MigrationEntity entity, String fileName, int total, int created, int updated, List<String> missing,
                          List<String> ignored, List<ValidationIssue> errors, List<ValidationIssue> warnings,
                          boolean dryRun, boolean applied, String user) {
        return new EntityRun(entity.slug(), fileName, total, created, updated, missing, ignored, errors, warnings,
                dryRun, applied, user, now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}

