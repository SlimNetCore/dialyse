package com.hemodialyse.backend.domain.referential.admin.model;

import java.util.List;

/**
 * Compte rendu d'un import : ce qui manque (colonnes), ce qui est faux (lignes) et ce qui sera / a été fait.
 *
 * @param kind           slug du référentiel importé
 * @param totalRows      lignes de données non vides lues
 * @param created        lignes créées (ou à créer en vérification seule)
 * @param updated        lignes mises à jour, reconnues par leur clé (ou à mettre à jour)
 * @param missingColumns clés des champs obligatoires absents des en-têtes
 * @param ignoredColumns en-têtes non reconnus (ignorés)
 * @param errors         anomalies bloquantes, ligne par ligne
 * @param applied        {@code true} si les données ont été écrites en base
 */
public record ImportReport(String kind, int totalRows, int created, int updated,
                           List<String> missingColumns, List<String> ignoredColumns,
                           List<ValidationIssue> errors, boolean applied) {

    public ImportReport {
        missingColumns = missingColumns == null ? List.of() : List.copyOf(missingColumns);
        ignoredColumns = ignoredColumns == null ? List.of() : List.copyOf(ignoredColumns);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Le fichier peut être importé tel quel.
     */
    public boolean isValid() {
        return missingColumns.isEmpty() && errors.isEmpty() && totalRows > 0;
    }
}

