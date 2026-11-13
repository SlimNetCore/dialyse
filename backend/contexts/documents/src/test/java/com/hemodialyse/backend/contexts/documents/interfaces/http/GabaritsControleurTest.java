package com.hemodialyse.backend.contexts.documents.interfaces.http;

import com.hemodialyse.backend.contexts.documents.application.GererLesGabarits;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.GabaritsLivresEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.HistoriquesEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.ModelesDuCentreEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.UniteImmediate;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.VerificationFixe;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCentre;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les cinq routes des versions de gabarit : mêmes URL et mêmes champs qu'avant ; l'ordre des contrôles (centre avant la forme des autres
 * paramètres) et les refus de forme propres (400, 415, 405 : KB-DOC3-1 et 2). Les rôles (garde de classe) sont couverts par le filet de la
 * plateforme (MockMvc avec la sécurité réelle).
 */
class GabaritsControleurTest {

    private static final UUID D = new UUID(0L, 0xdL);
    private static final UUID E = new UUID(0L, 0xeL);
    private static final UUID MODELE = new UUID(5L, 1L);
    private static final String RACINE = "/api/v1/documents/modeles/" + MODELE;

    private MockMvc mvc;
    private String original;

    @BeforeEach
    void preparer() throws Exception {
        try (var in = new ClassPathResource("reports/attestation.jrxml").getInputStream()) {
            original = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var modeles = new ModelesDuCentreEnMemoire();
        modeles.ajouter(new ModeleDuCentre(MODELE, D, "Rapport été", "ATTESTATION", "reports/attestation.jrxml"));
        var livres = new GabaritsLivresEnMemoire();
        livres.livrer("reports/attestation.jrxml", original);
        var gabarits = new GererLesGabarits(new SessionFixe(D), modeles, livres, new HistoriquesEnMemoire(), new VerificationFixe(),
                new UniteImmediate(), Clock.systemUTC(), UUID::randomUUID);
        mvc = MockMvcBuilders.standaloneSetup(new GabaritsControleur(gabarits)).setControllerAdvice(new ErreursDesGabaritsAdvice()).build();
    }

    private MockMultipartFile fichier(String contenu) {
        return new MockMultipartFile("file", "modele.jrxml", "application/xml", contenu.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void should_create_the_version_and_list_it_with_the_historical_field_names() throws Exception {
        mvc.perform(multipart(RACINE + "/versions").file(fichier(original.replace("topMargin=\"40\"", "topMargin=\"36\""))).param("commentaire", "Marge"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.uploadedBy").value("admin-fictif")).andExpect(jsonPath("$.commentaire").value("Marge"))
                .andExpect(jsonPath("$.sha256").isString()).andExpect(jsonPath("$.tailleOctets").isNumber())
                .andExpect(jsonPath("$.uploadedAt").isString());

        mvc.perform(get(RACINE + "/versions")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.activeVersion").value(1)).andExpect(jsonPath("$.items[0].version").value(1))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void should_answer_422_with_the_violations_when_the_template_is_refused() throws Exception {
        mvc.perform(multipart(RACINE + "/versions").file(new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[]{1})))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.violations[0].code").value("FILE_TYPE"));
    }

    @Test
    void should_serve_the_source_as_a_safe_attachment_named_after_the_model_code() throws Exception {
        mvc.perform(get(RACINE + "/source")).andExpect(status().isOk()).andExpect(content().contentType("application/xml"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", containsString("filename=\"rapport__t_.jrxml\"")));
    }

    @Test
    void should_check_the_centre_before_reading_the_other_parameters() throws Exception {
        mvc.perform(post(RACINE + "/versions/abc/activate").param("centerId", E.toString())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post(RACINE + "/versions/abc/activate").param("centerId", D.toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST")).andExpect(jsonPath("$.detail").value("Paramètre illisible : version"));
    }

    @Test
    void should_answer_400_for_unreadable_parameters_and_404_without_body_for_a_missing_model_or_version() throws Exception {
        mvc.perform(get(RACINE + "/versions").param("centerId", "x")).andExpect(status().isBadRequest());
        mvc.perform(get(RACINE + "/versions").param("size", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get(RACINE + "/source").param("origine", "peut-etre")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/documents/modeles/pas-un-uuid/versions")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/documents/modeles/" + new UUID(7L, 7L) + "/versions")).andExpect(status().isNotFound())
                .andExpect(content().string(""));
        mvc.perform(post(RACINE + "/versions/4/activate")).andExpect(status().isNotFound());
    }

    @Test
    void should_read_an_empty_size_as_the_default_and_the_usual_boolean_spellings() throws Exception {
        mvc.perform(get(RACINE + "/versions").param("size", "")).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20));
        mvc.perform(get(RACINE + "/source").param("origine", "YES")).andExpect(status().isOk());
        mvc.perform(get(RACINE + "/source").param("origine", "0")).andExpect(status().isOk());
    }

    @Test
    void should_answer_415_when_the_body_is_not_multipart_and_400_when_the_file_part_is_missing() throws Exception {
        mvc.perform(post(RACINE + "/versions").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post(RACINE + "/versions")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart(RACINE + "/versions")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void should_answer_405_with_the_allowed_verbs_for_the_verbs_the_routes_do_not_handle() throws Exception {
        mvc.perform(delete(RACINE + "/versions")).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET, HEAD, POST, OPTIONS"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(put(RACINE + "/versions")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post(RACINE + "/source")).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET, HEAD, OPTIONS"));
        mvc.perform(get(RACINE + "/versions/1/activate")).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "POST, OPTIONS"));
        mvc.perform(get(RACINE + "/reset")).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "POST, OPTIONS"));
    }

    @Test
    void should_answer_204_to_a_reset_and_set_the_audit_label_on_deposit_and_activation() throws Exception {
        var depot = mvc.perform(multipart(RACINE + "/versions").file(fichier(original.replace("topMargin=\"40\"", "topMargin=\"35\"")))).andReturn();
        org.assertj.core.api.Assertions.assertThat(depot.getRequest().getAttribute("hemodialyse.audit.libelle"))
                .isEqualTo("Gabarit de modèle téléversé : version 1");
        var activation = mvc.perform(post(RACINE + "/versions/1/activate")).andExpect(status().isOk()).andReturn();
        org.assertj.core.api.Assertions.assertThat(activation.getRequest().getAttribute("hemodialyse.audit.libelle"))
                .isEqualTo("Version de gabarit activée : version 1");
        mvc.perform(post(RACINE + "/reset")).andExpect(status().isNoContent());
    }
}
