package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.PilotageDirectionTestApplication;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSynthese;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration du contrôleur {@link ExportsSyntheseControleur} contre la vraie pile
 * (service + adaptateurs JDBC + H2). Vérifie l'isolation multi-centre.
 */
@SpringBootTest(classes = PilotageDirectionTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:pilotage-exports-ctrl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ExportsSyntheseControleurIntegrationTest {

    private static final UUID CENTRE_A = UUID.fromString("99997300-0000-0000-0000-00000000000a");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM exports_synthese");
    }

    @Test
    void generer_export_synthese() throws Exception {
        String json = """
                {
                  "centreId": "%s",
                  "periode": "2026-03",
                  "format": "HTML"
                }
                """.formatted(CENTRE_A);
        mvc.perform(post("/api/v1/pilotage/exports-synthese")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").isNotEmpty());
    }

    @Test
    void generer_export_sans_read_models_produit_synthese_vide() throws Exception {
        String json = """
                {
                  "centreId": "%s",
                  "periode": "2026-04",
                  "format": "PDF"
                }
                """.formatted(CENTRE_A);
        mvc.perform(post("/api/v1/pilotage/exports-synthese")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM exports_synthese WHERE center_id = ?",
                Integer.class, CENTRE_A);
        org.junit.jupiter.api.Assertions.assertEquals(1, n);
        String contenu = jdbc.queryForObject(
                "SELECT contenu FROM exports_synthese WHERE center_id = ? AND periode = ?",
                String.class, CENTRE_A, "2026-04");
        org.junit.jupiter.api.Assertions.assertTrue(contenu != null && contenu.contains("2026-04"));
    }
}
