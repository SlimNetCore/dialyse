package com.hemodialyse.backend.infrastructure.referential.admin;

import com.hemodialyse.backend.infrastructure.referential.admin.model.ImportReport;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ImportTable;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialValidationException;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.infrastructure.referential.admin.service.ReferentialAdminDomainService;
import com.hemodialyse.backend.application.erreur.BusinessException;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
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

    private static final CentreId CENTER = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final CentreId OTHER = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

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
        ReferentialEntry salle = service.create(CENTER, ReferentialKind.SALLE,
                Map.of("code", "  S1 ", "nom", "Salle principale", "isolement", "oui", "capacite", " 12 "));

        assertEquals("S1", salle.values().get("code"));
        assertEquals("OUI", salle.values().get("isolement"));
        assertEquals("12", salle.values().get("capacite"));

        ReferentialEntry autre = service.create(CENTER, ReferentialKind.SALLE, Map.of("code", "S2", "nom", "Salle 2"));
        assertEquals("NON", autre.values().get("isolement"));
    }

    @Test
    void createReportsEveryInvalidField() {
        Map<String, String> values = new HashMap<>();
        values.put("code", "X".repeat(51));
        values.put("capacite", "abc");

        ReferentialValidationException ex = assertThrows(ReferentialValidationException.class,
                () -> service.create(CENTER, ReferentialKind.SALLE, values));

        assertEquals(List.of("code:TOO_LONG", "nom:REQUIRED", "capacite:INVALID_INTEGER"),
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

    // ---- Import ----------------------------------------------------------------------------------------------

    @Test
    void deleteIsRefusedWhileStillUsed() {
        UUID salle = repo.seed(CENTER, ReferentialKind.SALLE, Map.of("code", "S1", "nom", "Salle 1"));
        repo.usages.put(salle, 2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete(CENTER, ReferentialKind.SALLE, salle));
        assertEquals("REFERENTIAL_IN_USE", ex.getCode());

        repo.usages.remove(salle);
        service.delete(CENTER, ReferentialKind.SALLE, salle);
        assertTrue(repo.of(CENTER, ReferentialKind.SALLE).isEmpty());
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
        ImportReport report = service.importEntries(CENTER, ReferentialKind.SALLE,
                table(List.of("Code", "Couleur"), List.of("S1", "bleu")), false);

        assertEquals(List.of("nom"), report.missingColumns());
        assertEquals(List.of("Couleur"), report.ignoredColumns());
        assertFalse(report.applied());
        assertFalse(report.isValid());
        assertEquals(0, repo.writes);
    }

    @Test
    void importReportsEachRowErrorWithItsLineNumberAndWritesNothing() {
        ImportReport report = service.importEntries(CENTER, ReferentialKind.SALLE, table(
                List.of("code", "nom", "Capacité (générateurs max.)"),
                List.of("S1", "Salle 1", "12"),
                List.of("S2", "", "12"),
                List.of("S3", "Salle 3", "beaucoup"),
                List.of("s1", "Salle 1 bis", "12")), false);

        assertEquals(4, report.totalRows());
        assertEquals(List.of("3:nom:REQUIRED", "4:capacite:INVALID_INTEGER", "5:code:DUPLICATE_IN_FILE"),
                report.errors().stream().map(i -> i.row() + ":" + i.field() + ":" + i.code()).toList());
        assertFalse(report.applied());
        assertEquals(0, repo.writes);
    }

    @Test
    void dryRunCountsCreationsAndUpdatesWithoutWriting() {
        repo.seed(CENTER, ReferentialKind.SALLE, Map.of("code", "S1", "nom", "Salle 1", "isolement", "NON"));

        ImportReport report = service.importEntries(CENTER, ReferentialKind.SALLE, table(
                List.of("Code", "Nom"),
                List.of("S1", "Salle un"),
                List.of("S2", "Salle deux"),
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
        UUID existing = repo.seed(CENTER, ReferentialKind.SALLE, Map.of("code", "S1", "nom", "Salle 1", "isolement", "NON"));

        ImportReport report = service.importEntries(CENTER, ReferentialKind.SALLE, table(
                List.of("code", "nom"),
                List.of("s1", "Salle un"),
                List.of("S2", "Salle deux")), false);

        assertTrue(report.applied());
        assertEquals(2, repo.of(CENTER, ReferentialKind.SALLE).size());
        assertEquals("Salle un", repo.findById(CENTER, ReferentialKind.SALLE, existing)
                .orElseThrow().values().get("nom"));
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

        ImportReport report = service.importEntries(CENTER, ReferentialKind.SALLE,
                tableOf(List.of("code", "nom"), rows), false);

        assertEquals(List.of("TOO_MANY_ROWS"), report.errors().stream().map(ValidationIssue::code).toList());
    }
}

