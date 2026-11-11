package com.hemodialyse.backend.infrastructure.migration.service;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures.AssureConnu;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAffectations;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAffectations.AffectationConnue;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAffectations.ReportDeLAffectation;
import com.hemodialyse.backend.infrastructure.migration.model.IdMapping;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationEntity;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.hemodialyse.backend.infrastructure.migration.service.EntityMigrator.issue;

/**
 * Reprise de l'historique des affectations assuré ↔ patient (qui était l'assuré ouvrant droit, et quand).
 * Le patient est désigné par son identifiant d'origine : le fichier des patients doit être importé avant.
 */
public class AffectationMigrator implements EntityMigrator {

    private final RepriseDesAffectations affectations;
    private final RepriseDesAssures assures;

    public AffectationMigrator(RepriseDesAffectations affectations, RepriseDesAssures assures) {
        this.affectations = affectations;
        this.assures = assures;
    }

    private static LocalDate date(String iso) {
        return iso == null ? null : LocalDate.parse(iso);
    }

    @Override
    public MigrationEntity entity() {
        return MigrationEntity.AFFECTATIONS;
    }

    @Override
    public Outcome migrate(Context ctx, List<Row> rows, boolean write) {
        CenterId center = ctx.centerId();
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        Map<String, String> migratedPatients = ctx.ids().findAll(center, MigrationEntity.PATIENTS);
        Map<String, String> mapped = ctx.ids().findAll(center, entity());
        Map<String, Integer> seenKeys = new HashMap<>();
        Map<UUID, Integer> primaryByPatient = new HashMap<>();
        Map<UUID, List<AffectationConnue>> histories = new HashMap<>();
        List<Plan> plans = new ArrayList<>();

        for (Row row : rows) {
            int errorsBefore = errors.size();
            String patientLegacy = row.get("patient");
            String numero = row.get("assureNumeroAssurance");
            LocalDate debut = date(row.get("dateDebut"));
            LocalDate fin = date(row.get("dateFin"));
            boolean principale = Boolean.parseBoolean(row.get("principale"));
            String legacy = row.get("legacyId") != null ? row.get("legacyId")
                    : patientLegacy + "|" + numero.toUpperCase() + "|" + (debut == null ? "" : debut);
            AssureMigrator.duplicate(seenKeys, legacy.toLowerCase(), row, "legacyId", "affectation", errors);

            UUID patientId = migratedPatients.containsKey(patientLegacy) ? UUID.fromString(migratedPatients.get(patientLegacy)) : null;
            if (patientId == null) {
                errors.add(issue(row.line(), "patient", "PATIENT_NOT_MIGRATED",
                        "Patient « " + patientLegacy + " » inconnu : importez d'abord le fichier des patients.",
                        Map.of("value", patientLegacy)));
            }
            Optional<AssureConnu> assure = assures.trouverSansCasse(numero);
            if (assure.isEmpty() || !center.value().equals(assure.get().centre())) {
                errors.add(issue(row.line(), "assureNumeroAssurance", "ASSURE_NOT_FOUND",
                        "Assuré « " + numero + " » introuvable dans le centre : importez d'abord le fichier des assurés.",
                        Map.of("value", numero)));
            }
            if (debut != null && fin != null && fin.isBefore(debut)) {
                errors.add(issue(row.line(), "dateFin", "INCONSISTENT_DATES", "La date de fin précède la date de début.", Map.of()));
            }
            if (principale && fin != null && fin.isBefore(LocalDate.now())) {
                errors.add(issue(row.line(), "principale", "PRIMARY_ENDED",
                        "Une affectation principale ne peut pas être terminée : laissez la date de fin vide.", Map.of()));
            }
            if (principale && patientId != null) {
                Integer first = primaryByPatient.putIfAbsent(patientId, row.line());
                if (first != null) {
                    errors.add(issue(row.line(), "principale", "DUPLICATE_PRIMARY",
                            "Ce patient a déjà une affectation principale à la ligne " + first + ".",
                            Map.of("firstRow", String.valueOf(first))));
                }
            }
            if (errors.size() > errorsBefore) continue;

            // Déjà reprise, ou déjà présente dans l'historique (même assuré, même date de début) : mise à jour.
            String canonique = assure.get().numeroAssurance();
            UUID existingId = mapped.containsKey(legacy) ? UUID.fromString(mapped.get(legacy)) : null;
            if (existingId == null) {
                existingId = histories.computeIfAbsent(patientId, id -> affectations.historique(center.value(), id)).stream()
                        .filter(a -> canonique.equalsIgnoreCase(a.numeroAssurance()) && Objects.equals(debut, a.dateDebut()))
                        .map(AffectationConnue::id)
                        .findFirst().orElse(null);
            }
            IdMapping.Operation operation = mapped.containsKey(legacy) || existingId == null
                    ? IdMapping.Operation.CREATED : IdMapping.Operation.UPDATED;
            plans.add(new Plan(row, legacy, patientId, canonique, existingId, operation));
        }

        int created = (int) plans.stream().filter(p -> p.existingId() == null).count();
        int updated = plans.size() - created;
        if (!errors.isEmpty() || !write) return new Outcome(created, updated, errors, warnings);

        for (Plan plan : plans) {
            Row row = plan.row();
            boolean principale = Boolean.parseBoolean(row.get("principale"));
            UUID id = affectations.reprendre(center.value(), plan.patientId(), plan.existingId(),
                    new ReportDeLAffectation(plan.assure(), principale, date(row.get("dateDebut")), date(row.get("dateFin"))));
            ctx.ids().save(center, ctx.batch().getId(), new IdMapping(entity(), plan.legacyId(), id.toString(), plan.operation()));
        }
        return new Outcome(created, updated, errors, warnings);
    }

    /** {@code assure} : numéro canonique de l'assuré (celui de la plateforme, quelle que soit la casse saisie). */
    private record Plan(Row row, String legacyId, UUID patientId, String assure, UUID existingId, IdMapping.Operation operation) {
    }
}

