package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La performance de la base est réservée au propriétaire (SUPERADMIN). Sur H2 (développement) la mesure n'existe pas :
 * l'API le dit au lieu d'échouer. La lecture réelle de {@code pg_stat_statements} ne se teste que sur PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SupervisionBaseAccessIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99996000-0000-0000-0000-00000000000b");
    private static final String BASE = "/api/v1/supervision/base-donnees";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private static UserPrincipal principal(String role) {
        return UserPrincipal.create(UUID.randomUUID().toString(), CENTRE.toString(), role.toLowerCase(), "",
                List.of(role), true);
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void the_owner_sees_that_the_measure_is_unavailable_on_h2() throws Exception {
        mockMvc.perform(get(BASE + "/statut").with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.raison").value("BASE_NON_POSTGRESQL"));
    }

    @Test
    void the_ranking_is_an_empty_page_when_the_measure_is_unavailable() throws Exception {
        mockMvc.perform(get(BASE + "/requetes").param("tri", "TEMPS_MOYEN").param("size", "10")
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void resetting_the_counters_is_refused_when_the_measure_is_unavailable() throws Exception {
        mockMvc.perform(post(BASE + "/reinitialisation").with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void every_other_profile_is_refused() throws Exception {
        for (String role : List.of("ADMIN", "MEDECIN", "INFIRMIER", "SECRETAIRE", "DIRECTION")) {
            mockMvc.perform(get(BASE + "/statut").with(user(principal(role))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/reinitialisation").with(user(principal(role))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void an_anonymous_caller_is_refused() throws Exception {
        mockMvc.perform(get(BASE + "/requetes")).andExpect(status().is4xxClientError());
    }
}
