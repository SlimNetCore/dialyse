package com.hemodialyse.backend.infrastructure.web.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La fiche patient est en consultation seule pour le médecin « seul » : les écritures sont refusées par le serveur
 * (403), alors que tout autre profil — y compris un médecin qui cumule un autre rôle — passe la sécurité.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PatientFicheEcritureIntegrationTest {

    private static final UUID PATIENT = UUID.fromString("99998400-0000-0000-0000-0000000000a1");
    private static final UUID CENTRE = UUID.fromString("99998400-0000-0000-0000-00000000000a");

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private int statutAffectation(String... roles) throws Exception {
        return mockMvc.perform(post("/api/v1/patients/{id}/assures/{numero}/affecter", PATIENT, "ASS-X")
                        .param("centerId", CENTRE.toString())
                        .with(user("u").roles(roles)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void a_doctor_alone_cannot_write_the_patient_file() throws Exception {
        mockMvc.perform(post("/api/v1/patients/{id}/assures/{numero}/affecter", PATIENT, "ASS-X")
                        .param("centerId", CENTRE.toString()).with(user("u").roles("MEDECIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/patients/assures/{numero}", "ASS-X")
                        .param("centerId", CENTRE.toString()).contentType("application/json").content("{}")
                        .with(user("u").roles("MEDECIN")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void every_other_profile_passes_the_security_check() throws Exception {
        for (String[] roles : new String[][]{{"ADMIN"}, {"SECRETAIRE"}, {"INFIRMIER"}, {"MEDECIN", "SECRETAIRE"},
                {"MEDECIN", "ADMIN"}, {"COMPTABLE"}}) {
            assertNotEquals(403, statutAffectation(roles), String.join("+", roles));
        }
    }
}
