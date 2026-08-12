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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class BonSortieRestControllerIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID PATIENT_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID SEANCE_BILLED_ID = UUID.fromString("99999999-9999-9999-9999-999999999991");
    private static final UUID SEANCE_VALIDATED_ID = UUID.fromString("99999999-9999-9999-9999-999999999992");
    private static final UUID BON_BILLED_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID BON_DATE_LOCK_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");

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
        jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id IN (?, ?)", BON_BILLED_ID, BON_DATE_LOCK_ID);
        jdbc.update("DELETE FROM bons_sortie WHERE id IN (?, ?)", BON_BILLED_ID, BON_DATE_LOCK_ID);
        jdbc.update("DELETE FROM seances WHERE id IN (?, ?)", SEANCE_BILLED_ID, SEANCE_VALIDATED_ID);
    }

    @Test
    void update_should_return_explicit_code_when_seance_is_billed() throws Exception {
        cleanup();
        seedSeance(SEANCE_BILLED_ID, "FACTUREE");
        seedBon(BON_BILLED_ID, SEANCE_BILLED_ID, LocalDate.of(2026, 8, 1));

        mockMvc.perform(put("/api/v1/stock/bons-sortie/{id}", BON_BILLED_ID)
                        .with(user("inf-01").roles("INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(SEANCE_BILLED_ID, LocalDate.of(2026, 8, 2))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SEANCE_BILLED_STOCK_EXIT_IMMUTABLE"));
    }

    @Test
    void update_should_return_explicit_code_when_changing_seance_exit_date() throws Exception {
        cleanup();
        seedSeance(SEANCE_VALIDATED_ID, "VALIDEE");
        seedBon(BON_DATE_LOCK_ID, SEANCE_VALIDATED_ID, LocalDate.of(2026, 8, 1));

        mockMvc.perform(put("/api/v1/stock/bons-sortie/{id}", BON_DATE_LOCK_ID)
                        .with(user("inf-01").roles("INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(SEANCE_VALIDATED_ID, LocalDate.of(2026, 8, 2))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SEANCE_STOCK_EXIT_DATE_IMMUTABLE"));
    }

    private void seedSeance(UUID seanceId, String status) {
        jdbc.update(
                "INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                seanceId,
                PATIENT_ID,
                CENTER_ID,
                LocalDate.of(2026, 8, 1),
                status,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private void seedBon(UUID bonId, UUID seanceId, LocalDate dateSortie) {
        jdbc.update(
                "INSERT INTO bons_sortie (id, center_id, reference, seance_id, patient_id, poste, date_sortie, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                bonId,
                CENTER_ID,
                "BS-TEST-" + bonId.toString().substring(0, 6),
                seanceId,
                PATIENT_ID,
                "SEANCE",
                dateSortie,
                "inf-01",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private String updatePayload(UUID seanceId, LocalDate dateSortie) {
        return """
                {
                  "centerId":"%s",
                  "seanceId":"%s",
                  "patientId":"%s",
                  "poste":"SEANCE",
                  "dateSortie":"%s",
                  "userId":"inf-01",
                  "items":[
                    {
                      "articleId":"%s",
                      "lotId":"%s",
                      "quantite":1
                    }
                  ]
                }
                """.formatted(
                CENTER_ID,
                seanceId,
                PATIENT_ID,
                dateSortie,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1")
        );
    }
}



