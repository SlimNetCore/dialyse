package com.hemodialyse.backend.contexts.documents.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Le format de sortie d'un document : tout format inconnu est servi en PDF (dette reproduite, KB-DOC1-5 e). */
class FormatDeSortieTest {

    @Test
    void should_recognise_spreadsheets_and_html_whatever_the_case() {
        for (String f : new String[]{"EXCEL", "excel", "XLS", "xlsx", "Xlsx"}) assertThat(FormatDeSortie.de(f)).isEqualTo(FormatDeSortie.TABLEUR);
        for (String f : new String[]{"HTML", "html"}) assertThat(FormatDeSortie.de(f)).isEqualTo(FormatDeSortie.HTML);
    }

    @Test
    void should_serve_pdf_for_pdf_and_for_anything_else() {
        for (String f : new String[]{"PDF", "pdf", "DOCX", "", "  "}) assertThat(FormatDeSortie.de(f)).isEqualTo(FormatDeSortie.PDF);
    }

    @Test
    void should_give_the_mime_type_and_the_file_name_of_each_format() {
        assertThat(FormatDeSortie.TABLEUR.typeMime()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(FormatDeSortie.PDF.typeMime()).isEqualTo("application/pdf");
        assertThat(FormatDeSortie.HTML.typeMime()).isEqualTo("text/html");
        assertThat(FormatDeSortie.TABLEUR.nomDeFichier("LISTE_PATIENTS")).isEqualTo("liste-patients.xlsx");
        assertThat(FormatDeSortie.PDF.nomDeFichier("Fiche_Patient")).isEqualTo("fiche-patient.pdf");
        assertThat(FormatDeSortie.HTML.nomDeFichier("PEC")).isEqualTo("pec.html");
    }
}
