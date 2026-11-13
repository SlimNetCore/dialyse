package com.hemodialyse.backend.contexts.documents.interfaces.http;

import com.hemodialyse.backend.contexts.documents.application.GererLesGabarits;
import com.hemodialyse.backend.contexts.documents.application.GererLesModeles;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.ModelesEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.GabaritsLivresEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.HistoriquesEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.ModelesDuCentreEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.UniteImmediate;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.VerificationFixe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les cinq routes de gestion des modèles : mêmes URL et mêmes champs qu'avant (colonnes de la table, {@code id}, {@code deleted}) ; l'ordre des
 * contrôles (centre avant la forme du modèle) et les refus de forme propres (400, 415, 405, 409 : KB-DOC4A-1 à 4). Les rôles (gardes de méthode)
 * sont couverts par le filet de la plateforme (MockMvc avec la sécurité réelle).
 */
class ModelesControleurTest {

    private static final UUID D = new UUID(0L, 0xdL);
    private static final UUID E = new UUID(0L, 0xeL);
    private static final UUID MODELE = new UUID(5L, 1L);
    private static final String RACINE = "/api/v1/documents/modeles";

    private MockMvc mvc;

    @BeforeEach
    void preparer() {
        var session = new SessionFixe(D);
        var gabarits = new GererLesGabarits(session, new ModelesDuCentreEnMemoire(), new GabaritsLivresEnMemoire(), new HistoriquesEnMemoire(),
                new VerificationFixe(), new UniteImmediate(), Clock.systemUTC(), UUID::randomUUID);
        var gestion = new GererLesModeles(session, new ModelesEnMemoire(), gabarits, new UniteImmediate(), () -> MODELE);
        var mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        mvc = MockMvcBuilders.standaloneSetup(new ModelesControleur(gestion, mapper)).setControllerAdvice(new ErreursDesModelesAdvice()).build();
    }

    private static String corps(String code, String chemin) {
        return "{\"centerId\":\"" + D + "\",\"code\":\"" + code + "\",\"libelle\":\"Libelle\",\"typeDocument\":\"CUSTOM\","
                + "\"cheminJrxml\":" + (chemin == null ? "null" : "\"" + chemin + "\"") + ",\"formatImpression\":\"PDF\",\"active\":true}";
    }

    private void creer(String code) throws Exception {
        mvc.perform(post(RACINE).contentType(MediaType.APPLICATION_JSON).content(corps(code, "reports/a.jrxml"))).andExpect(status().isOk());
    }

