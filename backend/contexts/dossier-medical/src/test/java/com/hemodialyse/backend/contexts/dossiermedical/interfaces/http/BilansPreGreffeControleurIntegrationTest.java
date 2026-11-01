package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.DossierMedicalTestApplication;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeBilan;
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
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration du contrôleur {@link BilansPreGreffeControleur} contre la vraie pile
 * (service + adaptateur JDBC + H2). Vérifie l'isolation multi-centre et la pagination.
 */
@SpringBootTest(classes = DossierMedicalTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:dossier-medical-bilans-ctrl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BilansPreGreffeControleurIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT_A = UUID.fromString("99997300-0000-0000-0000-000000000101");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private BilanPreGreffeRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;
    private OffsetDateTime baseTime;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        baseTime = java.time.Instant.parse("2026-03-10T08:00:00Z").atOffset(ZoneOffset.UTC);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM etapes_bilan_pregreffe");
        jdbc.update("DELETE FROM bilans_pregreffe");
    }

    private UUID insererBilan(CentreId centre, UUID patient) {
        UUID id = UUID.randomUUID();
        BilanPreGreffe b = BilanPreGreffe.demarrer(
                new BilanPreGreffeId(id), patient, centre,
                List.of(EtapeBilan.aFaire("CARDIO", "Bilan cardiaque")),
                LocalDate.of(2026, 3, 10), null, baseTime);
        repository.save(b);
        return id;
    }

    @Test
    void lister_retourne_page_pour_centre() throws Exception {
        insererBilan(CENTRE_A, PATIENT_A);
        insererBilan(CENTRE_B, PATIENT_A);

        mvc.perform(get("/api/v1/dossier-medical/bilans-pregreffe")
                        .param("centreId", CENTRE_A.valeur().toString())
                        .param("patientId", PATIENT_A.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items").isNotEmpty());
    }

    @Test
    void consulter_par_id_et_centre() throws Exception {
        UUID id = insererBilan(CENTRE_A, PATIENT_A);

        mvc.perform(get("/api/v1/dossier-medical/bilans-pregreffe/{bilanId}", id)
                        .param("centreId", CENTRE_A.valeur().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void demarrer_bilan() throws Exception {
        String json = """
                {
                  "patientId": "%s",
                  "centreId": "%s",
                  "etapes": [{"code": "CARDIO", "libelle": "Bilan cardiaque"}],
                  "dateDemarrage": "2026-03-10",
                  "commentaire": null
                }
                """.formatted(PATIENT_A, CENTRE_A.valeur());
        mvc.perform(post("/api/v1/dossier-medical/bilans-pregreffe")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").isNotEmpty());
    }

    @Test
    void valider_etape_puis_completer() throws Exception {
        UUID id = insererBilan(CENTRE_A, PATIENT_A);

        String validation = """
                {"centreId": "%s", "validePar": "MED-001"}
                """.formatted(CENTRE_A.valeur());
        mvc.perform(post("/api/v1/dossier-medical/bilans-pregreffe/{bilanId}/etapes/{code}/validation",
                        id, "CARDIO")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(validation))
                .andExpect(status().isOk());

        String centre = """
                {"centreId": "%s"}
                """.formatted(CENTRE_A.valeur());
        mvc.perform(post("/api/v1/dossier-medical/bilans-pregreffe/{bilanId}/completion", id)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(centre))
                .andExpect(status().isOk());

        // Le corps sérialisé du domaine n'est pas contractuel (accesseurs style record) :
        // l'état est vérifié via le port de sortie.
        BilanPreGreffe relu = repository.trouver(new BilanPreGreffeId(id), CENTRE_A).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(relu.estComplet());
    }
}
