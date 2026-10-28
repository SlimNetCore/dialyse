package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.planification.application.ImprimerLeCalendrierDeLaProposition;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.doubles.MondeDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adapter entrant de l'optimisation : codes (404, 409, 422, 400), plafond de pagination, JSON aux noms historiques, jamais de cache,
 * corps illisible en 400. Les droits (403 avant la lecture du corps) sont prouvés par le filet de caractérisation et le test d'intégration.
 */
class OptimisationsControleurTest {

    private static final UUID COMPTE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private final MondeDOptimisation m = new MondeDOptimisation();
    private MockMvc mvc;

    @BeforeEach
    void monter() {
        m.optimisation.donneesDuCentre = new JeuDOptimisation().patient(1, JeuDOptimisation.LMV, false,
                JeuDOptimisation.poste(JeuDePlaces.A, JeuDePlaces.MATIN, JeuDePlaces.A1)).donnees();
        m.places.aujourdhui = java.time.LocalDate.of(2026, 11, 4);
        SessionCourante session = new SessionCourante() {
            @Override
            public CentreId centre(UUID centreDemande) {
                return centreDemande == null ? JeuDePlaces.CENTRE : new CentreId(centreDemande);
            }

            @Override
            public CompteId utilisateur() {
                return new CompteId(COMPTE);
            }

            @Override
            public boolean administrateur() {
                return true;
            }
        };
        ImpressionDuCalendrierDeLaProposition impression = (centre, execution, titre, resume) ->
                new ImpressionDuCalendrierDeLaProposition.Document("%PDF-".getBytes(), "PDF");
        var controleur = new OptimisationsControleur(session, JsonMapper.builder().build(), m.places, m.lancer, m.consulter, m.arreter,
                m.appliquer, m.supprimer, new ImprimerLeCalendrierDeLaProposition(m.optimisation.executionsRepository,
                m.optimisation.calendriers, impression));
        mvc = MockMvcBuilders.standaloneSetup(controleur)
                .setControllerAdvice(new ErreursDeLOptimisationAdvice(), new EntreesDeLaPlanificationAdvice()).build();
    }

    private MvcResult lancer(String corps) throws Exception {
        return mvc.perform(post("/api/v1/planning/optimisations").contentType(MediaType.APPLICATION_JSON).content(corps)).andReturn();
    }

