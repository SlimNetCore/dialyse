package com.hemodialyse.backend.characterization.seance.se3;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE3, lot 4 : raccourcis de consommables du poste infirmier ({@code GET|PUT /api/v1/seances/raccourcis-consommables}) : lecture, remplacement
 * complet, bornes (douze articles, doublon, article inactif, inconnu ou d'un autre centre), cache par centre purgé à l'écriture, droits par
 * profil, cloisonnement, audit.
 */
class LesRaccourcisDeConsommablesCharacterizationIntegrationTest extends Se3CharacterizationSupport {

    @Override
    protected String dossier() {
        return "raccourcis";
    }

    @Test
    void l_administrateur_remplace_les_raccourcis_du_centre_l_ordre_est_conserve_et_chaque_remplacement_est_complet() throws Exception {
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID a2 = articleSe2(CENTRE_A, "ART_2", true, "10");
        UUID a3 = articleSe2(CENTRE_A, "ART_3", true, "10");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 lecture initiale (aucun raccourci)", reponse(lireLesRaccourcis(CENTRE_A, infirmierA())));
        m.put("02 remplacement par 3, 1, 2", reponse(remplacerLesRaccourcis(CENTRE_A, List.of(a3, a1, a2), adminA())));
        m.put("03 etat", etatDesRaccourcis());
        m.put("04 lecture par l'infirmier (ordre conserve)", reponse(lireLesRaccourcis(CENTRE_A, infirmierA())));
        m.put("05 remplacement par 2 seul", reponse(remplacerLesRaccourcis(CENTRE_A, List.of(a2), adminA())));
        m.put("06 etat", etatDesRaccourcis());
        m.put("07 remplacement par la liste vide", reponse(remplacerLesRaccourcis(CENTRE_A, List.of(), adminA())));
        m.put("08 etat (aucun)", etatDesRaccourcis());
        m.put("09 corps sans liste (articleIds absent)", refus(doPut(raccourcisApi(CENTRE_A), "{}", adminA())));
        m.put("10 liste nulle", refus(remplacerLesRaccourcis(CENTRE_A, null, adminA())));
        m.put("11 diffusions (aucune)", diffusionsLues());
        arbre("nominal", m);
    }

    @Test
    void au_plus_douze_raccourcis_sans_doublon_d_articles_actifs_du_centre_et_un_refus_ne_change_rien() throws Exception {
        List<UUID> articles = new ArrayList<>();
        for (int i = 1; i <= 13; i++) articles.add(articleSe2(CENTRE_A, String.format("ART_%02d", i), true, "10"));
        UUID inactif = articleSe2(CENTRE_A, "ART_INACTIF", false, "10");
        UUID autreCentre = articleSe2(CENTRE_B, "ART_AUTRE_CENTRE", true, "10");
        UUID inconnu = idStable("article", "inconnu");
        aliasNommes.put(inconnu.toString(), "<ART_INCONNU>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 douze articles", refus(remplacerLesRaccourcis(CENTRE_A, articles.subList(0, 12), adminA())));
        m.put("02 etat (douze)", etatDesRaccourcis().size());
        m.put("03 treize articles", refus(remplacerLesRaccourcis(CENTRE_A, articles, adminA())));
        m.put("04 doublon", refus(remplacerLesRaccourcis(CENTRE_A, List.of(articles.get(0), articles.get(1), articles.get(0)), adminA())));
        m.put("05 element nul", refus(remplacerLesRaccourcis(CENTRE_A, Arrays.asList(articles.get(0), null), adminA())));
        m.put("06 article inactif", refus(remplacerLesRaccourcis(CENTRE_A, List.of(articles.get(0), inactif), adminA())));
        m.put("07 article inconnu", refus(remplacerLesRaccourcis(CENTRE_A, List.of(articles.get(0), inconnu), adminA())));
        m.put("08 article d'un autre centre", refus(remplacerLesRaccourcis(CENTRE_A, List.of(articles.get(0), autreCentre), adminA())));
        m.put("09 identifiant illisible", refus(doPut(raccourcisApi(CENTRE_A), "{\"articleIds\":[\"pas-un-uuid\"]}", adminA())));
        m.put("10 corps absent", refus(doPut(raccourcisApi(CENTRE_A), "", adminA())));
        m.put("11 corps illisible", refus(doPut(raccourcisApi(CENTRE_A), "pas du json", adminA())));
        m.put("12 etat inchange apres tous les refus (douze)", etatDesRaccourcis().size());
        m.put("13 un article valide puis un invalide : rien n'est ecrit", refus(remplacerLesRaccourcis(CENTRE_A, List.of(articles.get(5), inconnu),
                adminA())) + " ; " + etatDesRaccourcis().stream().map(l -> l.get("article")).toList());
        arbre("bornes_nouveau_code", m);
    }

    @Test
    void la_lecture_est_mise_en_cache_par_centre_et_purgee_a_chaque_remplacement_du_centre() throws Exception {
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID a2 = articleSe2(CENTRE_A, "ART_2", true, "10");
        UUID b1 = articleSe2(CENTRE_B, "ART_B1", true, "10");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 lecture A (met en cache la liste vide)", reponse(lireLesRaccourcis(CENTRE_A, infirmierA())));
        jdbc.update("INSERT INTO seance_raccourcis_articles (center_id, article_id, ordre) VALUES (?, ?, 0)", CENTRE_A, a1);
        m.put("02 lecture A apres une ecriture SQL directe (valeur en cache)", reponse(lireLesRaccourcis(CENTRE_A, infirmierA())));
        m.put("03 remplacement par l'administrateur A (purge)", reponse(remplacerLesRaccourcis(CENTRE_A, List.of(a2), adminA())));
        m.put("04 lecture A (valeur ecrite)", reponse(lireLesRaccourcis(CENTRE_A, infirmierA())));
        m.put("05 lecture B (met en cache la liste vide)", reponse(lireLesRaccourcis(CENTRE_B, infirmierB())));
        jdbc.update("INSERT INTO seance_raccourcis_articles (center_id, article_id, ordre) VALUES (?, ?, 0)", CENTRE_B, b1);
        m.put("06 remplacement refuse au centre A (aucune purge)", refus(remplacerLesRaccourcis(CENTRE_A, List.of(a1, a1), adminA())));
        m.put("07 lecture B toujours en cache", reponse(lireLesRaccourcis(CENTRE_B, infirmierB())));
        m.put("08 remplacement A ne purge pas B", reponse(remplacerLesRaccourcis(CENTRE_A, List.of(a1), adminA())));
        m.put("09 lecture B toujours en cache apres l'ecriture de A", reponse(lireLesRaccourcis(CENTRE_B, infirmierB())));
        viderLesCaches();
        m.put("10 lecture B apres purge complete (valeur de la base)", reponse(lireLesRaccourcis(CENTRE_B, infirmierB())));
        arbre("cache", m);
    }

    @Test
    void les_droits_par_profil_des_raccourcis() throws Exception {
        Map<String, Object> lecture = new LinkedHashMap<>();
        Map<String, Object> ecriture = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            nettoyerSe1();
            UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
            lecture.put(p.getKey(), statutEtCode(lireLesRaccourcis(CENTRE_A, p.getValue())));
            ecriture.put(p.getKey(), statutEtCode(remplacerLesRaccourcis(CENTRE_A, List.of(a1), p.getValue())) + " ; raccourcis="
                    + etatDesRaccourcis().size());
        }
        nettoyerSe1();
        lecture.put("anonyme", statutEtCode(appel("GET", raccourcisApi(CENTRE_A), null, null)));
        ecriture.put("anonyme", statutEtCode(appel("PUT", raccourcisApi(CENTRE_A), "{\"articleIds\":[]}", null)) + " ; raccourcis="
                + etatDesRaccourcis().size());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lecture (GET)", lecture);
        m.put("ecriture (PUT)", ecriture);
        arbre("profils", m);
    }

    @Test
    void le_cloisonnement_par_centre_des_raccourcis_et_le_centre_de_la_session_par_defaut() throws Exception {
        UUID a1 = articleSe2(CENTRE_A, "ART_A", true, "10");
        UUID b1 = articleSe2(CENTRE_B, "ART_B", true, "10");
        remplacerLesRaccourcis(CENTRE_A, List.of(a1), adminA());
        remplacerLesRaccourcis(CENTRE_B, List.of(b1), adminB());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 etat de depart", etatDesRaccourcis());
        m.put("02 infirmier A lit le centre B", refus(lireLesRaccourcis(CENTRE_B, infirmierA())));
        m.put("03 infirmier B lit le centre A", refus(lireLesRaccourcis(CENTRE_A, infirmierB())));
        m.put("04 administrateur A ecrit au centre B", refus(remplacerLesRaccourcis(CENTRE_B, List.of(b1), adminA())));
        m.put("05 administrateur B ecrit au centre A", refus(remplacerLesRaccourcis(CENTRE_A, List.of(a1), adminB())));
        m.put("06 administrateur A ecrit au centre A avec un article du centre B", refus(remplacerLesRaccourcis(CENTRE_A, List.of(b1), adminA())));
        m.put("07 lecture sans centerId (centre de la session)", reponse(lireLesRaccourcis(null, infirmierA())));
        m.put("08 lecture sans centerId, session sans centre", refus(lireLesRaccourcis(null, as(null, "ADMIN"))));
        m.put("09 lecture illisible", refus(doGet(SEANCES_API + "/raccourcis-consommables?centerId=pas-un-uuid", infirmierA())));
        m.put("10 ecriture sans centerId (centre de la session)", reponse(remplacerLesRaccourcis(null, List.of(a1), adminA())));
        m.put("11 SUPERADMIN + ADMIN vise le centre B (transverse)", refus(remplacerLesRaccourcis(CENTRE_B, List.of(b1), as(CENTRE_A, "SUPERADMIN", "ADMIN"))));
        m.put("12 etat final", etatDesRaccourcis());
        arbre("cloisonnement_nouveau_code", m);
    }

    @Test
    void les_lectures_et_remplacements_de_raccourcis_sont_journalises_avec_la_route_et_le_statut() throws Exception {
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        monterLAudit();
        remplacerLesRaccourcis(CENTRE_A, List.of(a1), adminA());
        lireLesRaccourcis(CENTRE_A, infirmierA());
        remplacerLesRaccourcis(CENTRE_A, List.of(a1, a1), adminA());
        remplacerLesRaccourcis(CENTRE_A, List.of(a1), infirmierA());
        remplacerLesRaccourcis(CENTRE_B, List.of(a1), adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }
}
