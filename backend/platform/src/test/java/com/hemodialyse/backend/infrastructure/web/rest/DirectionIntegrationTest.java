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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sessions sans centre (propriétaire, direction) et tableau de bord consolidé : isolation entre sociétés,
 * anonymat des effectifs faibles, cantonnement des routes, révocation immédiate d'un compte désactivé.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DirectionIntegrationTest {

    private static final UUID SOC_A = UUID.fromString("99997000-0000-0000-0000-000000000001");
    private static final UUID SOC_B = UUID.fromString("99997000-0000-0000-0000-000000000002");
    private static final UUID A1 = UUID.fromString("99997000-0000-0000-0000-0000000000a1");
    private static final UUID A2 = UUID.fromString("99997000-0000-0000-0000-0000000000a2");
    private static final UUID B1 = UUID.fromString("99997000-0000-0000-0000-0000000000b1");
    private static final String PASSWORD = "Direction-Test-2026";
    private static final String OWNER_PASSWORD = "Proprietaire-Test-2026";
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

    private static String accountBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"fullName\":\"Direction " + username + "\",\"password\":\""
                + password + "\"}";
    }

    private static UserPrincipal principal(String role) {
        return UserPrincipal.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "u-" + role, "",
                List.of(role), true);
    }

    // ───────────────────────────── Comptes direction ─────────────────────────────

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(roleScopeFilter).build();
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_A, "ZT-DA", "Société A");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_B, "ZT-DB", "Société B");
        centre(A1, "ZT-DA-1", "Centre A1", SOC_A);
        centre(A2, "ZT-DA-2", "Centre A2", SOC_A);
        centre(B1, "ZT-DB-1", "Centre B1", SOC_B);
        // A1 : 6 patients (effectif publiable) ; A2 : 2 patients (masqué) ; B1 : 7 patients (autre société)
        patients(A1, 6, 2);
        patients(A2, 2, 0);
        patients(B1, 7, 0);
        seances(A1, 3);
        facture(A1, "ZT-F-1", "1000.00", "1190.00", "600.00");
        facture(A1, "ZT-F-2", "500.00", "595.00", "0.00");
        facture(B1, "ZT-F-3", "99999.00", "99999.00", "0.00");
        ensureRole("DIRECTION");
        ensureRole("SUPERADMIN");
        owner("zt-owner");
    }

    // ───────────────────────────── Connexion sans centre ─────────────────────────────

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM auth_refresh_token WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'zt-%')");
        jdbc.update("DELETE FROM app_user_societe WHERE societe_id IN (?, ?)", SOC_A, SOC_B);
        jdbc.update("DELETE FROM app_user_role WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'zt-%')");
        jdbc.update("DELETE FROM app_user WHERE username LIKE 'zt-%'");
        for (String table : List.of("resultats_analyses", "serologies_patient", "alertes_observance", "bilans_pre_greffe", "lots", "articles")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?, ?)", A1, A2, B1);
        }
        jdbc.update("DELETE FROM facture_reglements WHERE center_id IN (?, ?, ?)", A1, A2, B1);
        jdbc.update("DELETE FROM factures WHERE center_id IN (?, ?, ?)", A1, A2, B1);
        jdbc.update("DELETE FROM seances WHERE center_id IN (?, ?, ?)", A1, A2, B1);
        jdbc.update("DELETE FROM patients WHERE center_id IN (?, ?, ?)", A1, A2, B1);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-D%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-D%'");
    }

    @Test
    void only_the_owner_manages_direction_accounts_and_weak_passwords_are_refused() throws Exception {
        for (String role : List.of("ADMIN", "MEDECIN", "DIRECTION")) {
            mockMvc.perform(post("/api/v1/societes/{id}/direction-accounts", SOC_A).contentType(MediaType.APPLICATION_JSON)
                            .content(accountBody("zt-x", PASSWORD)).with(user(principal(role))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/societes/{id}/direction-accounts", SOC_A).contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("zt-weak", "court1")).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity());
        createAccount("zt-dir-a", SOC_A);
        mockMvc.perform(post("/api/v1/societes/{id}/direction-accounts", SOC_A).contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("zt-dir-a", PASSWORD)).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity()); // identifiant déjà pris
        mockMvc.perform(get("/api/v1/societes/{id}/direction-accounts", SOC_A).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("zt-dir-a"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    // ───────────────────────────── Tableau de bord ─────────────────────────────

    @Test
    void a_direction_account_logs_in_without_a_centre_and_only_for_its_own_societe() throws Exception {
        createAccount("zt-dir-a", SOC_A);

        MvcResult ok = login(SOC_A, "zt-dir-a", PASSWORD, 200);
        JsonNode body = mapper.readTree(ok.getResponse().getContentAsString());
        assertEquals("SOCIETE", body.get("scope").asText());
        assertEquals(SOC_A.toString(), body.get("societeId").asText());
        assertTrue(body.get("centerId").isNull(), "aucun centre");

        login(SOC_B, "zt-dir-a", PASSWORD, 422);          // rattaché à A seulement
        login(null, "zt-dir-a", PASSWORD, 422);           // sans société : réservé au propriétaire
        login(SOC_A, "zt-dir-a", "Mauvais-mot-de-passe-1", 400);
        login(SOC_A, "zt-owner", OWNER_PASSWORD, 422);    // le propriétaire n'est pas un compte direction
    }

    @Test
    void the_owner_logs_in_without_centre_nor_societe_and_the_session_survives_refresh() throws Exception {
        MvcResult ok = login(null, "zt-owner", OWNER_PASSWORD, 200);
        JsonNode body = mapper.readTree(ok.getResponse().getContentAsString());
        assertEquals("PLATEFORME", body.get("scope").asText());
        assertTrue(body.get("centerId").isNull());

        Cookie access = ok.getResponse().getCookie("HEMO_AUTH");
        Cookie refresh = ok.getResponse().getCookie("HEMO_REFRESH");
        assertNotNull(access);
        assertNotNull(refresh);
        mockMvc.perform(get("/api/v1/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("PLATEFORME"));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isOk());
    }

    @Test
    void the_dashboard_is_scoped_to_the_societe_anonymised_and_read_only() throws Exception {
        createAccount("zt-dir-a", SOC_A);
        Cookie session = login(SOC_A, "zt-dir-a", PASSWORD, 200).getResponse().getCookie("HEMO_AUTH");

        MvcResult result = mockMvc.perform(get("/api/v1/direction/overview").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.societeNom").value("Société A"))
                .andExpect(jsonPath("$.centres.length()").value(2))
                .andReturn();
        JsonNode overview = mapper.readTree(result.getResponse().getContentAsString());
        String raw = result.getResponse().getContentAsString();
        assertTrue(!raw.contains("Centre B1") && !raw.contains("99999"), "aucune donnée de la société B");

        JsonNode a1 = null, a2 = null;
        for (JsonNode c : overview.get("centres")) {
            if (A1.toString().equals(c.get("centerId").asText())) a1 = c;
            if (A2.toString().equals(c.get("centerId").asText())) a2 = c;
        }
        assertNotNull(a1);
        assertNotNull(a2);
        assertEquals(6, a1.get("patients").asInt());
        assertEquals(3, a1.get("seances").asInt());
        assertEquals(2, a1.get("factures").asInt());
        assertEquals(1500.00, a1.get("caHt").asDouble(), 0.001);
        assertEquals(1785.00, a1.get("caTtc").asDouble(), 0.001);
        assertEquals(600.00, a1.get("encaisse").asDouble(), 0.001);
        assertEquals(1185.00, a1.get("resteARecouvrer").asDouble(), 0.001);
        assertEquals(33.6, a1.get("tauxEncaissement").asDouble(), 0.001);
        assertTrue(a1.get("patientsSousKt").isNull(), "2 patients sous KT : effectif faible masqué");
        assertTrue(a2.get("patients").isNull(), "2 patients : effectif faible masqué");
        assertEquals(8, overview.get("totaux").get("patients").asInt(), "le total consolidé est publiable");
        assertEquals(5, overview.get("seuilAnonymat").asInt());
        assertTrue(overview.get("mensuel").size() >= 1);

        // comparaison à la période précédente (même durée, immédiatement avant) : aucune facture/séance avant
        // aujourd'hui dans ce jeu de test, donc des totaux à zéro, mais le champ doit être présent et cohérent
        JsonNode prev = overview.get("periodePrecedente");
        assertNotNull(prev, "le delta de période doit être calculé");
        assertEquals(0, prev.get("seances").asInt());
        assertEquals(0.0, prev.get("caTtc").asDouble(), 0.001);
        assertTrue(prev.get("to").asText().compareTo(overview.get("from").asText()) < 0,
                "la période précédente se termine avant le début de la période affichée");

        // aucune écriture, aucune donnée de centre
        int write = mockMvc.perform(post("/api/v1/direction/overview").cookie(session)).andReturn().getResponse().getStatus();
        assertTrue(write >= 400, "aucune écriture possible sur le tableau de bord");
        mockMvc.perform(get("/api/v1/patients").cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/societes").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void each_direction_only_sees_its_own_societe() throws Exception {
        createAccount("zt-dir-a", SOC_A);
        createAccount("zt-dir-b", SOC_B);
        Cookie b = login(SOC_B, "zt-dir-b", PASSWORD, 200).getResponse().getCookie("HEMO_AUTH");
        String raw = mockMvc.perform(get("/api/v1/direction/overview").cookie(b)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(raw.contains("Centre B1") && !raw.contains("Centre A1"));
        // le périmètre vient du jeton : un paramètre societeId dans la requête est sans effet
        String raw2 = mockMvc.perform(get("/api/v1/direction/overview").param("societeId", SOC_A.toString()).cookie(b))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(raw2.contains("Centre A1"));
    }

    @Test
    void indicators_are_scoped_kdigo_evaluated_anonymised_and_raise_alerts() throws Exception {
        List<UUID> a1 = jdbc.queryForList("SELECT id FROM patients WHERE center_id = ? ORDER BY code_patient", UUID.class, A1);
        List<UUID> a2 = jdbc.queryForList("SELECT id FROM patients WHERE center_id = ? ORDER BY code_patient", UUID.class, A2);
        List<UUID> b1 = jdbc.queryForList("SELECT id FROM patients WHERE center_id = ? ORDER BY code_patient", UUID.class, B1);
        // A1 : 6 patients évalués — Kt/V conforme pour 4 sur 6 (66,7 %), Hb dans la cible pour 5 sur 6 (83,3 %)
        for (int i = 0; i < 6; i++) {
            bilan(A1, a1.get(i), i < 4 ? "1.30" : "1.00", i < 5 ? "10.8" : "9.0");
        }
        bilan(A2, a2.get(0), "1.00", "9.0"); // 2 patients seulement : masqué
        bilan(A2, a2.get(1), "1.00", "9.0");
        for (UUID p : b1) {
            bilan(B1, p, "0.50", "5.0");      // autre société : jamais visible
        }
        for (int i = 0; i < 5; i++) {
            serologie(A1, a1.get(i), "AG_HBS", "POSITIF");
        }
        serologie(A2, a2.get(0), "AG_HBS", "POSITIF");
        UUID article = article(A1, "ZT-ART-1", "10", "50");   // sous le seuil
        article(A1, "ZT-ART-2", "100", "50");
        lot(A1, article, java.time.LocalDate.now().minusDays(2));   // périmé
        lot(A1, article, java.time.LocalDate.now().plusDays(30));   // péremption proche

        createAccount("zt-dir-a", SOC_A);
        Cookie session = login(SOC_A, "zt-dir-a", PASSWORD, 200).getResponse().getCookie("HEMO_AUTH");
        MvcResult result = mockMvc.perform(get("/api/v1/direction/indicators").param("from", "2000-01-01")
                        .param("to", "2100-01-01").cookie(session))
                .andExpect(status().is(422)).andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("PERIODE_TROP_LONGUE"));

        String from = java.time.LocalDate.now().minusYears(1).toString();
        String to = java.time.LocalDate.now().toString();
        result = mockMvc.perform(get("/api/v1/direction/indicators").param("from", from).param("to", to).cookie(session))
                .andExpect(status().isOk()).andReturn();
        String raw = result.getResponse().getContentAsString();
        assertFalse(raw.contains("Centre B1"), "aucune donnée de la société B");
        JsonNode body = mapper.readTree(raw);

        JsonNode c1 = null, c2 = null;
        for (JsonNode c : body.get("centres")) {
            if (A1.toString().equals(c.get("centerId").asText())) c1 = c;
            if (A2.toString().equals(c.get("centerId").asText())) c2 = c;
        }
        assertNotNull(c1);
        assertNotNull(c2);
        JsonNode ktv = c1.get("clinique").get("ktV");
        assertEquals(6, ktv.get("evalues").asInt());
        assertEquals(66.7, ktv.get("pctDansCible").asDouble(), 0.05);
        assertEquals(33.3, ktv.get("pctSousCible").asDouble(), 0.05);
        assertEquals(83.3, c1.get("clinique").get("hemoglobine").get("pctDansCible").asDouble(), 0.05);
        assertEquals(5, c1.get("clinique").get("vhbPositifs").asInt());
        assertEquals(1, c1.get("stock").get("articlesSousSeuil").asInt());
        assertEquals(1, c1.get("stock").get("lotsPerimes").asInt());
        assertEquals(1, c1.get("stock").get("lotsPeremptionProche").asInt());

        // effectifs faibles : tout est masqué
        assertTrue(c2.get("clinique").get("ktV").get("evalues").isNull());
        assertTrue(c2.get("clinique").get("ktV").get("pctDansCible").isNull());
        assertTrue(c2.get("clinique").get("vhbPositifs").isNull());
        assertEquals(6, body.get("totaux").get("clinique").get("vhbPositifs").asInt(), "total consolidé publiable");

        List<String> codes = new java.util.ArrayList<>();
        for (JsonNode a : body.get("alertes")) {
            if (A1.toString().equals(a.get("centerId").asText())) codes.add(a.get("code").asText());
        }
        assertTrue(codes.containsAll(List.of("KTV_CONFORMITE_BASSE", "STOCK_SOUS_SEUIL", "LOTS_PERIMES", "LOTS_PEREMPTION_PROCHE")), codes.toString());

        for (String role : List.of("ADMIN", "MEDECIN", "SUPERADMIN")) {
            mockMvc.perform(get("/api/v1/direction/indicators").with(user(principal(role)))).andExpect(status().isForbidden());
        }
    }

    @Test
    void alert_history_is_scoped_to_its_societe_and_direction_only() throws Exception {
        jdbc.update("INSERT INTO direction_alert_history (id, societe_id, center_id, centre_nom, code, severity, "
                        + "valeur, first_seen_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), SOC_A, A1, "Centre A1", "STOCK_SOUS_SEUIL", "WARNING", new java.math.BigDecimal("2"));
        jdbc.update("INSERT INTO direction_alert_history (id, societe_id, center_id, centre_nom, code, severity, "
                        + "valeur, first_seen_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), SOC_B, B1, "Centre B1", "LOTS_PERIMES", "CRITICAL", null);

        createAccount("zt-dir-a", SOC_A);
        Cookie session = login(SOC_A, "zt-dir-a", PASSWORD, 200).getResponse().getCookie("HEMO_AUTH");
        MvcResult result = mockMvc.perform(get("/api/v1/direction/alerts/history").cookie(session))
                .andExpect(status().isOk()).andReturn();
        String raw = result.getResponse().getContentAsString();
        assertTrue(raw.contains("STOCK_SOUS_SEUIL") && raw.contains("Centre A1"));
        assertFalse(raw.contains("LOTS_PERIMES") || raw.contains("Centre B1"), "aucune donnée de la société B");

        for (String role : List.of("ADMIN", "MEDECIN", "SUPERADMIN")) {
            mockMvc.perform(get("/api/v1/direction/alerts/history").with(user(principal(role))))
                    .andExpect(status().isForbidden());
        }

        jdbc.update("DELETE FROM direction_alert_history WHERE societe_id IN (?, ?)", SOC_A, SOC_B);
    }

    private void bilan(UUID centre, UUID patient, String ktv, String hb) {
        jdbc.update("INSERT INTO resultats_analyses (id, patient_id, center_id, date_prelevement, kt_v_mensuel, hb_g_dl) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), patient, centre, Date.valueOf(java.time.LocalDate.now().minusDays(10)),
                new java.math.BigDecimal(ktv), new java.math.BigDecimal(hb));
    }

    private void serologie(UUID centre, UUID patient, String marqueur, String resultat) {
        jdbc.update("INSERT INTO serologies_patient (id, patient_id, center_id, marqueur, resultat, date_prelevement, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), patient, centre, marqueur, resultat, Date.valueOf(java.time.LocalDate.now().minusDays(20)));
    }

    private UUID article(UUID centre, String code, String stock, String seuil) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, gere_par_lot, active) "
                        + "VALUES (?,?,?,?,?,?,?,TRUE,TRUE)",
                id, centre, code, "Article " + code, "U", new java.math.BigDecimal(stock), new java.math.BigDecimal(seuil));
        return id;
    }

    private void lot(UUID centre, UUID article, java.time.LocalDate peremption) {
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante) "
                        + "VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), centre, article, "L-" + UUID.randomUUID().toString().substring(0, 6),
                Date.valueOf(peremption), new java.math.BigDecimal("10"), new java.math.BigDecimal("5"));
    }

    // ───────────────────────────── Données de test ─────────────────────────────

    @Test
    void a_deactivated_account_or_societe_loses_access_immediately() throws Exception {
        createAccount("zt-dir-a", SOC_A);
        Cookie session = login(SOC_A, "zt-dir-a", PASSWORD, 200).getResponse().getCookie("HEMO_AUTH");
        mockMvc.perform(get("/api/v1/direction/overview").cookie(session)).andExpect(status().isOk());

        UUID userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'zt-dir-a'", UUID.class);
        mockMvc.perform(post("/api/v1/societes/{s}/direction-accounts/{u}/desactiver", SOC_A, userId)
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/direction/overview").cookie(session)).andExpect(status().isForbidden());
        login(SOC_A, "zt-dir-a", PASSWORD, 422);

        mockMvc.perform(post("/api/v1/societes/{s}/direction-accounts/{u}/activer", SOC_A, userId)
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/direction/overview").cookie(session)).andExpect(status().isOk());

        jdbc.update("UPDATE societes SET actif = FALSE WHERE id = ?", SOC_A);
        mockMvc.perform(get("/api/v1/direction/overview").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void the_period_is_validated_and_other_roles_cannot_read_the_dashboard() throws Exception {
        createAccount("zt-dir-a", SOC_A);
        Cookie session = login(SOC_A, "zt-dir-a", PASSWORD, 200).getResponse().getCookie("HEMO_AUTH");
        mockMvc.perform(get("/api/v1/direction/overview").param("from", "2026-12-31").param("to", "2026-01-01")
                        .cookie(session))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/direction/overview").param("from", "2000-01-01").param("to", "2026-01-01")
                        .cookie(session))
                .andExpect(status().isUnprocessableEntity());
        for (String role : List.of("ADMIN", "MEDECIN", "SUPERADMIN")) {
            mockMvc.perform(get("/api/v1/direction/overview").with(user(principal(role))))
                    .andExpect(status().isForbidden());
        }
    }

    private MvcResult login(UUID societeId, String username, String password, int expected) throws Exception {
        String body = "{" + (societeId != null ? "\"societeId\":\"" + societeId + "\"," : "")
                + "\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn();
    }

    private void createAccount(String username, UUID societe) throws Exception {
        mockMvc.perform(post("/api/v1/societes/{id}/direction-accounts", societe).contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody(username, PASSWORD)).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isCreated());
    }

    private void centre(UUID id, String code, String name, UUID societe) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", id, code, name, societe);
    }

    private void patients(UUID centre, int count, int sousKt) {
        for (int i = 0; i < count; i++) {
            jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, "
                            + "numero_assurance, date_admission, type_patient, etat_patient, qualite_assure, sous_kt, "
                            + "epo_enabled, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), centre, "ZT-" + centre.toString().substring(31) + "-" + i, "NOM", "Prenom", "M",
                    Date.valueOf("1970-01-01"), "ASS-" + i, Date.valueOf("2026-01-02"), "PERMANENT", "ACTIF", "ASSURE",
                    i < sousKt);
        }
    }

    private void seances(UUID centre, int count) {
        UUID patient = jdbc.queryForObject("SELECT id FROM patients WHERE center_id = ? LIMIT 1", UUID.class, centre);
        for (int i = 0; i < count; i++) {
            jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) "
                            + "VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), patient, centre, Date.valueOf(java.time.LocalDate.now()), "VALIDEE");
        }
    }

    private void facture(UUID centre, String numero, String ht, String ttc, String regle) {
        UUID patient = jdbc.queryForObject("SELECT id FROM patients WHERE center_id = ? LIMIT 1", UUID.class, centre);
        UUID id = UUID.randomUUID();
        Date today = Date.valueOf(java.time.LocalDate.now());
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, "
                        + "date_facturation, tva_rate, total_ht, total_tva, total_ttc) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, centre, patient, numero, today, today, today, new java.math.BigDecimal("19.00"),
                new java.math.BigDecimal(ht), new java.math.BigDecimal(ttc).subtract(new java.math.BigDecimal(ht)),
                new java.math.BigDecimal(ttc));
        if (new java.math.BigDecimal(regle).signum() > 0) {
            jdbc.update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), id, centre, new java.math.BigDecimal(regle), today);
        }
    }

    private void ensureRole(String code) {
        Integer n = jdbc.queryForObject("SELECT COUNT(1) FROM app_role WHERE code = ?", Integer.class, code);
        if (n == null || n == 0) {
            jdbc.update("INSERT INTO app_role (id, code, name, description) VALUES (?,?,?,?)", UUID.randomUUID(), code, code, code);
        }
    }

    private void owner(String username) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                id, username, encoder.encode(OWNER_PASSWORD), null, "Propriétaire test");
        UUID role = jdbc.queryForObject("SELECT id FROM app_role WHERE code = 'SUPERADMIN'", UUID.class);
        jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?, ?)", id, role);
    }
}
