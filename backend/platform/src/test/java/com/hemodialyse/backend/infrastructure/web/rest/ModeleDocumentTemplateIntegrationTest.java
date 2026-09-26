package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ModeleDocumentTemplateIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99994000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CENTER_ID = UUID.fromString("99994000-0000-0000-0000-000000000002");
    private static final UUID MODELE_ID = UUID.fromString("99994000-0000-0000-0000-000000000003");
    private static final UUID CUSTOM_ID = UUID.fromString("99994000-0000-0000-0000-000000000004");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    private static String original() throws Exception {
        try (var in = new ClassPathResource("reports/attestation.jrxml").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        insertModele(MODELE_ID, "reports/attestation.jrxml", "ATTESTATION");
        insertModele(CUSTOM_ID, "reports/inconnu.jrxml", "CUSTOM");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM modele_document_version WHERE modele_id IN (?, ?)", MODELE_ID, CUSTOM_ID);
        jdbc.update("DELETE FROM modele_document WHERE id IN (?, ?)", MODELE_ID, CUSTOM_ID);
    }

    @Test
    void admin_can_download_upload_activate_and_reset() throws Exception {
        // Téléchargement du modèle d'origine
        mockMvc.perform(get("/api/v1/documents/modeles/{id}/source", MODELE_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".jrxml")))
                .andExpect(content().string(containsString("<jasperReport")));

        // Téléversement d'une mise en page modifiée (marge) : accepté, version 1 active
        String modified = original().replace("topMargin=\"40\"", "topMargin=\"36\"");
        mockMvc.perform(multipart("/api/v1/documents/modeles/{id}/versions", MODELE_ID)
                        .file(new MockMultipartFile("file", "attestation.jrxml", "application/xml",
                                modified.getBytes(StandardCharsets.UTF_8)))
                        .param("centerId", CENTER_ID.toString())
                        .param("commentaire", "Marge gauche élargie")
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.uploadedBy").value("admin"));

        // Le téléchargement suivant renvoie la version personnalisée
        mockMvc.perform(get("/api/v1/documents/modeles/{id}/source", MODELE_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(content().string(containsString("topMargin=\"36\"")));

        // Seconde version, puis retour arrière sur la version 1
        mockMvc.perform(multipart("/api/v1/documents/modeles/{id}/versions", MODELE_ID)
                        .file(new MockMultipartFile("file", "attestation.jrxml", "application/xml",
                                original().replace("topMargin=\"40\"", "topMargin=\"34\"").getBytes(StandardCharsets.UTF_8)))
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(post("/api/v1/documents/modeles/{id}/versions/{v}/activate", MODELE_ID, 1)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(true));

        mockMvc.perform(get("/api/v1/documents/modeles/{id}/versions", MODELE_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.activeVersion").value(1))
                .andExpect(jsonPath("$.items[0].version").value(2))
                .andExpect(jsonPath("$.items[0].actif").value(false))
                .andExpect(jsonPath("$.items[1].actif").value(true));

        // Retour au modèle d'origine
        mockMvc.perform(post("/api/v1/documents/modeles/{id}/reset", MODELE_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/documents/modeles/{id}/source", MODELE_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(content().string(containsString("topMargin=\"40\"")));
    }

    @Test
    void a_template_that_changes_the_sql_query_is_rejected_and_not_stored() throws Exception {
        String malicious = original().replace("ORDER BY a.date_fin DESC",
                "UNION SELECT username, password_hash, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL FROM app_user");
        mockMvc.perform(multipart("/api/v1/documents/modeles/{id}/versions", MODELE_ID)
                        .file(new MockMultipartFile("file", "x.jrxml", "application/xml",
                                malicious.getBytes(StandardCharsets.UTF_8)))
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].code").value("QUERY_MODIFIED"));

        mockMvc.perform(get("/api/v1/documents/modeles/{id}/versions", MODELE_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void a_template_with_code_execution_is_rejected() throws Exception {
        String evil = original().replaceFirst("(?s)<textFieldExpression>.*?</textFieldExpression>",
                "<textFieldExpression><![CDATA[Runtime.getRuntime().exec(\"calc\")]]></textFieldExpression>");
        mockMvc.perform(multipart("/api/v1/documents/modeles/{id}/versions", MODELE_ID)
                        .file(new MockMultipartFile("file", "x.jrxml", "application/xml",
                                evil.getBytes(StandardCharsets.UTF_8)))
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].code").value("EXPRESSION_FORBIDDEN"));
    }

    @Test
    void a_wrong_extension_and_a_non_customizable_model_are_rejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents/modeles/{id}/versions", MODELE_ID)
                        .file(new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[]{1}))
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].code").value("FILE_TYPE"));

        mockMvc.perform(multipart("/api/v1/documents/modeles/{id}/versions", CUSTOM_ID)
                        .file(new MockMultipartFile("file", "x.jrxml", "application/xml",
                                original().getBytes(StandardCharsets.UTF_8)))
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].code").value("NOT_CUSTOMIZABLE"));
    }

    @Test
    void non_admin_roles_are_forbidden() throws Exception {
        for (String role : List.of("MEDECIN", "INFIRMIER")) {
            mockMvc.perform(get("/api/v1/documents/modeles/{id}/source", MODELE_ID)
                            .param("centerId", CENTER_ID.toString())
                            .with(user(principal("u", CENTER_ID, role))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(multipart("/api/v1/documents/modeles/{id}/versions", MODELE_ID)
                            .file(new MockMultipartFile("file", "x.jrxml", "application/xml", original().getBytes(StandardCharsets.UTF_8)))
                            .param("centerId", CENTER_ID.toString())
                            .with(user(principal("u", CENTER_ID, role))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void an_admin_cannot_reach_another_centers_templates() throws Exception {
        // centre du jeton ≠ centre demandé
        mockMvc.perform(get("/api/v1/documents/modeles/{id}/versions", MODELE_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", OTHER_CENTER_ID, "ADMIN"))))
                .andExpect(status().isForbidden());

        // même sans centerId explicite, le modèle d'un autre centre est introuvable
        mockMvc.perform(get("/api/v1/documents/modeles/{id}/source", MODELE_ID)
                        .with(user(principal("admin", OTHER_CENTER_ID, "ADMIN"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/documents/modeles/{id}/reset", MODELE_ID)
                        .with(user(principal("admin", OTHER_CENTER_ID, "ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void template_path_must_be_a_bundled_report() throws Exception {
        String body = """
                {"centerId":"%s","code":"X","libelle":"X","typeDocument":"CUSTOM",
                 "cheminJrxml":"../../etc/passwd","formatImpression":"PDF","description":null,"active":true}
                """.formatted(CENTER_ID);
        mockMvc.perform(post("/api/v1/documents/modeles")
                        .contentType("application/json").content(body)
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    private void insertModele(UUID id, String chemin, String type) {
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, "
                        + "format_impression, description, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                id, CENTER_ID, "T-" + id.toString().substring(31), "Modèle test", type, chemin, "PDF", null, true);
    }

    private UserPrincipal principal(String username, UUID centerId, String role) {
        return UserPrincipal.create(UUID.randomUUID().toString(), centerId.toString(), username, "", List.of(role), true);
    }
}
