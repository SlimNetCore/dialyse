package com.hemodialyse.backend.domain.referential.admin.service;

import com.hemodialyse.backend.domain.referential.admin.model.ImportReport;
import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialField;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialValidationException;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.referential.admin.port.ReferentialAdminRepositoryPort;
import com.hemodialyse.backend.domain.referential.admin.port.ReferentialAdminUseCase;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain Service — administration des référentiels d'un centre.
 * <p>
 * Pure domain class (aucune dépendance Spring/JPA — AGENTS.md §3), câblée dans {@code DomainServiceConfig}.
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

    private final ReferentialAdminRepositoryPort repo;
    private final ReferentialValuesValidator validator = new ReferentialValuesValidator();

    public ReferentialAdminDomainService(ReferentialAdminRepositoryPort repo) {
        this.repo = repo;
    }

    private static String naturalKeyLabel(ReferentialKind kind) {
        return String.join(" + ", kind.naturalKey().stream()
                .map(k -> kind.field(k).map(f -> f.label().toLowerCase(java.util.Locale.ROOT)).orElse(k))
                .toList());
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
    public PagedResult<ReferentialEntry> list(CenterId centerId, ReferentialKind kind, String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        String term = search == null || search.isBlank() ? null : search.trim();
        return repo.findPaged(centerId, kind, term, safePage, safeSize);
    }

    @Override
    public ReferentialEntry create(CenterId centerId, ReferentialKind kind, Map<String, String> values) {
        Map<String, String> normalized = validateForSave(centerId, kind, values, null);
        UUID id = repo.insert(centerId, kind, normalized);
        return repo.findById(centerId, kind, id).orElseThrow();
    }

    @Override
    public ReferentialEntry update(CenterId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        requireExisting(centerId, kind, id);
        Map<String, String> normalized = validateForSave(centerId, kind, values, id);
        repo.update(centerId, kind, id, normalized);
        return repo.findById(centerId, kind, id).orElseThrow();
    }

    @Override
    public void delete(CenterId centerId, ReferentialKind kind, UUID id) {
        requireExisting(centerId, kind, id);
        long usages = repo.countUsages(centerId, kind, id);
        if (usages > 0) {
            throw new BusinessException("REFERENTIAL_IN_USE",
                    "Suppression impossible : cet élément de « " + kind.label() + " » est encore utilisé ("
                            + usages + " référence(s) : patients, prises en charge, séances ou autres référentiels).");
        }
        repo.delete(centerId, kind, id);
    }

    @Override
    public ImportReport importEntries(CenterId centerId, ReferentialKind kind, ImportTable table, boolean dryRun) {
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

        // 2. Lignes → valeurs validées, doublons, création ou mise à jour.
        Matching matching = new Matching(centerId);
        Map<String, UUID> existing = matching.index(kind).byKey();
        Map<String, Integer> seenInFile = new HashMap<>();
        List<ValidationIssue> errors = new ArrayList<>();
        List<PlannedWrite> writes = new ArrayList<>();

        for (ImportTable.Row row : dataRows) {
            Map<String, String> raw = new HashMap<>();
            columns.forEach((index, field) -> raw.put(field.key(), row.cell(index)));

            ReferentialValuesValidator.Result result = validator.validate(kind, raw, matching::resolve);
            if (!result.valid()) {
                result.issues().forEach(issue -> errors.add(issue.atRow(row.lineNumber())));
                continue;
            }
            String key = kind.naturalKeyOf(result.values());
            Integer firstRow = seenInFile.putIfAbsent(key, row.lineNumber());
            if (firstRow != null) {
                errors.add(new ValidationIssue(row.lineNumber(), kind.naturalKey().getFirst(), "DUPLICATE_IN_FILE",
                        "Doublon : même " + naturalKeyLabel(kind) + " qu'à la ligne " + firstRow + ".",
                        Map.of("firstRow", String.valueOf(firstRow))));
                continue;
            }
            writes.add(new PlannedWrite(existing.get(key), result.values()));
        }

        int toUpdate = (int) writes.stream().filter(w -> w.existingId() != null).count();
        int toCreate = writes.size() - toUpdate;
        if (!errors.isEmpty()) {
            return new ImportReport(kind.slug(), dataRows.size(), toCreate, toUpdate, List.of(), ignored,
                    truncate(errors), false);
        }
        if (dryRun) {
            return new ImportReport(kind.slug(), dataRows.size(), toCreate, toUpdate, List.of(), ignored, List.of(), false);
        }

        // 3. Écriture (tout ou rien : la transaction est portée par l'appelant).
        for (PlannedWrite write : writes) {
            if (write.existingId() == null) repo.insert(centerId, kind, write.values());
            else repo.update(centerId, kind, write.existingId(), write.values());
        }
        return new ImportReport(kind.slug(), dataRows.size(), toCreate, toUpdate, List.of(), ignored, List.of(), true);
    }

    private Map<String, String> validateForSave(CenterId centerId, ReferentialKind kind, Map<String, String> values,
                                                UUID currentId) {
        Matching matching = new Matching(centerId);
        ReferentialValuesValidator.Result result = validator.validate(kind, values, matching::resolve);
        if (!result.valid()) {
            throw new ReferentialValidationException(result.issues());
        }
        UUID owner = matching.index(kind).byKey().get(kind.naturalKeyOf(result.values()));
        if (owner != null && !owner.equals(currentId)) {
            throw new ReferentialValidationException(List.of(new ValidationIssue(0, kind.naturalKey().getFirst(),
                    "ALREADY_EXISTS", "Un élément de « " + kind.label() + " » avec ce " + naturalKeyLabel(kind)
                    + " existe déjà.", Map.of())));
        }
        return result.values();
    }

    private void requireExisting(CenterId centerId, ReferentialKind kind, UUID id) {
        if (repo.findById(centerId, kind, id).isEmpty()) {
            throw new BusinessException("REFERENTIAL_NOT_FOUND",
                    "Élément introuvable dans « " + kind.label() + " » pour ce centre.");
        }
    }

    private record PlannedWrite(UUID existingId, Map<String, String> values) {
    }

    private record KeyIndex(Map<String, UUID> byKey, java.util.Set<UUID> ids) {
    }

    /**
     * Index de clés naturelles chargés à la demande, une fois par opération, pour le centre courant.
     */
    private final class Matching {
        private final CenterId centerId;
        private final Map<ReferentialKind, KeyIndex> indexes = new EnumMap<>(ReferentialKind.class);

        private Matching(CenterId centerId) {
            this.centerId = centerId;
        }

        KeyIndex index(ReferentialKind kind) {
            return indexes.computeIfAbsent(kind, k -> {
                Map<String, UUID> byKey = new HashMap<>();
                java.util.Set<UUID> ids = new java.util.HashSet<>();
                for (ReferentialEntry entry : repo.findAllForMatching(centerId, k)) {
                    byKey.putIfAbsent(k.naturalKeyOf(entry.values()), entry.id());
                    ids.add(entry.id());
                }
                return new KeyIndex(byKey, ids);
            });
        }

        /**
         * Identifiant (depuis un formulaire) ou code (depuis un fichier) → identifiant existant du centre.
         */
        Optional<UUID> resolve(ReferentialKind target, String idOrCode) {
            KeyIndex index = index(target);
            try {
                UUID id = UUID.fromString(idOrCode.trim());
                if (index.ids().contains(id)) return Optional.of(id);
            } catch (IllegalArgumentException notAnId) {
                // valeur saisie : c'est un code
            }
            Map<String, String> probe = new HashMap<>();
            probe.put(target.naturalKey().getFirst(), idOrCode);
            return Optional.ofNullable(index.byKey().get(target.naturalKeyOf(probe)));
        }
    }
}

