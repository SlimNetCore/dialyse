package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.DossierMedicalTestApplication;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration du contrôleur {@link AllergiesControleur} contre la vraie pile
 * (service + adaptateur JDBC + H2). Vérifie l'isolation multi-centre, la pagination et le CRUD.
 */
@SpringBootTest(classes = DossierMedicalTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:dossier-medical-allergies-ctrl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AllergiesControleurIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997500-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997500-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT_A = UUID.fromString("99997500-0000-0000-0000-000000000101");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AllergieRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;
    private OffsetDateTime baseTime;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        baseTime = Instant.parse("2026-03-10T08:00:00Z").atOffset(ZoneOffset.UTC);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM allergies");
    }

    private UUID insererAllergie(CentreId centre, UUID patient, OffsetDateTime createdAt) {
        UUID id = UUID.randomUUID();
        Allergie a = com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie.reconstituer(
                new com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieId(id), patient, centre,
                "Pénicilline", com.hemodialyse.backend.contexts.dossiermedical.domain.TypeAllergie.MEDICAMENTOSE,
                "Éruption cutanée", LocalDate.of(2020, 1, 1), null,
                com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique.ACTIF, null, null,
                createdAt, createdAt);
        repository.save(a);
        return id;
    }

    @Test
    void lister_retourne_page_pour_centre() throws Exception {
        insererAllergie(CENTRE_A, PATIENT_A, baseTime);
        insererAllergie(CENTRE_B, PATIENT_A, baseTime);

        mvc.perform(get("/api/v1/dossier-medical/allergies")
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
        UUID id = insererAllergie(CENTRE_A, PATIENT_A, baseTime);

        mvc.perform(get("/api/v1/dossier-medical/allergies/{allergieId}", id)
                        .param("centreId", CENTRE_A.valeur().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void lister_ne_retourne_que_donnees_du_centre_demande() throws Exception {
        insererAllergie(CENTRE_A, PATIENT_A, baseTime);
        insererAllergie(CENTRE_B, PATIENT_A, baseTime);

        mvc.perform(get("/api/v1/dossier-medical/allergies")
                        .param("centreId", CENTRE_B.valeur().toString())
                        .param("patientId", PATIENT_A.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items").isNotEmpty());
    }

    @Test
    void creer_allergie() throws Exception {
        String json = """
                {
                  "patientId": "%s",
                  "centreId": "%s",
                  "allergene": "Pénicilline",
                  "type": "MEDICAMENTOSE",
                  "reaction": "Éruption cutanée",
                  "dateDebut": "2020-01-01",
                  "severite": "sévère",
                  "note": null
                }
                """.formatted(PATIENT_A, CENTRE_A.valeur());
        mvc.perform(post("/api/v1/dossier-medical/allergies")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").isNotEmpty());
    }

    @Test
    void modifier_allergie() throws Exception {
        UUID id = insererAllergie(CENTRE_A, PATIENT_A, baseTime);
        String json = """
                {
                  "reaction": "Œdème de Quincke",
                  "dateDebut": "2019-01-01",
                  "severite": "grave",
                  "note": null
                }
                """;
        mvc.perform(put("/api/v1/dossier-medical/allergies/{allergieId}", id)
                        .param("centreId", CENTRE_A.valeur().toString())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void supprimer_allergie() throws Exception {
        UUID id = insererAllergie(CENTRE_A, PATIENT_A, baseTime);

        mvc.perform(delete("/api/v1/dossier-medical/allergies/{allergieId}", id)
                        .param("centreId", CENTRE_A.valeur().toString()))
                .andExpect(status().isNoContent());
    }
}
