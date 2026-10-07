package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le planning est en lecture seule pour le médecin : il le consulte (planning de la semaine, suivi des absences) mais
 * ne déclare pas l'absence d'un patient. Les {@code @PreAuthorize} ne sont évalués que par une pile Spring complète.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AbsenceDeclarationAccessIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99996000-0000-0000-0000-00000000000a");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private static UserPrincipal principal(String role) {
        return UserPrincipal.create(UUID.randomUUID().toString(), CENTRE.toString(), role.toLowerCase(), "",
                List.of(role), true);
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String declaration() {
        return """
                {"patientId":"%s","dateSeance":"%s","motif":"MALADIE"}""".formatted(UUID.randomUUID(),
                LocalDate.now().minusDays(1));
    }

    @Test
    void the_doctor_cannot_declare_the_absence_of_a_patient() throws Exception {
        mockMvc.perform(post("/api/v1/absences-patients").param("centerId", CENTRE.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(declaration())
                        .with(user(principal("MEDECIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void administrator_secretary_and_nurse_are_allowed_to_declare() throws Exception {
        for (String role : List.of("ADMIN", "SECRETAIRE", "INFIRMIER")) {
            int statut = mockMvc.perform(post("/api/v1/absences-patients").param("centerId", CENTRE.toString())
                            .contentType(MediaType.APPLICATION_JSON).content(declaration())
                            .with(user(principal(role))))
                    .andReturn().getResponse().getStatus();
            // patient inconnu : refus métier, mais jamais un refus d'accès
            assertNotEquals(403, statut, role);
        }
    }

    @Test
    void the_doctor_still_reads_the_week_of_the_planning_and_of_the_absences() throws Exception {
        mockMvc.perform(get("/api/v1/absences-patients/semaine").param("centerId", CENTRE.toString())
                        .with(user(principal("MEDECIN"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/planning/semaine").param("centerId", CENTRE.toString())
                        .with(user(principal("MEDECIN"))))
                .andExpect(status().isOk());
    }
}
