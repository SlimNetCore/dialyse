package com.hemodialyse.backend.infrastructure.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hemodialyse.backend.application.auth.mfa.Totp;
import com.hemodialyse.backend.infrastructure.security.RoleScopeFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Double authentification TOTP : inscription, connexion avec code, rejeu refusé, codes de secours à usage unique,
 * verrouillage après échecs, désactivation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MfaIntegrationTest {

    private static final String PASSWORD = "Mfa-Test-Password-2026";
    private static final String USERNAME = "zt-mfa";
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
    private UUID userId;

    private static void assertCode(String body, String code) {
        assertTrue(body.contains("\"code\":\"" + code + "\""), body);
    }

    private static long currentStep() {
        return Totp.stepAt(Instant.now().getEpochSecond());
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(roleScopeFilter).build();
        cleanup();
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                userId, USERNAME, encoder.encode(PASSWORD), null, "Utilisateur MFA");
        UUID admin = jdbc.queryForObject("SELECT id FROM app_role WHERE code = 'ADMIN'", UUID.class);
        jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?, ?)", userId, admin);
        jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?, '11111111-1111-1111-1111-111111111111')", userId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM app_user_mfa_recovery WHERE user_id IN (SELECT id FROM app_user WHERE username = ?)", USERNAME);
        jdbc.update("DELETE FROM app_user_mfa WHERE user_id IN (SELECT id FROM app_user WHERE username = ?)", USERNAME);
        jdbc.update("DELETE FROM auth_refresh_token WHERE user_id IN (SELECT id FROM app_user WHERE username = ?)", USERNAME);
        jdbc.update("DELETE FROM app_user_center WHERE user_id IN (SELECT id FROM app_user WHERE username = ?)", USERNAME);
        jdbc.update("DELETE FROM app_user_role WHERE user_id IN (SELECT id FROM app_user WHERE username = ?)", USERNAME);
        jdbc.update("DELETE FROM app_user WHERE username = ?", USERNAME);
    }

    @Test
    void without_mfa_the_login_needs_no_code_and_the_endpoints_need_a_session() throws Exception {
        login(null, 200);
        mockMvc.perform(get("/api/v1/auth/mfa/status").with(user(principal())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        int anonymous = mockMvc.perform(get("/api/v1/auth/mfa/status")).andReturn().getResponse().getStatus();
        assertTrue(anonymous == 401 || anonymous == 403);
    }

    @Test
    void enrollment_requires_a_valid_first_code_then_the_login_demands_a_code() throws Exception {
        String secret = enroll();
        // pas encore confirmé : la connexion reste possible sans code
        login(null, 200);
        confirm("000000", 422);
        confirm(Totp.codeAt(secret, currentStep()), 200);

        mockMvc.perform(get("/api/v1/auth/mfa/status").with(user(principal())))
                .andExpect(jsonPath("$.enabled").value(true));
        assertCode(login(null, 422), "MFA_REQUIRED");
        assertCode(login("123456", 422), "MFA_INVALID");
        // le pas courant a servi à confirmer : on prend le suivant (dans la tolérance d'horloge)
        String next = Totp.codeAt(secret, currentStep() + 1);
        login(next, 200);
        assertCode(login(next, 422), "MFA_INVALID"); // rejeu refusé
    }

    @Test
    void recovery_codes_work_once_and_the_secret_is_encrypted_at_rest() throws Exception {
        String secret = enroll();
        List<String> recovery = confirm(Totp.codeAt(secret, currentStep()), 200);
        assertEquals(8, recovery.size());
        String stored = jdbc.queryForObject("SELECT secret_cipher FROM app_user_mfa WHERE user_id = ?", String.class, userId);
        assertFalse(stored.contains(secret), "secret chiffré en base");

        login(recovery.get(0), 200);
        assertCode(login(recovery.get(0), 422), "MFA_INVALID");
        login(recovery.get(1).toLowerCase(), 200); // insensible à la casse
    }

    @Test
    void repeated_failures_lock_the_account_temporarily() throws Exception {
        String secret = enroll();
        confirm(Totp.codeAt(secret, currentStep()), 200);
        for (int i = 0; i < 5; i++) {
            assertCode(login("000000", 422), "MFA_INVALID");
        }
        // même avec le bon code, le compte est verrouillé
        assertCode(login(Totp.codeAt(secret, currentStep() + 1), 422), "MFA_LOCKED");
    }

    // ───────────────────────────── Utilitaires ─────────────────────────────

    @Test
    void disabling_requires_a_valid_code_and_removes_the_second_factor() throws Exception {
        String secret = enroll();
        confirm(Totp.codeAt(secret, currentStep()), 200);
        mockMvc.perform(post("/api/v1/auth/mfa/disable").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}").with(user(principal())))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/auth/mfa/disable").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + Totp.codeAt(secret, currentStep() + 1) + "\"}").with(user(principal())))
                .andExpect(status().isNoContent());
        login(null, 200);
    }

    @Test
    void the_mfa_also_protects_the_centreless_owner_login() throws Exception {
        UUID ownerRole = jdbc.queryForObject("SELECT id FROM app_role WHERE code = 'SUPERADMIN'", UUID.class);
        jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?, ?)", userId, ownerRole);
        String secret = enroll();
        confirm(Totp.codeAt(secret, currentStep()), 200);
        String body = "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}";
        assertCode(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andReturn().getResponse().getContentAsString(), "MFA_REQUIRED");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\",\"otp\":\""
                                + Totp.codeAt(secret, currentStep() + 1) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("PLATEFORME"));
    }

    private String enroll() throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/mfa/enroll").with(user(principal())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(json);
        assertTrue(node.get("otpauthUri").asText().startsWith("otpauth://totp/"));
        return node.get("secret").asText();
    }

    private List<String> confirm(String code, int expected) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/mfa/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}").with(user(principal())))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        if (expected != 200) return List.of();
        List<String> codes = new java.util.ArrayList<>();
        mapper.readTree(json).get("recoveryCodes").forEach(n -> codes.add(n.asText()));
        return codes;
    }

    /**
     * Connexion à un centre ; renvoie le corps de la réponse.
     */
    private String login(String otp, int expected) throws Exception {
        String body = "{\"centerId\":\"11111111-1111-1111-1111-111111111111\",\"username\":\"" + USERNAME
                + "\",\"password\":\"" + PASSWORD + "\"" + (otp != null ? ",\"otp\":\"" + otp + "\"" : "") + "}";
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private UserPrincipal principal() {
        return UserPrincipal.create(userId.toString(), "11111111-1111-1111-1111-111111111111", USERNAME, "",
                List.of("ADMIN"), true);
    }
}
