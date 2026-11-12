package com.hemodialyse.backend.contexts.documents.interfaces.http;

import com.hemodialyse.backend.contexts.documents.application.ImprimerUnDocument;
import com.hemodialyse.backend.contexts.documents.application.PapierEnTeteDesDocuments;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.IdentitesEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.ModelesEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.MoteurEspion;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.ReglesEspion;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les routes d'impression : mêmes URL, mêmes en-têtes, mêmes refus en texte brut qu'avant ; les corps et identifiants illisibles sont des 400
 * (KB-DOC1-5 c) et le centre étranger un 403 (KB-DOC1-1).
 */
class ImpressionControleurTest {

    private static final UUID D = new UUID(0L, 0xdL);
    private static final UUID E = new UUID(0L, 0xeL);
    private static final ModeleDeDocument LISTE = new ModeleDeDocument(new UUID(1L, 1L), "reports/liste_patients.jrxml", "PDF", "LISTE_PATIENTS");

    private final ModelesEnMemoire modeles = new ModelesEnMemoire();
    private final MoteurEspion moteur = new MoteurEspion();
    private MockMvc mvc;

    @BeforeEach
    void preparer() {
        modeles.actif(D, "LISTE_PATIENTS", LISTE);
        var imprimer = new ImprimerUnDocument(new SessionFixe(D), modeles, new PapierEnTeteDesDocuments(new IdentitesEnMemoire()), moteur,
                new ReglesEspion());
        mvc = MockMvcBuilders.standaloneSetup(new ImpressionControleur(imprimer)).setControllerAdvice(new ErreursDeLImpressionAdvice()).build();
    }

    private static String corps(UUID centre, String type, String format) {
        return "{\"centerId\":\"" + centre + "\",\"typeDocument\":\"" + type + "\"" + (format == null ? "" : ",\"formatOverride\":\"" + format + "\"")
                + ",\"params\":{}}";
    }

    private org.springframework.test.web.servlet.ResultActions imprimer(String json) throws Exception {
        return mvc.perform(post("/api/v1/documents/print").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void should_serve_a_pdf_inline_by_default() throws Exception {
        imprimer(corps(D, "LISTE_PATIENTS", null)).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=liste-patients.pdf")).andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void should_serve_a_spreadsheet_as_an_attachment_and_html_without_disposition() throws Exception {
        imprimer(corps(D, "LISTE_PATIENTS", "xlsx")).andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=liste-patients.xlsx"));
        imprimer(corps(D, "LISTE_PATIENTS", "HTML")).andExpect(status().isOk()).andExpect(content().contentType("text/html"))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }

    @Test
    void should_refuse_a_missing_field_in_plain_text_with_a_400() throws Exception {
        imprimer("{\"typeDocument\":\"LISTE_PATIENTS\"}").andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("centerId est obligatoire"));
        imprimer(corps(D, "INCONNU", null)).andExpect(status().isBadRequest())
                .andExpect(content().bytes(("Aucun modèle actif trouvé pour ce centre/type: INCONNU (centre=" + D + ")")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void should_answer_403_access_denied_to_a_foreign_centre() throws Exception {
        imprimer(corps(E, "LISTE_PATIENTS", null)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.title").value("Acces refuse"));
    }

    @Test
    void should_answer_400_bad_request_to_an_absent_or_unreadable_body() throws Exception {
        for (String json : new String[]{"{\"centerId\":", "{\"centerId\":\"x\",\"typeDocument\":\"LISTE_PATIENTS\"}",
                "{\"centerId\":\"" + D + "\",\"typeDocument\":\"LISTE_PATIENTS\",\"params\":[1]}"}) {
            imprimer(json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        mvc.perform(post("/api/v1/documents/print")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void should_answer_500_in_plain_text_when_the_engine_fails() throws Exception {
        moteur.echec = new IllegalStateException("Template introuvable : reports/x.jrxml");

        imprimer(corps(D, "LISTE_PATIENTS", null)).andExpect(status().isInternalServerError())
                .andExpect(content().string("Erreur d'impression: Template introuvable : reports/x.jrxml"));

        moteur.echec = new NoClassDefFoundError("net/sf/jasper");
        imprimer(corps(D, "LISTE_PATIENTS", null)).andExpect(status().isInternalServerError())
                .andExpect(content().string("Erreur critique d'impression: NoClassDefFoundError - net/sf/jasper"));
    }

    @Test
    void should_print_by_model_with_its_own_error_prefixes() throws Exception {
        mvc.perform(post("/api/v1/documents/print/" + LISTE.id()).contentType(MediaType.APPLICATION_JSON).content(corps(D, "ignore", null)))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", "inline; filename=liste-patients.pdf"));
        mvc.perform(post("/api/v1/documents/print/" + new UUID(9L, 9L)).contentType(MediaType.APPLICATION_JSON).content(corps(D, "ignore", null)))
                .andExpect(status().isBadRequest())
                .andExpect(content().bytes("Modèle introuvable pour ce centre".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        moteur.echec = new IllegalStateException("Template introuvable");
        mvc.perform(post("/api/v1/documents/print/" + LISTE.id()).contentType(MediaType.APPLICATION_JSON).content(corps(D, "ignore", null)))
                .andExpect(status().isInternalServerError()).andExpect(content().string("Erreur: Template introuvable"));
        moteur.echec = new NoClassDefFoundError("x");
        mvc.perform(post("/api/v1/documents/print/" + LISTE.id()).contentType(MediaType.APPLICATION_JSON).content(corps(D, "ignore", null)))
                .andExpect(status().isInternalServerError()).andExpect(content().string("Erreur critique: NoClassDefFoundError - x"));
    }

    @Test
    void should_answer_400_bad_request_to_a_model_identifier_that_is_not_a_uuid() throws Exception {
        mvc.perform(post("/api/v1/documents/print/pas-un-uuid").contentType(MediaType.APPLICATION_JSON).content(corps(D, "ignore", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
