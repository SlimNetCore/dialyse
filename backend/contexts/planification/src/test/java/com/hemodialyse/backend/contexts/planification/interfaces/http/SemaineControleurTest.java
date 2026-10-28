package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.ConsulterLaSemaine;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Adapter entrant de la semaine : forme du JSON, no-store, 400 pour une date ou un centre illisible (KB-PLN-67). */
class SemaineControleurTest {

    private MockMvc mvc;

    @BeforeEach
    void monter() {
        SessionCourante session = new SessionCourante() {
            @Override
            public CentreId centre(UUID centreDemande) {
                return JeuDePlaces.CENTRE;
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
        ConsulterLaSemaine consulter = new ConsulterLaSemaine((centre, du) -> JeuDePlaces.ressources(), centre -> List.of(),
                (centre, du, au) -> List.of(), (centre, du, au) -> List.of(), centre -> LocalDate.of(2026, 3, 4));
        mvc = MockMvcBuilders.standaloneSetup(new SemaineControleur(session, consulter)).setControllerAdvice(new EntreesDeLaPlanificationAdvice())
                .build();
    }

    @Test
    void should_answer_the_week_of_the_given_date_in_the_legacy_json_shape_without_caching() throws Exception {
        mvc.perform(get("/api/v1/planning/semaine").param("date", "2026-03-04"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.debut").value("2026-03-01"))
                .andExpect(jsonPath("$.fin").value("2026-03-07"))
                .andExpect(jsonPath("$.jours.length()").value(7))
                .andExpect(jsonPath("$.jours[0].jour").value("DIMANCHE"))
                .andExpect(jsonPath("$.jours[0].ouvertHebdomadaire").value(false))
                .andExpect(jsonPath("$.cellules.length()").value(3 * 2 * 7))
                .andExpect(jsonPath("$.patientsAReplanifier").value(0));
    }

    @Test
    void should_answer_the_current_week_of_the_centre_without_date() throws Exception {
        mvc.perform(get("/api/v1/planning/semaine")).andExpect(status().isOk()).andExpect(jsonPath("$.debut").value("2026-03-01"));
    }

    @Test
    void should_refuse_an_unreadable_date_or_centre_with_400() throws Exception {
        mvc.perform(get("/api/v1/planning/semaine").param("date", "demain")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/planning/semaine").param("date", "2026-13-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/planning/semaine").param("centerId", "pas-un-uuid")).andExpect(status().isBadRequest());
    }
}
