package com.hemodialyse.backend.infrastructure.referential.admin.service;

import com.hemodialyse.backend.infrastructure.referential.admin.model.ImportReport;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ImportTable;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialField;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegate;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminUseCase;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Façade fine de l'administration des référentiels d'un centre (PL11-6, ADR 0161) : elle normalise la pagination, lit le fichier
 * d'import (en-têtes, bornes, compte rendu) et distribue tout le reste au contexte propriétaire par le SPI
 * {@link ReferentialAdminDelegate}. Elle ne connaît aucune table, aucune règle de référentiel.
 * <p>
 * La transaction (import tout-ou-rien) est portée par le service applicatif.
 */
public class ReferentialAdminDomainService implements ReferentialAdminUseCase {

    /**
     * Au-delà, l'import doit être découpé (protège le serveur et garde un compte rendu lisible).
     */
    public static final int MAX_IMPORT_ROWS = 5000;
    /**
     * Nombre maximal d'anomalies renvoyées dans un compte rendu.
     */
    public static final int MAX_REPORTED_ERRORS = 500;
    private static final int MAX_PAGE_SIZE = 100;

    private final ReferentialAdminDelegate delegate;

    /**
     * @param delegate gestionnaire des référentiels, assemblé depuis les contextes propriétaires
     */
    public ReferentialAdminDomainService(ReferentialAdminDelegate delegate) {
        this.delegate = delegate;
    }

    private static ImportReport rejected(ReferentialKind kind, int rows, List<String> missing, List<String> ignored,
                                         List<ValidationIssue> errors) {
        return new ImportReport(kind.slug(), rows, 0, 0, missing, ignored, errors, false);
    }

    private static ValidationIssue fileIssue(String code, String message, Map<String, String> params) {
        return new ValidationIssue(0, null, code, message, params);
    }

    private static List<ValidationIssue> truncate(List<ValidationIssue> errors) {
        if (errors.size() <= MAX_REPORTED_ERRORS) return errors;
        List<ValidationIssue> head = new ArrayList<>(errors.subList(0, MAX_REPORTED_ERRORS));
        head.add(fileIssue("TOO_MANY_ERRORS", (errors.size() - MAX_REPORTED_ERRORS)
                        + " autre(s) anomalie(s) non affichée(s) : corrigez les premières puis relancez la vérification.",
                Map.of("count", String.valueOf(errors.size() - MAX_REPORTED_ERRORS))));
        return head;
    }

    @Override
    public PagedResult<ReferentialEntry> list(CentreId centerId, ReferentialKind kind, String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        String term = search == null || search.isBlank() ? null : search.trim();
        return delegate.list(centerId, kind, term, safePage, safeSize);
    }

    @Override
    public ReferentialEntry create(CentreId centerId, ReferentialKind kind, Map<String, String> values) {
        return delegate.create(centerId, kind, values);
    }

    @Override
    public ReferentialEntry update(CentreId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        return delegate.update(centerId, kind, id, values);
    }

    @Override
    public void delete(CentreId centerId, ReferentialKind kind, UUID id) {
        delegate.delete(centerId, kind, id);
    }

    @Override
    public ImportReport importEntries(CentreId centerId, ReferentialKind kind, ImportTable table, boolean dryRun) {
        if (table.headers().stream().allMatch(h -> h == null || h.isBlank())) {
            return rejected(kind, 0, List.of(), List.of(), List.of(fileIssue("EMPTY_FILE",
                    "Le fichier est vide : la première ligne doit contenir les en-têtes de colonnes.", Map.of())));
        }

        // 1. En-têtes → champs.
        Map<Integer, ReferentialField> columns = new LinkedHashMap<>();
        List<String> ignored = new ArrayList<>();
        for (int i = 0; i < table.headers().size(); i++) {
            String header = table.headers().get(i);
            if (header == null || header.isBlank()) continue;
            String normalized = ReferentialField.normalize(header);
            Optional<ReferentialField> field = kind.fields().stream()
                    .filter(f -> f.matchesHeader(normalized))
                    .filter(f -> !columns.containsValue(f))
                    .findFirst();
            if (field.isPresent()) columns.put(i, field.get());
            else ignored.add(header.trim());
        }
        List<String> missing = kind.fields().stream()
                .filter(ReferentialField::requiredColumn)
                .filter(f -> !columns.containsValue(f))
                .map(ReferentialField::key)
                .toList();

        List<ImportTable.Row> dataRows = table.rows().stream().filter(r -> !r.isBlank()).toList();
        if (!missing.isEmpty()) {
            return rejected(kind, dataRows.size(), missing, ignored, List.of());
        }
        if (dataRows.isEmpty()) {
            return rejected(kind, 0, List.of(), ignored, List.of(fileIssue("NO_DATA",
                    "Le fichier ne contient aucune ligne de données sous les en-têtes.", Map.of())));
        }
        if (dataRows.size() > MAX_IMPORT_ROWS) {
            return rejected(kind, dataRows.size(), List.of(), ignored, List.of(fileIssue("TOO_MANY_ROWS",
                    "Le fichier contient " + dataRows.size() + " lignes : le maximum est " + MAX_IMPORT_ROWS
                            + " par import. Découpez-le en plusieurs fichiers.",
                    Map.of("count", String.valueOf(dataRows.size()), "max", String.valueOf(MAX_IMPORT_ROWS)))));
        }

        // 2. Lignes → contexte propriétaire (contrôle, doublons, écriture tout ou rien).
        List<ReferentialAdminDelegate.Row> rows = new ArrayList<>();
        for (ImportTable.Row row : dataRows) {
            Map<String, String> raw = new HashMap<>();
            columns.forEach((index, field) -> raw.put(field.key(), row.cell(index)));
            rows.add(new ReferentialAdminDelegate.Row(row.lineNumber(), raw));
        }
        ReferentialAdminDelegate.Outcome outcome = delegate.importRows(centerId, kind, rows, dryRun);
        List<ValidationIssue> reported = outcome.errors().isEmpty() ? List.of() : truncate(outcome.errors());
        return new ImportReport(kind.slug(), dataRows.size(), outcome.toCreate(), outcome.toUpdate(), List.of(), ignored,
                reported, outcome.applied());
    }
}
