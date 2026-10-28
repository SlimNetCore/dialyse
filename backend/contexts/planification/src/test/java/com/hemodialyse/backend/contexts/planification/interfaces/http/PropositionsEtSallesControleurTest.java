package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.ConsulterLesSalles;
import com.hemodialyse.backend.contexts.planification.application.ProposerDesPlaces;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.application.VueDesSalles;
import com.hemodialyse.backend.contexts.planification.doubles.PlacesEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Adapters entrants de l'aide au placement et de la vue des salles : forme du JSON, no-store, 400 pour une entrée illisible. */
class PropositionsEtSallesControleurTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997805-0000-0000-0000-0000000000e1"));
    private static final SalleId SALLE = new SalleId(UUID.fromString("99997805-0000-0000-0000-0000000000e2"));
    private static final CreneauId CRENEAU = new CreneauId(UUID.fromString("99997805-0000-0000-0000-0000000000e3"));
    private static final GenerateurId GENERATEUR = new GenerateurId(UUID.fromString("99997805-0000-0000-0000-0000000000e4"));

    /** Lève un refus du langage publié des places, comme le fait la fiche du patient. */
    @RestController
    static class RefusDePlacement {
        @GetMapping("/test/refus")
        String refus() {
            throw new PlacementDesPatients.Refus("PLACEMENT_GENERATEUR_OCCUPE", "Placement refusé par la planification : [GENERATEUR_OCCUPE]");
        }

        @GetMapping("/test/concurrent")
        String concurrent() {
            throw new PlacementDesPatients.EcritureConcurrente("Le centre est modifié");
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void monter() {
        SessionCourante session = new SessionCourante() {
            @Override
            public CentreId centre(UUID centreDemande) {
                return CENTRE;
            }

            @Override
            public CompteId utilisateur() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean administrateur() {
                return false;
            }
        };
        RessourcesDuCentre ressources = new RessourcesDuCentre(List.of(new RessourcesDuCentre.Salle(SALLE, "Salle 1", false)),
                List.of(new RessourcesDuCentre.Creneau(CRENEAU, "Matin", 1)),
                List.of(new RessourcesDuCentre.Generateur(GENERATEUR, "G1", SALLE)), Set.of(), List.of());
        PlacesEnMemoire monde = new PlacesEnMemoire(ressources);
        ProposerDesPlaces proposer = new ProposerDesPlaces(monde, monde, monde, monde, monde);
        VueDesSalles vue = (centre, pagination) -> new VueDesSalles.Page(List.of(new VueDesSalles.SalleVue(SALLE.valeur(), "S1",
                "Salle 1", false, 2, List.of(new VueDesSalles.GenerateurVue(GENERATEUR.valeur(), "G1", "Equipement G1", "EN_SERVICE")))),
                1);
        mvc = MockMvcBuilders.standaloneSetup(new PropositionsControleur(session, proposer),
                        new SallesControleur(session, new ConsulterLesSalles(vue)), new RefusDePlacement())
                .setControllerAdvice(new ErreursMetierDeLaPlanificationAdvice(), new EntreesDeLaPlanificationAdvice(),
                        new EcritureConcurrenteDesFichesAdvice())
                .build();
    }

    @Test
    void should_propose_the_free_places_with_the_json_of_the_legacy() throws Exception {
        mvc.perform(get("/api/v1/planning/affectations").param("seances", "1").param("jours", "LUNDI"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.patientARisque").value(false))
                .andExpect(jsonPath("$.salles[0].nom").value("Salle 1"))
                .andExpect(jsonPath("$.creneaux[0].ordre").value(1))
                .andExpect(jsonPath("$.propositions[0].generateur.code").value("G1"))
                .andExpect(jsonPath("$.propositions[0].jours[0]").value(JourDeLaSemaine.LUNDI.name()))
                .andExpect(jsonPath("$.grille.length()").value(7))
                .andExpect(jsonPath("$.alertes.length()").value(0));
    }

    @Test
    void should_answer_400_instead_of_500_for_unreadable_or_out_of_range_parameters() throws Exception {
        mvc.perform(get("/api/v1/planning/affectations").param("seances", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/planning/affectations").param("seances", "2.5")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/planning/affectations").param("seances", "8")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/planning/affectations").param("jours", "lundi")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/planning/affectations").param("creneauId", "pas-un-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void should_list_the_rooms_with_capacity_and_remaining_places() throws Exception {
        mvc.perform(get("/api/v1/planning/salles"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].nbGenerateurs").value(1))
                .andExpect(jsonPath("$.items[0].placesRestantes").value(1))
                .andExpect(jsonPath("$.items[0].depassement").value(false))
                .andExpect(jsonPath("$.items[0].generateurs[0].statut").value("EN_SERVICE"));
    }

    @Test
    void should_cap_the_page_size_at_100_and_refuse_negative_pages_and_sizes() throws Exception {
        mvc.perform(get("/api/v1/planning/salles").param("size", "1000")).andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/api/v1/planning/salles").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/planning/salles").param("size", "-5")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/planning/salles").param("size", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void should_render_a_placement_refused_by_the_planification_as_422_with_a_stable_code() throws Exception {
        mvc.perform(get("/test/refus").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PLACEMENT_GENERATEUR_OCCUPE"))
                .andExpect(jsonPath("$.title").value("Regle metier violee"));
    }

    @Test
    void should_render_a_concurrent_write_of_the_places_as_409() throws Exception {
        mvc.perform(get("/test/concurrent"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLANIFICATION_ECRITURE_CONCURRENTE"));
    }
}
