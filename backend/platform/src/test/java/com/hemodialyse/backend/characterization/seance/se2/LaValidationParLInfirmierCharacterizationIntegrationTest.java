package com.hemodialyse.backend.characterization.seance.se2;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE2, lot 1 : validation d'une séance par l'infirmier ou l'administrateur ({@code POST /api/v1/seances/{id}/valider}) : transition
 * {@code CREE -> VALIDEE}, matrice des statuts, verrou des séances d'un jour passé (déverrouillage de l'administrateur), compte
 * enregistré, droits par profil, cloisonnement par centre, corps invalides, consommables imputés par le Stock (et retour arrière),
 * place mémorisée, diffusions, audit, écritures simultanées, lignes illisibles. Le jour de comparaison est celui de la JVM
 * ({@code LocalDate.now()}), nommé {@code <J>} comme le jour du centre (KB-SEA-01).
 */
class LaValidationParLInfirmierCharacterizationIntegrationTest extends Se2CharacterizationSupport {

    @Override
    protected String dossier() {
        return "validation";
    }

    @Test
    void l_infirmier_valide_la_seance_du_jour_et_une_seconde_validation_ne_change_rien_mais_rediffuse() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        LocalDate jour = jour(0);
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour, "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 validation par l'INFIRMIER", reponse(valider(seance, infirmierA())));
        m.put("02 etat (VALIDEE, signee par le compte du corps, validee ce jour)", etatSe2());
        m.put("03 diffusions (validation puis saisie pour le medecin)", diffusionsLues());
        String horodatage = validatedAtBrut(seance);
        diffusions.clear();
        m.put("04 seconde validation (retour anticipe du domaine)", reponse(valider(seance, infirmierA())));
        m.put("05 horodatage de validation inchange", horodatage.equals(validatedAtBrut(seance)));
        m.put("06 diffusions de la seconde validation (rediffusees malgre l'absence de changement)", diffusionsLues());
        m.put("07 etat", etatSe2());
        arbre("nominal", m);
    }

    @Test
    void le_compte_enregistre_comme_signataire_est_celui_du_corps_de_la_requete_et_non_celui_de_la_session() throws Exception {
        LocalDate jour = jour(0);
        Map<String, Object> m = new LinkedHashMap<>();
        String[] cas = {"corps sans userId", "userId vide", "userId d'un autre compte", "userId de 100 caracteres", "userId de 101 caracteres"};
        Object[] valeurs = {null, "", "autre-compte", "u".repeat(100), "u".repeat(101)};
        for (int i = 0; i < cas.length; i++) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour, "CREE");
            Resp r = doPost(validerApi(seance), corps("centerId", CENTRE_A, "userId", valeurs[i]), infirmierA());
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("reponse", refus(r));
            c.put("signataire en base", lignes("SELECT statut, signed_infirmier_by FROM seances WHERE id = ?", seance));
            m.put(String.format("%02d %s", i + 1, cas[i]), c);
        }
        arbre("compte_signataire", m);
    }

    @Test
    void la_matrice_des_statuts_cree_est_valide_les_statuts_deja_valides_sont_inchanges_facturee_et_les_autres_sont_refusees() throws Exception {
        LocalDate jour = jour(0);
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT", "EN_COURS")) {
            Map<String, Object> cas = new LinkedHashMap<>();
            Map<String, RequestPostProcessor> acteurs = new LinkedHashMap<>();
            acteurs.put("infirmier", infirmierA());
            acteurs.put("administrateur", adminA());
            for (Map.Entry<String, RequestPostProcessor> qui : acteurs.entrySet()) {
                nettoyerSe1();
                UUID patient = patientCouvert(CENTRE_A, "PAT_A");
                UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour, statut);
                diffusions.clear();
                Resp r = valider(seance, qui.getValue());
                cas.put(qui.getKey(), issue(r, seance) + " ; diffusions=" + diffusions.size());
            }
            m.put(statut, cas);
        }
        arbre("matrice_des_statuts", m);
    }

    @Test
    void une_seance_d_un_jour_passe_ne_se_valide_par_l_infirmier_qu_apres_le_deverrouillage_l_administrateur_et_le_futur_sont_libres() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        int[] decalages = {-400, -30, -8, -7, -1, 0, 1, 30};
        for (int decalage : decalages) {
            LocalDate jour = jour(decalage);
            Map<String, Object> cas = new LinkedHashMap<>();
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour, "CREE");
            cas.put("infirmier sans deverrouillage", issue(valider(seance, infirmierA()), seance));
            nettoyerSe1();
            patient = patientCouvert(CENTRE_A, "PAT_A");
            seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour, "CREE");
            cas.put("administrateur sans deverrouillage", issue(valider(seance, adminA()), seance));
            nettoyerSe1();
            patient = patientCouvert(CENTRE_A, "PAT_A");
            seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour, "CREE");
            deverrouillerEnBase(seance);
            cas.put("infirmier apres deverrouillage", issue(valider(seance, infirmierA()), seance));
            m.put(String.format("%+04d jours", decalage), cas);
        }
        arbre("jours_passes", m);
    }

    @Test
    void le_verrou_du_jour_passe_est_evalue_avant_le_statut_et_avant_l_idempotence() throws Exception {
        LocalDate passe = jour(-3);
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("VALIDEE", "SIGNEE", "FACTUREE", "ABSENT")) {
            Map<String, Object> cas = new LinkedHashMap<>();
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", passe, statut);
            cas.put("infirmier", issue(valider(seance, infirmierA()), seance));
            nettoyerSe1();
            patient = patientCouvert(CENTRE_A, "PAT_A");
            seance = seanceValidee(CENTRE_A, patient, "SEANCE", passe, statut);
            cas.put("administrateur", issue(valider(seance, adminA()), seance));
            m.put(statut + " d'un jour passe", cas);
        }
        arbre("verrou_avant_statut", m);
    }

    @Test
    void les_droits_par_profil_de_la_validation() throws Exception {
        LocalDate jour = jour(0);
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour, "CREE");
            profils.put(p.getKey(), statutEtCode(valider(seance, p.getValue())) + " ; " + lignes("SELECT statut FROM seances WHERE id = ?", seance)
                    + " ; diffusions=" + diffusions.size());
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour, "CREE");
        profils.put("anonyme", statutEtCode(appel("POST", validerApi(seance), corps("centerId", CENTRE_A, "userId", "inf-01"), null))
                + " ; " + lignes("SELECT statut FROM seances WHERE id = ?", seance));
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_la_garde_de_la_seance_et_du_patient() throws Exception {
        LocalDate jour = jour(0);
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID seanceA = seanceEnBase(CENTRE_A, patientA, "SEANCE_A", jour, "CREE");
        UUID seanceB = seanceEnBase(CENTRE_B, patientB, "SEANCE_B", jour, "CREE");
        UUID inconnue = idStable("seance", "inconnue");
        aliasNommes.put(inconnue.toString(), "<SEANCE_INCONNUE>");
        String avant = empreinteDesSeances();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 session A vise le centre B pour la seance B", refus(doPost(validerApi(seanceB), corps("centerId", CENTRE_B, "userId", "u"), infirmierA())));
        m.put("02 session B vise le centre A pour la seance A", refus(doPost(validerApi(seanceA), corps("centerId", CENTRE_A, "userId", "u"), infirmierB())));
        m.put("03 session B et centre B, seance du centre A", refus(doPost(validerApi(seanceA), corps("centerId", CENTRE_B, "userId", "u"), infirmierB())));
        m.put("04 session A et centre A, seance du centre B", refus(doPost(validerApi(seanceB), corps("centerId", CENTRE_A, "userId", "u"), infirmierA())));
        m.put("05 session sans centre", refus(doPost(validerApi(seanceA), corps("centerId", CENTRE_A, "userId", "u"), as(null, "INFIRMIER"))));
        m.put("06 seance inconnue", refus(doPost(validerApi(inconnue), corps("centerId", CENTRE_A, "userId", "u"), infirmierA())));
        m.put("07 aucune ecriture jusqu'ici", empreinteDesSeances().equals(avant));
        m.put("08 SUPERADMIN + ADMIN de la session A vise le centre B (transverse)", refus(doPost(validerApi(seanceB),
                corps("centerId", CENTRE_B, "userId", "u"), as(CENTRE_A, "SUPERADMIN", "ADMIN"))));
        m.put("09 etat apres le superadmin", etatSe2());
        arbre("cloisonnement", m);
    }

    @Test
    void les_corps_incomplets_ou_illisibles_et_les_identifiants_mal_formes() throws Exception {
        LocalDate jour = jour(0);
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour, "CREE");
        String avant = empreinteDesSeances();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 corps sans centerId", refus(doPost(validerApi(seance), corps("userId", "u"), infirmierA())));
        m.put("02 aucune ecriture", empreinteDesSeances().equals(avant));
        m.put("03 centerId null", refus(doPost(validerApi(seance), "{\"centerId\":null,\"userId\":\"u\"}", infirmierA())));
        m.put("04 centerId illisible", refus(doPost(validerApi(seance), "{\"centerId\":\"pas-un-uuid\",\"userId\":\"u\"}", infirmierA())));
        m.put("05 corps vide {}", refus(doPost(validerApi(seance), "{}", infirmierA())));
        m.put("06 corps illisible", refus(doPost(validerApi(seance), "{pas du json", infirmierA())));
        m.put("07 corps absent", refus(doPost(validerApi(seance), "", infirmierA())));
        m.put("08 corps null", refus(doPost(validerApi(seance), "null", infirmierA())));
        m.put("09 corps tableau", refus(doPost(validerApi(seance), "[]", infirmierA())));
        m.put("10 identifiant de seance mal forme", refus(doPost(SEANCES_API + "/pas-un-uuid/valider", corps("centerId", CENTRE_A, "userId", "u"),
                infirmierA())));
        m.put("11 propriete inconnue ignoree", refus(doPost(validerApi(seance), "{\"centerId\":\"" + CENTRE_A + "\",\"userId\":\"u\",\"inconnue\":1}",
                infirmierA())));
        m.put("12 etat final", etatSe2());
        arbre("corps_et_formes", m);
    }

    @Test
    void les_consommables_de_la_validation_sont_imputes_par_le_stock_sans_mouvement_de_stock_et_une_validation_repetee_les_cumule() throws Exception {
        LocalDate jour = jour(0);
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID a2 = articleSe2(CENTRE_A, "ART_2", true, "20");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour, "CREE");
        int mouvements = mouvementsDeStock();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 validation avec deux articles dont un repete", reponse(doPost(validerApi(seance),
                validationAvecConsommations(ligne(a1, 2), ligne(a2, 3), ligne(a1, 1)), infirmierA())));
        m.put("02 etat", etatSe2());
        m.put("03 consommations imputees (aucune piece : alerte AUCUNE_PIECE)", consommationsImputees());
        m.put("04 mouvements de stock inchanges", mouvementsDeStock() == mouvements);
        m.put("05 stock des articles inchange", stocksDesArticles());
        m.put("06 diffusions", diffusionsLues());
        m.put("07 seconde validation avec la meme liste (statut inchange, consommations ajoutees une seconde fois)", reponse(doPost(
                validerApi(seance), validationAvecConsommations(ligne(a1, 2)), infirmierA())));
        m.put("08 consommations apres la seconde validation", consommationsImputees());
        m.put("09 validation sans consommations d'une seance deja consommee", reponse(valider(seance, infirmierA())));
        m.put("10 consommations inchangees", consommationsImputees());
        arbre("consommables_imputes", m);
    }

    @Test
    void un_echec_du_stock_ou_du_catalogue_annule_la_validation_la_place_et_les_consommations_deja_imputees() throws Exception {
        LocalDate jour = jour(0);
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID salle = salle(CENTRE_A, "SALLE_1", "Salle 1", "NON");
        UUID creneau = creneau(CENTRE_A, "CRENEAU_MATIN", "Matin");
        placerLePatient(patient, salle, creneau, null);
        UUID actif = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID inactif = articleSe2(CENTRE_A, "ART_INACTIF", false, "10");
        UUID autreCentre = articleSe2(CENTRE_B, "ART_AUTRE_CENTRE", true, "10");
        UUID inconnu = idStable("article", "inconnu");
        aliasNommes.put(inconnu.toString(), "<ART_INCONNU>");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour, "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        Object[][] cas = {
                {"article actif puis article inactif (400)", validationAvecConsommations(ligne(actif, 1), ligne(inactif, 1))},
                {"article actif puis article inconnu (400)", validationAvecConsommations(ligne(actif, 1), ligne(inconnu, 1))},
                {"article actif puis article d'un autre centre (400)", validationAvecConsommations(ligne(actif, 1), ligne(autreCentre, 1))},
                {"article sans identifiant (400)", validationAvecConsommations(ligne(actif, 1), ligne(null, 1))},
                {"quantite nulle apres une ligne valide (422 du Stock)", validationAvecConsommations(ligne(actif, 1), ligne(actif, 0))},
                {"quantite negative (422 du Stock)", validationAvecConsommations(ligne(actif, -2))},
                {"quantite absente (422 du Stock)", validationAvecConsommations(ligne(actif, null))},
                {"ligne nulle dans la liste", corps("centerId", CENTRE_A, "userId", "inf-01", "consommations", java.util.Arrays.asList((Object) null))},
        };
        int i = 0;
        for (Object[] c : cas) {
            Resp r = doPost(validerApi(seance), (String) c[1], infirmierA());
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("reponse", refus(r));
            issue.put("seance", etatCourt(seance));
            issue.put("place memorisee", lignes("SELECT salle_id, creneau_id FROM seances WHERE id = ?", seance));
            issue.put("consommations imputees", consommationsImputees());
            m.put(String.format("%02d %s", ++i, c[0]), issue);
        }
        m.put("09 diffusions (aucune)", diffusionsLues());
        Resp valide = valider(seance, infirmierA());
        m.put("10 puis une validation sans consommations : la place du patient est alors memorisee", refus(valide) + " ; "
                + lignes("SELECT statut, salle_id, creneau_id FROM seances WHERE id = ?", seance));
        arbre("retour_arriere", m);
    }

    @Test
    void les_consommables_s_imputent_sur_la_sortie_pour_seances_ouverte_du_jour_avec_alerte_de_depassement() throws Exception {
        LocalDate demain = jour(1);
        UUID patient1 = patientCouvert(CENTRE_A, "PAT_A");
        UUID patient2 = patientCouvert(CENTRE_A, "PAT_B");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID lot = lotSe2(CENTRE_A, a1, "LOT_1", "10");
        Resp piece = doPost("/api/v1/stock/sorties-pour-seances", corps("jour", demain, "lignes", List.of(ligne(a1, 8))), adminA());
        UUID s1 = seanceEnBase(CENTRE_A, patient1, "SEANCE_1", demain, "CREE");
        UUID s2 = seanceEnBase(CENTRE_A, patient2, "SEANCE_2", demain, "CREE");
        int mouvements = mouvementsDeStock();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 creation de la piece du lendemain", piece.status());
        m.put("02 premiere validation de 5 sur la piece de 8", reponse(doPost(validerApi(s1), validationAvecConsommations(ligne(a1, 5)), infirmierA())));
        m.put("03 seconde validation de 5 (depassement : 3 restants)", reponse(doPost(validerApi(s2), validationAvecConsommations(ligne(a1, 5)),
                infirmierA())));
        m.put("04 consommations imputees (sur la piece, par lot)", consommationsImputees());
        m.put("05 mouvements de stock inchanges depuis la piece", mouvementsDeStock() == mouvements);
        m.put("06 lot inchange (la sortie a eu lieu a la piece)", lignes("SELECT numero_lot, quantite_restante FROM lots WHERE id = ?", lot));
        m.put("07 etat", etatSe2());
        arbre("sortie_pour_seances", m);
    }

    @Test
    void la_place_du_patient_est_memorisee_a_la_validation_sans_jamais_remplacer_une_place_deja_figee() throws Exception {
        decorDePlacement();
        LocalDate jour = jour(0);
        Map<String, Object> m = new LinkedHashMap<>();
        // place habituelle
        UUID p1 = patientCouvert(CENTRE_A, "PAT_HABITUEL");
        placerLePatient(p1, salle1, matin, g1a);
        UUID s1 = seanceEnBase(CENTRE_A, p1, "SEANCE_HABITUELLE", jour, "CREE");
        valider(s1, infirmierA());
        // deplacement temporaire du jour
        UUID p2 = patientCouvert(CENTRE_A, "PAT_DEPLACE");
        placerLePatient(p2, salle1, matin, g1a);
        deplacementTemporaire(CENTRE_A, p2, jour, salle2, apresMidi, g2a);
        UUID s2 = seanceEnBase(CENTRE_A, p2, "SEANCE_DEPLACEE", jour, "CREE");
        valider(s2, infirmierA());
        // sans place
        UUID p3 = patientCouvert(CENTRE_A, "PAT_SANS_PLACE");
        UUID s3 = seanceEnBase(CENTRE_A, p3, "SEANCE_SANS_PLACE", jour, "CREE");
        valider(s3, infirmierA());
        // place deja figee, differente de la place du patient
        UUID p4 = patientCouvert(CENTRE_A, "PAT_DEJA_FIGE");
        placerLePatient(p4, salle1, matin, g1a);
        UUID s4 = seanceEnBase(CENTRE_A, p4, "SEANCE_DEJA_FIGEE", jour, "CREE");
        jdbc.update("UPDATE seances SET salle_id = ?, creneau_id = ?, generateur_id = ? WHERE id = ?", salle2, apresMidi, g2a, s4);
        valider(s4, infirmierA());
        // validee avant que la place existe : la place est figee a la seconde validation, sans changer le statut
        UUID p5 = patientCouvert(CENTRE_A, "PAT_VALIDEE_SANS_PLACE");
        UUID s5 = seanceValidee(CENTRE_A, p5, "SEANCE_VALIDEE_SANS_PLACE", jour, "VALIDEE");
        placerLePatient(p5, salle1, matin, g1b);
        valider(s5, infirmierA());
        // facturee : refusee, aucune place
        UUID p6 = patientCouvert(CENTRE_A, "PAT_FACTUREE");
        placerLePatient(p6, salle1, matin, g1a);
        UUID s6 = seanceValidee(CENTRE_A, p6, "SEANCE_FACTUREE", jour, "FACTUREE");
        valider(s6, infirmierA());
        m.put("01 etat (place habituelle, deplacement du jour, sans place, deja figee, figee a la seconde validation, facturee)", etatSe2());
        arbre("place_memorisee", m);
    }

    @Test
    void aucun_cache_de_seances_n_existe_donc_la_validation_et_la_signature_n_en_evincent_aucun() throws Exception {
        LocalDate jour = jour(0);
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour, "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> noms = new java.util.ArrayList<>(caches.getCacheNames());
        java.util.Collections.sort(noms);
        m.put("01 caches dont le nom evoque les seances (le plan annonce ref.seances.du.jour et ref.seances.validees.non.signees)",
                noms.stream().filter(n -> n.toLowerCase().contains("seance")).toList());
        m.put("02 validation puis signature", refus(valider(seance, infirmierA())) + " ; " + refus(signer(seance, medecinA())));
        List<String> apres = new java.util.ArrayList<>(caches.getCacheNames());
        java.util.Collections.sort(apres);
        m.put("03 liste des caches inchangee", noms.equals(apres));
        arbre("caches", m);
    }

    @Test
    void les_validations_et_leurs_refus_sont_journalises_avec_la_route_et_le_statut() throws Exception {
        LocalDate jour = jour(0);
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID creee = seanceEnBase(CENTRE_A, patient, "SEANCE_CREE", jour, "CREE");
        UUID absente = seanceEnBase(CENTRE_A, patient, "SEANCE_ABSENTE", jour, "ABSENT");
        UUID passee = seanceEnBase(CENTRE_A, patient, "SEANCE_PASSEE", jour(-3), "CREE");
        UUID inconnue = idStable("seance", "inconnue");
        aliasNommes.put(inconnue.toString(), "<SEANCE_INCONNUE>");
        monterLAudit();
        valider(creee, infirmierA());
        valider(creee, adminA());
        valider(absente, infirmierA());
        valider(passee, infirmierA());
        valider(inconnue, infirmierA());
        valider(creee, medecinA());
        doPost(validerApi(creee), corps("centerId", CENTRE_B, "userId", "u"), infirmierA());
        doPost(validerApi(creee), "{pas du json", infirmierA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }

    @Test
    void deux_validations_simultanees_de_la_meme_seance_ne_sont_protegees_par_aucun_verrou() throws Exception {
        LocalDate jour = jour(0);
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour, "CREE");
        List<Resp> reponses = enParallele(
                () -> doPost(validerApi(seance), validationAvecConsommations(ligne(a1, 1)), infirmierA()),
                () -> doPost(validerApi(seance), validationAvecConsommations(ligne(a1, 1)), adminA()));
        long succes = reponses.stream().filter(r -> r.status() == 200).count();
        Number imputee = jdbc.queryForObject("SELECT COALESCE(SUM(quantite), 0) FROM consommations_de_seance_imputations WHERE article_id = ?",
                Number.class, a1);
        Map<String, Object> m = new LinkedHashMap<>();
        // Invariants seulement : ordre d'arrivee non deterministe, H2 n'est pas PostgreSQL.
        m.put("01 au moins une validation reussit", succes >= 1);
        m.put("02 une seule ligne de seance, VALIDEE", lignes("SELECT statut FROM seances WHERE center_id = ?", CENTRE_A));
        m.put("03 la quantite imputee n'excede jamais le nombre de validations reussies", imputee.intValue() <= succes && imputee.intValue() >= 1);
        arbre("ecritures_simultanees", m);
    }

    @Test
    void une_seance_dont_le_patient_a_disparu_ou_dont_le_statut_est_illisible_est_refusee_en_400_sans_ecriture() throws Exception {
        LocalDate jour = jour(0);
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID sansPatient = seanceEnBase(CENTRE_A, patient, "SEANCE_SANS_PATIENT", jour, "CREE");
        UUID autre = patientCouvert(CENTRE_A, "PAT_B");
        UUID illisible = seanceEnBase(CENTRE_A, autre, "SEANCE_STATUT_MINUSCULE", jour, "cree");
        jdbc.update("DELETE FROM patients WHERE id = ?", patient);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 patient disparu : infirmier", refus(valider(sansPatient, infirmierA())));
        m.put("02 patient disparu : administrateur", refus(valider(sansPatient, adminA())));
        m.put("03 statut en minuscules : infirmier", refus(valider(illisible, infirmierA())));
        m.put("04 etat", etatSe2());
        arbre("lignes_illisibles", m);
    }
}
