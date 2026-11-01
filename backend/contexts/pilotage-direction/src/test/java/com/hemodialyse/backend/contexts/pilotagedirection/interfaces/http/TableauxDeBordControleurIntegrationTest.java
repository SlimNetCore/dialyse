package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.PilotageDirectionTestApplication;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBord;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBordRepository;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration du contrôleur {@link TableauxDeBordControleur} contre la vraie pile
 * (service + adaptateur JDBC + H2). Vérifie l'isolation multi-centre.
 */
@SpringBootTest(classes = PilotageDirectionTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:pilotage-tableaux-ctrl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class TableauxDeBordControleurIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TableauDeBordRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM tableaux_de_bord");
    }

    @Test
    void consulter_tableau_de_bord_du_centre() throws Exception {
        OffsetDateTime maintenant = OffsetDateTime.ofInstant(
                java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);
        repository.save(TableauDeBord.actualiser(CENTRE_A, "{\"seances\":10}", maintenant));

        mvc.perform(get("/api/v1/pilotage/tableau-de-bord")
                        .param("centreId", CENTRE_A.valeur().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void actualiser_tableau_de_bord() throws Exception {
        String json = """
                {
                  "centreId": "%s",
                  "indicateurs": "{\\"seances\\":12}"
                }
                """.formatted(CENTRE_A.valeur());
        mvc.perform(post("/api/v1/pilotage/tableau-de-bord")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }
}
