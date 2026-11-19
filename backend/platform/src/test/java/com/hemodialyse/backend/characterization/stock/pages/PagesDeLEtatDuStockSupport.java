package com.hemodialyse.backend.characterization.stock.pages;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Harnais HTTP des listes paginées de l'état du stock (contrat {@code pagination-serveur} E1 à E5) : MockMvc avec la sécurité
 * réelle et le RoleScopeFilter, H2, même configuration de contexte que les autres tests du Stock (contexte Spring partagé).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class PagesDeLEtatDuStockSupport {

    protected static final UUID CENTRE_A = UUID.fromString("99997600-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99997600-0000-0000-0000-00000000000b");

    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected com.hemodialyse.backend.infrastructure.security.RoleScopeFilter roleScopeFilter;
    protected MockMvc mvc;

    protected record Reponse(int statut, JsonNode json, String brut) {
    }

    protected static RequestPostProcessor as(UUID centre, String... roles) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre.toString(), "u", "", List.of(roles), true));
    }

    protected static RequestPostProcessor adminA() {
        return as(CENTRE_A, "ADMIN");
    }

    @BeforeEach
    void preparerLesPages() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        viderLesPages();
    }

    @AfterEach
    void viderLesPages() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM sorties_pour_seances_lignes WHERE sortie_id IN (SELECT id FROM sorties_pour_seances WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM sorties_pour_seances WHERE center_id = ?", c);
            jdbc.update("DELETE FROM inventaire_lignes WHERE center_id = ?", c);
            jdbc.update("DELETE FROM inventaires WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM articles WHERE center_id = ?", c);
        }
    }

    protected Reponse lire(String chemin, RequestPostProcessor qui) throws Exception {
        MockHttpServletResponse r = mvc.perform(get(java.net.URI.create(chemin)).with(qui)).andReturn().getResponse();
        String brut = r.getContentAsString(StandardCharsets.UTF_8);
        return new Reponse(r.getStatus(), brut.isBlank() ? null : JsonMapper.builder().build().readTree(brut), brut);
    }

    protected Reponse lireAnonyme(String chemin) throws Exception {
        MockHttpServletResponse r = mvc.perform(get(java.net.URI.create(chemin))).andReturn().getResponse();
        return new Reponse(r.getStatus(), null, r.getContentAsString(StandardCharsets.UTF_8));
    }

    /** 400 {@code ProblemDetail} « Requete invalide » avec le code stable attendu. */
    protected void refuse(String chemin, String code) throws Exception {
        Reponse r = lire(chemin, adminA());
        assertEquals(400, r.statut(), chemin + " : " + r.brut());
        assertEquals("Requete invalide", r.json().path("title").asString(), r.brut());
        assertEquals(code, r.json().path("code").asString(), r.brut());
    }

    protected UUID article(UUID centre, String code, String libelle, String stock, String pmp) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, "
                        + "gere_par_lot, active, created_at, peremption_obligatoire, produit_dangereux, dechet_dasri) "
                        + "VALUES (?, ?, ?, ?, 'piece', ?, 0, ?, false, true, CURRENT_TIMESTAMP, false, false, false)",
                id, centre, code, libelle, new BigDecimal(stock), new BigDecimal(pmp));
        return id;
    }

    protected static List<String> textes(JsonNode tableau, String champ) {
        List<String> valeurs = new java.util.ArrayList<>();
        tableau.forEach(n -> valeurs.add(n.path(champ).asString()));
        return valeurs;
    }
}
