package com.hemodialyse.backend.infrastructure.migration.service;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures.AssureConnu;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures.ReportDeLAssure;
import com.hemodialyse.backend.infrastructure.migration.model.IdMapping;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationEntity;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ValidationIssue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.hemodialyse.backend.infrastructure.migration.service.EntityMigrator.issue;

/**
 * Reprise des assurés. Le n° d'assurance est unique sur toute la plateforme : un n° déjà rattaché à un autre
 * centre est refusé ; un n° déjà présent dans le centre est rapproché (mis à jour, jamais dupliqué).
 */
public class AssureMigrator implements EntityMigrator {

    private final RepriseDesAssures assures;

    public AssureMigrator(RepriseDesAssures assures) {
        this.assures = assures;
    }

    /**
     * Valeurs du fichier ; les valeurs vides restent nulles, donc ne sont pas reportées : une reprise n'efface jamais une donnée
     * (la fusion est une règle du contexte Patient et couverture).
     */
    private static ReportDeLAssure report(Row row) {
        return new ReportDeLAssure(row.get("numeroAssurance"), row.get("nom"), row.get("prenom"), row.get("sexe"),
                row.get("dateNaissance") == null ? null : LocalDate.parse(row.get("dateNaissance")), row.get("telPersonnel"),
                row.get("telMobile"), row.get("telBureau"), row.get("adresse"), row.get("groupeSanguin"));
    }

    static void duplicate(Map<String, Integer> seen, String key, Row row, String field, String label,
                          List<ValidationIssue> errors) {
        Integer first = seen.putIfAbsent(key, row.line());
        if (first != null) {
            errors.add(issue(row.line(), field, "DUPLICATE_IN_FILE",
                    "Doublon : même " + label + " qu'à la ligne " + first + ".", Map.of("firstRow", String.valueOf(first))));
        }
    }

    @Override
    public MigrationEntity entity() {
        return MigrationEntity.ASSURES;
    }

    @Override
    public Outcome migrate(Context ctx, List<Row> rows, boolean write) {
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        Map<String, String> mapped = ctx.ids().findAll(ctx.centerId(), entity());
        Map<String, Integer> seenLegacy = new HashMap<>();
        Map<String, Integer> seenNumero = new HashMap<>();
        List<Plan> plans = new ArrayList<>();

        for (Row row : rows) {
            String legacy = row.get("legacyId");
            String numero = row.get("numeroAssurance");
            int errorsBefore = errors.size();
            duplicate(seenLegacy, legacy, row, "legacyId", "identifiant d'origine", errors);
            duplicate(seenNumero, numero.toLowerCase(Locale.ROOT), row, "numeroAssurance", "n° d'assurance", errors);

            String alreadyMigrated = mapped.get(legacy);
            if (alreadyMigrated != null && !alreadyMigrated.equalsIgnoreCase(numero)) {
                errors.add(issue(row.line(), "numeroAssurance", "LEGACY_ID_CONFLICT",
                        "L'identifiant d'origine « " + legacy + " » a déjà été repris avec le n° « " + alreadyMigrated + " ».",
                        Map.of("value", alreadyMigrated)));
            }
            Optional<AssureConnu> existing = assures.trouver(numero);
            if (existing.isPresent() && existing.get().centre() != null
                    && !existing.get().centre().equals(ctx.centerId().value())) {
                errors.add(issue(row.line(), "numeroAssurance", "OTHER_CENTER",
                        "Le n° d'assurance « " + numero + " » est déjà rattaché à un autre centre.", Map.of("value", numero)));
            }
            if (errors.size() > errorsBefore) continue;

            IdMapping.Operation operation;
            if (alreadyMigrated != null) {
                operation = IdMapping.Operation.UPDATED;
            } else if (existing.isPresent()) {
                operation = IdMapping.Operation.UPDATED;
                warnings.add(issue(row.line(), "numeroAssurance", "MATCHED_EXISTING",
                        "Assuré déjà présent (n° « " + numero + " ») : il sera mis à jour et rapproché.", Map.of("value", numero)));
            } else {
                operation = IdMapping.Operation.CREATED;
            }
            plans.add(new Plan(row, legacy, numero, existing.orElse(null), operation));
        }

        int created = (int) plans.stream().filter(p -> p.existing() == null).count();
        int updated = plans.size() - created;
        if (!errors.isEmpty() || !write) return new Outcome(created, updated, errors, warnings);

        for (Plan plan : plans) {
            assures.reprendre(ctx.centerId().value(), report(plan.row()));
            IdMapping.Operation operation = mapped.containsKey(plan.legacyId()) ? IdMapping.Operation.UPDATED : plan.operation();
            ctx.ids().save(ctx.centerId(), ctx.batch().getId(),
                    new IdMapping(entity(), plan.legacyId(), plan.numero(), operation));
        }
        return new Outcome(created, updated, errors, warnings);
    }

    private record Plan(Row row, String legacyId, String numero, AssureConnu existing, IdMapping.Operation operation) {
    }
}

