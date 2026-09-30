package com.hemodialyse.backend.domain.migration;

import com.hemodialyse.backend.domain.migration.model.EntityRun;
import com.hemodialyse.backend.domain.migration.model.IdMapping;
import com.hemodialyse.backend.domain.migration.model.MigrationBatch;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.service.AffectationMigrator;
import com.hemodialyse.backend.domain.migration.service.AssureMigrator;
import com.hemodialyse.backend.domain.migration.service.MigrationDomainService;
import com.hemodialyse.backend.domain.migration.service.PatientMigrator;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationDomainServiceTest {

    private static final CenterId CENTER = CenterId.of(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final CenterId OTHER = CenterId.of(UUID.fromString("00000000-0000-0000-0000-00000000000b"));
    private static final List<String> PATIENT_HEADERS = List.of("Identifiant d'origine", "N° d'assurance", "Nom", "Prénom",
            "Sexe", "Date de naissance", "Date d'admission", "État", "Qualité", "N° d'assurance de l'assuré", "Salle",
            "Générateur", "Jours de dialyse");

    private InMemoryMigrationPorts.Batches batches;
    private InMemoryMigrationPorts.Ids ids;
    private InMemoryMigrationPorts.Values values;
    private InMemoryMigrationPorts.Rollback rollback;
    private InMemoryMigrationPorts.Patients patients;
    private InMemoryMigrationPorts.Assures assures;
    private InMemoryMigrationPorts.Assignments assignments;
    private InMemoryMigrationPorts.Referentials referentials;
    private MigrationDomainService service;
    private UUID salle;
    private UUID generateur;

    private static List<String> patientRow(String legacyId, String numero) {
        return List.of(legacyId, numero, "BENALI", "Karim", "Homme", "15/03/1962", "2019-01-02", "Actif", "Assuré",
                "", "S1", "G01", "Lundi, Mercredi, Vendredi");
    }

    // ---- Lot ---------------------------------------------------------------------------------------------------

    @SafeVarargs
    private static ImportTable table(List<String> headers, List<String>... rows) {
        List<ImportTable.Row> out = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) out.add(new ImportTable.Row(i + 2, rows[i]));
        return new ImportTable(headers, out);
    }

    @BeforeEach
    void setUp() {
        batches = new InMemoryMigrationPorts.Batches();
        ids = new InMemoryMigrationPorts.Ids();
        values = new InMemoryMigrationPorts.Values();
        rollback = new InMemoryMigrationPorts.Rollback();
        patients = new InMemoryMigrationPorts.Patients();
        assures = new InMemoryMigrationPorts.Assures();
        assignments = new InMemoryMigrationPorts.Assignments();
        referentials = new InMemoryMigrationPorts.Referentials();
        salle = referentials.add(ReferentialKind.SALLE, Map.of("code", "S1", "nom", "Salle 1"));
        generateur = referentials.add(ReferentialKind.GENERATEUR, Map.of("numero", "G01", "salle", salle.toString()));
        service = new MigrationDomainService(batches, ids, values, rollback, List.of(
                new AssureMigrator(assures),
                new PatientMigrator(patients, assures, assignments, referentials),
                new AffectationMigrator(patients, assures, assignments)),
                Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC));
    }

    // ---- Patients ----------------------------------------------------------------------------------------------

    @Test
    void onlyOneOpenBatchPerCenter() {
        service.open(CENTER, "Reprise 2026", "AncienLogiciel", LocalDate.of(2022, 1, 1), "admin");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.open(CENTER, "Autre", null, null, "admin"));
        assertEquals("MIGRATION_BATCH_ALREADY_OPEN", ex.getCode());
        service.open(OTHER, "Reprise autre centre", null, null, "admin");
    }

    @Test
    void closedBatchRefusesImports() {
        MigrationBatch batch = service.open(CENTER, "Reprise", null, null, "admin");
        service.close(CENTER, batch.getId());

        BusinessException ex = assertThrows(BusinessException.class, () -> importPatients(batch, false, patientRow("P1", "111")));
        assertEquals("MIGRATION_BATCH_CLOSED", ex.getCode());
    }

    @Test
    void reportsMissingColumns() {
        MigrationBatch batch = service.open(CENTER, "Reprise", null, null, "admin");

        EntityRun run = service.importEntity(CENTER, batch.getId(), MigrationEntity.PATIENTS, "patients.csv",
                table(List.of("Nom", "Prénom", "Colonne inconnue"), List.of("A", "B", "x")), true, "admin");

        assertEquals(List.of("legacyId", "numeroAssurance", "sexe", "dateNaissance", "dateAdmission"), run.missingColumns());
        assertEquals(List.of("Colonne inconnue"), run.ignoredColumns());
        assertFalse(run.isValid());
    }

    @Test
    void dryRunValidatesWithoutWritingThenImportCreatesPatientsAndMappings() {
        MigrationBatch batch = service.open(CENTER, "Reprise", null, null, "admin");

        EntityRun check = importPatients(batch, true, patientRow("P1", "111"), patientRow("P2", "222"));
        assertTrue(check.isValid(), () -> check.errors().toString());
        assertEquals(2, check.created());
        assertEquals(0, patients.saves);

        EntityRun applied = importPatients(batch, false, patientRow("P1", "111"), patientRow("P2", "222"));
        assertTrue(applied.applied());
        assertEquals(2, patients.rows.size());
        Patient p1 = patients.findByNumeroAssurance(CENTER, "111").orElseThrow();
        assertEquals(salle, p1.getSalleId());
        assertEquals(generateur, p1.getGenerateurId());
        assertTrue(p1.getJoursDialyse().lundi() && p1.getJoursDialyse().mercredi() && p1.getJoursDialyse().vendredi());
        assertEquals(LocalDate.of(1962, 3, 15), p1.getDateNaissance());
        assertEquals("111", p1.getAssureNumeroAssurance());
        assertEquals(p1.getId().value().toString(), ids.findTarget(CENTER, MigrationEntity.PATIENTS, "P1").orElseThrow());
    }

    @Test
    void replayingAFileUpdatesInsteadOfDuplicating() {
        MigrationBatch batch = service.open(CENTER, "Reprise", null, null, "admin");
        importPatients(batch, false, patientRow("P1", "111"));

        List<String> changed = new ArrayList<>(patientRow("P1", "111"));
        changed.set(2, "BENALI-MODIFIE");
        EntityRun replay = importPatients(batch, false, changed);

        assertEquals(0, replay.created());
        assertEquals(1, replay.updated());
        assertEquals(1, patients.rows.size());
        assertEquals("BENALI-MODIFIE", patients.rows.values().iterator().next().getNom());
    }

    @Test
    void existingPatientWithSameInsuranceNumberIsMatchedNotDuplicated() {
        Patient existing = Patient.creer(CENTER, "BENALI", "Karim", "M", LocalDate.of(2019, 1, 2),
                LocalDate.of(1962, 3, 15), new NumeroAssurance("111"), PatientType.NON_VACANCIER);
        patients.save(existing);
        MigrationBatch batch = service.open(CENTER, "Reprise", null, null, "admin");

        EntityRun run = importPatients(batch, false, patientRow("P1", "111"));

        assertEquals(1, run.updated());
        assertEquals("MATCHED_EXISTING", run.warnings().getFirst().code());
        assertEquals(1, patients.rows.size());
        assertEquals(IdMapping.Operation.UPDATED, ids.findByBatch(CENTER, batch.getId()).getFirst().operation());
    }

    // ---- Assurés et affectations -------------------------------------------------------------------------------

    @Test
    void unknownLegacyValuesCanBeMappedByTheCenter() {
        MigrationBatch batch = service.open(CENTER, "Reprise", null, null, "admin");
        List<String> row = new ArrayList<>(patientRow("P1", "111"));
        row.set(7, "D");

        EntityRun first = importPatients(batch, true, row);
        assertEquals("UNKNOWN_VALUE", first.errors().getFirst().code());
        assertEquals("etatPatient", first.errors().getFirst().field());

        service.saveValueMapping(CENTER, "etatPatient", "d", "DECEDE");
        EntityRun second = importPatients(batch, true, row);
        assertTrue(second.isValid(), () -> second.errors().toString());
        assertEquals("EVENT_DATE_MISSING", second.warnings().getFirst().code());

        assertThrows(BusinessException.class, () -> service.saveValueMapping(CENTER, "etatPatient", "X", "INCONNU"));
        assertThrows(BusinessException.class, () -> service.saveValueMapping(CENTER, "nom", "X", "Y"));
    }

    @Test
    void reportsRowErrorsWithLineNumbersAndWritesNothing() {
        MigrationBatch batch = service.open(CENTER, "Reprise", null, null, "admin");
        List<String> badDate = new ArrayList<>(patientRow("P2", "222"));
        badDate.set(5, "31/02/1960");
        List<String> unknownRoom = new ArrayList<>(patientRow("P3", "333"));
        unknownRoom.set(10, "S9");
        unknownRoom.set(11, "");
        List<String> childWithoutAssure = new ArrayList<>(patientRow("P4", "444"));
        childWithoutAssure.set(8, "Enfant");

        EntityRun run = importPatients(batch, false, patientRow("P1", "111"), badDate, unknownRoom, childWithoutAssure,
                patientRow("P1", "555"));

        assertEquals(List.of("3:dateNaissance:INVALID_DATE", "4:salle:REFERENCE_NOT_FOUND",
                        "5:assureNumeroAssurance:REQUIRED", "6:legacyId:DUPLICATE_IN_FILE"),
                run.errors().stream().map(i -> i.row() + ":" + i.field() + ":" + i.code()).toList());
        assertFalse(run.applied());
        assertEquals(0, patients.saves);
    }

    // ---- Annulation --------------------------------------------------------------------------------------------

    @Test
    void assureOfAnotherCenterIsRefused() {
        assures.save(InMemoryMigrationPorts.Assures.of("999", OTHER));
        MigrationBatch batch = service.open(CENTER, "Reprise", null, null, "admin");

        EntityRun run = service.importEntity(CENTER, batch.getId(), MigrationEntity.ASSURES, "assures.csv",
                table(List.of("legacy_id", "numero_assurance", "nom"), List.of("A1", "999", "BENALI")), false, "admin");

        assertEquals("OTHER_CENTER", run.errors().getFirst().code());
    }

    // ---- Utilitaires -------------------------------------------------------------------------------------------

    @Test
    void childPatientIsLinkedToItsAssureAndHistoryIsImported() {
        MigrationBatch batch = service.open(CENTER, "Reprise", null, null, "admin");
        service.importEntity(CENTER, batch.getId(), MigrationEntity.ASSURES, "assures.csv",
                table(List.of("legacy_id", "numero_assurance", "nom", "prenom"),
                        List.of("A1", "900", "BENALI", "Ahmed"), List.of("A2", "901", "BENALI", "Fatima")), false, "admin");
        List<String> child = new ArrayList<>(patientRow("P1", "111"));
        child.set(8, "Fils");
        child.set(9, "900");
        importPatients(batch, false, child);
        Patient patient = patients.findByNumeroAssurance(CENTER, "111").orElseThrow();
        assertEquals("900", patient.getAssureNumeroAssurance());
        assertEquals("ENFANT", patient.getQualiteAssure());

        EntityRun history = service.importEntity(CENTER, batch.getId(), MigrationEntity.AFFECTATIONS, "affectations.csv",
                table(List.of("Patient", "N° d'assurance de l'assuré", "Date de début", "Date de fin", "Principale"),
                        List.of("P1", "901", "01/01/2015", "31/12/2018", "non"),
                        List.of("P1", "900", "01/01/2019", "", "oui"),
                        List.of("P9", "900", "01/01/2019", "", "non")), true, "admin");
        assertEquals(List.of("PATIENT_NOT_MIGRATED"), history.errors().stream().map(ValidationIssue::code).toList());

        EntityRun applied = service.importEntity(CENTER, batch.getId(), MigrationEntity.AFFECTATIONS, "affectations.csv",
                table(List.of("Patient", "N° d'assurance de l'assuré", "Date de début", "Date de fin", "Principale"),
                        List.of("P1", "901", "01/01/2015", "31/12/2018", "non"),
                        List.of("P1", "900", "02/01/2019", "", "oui")), false, "admin");
        assertTrue(applied.applied(), () -> applied.errors().toString());
        assertEquals(1, applied.created());
        assertEquals(1, applied.updated()); // l'affectation principale créée avec le patient est reconnue
        assertEquals(2, assignments.findHistory(CENTER, patient.getId().value()).size());
        assertEquals("900", assignments.findPrimary(CENTER, patient.getId().value()).orElseThrow().getNumeroAssurance());
    }

    @Test
    void cancelDeletesOnlyCreatedDataAndIsRefusedWhenAlreadyUsed() {
        Patient existing = Patient.creer(CENTER, "EXISTANT", "Patient", "F", LocalDate.of(2019, 1, 2),
                LocalDate.of(1970, 1, 1), new NumeroAssurance("222"), PatientType.NON_VACANCIER);
        patients.save(existing);
        MigrationBatch batch = service.open(CENTER, "Reprise", null, null, "admin");
        importPatients(batch, false, patientRow("P1", "111"), patientRow("P2", "222"));

        rollback.blockers.add("P1 a une prise en charge");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(CENTER, batch.getId()));
        assertEquals("MIGRATION_CANCEL_BLOCKED", ex.getCode());
        assertTrue(rollback.deleted.isEmpty());

        rollback.blockers.clear();
        MigrationBatch cancelled = service.cancel(CENTER, batch.getId());
        assertEquals(MigrationBatch.Status.ANNULE, cancelled.getStatus());
        assertEquals(List.of("P1"), rollback.deleted.stream().map(IdMapping::legacyId).toList());
        assertTrue(ids.findByBatch(CENTER, batch.getId()).isEmpty());
    }

    private EntityRun importPatients(MigrationBatch batch, boolean dryRun, List<String>... rows) {
        return service.importEntity(CENTER, batch.getId(), MigrationEntity.PATIENTS, "patients.csv",
                table(PATIENT_HEADERS, rows), dryRun, "admin");
    }
}


