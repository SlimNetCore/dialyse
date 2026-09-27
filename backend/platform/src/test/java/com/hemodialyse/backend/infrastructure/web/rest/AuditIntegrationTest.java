package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.audit.AuditWriterService;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Journal d'audit : toute écriture est tracée automatiquement (filet de sécurité générique), écrite en base sans
 * bloquer la requête, consultable en pagination par un ADMIN (son centre) ou un SUPERADMIN (tout), jamais par les
 * autres rôles.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AuditIntegrationTest {

    private static final UUID SOC = UUID.fromString("99994000-0000-0000-0000-000000000001");
    private static final UUID CENTRE_A = UUID.fromString("99994000-0000-0000-0000-0000000000a1");
    private static final UUID CENTRE_B = UUID.fromString("99994000-0000-0000-0000-0000000000b1");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RoleScopeFilter roleScopeFilter;
    @Autowired
    private AuditRequestFilter auditRequestFilter;
    @Autowired
    private AuditWriterService auditWriter;
    private MockMvc mockMvc;

    private static UserPrincipal principal(String role, UUID centerId) {
        return UserPrincipal.create(UUID.randomUUID().toString(), centerId == null ? null : centerId.toString(),
                "u-" + role, "", List.of(role), true);
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(roleScopeFilter, auditRequestFilter).build();
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC, "ZT-AU", "Société Audit");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)",
                CENTRE_A, "ZT-AU-A", "Centre Audit A", SOC);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)",
                CENTRE_B, "ZT-AU-B", "Centre Audit B", SOC);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM app_user_societe WHERE societe_id = ?", SOC);
        jdbc.update("DELETE FROM app_user_role WHERE user_id IN (SELECT id FROM app_user WHERE username LIKE 'zt-au-%')");
        jdbc.update("DELETE FROM app_user WHERE username LIKE 'zt-au-%'");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-AU%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-AU%'");
    }

    @Test
    void a_write_is_traced_automatically_without_touching_the_response() throws Exception {
        mockMvc.perform(post("/api/v1/societes/{id}/direction-accounts", SOC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zt-au-dir\",\"fullName\":\"Direction Audit\",\"password\":\"Direction-Audit-2026\"}")
                        .with(user(principal("SUPERADMIN", null))))
                .andExpect(status().isCreated());

        auditWriter.flush();

        List<String> rows = jdbc.queryForList(
                "SELECT action_code FROM audit_log WHERE route_template = ?", String.class,
                "/api/v1/societes/{societeId}/direction-accounts");
        assertTrue(rows.contains("SOCIETES_CREATION"), rows.toString());
    }

    @Test
    void reading_a_medical_record_is_tracked_as_a_sensitive_consultation() throws Exception {
        UUID patientId = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, "
                        + "numero_assurance, date_admission, type_patient, etat_patient, qualite_assure, sous_kt, "
                        + "epo_enabled, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,FALSE,FALSE,CURRENT_TIMESTAMP)",
                patientId, CENTRE_A, "ZT-AU-P1", "NOM", "Prenom", "M", java.sql.Date.valueOf("1970-01-01"),
                "ASS-AU-1", java.sql.Date.valueOf("2026-01-02"), "PERMANENT", "ACTIF", "ASSURE");
        try {
            mockMvc.perform(get("/api/v1/patients/{patientId}/dossier-medical", patientId)
                    .param("centerId", CENTRE_A.toString())
                    .with(user(principal("MEDECIN", CENTRE_A))));

            auditWriter.flush();

            List<String> rows = jdbc.queryForList(
                    "SELECT action_code FROM audit_log WHERE route_template = ?", String.class,
                    "/api/v1/patients/{patientId}/dossier-medical");
            assertTrue(rows.contains("DOSSIER_MEDICAL_CONSULTATION"), rows.toString());
        } finally {
            jdbc.update("DELETE FROM patients WHERE id = ?", patientId);
        }
    }

    @Test
    void an_admin_only_sees_its_own_centre_a_superadmin_sees_everything_others_are_forbidden() throws Exception {
        insertRow(CENTRE_A, SOC, "PATIENTS_MODIFICATION");
        insertRow(CENTRE_B, SOC, "PATIENTS_MODIFICATION");

        mockMvc.perform(get("/api/v1/audit").with(user(principal("ADMIN", CENTRE_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].centerId").value(CENTRE_A.toString()));

        mockMvc.perform(get("/api/v1/audit").param("societeId", SOC.toString())
                        .with(user(principal("SUPERADMIN", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        for (String role : List.of("MEDECIN", "INFIRMIER", "SECRETAIRE", "DIRECTION")) {
            mockMvc.perform(get("/api/v1/audit").with(user(principal(role, CENTRE_A))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void an_admin_cannot_widen_its_scope_with_a_centre_parameter() throws Exception {
        insertRow(CENTRE_A, SOC, "PATIENTS_MODIFICATION");
        insertRow(CENTRE_B, SOC, "PATIENTS_MODIFICATION");

        String raw = mockMvc.perform(get("/api/v1/audit").param("centerId", CENTRE_B.toString())
                        .with(user(principal("ADMIN", CENTRE_A))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(raw.contains(CENTRE_B.toString()), "le paramètre client ne doit jamais élargir le périmètre");
    }

    private void insertRow(UUID centerId, UUID societeId, String actionCode) {
        jdbc.update("INSERT INTO audit_log (id, occurred_at, center_id, societe_id, action_code, http_method, "
                        + "route_template, status_code, duration_ms) "
                        + "VALUES (?,CURRENT_TIMESTAMP,?,?,?,?,?,?,?)",
                UUID.randomUUID(), centerId, societeId, actionCode, "PUT", "/api/v1/patients/{id}", 200, 5);
    }
}