    private String idDe(MvcResult r) throws Exception {
        return JsonMapper.builder().build().readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void should_answer_202_with_the_execution_in_progress_in_the_historical_json_without_caching() throws Exception {
        MvcResult r = lancer("{\"perimetre\":\"PATIENTS\",\"debutSemaine\":\"2026-11-04\"}");

        assertThat(r.getResponse().getStatus()).isEqualTo(202);
        assertThat(r.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        mvc.perform(get("/api/v1/planning/optimisations/" + idDe(r)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.centerId").value(JeuDePlaces.CENTRE.valeur().toString()))
                .andExpect(jsonPath("$.parametres.perimetre").value("PATIENTS"))
                .andExpect(jsonPath("$.parametres.debutSemaine").value("2026-11-01"))
                .andExpect(jsonPath("$.parametres.dureeMaxSecondes").value(20))
                .andExpect(jsonPath("$.lancePar").value(COMPTE.toString()))
                .andExpect(jsonPath("$.empreinte").isString())
                .andExpect(jsonPath("$.resultat").doesNotExist());
    }

    @Test
    void should_default_the_start_to_the_week_of_the_centre_day_and_apply_the_defaults_of_the_other_parameters() throws Exception {
        MvcResult r = lancer("{\"perimetre\":\"COUVERTURE\",\"nbSemaines\":3}");
        mvc.perform(get("/api/v1/planning/optimisations/" + idDe(r)))
                .andExpect(jsonPath("$.parametres.debutSemaine").value("2026-11-01"))
                .andExpect(jsonPath("$.parametres.nbSemaines").value(3))
                .andExpect(jsonPath("$.parametres.stabilite").value(5))
                .andExpect(jsonPath("$.parametres.objectif").value("EQUITE"));
    }

    @Test
    void should_answer_409_for_a_second_launch_while_one_is_in_progress() throws Exception {
        lancer("{\"perimetre\":\"PATIENTS\"}");
        mvc.perform(post("/api/v1/planning/optimisations").contentType(MediaType.APPLICATION_JSON).content("{\"perimetre\":\"COMPLET\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPTIMISATION_DEJA_EN_COURS"));
    }

    @Test
    void should_answer_400_for_every_unreadable_body() throws Exception {
        for (String corps : new String[]{"", "pas du json", "[]", "{}", "{\"perimetre\":\"INCONNU\"}", "{\"perimetre\":\"patients\"}",
                "{\"perimetre\":\"PATIENTS\",\"nbSemaines\":\"deux\"}", "{\"perimetre\":\"PATIENTS\",\"debutSemaine\":\"demain\"}",
                "{\"perimetre\":\"PATIENTS\",\"objectif\":\"AUTRE\"}"}) {
            MvcResult r = lancer(corps);
            assertThat(r.getResponse().getStatus()).as(corps).isEqualTo(400);
            assertThat(r.getResponse().getContentAsString()).as(corps).contains("BAD_REQUEST");
        }
        assertThat(m.optimisation.executions).isEmpty();
    }

    @Test
    void should_answer_400_for_every_parameter_out_of_its_bounds() throws Exception {
        for (String corps : new String[]{"{\"perimetre\":\"PATIENTS\",\"nbSemaines\":0}", "{\"perimetre\":\"PATIENTS\",\"nbSemaines\":9}",
                "{\"perimetre\":\"PATIENTS\",\"dureeMaxSecondes\":1}", "{\"perimetre\":\"PATIENTS\",\"dureeMaxSecondes\":301}",
                "{\"perimetre\":\"PATIENTS\",\"stabilite\":11}", "{\"perimetre\":\"PATIENTS\",\"maxVacationsParJour\":0}",
                "{\"perimetre\":\"PATIENTS\",\"maxVacationsParSemaine\":15}"}) {
            assertThat(lancer(corps).getResponse().getStatus()).as(corps).isEqualTo(400);
        }
        assertThat(lancer("{\"perimetre\":\"PATIENTS\",\"nbSemaines\":8,\"dureeMaxSecondes\":300,\"stabilite\":10}").getResponse().getStatus())
                .isEqualTo(202);
    }

    @Test
    void should_answer_404_for_an_unknown_execution_on_every_route_and_400_for_an_identifier_that_is_not_a_uuid() throws Exception {
        String inconnue = "/api/v1/planning/optimisations/" + UUID.randomUUID();
        mvc.perform(get(inconnue)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OPTIMISATION_INTROUVABLE"));
        mvc.perform(post(inconnue + "/arret")).andExpect(status().isNotFound());
        mvc.perform(post(inconnue + "/application")).andExpect(status().isNotFound());
        mvc.perform(delete(inconnue)).andExpect(status().isNotFound());
        mvc.perform(get(inconnue + "/calendrier")).andExpect(status().isNotFound());
        mvc.perform(get(inconnue + "/impression")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/planning/optimisations/pas-un-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get(inconnue + "/calendrier").param("semaine", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void should_cap_the_page_size_at_100_and_refuse_negative_pagination() throws Exception {
        lancer("{\"perimetre\":\"PATIENTS\"}");
        mvc.perform(get("/api/v1/planning/optimisations").param("size", "1000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.page").value(0));
        mvc.perform(get("/api/v1/planning/optimisations").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/planning/optimisations").param("size", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/planning/optimisations").param("page", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/planning/optimisations").param("page", "5")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void should_answer_422_for_a_deletion_in_progress_and_for_an_execution_that_cannot_be_applied() throws Exception {
        String id = idDe(lancer("{\"perimetre\":\"PATIENTS\"}"));
        mvc.perform(delete("/api/v1/planning/optimisations/" + id)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OPTIMISATION_SUPPRESSION_EN_COURS"));
        mvc.perform(post("/api/v1/planning/optimisations/" + id + "/application")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OPTIMISATION_NON_APPLICABLE"));
    }

    @Test
    void should_answer_202_to_a_stop_with_the_state_before_the_stop() throws Exception {
        String id = idDe(lancer("{\"perimetre\":\"PATIENTS\"}"));
        mvc.perform(post("/api/v1/planning/optimisations/" + id + "/arret")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statut").value("EN_COURS"));
        assertThat(m.optimisation.arrets).hasSize(1);
    }

    @Test
    void should_apply_a_finished_proposition_then_answer_409_to_a_second_application() throws Exception {
        String id = idDe(lancer("{\"perimetre\":\"PATIENTS\"}"));
        m.optimisation.derniereDemande().ecouteur().termine(com.hemodialyse.backend.contexts.planification.doubles.OptimisationEnMemoire
                .resultatVide(), "0hard/0soft");

        mvc.perform(post("/api/v1/planning/optimisations/" + id + "/application")).andExpect(status().isOk())
                .andExpect(jsonPath("$.appliqueLe").isString()).andExpect(jsonPath("$.statut").value("TERMINEE"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        mvc.perform(post("/api/v1/planning/optimisations/" + id + "/application")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISATION_DEJA_APPLIQUEE"));
    }

    @Test
    void should_answer_409_to_the_application_of_an_outdated_proposition() throws Exception {
        String id = idDe(lancer("{\"perimetre\":\"PATIENTS\"}"));
        m.optimisation.derniereDemande().ecouteur().termine(com.hemodialyse.backend.contexts.planification.doubles.OptimisationEnMemoire
                .resultatVide(), "s");
        m.optimisation.donneesDuCentre = new JeuDOptimisation().donnees();

        mvc.perform(post("/api/v1/planning/optimisations/" + id + "/application")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISATION_PERIMEE"));
    }

    @Test
    void should_answer_the_frozen_calendar_with_its_weeks_and_print_it_only_when_it_exists() throws Exception {
        String id = idDe(lancer("{\"perimetre\":\"PATIENTS\",\"nbSemaines\":2}"));
        mvc.perform(get("/api/v1/planning/optimisations/" + id + "/impression")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OPTIMISATION_CALENDRIER_ABSENT"));
        mvc.perform(get("/api/v1/planning/optimisations/" + id + "/calendrier")).andExpect(status().isOk())
                .andExpect(jsonPath("$.semaines.length()").value(0)).andExpect(jsonPath("$.cases.length()").value(0));

        m.optimisation.derniereDemande().ecouteur().termine(com.hemodialyse.backend.contexts.planification.doubles.OptimisationEnMemoire
                .resultatVide(), "s");

        mvc.perform(get("/api/v1/planning/optimisations/" + id + "/calendrier")).andExpect(status().isOk())
                .andExpect(jsonPath("$.semaines.length()").value(2)).andExpect(jsonPath("$.semaine").value("2026-11-01"))
                .andExpect(jsonPath("$.cases.length()").value(1)).andExpect(jsonPath("$.cases[0].salleNom").value("Salle A"))
                .andExpect(jsonPath("$.cases[0].jours.length()").value(7));
        mvc.perform(get("/api/v1/planning/optimisations/" + id + "/calendrier").param("semaine", "2026-11-12"))
                .andExpect(jsonPath("$.semaine").value("2026-11-12")).andExpect(jsonPath("$.cases.length()").value(1));
        mvc.perform(get("/api/v1/planning/optimisations/" + id + "/impression")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"));
    }
}
