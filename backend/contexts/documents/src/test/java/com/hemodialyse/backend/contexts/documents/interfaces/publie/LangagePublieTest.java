package com.hemodialyse.backend.contexts.documents.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.application.PapierEnTeteDesDocuments;
import com.hemodialyse.backend.contexts.documents.application.RendreUnDocument;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.IdentitesEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.MoteurEspion;
import com.hemodialyse.backend.contexts.documents.domain.IdentiteImprimable;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** documents.v1 : le papier en-tête et le rendu publiés, et le type de document déclaré. */
class LangagePublieTest {

    private static final UUID CENTRE = new UUID(0L, 0xdL);
    private static final UUID SOCIETE = new UUID(0L, 0x5L);

    private final IdentitesEnMemoire identites = new IdentitesEnMemoire()
            .centre(CENTRE, new IdentiteImprimable("Centre", "", "", "", "", "", "", IdentiteImprimable.Societe.vide()))
            .societe(SOCIETE, new IdentiteImprimable.Societe("Raison", "", "", "", "", "", "", "NIF", "", "", "Pied", new byte[]{1}));
    private final PapierEnTeteDuCentre papier = new PapierEnTeteDuCentrePublie(new PapierEnTeteDesDocuments(identites));

    @Test
    void should_dress_an_html_document_with_the_letterhead_of_the_centre() {
        assertThat(papier.habiller("<body>x</body>", CENTRE)).contains("Centre").contains(">x<");
        assertThat(papier.habiller("<p>x</p>", CENTRE)).isEqualTo("<p>x</p>");
    }

    @Test
    void should_give_the_identity_parameters_and_drop_the_identity_sent_by_a_caller() {
        assertThat(papier.parametresDeLEnTete(CENTRE)).containsEntry("CENTRE_NOM", "Centre");

        Map<String, Object> appelant = new HashMap<>();
        appelant.put("SOCIETE_NOM", "x");
        appelant.put("CENTER_ID", "garde");
        assertThat(papier.ecarterLIdentiteDeLAppelant(appelant)).containsOnlyKeys("CENTER_ID");
    }

    @Test
    void should_give_the_letterhead_of_a_company_alone() {
        var entete = papier.enTeteDeLaSociete(SOCIETE).orElseThrow();

        assertThat(entete.nom()).isEqualTo("Raison");
        assertThat(entete.legal()).isEqualTo("NIF : NIF");
        assertThat(entete.piedDePage()).isEqualTo("Pied");
        assertThat(entete.logo()).isEqualTo(new byte[]{1});
        assertThat(papier.enTeteDeLaSociete(new UUID(9L, 9L))).isEmpty();
    }

    @Test
    void should_render_through_the_published_port() {
        var moteur = new MoteurEspion();
        RenduDeDocument rendu = new RenduDeDocumentPublie(new RendreUnDocument(moteur));

        assertThat(rendu.rendre(CENTRE, "PEC", Map.of(), null).nomDeFichier()).isEqualTo("pec.pdf");
    }

    @Test
    void should_declare_a_type_of_document_with_its_default_template_and_expected_parameters() {
        var type = new TypeDeDocumentDeclare("CAHIER_DIALYSE", "Cahier de dialyse", "reports/cahier_dialyse.jrxml", List.of("PATIENT_ID"), 1);

        assertThat(type.parametresAttendus()).containsExactly("PATIENT_ID");
        assertThatThrownBy(() -> new TypeDeDocumentDeclare("cahier", "L", "g", null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TypeDeDocumentDeclare("CODE", " ", "g", null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TypeDeDocumentDeclare("CODE", "L", "", null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TypeDeDocumentDeclare("CODE", "L", "g", null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new TypeDeDocumentDeclare("CODE", "L", "g", null, 1).parametresAttendus()).isEmpty();
    }
}
