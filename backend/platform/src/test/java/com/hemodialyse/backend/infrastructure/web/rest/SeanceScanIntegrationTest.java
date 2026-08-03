package com.hemodialyse.backend.infrastructure.web.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SeanceScanIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PATIENT_ID = UUID.fromString("10000000-0000-0000-0000-000000009991");

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
        jdbc.update("DELETE FROM seances WHERE patient_id = ? AND center_id = ?", PATIENT_ID, CENTER_ID);
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_ID);
    }

    @Test
    void scan_should_return_200_when_patient_has_no_generateur() throws Exception {
        cleanup();
        seedPatientWithoutGenerateur();

        String payload = """
                {
                  "centerId": "%s",
                  "qrCode": "PAT-SCAN-NULL-GEN"
                }
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/seances/scan")
                        .with(user("infirmer-01").roles("INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("CREE"))
                .andExpect(jsonPath("$.generateurId").value(nullValue()))
                .andExpect(jsonPath("$.generateurNom").value(nullValue()))
                .andExpect(jsonPath("$.generateurMarque").value(nullValue()))
                .andExpect(jsonPath("$.generateurEtat").value(nullValue()));

        Long createdSeances = jdbc.queryForObject(
                "SELECT COUNT(1) FROM seances WHERE center_id = ? AND patient_id = ?",
                Long.class,
                CENTER_ID,
                PATIENT_ID
        );
        org.junit.jupiter.api.Assertions.assertEquals(1L, createdSeances == null ? 0L : createdSeances);
    }

    private void seedPatientWithoutGenerateur() {
        jdbc.update(
                """
                        INSERT INTO patients (
                            id, center_id, code_patient, nom, prenom, sexe, date_admission,
                            numero_assurance, type_patient, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE, ?, ?, ?)
                        """,
                PATIENT_ID,
                CENTER_ID,
                "PAT-SCAN-NULL-GEN",
                "Patient",
                "SansGenerateur",
                "M",
                "ASS-SCAN-NULL-GEN",
                "NON_VACANCIER",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}

