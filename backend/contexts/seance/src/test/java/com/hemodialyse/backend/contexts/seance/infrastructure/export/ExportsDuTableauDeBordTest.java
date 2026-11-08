package com.hemodialyse.backend.contexts.seance.infrastructure.export;

import com.hemodialyse.backend.contexts.seance.domain.Demographie;
import com.hemodialyse.backend.contexts.seance.domain.EffectifDuMois;
import com.hemodialyse.backend.contexts.seance.domain.FormatDExport;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SeancesAttendues;
import com.hemodialyse.backend.contexts.seance.domain.TableauDeBordDesSeances;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exports du tableau de bord (CSV, XLSX, PDF) : contenu décodé, aucune donnée nominative, aucun montant. Les mêmes octets que le legacy
 * sont fixés au niveau supérieur par les goldens de caractérisation SE7b (parité, lot B).
 */
class ExportsDuTableauDeBordTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 1, 31);

    private final ExportsDuTableauDeBordCsvPdfXlsx exports = new ExportsDuTableauDeBordCsvPdfXlsx();

    private static TableauDeBordDesSeances tableau() {
        var effectif = EffectifDuMois.composer(List.of(), List.of(new Demographie(new PatientId(new UUID(1, 1)), "F", LocalDate.of(1950, 5, 5)),
                new Demographie(new PatientId(new UUID(1, 2)), "H", null)), DEBUT, FIN);
        return TableauDeBordDesSeances.composer(new MoisDuTableauDeBord(2026, 1), LocalDate.of(2026, 6, 15), 7, 2,
                SeancesAttendues.calculer(List.of(), DEBUT, FIN, Set.of()), effectif, 0);
    }

    @Test
    void should_write_the_csv_summary_then_the_sex_and_age_distributions() {
        String csv = new String(exports.produire(FormatDExport.CSV, tableau()), StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("""
                year,month,expectedSeances,presenceCount,absenceCount,totalSeances
                2026,1,0,7,2,7

                sexe,count
                M,1
                F,1
                AUTRE,0

                ageRange,count
                0-17,0
                18-39,0
                40-59,0
                60+,1
                INCONNU,1
                """);
    }

    @Test
    void should_write_three_xlsx_sheets_with_the_unaccented_labels() throws Exception {
        Map<String, List<String>> feuilles = new LinkedHashMap<>();
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(exports.produire(FormatDExport.XLSX, tableau())))) {
            DataFormatter format = new DataFormatter();
            for (Sheet feuille : classeur) {
                List<String> lignes = new ArrayList<>();
                for (Row ligne : feuille) lignes.add(format.formatCellValue(ligne.getCell(0)) + " | " + format.formatCellValue(ligne.getCell(1)));
                feuilles.put(feuille.getSheetName(), lignes);
            }
        }

        assertThat(feuilles.keySet()).containsExactly("Synthese", "Repartition sexe", "Repartition age");
        assertThat(feuilles.get("Synthese")).containsExactly("Annee | 2026", "Mois | 1", "Seances prevues | 0", "Presences | 7",
                "Absences | 2", "Seances totales | 7");
        assertThat(feuilles.get("Repartition sexe")).containsExactly("Sexe | Count", "M | 1", "F | 1", "AUTRE | 0");
        assertThat(feuilles.get("Repartition age")).contains("Tranche age | Count", "60+ | 1", "INCONNU | 1");
    }

    @Test
    void should_write_a_one_page_pdf_with_the_period_the_counters_and_both_tables() throws Exception {
        byte[] octets = exports.produire(FormatDExport.PDF, tableau());
        List<String> lignes = new ArrayList<>();
        try (PDDocument pdf = PDDocument.load(octets)) {
            for (String ligne : new PDFTextStripper().getText(pdf).split("\\R")) if (!ligne.isBlank()) lignes.add(ligne.strip());
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
        }

        assertThat(lignes).contains("Dashboard séances", "Période: 2026-01", "Séances prévues: 0", "Présences: 7", "Absences: 2",
                "Séances totales: 7", "Répartition sexe", "Répartition âge");
    }

    @Test
    void should_never_write_a_name_or_an_amount() throws Exception {
        String csv = new String(exports.produire(FormatDExport.CSV, tableau()), StandardCharsets.UTF_8).toLowerCase();

        assertThat(csv).doesNotContain("nom", "prenom", "montant", "ttc");
    }
}
