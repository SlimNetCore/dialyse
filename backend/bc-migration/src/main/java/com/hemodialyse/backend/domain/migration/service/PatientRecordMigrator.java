package com.hemodialyse.backend.domain.migration.service;

import com.hemodialyse.backend.domain.migration.model.IdMapping;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.port.HistoricalRecordPort;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.hemodialyse.backend.domain.migration.service.EntityMigrator.issue;

/**
 * Reprise générique d'une donnée historique rattachée à un patient, selon ses {@link RecordRules} :
 * <ul>
 *   <li>le patient est désigné par son identifiant d'origine (il doit avoir été repris) ;</li>
 *   <li>les lignes antérieures à la période reprise (N dernières années) sont ignorées, avec un avertissement ;</li>
 *   <li>une ligne déjà présente (même clé métier) est mise à jour, ou conservée si la donnée ne l'autorise pas ;</li>
 *   <li>rien n'est écrit tant que le fichier contient une anomalie.</li>
 * </ul>
 */
public class PatientRecordMigrator implements EntityMigrator {

    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RecordRules rules;
    private final HistoricalRecordPort records;

    public PatientRecordMigrator(RecordRules rules, HistoricalRecordPort records) {
        this.rules = rules;
        this.records = records;
    }

    @Override
    public MigrationEntity entity() {
        return rules.entity();
    }

    @Override
    public Outcome migrate(Context ctx, List<Row> rows, boolean write) {
        CenterId center = ctx.centerId();
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        Map<String, String> patients = ctx.ids().findAll(center, MigrationEntity.PATIENTS);
        LocalDate periodStart = ctx.batch().getDateDebutReprise();
        Map<String, Integer> seen = new HashMap<>();
        List<Plan> plans = new ArrayList<>();
        int kept = 0;
        rules.begin(center);

        for (Row row : rows) {
            int errorsBefore = errors.size();
            String patientLegacy = row.get("patient");
            String patientTarget = patients.get(patientLegacy);
            if (patientTarget == null) {
                errors.add(issue(row.line(), "patient", "PATIENT_NOT_MIGRATED",
                        "Patient « " + patientLegacy + " » inconnu : importez d'abord le fichier des patients.",
                        Map.of("value", patientLegacy)));
                continue;
            }
            UUID patientId = UUID.fromString(patientTarget);

            String periodField = rules.periodField();
            LocalDate periodDate = periodField == null ? null : RowValues.date(row, periodField);
            if (periodStart != null && periodDate != null && periodDate.isBefore(periodStart)) {
                warnings.add(issue(row.line(), periodField, "OUT_OF_PERIOD",
                        "Ligne antérieure à la période reprise (depuis le " + periodStart.format(FR) + ") : ignorée.",
                        Map.of("date", periodStart.format(FR))));
                continue;
            }

            rules.check(row, errors, warnings);
            Map<String, Object> key = new LinkedHashMap<>(rules.key(row));
            String keyText = key.values().stream().map(v -> v == null ? "" : v.toString()).collect(Collectors.joining("|"));
            Integer first = seen.putIfAbsent(patientId + "|" + keyText.toLowerCase(), row.line());
            if (first != null) {
                errors.add(issue(row.line(), key.isEmpty() ? "patient" : firstKeyColumn(row), "DUPLICATE_IN_FILE",
                        "Doublon : même patient" + (key.isEmpty() ? "" : " et même date / clé") + " qu'à la ligne " + first + ".",
                        Map.of("firstRow", String.valueOf(first))));
            }
            if (errors.size() > errorsBefore) continue;

            key.put("patientId", patientId);
            Optional<UUID> existing = records.findExisting(center, entity(), key);
            if (existing.isPresent() && !rules.updatable()) {
                warnings.add(issue(row.line(), "patient", "ALREADY_PRESENT_KEPT",
                        "Déjà présente dans la plateforme : conservée telle quelle.", Map.of()));
                continue;
            }
            String legacy = row.get("legacyId") != null ? row.get("legacyId")
                    : patientLegacy + (keyText.isEmpty() ? "" : "|" + keyText);
            plans.add(new Plan(row, legacy, patientId, existing.orElse(null)));
        }

        int created = (int) plans.stream().filter(p -> p.existingId() == null).count();
        int updated = plans.size() - created;
        if (!errors.isEmpty() || !write) return new Outcome(created, updated, errors, warnings);

        for (Plan plan : plans) {
            Map<String, Object> values = new LinkedHashMap<>();
            rules.values(plan.row()).forEach((k, v) -> {
                if (v != null) values.put(k, v);
            });
            UUID id;
            if (plan.existingId() == null) {
                values.put("patientId", plan.patientId());
                id = records.insert(center, entity(), values);
            } else {
                id = plan.existingId();
                records.update(center, entity(), id, values);
            }
            ctx.ids().save(center, ctx.batch().getId(), new IdMapping(entity(), plan.legacyId(), id.toString(),
                    plan.existingId() == null ? IdMapping.Operation.CREATED : IdMapping.Operation.UPDATED));
        }
        return new Outcome(created, updated, errors, warnings);
    }

    private String firstKeyColumn(Row row) {
        return entity().columns().stream()
                .map(c -> c.key())
                .filter(k -> !k.equals("legacyId") && !k.equals("patient") && row.get(k) != null)
                .findFirst().orElse("patient");
    }

    private record Plan(Row row, String legacyId, UUID patientId, UUID existingId) {
    }
}



