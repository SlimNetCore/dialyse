package com.hemodialyse.backend.contexts.facturation.infrastructure;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.documents.SyntheseRendueParDocuments;
import com.hemodialyse.backend.contexts.facturation.infrastructure.classeur.SyntheseEnClasseur;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FormatDImpression;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ModeleDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle.Caisse;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle.Ligne;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheseRendueParDocumentsTest {

    private static final UUID CENTRE = UUID.fromString("99996900-0000-0000-0000-0000000000f5");

    private static SyntheseMensuelle synthese(List<Ligne> lignes) {
        return new SyntheseMensuelle(CENTRE, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                List.of(new Caisse("CASNOS", "CASNOS Centre"), new Caisse("CNAS", "CNAS Centre")), List.of(), List.of(), 2, 1, 3,
                new BigDecimal("200.00"), new BigDecimal("38.00"), new BigDecimal("238.00"), lignes);
    }

    @Test
    void should_render_pdf_through_the_documents_port_with_the_lines_of_the_billing() {
        List<Object[]> appels = new ArrayList<>();
        SyntheseRendueParDocuments acl = new SyntheseRendueParDocuments((centre, type, donnees, format) -> {
            appels.add(new Object[]{centre, type, donnees, format});
            return new DocumentRendu(new byte[]{1}, "application/pdf", "x.pdf");
        });

        byte[] octets = acl.imprimer(synthese(List.of(new Ligne("Forfait HD", "CNAS", new BigDecimal("100.00"), new BigDecimal("119.00")))),
                FormatDImpression.PDF);

        assertThat(octets).containsExactly(1);
        assertThat(appels).singleElement().satisfies(a -> {
            assertThat(a[0]).isEqualTo(CENTRE);
            assertThat(a[1]).isEqualTo("SYNTHESE_FACTURATION_MENSUELLE");
            assertThat(a[3]).isEqualTo("PDF");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lignes = (List<Map<String, Object>>) ((Map<String, Object>) a[2]).get(ModeleDeLaSynthese.LIGNES);
            // CNAS est la deuxième colonne (rang 1) : le rang ordonne, le libellé s'affiche
            assertThat(lignes).singleElement().satisfies(l -> {
                assertThat(l.get("FORFAIT")).isEqualTo("Forfait HD");
                assertThat(l.get("COLONNE")).isEqualTo("0001CNAS Centre");
            });
        });
    }

    @Test
    void should_build_the_excel_workbook_itself_without_calling_the_documents_port() throws Exception {
        SyntheseRendueParDocuments acl = new SyntheseRendueParDocuments((centre, type, donnees, format) -> {
            throw new AssertionError("Excel ne passe pas par le moteur de mise en page");
        });

        byte[] classeur = acl.imprimer(synthese(List.of(new Ligne("Forfait HD", "CNAS", new BigDecimal("100.00"), new BigDecimal("0.55")))),
                FormatDImpression.EXCEL);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(classeur))) {
            Sheet feuille = wb.getSheetAt(0);
            assertThat(feuille.getSheetName()).isEqualTo(SyntheseEnClasseur.NOM_DE_LA_FEUILLE).hasSizeLessThanOrEqualTo(31);
            assertThat(feuille.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Forfait");
            assertThat(feuille.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Forfait HD");
            assertThat(feuille.getRow(4).getCell(4).getNumericCellValue()).isEqualTo(0.55);
        }
    }

    @Test
    void should_mention_the_empty_period_in_the_workbook() throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(SyntheseEnClasseur.classeur(synthese(List.of()))))) {
            assertThat(wb.getSheetAt(0).getRow(4).getCell(0).getStringCellValue()).isEqualTo("Aucune facture sur la période.");
        }
    }
}
