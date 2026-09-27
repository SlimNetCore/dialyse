package com.hemodialyse.backend.infrastructure.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hemodialyse.backend.infrastructure.security.RoleScopeFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
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

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Répartitions du tableau de bord de la direction : sexe, âge, caisse d'assurance (patients, séances, CA HT) et
 * anémie, par centre — cloisonnées par société et anonymisées sous le seuil.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DirectionBreakdownIntegrationTest {

    private static final UUID SOC_A = UUID.fromString("99994000-0000-0000-0000-000000000001");
    private static final UUID SOC_B = UUID.fromString("99994000-0000-0000-0000-000000000002");
    private static final UUID A1 = UUID.fromString("99994000-0000-0000-0000-0000000000a1");
    private static final UUID A2 = UUID.fromString("99994000-0000-0000-0000-0000000000a2");
    private static final UUID B1 = UUID.fromString("99994000-0000-0000-0000-0000000000b1");
    private static final String PASSWORD = "Direction-Breakdown-2026";
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

    private static JsonNode find(JsonNode rows, UUID centre) {
        for (JsonNode r : rows) if (centre.toString().equals(r.get("centerId").asText())) return r;
        throw new AssertionError("centre absent : " + centre);
    }

    private static JsonNode tranche(JsonNode tranches, String code) {
        for (JsonNode t : tranches) if (code.equals(t.get("code").asText())) return t.get("count");
        throw new AssertionError("tranche absente : " + code);
    }

    private static JsonNode caisse(JsonNode rows, UUID centre, String code) {
        for (JsonNode r : rows) {
            if (centre.toString().equals(r.get("centerId").asText()) && code.equals(r.get("caisseCode").asText()))
                return r;
        }
        throw new AssertionError("caisse absente : " + code);
    }

    // ───────────────────────────── Utilitaires ─────────────────────────────

    private static UserPrincipal principal(String role) {
        return UserPrincipal.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "u-" + role, "",
                List.of(role), true);
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(roleScopeFilter).build();
        cleanup();
        societe(SOC_A, "ZT-BK1");
        societe(SOC_B, "ZT-BK2");
        centre(A1, "ZT-BK1-A", "Centre A1", SOC_A);
        centre(A2, "ZT-BK1-B", "Centre A2", SOC_A);
        centre(B1, "ZT-BK2-A", "Centre B1", SOC_B);
        direction("zt-bk-a", SOC_A);
        ensureRole("DIRECTION");
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("administrations_anemie", "facture_reglements", "factures", "seances", "patients",
                "centre_payeur", "agence", "caisse_assurance")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?, ?)", A1, A2, B1);
        }
        jdbc.update("DELETE FROM auth_refresh_token WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'zt-bk-%')");
        jdbc.update("DELETE FROM app_user_societe WHERE societe_id IN (?, ?)", SOC_A, SOC_B);
        jdbc.update("DELETE FROM app_user_role WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'zt-bk-%')");
        jdbc.update("DELETE FROM app_user WHERE username LIKE 'zt-bk-%'");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-BK%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-BK%'");
    }

    @Test
    void breaks_the_societe_down_per_centre_with_anonymity_and_isolation() throws Exception {
        // A1 : 5 hommes nés en 1970, 7 femmes (5 nées en 1970, 2 en 2015) ; A2 : 2 hommes ; B1 : autre société
        UUID caisse = UUID.randomUUID();
        UUID agence = UUID.randomUUID();
        UUID payeur = UUID.randomUUID();
        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?,?,?,?)", caisse, A1, "CNAS", "CNAS Nationale");
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?,?,?,?,?)", agence, A1, caisse, "AG1", "Agence 1");
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?,?,?,?,?)", payeur, A1, agence, "CP1", "Payeur 1");

        List<UUID> a1 = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) a1.add(patient(A1, "M", "1970-05-05", i < 5 ? payeur : null));
        for (int i = 0; i < 5; i++) a1.add(patient(A1, "F", "1970-05-05", null));
        a1.add(patient(A1, "F", "2015-05-05", null));
        a1.add(patient(A1, "F", "2015-05-05", null));
        patient(A2, "M", "1970-05-05", null);
        patient(A2, "M", "1970-05-05", null);
        for (int i = 0; i < 6; i++) patient(B1, "F", "1980-01-01", null);

        // séances : 2 validées + 1 facturée pour des patients CNAS, 1 validée hors caisse (période en cours)
        LocalDate today = LocalDate.now();
        seance(A1, a1.get(0), today, "VALIDEE");
        seance(A1, a1.get(1), today, "VALIDEE");
        seance(A1, a1.get(2), today, "FACTUREE");
        seance(A1, a1.get(6), today, "VALIDEE");
        seance(A1, a1.get(6), today, "CREE"); // non réalisée : ignorée
        seance(B1, a1.get(0), today, "VALIDEE"); // autre centre : ignorée

        facture(A1, a1.get(0), "ZT-BK-F1", "1000.00", agence);
        facture(A1, a1.get(1), "ZT-BK-F2", "500.00", agence);
        facture(A1, a1.get(6), "ZT-BK-F3", "200.00", null);   // caisse inconnue
        facture(B1, a1.get(0), "ZT-BK-F4", "99999.00", null);

        // anémie : 5 patients traités par EPO (5 administrations), 1 patient sous fer (2), 1 dose non administrée
        for (int i = 0; i < 5; i++) administration(A1, a1.get(i), "EPO", true);
        administration(A1, a1.get(5), "FER_INJECTABLE", true);
        administration(A1, a1.get(5), "FER_INJECTABLE", true);
        administration(A1, a1.get(6), "EPO", false);

        Cookie session = login();
        String raw = mockMvc.perform(get("/api/v1/direction/breakdown").cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(!raw.contains("Centre B1") && !raw.contains("99999"), "aucune donnée de l'autre société");
        JsonNode body = mapper.readTree(raw);

        // sexe : A1 = 5 hommes, 7 femmes ; A2 = 2 hommes → masqué
        JsonNode sexeA1 = find(body.get("sexe"), A1);
        assertEquals(5, sexeA1.get("masculin").asInt());
        assertEquals(7, sexeA1.get("feminin").asInt());
        JsonNode sexeA2 = find(body.get("sexe"), A2);
        assertTrue(sexeA2.get("masculin").isNull(), "2 patients : effectif masqué");
        assertEquals(0, sexeA2.get("feminin").asInt());

        // âge : 10 patients de 45-59 ans à A1 ; 2 enfants → masqué
        JsonNode agesA1 = find(body.get("ages"), A1).get("tranches");
        assertEquals(10, tranche(agesA1, "45_59").asInt());
        assertTrue(tranche(agesA1, "0_17").isNull());

        // caisses : A1 / CNAS = 5 patients, 3 séances, 1500 HT ; hors caisse = 7 patients, 1 séance, 200 HT
        JsonNode cnas = caisse(body.get("caisses"), A1, "CNAS");
        assertEquals("CNAS Nationale", cnas.get("caisse").asText());
        assertEquals(5, cnas.get("patients").asInt());
        assertEquals(3, cnas.get("seances").asInt());
        assertEquals(1500.00, cnas.get("caHt").asDouble(), 0.001);
        JsonNode sans = caisse(body.get("caisses"), A1, "");
        assertEquals(7, sans.get("patients").asInt());
        assertEquals(1, sans.get("seances").asInt());
        assertEquals(200.00, sans.get("caHt").asDouble(), 0.001);
        JsonNode total = null;
        for (JsonNode t : body.get("caisseTotaux")) if ("CNAS".equals(t.get("caisseCode").asText())) total = t;
        assertNotNull(total);
        assertEquals(1500.00, total.get("caHt").asDouble(), 0.001);

        // anémie A1 : 5 patients EPO, fer masqué (1 patient), 5 + 2 administrées, 1 non administrée
        JsonNode anemie = find(body.get("anemie"), A1);
        assertEquals(5, anemie.get("patientsEpo").asInt());
        assertTrue(anemie.get("patientsFer").isNull());
        assertEquals(5, anemie.get("administreesEpo").asInt());
        assertEquals(2, anemie.get("administreesFer").asInt());
        assertEquals(1, anemie.get("nonAdministrees").asInt());
        assertEquals(87.5, anemie.get("tauxAdministration").asDouble(), 0.05);

        // lecture seule et réservée à la direction
        int write = mockMvc.perform(post("/api/v1/direction/breakdown").cookie(session)).andReturn().getResponse().getStatus();
        assertTrue(write >= 400);
        for (String role : List.of("ADMIN", "MEDECIN", "SUPERADMIN")) {
            mockMvc.perform(get("/api/v1/direction/breakdown").with(user(principal(role)))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/direction/breakdown").param("from", "2026-12-31").param("to", "2026-01-01")
                .cookie(session)).andExpect(status().isUnprocessableEntity());
    }

    private Cookie login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"societeId\":\"" + SOC_A + "\",\"username\":\"zt-bk-a\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("HEMO_AUTH");
    }

    private void societe(UUID id, String code) {
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                id, code, "Société " + code);
    }

    private void centre(UUID id, String code, String name, UUID societe) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", id, code, name, societe);
    }

    private UUID patient(UUID centre, String sexe, String naissance, UUID payeur) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, etat_patient, qualite_assure, sous_kt, epo_enabled, created_at, centre_payeur_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,CURRENT_TIMESTAMP,?)",
                id, centre, "ZT-" + id.toString().substring(0, 8), "NOM", "Prenom", sexe, Date.valueOf(naissance),
                "ASS-" + id.toString().substring(0, 8), Date.valueOf("2026-01-02"), "NON_VACANCIER", "PERMANENT",
                "ASSURE", false, payeur);
        return id;
    }

    private void seance(UUID centre, UUID patient, LocalDate date, String statut) {
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), patient, centre, Date.valueOf(date), statut);
    }

    private void facture(UUID centre, UUID patient, String numero, String ht, UUID agence) {
        Date today = Date.valueOf(LocalDate.now());
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, "
                        + "tva_rate, total_ht, total_tva, total_ttc, agence_id_snapshot) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), centre, patient, numero, today, today, today, new BigDecimal("19.00"),
                new BigDecimal(ht), BigDecimal.ZERO, new BigDecimal(ht), agence);
    }

    private void administration(UUID centre, UUID patient, String type, boolean administree) {
        jdbc.update("INSERT INTO administrations_anemie (id, patient_id, center_id, type_traitement, date_administration, "
                        + "administree, created_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), patient, centre, type, Date.valueOf(LocalDate.now()), administree);
    }

    private void direction(String username, UUID societe) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                userId, username, encoder.encode(PASSWORD), null, "Direction");
        Integer n = jdbc.queryForObject("SELECT COUNT(1) FROM app_role WHERE code = 'DIRECTION'", Integer.class);
        if (n == null || n == 0) {
            jdbc.update("INSERT INTO app_role (id, code, name, description) VALUES (?,?,?,?)", UUID.randomUUID(), "DIRECTION", "Direction", "Direction");
        }
        UUID role = jdbc.queryForObject("SELECT id FROM app_role WHERE code = 'DIRECTION'", UUID.class);
        jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?, ?)", userId, role);
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", userId, societe);
    }

    private void ensureRole(String code) {
        Integer n = jdbc.queryForObject("SELECT COUNT(1) FROM app_role WHERE code = ?", Integer.class, code);
        if (n == null || n == 0) {
            jdbc.update("INSERT INTO app_role (id, code, name, description) VALUES (?,?,?,?)", UUID.randomUUID(), code, code, code);
        }
    }
}
