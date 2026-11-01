package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.DossierMedicalTestApplication;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration du contrôleur {@link ObservationsBiologiquesControleur} contre la vraie pile
 * (service + adaptateur JDBC + H2). Vérifie l'isolation multi-centre et la pagination.
 */
@SpringBootTest(classes = DossierMedicalTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:dossier-medical-observations-ctrl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ObservationsBiologiquesControleurIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT_A = UUID.fromString("99997300-0000-0000-0000-000000000101");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObservationBiologiqueRepository repository;
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
        jdbc.update("DELETE FROM observations_biologiques");
    }

    private UUID insererObservation(CentreId centre, UUID patient, String code) {
        UUID id = UUID.randomUUID();
        ObservationBiologique o = ObservationBiologique.reconstituer(
                new ObservationBiologiqueId(id), patient, centre, code, "Libellé " + code,
                "11.5", "g/dL", 12.0, 16.0, LocalDate.of(2026, 3, 10),
                "MED-001", "Labo Central", null, baseTime, baseTime);
        repository.save(o);
        return id;
    }

    @Test
    void lister_retourne_page_pour_centre() throws Exception {
        insererObservation(CENTRE_A, PATIENT_A, "HGB");
        insererObservation(CENTRE_B, PATIENT_A, "HGB");

        mvc.perform(get("/api/v1/dossier-medical/observations-biologiques")
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
        UUID id = insererObservation(CENTRE_A, PATIENT_A, "HGB");

        mvc.perform(get("/api/v1/dossier-medical/observations-biologiques/{observationId}", id)
                        .param("centreId", CENTRE_A.valeur().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void lister_ne_retourne_que_donnees_du_centre_demande() throws Exception {
        insererObservation(CENTRE_A, PATIENT_A, "HGB");
        insererObservation(CENTRE_B, PATIENT_A, "CRP");

        mvc.perform(get("/api/v1/dossier-medical/observations-biologiques")
                        .param("centreId", CENTRE_B.valeur().toString())
                        .param("patientId", PATIENT_A.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items").isNotEmpty());
    }

    @Test
    void creer_observation() throws Exception {
        String json = """
                {
                  "patientId": "%s",
                  "centreId": "%s",
                  "code": "HGB",
                  "libelle": "Hémoglobine",
                  "valeur": "11.5",
                  "unite": "g/dL",
                  "seuilBas": 12.0,
                  "seuilHaut": 16.0,
                  "dateObservation": "2026-03-10",
                  "prescripteurId": "MED-001",
                  "laboratoire": "Labo Central",
                  "commentaire": "Bilan de routine"
                }
                """.formatted(PATIENT_A, CENTRE_A.valeur());
        mvc.perform(post("/api/v1/dossier-medical/observations-biologiques")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").isNotEmpty());
    }
}
