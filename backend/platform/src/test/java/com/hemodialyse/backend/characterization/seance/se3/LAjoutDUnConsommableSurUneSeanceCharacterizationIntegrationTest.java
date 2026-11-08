package com.hemodialyse.backend.characterization.seance.se3;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE3, lot 1 : ajout d'un consommable à une séance déjà validée ({@code POST /api/v1/seances/{id}/consommables}) : imputation par le Stock
 * (aucun mouvement de stock), cumul, matrice des statuts, quantités, articles, sortie pour séances, droits par profil, cloisonnement par
 * centre, lignes illisibles, audit.
 */
class LAjoutDUnConsommableSurUneSeanceCharacterizationIntegrationTest extends Se3CharacterizationSupport {

    @Override
    protected String dossier() {
        return "ajout";
    }

    @Test
    void l_infirmier_ajoute_un_consommable_a_une_seance_validee_le_stock_l_impute_sans_mouvement_et_les_ajouts_se_cumulent() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID a2 = articleSe2(CENTRE_A, "ART_2", true, "20");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        int mouvements = mouvementsDeStock();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 ajout de 2 de l'article 1", reponse(ajouter(seance, a1, 2, infirmierA())));
        m.put("02 consommations imputees (aucune piece : alerte AUCUNE_PIECE)", consommationsImputees());
        m.put("03 mouvements de stock inchanges", mouvementsDeStock() == mouvements);
        m.put("04 stock des articles inchange", stocksDesArticles());
        m.put("05 diffusions (mise a jour de la seance, puis saisie de l'infirmier)", diffusionsLues());
        diffusions.clear();
        m.put("06 second ajout de 3 du meme article (cumul)", reponse(ajouter(seance, a1, 3, infirmierA())));
        m.put("07 ajout de 1.5 de l'article 2 (quantite decimale)", reponse(ajouter(seance, a2, "1.5", infirmierA())));
        m.put("08 consommations apres les trois ajouts", consommationsImputees());
        m.put("09 etat de la seance inchange", etatSe2());
        arbre("nominal", m);
    }

    @Test
    void l_ajout_n_est_accepte_que_sur_une_seance_validee_ou_signee_la_matrice_des_statuts() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT", "EN_COURS")) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), statut);
            diffusions.clear();
            m.put(statut, issueEtConsommations(ajouter(seance, a1, 2, infirmierA())) + " ; diffusions=" + diffusions.size());
        }
        arbre("matrice_des_statuts", m);
    }

    @Test
    void la_quantite_doit_etre_strictement_positive_et_les_formes_illisibles_sont_refusees() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 quantite nulle", refus(ajouter(seance, a1, 0, infirmierA())));
        m.put("02 quantite negative", refus(ajouter(seance, a1, -1, infirmierA())));
        m.put("03 quantite absente", refus(ajouter(seance, a1, null, infirmierA())));
        m.put("04 quantite textuelle", refus(doPost(consommablesApi(seance),
                "{\"centerId\":\"" + CENTRE_A + "\",\"articleId\":\"" + a1 + "\",\"quantite\":\"beaucoup\"}", infirmierA())));
        m.put("05 article absent", refus(doPost(consommablesApi(seance), corps("centerId", CENTRE_A, "quantite", 1), infirmierA())));
        m.put("06 centre absent", refus(doPost(consommablesApi(seance), corps("articleId", a1, "quantite", 1), infirmierA())));
        m.put("07 article illisible", refus(doPost(consommablesApi(seance),
                "{\"centerId\":\"" + CENTRE_A + "\",\"articleId\":\"pas-un-uuid\",\"quantite\":1}", infirmierA())));
        m.put("08 corps vide", refus(doPost(consommablesApi(seance), "{}", infirmierA())));
        m.put("09 corps absent", refus(doPost(consommablesApi(seance), "", infirmierA())));
        m.put("10 corps illisible", refus(doPost(consommablesApi(seance), "pas du json", infirmierA())));
        m.put("11 propriete inconnue ignoree", refus(doPost(consommablesApi(seance),
                "{\"centerId\":\"" + CENTRE_A + "\",\"articleId\":\"" + a1 + "\",\"quantite\":1,\"inconnue\":1}", infirmierA())));
        m.put("12 quantite tres precise (1.23456789)", refus(ajouter(seance, a1, "1.23456789", infirmierA())));
        m.put("13 quantite enorme (1e12)", refus(ajouter(seance, a1, "1000000000000", infirmierA())));
        m.put("14 consommations apres les refus et les deux derniers ajouts", consommationsImputees());
        m.put("15 stock des articles inchange", stocksDesArticles());
        arbre("quantites_et_formes", m);
    }

    @Test
    void l_article_doit_etre_actif_et_du_centre_la_quantite_est_controlee_avant_la_seance_et_la_seance_avant_l_article() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID actif = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID inactif = articleSe2(CENTRE_A, "ART_INACTIF", false, "10");
        UUID autreCentre = articleSe2(CENTRE_B, "ART_AUTRE_CENTRE", true, "10");
        UUID inconnu = idStable("article", "inconnu");
        aliasNommes.put(inconnu.toString(), "<ART_INCONNU>");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        UUID creee = seanceValidee(CENTRE_A, patient, "SEANCE_CREEE", jour(1), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 article actif", refus(ajouter(seance, actif, 1, infirmierA())));
        m.put("02 article inactif", refus(ajouter(seance, inactif, 1, infirmierA())));
        m.put("03 article inconnu", refus(ajouter(seance, inconnu, 1, infirmierA())));
        m.put("04 article d'un autre centre", refus(ajouter(seance, autreCentre, 1, infirmierA())));
        m.put("05 seance creee et article inconnu (le statut est controle avant l'article)", refus(ajouter(creee, inconnu, 1, infirmierA())));
        m.put("06 seance inconnue et quantite nulle (la validation du corps precede la recherche de la seance)", refus(ajouter(idStable("seance", "inconnue"),
                actif, 0, infirmierA())));
        m.put("07 seance inconnue", refus(ajouter(idStable("seance", "inconnue"), actif, 1, infirmierA())));
        m.put("08 consommations (une seule : article actif)", consommationsImputees());
        arbre("articles", m);
    }

    @Test
    void l_ajout_s_impute_sur_la_sortie_pour_seances_du_jour_avec_alerte_de_depassement_ou_hors_piece() throws Exception {
        LocalDate demain = jour(1);
        UUID patient1 = patientCouvert(CENTRE_A, "PAT_A");
        UUID patient2 = patientCouvert(CENTRE_A, "PAT_B");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID a2 = articleSe2(CENTRE_A, "ART_2", true, "10");
        UUID lot = lotSe2(CENTRE_A, a1, "LOT_1", "10");
        Resp piece = doPost("/api/v1/stock/sorties-pour-seances", corps("jour", demain, "lignes", List.of(ligne(a1, 8))), adminA());
        UUID s1 = seanceValidee(CENTRE_A, patient1, "SEANCE_1", demain, "VALIDEE");
        UUID s2 = seanceValidee(CENTRE_A, patient2, "SEANCE_2", demain, "VALIDEE");
        int mouvements = mouvementsDeStock();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 piece du lendemain", piece.status());
        m.put("02 ajout de 5 sur la piece de 8", refus(ajouter(s1, a1, 5, infirmierA())));
        m.put("03 ajout de 5 par un autre patient (depassement, 3 restants)", refus(ajouter(s2, a1, 5, infirmierA())));
        m.put("04 ajout d'un article hors piece", refus(ajouter(s2, a2, 1, infirmierA())));
        m.put("05 consommations imputees (par lot, alertes)", consommationsImputees());
        m.put("06 mouvements de stock inchanges depuis la piece", mouvementsDeStock() == mouvements);
        m.put("07 lot inchange (la sortie a eu lieu a la piece)", lignes("SELECT numero_lot, quantite_restante FROM lots WHERE id = ?", lot));
        arbre("sortie_pour_seances", m);
    }

    @Test
    void l_ajout_est_refuse_quand_la_sortie_pour_seances_du_jour_est_rapprochee_mais_pas_pendant_le_constat_du_rapprochement() throws Exception {
        LocalDate demain = jour(1);
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID a2 = articleSe2(CENTRE_A, "ART_2", true, "10");
        lotSe2(CENTRE_A, a1, "LOT_1", "10");
        Resp piece = doPost("/api/v1/stock/sorties-pour-seances", corps("jour", demain, "lignes", List.of(ligne(a1, 8))), adminA());
        UUID pieceId = UUID.fromString(idDe(piece));
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", demain, "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 piece du lendemain", piece.status());
        m.put("02 ajout de 8 (toute la piece)", refus(ajouter(seance, a1, 8, infirmierA())));
        Resp constate = doPost("/api/v1/stock/sorties-pour-seances/" + pieceId + "/rapprochement", "{}", adminA());
        m.put("03 rapprochement constate (statut)", refus(constate));
        m.put("04 ajout pendant le constat du rapprochement (non refuse)", refus(ajouter(seance, a1, 1, infirmierA())));
        m.put("05 validation du rapprochement avec un ecart", refus(doPost("/api/v1/stock/sorties-pour-seances/" + pieceId + "/valider", "{}",
                adminA())));
        m.put("06 retour a 8 par la modification (ecart nul)", refus(modifier(seance, a1, 8, infirmierA())));
        Resp validee = doPost("/api/v1/stock/sorties-pour-seances/" + pieceId + "/valider", "{}", adminA());
        m.put("07 validation du rapprochement sans ecart", refus(validee));
        m.put("08 ajout sur la piece rapprochee", refus(ajouter(seance, a1, 1, infirmierA())));
        m.put("09 ajout d'un article hors piece sur la piece rapprochee", refus(ajouter(seance, a2, 1, infirmierA())));
        m.put("10 modification sur la piece rapprochee", refus(modifier(seance, a1, 2, infirmierA())));
        m.put("11 retrait sur la piece rapprochee", refus(retirer(seance, a1, infirmierA())));
        m.put("12 consommations apres le rapprochement (inchangees)", consommationsImputees());
        arbre("piece_rapprochee", m);
    }

    @Test
    void les_droits_par_profil_de_l_ajout() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
            diffusions.clear();
            profils.put(p.getKey(), statutEtCode(ajouter(seance, a1, 1, p.getValue())) + " ; consommations=" + consommationsImputees().size()
                    + " ; diffusions=" + diffusions.size());
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        profils.put("anonyme", statutEtCode(appel("POST", consommablesApi(seance), ajout(a1, 1), null)) + " ; consommations="
                + consommationsImputees().size());
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_l_auteur_est_celui_de_la_session_et_la_seance_d_un_autre_centre_est_introuvable() throws Exception {
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID articleA = articleSe2(CENTRE_A, "ART_A", true, "10");
        UUID articleB = articleSe2(CENTRE_B, "ART_B", true, "10");
        UUID seanceA = seanceValidee(CENTRE_A, patientA, "SEANCE_A", jour(0), "VALIDEE");
        UUID seanceB = seanceValidee(CENTRE_B, patientB, "SEANCE_B", jour(0), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 infirmier A vise le centre B", refus(doPost(consommablesApi(seanceB), corps("centerId", CENTRE_B, "articleId", articleB, "quantite", 1),
                infirmierA())));
        m.put("02 infirmier B vise le centre A", refus(doPost(consommablesApi(seanceA), corps("centerId", CENTRE_A, "articleId", articleA, "quantite", 1),
                infirmierB())));
        m.put("03 infirmier B, centre B, seance du centre A", refus(doPost(consommablesApi(seanceA), corps("centerId", CENTRE_B, "articleId", articleB,
                "quantite", 1), infirmierB())));
        m.put("04 infirmier A, centre A, seance du centre B", refus(doPost(consommablesApi(seanceB), corps("centerId", CENTRE_A, "articleId", articleA,
                "quantite", 1), infirmierA())));
        m.put("05 infirmier A, centre A, article du centre B", refus(ajouter(seanceA, articleB, 1, infirmierA())));
        m.put("06 administrateur sans centre", refus(ajouter(seanceA, articleA, 1, as(null, "ADMIN"))));
        m.put("07 SUPERADMIN + ADMIN vise le centre B (transverse)", refus(doPost(consommablesApi(seanceB), corps("centerId", CENTRE_B, "articleId",
                articleB, "quantite", 1), as(CENTRE_A, "SUPERADMIN", "ADMIN"))));
        m.put("08 consommations (aucune)", consommationsImputees());
        m.put("09 l'administrateur A ajoute au centre A", refus(ajouter(seanceA, articleA, 1, adminA())));
        m.put("10 consommations", consommationsImputees());
        arbre("cloisonnement", m);
    }

    @Test
    void l_ajout_sur_une_seance_dont_le_patient_a_disparu_est_ecrit_puis_la_notification_echoue() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        jdbc.update("DELETE FROM patients WHERE id = ?", patient);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 ajout", refus(ajouter(seance, a1, 2, infirmierA())));
        m.put("02 consommations (ecrites malgre l'echec de la reponse ?)", consommationsImputees());
        m.put("03 diffusions", diffusionsLues());
        arbre("patient_disparu", m);
    }

    @Test
    void les_ajouts_et_leurs_refus_sont_journalises_avec_la_route_et_le_statut() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID validee = seanceValidee(CENTRE_A, patient, "SEANCE_VALIDEE", jour(0), "VALIDEE");
        UUID creee = seanceValidee(CENTRE_A, patient, "SEANCE_CREEE", jour(1), "CREE");
        monterLAudit();
        ajouter(validee, a1, 1, infirmierA());
        ajouter(validee, a1, 0, infirmierA());
        ajouter(creee, a1, 1, infirmierA());
        ajouter(validee, a1, 1, secretaireA());
        doPost(consommablesApi(validee), corps("centerId", CENTRE_B, "articleId", a1, "quantite", 1), infirmierA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }
}
