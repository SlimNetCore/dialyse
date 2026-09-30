package com.hemodialyse.backend.domain.migration;

import com.hemodialyse.backend.domain.migration.model.EntityRun;
import com.hemodialyse.backend.domain.migration.model.IdMapping;
import com.hemodialyse.backend.domain.migration.model.MigrationBatch;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.service.EntityMigrator;
import com.hemodialyse.backend.domain.migration.service.HistoryRules;
import com.hemodialyse.backend.domain.migration.service.MigrationDomainService;
import com.hemodialyse.backend.domain.migration.service.OpeningBalanceMigrator;
import com.hemodialyse.backend.domain.migration.service.PatientRecordMigrator;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reprise de l'historique patient (attestations, PEC, dossier, séances…) et des soldes d'ouverture.
 */
class HistoryMigrationTest {

    private static final CenterId CENTER = CenterId.of(UUID.fromString("00000000-0000-0000-0000-00000000000a"));

    private InMemoryMigrationPorts.Ids ids;
    private InMemoryHistoryPorts.Records records;
    private InMemoryHistoryPorts.Balances balances;
    private MigrationDomainService service;
    private MigrationBatch batch;
    private UUID patientId;
    private UUID forfait;

    private static List<String> codes(List<ValidationIssue> issues) {
        return issues.stream().map(i -> i.row() + ":" + i.field() + ":" + i.code()).toList();
    }

    @BeforeEach
    void setUp() {
        ids = new InMemoryMigrationPorts.Ids();
        records = new InMemoryHistoryPorts.Records();
        balances = new InMemoryHistoryPorts.Balances();
        InMemoryMigrationPorts.Patients patients = new InMemoryMigrationPorts.Patients();
        InMemoryMigrationPorts.Referentials referentials = new InMemoryMigrationPorts.Referentials();
        forfait = referentials.add(ReferentialKind.FORFAIT, Map.of("code", "HD-CONV", "libelle", "Hémodialyse"));

        List<EntityMigrator> migrators = new ArrayList<>();
        HistoryRules.all(referentials).forEach(rules -> migrators.add(new PatientRecordMigrator(rules, records)));
        migrators.add(new OpeningBalanceMigrator(balances, patients));
        service = new MigrationDomainService(new InMemoryMigrationPorts.Batches(), ids, new InMemoryMigrationPorts.Values(),
                new InMemoryMigrationPorts.Rollback(), migrators, Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC));

