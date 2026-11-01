package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.PilotageDirectionTestApplication;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.Mois;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration du contrôleur {@link InstantanesMensuelsControleur} contre la vraie pile
 * (service + adaptateur JDBC + H2). Vérifie l'isolation multi-centre et la pagination.
 */
@SpringBootTest(classes = PilotageDirectionTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:pilotage-instantanes-ctrl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class InstantanesMensuelsControleurIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InstantaneMensuelRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM instantanes_mensuels");
    }

    private void insererInstantane(CentreId centre, int annee, int mois) {
        OffsetDateTime maintenant = OffsetDateTime.ofInstant(
                java.time.Instant.parse("2026-03-31T23:00:00Z"), ZoneOffset.UTC);
        repository.save(InstantaneMensuel.capturer(new InstantaneMensuelId(UUID.randomUUID()),
                centre, Mois.de(annee, mois), "{\"seances\":120}", maintenant));
    }

    @Test
    void lister_retourne_page_pour_centre() throws Exception {
        insererInstantane(CENTRE_A, 2026, 3);
        insererInstantane(CENTRE_B, 2026, 3);

        mvc.perform(get("/api/v1/pilotage/instantanes-mensuels")
                        .param("centreId", CENTRE_A.valeur().toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void capturer_instantane() throws Exception {
        String json = """
                {
                  "centreId": "%s",
                  "annee": 2026,
                  "mois": 3,
                  "donnees": "{\\"seances\\":120}"
                }
                """.formatted(CENTRE_A.valeur());
        mvc.perform(post("/api/v1/pilotage/instantanes-mensuels")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").isNotEmpty());
    }
}