    @Test
    void should_create_a_model_then_list_it_with_the_column_names_of_the_table() throws Exception {
        mvc.perform(post(RACINE).contentType(MediaType.APPLICATION_JSON).content(corps("A", "reports/a.jrxml")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(MODELE.toString()));

        mvc.perform(get(RACINE).param("centerId", D.toString())).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(MODELE.toString()))
                .andExpect(jsonPath("$[0].center_id").value(D.toString())).andExpect(jsonPath("$[0].code").value("A"))
                .andExpect(jsonPath("$[0].libelle").value("Libelle")).andExpect(jsonPath("$[0].type_document").value("CUSTOM"))
                .andExpect(jsonPath("$[0].chemin_jrxml").value("reports/a.jrxml")).andExpect(jsonPath("$[0].format_impression").value("PDF"))
                .andExpect(jsonPath("$[0].description").doesNotExist()).andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void should_search_a_page_and_refuse_a_size_out_of_bounds() throws Exception {
        creer("A");

        mvc.perform(post(RACINE + "/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + D + "\",\"page\":0,\"size\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post(RACINE + "/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + D + "\",\"page\":0,\"size\":201}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post(RACINE + "/search").contentType(MediaType.APPLICATION_JSON).content("{\"centerId\":\"" + D + "\",\"page\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_check_the_centre_before_the_shape_of_the_search() throws Exception {
        mvc.perform(post(RACINE + "/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + E + "\",\"page\":0,\"size\":0}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void should_refuse_a_foreign_centre_with_403_on_every_route() throws Exception {
        mvc.perform(get(RACINE).param("centerId", E.toString())).andExpect(status().isForbidden());
        mvc.perform(post(RACINE).contentType(MediaType.APPLICATION_JSON).content(corps("A", "reports/a.jrxml").replace(D.toString(), E.toString())))
                .andExpect(status().isForbidden());
        mvc.perform(put(RACINE + "/" + MODELE).contentType(MediaType.APPLICATION_JSON)
                .content(corps("A", "reports/a.jrxml").replace(D.toString(), E.toString()))).andExpect(status().isForbidden());
        mvc.perform(delete(RACINE + "/" + MODELE).param("centerId", E.toString())).andExpect(status().isForbidden());
    }

    @Test
    void should_answer_400_for_a_path_that_is_not_a_delivered_template() throws Exception {
        mvc.perform(post(RACINE).contentType(MediaType.APPLICATION_JSON).content(corps("A", "../../etc/passwd")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Chemin de modèle invalide : attendu reports/<nom>.jrxml"));
        mvc.perform(put(RACINE + "/" + MODELE).contentType(MediaType.APPLICATION_JSON).content(corps("A", null))).andExpect(status().isBadRequest());
    }

    @Test
    void should_answer_409_for_a_code_already_taken_and_400_for_a_missing_field() throws Exception {
        creer("A");

        mvc.perform(post(RACINE).contentType(MediaType.APPLICATION_JSON).content(corps("A", "reports/a.jrxml").replace("\"id\"", "\"x\"")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(post(RACINE).contentType(MediaType.APPLICATION_JSON).content("{\"centerId\":\"" + D + "\",\"cheminJrxml\":\"reports/a.jrxml\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_answer_200_when_updating_or_deleting_an_unknown_model_like_before() throws Exception {
        mvc.perform(put(RACINE + "/" + MODELE).contentType(MediaType.APPLICATION_JSON).content(corps("A", "reports/a.jrxml")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(MODELE.toString()));
        mvc.perform(delete(RACINE + "/" + MODELE).param("centerId", D.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
    }

    @Test
    void should_read_the_body_as_json_only_and_answer_400_or_415_otherwise() throws Exception {
        mvc.perform(post(RACINE).contentType(MediaType.APPLICATION_JSON).content("{pas du json")).andExpect(status().isBadRequest());
        mvc.perform(post(RACINE).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        mvc.perform(post(RACINE).contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isBadRequest());
        mvc.perform(post(RACINE).contentType(MediaType.APPLICATION_JSON).content("null")).andExpect(status().isBadRequest());
        mvc.perform(post(RACINE).contentType(MediaType.TEXT_PLAIN).content(corps("A", "reports/a.jrxml")))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post(RACINE).content(corps("A", "reports/a.jrxml"))).andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(RACINE).contentType(MediaType.APPLICATION_XML).content("<a/>")).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void should_answer_400_for_an_unreadable_or_missing_identifier_or_centre() throws Exception {
        mvc.perform(get(RACINE)).andExpect(status().isBadRequest());
        mvc.perform(get(RACINE).param("centerId", "pas-un-uuid")).andExpect(status().isBadRequest());
        mvc.perform(delete(RACINE + "/pas-un-uuid").param("centerId", D.toString())).andExpect(status().isBadRequest());
        mvc.perform(delete(RACINE + "/" + MODELE)).andExpect(status().isBadRequest());
        mvc.perform(delete(RACINE + "/" + MODELE).param("centerId", "pas-un-uuid")).andExpect(status().isBadRequest());
        mvc.perform(put(RACINE + "/pas-un-uuid").contentType(MediaType.APPLICATION_JSON).content(corps("A", "reports/a.jrxml")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_answer_406_when_the_requested_representation_is_not_available() throws Exception {
        mvc.perform(get(RACINE).param("centerId", D.toString()).accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable()).andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    @Test
    void should_answer_405_with_the_allowed_verbs_for_a_verb_that_is_not_handled() throws Exception {
        mvc.perform(put(RACINE)).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET, HEAD, POST, OPTIONS"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(patch(RACINE)).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(RACINE)).andExpect(status().isMethodNotAllowed());
        mvc.perform(get(RACINE + "/search")).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "POST, OPTIONS"));
        mvc.perform(put(RACINE + "/search")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get(RACINE + "/" + MODELE)).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "PUT, DELETE, OPTIONS"));
        mvc.perform(post(RACINE + "/" + MODELE)).andExpect(status().isMethodNotAllowed());
        mvc.perform(patch(RACINE + "/" + MODELE)).andExpect(status().isMethodNotAllowed());
    }
}
