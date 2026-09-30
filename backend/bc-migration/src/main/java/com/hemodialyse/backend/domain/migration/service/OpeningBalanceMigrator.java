package com.hemodialyse.backend.domain.migration.service;

import com.hemodialyse.backend.domain.migration.model.IdMapping;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.port.OpeningBalancePort;
import com.hemodialyse.backend.domain.migration.port.OpeningBalancePort.OpeningInvoice;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.hemodialyse.backend.domain.migration.service.EntityMigrator.issue;
import static com.hemodialyse.backend.domain.migration.service.RowValues.date;
import static com.hemodialyse.backend.domain.migration.service.RowValues.decimal;

/**
 * Reprise des soldes d'ouverture : seules les factures de l'ancien logiciel restant à payer sont reprises, avec
 * le montant déjà encaissé. Les factures soldées sont ignorées (rien à recouvrer). Une facture de reprise déjà
 * encaissée dans la plateforme n'est plus modifiable par la reprise.
 */
public class OpeningBalanceMigrator implements EntityMigrator {

    private final OpeningBalancePort balances;
    private final PatientRepositoryPort patients;

    public OpeningBalanceMigrator(OpeningBalancePort balances, PatientRepositoryPort patients) {
        this.balances = balances;
        this.patients = patients;
    }

    @Override
    public MigrationEntity entity() {
        return MigrationEntity.SOLDES_OUVERTURE;
    }

    @Override
    public Outcome migrate(Context ctx, List<Row> rows, boolean write) {
        CenterId center = ctx.centerId();
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        Map<String, String> migratedPatients = ctx.ids().findAll(center, MigrationEntity.PATIENTS);
        Map<String, Integer> seen = new HashMap<>();
        List<Plan> plans = new ArrayList<>();

        for (Row row : rows) {
            int errorsBefore = errors.size();
            String legacyNumber = row.get("numeroFacture");
            AssureMigrator.duplicate(seen, legacyNumber.toLowerCase(Locale.ROOT), row, "numeroFacture", "n° de facture", errors);

            String patientTarget = migratedPatients.get(row.get("patient"));
            Optional<Patient> patient = patientTarget == null ? Optional.empty()
                    : patients.findById(PatientId.of(UUID.fromString(patientTarget)), center);
            if (patient.isEmpty()) {
                errors.add(issue(row.line(), "patient", "PATIENT_NOT_MIGRATED",
                        "Patient « " + row.get("patient") + " » inconnu : importez d'abord le fichier des patients.",
                        Map.of("value", row.get("patient"))));
            }

            BigDecimal montant = decimal(row, "montantTtc");
            BigDecimal regle = decimal(row, "montantRegle");
            if (montant.signum() == 0) {
                errors.add(issue(row.line(), "montantTtc", "INVALID_AMOUNT", "Le montant de la facture doit être positif.", Map.of()));
            } else if (regle.compareTo(montant) > 0) {
                errors.add(issue(row.line(), "montantRegle", "OVERPAID",
                        "Le montant réglé dépasse le montant de la facture.", Map.of()));
            }
            LocalDate dateFacture = date(row, "dateFacture");
            LocalDate debut = date(row, "periodeDebut") != null ? date(row, "periodeDebut") : dateFacture;
            LocalDate fin = date(row, "periodeFin") != null ? date(row, "periodeFin") : dateFacture;
            if (fin.isBefore(debut)) {
                errors.add(issue(row.line(), "periodeFin", "INCONSISTENT_DATES", "La fin de période précède son début.", Map.of()));
            }
            if (dateFacture.isAfter(LocalDate.now())) {
                errors.add(issue(row.line(), "dateFacture", "DATE_IN_FUTURE", "La date est dans le futur.", Map.of()));
            }
            if (errors.size() > errorsBefore) continue;

            if (montant.compareTo(regle) == 0) {
                warnings.add(issue(row.line(), "montantRegle", "FULLY_PAID",
                        "Facture entièrement réglée dans l'ancien logiciel : rien à reprendre, ligne ignorée.", Map.of()));
                continue;
            }

            String numero = OpeningBalancePort.NUMBER_PREFIX + legacyNumber.trim();
            Optional<UUID> existing = balances.findByNumero(center, numero);
            if (existing.isPresent() && balances.countPaymentsOutsideMigration(center, existing.get()) > 0) {
                errors.add(issue(row.line(), "numeroFacture", "PAID_IN_PLATFORM",
                        "Cette facture a déjà été encaissée dans la plateforme : elle ne peut plus être modifiée par la reprise.",
                        Map.of("value", legacyNumber)));
                continue;
            }
            Patient p = patient.get();
            LocalDate dateReglement = date(row, "dateDernierReglement") != null ? date(row, "dateDernierReglement") : dateFacture;
            OpeningInvoice invoice = new OpeningInvoice(p.getId().value(), numero, dateFacture, debut, fin, montant, regle,
                    dateReglement, row.get("libelle") != null ? row.get("libelle") : "Solde d'ouverture — reprise " + legacyNumber.trim(),
                    p.getCodePatient(), (p.getNom() + " " + p.getPrenom()).trim(), p.getEtatPatient(),
                    p.getNumeroAssurance() != null ? p.getNumeroAssurance().value() : null, p.getCentrePayeurId());
            plans.add(new Plan(legacyNumber.trim(), invoice, existing.orElse(null)));
        }

        int created = (int) plans.stream().filter(p -> p.existingId() == null).count();
        int updated = plans.size() - created;
        if (!errors.isEmpty() || !write) return new Outcome(created, updated, errors, warnings);

        for (Plan plan : plans) {
            UUID id;
            if (plan.existingId() == null) {
                id = balances.create(center, plan.invoice());
            } else {
                id = plan.existingId();
                balances.replace(center, id, plan.invoice());
            }
            ctx.ids().save(center, ctx.batch().getId(), new IdMapping(entity(), plan.legacyId(), id.toString(),
                    plan.existingId() == null ? IdMapping.Operation.CREATED : IdMapping.Operation.UPDATED));
        }
        return new Outcome(created, updated, errors, warnings);
    }

    private record Plan(String legacyId, OpeningInvoice invoice, UUID existingId) {
    }
}

