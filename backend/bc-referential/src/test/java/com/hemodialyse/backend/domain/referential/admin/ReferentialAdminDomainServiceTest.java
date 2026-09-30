package com.hemodialyse.backend.domain.referential.admin;

import com.hemodialyse.backend.domain.referential.admin.model.ImportReport;
import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialValidationException;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.referential.admin.service.ReferentialAdminDomainService;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferentialAdminDomainServiceTest {

    private static final CenterId CENTER = CenterId.of(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final CenterId OTHER = CenterId.of(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

    private InMemoryReferentialAdminRepository repo;
    private ReferentialAdminDomainService service;

    @SafeVarargs
    private static ImportTable table(List<String> headers, List<String>... rows) {
        return tableOf(headers, List.of(rows));
    }

    // ---- Saisie unitaire -------------------------------------------------------------------------------------

    private static ImportTable tableOf(List<String> headers, List<List<String>> rows) {
        List<ImportTable.Row> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) out.add(new ImportTable.Row(i + 2, rows.get(i)));
        return new ImportTable(headers, out);
    }

    @BeforeEach
    void setUp() {
        repo = new InMemoryReferentialAdminRepository();
        service = new ReferentialAdminDomainService(repo);
    }

    @Test
    void createNormalizesValuesAndAppliesDefaults() {
        ReferentialEntry forfait = service.create(CENTER, ReferentialKind.FORFAIT,
                Map.of("code", "  HD-CONV ", "libelle", "Hémodialyse conventionnelle", "prix", "5 600,5"));

        assertEquals("HD-CONV", forfait.values().get("code"));
        assertEquals("5600.50", forfait.values().get("prix"));

        ReferentialEntry caisse = service.create(CENTER, ReferentialKind.CAISSE, Map.of("code", "CNAS", "nom", "CNAS"));
        assertEquals("STANDARD", caisse.values().get("typeCaisse"));
    }

    @Test
    void createReportsEveryInvalidField() {
        Map<String, String> values = new HashMap<>();
        values.put("code", "X".repeat(51));
        values.put("prix", "abc");

        ReferentialValidationException ex = assertThrows(ReferentialValidationException.class,
                () -> service.create(CENTER, ReferentialKind.FORFAIT, values));

        assertEquals(List.of("code:TOO_LONG", "libelle:REQUIRED", "prix:INVALID_NUMBER"),
                ex.getIssues().stream().map(i -> i.field() + ":" + i.code()).toList());
        assertEquals(0, repo.writes);
    }

    @Test
    void codeMustBeUniqueWithinTheCenterOnly() {
        service.create(CENTER, ReferentialKind.SALLE, Map.of("code", "S1", "nom", "Salle 1"));

        ReferentialValidationException ex = assertThrows(ReferentialValidationException.class,
                () -> service.create(CENTER, ReferentialKind.SALLE, Map.of("code", "s1", "nom", "Autre")));
        assertEquals("ALREADY_EXISTS", ex.getIssues().getFirst().code());

        // Un autre centre peut réutiliser le même code.
        service.create(OTHER, ReferentialKind.SALLE, Map.of("code", "S1", "nom", "Salle 1"));
        assertEquals(1, repo.of(OTHER, ReferentialKind.SALLE).size());
    }

    @Test
    void updateKeepsItsOwnCode() {
        ReferentialEntry salle = service.create(CENTER, ReferentialKind.SALLE, Map.of("code", "S1", "nom", "Salle 1"));

        ReferentialEntry updated = service.update(CENTER, ReferentialKind.SALLE, salle.id(),
                Map.of("code", "S1", "nom", "Salle principale"));

        assertEquals("Salle principale", updated.values().get("nom"));
    }

    @Test
    void referenceAcceptsIdOrCodeButOnlyFromTheSameCenter() {
        UUID salle = repo.seed(CENTER, ReferentialKind.SALLE, Map.of("code", "S1", "nom", "Salle 1"));
        repo.seed(OTHER, ReferentialKind.SALLE, Map.of("code", "S9", "nom", "Salle autre centre"));

        ReferentialEntry byCode = service.create(CENTER, ReferentialKind.GENERATEUR, Map.of("numero", "G01", "salle", "s1"));
        ReferentialEntry byId = service.create(CENTER, ReferentialKind.GENERATEUR,
                Map.of("numero", "G02", "salle", salle.toString(), "etat", "en panne"));

        assertEquals(salle.toString(), byCode.values().get("salle"));
        assertEquals("FONCTIONNEL", byCode.values().get("etat"));
        assertEquals("EN_PANNE", byId.values().get("etat"));

        ReferentialValidationException ex = assertThrows(ReferentialValidationException.class,
                () -> service.create(CENTER, ReferentialKind.GENERATEUR, Map.of("numero", "G03", "salle", "S9")));
        assertEquals("REFERENCE_NOT_FOUND", ex.getIssues().getFirst().code());
    }

    // ---- Import ----------------------------------------------------------------------------------------------

    @Test
    void deleteIsRefusedWhileStillUsed() {
        UUID caisse = repo.seed(CENTER, ReferentialKind.CAISSE, Map.of("code", "CNAS", "nom", "CNAS", "typeCaisse", "STANDARD"));
        repo.usages.put(caisse, 2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete(CENTER, ReferentialKind.CAISSE, caisse));
        assertEquals("REFERENTIAL_IN_USE", ex.getCode());

        repo.usages.remove(caisse);
        service.delete(CENTER, ReferentialKind.CAISSE, caisse);
        assertTrue(repo.of(CENTER, ReferentialKind.CAISSE).isEmpty());
    }

    @Test
    void anotherCenterCannotTouchTheRow() {
        UUID salle = repo.seed(CENTER, ReferentialKind.SALLE, Map.of("code", "S1", "nom", "Salle 1"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete(OTHER, ReferentialKind.SALLE, salle));
        assertEquals("REFERENTIAL_NOT_FOUND", ex.getCode());
        assertEquals(1, repo.of(CENTER, ReferentialKind.SALLE).size());
    }

    @Test
    void importReportsMissingRequiredColumnsAndIgnoredOnes() {
        ImportReport report = service.importEntries(CENTER, ReferentialKind.GENERATEUR,
                table(List.of("Numéro", "Marque", "Couleur"), List.of("G01", "Fresenius", "bleu")), false);

        assertEquals(List.of("salle"), report.missingColumns());
        assertEquals(List.of("Couleur"), report.ignoredColumns());
        assertFalse(report.applied());
        assertFalse(report.isValid());
        assertEquals(0, repo.writes);
    }

    @Test
    void importReportsEachRowErrorWithItsLineNumberAndWritesNothing() {
        repo.seed(CENTER, ReferentialKind.CAISSE, Map.of("code", "CNAS", "nom", "CNAS", "typeCaisse", "STANDARD"));

        ImportReport report = service.importEntries(CENTER, ReferentialKind.AGENCE, table(
                List.of("code", "nom", "Caisse (code)"),
                List.of("AG1", "Agence 1", "CNAS"),
                List.of("AG2", "", "CNAS"),
                List.of("AG3", "Agence 3", "INCONNUE"),
                List.of("ag1", "Agence 1 bis", "CNAS")), false);

        assertEquals(4, report.totalRows());
        assertEquals(List.of("3:nom:REQUIRED", "4:caisse:REFERENCE_NOT_FOUND", "5:code:DUPLICATE_IN_FILE"),
                report.errors().stream().map(i -> i.row() + ":" + i.field() + ":" + i.code()).toList());
        assertFalse(report.applied());
        assertEquals(0, repo.writes);
    }

    @Test
    void dryRunCountsCreationsAndUpdatesWithoutWriting() {
        repo.seed(CENTER, ReferentialKind.CRENEAU, Map.of("code", "CR1", "libelle", "Matin"));

        ImportReport report = service.importEntries(CENTER, ReferentialKind.CRENEAU, table(
                List.of("Code", "Libellé"),
                List.of("CR1", "Matin (06h30 – 10h30)"),
                List.of("CR2", "Après-midi"),
                List.of("", "")), true);

        assertTrue(report.isValid());
        assertEquals(2, report.totalRows());
        assertEquals(1, report.created());
        assertEquals(1, report.updated());
        assertFalse(report.applied());
        assertEquals(0, repo.writes);
    }

    @Test
    void importCreatesAndUpdatesByNaturalKey() {
        UUID existing = repo.seed(CENTER, ReferentialKind.CRENEAU, Map.of("code", "CR1", "libelle", "Matin"));

        ImportReport report = service.importEntries(CENTER, ReferentialKind.CRENEAU, table(
                List.of("code", "libelle"),
                List.of("cr1", "Matin (06h30 – 10h30)"),
                List.of("CR2", "Après-midi")), false);

        assertTrue(report.applied());
        assertEquals(2, repo.of(CENTER, ReferentialKind.CRENEAU).size());
        assertEquals("Matin (06h30 – 10h30)", repo.findById(CENTER, ReferentialKind.CRENEAU, existing)
                .orElseThrow().values().get("libelle"));
    }

    @Test
    void importRejectsEmptyFilesAndFilesWithoutData() {
        ImportReport empty = service.importEntries(CENTER, ReferentialKind.SALLE, new ImportTable(List.of(), List.of()), false);
        assertEquals("EMPTY_FILE", empty.errors().getFirst().code());

        ImportReport noData = service.importEntries(CENTER, ReferentialKind.SALLE, table(List.of("code", "nom")), false);
        assertEquals("NO_DATA", noData.errors().getFirst().code());
    }

    @Test
    void importRefusesTooManyRows() {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i <= ReferentialAdminDomainService.MAX_IMPORT_ROWS; i++) rows.add(List.of("T" + i, "Nom " + i));

        ImportReport report = service.importEntries(CENTER, ReferentialKind.TRANSPORTEUR,
                tableOf(List.of("nom", "telephone"), rows), false);

        assertEquals(List.of("TOO_MANY_ROWS"), report.errors().stream().map(ValidationIssue::code).toList());
    }
}

