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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SocieteIntegrationTest {

    private static final UUID SEED_CENTER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    private MockMvc mockMvc;

    private static String createBody(String societeCode, String centreCode) {
        return "{\"societe\":{\"code\":\"" + societeCode + "\",\"raisonSociale\":\"Société " + societeCode
                + "\",\"email\":null},\"premierCentre\":" + centreBody(centreCode) + "}";
    }

    private static String centreBody(String code) {
        return "{\"code\":\"" + code + "\",\"nom\":\"Centre " + code + "\",\"ville\":\"Alger\"}";
    }

    // ───────────────────────────── Droits ─────────────────────────────

    private static UserPrincipal principal(String role) {
        return UserPrincipal.create(UUID.randomUUID().toString(), SEED_CENTER.toString(), "u-" + role, "", List.of(role), true);
    }

    // ───────────────────────────── Invariants ─────────────────────────────

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM app_user_center WHERE center_id IN (SELECT id FROM centers WHERE code LIKE 'ZT-%')");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-%'");
    }

    @Test
    void only_superadmin_can_manage_societes() throws Exception {
        for (String role : List.of("ADMIN", "MEDECIN", "INFIRMIER")) {
            mockMvc.perform(get("/api/v1/societes").with(user(principal(role))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/societes").contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("ZT-X", "ZT-X-C1")).with(user(principal(role))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/societes")).andExpect(status().is4xxClientError());
    }

    @Test
    void a_societe_is_created_with_its_first_centre_and_cannot_lose_its_last_active_centre() throws Exception {
        JsonNode societe = create("ZT-S1", "ZT-S1-C1");
        String id = societe.get("id").asText();
        String premier = societe.get("centres").get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/societes/{id}/centres/{c}/desactiver", id, premier).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("dernier centre")));

        // Ajout d'un second centre : le premier peut alors être désactivé, mais pas le second
        JsonNode avecDeux = postJson("/api/v1/societes/" + id + "/centres", centreBody("ZT-S1-C2"), 201);
        String second = avecDeux.get("centres").get(1).get("id").asText();
        postJson("/api/v1/societes/" + id + "/centres/" + premier + "/desactiver", null, 200);
        mockMvc.perform(post("/api/v1/societes/{id}/centres/{c}/desactiver", id, second).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void a_societe_cannot_be_created_without_a_first_centre() throws Exception {
        mockMvc.perform(post("/api/v1/societes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"societe\":{\"code\":\"ZT-S2\",\"raisonSociale\":\"Sans centre\"}}")
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isBadRequest());
    }

    // ───────────────────────────── Annuaire et connexion ─────────────────────────────

    @Test
    void codes_are_unique_and_contact_details_are_validated() throws Exception {
        create("ZT-S3", "ZT-S3-C1");
        mockMvc.perform(post("/api/v1/societes").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("ZT-S3", "ZT-S3-C9")).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/societes").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("ZT-S4", "ZT-S3-C1")).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity());
        String badEmail = createBody("ZT-S5", "ZT-S5-C1").replace("\"email\":null", "\"email\":\"pas-un-email\"");
        mockMvc.perform(post("/api/v1/societes").contentType(MediaType.APPLICATION_JSON)
                        .content(badEmail).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void a_centre_can_be_transferred_only_if_the_source_keeps_an_active_centre() throws Exception {
        JsonNode a = create("ZT-A", "ZT-A-C1");
        JsonNode b = create("ZT-B", "ZT-B-C1");
        String aId = a.get("id").asText();
        String bId = b.get("id").asText();
        String aC1 = a.get("centres").get(0).get("id").asText();

        // le seul centre de A ne peut pas partir
        mockMvc.perform(post("/api/v1/societes/{id}/centres/{c}/transfert", aId, aC1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"societeCibleId\":\"" + bId + "\"}")
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity());

        JsonNode aDeux = postJson("/api/v1/societes/" + aId + "/centres", centreBody("ZT-A-C2"), 201);
        String aC2 = aDeux.get("centres").get(1).get("id").asText();
        mockMvc.perform(post("/api/v1/societes/{id}/centres/{c}/transfert", aId, aC2)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"societeCibleId\":\"" + bId + "\"}")
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/societes/{id}", aId).with(user(principal("SUPERADMIN"))))
                .andExpect(jsonPath("$.centres.length()").value(1));
        mockMvc.perform(get("/api/v1/societes/{id}", bId).with(user(principal("SUPERADMIN"))))
                .andExpect(jsonPath("$.centres.length()").value(2));
    }

    @Test
    void the_list_is_paged_and_searchable() throws Exception {
        create("ZT-L1", "ZT-L1-C1");
        create("ZT-L2", "ZT-L2-C1");
        mockMvc.perform(get("/api/v1/societes").param("q", "ZT-L").param("size", "1").with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void the_public_directory_exposes_only_ids_and_names_of_active_societes_and_centres() throws Exception {
        JsonNode societe = create("ZT-P1", "ZT-P1-C1");
        String id = societe.get("id").asText();

        MvcResult result = mockMvc.perform(get("/api/v1/auth/societes")).andExpect(status().isOk()).andReturn();
        JsonNode list = mapper.readTree(result.getResponse().getContentAsString());
        JsonNode mine = null;
        for (JsonNode n : list) {
            if (id.equals(n.get("id").asText())) mine = n;
        }
        org.junit.jupiter.api.Assertions.assertNotNull(mine, "la société active doit être proposée");
        org.junit.jupiter.api.Assertions.assertEquals(2, mine.size(), "seuls id et name sont exposés");

        mockMvc.perform(get("/api/v1/auth/societes/{id}/centres", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Centre ZT-P1-C1"));

        postJson("/api/v1/societes/" + id + "/desactiver", null, 200);
        mockMvc.perform(get("/api/v1/auth/societes/{id}/centres", id))
                .andExpect(jsonPath("$.length()").value(0));
        MvcResult after = mockMvc.perform(get("/api/v1/auth/societes")).andReturn();
        org.junit.jupiter.api.Assertions.assertFalse(after.getResponse().getContentAsString().contains(id));
    }

    // ───────────────────────────── Logo ─────────────────────────────

    @Test
    void login_requires_the_centre_to_belong_to_the_chosen_societe_and_both_to_be_active() throws Exception {
        JsonNode societe = create("ZT-G1", "ZT-G1-C1");
        String societeId = societe.get("id").asText();
        String centreId = societe.get("centres").get(0).get("id").asText();
        jdbc.update("INSERT INTO app_user_center (user_id, center_id) SELECT id, ? FROM app_user WHERE username = 'admin'",
                UUID.fromString(centreId));

        // société erronée
        login(societeId, SEED_CENTER.toString(), 422);
        // centre correct dans la bonne société
        login(societeId, centreId, 200);
        // sans précision de société (compatibilité)
        login(null, centreId, 200);

        // centre désactivé (il faut d'abord un second centre actif pour respecter l'invariant)
        JsonNode avecDeux = postJson("/api/v1/societes/" + societeId + "/centres", centreBody("ZT-G1-C2"), 201);
        postJson("/api/v1/societes/" + societeId + "/centres/" + centreId + "/desactiver", null, 200);
        login(societeId, centreId, 422);
        postJson("/api/v1/societes/" + societeId + "/centres/" + centreId + "/activer", null, 200);
        login(societeId, centreId, 200);

        // société désactivée
        postJson("/api/v1/societes/" + societeId + "/desactiver", null, 200);
        login(societeId, centreId, 422);
        org.junit.jupiter.api.Assertions.assertNotNull(avecDeux);
    }

    @Test
    void a_societe_can_be_renamed_and_keeps_its_centres() throws Exception {
        JsonNode societe = create("ZT-U1", "ZT-U1-C1");
        String id = societe.get("id").asText();
        mockMvc.perform(put("/api/v1/societes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ZT-U1\",\"raisonSociale\":\"Nouveau nom\",\"telephone\":\"0555 12 34 56\","
                                + "\"email\":\"Contact@Exemple.DZ\"}")
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.raisonSociale").value("Nouveau nom"))
                .andExpect(jsonPath("$.email").value("contact@exemple.dz"))
                .andExpect(jsonPath("$.centres.length()").value(1));
    }

    // ───────────────────────────── Utilitaires ─────────────────────────────

    @Test
    void accessible_centres_are_scoped_to_the_callers_societe_except_for_superadmin() throws Exception {
        JsonNode societe = create("ZT-C1", "ZT-C1-C1");
        String own = societe.get("centres").get(0).get("id").asText();
        var asAdminOfNewCentre = user(UserPrincipal.create(UUID.randomUUID().toString(), own, "adm", "",
                List.of("ADMIN"), true));

        MvcResult mine = mockMvc.perform(get("/api/v1/auth/centres").with(asAdminOfNewCentre))
                .andExpect(status().isOk()).andReturn();
        JsonNode list = mapper.readTree(mine.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(1, list.size(), "un ADMIN ne voit que les centres de sa société");
        org.junit.jupiter.api.Assertions.assertEquals(own, list.get(0).get("id").asText());

        MvcResult all = mockMvc.perform(get("/api/v1/auth/centres").with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk()).andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(all.getResponse().getContentAsString().contains(own));
        org.junit.jupiter.api.Assertions.assertTrue(
                all.getResponse().getContentAsString().contains(SEED_CENTER.toString()));

        mockMvc.perform(get("/api/v1/auth/centres")).andExpect(status().is4xxClientError());
    }

    @Test
    void the_logo_is_validated_stored_served_and_removed_by_the_superadmin_only() throws Exception {
        JsonNode societe = create("ZT-LG", "ZT-LG-C1");
        String id = societe.get("id").asText();
        org.junit.jupiter.api.Assertions.assertFalse(societe.get("hasLogo").asBoolean());

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(60, 40, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", png);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                                org.springframework.http.HttpMethod.PUT, "/api/v1/societes/{id}/logo", id)
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "logo.png", "image/png", png.toByteArray()))
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasLogo").value(true));

        mockMvc.perform(get("/api/v1/societes/{id}/logo", id).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", "image/png"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Content-Type-Options", "nosniff"));

        // un SVG (script embarqué possible) est refusé, même déclaré image/png
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                                org.springframework.http.HttpMethod.PUT, "/api/v1/societes/{id}/logo", id)
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "logo.png", "image/png",
                                "<svg xmlns='http://www.w3.org/2000/svg'><script>1</script></svg>".getBytes()))
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].code").value("FILE_TYPE"));

        // les autres rôles n'ont aucun accès
        mockMvc.perform(get("/api/v1/societes/{id}/logo", id).with(user(principal("ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/societes/{id}/logo", id)
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/societes/{id}/logo", id).with(user(principal("SUPERADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void the_footer_text_is_stored_and_limited() throws Exception {
        JsonNode societe = create("ZT-PP", "ZT-PP-C1");
        String id = societe.get("id").asText();
        mockMvc.perform(put("/api/v1/societes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ZT-PP\",\"raisonSociale\":\"S\",\"piedDePage\":\"SARL au capital de 1 000 000 DA\"}")
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.piedDePage").value("SARL au capital de 1 000 000 DA"));
        mockMvc.perform(put("/api/v1/societes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ZT-PP\",\"raisonSociale\":\"S\",\"piedDePage\":\"" + "x".repeat(501) + "\"}")
                        .with(user(principal("SUPERADMIN"))))
                .andExpect(status().isBadRequest());
    }

    private void login(String societeId, String centreId, int expectedStatus) throws Exception {
        String body = "{" + (societeId != null ? "\"societeId\":\"" + societeId + "\"," : "")
                + "\"centerId\":\"" + centreId + "\",\"username\":\"admin\",\"password\":\"admin$$2026dz\"}";
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode create(String societeCode, String centreCode) throws Exception {
        return postJson("/api/v1/societes", createBody(societeCode, centreCode), 201);
    }

    private JsonNode postJson(String url, String body, int expectedStatus) throws Exception {
        var request = post(url).with(user(principal("SUPERADMIN")));
        if (body != null) request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? null : mapper.readTree(content);
    }
}
