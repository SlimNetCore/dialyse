package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.DossierMedicalTestApplication;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeSerologie;
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
 * Test d'intégration du contrôleur {@link SerologiesControleur} contre la vraie pile
 * (service + adaptateur JDBC + H2). Vérifie l'isolation multi-centre et la pagination.
 */
@SpringBootTest(classes = DossierMedicalTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:dossier-medical-serologies-ctrl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SerologiesControleurIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT_A = UUID.fromString("99997300-0000-0000-0000-000000000101");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private SerologieRepository repository;
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
        jdbc.update("DELETE FROM serologies");
    }

    private UUID insererSerologie(CentreId centre, UUID patient, TypeSerologie type,
                                  ResultatSerologie resultat, OffsetDateTime createdAt) {
        UUID id = UUID.randomUUID();
        Serologie s = Serologie.reconstituer(
                new SerologieId(id), patient, centre, "MED-001",
                LocalDate.of(2026, 3, 10), type, resultat,
                "45.0", "UI/mL", Instant.parse("2026-03-11T10:00:00Z"),
                "Labo Central", "Bilan",
                createdAt, createdAt);
        repository.save(s);
        return id;
    }

    @Test
    void lister_retourne_page_pour_centre() throws Exception {
        insererSerologie(CENTRE_A, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.NEGATIF, baseTime);
        insererSerologie(CENTRE_B, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.NEGATIF, baseTime);

        mvc.perform(get("/api/v1/dossier-medical/serologies")
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
        UUID id = insererSerologie(CENTRE_A, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.NEGATIF, baseTime);

        mvc.perform(get("/api/v1/dossier-medical/serologies/{serologieId}", id)
                        .param("centreId", CENTRE_A.valeur().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void lister_ne_retourne_que_donnees_du_centre_demande() throws Exception {
        insererSerologie(CENTRE_A, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.NEGATIF, baseTime);
        insererSerologie(CENTRE_B, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.POSITIF, baseTime);

        // Le centre B ne doit voir que ses propres sérologies (isolation multi-centre)
        mvc.perform(get("/api/v1/dossier-medical/serologies")
                        .param("centreId", CENTRE_B.valeur().toString())
                        .param("patientId", PATIENT_A.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items").isNotEmpty());
    }

    @Test
    void creer_serologie() throws Exception {
        String json = """
                {
                  "patientId": "%s",
                  "centreId": "%s",
                  "prescripteurId": "MED-001",
                  "datePrelevement": "2026-03-10",
                  "typeSerologie": "VHB",
                  "resultat": "POSITIF",
                  "valeur": "45.0",
                  "unite": "UI/mL",
                  "dateResultat": "2026-03-11T10:00:00Z",
                  "laboratoire": "Labo Central",
                  "commentaire": "Bilan de routine"
                }
                """.formatted(PATIENT_A, CENTRE_A.valeur());
        mvc.perform(post("/api/v1/dossier-medical/serologies")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").isNotEmpty());
    }

    @Test
    void lister_uniquement_du_centre_actif() throws Exception {
        insererSerologie(CENTRE_A, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.NEGATIF, baseTime);
        insererSerologie(CENTRE_B, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.POSITIF, baseTime);

        mvc.perform(get("/api/v1/dossier-medical/serologies")
                        .param("centreId", CENTRE_A.valeur().toString())
                        .param("patientId", PATIENT_A.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }
}
