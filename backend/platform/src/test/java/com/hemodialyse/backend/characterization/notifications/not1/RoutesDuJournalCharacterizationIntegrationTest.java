package com.hemodialyse.backend.characterization.notifications.not1;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NOT1 : les trois routes {@code GET /api/v1/notifications}, {@code POST .../lecture}, {@code POST .../lecture-tout}.
 * Jeu de reference ({@link #jeu()}) : N01 ADMIN, N02 ADMIN+SECRETAIRE, N03 tout le monde, N04 INFIRMIER, N05 ADMIN + compte INF,
 * N06 MEDECIN (centre A), N07 ADMIN (centre B), N08 ADMIN vieille de 31 jours (hors fenetre de 30 jours).
 */
class RoutesDuJournalCharacterizationIntegrationTest extends Not1Support {

    private void jeu() {
        evenement(1, CENTRE_A, "GENERATEUR_INDISPONIBLE", "ADMIN", 50);
        evenement(2, CENTRE_A, "OPTIMISATION_PROPOSITION", "ADMIN,SECRETAIRE", 40);
        evenement(3, CENTRE_A, "REPLANIFICATION_NOCTURNE", "", 30);
        evenement(4, CENTRE_A, "INFIRMIER_SOUS_EFFECTIF", "INFIRMIER", 20);
        evenement(5, CENTRE_A, "INFIRMIER_ROULEMENT_MODIFIE", "{\"cle\":\"v5\",\"targetRoles\":\"ADMIN\",\"targetUserId\":\"" + USER_INF + "\"}",
                "ADMIN", USER_INF, il_y_a(10));
        evenement(6, CENTRE_A, "OBSERVANCE_NON_RESPECTEE", "MEDECIN", 5);
        evenement(7, CENTRE_B, "GENERATEUR_INDISPONIBLE", "ADMIN", 1);
        evenement(8, CENTRE_A, "GENERATEUR_INDISPONIBLE", "{\"cle\":\"v8\",\"targetRoles\":\"ADMIN\"}", "ADMIN", null, il_y_a_jours(31, 0));
    }

    @Test
    void le_journal_vide_est_une_page_vide_sans_cache() throws Exception {
        golden("liste_vide", vue(get(null, admin())));
    }

    @Test
    void une_alerte_est_servie_avec_son_contenu_son_centre_et_son_etat_de_lecture() throws Exception {
        evenement(1, CENTRE_A, "GENERATEUR_INDISPONIBLE",
                "{\"generateur\":\"A-G1\",\"statut\":\"HORS_SERVICE\",\"patients\":\"BENALI Karim\",\"targetRoles\":\"ADMIN,MEDECIN\"}",
                "ADMIN,MEDECIN", null, il_y_a(5));
        golden("alerte_non_lue", vue(get(null, admin())));
        post("/lecture", null, lecture(nid(1)), admin());
        golden("alerte_lue", vue(get(null, admin())));
    }

    @Test
    void la_visibilite_depend_des_roles_de_la_session_et_du_compte_destinataire() throws Exception {
        jeu();
        Map<String, Object> profils = new LinkedHashMap<>();
        Map<String, RequestPostProcessor> qui = new LinkedHashMap<>();
        qui.put("ADMIN", admin());
        qui.put("SECRETAIRE", qui(CENTRE_A, USER_ADMIN_2, "SECRETAIRE"));
        qui.put("MEDECIN", qui(CENTRE_A, USER_MED, "MEDECIN"));
        qui.put("INFIRMIER (compte destinataire de N05)", qui(CENTRE_A, USER_INF, "INFIRMIER"));
        qui.put("INFIRMIER (autre compte)", qui(CENTRE_A, USER_INF_2, "INFIRMIER"));
        qui.put("ADMIN + MEDECIN", qui(CENTRE_A, USER_ADMIN_2, "ADMIN", "MEDECIN"));
        qui.put("SUPERADMIN seul", qui(CENTRE_A, USER_ADMIN_2, "SUPERADMIN"));
        qui.put("DIRECTION", qui(CENTRE_A, USER_ADMIN_2, "DIRECTION"));
        qui.put("COMPTABLE (role inconnu)", qui(CENTRE_A, USER_ADMIN_2, "COMPTABLE"));
        qui.put("aucun role", qui(CENTRE_A, USER_ADMIN_2));
        qui.put("ADMIN du centre B", qui(CENTRE_B, USER_B, "ADMIN"));
        for (var p : qui.entrySet()) profils.put(p.getKey(), resume(get(null, p.getValue())));
        golden("visibilite_par_profil", profils);
    }

    @Test
    void un_role_dont_le_nom_contient_un_souligne_ne_filtre_pas_comme_un_texte_exact() throws Exception {
        evenement(1, CENTRE_A, "REFORME_PROPOSEE", "GMAO_REFORME", 10);
        evenement(2, CENTRE_A, "REFORME_PROPOSEE", "GMAOXREFORME", 5);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("role GMAO_REFORME (N01 cible ; N02 vise GMAOXREFORME)", resume(get(null, qui(CENTRE_A, USER_ADMIN, "GMAO_REFORME"))));
        cas.put("role GMAOXREFORME (N02 cible)", resume(get(null, qui(CENTRE_A, USER_ADMIN, "GMAOXREFORME"))));
        cas.put("role GMAO_RECTIFICATION (autre nom)", resume(get(null, qui(CENTRE_A, USER_ADMIN, "GMAO_RECTIFICATION"))));
        golden("role_avec_souligne_nouveau_code", cas);
    }

    @Test
    void le_journal_ne_sert_que_la_fenetre_de_trente_jours_du_plus_recent_au_plus_ancien() throws Exception {
        jeu();
        evenement(9, CENTRE_A, "SEANCES_DEPLACEES", "{\"cle\":\"v9\"}", "ADMIN", null, il_y_a_jours(29, 0));
        evenement(10, CENTRE_A, "SEANCES_DEPLACEES", "{\"cle\":\"v10\"}", "ADMIN", null, il_y_a_jours(30, 60));
        golden("fenetre_et_ordre", resume(get(null, admin())));
    }

    @Test
    void la_pagination_est_bornee_de_1_a_100_et_a_partir_de_zero() throws Exception {
        for (int i = 1; i <= 7; i++) evenement(i, CENTRE_A, "REPLANIFICATION_NOCTURNE", "", 100 - i);
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String q : List.of("page=0&size=3", "page=1&size=3", "page=2&size=3", "page=9&size=3", "size=0", "size=-5",
                "size=5000", "page=-3&size=2", "size=100", "size=101", "page=1")) {
            cas.put(q, resume(get(q, admin())));
        }
        golden("pagination", cas);
    }

    @Test
    void les_parametres_illisibles_de_la_liste_sont_refuses() throws Exception {
        jeu();
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String q : List.of("page=abc", "size=abc", "page=1.5", "size=", "centerId=pas-un-uuid", "page=99999999999")) {
            cas.put(q, vue(get(q, admin())));
        }
        golden("parametres_illisibles_nouveau_code", cas);
    }

    @Test
    void marquer_comme_lues_est_personnel_idempotent_et_borne_au_centre() throws Exception {
        jeu();
        RequestPostProcessor admin2 = qui(CENTRE_A, USER_ADMIN_2, "ADMIN");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("1 marquer N01 et N02 (admin)", vue(post("/lecture", null, lecture(nid(1), nid(2)), admin())));
        cas.put("2 liste admin", resume(get(null, admin())));
        cas.put("3 liste du second admin (rien de lu)", resume(get(null, admin2)));
        cas.put("4 rejouer N01 + doublon N01 + id inconnu", vue(post("/lecture", null,
                lecture(nid(1), nid(1), UUID.fromString("99998140-0000-0000-0000-000000000199")), admin())));
        cas.put("5 liste vide d'ids", vue(post("/lecture", null, lecture(), admin())));
        cas.put("6 N07 est au centre B (ignore)", vue(post("/lecture", null, lecture(nid(7)), admin())));
        cas.put("7 N04 reserve aux infirmiers, marque par un admin (ignore : hors audience, KB-NOT1-3)", vue(post("/lecture", null, lecture(nid(4)), admin())));
        cas.put("8 N08 hors fenetre (accepte)", vue(post("/lecture", null, lecture(nid(8)), admin())));
        cas.put("lignes de lecture de l'admin / du second admin", List.of(lectures(USER_ADMIN), lectures(USER_ADMIN_2)));
        golden("marquer_lues_nouveau_code", cas);
    }

    @Test
    void tout_marquer_lu_ne_touche_que_les_alertes_visibles_du_centre_et_de_la_fenetre() throws Exception {
        jeu();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("1 lecture-tout (admin)", vue(post("/lecture-tout", null, null, admin())));
        cas.put("2 liste admin", resume(get(null, admin())));
        cas.put("3 lignes de lecture admin (N01 N02 N03 N05 attendus)", lectures(USER_ADMIN));
        cas.put("4 rejouer", vue(post("/lecture-tout", null, null, admin())));
        cas.put("5 lignes de lecture apres rejeu", lectures(USER_ADMIN));
        cas.put("6 liste du medecin (non lues)", resume(get(null, qui(CENTRE_A, USER_MED, "MEDECIN"))));
        cas.put("7 lecture-tout infirmier (compte destinataire)", vue(post("/lecture-tout", null, null, qui(CENTRE_A, USER_INF, "INFIRMIER"))));
        cas.put("8 lignes de lecture de l'infirmier (N03 N04 N05)", lectures(USER_INF));
        cas.put("9 lecture-tout sans aucun role (tout le monde seulement)", vue(post("/lecture-tout", null, null, qui(CENTRE_A, USER_INF_2))));
        cas.put("10 lignes de lecture sans role", lectures(USER_INF_2));
        golden("lecture_tout", cas);
    }

    @Test
    void les_corps_mal_formes_de_la_lecture_sont_refuses_ou_plantent() throws Exception {
        jeu();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("sans corps", vue(post("/lecture", null, null, admin())));
        cas.put("corps vide {}", vue(post("/lecture", null, "{}", admin())));
        cas.put("ids null", vue(post("/lecture", null, "{\"ids\":null}", admin())));
        cas.put("ids illisible", vue(post("/lecture", null, "{\"ids\":[\"pas-un-uuid\"]}", admin())));
        cas.put("ids n'est pas un tableau", vue(post("/lecture", null, "{\"ids\":\"x\"}", admin())));
        cas.put("ids contient null", vue(post("/lecture", null, "{\"ids\":[null]}", admin())));
        cas.put("json casse", vue(post("/lecture", null, "{\"ids\":[", admin())));
        cas.put("champ inconnu seul", vue(post("/lecture", null, "{\"id\":\"x\"}", admin())));
        cas.put("lignes de lecture apres tous ces essais", lectures(USER_ADMIN));
        golden("corps_mal_formes_nouveau_code", cas);
    }

    @Test
    void la_garde_de_centre_et_l_authentification_gardent_les_trois_routes() throws Exception {
        jeu();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("GET centerId = centre de la session", resume(get("centerId=" + CENTRE_A, admin())));
        cas.put("GET centerId = autre centre (admin A)", vue(get("centerId=" + CENTRE_B, admin())));
        cas.put("POST lecture centerId = autre centre", vue(post("/lecture", "centerId=" + CENTRE_B, lecture(nid(7)), admin())));
        cas.put("POST lecture-tout centerId = autre centre", vue(post("/lecture-tout", "centerId=" + CENTRE_B, null, admin())));
        cas.put("GET anonyme", vue(get(null, null)));
        cas.put("POST lecture anonyme", vue(post("/lecture", null, lecture(nid(1)), null)));
        cas.put("POST lecture-tout anonyme", vue(post("/lecture-tout", null, null, null)));
        cas.put("GET session sans centre (admin)", vue(get(null, qui(null, USER_ADMIN, "ADMIN"))));
        cas.put("GET SUPERADMIN+ADMIN centerId = centre B", resume(get("centerId=" + CENTRE_B, qui(CENTRE_A, USER_ADMIN_2, "SUPERADMIN", "ADMIN"))));
        cas.put("GET SUPERADMIN seul centerId = centre B", vue(get("centerId=" + CENTRE_B, qui(CENTRE_A, USER_ADMIN_2, "SUPERADMIN"))));
        cas.put("lignes de lecture creees par les refus", lectures(USER_ADMIN));
        assertThat(evenements()).isEqualTo(8);
        golden("garde_de_centre_et_authentification", cas);
    }

    @Test
    void les_alertes_d_un_centre_ne_sont_jamais_lues_ni_marquees_depuis_un_autre() throws Exception {
        jeu();
        RequestPostProcessor adminB = qui(CENTRE_B, USER_B, "ADMIN");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("1 liste du centre B", resume(get(null, adminB)));
        cas.put("2 B marque N01 (centre A) lue", vue(post("/lecture", null, lecture(nid(1)), adminB)));
        cas.put("3 lecture-tout du centre B", vue(post("/lecture-tout", null, null, adminB)));
        cas.put("4 liste admin A (N01 reste non lue)", resume(get(null, admin())));
        cas.put("5 lignes de lecture de B (N07 seulement)", lectures(USER_B));
        golden("cloisonnement_par_centre", cas);
    }
}
