package com.hemodialyse.backend.infrastructure.web.dto.migration;

import com.hemodialyse.backend.domain.migration.model.ColumnDef;
import com.hemodialyse.backend.domain.migration.model.EntityRun;
import com.hemodialyse.backend.domain.migration.model.MigrationBatch;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.port.MigrationUseCase;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO de l'API de reprise des données.
 */
public final class MigrationDtos {

    private MigrationDtos() {
    }

    public record ColumnResponse(String key, String label, String type, boolean requiredColumn,
                                 List<String> allowedValues, String defaultValue, String example) {
        static ColumnResponse from(ColumnDef c) {
            return new ColumnResponse(c.key(), c.label(), c.type().name(), c.requiredColumn(), c.allowedValues(),
                    c.defaultValue(), c.example());
        }
    }

    public record EntityResponse(String slug, String label, int order, List<ColumnResponse> columns) {
        public static EntityResponse from(MigrationEntity e) {
            return new EntityResponse(e.slug(), e.label(), e.order(), e.columns().stream().map(ColumnResponse::from).toList());
        }
    }

    public record BatchResponse(UUID id, String libelle, String sourceSystem, LocalDate dateDebutReprise, String status,
                                String createdBy, OffsetDateTime createdAt, OffsetDateTime closedAt) {
        public static BatchResponse from(MigrationBatch b) {
            return new BatchResponse(b.getId(), b.getLibelle(), b.getSourceSystem(), b.getDateDebutReprise(),
                    b.getStatus().name(), b.getCreatedBy(), b.getCreatedAt(), b.getClosedAt());
        }
    }

    public record RunResponse(String entity, String fileName, int totalRows, int created, int updated,
                              List<String> missingColumns, List<String> ignoredColumns,
                              List<ValidationIssue> errors, List<ValidationIssue> warnings,
                              boolean valid, boolean dryRun, boolean applied, String executedBy,
                              OffsetDateTime executedAt) {
        public static RunResponse from(EntityRun r) {
            return new RunResponse(r.entity(), r.fileName(), r.totalRows(), r.created(), r.updated(), r.missingColumns(),
                    r.ignoredColumns(), r.errors(), r.warnings(), r.isValid(), r.dryRun(), r.applied(), r.executedBy(),
                    r.executedAt());
        }
    }

    public record BatchDetailResponse(BatchResponse batch, List<RunResponse> runs) {
        public static BatchDetailResponse from(MigrationUseCase.BatchDetail d) {
            return new BatchDetailResponse(BatchResponse.from(d.batch()), d.runs().stream().map(RunResponse::from).toList());
        }
    }

    public record OpenBatchRequest(UUID centerId, @NotBlank @Size(max = 200) String libelle,
                                   @Size(max = 100) String sourceSystem, LocalDate dateDebutReprise) {
    }

    public record ValueMappingResponse(String column, String source, String target) {
    }

    public record ValueMappingRequest(UUID centerId, @NotBlank String column, @NotBlank @Size(max = 150) String source,
                                      @NotBlank String target) {
    }
}

