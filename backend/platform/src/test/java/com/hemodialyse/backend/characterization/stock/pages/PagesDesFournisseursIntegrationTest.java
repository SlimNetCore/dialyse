package com.hemodialyse.backend.characterization.stock.pages;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Liste paginée des fournisseurs ({@code GET /api/v1/stock/referentiel/fournisseurs/page}, RG-STK-087) en HTTP réel : actifs
 * du centre, tri, recherche {@code q} et filtres de colonne, codes 400, rôles et isolation du centre. La liste complète
 * {@code GET /referentiel/fournisseurs} reste inchangée (golden masters du référentiel).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PagesDesFournisseursIntegrationTest {

    private static final UUID CENTRE_A = UUID.fromString("99997700-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("99997700-0000-0000-0000-00000000000b");

    private final ObjectMapper mapper = JsonMapper.builder().build();
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private com.hemodialyse.backend.infrastructure.security.RoleScopeFilter roleScopeFilter;
    private MockMvc mvc;

    private record Reponse(int statut, JsonNode corps) {
    }

    private static RequestPostProcessor as(UUID centre, String... roles) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre.toString(), "u", "", List.of(roles), true));
    }

    @BeforeEach
    void avant() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        nettoyer();
        fournisseur(CENTRE_A, "F3", "Cedre Medical", "Dupont", true);
        fournisseur(CENTRE_A, "F1", "Acme Pharma", "Martin", true);
        fournisseur(CENTRE_A, "F2", "Biolab", "Durand", true);
        fournisseur(CENTRE_A, "F4", "Ancien Acme", "Dupont", false);
        fournisseur(CENTRE_B, "F9", "Acme Ailleurs", "Dupont", true);
    }

    @AfterEach
    void nettoyer() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM fournisseurs WHERE center_id = ?", centre);
        }
    }

    private void fournisseur(UUID centre, String code, String raisonSociale, String contact, boolean actif) {
        jdbc.update("INSERT INTO fournisseurs (id, center_id, code, raison_sociale, contact, telephone, email, actif) "
                + "VALUES (?, ?, ?, ?, ?, '0102', NULL, ?)", UUID.randomUUID(), centre, code, raisonSociale, contact, actif);
    }

    private Reponse lire(String requete, RequestPostProcessor qui) throws Exception {
        MockHttpServletResponse r = mvc.perform(get(URI.create("/api/v1/stock/referentiel/fournisseurs/page" + requete)).with(qui))
                .andReturn().getResponse();
        String corps = r.getContentAsString(StandardCharsets.UTF_8);
        return new Reponse(r.getStatus(), corps.isBlank() ? null : mapper.readTree(corps));
    }

    private static List<String> codes(Reponse r) {
        List<String> codes = new ArrayList<>();
        r.corps().get("items").forEach(n -> codes.add(n.get("code").asString()));
        return codes;
    }

    @Test
    void fournisseurs_actifs_pagines_par_raison_sociale() throws Exception {
        Reponse page = lire("?size=2", as(CENTRE_A, "INFIRMIER"));
        assertEquals(200, page.statut());
        assertEquals(3, page.corps().get("total").asLong());
        assertEquals(List.of("F1", "F2"), codes(page));
        JsonNode f = page.corps().get("items").get(0);
        for (String champ : List.of("id", "centerId", "code", "raisonSociale", "contact", "telephone", "email", "actif")) {
            assertTrue(f.has(champ), champ);
        }
        assertEquals(List.of("F3"), codes(lire("?page=1&size=2", as(CENTRE_A, "INFIRMIER"))));
    }

    @Test
    void recherche_globale_filtres_et_tri() throws Exception {
        assertEquals(List.of("F3", "F2"), codes(lire("?q=DU&sort=code,desc", as(CENTRE_A, "PHARMACIEN"))));
        assertEquals(List.of("F3"), codes(lire("?raisonSociale=cedre&contact=dup&code=f", as(CENTRE_A, "PHARMACIEN"))));
        assertEquals(List.of("F3", "F2", "F1"), codes(lire("?sort=contact", as(CENTRE_A, "PHARMACIEN"))));
    }

    @Test
    void refus_codes_stables_roles_et_centre() throws Exception {
        RequestPostProcessor admin = as(CENTRE_A, "ADMIN");
        assertEquals("TRI_INVALIDE", lire("?sort=telephone", admin).corps().get("code").asString());
        assertEquals("TAILLE_INVALIDE", lire("?size=500", admin).corps().get("code").asString());
        assertEquals("PAGE_INVALIDE", lire("?page=-2", admin).corps().get("code").asString());
        assertEquals("PARAMETRE_INVALIDE", lire("?size=abc", admin).corps().get("code").asString());
        assertEquals(403, lire("?centerId=" + CENTRE_B, admin).statut());
        assertEquals(403, lire("", as(CENTRE_A, "MEDECIN")).statut());
    }
}
