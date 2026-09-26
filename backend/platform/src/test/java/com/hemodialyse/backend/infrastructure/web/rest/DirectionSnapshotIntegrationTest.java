package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.direction.DirectionSnapshotService;
import com.hemodialyse.backend.infrastructure.security.RoleScopeFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Instantanés mensuels et rapport PDF de la direction : figés une seule fois, cloisonnés par société, mois écoulés
 * uniquement, tâche planifiée rejouable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DirectionSnapshotIntegrationTest {

    private static final UUID SOC_A = UUID.fromString("99996000-0000-0000-0000-000000000001");
    private static final UUID SOC_B = UUID.fromString("99996000-0000-0000-0000-000000000002");
    private static final UUID CENTRE_A = UUID.fromString("99996000-0000-0000-0000-0000000000a1");
    private static final UUID CENTRE_B = UUID.fromString("99996000-0000-0000-0000-0000000000b1");
    private static final String PASSWORD = "Direction-Snapshot-2026";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private RoleScopeFilter roleScopeFilter;
    @Autowired
    private DirectionSnapshotService snapshots;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(roleScopeFilter).build();
        cleanup();
        societe(SOC_A, "ZT-SN1", CENTRE_A, "Centre Snap A");
        societe(SOC_B, "ZT-SN2", CENTRE_B, "Centre Snap B");
        direction("zt-snap-a", SOC_A);
        direction("zt-snap-b", SOC_B);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM direction_snapshot WHERE societe_id IN (?, ?)", SOC_A, SOC_B);
        jdbc.update("DELETE FROM auth_refresh_token WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'zt-snap-%')");
        jdbc.update("DELETE FROM app_user_societe WHERE societe_id IN (?, ?)", SOC_A, SOC_B);
        jdbc.update("DELETE FROM app_user_role WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'zt-snap-%')");
        jdbc.update("DELETE FROM app_user WHERE username LIKE 'zt-snap-%'");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-SN%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-SN%'");
    }

    @Test
    void a_past_month_is_frozen_once_and_never_recomputed() throws Exception {
        Cookie a = login(SOC_A, "zt-snap-a");
        String mois = YearMonth.now().minusMonths(1).toString();

        mockMvc.perform(get("/api/v1/direction/snapshots").cookie(a))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/direction/snapshots/{m}", mois).cookie(a)).andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/direction/snapshots/{m}", mois).cookie(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mois").value(mois))
                .andExpect(jsonPath("$.overview.societeNom").exists())
                .andExpect(jsonPath("$.indicators.centres.length()").value(1));
        OffsetDateTime first = jdbc.queryForObject("SELECT generated_at FROM direction_snapshot WHERE societe_id = ?",
                OffsetDateTime.class, SOC_A);

        // idempotent : la deuxième demande ne recalcule rien
        mockMvc.perform(post("/api/v1/direction/snapshots/{m}", mois).cookie(a)).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(1) FROM direction_snapshot WHERE societe_id = ?", Integer.class, SOC_A));
        assertEquals(first, jdbc.queryForObject("SELECT generated_at FROM direction_snapshot WHERE societe_id = ?",
                OffsetDateTime.class, SOC_A));

        mockMvc.perform(get("/api/v1/direction/snapshots").cookie(a))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].mois").value(mois));
    }

    @Test
    void only_complete_and_valid_months_can_be_frozen() throws Exception {
        Cookie a = login(SOC_A, "zt-snap-a");
        for (String bad : new String[]{YearMonth.now().toString(), YearMonth.now().plusMonths(1).toString(),
                "2000-01", "2026-13", "abcd-ef", "2026-1"}) {
            mockMvc.perform(post("/api/v1/direction/snapshots/{m}", bad).cookie(a))
                    .andExpect(status().isUnprocessableEntity());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(1) FROM direction_snapshot WHERE societe_id = ?", Integer.class, SOC_A));
    }

    @Test
    void snapshots_are_isolated_between_societes_and_reserved_to_the_direction() throws Exception {
        Cookie a = login(SOC_A, "zt-snap-a");
        Cookie b = login(SOC_B, "zt-snap-b");
        String mois = YearMonth.now().minusMonths(1).toString();
        mockMvc.perform(post("/api/v1/direction/snapshots/{m}", mois).cookie(a)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/direction/snapshots").cookie(b)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/direction/snapshots/{m}", mois).cookie(b)).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/direction/snapshots/{m}/report", mois).cookie(b)).andExpect(status().isUnprocessableEntity());
        int anonymous = mockMvc.perform(get("/api/v1/direction/snapshots")).andReturn().getResponse().getStatus();
        assertTrue(anonymous == 401 || anonymous == 403);
    }

    @Test
    void the_report_is_a_pdf_built_from_the_frozen_snapshot() throws Exception {
        Cookie a = login(SOC_A, "zt-snap-a");
        String mois = YearMonth.now().minusMonths(1).toString();
        mockMvc.perform(post("/api/v1/direction/snapshots/{m}", mois).cookie(a)).andExpect(status().isOk());
        byte[] pdf = mockMvc.perform(get("/api/v1/direction/snapshots/{m}/report", mois).cookie(a))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(pdf.length > 500);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test
    void the_scheduled_job_freezes_the_previous_month_of_active_societes_and_is_replayable() {
        int created = snapshots.generateForPreviousMonth();
        assertTrue(created >= 2, "au moins les deux sociétés de test");
        String previous = YearMonth.now().minusMonths(1).toString();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(1) FROM direction_snapshot WHERE societe_id = ? AND mois = ?",
                Integer.class, SOC_A, previous));
        assertEquals(0, snapshots.generateForPreviousMonth(), "rejouable : rien de plus à figer");

        jdbc.update("UPDATE societes SET actif = FALSE WHERE id = ?", SOC_B);
        jdbc.update("DELETE FROM direction_snapshot WHERE societe_id = ?", SOC_B);
        snapshots.generateForPreviousMonth();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(1) FROM direction_snapshot WHERE societe_id = ?", Integer.class, SOC_B));
    }

    // ───────────────────────────── Utilitaires ─────────────────────────────

    private void societe(UUID id, String code, UUID centre, String centreNom) {
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                id, code, "Société " + code);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)",
                centre, code + "-C", centreNom, id);
    }

    private void direction(String username, UUID societe) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                userId, username, encoder.encode(PASSWORD), null, "Direction");
        UUID role = jdbc.queryForObject("SELECT id FROM app_role WHERE code = 'DIRECTION'", UUID.class);
        jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?, ?)", userId, role);
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", userId, societe);
    }

    private Cookie login(UUID societe, String username) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"societeId\":\"" + societe + "\",\"username\":\"" + username + "\",\"password\":\""
                                + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("HEMO_AUTH");
    }
}
