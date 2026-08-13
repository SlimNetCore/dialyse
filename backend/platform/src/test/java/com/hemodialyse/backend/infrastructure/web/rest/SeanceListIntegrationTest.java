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
class SeanceListIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99992000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CENTER_ID = UUID.fromString("99992000-0000-0000-0000-000000000002");
    private static final UUID PATIENT_ID = UUID.fromString("99992000-0000-0000-0000-000000000101");
    private static final UUID OTHER_PATIENT_ID = UUID.fromString("99992000-0000-0000-0000-000000000102");
    private static final UUID VALIDATED_SEANCE_ID = UUID.fromString("99992000-0000-0000-0000-000000000201");
    private static final UUID BILLED_SEANCE_ID = UUID.fromString("99992000-0000-0000-0000-000000000202");
    private static final UUID OTHER_CENTER_SEANCE_ID = UUID.fromString("99992000-0000-0000-0000-000000000203");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        ensureForfaitTable();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM seances WHERE id IN (?, ?, ?)", VALIDATED_SEANCE_ID, BILLED_SEANCE_ID, OTHER_CENTER_SEANCE_ID);
        jdbc.update("DELETE FROM patients WHERE id IN (?, ?)", PATIENT_ID, OTHER_PATIENT_ID);
    }

    @Test
    void list_should_include_facturee_seances_for_current_center_only() throws Exception {
        cleanup();
        seedPatient(PATIENT_ID, CENTER_ID, "PAT-CAHIER-001", "Dialyse", "Patient");
        seedPatient(OTHER_PATIENT_ID, OTHER_CENTER_ID, "PAT-OTHER-001", "Autre", "Centre");
        seedSeance(VALIDATED_SEANCE_ID, PATIENT_ID, CENTER_ID, LocalDate.of(2026, 8, 1), "VALIDEE");
        seedSeance(BILLED_SEANCE_ID, PATIENT_ID, CENTER_ID, LocalDate.of(2026, 8, 2), "FACTUREE");
        seedSeance(OTHER_CENTER_SEANCE_ID, OTHER_PATIENT_ID, OTHER_CENTER_ID, LocalDate.of(2026, 8, 3), "FACTUREE");

        mockMvc.perform(get("/api/v1/seances")
                        .param("centerId", CENTER_ID.toString())
                        .with(user("secretaire").roles("SECRETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(VALIDATED_SEANCE_ID)).isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id=='%s' && @.status=='FACTUREE')]".formatted(BILLED_SEANCE_ID)).isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(OTHER_CENTER_SEANCE_ID)).isEmpty());
    }

    private void seedPatient(UUID patientId, UUID centerId, String codePatient, String nom, String prenom) {
        jdbc.update(
                """
                        INSERT INTO patients (
                            id, center_id, code_patient, nom, prenom, sexe, date_admission,
                            numero_assurance, type_patient, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                patientId,
                centerId,
                codePatient,
                nom,
                prenom,
                "M",
                LocalDate.of(2026, 7, 1),
                "ASS-" + codePatient,
                "NON_VACANCIER",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private void seedSeance(UUID seanceId, UUID patientId, UUID centerId, LocalDate dateSeance, String statut) {
        jdbc.update(
                "INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                seanceId,
                patientId,
                centerId,
                dateSeance,
                statut,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private void ensureForfaitTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS forfait (
                    id UUID PRIMARY KEY,
                    center_id UUID NOT NULL,
                    code VARCHAR(50) NOT NULL,
                    libelle VARCHAR(255) NOT NULL,
                    prix DECIMAL(10,2)
                )
                """);
    }
}

