package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.doubles.Doubles.IdentitesEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.MoteurEspion;
import com.hemodialyse.backend.contexts.documents.domain.IdentiteImprimable;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.MoteurDeRendu.Rendu;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Le port de rendu publié (documents.v1) : le moteur transitoire rend, Documents donne le type MIME et le nom de fichier. */
class RendreUnDocumentTest {

    private static final UUID CENTRE = new UUID(0L, 0xdL);

    private final MoteurEspion moteur = new MoteurEspion();
    private final RendreUnDocument rendre = new RendreUnDocument(moteur);

    @Test
    void should_render_a_document_by_type_and_describe_it_with_its_mime_type_and_file_name() {
        moteur.rendu = new Rendu(new byte[]{7, 7}, "EXCEL");

        DocumentRendu document = rendre.rendre(CENTRE, "liste_patients", Map.of("a", 1), null);

        assertThat(document.octets()).isEqualTo(new byte[]{7, 7});
        assertThat(document.typeMime()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(document.nomDeFichier()).isEqualTo("liste-patients.xlsx");
        assertThat(moteur.centre).isEqualTo(CENTRE);
        assertThat(moteur.typeDemande).isEqualTo("liste_patients");
        assertThat(moteur.donneesDemandees).containsEntry("a", 1);
    }

    @Test
    void should_pass_the_asked_format_to_the_engine_and_serve_a_pdf_for_an_unknown_one() {
        moteur.rendu = new Rendu(new byte[]{1}, "DOCX");

        DocumentRendu document = rendre.rendre(CENTRE, "PEC", Map.of(), "docx");

        assertThat(moteur.formatDemande).isEqualTo("docx");
        assertThat(document.typeMime()).isEqualTo("application/pdf");
    }

    @Test
    void should_describe_the_letterhead_of_a_centre_and_leave_unknown_ones_blank() {
        var identites = new IdentitesEnMemoire().centre(CENTRE, new IdentiteImprimable("Centre", "", "", "", "", "", "",
                IdentiteImprimable.Societe.vide()));
        var papier = new PapierEnTeteDesDocuments(identites);

        assertThat(papier.parametres(CENTRE)).containsEntry("CENTRE_NOM", "Centre");
        assertThat(papier.parametres(new UUID(9L, 9L))).containsEntry("CENTRE_NOM", "");
        assertThat(papier.parametres(null)).containsEntry("CENTRE_NOM", "");
        assertThat(papier.habiller("<body>x</body>", CENTRE)).contains("Centre").contains("x");
        assertThat(papier.habiller("<p>x</p>", new UUID(9L, 9L))).isEqualTo("<p>x</p>");
        assertThat(papier.enTeteDeLaSociete(null)).isEmpty();
    }
}
