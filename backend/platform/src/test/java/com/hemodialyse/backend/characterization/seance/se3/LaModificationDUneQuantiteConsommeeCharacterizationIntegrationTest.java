package com.hemodialyse.backend.characterization.seance.se3;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE3, lot 2 : modification de la quantité consommée d'un article ({@code PUT /api/v1/seances/{id}/consommables/{articleId}}) : la
 * quantité remplace la précédente, aucun contrôle de statut hors facturée, article inactif, droits, cloisonnement, audit.
 */
class LaModificationDUneQuantiteConsommeeCharacterizationIntegrationTest extends Se3CharacterizationSupport {

    @Override
    protected String dossier() {
        return "modification";
    }

    @Test
    void la_modification_remplace_la_quantite_consommee_et_une_quantite_identique_ne_change_rien_mais_notifie() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID a2 = articleSe2(CENTRE_A, "ART_2", true, "10");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        int mouvements = mouvementsDeStock();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 ajout de 2 de l'article 1 et de 4 de l'article 2", refus(ajouter(seance, a1, 2, infirmierA())) + " ; "
                + refus(ajouter(seance, a2, 4, infirmierA())));
        diffusions.clear();
        m.put("02 modification de l'article 1 a 5", reponse(modifier(seance, a1, 5, infirmierA())));
        m.put("03 consommations (5 et 4)", consommationsImputees());
        m.put("04 diffusions (mise a jour, puis saisie de modification)", diffusionsLues());
        diffusions.clear();
        m.put("05 modification a la meme quantite", reponse(modifier(seance, a1, 5, infirmierA())));
        m.put("06 diffusions de la modification sans effet (rediffusees)", diffusionsLues());
        m.put("07 modification a la baisse (1)", reponse(modifier(seance, a1, 1, infirmierA())));
        m.put("08 modification decimale (0.25)", reponse(modifier(seance, a1, "0.25", infirmierA())));
        m.put("09 consommations (0.25 et 4)", consommationsImputees());
        m.put("10 mouvements de stock inchanges", mouvementsDeStock() == mouvements);
        m.put("11 stock des articles inchange", stocksDesArticles());
        arbre("nominal", m);
    }

    @Test
    void la_modification_cree_la_ligne_d_un_article_jamais_declare_et_s_accepte_sur_n_importe_quel_statut_sauf_facturee() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT", "EN_COURS")) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), statut);
            diffusions.clear();
            m.put(statut, issueEtConsommations(modifier(seance, a1, 3, infirmierA())) + " ; diffusions=" + diffusions.size());
        }
        arbre("matrice_des_statuts", m);
    }

    @Test
    void la_quantite_doit_etre_strictement_positive_et_l_article_inactif_ne_peut_qu_etre_diminue() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID actif = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID inactif = articleSe2(CENTRE_A, "ART_INACTIF", true, "10");
        UUID autreCentre = articleSe2(CENTRE_B, "ART_AUTRE_CENTRE", true, "10");
        UUID inconnu = idStable("article", "inconnu");
        aliasNommes.put(inconnu.toString(), "<ART_INCONNU>");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        ajouter(seance, inactif, 4, infirmierA());
        jdbc.update("UPDATE articles SET active = false WHERE id = ?", inactif);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 quantite nulle", refus(modifier(seance, actif, 0, infirmierA())));
        m.put("02 quantite negative", refus(modifier(seance, actif, -1, infirmierA())));
        m.put("03 quantite absente", refus(modifier(seance, actif, null, infirmierA())));
        m.put("04 quantite textuelle", refus(doPut(consommableApi(seance, actif, null), "{\"centerId\":\"" + CENTRE_A + "\",\"quantite\":\"beaucoup\"}",
                infirmierA())));
        m.put("05 augmentation d'un article devenu inactif (5 > 4)", refus(modifier(seance, inactif, 5, infirmierA())));
        m.put("06 diminution d'un article devenu inactif (2 < 4)", refus(modifier(seance, inactif, 2, infirmierA())));
        m.put("07 meme quantite d'un article inactif", refus(modifier(seance, inactif, 2, infirmierA())));
        m.put("08 article inconnu", refus(modifier(seance, inconnu, 1, infirmierA())));
        m.put("09 article d'un autre centre", refus(modifier(seance, autreCentre, 1, infirmierA())));
        m.put("10 seance inconnue", refus(modifier(idStable("seance", "inconnue"), actif, 1, infirmierA())));
        m.put("11 consommations", consommationsImputees());
        m.put("12 centre absent du corps", refus(doPut(consommableApi(seance, actif, null), corps("quantite", 1), infirmierA())));
        m.put("13 corps absent", refus(doPut(consommableApi(seance, actif, null), "", infirmierA())));
        m.put("14 corps illisible", refus(doPut(consommableApi(seance, actif, null), "pas du json", infirmierA())));
        m.put("15 identifiant d'article mal forme", refus(doPut(consommablesApi(seance) + "/pas-un-uuid", modification(1), infirmierA())));
        m.put("16 sans userId dans le corps", refus(doPut(consommableApi(seance, actif, null), corps("centerId", CENTRE_A, "quantite", 1), infirmierA())));
        m.put("17 consommations finales", consommationsImputees());
        arbre("quantites_et_articles", m);
    }

    @Test
    void les_droits_par_profil_de_la_modification() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
            diffusions.clear();
            profils.put(p.getKey(), statutEtCode(modifier(seance, a1, 1, p.getValue())) + " ; consommations=" + consommationsImputees().size()
                    + " ; diffusions=" + diffusions.size());
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        profils.put("anonyme", statutEtCode(appel("PUT", consommableApi(seance, a1, null), modification(1), null)) + " ; consommations="
                + consommationsImputees().size());
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_de_la_modification() throws Exception {
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID articleA = articleSe2(CENTRE_A, "ART_A", true, "10");
        UUID articleB = articleSe2(CENTRE_B, "ART_B", true, "10");
        UUID seanceA = seanceValidee(CENTRE_A, patientA, "SEANCE_A", jour(0), "VALIDEE");
        UUID seanceB = seanceValidee(CENTRE_B, patientB, "SEANCE_B", jour(0), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 infirmier A vise le centre B", refus(doPut(consommableApi(seanceB, articleB, null), corps("centerId", CENTRE_B, "quantite", 1),
                infirmierA())));
        m.put("02 infirmier B vise le centre A", refus(doPut(consommableApi(seanceA, articleA, null), corps("centerId", CENTRE_A, "quantite", 1),
                infirmierB())));
        m.put("03 infirmier B, centre B, seance du centre A", refus(doPut(consommableApi(seanceA, articleB, null), corps("centerId", CENTRE_B,
                "quantite", 1), infirmierB())));
        m.put("04 infirmier A, centre A, seance du centre B", refus(doPut(consommableApi(seanceB, articleA, null), corps("centerId", CENTRE_A,
                "quantite", 1), infirmierA())));
        m.put("05 infirmier A, centre A, article du centre B", refus(modifier(seanceA, articleB, 1, infirmierA())));
        m.put("06 consommations (aucune)", consommationsImputees());
        m.put("07 SUPERADMIN + ADMIN vise le centre B (transverse)", refus(doPut(consommableApi(seanceB, articleB, null), corps("centerId", CENTRE_B,
                "quantite", 1), as(CENTRE_A, "SUPERADMIN", "ADMIN"))));
        m.put("08 administrateur A", refus(modifier(seanceA, articleA, 1, adminA())));
        m.put("09 consommations", consommationsImputees());
        arbre("cloisonnement", m);
    }

    @Test
    void les_modifications_et_leurs_refus_sont_journalises_avec_la_route_et_le_statut() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID validee = seanceValidee(CENTRE_A, patient, "SEANCE_VALIDEE", jour(0), "VALIDEE");
        UUID facturee = seanceValidee(CENTRE_A, patient, "SEANCE_FACTUREE", jour(1), "FACTUREE");
        monterLAudit();
        modifier(validee, a1, 2, infirmierA());
        modifier(validee, a1, 0, infirmierA());
        modifier(facturee, a1, 1, infirmierA());
        modifier(validee, a1, 1, secretaireA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }
}
