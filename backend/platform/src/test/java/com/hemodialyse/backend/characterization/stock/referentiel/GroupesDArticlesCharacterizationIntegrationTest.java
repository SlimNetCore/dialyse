package com.hemodialyse.backend.characterization.stock.referentiel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Filet de caractérisation S2 — groupes d'articles : {@code GET|POST /api/v1/stock/groupes-articles},
 * {@code GET|PUT|DELETE /{id}} (RG-STK-070 à 072). Point d'observation : HTTP et état SQL de {@code groupes_articles} et
 * {@code groupes_articles_items}. Écrit sur le legacy ({@code GroupeArticleRestController}, {@code GroupeArticle},
 * {@code GroupeArticleApplicationService}, {@code GroupeArticleRepositoryAdapter}).
 * <p>
 * Les articles des scénarios ont des identifiants fixes (la réponse trie les articles par identifiant : l'ordre des alias
 * doit être stable d'une exécution à l'autre).
 */
class GroupesDArticlesCharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final String GROUPES = "/api/v1/stock/groupes-articles";

    private static String corps(String nom, String description, List<UUID> articles) {
        String ids = articles.stream().map(a -> a == null ? "null" : "'" + a + "'").collect(Collectors.joining(","));
        return json("{" + (nom == null ? "" : "'nom':'" + nom + "',")
                + (description == null ? "" : "'description':'" + description + "',")
                + "'articleIds':[" + ids + "]}");
    }

    @Override
    protected String theme() {
        return "groupes-articles";
    }

    // ------------------------------------------------------------------ données

    @BeforeEach
    @AfterEach
    void viderLesGroupes() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM groupes_articles_items WHERE groupe_id IN (SELECT id FROM groupes_articles WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM groupes_articles WHERE center_id = ?", centre);
        }
    }

    /**
     * Article à identifiant fixe {@code 99997500-0000-0000-0001-<n>} (alias nommé).
     */
    private UUID articleFixe(UUID centre, String alias, int n, boolean actif) {
        UUID id = UUID.fromString(String.format("99997500-0000-0000-0001-%012d", n));
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, "
                        + "gere_par_lot, active, created_at, peremption_obligatoire, produit_dangereux, dechet_dasri) "
                        + "VALUES (?, ?, ?, ?, 'piece', 0, 0, 0, false, ?, CURRENT_TIMESTAMP, false, false, false)",
                id, centre, alias, "Libelle " + alias, actif);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    private Resp poster(String requete, String corps, org.springframework.test.web.servlet.request.RequestPostProcessor qui)
            throws Exception {
        return send(post(java.net.URI.create(requete)).contentType(MediaType.APPLICATION_JSON).content(corps).with(qui));
    }

    private Resp creer(String nom, List<UUID> articles) throws Exception {
        return poster(GROUPES, corps(nom, null, articles), adminA());
    }

    private UUID idDe(Resp r) {
        return UUID.fromString(json(r).path("id").asString());
    }

    private Resp lire(String chemin, org.springframework.test.web.servlet.request.RequestPostProcessor qui) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(java.net.URI.create(GROUPES + chemin))
                .with(qui));
    }

    private Resp modifier(UUID id, String corps, org.springframework.test.web.servlet.request.RequestPostProcessor qui)
            throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(java.net.URI.create(GROUPES + "/" + id))
                .contentType(MediaType.APPLICATION_JSON).content(corps).with(qui));
    }

    private Resp supprimer(UUID id, org.springframework.test.web.servlet.request.RequestPostProcessor qui) throws Exception {
        return send(delete(java.net.URI.create(GROUPES + "/" + id)).with(qui));
    }

    private void goldenGroupes(String name, UUID centre) {
        goldenSql(name, "SELECT g.nom, g.nom_cle, g.description, (g.updated_at IS NULL) AS updated_at_nul, "
                + "(SELECT COUNT(*) FROM groupes_articles_items i WHERE i.groupe_id = g.id) AS nb_items "
                + "FROM groupes_articles g WHERE g.center_id = '" + centre + "' ORDER BY g.nom_cle");
    }

    private void goldenItems(String name, UUID centre) {
        goldenSql(name, "SELECT g.nom_cle, i.article_id FROM groupes_articles_items i JOIN groupes_articles g ON g.id = i.groupe_id "
                + "WHERE g.center_id = '" + centre + "' ORDER BY g.nom_cle, i.article_id");
    }

    // ------------------------------------------------------------------ création

    @Test
    void la_creation_nettoie_le_nom_et_la_description_et_dedoublonne_les_articles() throws Exception {
        UUID a1 = articleFixe(CENTRE_A, "ART_1", 1, true);
        UUID a2 = articleFixe(CENTRE_A, "ART_2", 2, true);
        UUID inactif = articleFixe(CENTRE_A, "ART_INACTIF", 3, false);
        golden("creation_nominale_201", poster(GROUPES, corps("  Kit   CNAS  ", "  kit mensuel  ", List.of(a2, a1, a2, inactif)),
                adminA()));
        goldenGroupes("creation_nominale_etat_base", CENTRE_A);
        goldenItems("creation_nominale_items", CENTRE_A);
    }

    @Test
    void la_description_blanche_est_absente_et_le_centre_vient_de_la_session() throws Exception {
        UUID a1 = articleFixe(CENTRE_A, "ART_1", 1, true);
        golden("creation_description_blanche_201", poster(GROUPES, corps("Kit", "   ", List.of(a1)), adminA()));
        golden("creation_centre_explicite_201", poster(GROUPES + "?centerId=" + CENTRE_A, corps("Kit 2", null, List.of(a1)), adminA()));
        golden("refus_creation_centre_autre_que_la_session", poster(GROUPES + "?centerId=" + CENTRE_B, corps("Kit 3", null, List.of(a1)),
                adminA()));
        goldenGroupes("creation_centre_etat_base", CENTRE_A);
        assertEquals(0, count("groupes_articles", CENTRE_B));
    }

    @Test
    void un_groupe_compte_de_1_a_500_articles() throws Exception {
        List<UUID> cinqCents = new ArrayList<>();
        IntStream.rangeClosed(1, 501).forEach(n -> cinqCents.add(articleFixe(CENTRE_A, "A" + n, n, true)));
        Resp cinqCentsOk = creer("Gros kit", cinqCents.subList(0, 500));
        assertEquals(201, cinqCentsOk.status());
        assertEquals(500, json(cinqCentsOk).path("nbArticles").asInt());
        golden("refus_501_articles", creer("Trop gros", cinqCents));
        golden("refus_liste_vide", creer("Vide", List.of()));
        java.util.List<UUID> seulementNull = new ArrayList<>();
        seulementNull.add(null);
        golden("refus_liste_de_null", creer("Null", seulementNull));
        golden("refus_sans_liste", poster(GROUPES, json("{'nom':'Sans liste'}"), adminA()));
        assertEquals(1, count("groupes_articles", CENTRE_A));
    }

    @Test
    void le_nom_et_la_description_sont_obligatoires_et_bornes() throws Exception {
        UUID a1 = articleFixe(CENTRE_A, "ART_1", 1, true);
        golden("refus_nom_absent", creer(null, List.of(a1)));
        golden("refus_nom_blanc", creer("   ", List.of(a1)));
        golden("refus_nom_101", creer("N".repeat(101), List.of(a1)));
        golden("nom_100_201", creer("N".repeat(100), List.of(a1)));
        golden("refus_nom_100_entoure_d_espaces", creer(" " + "M".repeat(100) + " ", List.of(a1)));
        golden("refus_description_501", poster(GROUPES, corps("Desc", "D".repeat(501), List.of(a1)), adminA()));
        golden("description_500_201", poster(GROUPES, corps("Desc 2", "D".repeat(500), List.of(a1)), adminA()));
        golden("refus_corps_absent", send(post(java.net.URI.create(GROUPES)).contentType(MediaType.APPLICATION_JSON).with(adminA())));
        golden("refus_json_illisible", poster(GROUPES, "{pas du json", adminA()));
        goldenGroupes("bornes_etat_base", CENTRE_A);
    }

    @Test
    void le_nom_est_unique_par_centre_sans_casse_accents_ni_espaces_multiples() throws Exception {
        UUID a1 = articleFixe(CENTRE_A, "ART_1", 1, true);
        UUID b1 = articleFixe(CENTRE_B, "ART_B1", 101, true);
        assertEquals(201, creer("Kit CNAS", List.of(a1)).status());
        golden("refus_nom_casse_differente", creer("KIT cnas", List.of(a1)));
        golden("refus_nom_espaces_multiples", creer("Kit    CNAS", List.of(a1)));
        golden("refus_nom_accentue", creer("Kït CNÀS", List.of(a1)));
        golden("nom_proche_201", creer("Kit CNAS 2", List.of(a1)));
        golden("meme_nom_autre_centre_201", poster(GROUPES, corps("Kit CNAS", null, List.of(b1)), as(CENTRE_B, "ADMIN")));
        goldenGroupes("unicite_etat_base", CENTRE_A);
    }

    @Test
    void un_groupe_ne_contient_que_des_articles_du_centre() throws Exception {
        UUID a1 = articleFixe(CENTRE_A, "ART_1", 1, true);
        UUID b1 = articleFixe(CENTRE_B, "ART_B1", 101, true);
        golden("refus_article_d_un_autre_centre", creer("Mixte", List.of(a1, b1)));
        golden("refus_article_inconnu", creer("Inconnu", List.of(a1, UUID.fromString("99997500-0000-0000-0001-999999999999"))));
        assertEquals(0, count("groupes_articles", CENTRE_A));
    }

    // ------------------------------------------------------------------ lecture et liste

    @Test
    void la_lecture_d_un_groupe() throws Exception {
        UUID a1 = articleFixe(CENTRE_A, "ART_1", 1, true);
        UUID b1 = articleFixe(CENTRE_B, "ART_B1", 101, true);
        UUID g = idDe(creer("Kit", List.of(a1)));
        aliasNommes.put(g.toString(), "<GROUPE_A>");
        UUID gb = idDe(poster(GROUPES, corps("Kit B", null, List.of(b1)), as(CENTRE_B, "ADMIN")));
        aliasNommes.put(gb.toString(), "<GROUPE_B>");
        golden("lecture_200", lire("/" + g, adminA()));
        golden("lecture_centre_explicite_200", lire("/" + g + "?centerId=" + CENTRE_A, adminA()));
        golden("refus_lecture_inconnu", lire("/" + UUID.fromString("99997500-0000-0000-0002-000000000001"), adminA()));
        golden("refus_lecture_groupe_d_un_autre_centre", lire("/" + gb, adminA()));
        golden("refus_lecture_centre_autre_que_la_session", lire("/" + gb + "?centerId=" + CENTRE_B, adminA()));
        golden("refus_lecture_identifiant_illisible", lire("/pas-un-uuid", adminA()));
    }

    @Test
    void la_liste_est_paginee_et_triee_par_nom_normalise() throws Exception {
        UUID a1 = articleFixe(CENTRE_A, "ART_1", 1, true);
        UUID b1 = articleFixe(CENTRE_B, "ART_B1", 101, true);
        for (String nom : List.of("zeta", "Alpha", "élan", "Beta")) {
            UUID g = idDe(creer(nom, List.of(a1)));
            aliasNommes.put(g.toString(), "<G_" + nom.toUpperCase().replace('É', 'E') + ">");
        }
        UUID gb = idDe(poster(GROUPES, corps("Groupe du centre B", null, List.of(b1)), as(CENTRE_B, "ADMIN")));
        aliasNommes.put(gb.toString(), "<G_CENTRE_B>");
        golden("liste_defaut_200", lire("", adminA()));
        golden("liste_page_2_taille_2_200", lire("?page=1&size=2", adminA()));
        golden("liste_page_hors_limites_200", lire("?page=9&size=2", adminA()));
        golden("liste_grande_taille_200", lire("?size=100000", adminA()));
        golden("refus_liste_taille_nulle", lire("?size=0", adminA()));
        golden("refus_liste_page_negative", lire("?page=-1", adminA()));
        golden("refus_liste_taille_illisible", lire("?size=abc", adminA()));
        golden("refus_liste_centre_autre_que_la_session", lire("?centerId=" + CENTRE_B, adminA()));
    }

    // ------------------------------------------------------------------ modification

    @Test
    void la_modification_remplace_nom_description_et_composition() throws Exception {
        UUID a1 = articleFixe(CENTRE_A, "ART_1", 1, true);
        UUID a2 = articleFixe(CENTRE_A, "ART_2", 2, true);
        UUID a3 = articleFixe(CENTRE_A, "ART_3", 3, true);
        UUID g = idDe(poster(GROUPES, corps("Kit CNAS", "ancienne", List.of(a1, a2)), adminA()));
        aliasNommes.put(g.toString(), "<GROUPE_A>");
        UUID autre = idDe(creer("Autre", List.of(a1)));
        aliasNommes.put(autre.toString(), "<AUTRE>");
        golden("modification_200", modifier(g, corps(" Kit   Mensuel ", null, List.of(a3, a2)), adminA()));
        golden("modification_meme_nom_casse_differente_200", modifier(g, corps("KIT MENSUEL", "d", List.of(a3)), adminA()));
        golden("refus_modification_vers_nom_d_un_autre", modifier(g, corps("autre", null, List.of(a3)), adminA()));
        golden("refus_modification_article_d_un_autre_centre",
                modifier(g, corps("KIT MENSUEL", null, List.of(articleFixe(CENTRE_B, "ART_B1", 101, true))), adminA()));
        golden("refus_modification_liste_vide", modifier(g, corps("KIT MENSUEL", null, List.of()), adminA()));
        golden("refus_modification_inconnu", modifier(UUID.fromString("99997500-0000-0000-0002-000000000001"),
                corps("X", null, List.of(a1)), adminA()));
        goldenGroupes("modification_etat_base", CENTRE_A);
        goldenItems("modification_items", CENTRE_A);
    }

    @Test
    void la_modification_d_un_groupe_d_un_autre_centre() throws Exception {
        UUID b1 = articleFixe(CENTRE_B, "ART_B1", 101, true);
        UUID gb = idDe(poster(GROUPES, corps("Kit B", null, List.of(b1)), as(CENTRE_B, "ADMIN")));
        aliasNommes.put(gb.toString(), "<GROUPE_B>");
        golden("refus_modification_groupe_d_un_autre_centre", modifier(gb, corps("Intrus", null, List.of(b1)), adminA()));
        golden("refus_modification_centre_autre_que_la_session",
                send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                java.net.URI.create(GROUPES + "/" + gb + "?centerId=" + CENTRE_B))
                        .contentType(MediaType.APPLICATION_JSON).content(corps("Intrus", null, List.of(b1))).with(adminA())));
        goldenGroupes("autre_centre_inchange", CENTRE_B);
    }

    // ------------------------------------------------------------------ suppression

    @Test
    void la_suppression_retire_le_groupe_et_sa_composition() throws Exception {
        UUID a1 = articleFixe(CENTRE_A, "ART_1", 1, true);
        UUID b1 = articleFixe(CENTRE_B, "ART_B1", 101, true);
        UUID g = idDe(creer("Kit", List.of(a1)));
        UUID gb = idDe(poster(GROUPES, corps("Kit B", null, List.of(b1)), as(CENTRE_B, "ADMIN")));
        golden("suppression_204", supprimer(g, adminA()));
        golden("refus_lecture_apres_suppression", lire("/" + g, adminA()));
        golden("refus_seconde_suppression", supprimer(g, adminA()));
        golden("refus_suppression_groupe_d_un_autre_centre", supprimer(gb, adminA()));
        assertEquals(0, count("groupes_articles", CENTRE_A));
        assertEquals(1, count("groupes_articles", CENTRE_B));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM groupes_articles_items WHERE groupe_id = ?", Integer.class, g));
        assertEquals(1, count("articles", CENTRE_A));
    }

    // ------------------------------------------------------------------ rôles

    @Test
    void la_gestion_des_groupes_est_reservee_a_l_administrateur() throws Exception {
        UUID a1 = articleFixe(CENTRE_A, "ART_1", 1, true);
        UUID g = idDe(creer("Kit", List.of(a1)));
        for (String role : List.of("PHARMACIEN", "INFIRMIER", "MEDECIN", "SECRETAIRE")) {
            golden("refus_liste_" + role.toLowerCase(), lire("", as(CENTRE_A, role)));
            golden("refus_creation_" + role.toLowerCase(), poster(GROUPES, corps("K " + role, null, List.of(a1)), as(CENTRE_A, role)));
        }
        golden("refus_modification_pharmacien", modifier(g, corps("X", null, List.of(a1)), as(CENTRE_A, "PHARMACIEN")));
        golden("refus_suppression_pharmacien", supprimer(g, as(CENTRE_A, "PHARMACIEN")));
        golden("refus_anonyme", send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                java.net.URI.create(GROUPES))));
        golden("refus_direction", lire("", as(CENTRE_A, "DIRECTION")));
        assertEquals(1, count("groupes_articles", CENTRE_A));
        assertEquals(0, new BigDecimal(count("groupes_articles", CENTRE_B)).intValue());
    }
}

