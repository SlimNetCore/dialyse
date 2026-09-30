package com.hemodialyse.backend.domain.migration.model;

import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Compte rendu d'un passage (vérification ou import) d'un fichier de reprise.
 *
 * @param entity         slug de la donnée reprise
 * @param fileName       nom du fichier reçu
 * @param totalRows      lignes de données non vides
 * @param created        lignes créées (ou à créer en vérification)
 * @param updated        lignes mises à jour — déjà reprises, ou rapprochées d'une donnée existante
 * @param missingColumns clés des colonnes obligatoires absentes
 * @param ignoredColumns en-têtes non reconnus
 * @param errors         anomalies bloquantes, ligne par ligne
 * @param warnings       points d'attention non bloquants (ex. patient rapproché d'un dossier existant)
 * @param dryRun         vérification seule
 * @param applied        données écrites en base
 * @param executedBy     utilisateur
 * @param executedAt     date d'exécution
 */
public record EntityRun(String entity, String fileName, int totalRows, int created, int updated,
                        List<String> missingColumns, List<String> ignoredColumns,
                        List<ValidationIssue> errors, List<ValidationIssue> warnings,
                        boolean dryRun, boolean applied, String executedBy, OffsetDateTime executedAt) {

    public EntityRun {
        missingColumns = missingColumns == null ? List.of() : List.copyOf(missingColumns);
        ignoredColumns = ignoredColumns == null ? List.of() : List.copyOf(ignoredColumns);
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean isValid() {
        return missingColumns.isEmpty() && errors.isEmpty() && totalRows > 0;
    }
}

