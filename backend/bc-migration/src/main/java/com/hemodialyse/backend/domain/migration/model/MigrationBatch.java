package com.hemodialyse.backend.domain.migration.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Aggregate root — lot de reprise d'un centre. Un seul lot actif par centre ; tant qu'il est EN_COURS, les
 * fichiers peuvent être vérifiés et importés (autant de fois que nécessaire, la reprise est rejouable).
 */
public class MigrationBatch {

    private UUID id;
    private CenterId centerId;
    private String libelle;
    private String sourceSystem;
    private LocalDate dateDebutReprise;
    private Status status;
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime closedAt;

    public static MigrationBatch ouvrir(CenterId centerId, String libelle, String sourceSystem,
                                        LocalDate dateDebutReprise, String createdBy, OffsetDateTime now) {
        if (libelle == null || libelle.isBlank()) {
            throw new BusinessException("MIGRATION_LABEL_REQUIRED", "Le libellé du lot de reprise est obligatoire.");
        }
        if (dateDebutReprise != null && dateDebutReprise.isAfter(now.toLocalDate())) {
            throw new BusinessException("MIGRATION_START_IN_FUTURE",
                    "La date de début de reprise ne peut pas être dans le futur.");
        }
        MigrationBatch batch = new MigrationBatch();
        batch.id = UUID.randomUUID();
        batch.centerId = centerId;
        batch.libelle = libelle.trim();
        batch.sourceSystem = sourceSystem == null || sourceSystem.isBlank() ? null : sourceSystem.trim();
        batch.dateDebutReprise = dateDebutReprise;
        batch.status = Status.EN_COURS;
        batch.createdBy = createdBy;
        batch.createdAt = now;
        return batch;
    }

    public static MigrationBatch restore(UUID id, CenterId centerId, String libelle, String sourceSystem,
                                         LocalDate dateDebutReprise, Status status, String createdBy,
                                         OffsetDateTime createdAt, OffsetDateTime closedAt) {
        MigrationBatch batch = new MigrationBatch();
        batch.id = id;
        batch.centerId = centerId;
        batch.libelle = libelle;
        batch.sourceSystem = sourceSystem;
        batch.dateDebutReprise = dateDebutReprise;
        batch.status = status;
        batch.createdBy = createdBy;
        batch.createdAt = createdAt;
        batch.closedAt = closedAt;
        return batch;
    }

    public void ensureOpen() {
        if (status != Status.EN_COURS) {
            throw new BusinessException("MIGRATION_BATCH_CLOSED",
                    "Ce lot de reprise est " + (status == Status.TERMINE ? "terminé" : "annulé") + " : plus aucun import n'est possible.");
        }
    }

    public void terminer(OffsetDateTime now) {
        ensureOpen();
        status = Status.TERMINE;
        closedAt = now;
    }

    public void annuler(OffsetDateTime now) {
        ensureOpen();
        status = Status.ANNULE;
        closedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public CenterId getCenterId() {
        return centerId;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    /**
     * Début de la période reprise (N dernières années) : borne les données historiques des lots suivants.
     */
    public LocalDate getDateDebutReprise() {
        return dateDebutReprise;
    }

    public Status getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public enum Status {
        /**
         * Fichiers en cours de vérification / d'import.
         */
        EN_COURS,
        /**
         * Reprise validée par le centre : plus aucun import possible.
         */
        TERMINE,
        /**
         * Les données créées par le lot ont été supprimées.
         */
        ANNULE
    }
}

