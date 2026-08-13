package com.hemodialyse.backend.infrastructure.web.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PatientGenerateurIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99993000-0000-0000-0000-000000000001");
    private static final UUID PATIENT_ID = UUID.fromString("99993000-0000-0000-0000-000000000002");
    private static final UUID GENERATEUR_ID = UUID.fromString("99993000-0000-0000-0000-000000000003");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM generateur WHERE id = ?", GENERATEUR_ID);
    }

    @Test
    void get_should_return_patient_with_generateur_details() throws Exception {
        seedGenerateur();
        seedPatient();

        mockMvc.perform(get("/api/v1/patients/{id}", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .param("userId", "admin")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id.value").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.generateurId").value(GENERATEUR_ID.toString()))
                .andExpect(jsonPath("$.generateurNom").value("G10"))
                .andExpect(jsonPath("$.generateurMarque").value("Fresenius"))
                .andExpect(jsonPath("$.generateurEtat").value("FONCTIONNEL"));
    }

    private void seedGenerateur() {
        jdbc.update(
                "INSERT INTO generateur (id, salle_id, center_id, numero, marque, modele, etat) VALUES (?, ?, ?, ?, ?, ?, ?)",
                GENERATEUR_ID,
                UUID.fromString("50000001-0000-0000-0000-000000000001"),
                CENTER_ID,
                "G10",
                "Fresenius",
                "5008S",
                "FONCTIONNEL"
        );
    }

    private void seedPatient() {
        jdbc.update(
                """
                        INSERT INTO patients (
                            id, center_id, code_patient, nom, prenom, sexe, date_admission,
                            numero_assurance, type_patient, generateur_id, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                PATIENT_ID,
                CENTER_ID,
                "PAT-GEN-001",
                "Dupont",
                "Jean",
                "M",
                LocalDate.of(2026, 7, 1),
                "ASS-1001",
                "NON_VACANCIER",
                GENERATEUR_ID,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}

