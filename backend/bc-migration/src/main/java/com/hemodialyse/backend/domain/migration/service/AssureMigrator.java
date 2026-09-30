package com.hemodialyse.backend.domain.migration.service;

import com.hemodialyse.backend.domain.assure.model.Assure;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.domain.migration.model.IdMapping;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.hemodialyse.backend.domain.migration.service.EntityMigrator.issue;

/**
 * Reprise des assurés. Le n° d'assurance est unique sur toute la plateforme : un n° déjà rattaché à un autre
 * centre est refusé ; un n° déjà présent dans le centre est rapproché (mis à jour, jamais dupliqué).
 */
public class AssureMigrator implements EntityMigrator {

    private final AssureRepositoryPort assures;

    public AssureMigrator(AssureRepositoryPort assures) {
        this.assures = assures;
    }

    /**
     * Seules les valeurs renseignées dans le fichier sont reportées : une reprise n'efface jamais une donnée.
     */
    private static void apply(Assure assure, Row row, Context ctx) {
        assure.setNumeroAssurance(row.get("numeroAssurance"));
        assure.setCenterId(ctx.centerId().value());
        assure.setNom(row.get("nom"));
        if (row.get("prenom") != null) assure.setPrenom(row.get("prenom"));
        if (row.get("sexe") != null) assure.setSexe(row.get("sexe"));
        if (row.get("dateNaissance") != null) assure.setDateNaissance(LocalDate.parse(row.get("dateNaissance")));
        if (row.get("telPersonnel") != null) assure.setTelPersonnel(row.get("telPersonnel"));
        if (row.get("telMobile") != null) assure.setTelMobile(row.get("telMobile"));
        if (row.get("telBureau") != null) assure.setTelBureau(row.get("telBureau"));
        if (row.get("adresse") != null) assure.setAdresse(row.get("adresse"));
        if (row.get("groupeSanguin") != null) assure.setGroupeSanguin(row.get("groupeSanguin"));
        if (assure.getCreatedAt() == null) assure.setCreatedAt(OffsetDateTime.now());
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
            Optional<Assure> existing = assures.findByNumeroAssurance(numero);
            if (existing.isPresent() && existing.get().getCenterId() != null
                    && !existing.get().getCenterId().equals(ctx.centerId().value())) {
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
            Assure assure = plan.existing() != null ? plan.existing() : new Assure();
            apply(assure, plan.row(), ctx);
            assures.save(assure);
            IdMapping.Operation operation = mapped.containsKey(plan.legacyId()) ? IdMapping.Operation.UPDATED : plan.operation();
            ctx.ids().save(ctx.centerId(), ctx.batch().getId(),
                    new IdMapping(entity(), plan.legacyId(), plan.numero(), operation));
        }
        return new Outcome(created, updated, errors, warnings);
    }

    private record Plan(Row row, String legacyId, String numero, Assure existing, IdMapping.Operation operation) {
    }
}

