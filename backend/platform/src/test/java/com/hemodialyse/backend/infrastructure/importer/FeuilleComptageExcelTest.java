package com.hemodialyse.backend.infrastructure.importer;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.stock.model.ComptageImporte;
import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.LigneInventaire;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feuille de comptage : téléchargée, remplie dans un tableur, puis relue.
 */
class FeuilleComptageExcelTest {

    private final FeuilleComptageExcel excel = new FeuilleComptageExcel();

    private static Inventaire inventaire() {
        LigneInventaire a = LigneInventaire.theorique(UUID.randomUUID(), "DIAL-01", "Dialyseur", "U", UUID.randomUUID(),
                "A-01", LocalDate.of(2027, 1, 31), new BigDecimal("6"), new BigDecimal("1500"));
        LigneInventaire b = LigneInventaire.theorique(UUID.randomUUID(), "AIG-02", "Aiguille", "U", UUID.randomUUID(),
                "B-02", null, new BigDecimal("40"), new BigDecimal("12"));
        return Inventaire.ouvrir(UUID.randomUUID(), "INV-00001", LocalDate.of(2026, 9, 30), null, "p",
                OffsetDateTime.of(2026, 9, 30, 10, 0, 0, 0, ZoneOffset.UTC), List.of(a, b));
    }

    private static byte[] remplir(byte[] xlsx, Consumer<Sheet> saisie) throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsx));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            saisie.accept(wb.getSheetAt(0));
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static int col(Sheet sheet, String header) {
        Row row = sheet.getRow(3);
        for (int c = 0; c < row.getLastCellNum(); c++) {
            if (header.equals(row.getCell(c).getStringCellValue())) return c;
        }
        throw new IllegalStateException(header);
    }

    @Test
    void readsQuantitiesAndReasonsTypedIntoTheDownloadedSheet() throws Exception {
        Inventaire inv = inventaire();
        byte[] rempli = remplir(excel.ecrire(inv, true), sheet -> {
            int qty = col(sheet, "Quantité comptée");
            int motif = col(sheet, "Motif écart");
            sheet.getRow(4).getCell(qty).setCellValue(5.5);
            sheet.getRow(4).getCell(motif).setCellValue("casse");
            sheet.getRow(5).getCell(qty).setCellValue("38,25");
        });

        FeuilleComptageExcel.Lecture lecture = excel.lire(rempli, "INV-00001");

        assertTrue(lecture.anomalies().isEmpty());
        List<ComptageImporte> lignes = lecture.lignes();
        assertEquals(2, lignes.size());
        assertEquals(inv.getLignes().get(0).getId(), lignes.get(0).ligneId());
        assertEquals(0, new BigDecimal("5.5").compareTo(lignes.get(0).quantite()));
        assertEquals("CASSE", lignes.get(0).motif());
        assertEquals(0, new BigDecimal("38.25").compareTo(lignes.get(1).quantite()));
        assertNull(lignes.get(1).motif());
        assertEquals("AIG-02", lignes.get(1).articleCode());
    }

    @Test
    void emptyQuantitiesAreKeptAsNotCountedAndBadCellsAreReported() throws Exception {
        byte[] rempli = remplir(excel.ecrire(inventaire(), false), sheet -> {
            sheet.getRow(4).getCell(col(sheet, "Quantité comptée")).setCellValue("douze");
            sheet.getRow(5).getCell(col(sheet, "Motif écart")).setCellValue("Inconnu");
            sheet.getRow(5).getCell(col(sheet, "Quantité comptée")).setCellValue(3);
        });

        FeuilleComptageExcel.Lecture lecture = excel.lire(rempli, "INV-00001");

        assertEquals(List.of(5, 6), lecture.anomalies().stream().map(a -> a.ligneFichier()).toList());
        assertTrue(lecture.lignes().isEmpty());
    }

    @Test
    void refusesASheetFromAnotherInventoryOrANonExcelFile() {
        byte[] xlsx = excel.ecrire(inventaire(), true);
        assertEquals("INVENTORY_SHEET_OTHER_INVENTORY",
                assertThrows(BusinessException.class, () -> excel.lire(xlsx, "INV-00002")).getCode());
        assertEquals("IMPORT_UNSUPPORTED_FORMAT",
                assertThrows(BusinessException.class, () -> excel.lire("code;qte".getBytes(), "INV-00001")).getCode());
    }
}

