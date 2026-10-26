package com.hemodialyse.backend.characterization.stock.referentiel;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Filet de caractérisation S8 — valorisation historique du stock lue par la Direction : {@code GET
 * /api/v1/direction/stock-groupes} ({@code DirectionStockGroupesQueryService} → {@code ValorisationArticlesService} → rejeu de
 * l'historique des mouvements, RG-STK-043). Point d'observation : HTTP (sécurité réelle, rôle DIRECTION, société du jeton revérifiée
 * en base) sur des mouvements posés en SQL à des dates absolues de janvier 2026 (aucune dépendance au jour d'exécution). Les
 * exemples couvrent : stock au début, entrées, sorties valorisées au PMP, inventaire, mouvement hors période, consolidation d'un même
 * groupe (clé sans casse ni accent) entre centres, centre inactif de la société, autre société, groupe sans mouvement.
 */
class ValorisationDeLaDirectionCharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final String ROUTE = "/api/v1/direction/stock-groupes";
    private static final UUID SOCIETE_1 = UUID.fromString("99997500-0000-0000-0020-000000000001");
    private static final UUID SOCIETE_2 = UUID.fromString("99997500-0000-0000-0020-000000000002");
    private static final UUID CENTRE_INACTIF = UUID.fromString("99997500-0000-0000-0020-0000000000c1");
    private static final UUID CENTRE_AUTRE_SOCIETE = UUID.fromString("99997500-0000-0000-0020-0000000000d1");
    private static final UUID DIRECTEUR_1 = UUID.fromString("99997500-0000-0000-0020-0000000000e1");
    private static final UUID DIRECTEUR_2 = UUID.fromString("99997500-0000-0000-0020-0000000000e2");

    @Override
    protected String theme() {
        return "valorisation-direction";
    }

    // ------------------------------------------------------------------ données

    @BeforeEach
    void preparer() {
        nettoyer();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?, 'ZT-S8V-1', 'Société valorisation 1', TRUE, CURRENT_TIMESTAMP)", SOCIETE_1);
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?, 'ZT-S8V-2', 'Société valorisation 2', TRUE, CURRENT_TIMESTAMP)", SOCIETE_2);
        centre(CENTRE_A, "ZT-S8V-A", SOCIETE_1, true);
        centre(CENTRE_B, "ZT-S8V-B", SOCIETE_1, true);
        centre(CENTRE_INACTIF, "ZT-S8V-C", SOCIETE_1, false);
        centre(CENTRE_AUTRE_SOCIETE, "ZT-S8V-D", SOCIETE_2, true);
        for (UUID[] u : new UUID[][]{{DIRECTEUR_1, SOCIETE_1}, {DIRECTEUR_2, SOCIETE_2}}) {
            jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                    u[0], "zt-s8v-" + u[0].toString().substring(31), "x", null, "Direction");
            jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", u[0], u[1]);
        }
        aliasNommes.put(SOCIETE_1.toString(), "<SOCIETE_1>");
        aliasNommes.put(SOCIETE_2.toString(), "<SOCIETE_2>");
        aliasNommes.put(CENTRE_INACTIF.toString(), "<CENTRE_INACTIF>");
        aliasNommes.put(CENTRE_AUTRE_SOCIETE.toString(), "<CENTRE_AUTRE_SOCIETE>");
    }

    @AfterEach
    void nettoyer() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B, CENTRE_INACTIF, CENTRE_AUTRE_SOCIETE)) {
            jdbc.update("DELETE FROM groupes_articles_items WHERE groupe_id IN (SELECT id FROM groupes_articles WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM groupes_articles WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM articles WHERE center_id = ?", c);
            jdbc.update("DELETE FROM centers WHERE id = ?", c);
        }
        for (UUID u : List.of(DIRECTEUR_1, DIRECTEUR_2)) {
            jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", u);
            jdbc.update("DELETE FROM app_user WHERE id = ?", u);
        }
        jdbc.update("DELETE FROM societes WHERE id IN (?, ?)", SOCIETE_1, SOCIETE_2);
    }

    private void centre(UUID id, String code, UUID societe, boolean actif) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, ?, ?, ?, ?)", id, code, "Centre " + code, societe, actif);
    }

    private UUID art(UUID centre, String alias) {
        return article(centre, alias, alias, "Article " + alias, null, null, true, null, "0", "0");
    }

    private void groupe(UUID centre, String nom, String cle, UUID... articles) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO groupes_articles (id, center_id, nom, nom_cle, description, updated_at) VALUES (?, ?, ?, ?, NULL, CURRENT_TIMESTAMP)",
                id, centre, nom, cle);
        for (UUID a : articles) {
            jdbc.update("INSERT INTO groupes_articles_items (groupe_id, article_id) VALUES (?, ?)", id, a);
        }
    }

    private void mouvement(UUID centre, UUID article, String type, String quantite, String prix, String jour) {
        OffsetDateTime quand = OffsetDateTime.parse(jour + "T12:00:00Z");
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, mouvement_type, quantite, prix_unitaire, created_by, created_at, saisi_le) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'test', ?, ?)", UUID.randomUUID(), centre, article, type, new BigDecimal(quantite),
                prix == null ? null : new BigDecimal(prix), quand, quand.plusMinutes(1));
    }

    private static RequestPostProcessor direction(UUID directeur, UUID societe) {
        return user(new UserPrincipal(directeur.toString(), null, "u-direction", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, societe.toString()));
    }

    private Resp lire(String requete, RequestPostProcessor qui) throws Exception {
        return send(get(java.net.URI.create(ROUTE + requete)).with(qui));
    }

    /**
     * Société 1 : « KIT CNAS » dans A (A1, A2), B (B1, saisi « kit  cnas ») et le centre inactif ; « Hémodialyse » dans B (B2, sans
     * mouvement). Société 2 : « KIT CNAS » dans un autre centre.
     */
    private void jeu() {
        UUID a1 = art(CENTRE_A, "ART_A1");
        UUID a2 = art(CENTRE_A, "ART_A2");
        UUID a3 = art(CENTRE_A, "ART_A3_HORS_GROUPE");
        UUID b1 = art(CENTRE_B, "ART_B1");
        UUID b2 = art(CENTRE_B, "ART_B2");
        UUID c1 = art(CENTRE_INACTIF, "ART_C1");
        UUID d1 = art(CENTRE_AUTRE_SOCIETE, "ART_D1");
        groupe(CENTRE_A, "KIT CNAS", "kit cnas", a1, a2);
        groupe(CENTRE_A, "Dialyse", "dialyse", a2);
        groupe(CENTRE_B, "kit  CNAS", "kit cnas", b1);
        groupe(CENTRE_B, "Hémodialyse", "hemodialyse", b2);
        groupe(CENTRE_INACTIF, "Kit CNAS", "kit cnas", c1);
        groupe(CENTRE_AUTRE_SOCIETE, "KIT CNAS", "kit cnas", d1);
        mouvement(CENTRE_A, a1, "ENTREE", "100", "10", "2025-12-10");
        mouvement(CENTRE_A, a1, "SORTIE", "30", null, "2026-01-10");
        mouvement(CENTRE_A, a1, "ENTREE", "50", "13", "2026-01-20");
        mouvement(CENTRE_A, a1, "INVENTAIRE", "115", "13", "2026-01-25");
        mouvement(CENTRE_A, a2, "ENTREE", "20", "7", "2026-01-05");
        mouvement(CENTRE_A, a2, "SORTIE", "5", null, "2026-02-03");
        mouvement(CENTRE_A, a3, "ENTREE", "1000", "99", "2026-01-06");
        mouvement(CENTRE_B, b1, "ENTREE", "10", "20", "2026-01-12");
        mouvement(CENTRE_B, b1, "SORTIE", "4", null, "2026-01-15");
        mouvement(CENTRE_INACTIF, c1, "ENTREE", "8", "5", "2026-01-08");
        mouvement(CENTRE_AUTRE_SOCIETE, d1, "ENTREE", "500", "50", "2026-01-09");
    }

    // ------------------------------------------------------------------ scénarios

    @Test
    void la_valorisation_de_janvier_consolide_les_groupes_de_meme_cle_entre_les_centres_de_la_societe() throws Exception {
        jeu();
        golden("janvier_2026_societe_1", lire("?from=2026-01-01&to=2026-01-31", direction(DIRECTEUR_1, SOCIETE_1)));
    }

    @Test
    void la_valorisation_distingue_stock_au_debut_entrees_sorties_au_pmp_et_autres_variations() throws Exception {
        jeu();
        golden("journee_du_20_janvier", lire("?from=2026-01-20&to=2026-01-20", direction(DIRECTEUR_1, SOCIETE_1)));
        golden("avant_les_mouvements", lire("?from=2025-11-01&to=2025-11-30", direction(DIRECTEUR_1, SOCIETE_1)));
        golden("decembre_2025", lire("?from=2025-12-01&to=2025-12-31", direction(DIRECTEUR_1, SOCIETE_1)));
        golden("janvier_et_fevrier_2026", lire("?from=2026-01-01&to=2026-02-28", direction(DIRECTEUR_1, SOCIETE_1)));
    }

    @Test
    void la_direction_de_l_autre_societe_ne_voit_que_ses_centres() throws Exception {
        jeu();
        golden("janvier_2026_societe_2", lire("?from=2026-01-01&to=2026-01-31", direction(DIRECTEUR_2, SOCIETE_2)));
    }

    @Test
    void une_societe_sans_groupe_d_articles_obtient_une_liste_vide() throws Exception {
        golden("societe_sans_groupe", lire("?from=2026-01-01&to=2026-01-31", direction(DIRECTEUR_1, SOCIETE_1)));
    }

    @Test
    void les_periodes_invalides_sont_refusees_et_les_acces_non_direction_aussi() throws Exception {
        jeu();
        golden("refus_debut_apres_fin", lire("?from=2026-02-01&to=2026-01-01", direction(DIRECTEUR_1, SOCIETE_1)));
        golden("refus_plus_de_cinq_ans", lire("?from=2020-01-01&to=2026-01-02", direction(DIRECTEUR_1, SOCIETE_1)));
        golden("cinq_ans_exactement", lire("?from=2021-01-31&to=2026-01-31", direction(DIRECTEUR_1, SOCIETE_1)));
        golden("date_illisible_nouveau_code", lire("?from=01-01-2026&to=2026-01-31", direction(DIRECTEUR_1, SOCIETE_1)));
        golden("refus_admin", lire("?from=2026-01-01&to=2026-01-31", adminA()));
        golden("refus_anonyme", send(get(java.net.URI.create(ROUTE + "?from=2026-01-01&to=2026-01-31"))));
        golden("refus_direction_rattachee_a_une_autre_societe", lire("?from=2026-01-01&to=2026-01-31", direction(DIRECTEUR_2, SOCIETE_1)));
    }

    @Test
    void un_nouveau_mouvement_est_visible_au_second_appel_apres_l_evenement_de_mouvement() throws Exception {
        jeu();
        RequestPostProcessor qui = direction(DIRECTEUR_1, SOCIETE_1);
        golden("cache_premier_appel", lire("?from=2026-01-01&to=2026-01-31", qui));
        UUID a1 = jdbc.queryForObject("SELECT id FROM articles WHERE center_id = ? AND code = 'ART_A1'", UUID.class, CENTRE_A);
        mouvement(CENTRE_A, a1, "ENTREE", "10", "100", "2026-01-28");
        golden("cache_second_appel_identique_sans_evenement", lire("?from=2026-01-01&to=2026-01-31", qui));
        caches.getCache("stock.valorisation.articles").clear();
        golden("cache_apres_vidage", lire("?from=2026-01-01&to=2026-01-31", qui));
    }
}
