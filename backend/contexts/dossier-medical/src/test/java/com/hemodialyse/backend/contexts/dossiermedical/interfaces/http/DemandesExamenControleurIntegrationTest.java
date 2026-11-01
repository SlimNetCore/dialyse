package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.DossierMedicalTestApplication;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDemandeExamen;
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
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'int\u00e9gration du contr\u00f4leur {@link DemandesExamenControleur} contre la vraie pile
 * (service + adaptateur JDBC + H2). V\u00e9rifie l'isolation multi-centre et la pagination.
 */
@SpringBootTest(classes = DossierMedicalTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:dossier-medical-examens-ctrl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class DemandesExamenControleurIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT_A = UUID.fromString("99997300-0000-0000-0000-000000000101");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DemandeExamenRepository repository;
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
        jdbc.update("DELETE FROM lignes_demande_examen");
        jdbc.update("DELETE FROM demandes_examen");
    }

    private UUID insererDemande(CentreId centre, UUID patient, StatutDemandeExamen statut, OffsetDateTime createdAt) {
        UUID id = UUID.randomUUID();
        DemandeExamen d = DemandeExamen.reconstituer(
                new DemandeExamenId(id), patient, centre, "MED-001",
                LocalDate.of(2026, 3, 10), CategorieExamen.BIOLOGIE, false, "Bilan",
                statut, null,
                List.of(LigneDemandeExamen.creer(UUID.randomUUID(), "LOINC", "1234-5", "Sodium", "Sodium s\u00e9rum", null)),
                createdAt, createdAt);
        repository.save(d);
        return id;
    }

    @Test
    void lister_retourne_page_pour_centre() throws Exception {
        UUID idA = insererDemande(CENTRE_A, PATIENT_A, StatutDemandeExamen.DEMANDE, baseTime);
        insererDemande(CENTRE_B, PATIENT_A, StatutDemandeExamen.DEMANDE, baseTime);

        mvc.perform(get("/api/v1/dossier-medical/demandes-examen")
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
        UUID id = insererDemande(CENTRE_A, PATIENT_A, StatutDemandeExamen.DEMANDE, baseTime);

        mvc.perform(get("/api/v1/dossier-medical/demandes-examen/{demandeId}", id)
                        .param("centreId", CENTRE_A.valeur().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void creer_demande_examen() throws Exception {
        String json = """
                {
                  "patientId": "%s",
                  "centreId": "%s",
                  "prescripteurId": "MED-001",
                  "dateDemande": "2026-03-10",
                  "categorie": "BIOLOGIE",
                  "urgent": false,
                  "motif": "Bilan de routine",
                  "lignes": [
                    { "id": "%s", "codeSystem": "LOINC", "code": "1234-5", "codeDisplay": "Sodium", "libelle": "Sodium s\u00e9rum", "commentaire": null }
                  ]
                }
                """.formatted(PATIENT_A, CENTRE_A.valeur(), UUID.randomUUID());
        mvc.perform(post("/api/v1/dossier-medical/demandes-examen")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").isNotEmpty());
    }
}
