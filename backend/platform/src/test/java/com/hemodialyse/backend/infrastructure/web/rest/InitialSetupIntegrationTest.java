package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.security.RoleScopeFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Installation initiale (création du compte propriétaire, une seule fois, mot de passe exigeant) et administrateurs
 * de centre créés par le propriétaire.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class InitialSetupIntegrationTest {

    private static final UUID SOC = UUID.fromString("99998000-0000-0000-0000-000000000001");
    private static final UUID SOC_OTHER = UUID.fromString("99998000-0000-0000-0000-000000000002");
    private static final UUID CENTRE = UUID.fromString("99998000-0000-0000-0000-0000000000a1");
    private static final UUID CENTRE_OTHER = UUID.fromString("99998000-0000-0000-0000-0000000000b1");
    private static final String STRONG = "Proprietaire#Solide-2026";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RoleScopeFilter roleScopeFilter;
    private MockMvc mockMvc;

    private static String body(String username, String password) {
        return "{\"username\":\"" + username + "\",\"fullName\":\"Propriétaire\",\"password\":\"" + password + "\"}";
    }

    private static String adminBody(UUID centre, String username, String password) {
        return "{\"centerId\":\"" + centre + "\",\"username\":\"" + username + "\",\"fullName\":\"Admin\",\"password\":\""
                + password + "\"}";
    }

    // ───────────────────────────── Installation initiale ─────────────────────────────

    private static UserPrincipal principal(String role) {
        return UserPrincipal.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "u-" + role, "",
                List.of(role), true);
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(roleScopeFilter).build();
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC, "ZT-SU1", "Société Setup 1");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_OTHER, "ZT-SU2", "Société Setup 2");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", CENTRE, "ZT-SU1-C", "Centre S1", SOC);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", CENTRE_OTHER, "ZT-SU2-C", "Centre S2", SOC_OTHER);
        ensureRole("ADMIN");
        ensureRole("SUPERADMIN");
    }

    // ───────────────────────────── Administrateurs de centre ─────────────────────────────

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM app_user_center WHERE center_id IN (?, ?, ?) OR user_id IN (SELECT id FROM app_user WHERE username LIKE 'zt-%')",
                CENTRE, CENTRE_OTHER, UUID.fromString("99998000-0000-0000-0000-0000000000a2"));
        jdbc.update("DELETE FROM app_user_role WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'zt-%')");
        jdbc.update("DELETE FROM app_user WHERE username LIKE 'zt-%'");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-SU%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-SU%'");
    }

    @Test
    void the_first_owner_is_created_with_a_demanding_password_and_only_once() throws Exception {
        assertNoOwnerYet();
        mockMvc.perform(get("/api/v1/auth/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(true));

        for (String weak : List.of("court", "toutenminuscule1234", "SANSCHIFFRE-Symbole!!", "SansSymbole12345Ab", "zt-owner-Aa1!aaaa")) {
            mockMvc.perform(post("/api/v1/auth/setup/superadmin").contentType(MediaType.APPLICATION_JSON)
                            .content(body("zt-owner", weak)))
                    .andExpect(status().isUnprocessableEntity());
        }
        assertEquals(0, ownerCount(), "aucun propriétaire créé avec un mot de passe faible");

        mockMvc.perform(post("/api/v1/auth/setup/superadmin").contentType(MediaType.APPLICATION_JSON)
                        .content(body("zt-owner", STRONG)))
                .andExpect(status().isCreated());
        assertEquals(1, ownerCount());

        mockMvc.perform(get("/api/v1/auth/setup/status")).andExpect(jsonPath("$.required").value(false));
        // définitivement refusé ensuite, même avec un mot de passe valide
        mockMvc.perform(post("/api/v1/auth/setup/superadmin").contentType(MediaType.APPLICATION_JSON)
                        .content(body("zt-owner2", STRONG)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SETUP_ALREADY_DONE"));
        assertEquals(1, ownerCount());

        // le propriétaire créé peut se connecter, sans société ni centre
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zt-owner\",\"password\":\"" + STRONG + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("PLATEFORME"));
    }

    @Test
    void the_owner_assigns_an_existing_admin_to_another_centre_of_the_societe() throws Exception {
        UUID second = UUID.fromString("99998000-0000-0000-0000-0000000000a2");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", second, "ZT-SU1-D", "Centre S1 bis", SOC);
        mockMvc.perform(post("/api/v1/societes/{id}/admin-accounts", SOC).contentType(MediaType.APPLICATION_JSON)
                .content(adminBody(CENTRE, "zt-admin-s1", "Admin-Centre-2026")).with(user(principal("SUPERADMIN"))));
        UUID userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'zt-admin-s1'", UUID.class);
        // un rattachement à une autre société ne doit jamais être touché
        jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?, ?)", userId, CENTRE_OTHER);

        String both = "{\"fullName\":\"Admin Renommé\",\"email\":\"admin@exemple.dz\",\"centerIds\":[\"" + CENTRE + "\",\"" + second + "\"]}";
        mockMvc.perform(put("/api/v1/societes/{s}/admin-accounts/{u}", SOC, userId).contentType(MediaType.APPLICATION_JSON)
                        .content(both).with(user(principal("ADMIN"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/societes/{s}/admin-accounts/{u}", SOC, userId).contentType(MediaType.APPLICATION_JSON)
                        .content(both).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Admin Renommé"))
                .andExpect(jsonPath("$.centres.length()").value(2));

        // centre d'une autre société, liste vide : refusés
        mockMvc.perform(put("/api/v1/societes/{s}/admin-accounts/{u}", SOC, userId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerIds\":[\"" + CENTRE_OTHER + "\"]}").with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/api/v1/societes/{s}/admin-accounts/{u}", SOC, userId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerIds\":[]}").with(user(principal("SUPERADMIN"))))
                .andExpect(status().isBadRequest());

        // retrait du premier centre : reste le second, et le lien vers l'autre société est conservé
        mockMvc.perform(put("/api/v1/societes/{s}/admin-accounts/{u}", SOC, userId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerIds\":[\"" + second + "\"]}").with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.centres.length()").value(1))
                .andExpect(jsonPath("$.centres[0].name").value("Centre S1 bis"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(1) FROM app_user_center WHERE user_id = ? AND center_id = ?",
                Integer.class, userId, CENTRE_OTHER));
    }

    // ───────────────────────────── Utilitaires ─────────────────────────────

    @Test
    void an_admin_can_never_hand_out_the_owner_role() throws Exception {
        UUID ownerRole = jdbc.queryForObject("SELECT id FROM app_role WHERE code = 'SUPERADMIN'", UUID.class);
        String payload = "{\"username\":\"zt-sneaky\",\"password\":\"Motdepasse-2026x\",\"active\":true,"
                + "\"roleIds\":[\"" + ownerRole + "\"],\"centerIds\":[\"" + CENTRE + "\"]}";
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(payload)
                .with(user(principal("ADMIN"))));
        Integer owners = jdbc.queryForObject(
                "SELECT COUNT(1) FROM app_user_role ur INNER JOIN app_user u ON u.id = ur.user_id "
                        + "INNER JOIN app_role r ON r.id = ur.role_id WHERE r.code = 'SUPERADMIN' AND u.username = 'zt-sneaky'",
                Integer.class);
        assertEquals(0, owners);
    }

    @Test
    void the_owner_creates_the_admin_of_a_centre_of_the_societe() throws Exception {
        String ok = adminBody(CENTRE, "zt-admin-s1", "Admin-Centre-2026");
        for (String role : List.of("ADMIN", "MEDECIN", "DIRECTION")) {
            mockMvc.perform(post("/api/v1/societes/{id}/admin-accounts", SOC).contentType(MediaType.APPLICATION_JSON)
                            .content(ok).with(user(principal(role))))
                    .andExpect(status().isForbidden());
        }
        // centre d'une autre société, mot de passe faible, identifiant déjà pris
        mockMvc.perform(post("/api/v1/societes/{id}/admin-accounts", SOC).contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody(CENTRE_OTHER, "zt-admin-x", "Admin-Centre-2026"))
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CENTRE_HORS_SOCIETE"));
        mockMvc.perform(post("/api/v1/societes/{id}/admin-accounts", SOC).contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody(CENTRE, "zt-admin-x", "court1")).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/societes/{id}/admin-accounts", SOC).contentType(MediaType.APPLICATION_JSON)
                        .content(ok).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.centres[0].name").value("Centre S1"));
        mockMvc.perform(post("/api/v1/societes/{id}/admin-accounts", SOC).contentType(MediaType.APPLICATION_JSON)
                        .content(ok).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/societes/{id}/admin-accounts", SOC).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].password").doesNotExist());
        mockMvc.perform(get("/api/v1/societes/{id}/admin-accounts", SOC_OTHER).with(user(principal("SUPERADMIN"))))
                .andExpect(jsonPath("$.length()").value(0));

        // l'administrateur créé se connecte à son centre (société → centre)
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"societeId\":\"" + SOC + "\",\"centerId\":\"" + CENTRE
                                + "\",\"username\":\"zt-admin-s1\",\"password\":\"Admin-Centre-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("CENTRE"));
    }

    @Test
    void the_owner_can_deactivate_and_reset_an_admin_only_within_the_societe() throws Exception {
        mockMvc.perform(post("/api/v1/societes/{id}/admin-accounts", SOC).contentType(MediaType.APPLICATION_JSON)
                .content(adminBody(CENTRE, "zt-admin-s1", "Admin-Centre-2026")).with(user(principal("SUPERADMIN"))));
        UUID userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'zt-admin-s1'", UUID.class);

        mockMvc.perform(post("/api/v1/societes/{s}/admin-accounts/{u}/desactiver", SOC_OTHER, userId)
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity()); // autre société : introuvable
        mockMvc.perform(post("/api/v1/societes/{s}/admin-accounts/{u}/desactiver", SOC, userId)
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isNoContent());
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT active FROM app_user WHERE id = ?", Boolean.class, userId));

        mockMvc.perform(put("/api/v1/societes/{s}/admin-accounts/{u}/password", SOC, userId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"faible\"}")
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/api/v1/societes/{s}/admin-accounts/{u}/password", SOC, userId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Nouveau-Secret-2026\"}")
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isNoContent());
    }

    private void assertNoOwnerYet() {
        assertEquals(0, ownerCount(), "ce test suppose une installation vierge (aucun propriétaire)");
    }

    private int ownerCount() {
        Integer n = jdbc.queryForObject("SELECT COUNT(1) FROM app_user_role ur INNER JOIN app_role r ON r.id = ur.role_id "
                + "WHERE r.code = 'SUPERADMIN'", Integer.class);
        return n == null ? 0 : n;
    }

    private void ensureRole(String code) {
        Integer n = jdbc.queryForObject("SELECT COUNT(1) FROM app_role WHERE code = ?", Integer.class, code);
        if (n == null || n == 0) {
            jdbc.update("INSERT INTO app_role (id, code, name, description) VALUES (?,?,?,?)", UUID.randomUUID(), code, code, code);
        }
    }
}
