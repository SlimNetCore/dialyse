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

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Listes paginées des bons ({@code GET /api/v1/stock/bons-commande|bons-reception|bons-sortie/page}, slice
 * {@code pagination-serveur}, RG-STK-088/089) en HTTP réel (sécurité + RoleScopeFilter, H2) : enveloppe
 * {@code {items,total,page,size}}, tri et filtres, codes 400 stables, plafond 100, rôles et isolation du centre ; régression
 * KB-STK-58 sur le jeu d'amorçage (bons de réception au statut {@code RECU}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PagesDesBonsIntegrationTest {

    private static final UUID CENTRE_A = UUID.fromString("99997600-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("99997600-0000-0000-0000-00000000000b");
    /** Centre du jeu d'amorçage H2 dont 60 bons de réception sont enregistrés au statut historique RECU. */
    private static final UUID CENTRE_DU_JEU = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 2, 1, 8, 0, 0, 0, ZoneOffset.UTC);

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
    }

    @AfterEach
    void nettoyer() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM bons_commande_lignes WHERE bon_commande_id IN (SELECT id FROM bons_commande WHERE center_id = ?)",
                    centre);
            jdbc.update("DELETE FROM bons_commande WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM bons_reception_lignes WHERE bon_reception_id IN (SELECT id FROM bons_reception WHERE center_id = ?)",
                    centre);
            jdbc.update("DELETE FROM bons_reception WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id IN (SELECT id FROM bons_sortie WHERE center_id = ?)",
                    centre);
            jdbc.update("DELETE FROM bons_sortie WHERE center_id = ?", centre);
        }
    }

    private Reponse lire(String chemin, RequestPostProcessor qui) throws Exception {
        MockHttpServletResponse r = mvc.perform(get(URI.create("/api/v1/stock" + chemin)).with(qui)).andReturn().getResponse();
        String corps = r.getContentAsString(StandardCharsets.UTF_8);
        return new Reponse(r.getStatus(), corps.isBlank() ? null : mapper.readTree(corps));
    }

    private static List<String> references(Reponse r) {
        List<String> refs = new ArrayList<>();
        r.corps().get("items").forEach(n -> refs.add(n.get("reference").asString()));
        return refs;
    }

    private void commande(UUID centre, String reference, String statut, int heure) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_commande (id, center_id, reference, statut, created_by, created_at) VALUES (?, ?, ?, ?, 'u', ?)",
                id, centre, reference, statut, T0.plusHours(heure));
        jdbc.update("INSERT INTO bons_commande_lignes (id, bon_commande_id, article_id, quantite, prix_unitaire) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), id, UUID.randomUUID(), BigDecimal.ONE, BigDecimal.TEN);
    }

    private void reception(UUID centre, String reference, String statut, int heure) {
        jdbc.update("INSERT INTO bons_reception (id, center_id, reference, date_reception, statut, created_by, created_at) "
                + "VALUES (?, ?, ?, ?, ?, 'u', ?)", UUID.randomUUID(), centre, reference, LocalDate.of(2026, 2, 1), statut,
                T0.plusHours(heure));
    }

    private void sortie(UUID centre, String reference, int heure) {
        jdbc.update("INSERT INTO bons_sortie (id, center_id, reference, seance_id, date_sortie, created_by, created_at) "
                + "VALUES (?, ?, ?, ?, ?, 'u', ?)", UUID.randomUUID(), centre, reference, UUID.randomUUID(),
                LocalDate.of(2026, 2, 1).plusDays(heure), T0.plusHours(heure));
    }

    @Test
    void bons_de_commande_enveloppe_tri_filtres_et_lignes() throws Exception {
        commande(CENTRE_A, "BC-00001", "BROUILLON", 1);
        commande(CENTRE_A, "BC-00002", "VALIDE", 2);
        commande(CENTRE_A, "BC-00003", "RECU", 3);
        commande(CENTRE_B, "BC-00004", "VALIDE", 4);

        Reponse page = lire("/bons-commande/page?size=2", as(CENTRE_A, "PHARMACIEN"));
        assertEquals(200, page.statut());
        assertEquals(3, page.corps().get("total").asLong());
        assertEquals(0, page.corps().get("page").asInt());
        assertEquals(2, page.corps().get("size").asInt());
        assertEquals(List.of("BC-00003", "BC-00002"), references(page));
        JsonNode bon = page.corps().get("items").get(0);
        for (String champ : List.of("id", "centerId", "reference", "fournisseurId", "statut", "createdBy", "createdAt", "lignes")) {
            assertTrue(bon.has(champ), champ);
        }
        assertEquals(1, bon.get("lignes").size());

        Reponse filtree = lire("/bons-commande/page?statut=VALIDE,BROUILLON&sort=reference,asc&reference=bc-0",
                as(CENTRE_A, "INFIRMIER"));
        assertEquals(List.of("BC-00001", "BC-00002"), references(filtree));
        assertEquals(2, filtree.corps().get("total").asLong());
    }

    @Test
    void bons_de_reception_statut_historique_recu_lu_valide() throws Exception {
        reception(CENTRE_A, "BR-00001", "BROUILLON", 1);
        reception(CENTRE_A, "BR-ANN-0002", "RECU", 2);

        Reponse validees = lire("/bons-reception/page?statut=VALIDE", as(CENTRE_A, "ADMIN"));
        assertEquals(200, validees.statut());
        assertEquals(List.of("BR-ANN-0002"), references(validees));
        assertEquals("VALIDE", validees.corps().get("items").get(0).get("statut").asString());
        assertEquals(List.of("BR-00001", "BR-ANN-0002"), references(lire("/bons-reception/page?sort=statut",
                as(CENTRE_A, "ADMIN"))));
    }

    @Test
    void la_liste_complete_des_receptions_du_jeu_d_amorcage_ne_tombe_plus_en_400() throws Exception {
        Reponse liste = lire("/bons-reception", as(CENTRE_DU_JEU, "ADMIN"));
        assertEquals(200, liste.statut());
        assertTrue(liste.corps().size() > 0);
        liste.corps().forEach(b -> assertTrue(List.of("BROUILLON", "VALIDE").contains(b.get("statut").asString())));

        Reponse page = lire("/bons-reception/page?reference=BR-ANN&size=100", as(CENTRE_DU_JEU, "ADMIN"));
        assertEquals(200, page.statut());
        assertTrue(page.corps().get("total").asLong() > 0);
        page.corps().get("items").forEach(b -> assertEquals("VALIDE", b.get("statut").asString()));
    }

    @Test
    void bons_de_sortie_pagines_par_date() throws Exception {
        sortie(CENTRE_A, "BS-00001", 1);
        sortie(CENTRE_A, "BS-00002", 2);
        sortie(CENTRE_A, "BS-00003", 3);

        Reponse page = lire("/bons-sortie/page?page=1&size=2&sort=dateSortie,desc", as(CENTRE_A, "INFIRMIER"));
        assertEquals(200, page.statut());
        assertEquals(3, page.corps().get("total").asLong());
        assertEquals(List.of("BS-00001"), references(page));
        assertTrue(page.corps().get("items").get(0).has("lignes"));
        assertEquals(List.of(), references(lire("/bons-sortie/page?page=9", as(CENTRE_A, "INFIRMIER"))));
    }

    @Test
    void parametres_refuses_avec_un_code_stable() throws Exception {
        RequestPostProcessor pharmacien = as(CENTRE_A, "PHARMACIEN");
        for (String[] cas : List.of(
                new String[]{"/bons-commande/page?page=-1", "PAGE_INVALIDE"},
                new String[]{"/bons-commande/page?size=101", "TAILLE_INVALIDE"},
                new String[]{"/bons-reception/page?size=0", "TAILLE_INVALIDE"},
                new String[]{"/bons-commande/page?sort=id", "TRI_INVALIDE"},
                new String[]{"/bons-sortie/page?sort=statut", "TRI_INVALIDE"},
                new String[]{"/bons-reception/page?sort=reference,haut", "TRI_INVALIDE"},
                new String[]{"/bons-reception/page?statut=RECU", "FILTRE_INVALIDE"},
                new String[]{"/bons-commande/page?statut=ANNULE", "FILTRE_INVALIDE"},
                new String[]{"/bons-commande/page?page=x", "PARAMETRE_INVALIDE"},
                new String[]{"/bons-sortie/page?centerId=pas-un-uuid", "PARAMETRE_INVALIDE"})) {
            Reponse r = lire(cas[0], pharmacien);
            assertEquals(400, r.statut(), cas[0]);
            assertEquals(cas[1], r.corps().get("code").asString(), cas[0]);
            assertEquals("Requete invalide", r.corps().get("title").asString(), cas[0]);
        }
    }

    @Test
    void roles_et_isolation_du_centre_inchanges() throws Exception {
        commande(CENTRE_B, "BC-00009", "VALIDE", 1);
        assertEquals(403, lire("/bons-commande/page?centerId=" + CENTRE_B, as(CENTRE_A, "ADMIN")).statut());
        assertEquals(List.of(), references(lire("/bons-commande/page", as(CENTRE_A, "ADMIN"))));
        assertEquals(403, lire("/bons-reception/page", as(CENTRE_A, "MEDECIN")).statut());
        assertEquals(403, lire("/bons-sortie/page", as(CENTRE_A, "MEDECIN")).statut());
        assertEquals(200, lire("/bons-sortie/page", as(CENTRE_A, "DIRECTEUR_ADMINISTRATIF")).statut());
    }
}
