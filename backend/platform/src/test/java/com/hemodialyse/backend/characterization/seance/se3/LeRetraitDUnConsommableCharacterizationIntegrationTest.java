package com.hemodialyse.backend.characterization.seance.se3;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE3, lot 3 : retrait d'un consommable ({@code DELETE /api/v1/seances/{id}/consommables/{articleId}?centerId=}) : la quantité est fixée
 * à zéro, aucun mouvement de stock (le commentaire du contrôleur parle d'une restitution), matrice des statuts, droits, cloisonnement,
 * audit.
 */
class LeRetraitDUnConsommableCharacterizationIntegrationTest extends Se3CharacterizationSupport {

    @Override
    protected String dossier() {
        return "retrait";
    }

    @Test
    void le_retrait_met_la_quantite_a_zero_supprime_la_consommation_vide_et_ne_restitue_aucun_stock() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID a2 = articleSe2(CENTRE_A, "ART_2", true, "10");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        ajouter(seance, a1, 2, infirmierA());
        ajouter(seance, a2, 3, infirmierA());
        int mouvements = mouvementsDeStock();
        diffusions.clear();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 consommations avant", consommationsImputees());
        m.put("02 retrait de l'article 1", reponse(retirer(seance, a1, infirmierA())));
        m.put("03 consommations (article 2 seul)", consommationsImputees());
        m.put("04 diffusions (mise a jour, puis saisie de retrait)", diffusionsLues());
        diffusions.clear();
        m.put("05 retrait de l'article 1 une seconde fois (sans effet, rediffuse)", reponse(retirer(seance, a1, infirmierA())));
        m.put("06 diffusions du retrait sans effet", diffusionsLues());
        m.put("07 retrait d'un article jamais declare", reponse(retirer(seance, articleSe2(CENTRE_A, "ART_3", true, "10"), infirmierA())));
        m.put("08 retrait de la derniere ligne", reponse(retirer(seance, a2, infirmierA())));
        m.put("09 consommations (la consommation de la seance disparait)", lignes("SELECT COUNT(*) AS consommations FROM consommations_de_seance "
                + "WHERE center_id = ?", CENTRE_A));
        m.put("10 mouvements de stock inchanges (aucune restitution)", mouvementsDeStock() == mouvements);
        m.put("11 stock des articles inchange", stocksDesArticles());
        m.put("12 ajout apres le retrait (nouvelle consommation)", refus(ajouter(seance, a1, 1, infirmierA())));
        m.put("13 consommations", consommationsImputees());
        arbre("nominal", m);
    }

    @Test
    void le_retrait_n_est_refuse_que_pour_une_seance_facturee_la_matrice_des_statuts() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT", "EN_COURS")) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
            ajouter(seance, a1, 2, infirmierA());
            jdbc.update("UPDATE seances SET statut = ? WHERE id = ?", statut, seance);
            diffusions.clear();
            m.put(statut, issueEtConsommations(retirer(seance, a1, infirmierA())) + " ; diffusions=" + diffusions.size());
        }
        arbre("matrice_des_statuts", m);
    }

    @Test
    void les_parametres_du_retrait_sont_controles_la_seance_inconnue_et_les_formes_illisibles() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        UUID inconnu = idStable("article", "inconnu");
        aliasNommes.put(inconnu.toString(), "<ART_INCONNU>");
        ajouter(seance, a1, 2, infirmierA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 seance inconnue", refus(retirer(idStable("seance", "inconnue"), a1, infirmierA())));
        m.put("02 article inconnu (retrait sans effet)", refus(retirer(seance, inconnu, infirmierA())));
        m.put("03 centre absent", refus(doDelete(consommableApi(seance, a1, null), infirmierA())));
        m.put("04 centre illisible", refus(doDelete(consommablesApi(seance) + "/" + a1 + "?centerId=pas-un-uuid", infirmierA())));
        m.put("05 article mal forme", refus(doDelete(consommablesApi(seance) + "/pas-un-uuid?centerId=" + CENTRE_A, infirmierA())));
        m.put("06 consommations inchangees", consommationsImputees());
        m.put("07 utilisateur du parametre ignore par l'observation", refus(doDelete(consommableApi(seance, a1, CENTRE_A) + "&userId=autre-compte",
                infirmierA())));
        m.put("08 consommations", consommationsImputees());
        arbre("parametres", m);
    }

    @Test
    void les_droits_par_profil_du_retrait() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
            ajouter(seance, a1, 2, infirmierA());
            diffusions.clear();
            profils.put(p.getKey(), statutEtCode(retirer(seance, a1, p.getValue())) + " ; consommations=" + consommationsImputees().size()
                    + " ; diffusions=" + diffusions.size());
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        ajouter(seance, a1, 2, infirmierA());
        profils.put("anonyme", statutEtCode(appel("DELETE", consommableApi(seance, a1, CENTRE_A), null, null)) + " ; consommations="
                + consommationsImputees().size());
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_du_retrait() throws Exception {
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID articleA = articleSe2(CENTRE_A, "ART_A", true, "10");
        UUID articleB = articleSe2(CENTRE_B, "ART_B", true, "10");
        UUID seanceA = seanceValidee(CENTRE_A, patientA, "SEANCE_A", jour(0), "VALIDEE");
        UUID seanceB = seanceValidee(CENTRE_B, patientB, "SEANCE_B", jour(0), "VALIDEE");
        ajouter(seanceA, articleA, 2, infirmierA());
        doPost(consommablesApi(seanceB), corps("centerId", CENTRE_B, "articleId", articleB, "quantite", 2), infirmierB());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("00 consommations de depart", consommationsImputees());
        m.put("01 infirmier A vise le centre B", refus(doDelete(consommableApi(seanceB, articleB, CENTRE_B), infirmierA())));
        m.put("02 infirmier B vise le centre A", refus(doDelete(consommableApi(seanceA, articleA, CENTRE_A), infirmierB())));
        m.put("03 infirmier B, centre B, seance du centre A", refus(doDelete(consommableApi(seanceA, articleA, CENTRE_B), infirmierB())));
        m.put("04 infirmier A, centre A, seance du centre B", refus(doDelete(consommableApi(seanceB, articleB, CENTRE_A), infirmierA())));
        m.put("05 consommations inchangees", consommationsImputees());
        m.put("06 SUPERADMIN + ADMIN vise le centre B (transverse)", refus(doDelete(consommableApi(seanceB, articleB, CENTRE_B),
                as(CENTRE_A, "SUPERADMIN", "ADMIN"))));
        m.put("07 administrateur A retire au centre A", refus(doDelete(consommableApi(seanceA, articleA, CENTRE_A), adminA())));
        m.put("08 consommations", consommationsImputees());
        arbre("cloisonnement", m);
    }

    @Test
    void les_retraits_et_leurs_refus_sont_journalises_avec_la_route_et_le_statut() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID validee = seanceValidee(CENTRE_A, patient, "SEANCE_VALIDEE", jour(0), "VALIDEE");
        ajouter(validee, a1, 2, infirmierA());
        monterLAudit();
        retirer(validee, a1, infirmierA());
        retirer(validee, a1, secretaireA());
        retirer(idStable("seance", "inconnue"), a1, infirmierA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }
}
