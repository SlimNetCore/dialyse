package com.hemodialyse.backend.infrastructure.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class LicenseSocieteIntegrationTest {

    private static final UUID SOCIETE = UUID.fromString("99996000-0000-0000-0000-000000000001");
    private static final UUID C1 = UUID.fromString("99996000-0000-0000-0000-000000000011");
    private static final UUID C2 = UUID.fromString("99996000-0000-0000-0000-000000000012");
    private static final UUID C3_INACTIVE = UUID.fromString("99996000-0000-0000-0000-000000000013");
    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    private MockMvc mockMvc;

    private static UserPrincipal principal(String role) {
        return UserPrincipal.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "u-" + role, "",
                List.of(role), true);
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOCIETE, "ZT-LIC", "Société licences");
        insertCentre(C1, "ZT-LIC-C1", "Centre A", true);
        insertCentre(C2, "ZT-LIC-C2", "Centre B", true);
        insertCentre(C3_INACTIVE, "ZT-LIC-C3", "Centre C", false);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM license WHERE center_id IN (SELECT id FROM centers WHERE code LIKE 'ZT-LIC%')");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-LIC%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-LIC%'");
    }

    @Test
    void a_licence_is_issued_for_every_active_centre_of_the_societe_and_listed_with_its_societe() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/licenses/issue-societe").contentType(MediaType.APPLICATION_JSON)
                        .content(body(null)).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        JsonNode issued = mapper.readTree(result.getResponse().getContentAsString());
        assertEquals(2, issued.size(), "un seul centre inactif est ignoré");
        for (JsonNode l : issued) {
            assertTrue(l.get("licenseKey").asText().length() > 20, "chaque centre reçoit sa propre clé");
            assertEquals("STANDARD", l.get("type").asText());
        }

        MvcResult all = mockMvc.perform(get("/api/v1/licenses").with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk()).andReturn();
        long mine = 0;
        for (JsonNode l : mapper.readTree(all.getResponse().getContentAsString())) {
            if (SOCIETE.toString().equals(l.path("societeId").asText())) {
                mine++;
                assertEquals("Société licences", l.get("societeName").asText());
            }
        }
        assertEquals(2, mine);
    }

    @Test
    void a_selection_of_centres_can_be_licensed_but_never_a_foreign_or_inactive_centre() throws Exception {
        mockMvc.perform(post("/api/v1/licenses/issue-societe").contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of(C1))).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].centerId").value(C1.toString()));

        for (UUID refused : List.of(C3_INACTIVE, UUID.fromString("11111111-1111-1111-1111-111111111111"))) {
            mockMvc.perform(post("/api/v1/licenses/issue-societe").contentType(MediaType.APPLICATION_JSON)
                            .content(body(List.of(C2, refused))).with(user(principal("SUPERADMIN"))))
                    .andExpect(status().isBadRequest());
        }
        // rien n'a été émis pour C2 malgré le premier identifiant valide : l'opération est atomique
        Integer c2 = jdbc.queryForObject("SELECT COUNT(1) FROM license WHERE center_id = ?", Integer.class, C2);
        assertEquals(0, c2);
    }

    @Test
    void dates_and_seats_are_validated() throws Exception {
        Instant now = Instant.now();
        String backwards = "{\"societeId\":\"" + SOCIETE + "\",\"type\":\"STANDARD\",\"maxUsers\":5,\"validFrom\":\""
                + now.plus(10, ChronoUnit.DAYS) + "\",\"validUntil\":\"" + now + "\"}";
        mockMvc.perform(post("/api/v1/licenses/issue-societe").contentType(MediaType.APPLICATION_JSON)
                        .content(backwards).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/licenses/issue-societe").contentType(MediaType.APPLICATION_JSON)
                        .content(body(null).replace("\"maxUsers\":5", "\"maxUsers\":0")).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void only_the_owner_can_issue_licences() throws Exception {
        for (String role : List.of("ADMIN", "MEDECIN", "DIRECTION")) {
            mockMvc.perform(post("/api/v1/licenses/issue-societe").contentType(MediaType.APPLICATION_JSON)
                            .content(body(null)).with(user(principal(role))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void the_direction_role_is_seeded() {
        Integer n = jdbc.queryForObject("SELECT COUNT(1) FROM app_role WHERE code = 'DIRECTION'", Integer.class);
        assertEquals(1, n);
    }

    private String body(List<UUID> centres) {
        Instant now = Instant.now();
        return "{\"societeId\":\"" + SOCIETE + "\","
                + (centres == null ? "" : "\"centerIds\":[" + String.join(",", centres.stream().map(u -> "\"" + u + "\"").toList()) + "],")
                + "\"type\":\"STANDARD\",\"maxUsers\":5,\"validFrom\":\"" + now + "\",\"validUntil\":\""
                + now.plus(365, ChronoUnit.DAYS) + "\"}";
    }

    private void insertCentre(UUID id, String code, String name, boolean actif) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,?)", id, code, name, SOCIETE, actif);
    }
}
