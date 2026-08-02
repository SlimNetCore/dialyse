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
class SeanceDetailsIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PATIENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SEANCE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PEC_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID FORFAIT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID GENERATEUR_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setupMockMvc() {
        ensureForfaitTable();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM prise_en_charge WHERE id = ?", PEC_ID);
        jdbc.update("DELETE FROM seances WHERE id = ?", SEANCE_ID);
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM forfait WHERE id = ?", FORFAIT_ID);
        jdbc.update("DELETE FROM generateur WHERE id = ?", GENERATEUR_ID);
    }

    @Test
    void details_should_return_forfait_from_schema_columns_without_security_fallback_403() throws Exception {
        cleanup();
        seedPatient();
        seedGenerateur();
        seedForfait();
        seedPec();
        seedSeance();

        mockMvc.perform(get("/api/v1/seances/{seanceId}", SEANCE_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seance.id").value(SEANCE_ID.toString()))
                .andExpect(jsonPath("$.patient.id").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.patient.generateurId").value(GENERATEUR_ID.toString()))
                .andExpect(jsonPath("$.patient.generateurNom").value("G01"))
                .andExpect(jsonPath("$.patient.generateurMarque").value("Fresenius"))
                .andExpect(jsonPath("$.patient.generateurEtat").value("FONCTIONNEL"))
                .andExpect(jsonPath("$.forfait.id").value(FORFAIT_ID.toString()))
                .andExpect(jsonPath("$.forfait.code").value("F-SEANCE"))
                .andExpect(jsonPath("$.forfait.nom").value("Forfait séance test"));
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
                "PAT-SEANCE-001",
                "Dupont",
                "Jean",
                "M",
                LocalDate.of(2026, 7, 1),
                "ASS-0001",
                "NON_VACANCIER",
                GENERATEUR_ID,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private void seedGenerateur() {
        jdbc.update(
                "INSERT INTO generateur (id, salle_id, center_id, numero, marque, modele, etat) VALUES (?, ?, ?, ?, ?, ?, ?)",
                GENERATEUR_ID,
                UUID.fromString("50000001-0000-0000-0000-000000000001"),
                CENTER_ID,
                "G01",
                "Fresenius",
                "5008S",
                "FONCTIONNEL"
        );
    }

    private void seedSeance() {
        jdbc.update(
                "INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                SEANCE_ID,
                PATIENT_ID,
                CENTER_ID,
                LocalDate.of(2026, 7, 25),
                "CREE",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private void seedPec() {
        jdbc.update(
                """
                        INSERT INTO prise_en_charge (
                            id, patient_id, center_id, date_debut_demande, date_fin_demande,
                            forfait_demande_id, statut, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                PEC_ID,
                PATIENT_ID,
                CENTER_ID,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                FORFAIT_ID,
                "VALIDEE",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private void seedForfait() {
        jdbc.update(
                "INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, ?, ?, ?)",
                FORFAIT_ID,
                CENTER_ID,
                "F-SEANCE",
                "Forfait séance test",
                java.math.BigDecimal.valueOf(3500)
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



