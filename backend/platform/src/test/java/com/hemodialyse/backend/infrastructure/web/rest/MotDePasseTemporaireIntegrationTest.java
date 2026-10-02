package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mot de passe temporaire : tant qu'il n'est pas remplacé, l'API répond 403 {@code PASSWORD_CHANGE_REQUIRED} (hors
 * routes d'authentification) ; le changement exige l'ancien mot de passe, refuse un mot de passe faible ou identique,
 * lève le blocage et enregistre un nouveau hachage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MotDePasseTemporaireIntegrationTest {

    private static final UUID USER = UUID.fromString("99998300-0000-0000-0000-0000000000a1");
    private static final String USERNAME = "it-mdp-sara";
    private static final String TEMPORAIRE = "Temp-4bc9Xk2m!";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CacheManager caches;
    private MockMvc mockMvc;

    private static UserPrincipal principal() {
        return UserPrincipal.create(USER.toString(), UUID.randomUUID().toString(), USERNAME, "", List.of("INFIRMIER"), true);
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        caches.getCache("auth.mustChangePassword").clear();   // même identifiant d'un test à l'autre
        cleanup();
        jdbc.update("INSERT INTO app_user (id, username, password_hash, full_name, active, created_at, must_change_password) "
                        + "VALUES (?, ?, ?, 'Sara', TRUE, ?, TRUE)", USER, USERNAME, passwordEncoder.encode(TEMPORAIRE),
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM app_user WHERE username = ?", USERNAME);
    }

    private String corps(String actuel, String nouveau) {
        return "{\"currentPassword\":\"" + actuel + "\",\"newPassword\":\"" + nouveau + "\"}";
    }

    private boolean doitChanger() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT must_change_password FROM app_user WHERE id = ?", Boolean.class, USER));
    }

    @Test
    void the_api_is_blocked_until_the_temporary_password_is_replaced_then_released() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping").with(user(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/v1/system/ping")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/change-password").with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON).content(corps(TEMPORAIRE, "Nouveau-mdp-2026")))
                .andExpect(status().isNoContent());

        assertFalse(doitChanger());
        mockMvc.perform(get("/api/v1/system/ping").with(user(principal()))).andExpect(status().isOk());
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, USER);
        assertTrue(passwordEncoder.matches("Nouveau-mdp-2026", hash));
        assertFalse(passwordEncoder.matches(TEMPORAIRE, hash));
    }

    @Test
    void the_change_is_refused_for_a_wrong_current_identical_or_weak_password() throws Exception {
        mockMvc.perform(post("/api/v1/auth/change-password").with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON).content(corps("mauvais", "Nouveau-mdp-2026")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PASSWORD_ACTUEL_INVALIDE"));
        mockMvc.perform(post("/api/v1/auth/change-password").with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON).content(corps(TEMPORAIRE, TEMPORAIRE)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PASSWORD_IDENTIQUE"));
        for (String faible : List.of("court1", "sansaucunchiffre", "1234567890123", "xx-" + USERNAME + "-123")) {
            mockMvc.perform(post("/api/v1/auth/change-password").with(user(principal()))
                            .contentType(MediaType.APPLICATION_JSON).content(corps(TEMPORAIRE, faible)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("PASSWORD_TROP_FAIBLE"));
        }

        assertTrue(doitChanger());
        mockMvc.perform(get("/api/v1/system/ping").with(user(principal()))).andExpect(status().isForbidden());
    }

    @Test
    void the_change_requires_authentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON).content(corps(TEMPORAIRE, "Nouveau-mdp-2026")))
                .andExpect(status().is4xxClientError());
        assertTrue(doitChanger());
    }
}