        batch = service.open(CENTER, "Reprise", "Ancien", LocalDate.of(2023, 9, 30), "admin");
        Patient patient = Patient.creer(CENTER, "BENALI", "Karim", "M", LocalDate.of(2019, 1, 2), LocalDate.of(1962, 3, 15),
                new NumeroAssurance("111"), PatientType.NON_VACANCIER);
        patients.save(patient);
        patientId = patient.getId().value();
        ids.save(CENTER, batch.getId(), new IdMapping(MigrationEntity.PATIENTS, "P1", patientId.toString(), IdMapping.Operation.CREATED));
    }

    @Test
    void attestationsAreImportedOnceAndOldOnesIgnored() {
        EntityRun run = importFile(MigrationEntity.ATTESTATIONS, List.of("Patient", "Date de début", "Date de fin"),
                List.of("P1", "01/01/2026", "31/12/2026"),
                List.of("P1", "01/01/2020", "31/12/2020"),
                List.of("P1", "01/01/2025", "31/12/2024"),
                List.of("P9", "01/01/2026", "31/12/2026"));

        assertEquals(List.of("4:dateFin:INCONSISTENT_DATES", "5:patient:PATIENT_NOT_MIGRATED"), codes(run.errors()));
        assertEquals("OUT_OF_PERIOD", run.warnings().getFirst().code());
        assertTrue(records.of(MigrationEntity.ATTESTATIONS).isEmpty());

        EntityRun ok = importFile(MigrationEntity.ATTESTATIONS, List.of("Patient", "Date de début", "Date de fin"),
                List.of("P1", "01/01/2026", "31/12/2026"));
        EntityRun replay = importFile(MigrationEntity.ATTESTATIONS, List.of("Patient", "Date de début", "Date de fin"),
                List.of("P1", "01/01/2026", "30/06/2026"));

        assertTrue(ok.applied());
        assertEquals(1, replay.updated());
        assertEquals(1, records.of(MigrationEntity.ATTESTATIONS).size());
        assertEquals(LocalDate.of(2026, 6, 30), records.of(MigrationEntity.ATTESTATIONS).getFirst().values().get("dateFin"));
        assertEquals(patientId, records.of(MigrationEntity.ATTESTATIONS).getFirst().values().get("patientId"));
    }

    @Test
    void priseEnChargeResolvesForfaitAndDefaultsGrantedValues() {
        EntityRun unknown = importFile(MigrationEntity.PRISES_EN_CHARGE,
                List.of("Patient", "Début demandé", "Fin demandée", "Forfait demandé (code)"),
                List.of("P1", "01/01/2026", "30/06/2026", "INCONNU"));
        assertEquals(List.of("2:forfaitDemande:REFERENCE_NOT_FOUND"), codes(unknown.errors()));

        EntityRun run = importFile(MigrationEntity.PRISES_EN_CHARGE,
                List.of("Patient", "Début demandé", "Fin demandée", "Forfait demandé (code)", "Statut"),
                List.of("P1", "01/01/2026", "30/06/2026", "hd-conv", "Accordée"));

        assertTrue(run.applied(), () -> run.errors().toString());
        Map<String, Object> pec = records.of(MigrationEntity.PRISES_EN_CHARGE).getFirst().values();
        assertEquals("VALIDEE", pec.get("statut"));
        assertEquals(forfait, pec.get("forfaitDemandeId"));
        assertEquals(forfait, pec.get("forfaitEffectifId"));
        assertEquals(LocalDate.of(2026, 1, 1), pec.get("dateDebutEffectif"));
    }

    @Test
    void seancesKeepTheirBillingStateAndExistingOnesAreNeverModified() {
        records.seed(CENTER, MigrationEntity.SEANCES, Map.of("patientId", patientId, "dateSeance", LocalDate.of(2026, 1, 7),
                "statut", "FACTUREE"));

        EntityRun run = importFile(MigrationEntity.SEANCES, List.of("Patient", "Date de séance", "Facturée"),
                List.of("P1", "05/01/2026", "oui"),
                List.of("P1", "07/01/2026", "non"),
                List.of("P1", "09/01/2026", "non"),
                List.of("P1", "05/01/2020", "oui"));

        assertTrue(run.applied(), () -> run.errors().toString());
        assertEquals(2, run.created());
        assertEquals(List.of("ALREADY_PRESENT_KEPT", "OUT_OF_PERIOD"), run.warnings().stream().map(ValidationIssue::code).toList());
        assertEquals(List.of("FACTUREE", "FACTUREE", "SIGNEE"), records.of(MigrationEntity.SEANCES).stream()
                .map(r -> r.values().get("statut")).toList());
    }

    @Test
    void medicalRecordIsOnePerPatientAndAnalysesNeedAResult() {
        EntityRun dossier = importFile(MigrationEntity.DOSSIERS_MEDICAUX,
                List.of("Patient", "Date de mise en dialyse", "Néphropathie initiale"),
                List.of("P1", "15/06/2018", "Diabète"), List.of("P1", "", "Autre"));
        assertEquals(List.of("3:patient:DUPLICATE_IN_FILE"), codes(dossier.errors()));

        EntityRun analyses = importFile(MigrationEntity.ANALYSES, List.of("Patient", "Date de prélèvement", "Hb", "Kt/V"),
                List.of("P1", "05/01/2026", "10,8", "1,3"), List.of("P1", "05/02/2026", "", ""));
        assertEquals(List.of("3:datePrelevement:NO_RESULT"), codes(analyses.errors()));

        EntityRun ok = importFile(MigrationEntity.ANALYSES, List.of("Patient", "Date de prélèvement", "Hb", "Kt/V"),
                List.of("P1", "05/01/2026", "10,8", "1,3"));
        assertTrue(ok.applied());
        assertEquals(new BigDecimal("10.8"), records.of(MigrationEntity.ANALYSES).getFirst().values().get("hbGDl"));
    }

    // ---- Utilitaires -------------------------------------------------------------------------------------------

    @Test
    void openingBalancesKeepOnlyWhatRemainsToBePaid() {
        List<String> headers = List.of("N° de facture d'origine", "Patient", "Date de facture", "Montant TTC", "Montant déjà réglé");

        EntityRun invalid = importFile(MigrationEntity.SOLDES_OUVERTURE, headers,
                List.of("F1", "P1", "31/01/2026", "1000", "1500"));
        assertEquals(List.of("2:montantRegle:OVERPAID"), codes(invalid.errors()));

        EntityRun run = importFile(MigrationEntity.SOLDES_OUVERTURE, headers,
                List.of("F1", "P1", "31/01/2026", "67200", "20000"),
                List.of("F2", "P1", "28/02/2026", "5600", "5600"));
        assertTrue(run.applied(), () -> run.errors().toString());
        assertEquals(1, run.created());
        assertEquals("FULLY_PAID", run.warnings().getFirst().code());
        OpeningBalanceInvoiceAssert.check(balances.invoices.getFirst().invoice(), "REPRISE-F1", "67200", "20000", "111");

        balances.paidInPlatform.add(balances.invoices.getFirst().id());
        EntityRun replay = importFile(MigrationEntity.SOLDES_OUVERTURE, headers, List.of("F1", "P1", "31/01/2026", "67200", "30000"));
        assertEquals(List.of("2:numeroFacture:PAID_IN_PLATFORM"), codes(replay.errors()));
        assertFalse(replay.applied());
    }

    @SafeVarargs
    private EntityRun importFile(MigrationEntity entity, List<String> headers, List<String>... rows) {
        List<ImportTable.Row> out = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) out.add(new ImportTable.Row(i + 2, rows[i]));
        return service.importEntity(CENTER, batch.getId(), entity, entity.slug() + ".csv", new ImportTable(headers, out), false, "admin");
    }

    private static final class OpeningBalanceInvoiceAssert {
        static void check(com.hemodialyse.backend.domain.migration.port.OpeningBalancePort.OpeningInvoice invoice,
                          String numero, String ttc, String regle, String immatriculation) {
            assertEquals(numero, invoice.numero());
            assertEquals(0, new BigDecimal(ttc).compareTo(invoice.montantTtc()));
            assertEquals(0, new BigDecimal(regle).compareTo(invoice.montantRegle()));
            assertEquals(immatriculation, invoice.numeroImmatriculation());
            assertEquals(LocalDate.of(2026, 1, 31), invoice.periodStart());
        }
    }
}

