package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.StatutEcriture;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ComptabiliteRestControllerIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99992000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("99992000-0000-0000-0000-000000000101");
    private static final UUID TIERS_ID = UUID.fromString("99992000-0000-0000-0000-000000000201");
    private static final LocalDate DATE_ECRITURE = LocalDate.of(2026, 8, 15);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EcritureComptableRepositoryPort ecritureRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        cleanup();
        seedEcriture();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void search_should_return_saved_ecriture_for_center_and_period() throws Exception {
        mockMvc.perform(get("/api/v1/comptabilite/ecritures")
                        .param("centerId", CENTER_ID.toString())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].centerId").value(CENTER_ID.toString()))
                .andExpect(jsonPath("$.items[0].numeroPiece").value("VE-2026-000001"))
                .andExpect(jsonPath("$.items[0].journalCode").value("VE"))
                .andExpect(jsonPath("$.items[0].statut").value("VALIDEE"))
                .andExpect(jsonPath("$.items[0].lignes.length()").value(2));
    }

    @Test
    void search_should_filter_by_statut_when_requested() throws Exception {
        mockMvc.perform(get("/api/v1/comptabilite/ecritures")
                        .param("centerId", CENTER_ID.toString())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("statut", "VALIDEE")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/v1/comptabilite/ecritures")
                        .param("centerId", CENTER_ID.toString())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("statut", "EXPORTEE")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private void seedEcriture() {
        EcritureComptable ecriture = new EcritureComptable(
                UUID.randomUUID(),
                CENTER_ID,
                JournalCode.VE,
                DATE_ECRITURE,
                DATE_ECRITURE,
                "VE-2026-000001",
                "Fact. FACT-2026-000001 - Patient test",
                List.of(
                        LigneEcriture.debit("411500", "Client", new BigDecimal("1190.00"), TIERS_ID, List.of()),
                        LigneEcriture.credit("706", "Prestation", new BigDecimal("1190.00"), null, List.of())
                ),
                StatutEcriture.VALIDEE,
                SOURCE_ID
        );
        ecritureRepository.save(ecriture);
    }

    private void cleanup() {
        jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id IN (SELECT id FROM ecritures_comptables WHERE center_id = ?)", CENTER_ID);
        jdbc.update("DELETE FROM ecritures_comptables WHERE center_id = ?", CENTER_ID);
    }
}

