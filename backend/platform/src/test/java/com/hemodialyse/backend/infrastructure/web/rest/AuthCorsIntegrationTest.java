package com.hemodialyse.backend.infrastructure.web.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test CORS preflight sur l'endpoint d'authentification.
 * <p>
 * Ce test utilise une base H2 isolée (hemodialyse_cors) et @DirtiesContext pour éviter
 * tout conflit de verrous H2 lors d'une exécution parallèle avec les autres @SpringBootTest
 * qui partagent la base commune "hemodialyse".
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.cors.allowed-origins=https://dialysis-beta.vercel.app,http://localhost:4200",
                "app.cors.allowed-origin-patterns=https://*.vercel.app",
                // Base H2 isolée pour éviter les conflits lors d'exécutions parallèles
                "spring.datasource.url=jdbc:h2:mem:hemodialyse_cors;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        }
)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthCorsIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void preflight_should_allow_known_frontend_origin_for_login() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://dialysis-beta.vercel.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://dialysis-beta.vercel.app"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void preflight_should_allow_vercel_preview_origin_when_pattern_is_configured() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://hemodialyse-preview-123.vercel.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://hemodialyse-preview-123.vercel.app"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}


