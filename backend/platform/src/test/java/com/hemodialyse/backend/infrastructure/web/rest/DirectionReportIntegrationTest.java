package com.hemodialyse.backend.infrastructure.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hemodialyse.backend.infrastructure.security.RoleScopeFilter;
import jakarta.servlet.http.Cookie;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rapports imprimés de la direction : en-tête et pied de page de la société sur chaque page, toutes les statistiques
 * du tableau de bord, données masquées conservées, instantanés anciens (sans répartitions) toujours imprimables.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DirectionReportIntegrationTest {

    private static final UUID SOC = UUID.fromString("99992000-0000-0000-0000-000000000001");
    private static final UUID SOC_B = UUID.fromString("99992000-0000-0000-0000-000000000002");
    private static final UUID A1 = UUID.fromString("99992000-0000-0000-0000-0000000000a1");
    private static final UUID A2 = UUID.fromString("99992000-0000-0000-0000-0000000000a2");
    private static final UUID B1 = UUID.fromString("99992000-0000-0000-0000-0000000000b1");
    private static final String PASSWORD = "Direction-Report-2026";
    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private RoleScopeFilter roleScopeFilter;
    private MockMvc mockMvc;

    private static String text(byte[] pdf) throws Exception {
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(roleScopeFilter).build();
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, nif, adresse, ville, telephone, pied_page, actif, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC, "ZT-RP1", "Clinique Rapport Test", "099912345678901", "12 rue des Fleurs", "Annaba", "038 00 00 00",
                "Pied personnalisé 2026 — Clinique Rapport Test");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_B, "ZT-RP2", "Autre Société Confidentielle");
        centre(A1, "ZT-RP1-A", "Centre Alpha", SOC);
        centre(A2, "ZT-RP1-B", "Centre Beta", SOC);
        centre(B1, "ZT-RP2-A", "Centre Confidentiel", SOC_B);

        List<UUID> patients = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) patients.add(patient(A1, i < 4 ? "M" : "F"));
        patient(A2, "M");
        for (int i = 0; i < 6; i++) patient(B1, "F");
        Date today = Date.valueOf(LocalDate.now());
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), patients.get(0), A1, today, "VALIDEE");
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, "
                        + "tva_rate, total_ht, total_tva, total_ttc) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), A1, patients.get(0), "ZT-RP-F1", today, today, today, new BigDecimal("19.00"),
                new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190"));
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                UUID.randomUUID(), "zt-rp-dir", encoder.encode(PASSWORD), null, "Direction");
        UUID user = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'zt-rp-dir'", UUID.class);
        Integer roles = jdbc.queryForObject("SELECT COUNT(1) FROM app_role WHERE code = 'DIRECTION'", Integer.class);
        if (roles == null || roles == 0) {
            jdbc.update("INSERT INTO app_role (id, code, name, description) VALUES (?,?,?,?)", UUID.randomUUID(), "DIRECTION", "Direction", "Direction");
        }
        jdbc.update("INSERT INTO app_user_role (user_id, role_id) SELECT ?, id FROM app_role WHERE code = 'DIRECTION'", user);
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", user, SOC);
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("factures", "seances", "patients")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?, ?)", A1, A2, B1);
        }
        jdbc.update("DELETE FROM direction_snapshot WHERE societe_id IN (?, ?)", SOC, SOC_B);
        jdbc.update("DELETE FROM auth_refresh_token WHERE user_id IN (SELECT id FROM app_user WHERE username = 'zt-rp-dir')");
        jdbc.update("DELETE FROM app_user_societe WHERE societe_id IN (?, ?)", SOC, SOC_B);
        jdbc.update("DELETE FROM app_user_role WHERE user_id IN (SELECT id FROM app_user WHERE username = 'zt-rp-dir')");
        jdbc.update("DELETE FROM app_user WHERE username = 'zt-rp-dir'");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-RP%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-RP%'");
    }

    @Test
    void the_live_report_carries_the_letterhead_the_footer_and_every_statistic() throws Exception {
        Cookie session = login();
        byte[] pdf = mockMvc.perform(get("/api/v1/direction/report").cookie(session))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(MediaType.APPLICATION_PDF_VALUE, result.getResponse().getContentType()))
                .andReturn().getResponse().getContentAsByteArray();
        String text = text(pdf);

        // en-tête : raison sociale, coordonnées, mentions légales ; pied de page personnalisé ; pagination
        assertTrue(text.contains("Clinique Rapport Test"), text);
        assertTrue(text.contains("12 rue des Fleurs"));
        assertTrue(text.contains("NIF : 099912345678901"));
        assertTrue(text.contains("Pied personnalisé 2026"));
        assertTrue(text.contains("Page 1 /"), "numéro de page");

        // toutes les statistiques du tableau de bord
        for (String section : List.of("Finances et activité par centre", "Évolution mensuelle", "Qualité des soins",
                "Stock", "Répartition des patients par sexe et par âge", "Caisses d'assurance par centre",
                "Traitement de l'anémie par centre", "Total société")) {
            assertTrue(text.contains(section), "section absente : " + section);
        }
        assertTrue(text.contains("Centre Alpha") && text.contains("Centre Beta"));
        assertTrue(text.contains("< seuil"), "les effectifs masqués restent masqués");
        assertTrue(!text.contains("Centre Confidentiel") && !text.contains("Autre Société Confidentielle"),
                "aucune donnée d'une autre société");
    }

    @Test
    void the_monthly_report_uses_the_same_letterhead_and_survives_snapshots_without_breakdown() throws Exception {
        Cookie session = login();
        String mois = YearMonth.now().minusMonths(1).toString();
        mockMvc.perform(post("/api/v1/direction/snapshots/{m}", mois).cookie(session)).andExpect(status().isOk());

        String withBreakdown = text(mockMvc.perform(get("/api/v1/direction/snapshots/{m}/report", mois).cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertTrue(withBreakdown.contains("Rapport mensuel — " + mois));
        assertTrue(withBreakdown.contains("Clinique Rapport Test") && withBreakdown.contains("Pied personnalisé 2026"));
        assertTrue(withBreakdown.contains("Caisses d'assurance par centre"));

        // instantané créé avant l'ajout des répartitions : le rapport reste imprimable, sans cette section
        String payload = jdbc.queryForObject("SELECT payload FROM direction_snapshot WHERE societe_id = ?", String.class, SOC);
        ObjectNode legacy = (ObjectNode) mapper.readTree(payload);
        legacy.remove("breakdown");
        jdbc.update("UPDATE direction_snapshot SET payload = ? WHERE societe_id = ?", mapper.writeValueAsString(legacy), SOC);
        String old = text(mockMvc.perform(get("/api/v1/direction/snapshots/{m}/report", mois).cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertTrue(old.contains("Finances et activité par centre"));
        assertFalse(old.contains("Caisses d'assurance par centre"));
    }

    // ───────────────────────────── Utilitaires ─────────────────────────────

    @Test
    void the_report_validates_its_period_and_is_reserved_to_the_direction() throws Exception {
        Cookie session = login();
        mockMvc.perform(get("/api/v1/direction/report").param("from", "2026-12-31").param("to", "2026-01-01").cookie(session))
                .andExpect(status().isUnprocessableEntity());
        int anonymous = mockMvc.perform(get("/api/v1/direction/report")).andReturn().getResponse().getStatus();
        assertTrue(anonymous == 401 || anonymous == 403);
    }

    private Cookie login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"societeId\":\"" + SOC + "\",\"username\":\"zt-rp-dir\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("HEMO_AUTH");
    }

    private void centre(UUID id, String code, String name, UUID societe) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", id, code, name, societe);
    }

    private UUID patient(UUID centre, String sexe) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, etat_patient, qualite_assure, sous_kt, epo_enabled, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,CURRENT_TIMESTAMP)",
                id, centre, "ZT-" + id.toString().substring(0, 8), "NOM", "Prenom", sexe, Date.valueOf("1970-01-01"),
                "ASS-" + id.toString().substring(0, 8), Date.valueOf("2026-01-02"), "NON_VACANCIER", "PERMANENT",
                "ASSURE", false);
        return id;
    }
}
