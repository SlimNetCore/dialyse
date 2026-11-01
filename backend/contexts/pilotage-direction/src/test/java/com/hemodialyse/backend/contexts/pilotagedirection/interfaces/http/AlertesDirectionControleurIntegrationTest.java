package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.PilotageDirectionTestApplication;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TypeAlerte;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration du contrôleur {@link AlertesDirectionControleur} contre la vraie pile
 * (service + adaptateur JDBC + H2). Vérifie l'isolation multi-centre et l'acquittement.
 */
@SpringBootTest(classes = PilotageDirectionTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:pilotage-alertes-ctrl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AlertesDirectionControleurIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AlerteDirectionRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM alertes_direction");
    }

    private UUID insererAlerte(CentreId centre, LocalDate date, TypeAlerte type) {
        OffsetDateTime maintenant = OffsetDateTime.ofInstant(
                java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);
        AlerteDirection a = AlerteDirection.generer(new AlerteDirectionId(UUID.randomUUID()),
                centre, type, "seuil", "message", date, maintenant);
        repository.save(a);
        return a.id().value();
    }

    @Test
    void lister_retourne_page_pour_centre() throws Exception {
        insererAlerte(CENTRE_A, LocalDate.of(2026, 3, 10), TypeAlerte.SOUS_EFFECTIF);
        insererAlerte(CENTRE_B, LocalDate.of(2026, 3, 10), TypeAlerte.SOUS_EFFECTIF);

        mvc.perform(get("/api/v1/pilotage/alertes")
                        .param("centreId", CENTRE_A.valeur().toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void generer_alerte() throws Exception {
        String json = """
                {
                  "centreId": "%s",
                  "type": "SOUS_EFFECTIF",
                  "seuil": "ratio<0.8",
                  "message": "Sous-effectif détecté",
                  "date": "2026-03-10"
                }
                """.formatted(CENTRE_A.valeur());
        mvc.perform(post("/api/v1/pilotage/alertes")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").isNotEmpty());
    }

    @Test
    void acquitter_alerte() throws Exception {
        UUID id = insererAlerte(CENTRE_A, LocalDate.of(2026, 3, 10), TypeAlerte.SOUS_EFFECTIF);

        String centre = """
                {"centreId": "%s"}
                """.formatted(CENTRE_A.valeur());
        mvc.perform(post("/api/v1/pilotage/alertes/{alerteId}/acquittement", id)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(centre))
                .andExpect(status().isOk());

        AlerteDirection relue = repository.trouver(new AlerteDirectionId(id), CENTRE_A).orElseThrow();
        assertTrue(relue.lue());
    }
}
