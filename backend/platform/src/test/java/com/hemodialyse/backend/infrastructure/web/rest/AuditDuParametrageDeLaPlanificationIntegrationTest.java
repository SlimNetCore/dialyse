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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Audit des écritures de paramétrage de la planification (slice P1, décision du 26/10/2026) : la voie est le filtre générique
 * {@link AuditRequestFilter}, qui trace toute écriture {@code PUT} sur {@code /api/v1/**} avec l'utilisateur, le centre, le gabarit
 * de route et le statut de la réponse (y compris un refus métier) ; les lectures ne sont pas tracées. Le filet de caractérisation
 * n'inclut pas ce filtre : il est monté ici, avec les routes du contexte Planification.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AuditDuParametrageDeLaPlanificationIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99997810-0000-0000-0000-0000000000a1");
    private static final UUID INFIRMIER = UUID.fromString("99997810-0000-0000-0000-0000000000b1");

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

    private static UserPrincipal admin() {
        return UserPrincipal.create(UUID.randomUUID().toString(), CENTRE.toString(), "u-admin-audit-plan", "", List.of("ADMIN"), true);
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(roleScopeFilter, auditRequestFilter).build();
        cleanup();
        jdbc.update("INSERT INTO infirmier (id, center_id, matricule, nom, prenom, qualification, habilite_isolement, actif) "
                + "VALUES (?, ?, 'AUD-1', 'Audit', 'Un', 'INFIRMIER', FALSE, TRUE)", INFIRMIER, CENTRE);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_log WHERE center_id = ?", CENTRE);
        for (String table : List.of("planning_parametres", "planification_reglages", "infirmier_profil_planning", "infirmier")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", CENTRE);
        }
    }

    private List<Map<String, Object>> traces() {
        auditWriter.flush();
        return jdbc.queryForList("SELECT action_code, entity_type, entity_id, http_method, route_template, status_code "
                + "FROM audit_log WHERE center_id = ? ORDER BY route_template, status_code", CENTRE);
    }

    @Test
    void a_write_of_the_planning_settings_is_traced_with_its_route_and_status() throws Exception {
        mockMvc.perform(put("/api/v1/planning/parametres").contentType(MediaType.APPLICATION_JSON)
                .content("{\"joursOuverts\":[\"LUNDI\"]}").with(user(admin())));
        mockMvc.perform(put("/api/v1/planning/preferences/reglages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"replanificationAuto\":true,\"heuresParVacation\":5,\"heuresHebdoTempsPlein\":40,\"reposHebdoMin\":1}")
                .with(user(admin())));
        mockMvc.perform(put("/api/v1/planning/preferences/infirmiers/{id}", INFIRMIER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tauxActivite\":80,\"competences\":[\"CATHETER\"]}").with(user(admin())));

        List<Map<String, Object>> traces = traces();

        assertEquals(3, traces.size(), traces.toString());
        assertTrue(traces.stream().allMatch(t -> "PLANNING_MODIFICATION".equals(t.get("action_code"))
                && "planning".equals(t.get("entity_type")) && "PUT".equals(t.get("http_method"))
                && Integer.valueOf(200).equals(t.get("status_code"))), traces.toString());
        assertEquals(List.of("/api/v1/planning/parametres", "/api/v1/planning/preferences/infirmiers/{infirmierId}",
                "/api/v1/planning/preferences/reglages"), traces.stream().map(t -> (String) t.get("route_template")).toList());
        assertEquals(INFIRMIER.toString(), traces.get(1).get("entity_id"));
    }

    @Test
    void a_refused_write_is_traced_with_its_status_and_a_read_is_not_traced() throws Exception {
        mockMvc.perform(put("/api/v1/planning/parametres").contentType(MediaType.APPLICATION_JSON)
                .content("{\"joursOuverts\":[]}").with(user(admin())));
        mockMvc.perform(get("/api/v1/planning/parametres").with(user(admin())));

        List<Map<String, Object>> traces = traces();

        assertEquals(1, traces.size(), traces.toString());
        assertEquals(400, traces.getFirst().get("status_code"));
    }
}
