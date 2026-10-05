package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Raccourcis de consommables (par centre, mis en cache) et rappel des dernières séances du poste infirmier.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SeanceStationIntegrationTest {

    private static final UUID CENTER_A = UUID.fromString("88888888-8888-8888-8888-888888888881");
    private static final UUID CENTER_B = UUID.fromString("88888888-8888-8888-8888-888888888882");
    private static final UUID PATIENT = UUID.fromString("88888888-8888-8888-8888-888888888883");
    private static final UUID ARTICLE_1 = UUID.fromString("88888888-8888-8888-8888-888888888884");
    private static final UUID ARTICLE_2 = UUID.fromString("88888888-8888-8888-8888-888888888885");
    private static final UUID ARTICLE_OTHER_CENTER = UUID.fromString("88888888-8888-8888-8888-888888888886");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    private static RequestPostProcessor as(UUID center, String role) {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID().toString(), center.toString(), "u-" + role, "",
                List.of(role), true);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        article(ARTICLE_1, CENTER_A, "SST-1");
        article(ARTICLE_2, CENTER_A, "SST-2");
        article(ARTICLE_OTHER_CENTER, CENTER_B, "SST-B");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM seance_raccourcis_articles WHERE center_id IN (?, ?)", CENTER_A, CENTER_B);
        jdbc.update("DELETE FROM volet_paramedical WHERE center_id = ?", CENTER_A);
        jdbc.update("DELETE FROM seances WHERE center_id IN (?, ?)", CENTER_A, CENTER_B);
        jdbc.update("DELETE FROM articles WHERE center_id IN (?, ?)", CENTER_A, CENTER_B);
    }

    private void article(UUID id, UUID center, String code) {
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, gere_par_lot, active, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, center, code, "Article " + code, "u", BigDecimal.TEN, BigDecimal.ONE, true, true,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void seance(LocalDate date, String statut) {
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), PATIENT, CENTER_A, date, statut, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private String body(UUID... ids) {
        StringBuilder sb = new StringBuilder("{\"articleIds\":[");
        for (int i = 0; i < ids.length; i++) {
            sb.append(i > 0 ? "," : "").append('"').append(ids[i]).append('"');
        }
        return sb.append("]}").toString();
    }

    @Test
    void the_admin_saves_the_shortcuts_in_order_and_the_nurse_reads_them() throws Exception {
        mockMvc.perform(put("/api/v1/seances/raccourcis-consommables").with(as(CENTER_A, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(ARTICLE_2, ARTICLE_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(ARTICLE_2.toString()))
                .andExpect(jsonPath("$[1]").value(ARTICLE_1.toString()));

        mockMvc.perform(get("/api/v1/seances/raccourcis-consommables").with(as(CENTER_A, "INFIRMIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]").value(ARTICLE_2.toString()));
    }

    @Test
    void only_the_admin_can_change_the_shortcuts() throws Exception {
        mockMvc.perform(put("/api/v1/seances/raccourcis-consommables").with(as(CENTER_A, "INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(ARTICLE_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shortcuts_never_leak_to_another_center_nor_accept_a_foreign_article() throws Exception {
        mockMvc.perform(put("/api/v1/seances/raccourcis-consommables").with(as(CENTER_A, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body(ARTICLE_1))).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/seances/raccourcis-consommables").with(as(CENTER_B, "INFIRMIER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(put("/api/v1/seances/raccourcis-consommables").with(as(CENTER_A, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(ARTICLE_OTHER_CENTER)))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/api/v1/seances/raccourcis-consommables").param("centerId", CENTER_A.toString())
                        .with(as(CENTER_B, "INFIRMIER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void the_cached_shortcuts_are_served_until_a_write_evicts_them() throws Exception {
        mockMvc.perform(put("/api/v1/seances/raccourcis-consommables").with(as(CENTER_A, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body(ARTICLE_1))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/seances/raccourcis-consommables").with(as(CENTER_A, "INFIRMIER")))
                .andExpect(jsonPath("$", hasSize(1)));

        // Modification directe en base : le cache (hit) masque le changement…
        jdbc.update("INSERT INTO seance_raccourcis_articles (center_id, article_id, ordre) VALUES (?, ?, 1)", CENTER_A, ARTICLE_2);
        mockMvc.perform(get("/api/v1/seances/raccourcis-consommables").with(as(CENTER_A, "INFIRMIER")))
                .andExpect(jsonPath("$", hasSize(1)));

        // …et une écriture par l'API purge l'entrée du centre.
        mockMvc.perform(put("/api/v1/seances/raccourcis-consommables").with(as(CENTER_A, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body(ARTICLE_1, ARTICLE_2))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/seances/raccourcis-consommables").with(as(CENTER_A, "INFIRMIER")))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void the_recent_sessions_are_the_latest_before_the_date_newest_first_and_scoped_to_the_center() throws Exception {
        LocalDate today = LocalDate.of(2026, 10, 4);
        for (int i = 0; i < 5; i++) {
            seance(today.minusDays(i), "VALIDEE");
        }

        mockMvc.perform(get("/api/v1/seances/patient/{id}/recentes", PATIENT).param("before", today.toString())
                        .param("limit", "3").with(as(CENTER_A, "INFIRMIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].dateSeance").value("2026-10-03"))
                .andExpect(jsonPath("$[2].dateSeance").value("2026-10-01"))
                .andExpect(jsonPath("$[0].status").value("VALIDEE"));

        mockMvc.perform(get("/api/v1/seances/patient/{id}/recentes", PATIENT).param("before", today.toString())
                        .with(as(CENTER_B, "INFIRMIER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/seances/patient/{id}/recentes", PATIENT).with(as(CENTER_A, "SECRETAIRE")))
                .andExpect(status().isForbidden());
    }
}
