package com.hemodialyse.backend.characterization.planification.infirmiers;

import com.hemodialyse.backend.characterization.planification.InfirmiersCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filet de caractérisation P2 : référentiel des infirmiers, {@code GET|POST /api/v1/infirmiers},
 * {@code PUT /{id}}, {@code POST /{id}/desactiver|reactiver} (RG-INF-001 à 004). Points d'observation : HTTP et état SQL de
 * {@code infirmier}. Aucune date : le référentiel n'en porte pas.
 */
class ReferentielDesInfirmiersCharacterizationIntegrationTest extends InfirmiersCharacterizationSupport {

    @Override
    protected String theme() {
        return "p2-infirmiers/referentiel";
    }

    private static String fiche(String matricule, String nom, String prenom, String telephone, String qualification,
                                Object isolement) {
        return json("{'matricule':" + q(matricule) + ",'nom':" + q(nom) + ",'prenom':" + q(prenom) + ",'telephone':"
                + q(telephone) + ",'qualification':" + q(qualification) + ",'habiliteIsolement':" + isolement + "}");
    }

    private static String q(String s) {
        return s == null ? "null" : "\"" + s.replace("\"", "\\\"") + "\"";
    }

    private static String simple(String matricule, String nom) {
        return fiche(matricule, nom, "Prenom", null, "INFIRMIER", false);
    }

    // ------------------------------------------------------------------ lecture

    @Test
    void la_liste_d_un_centre_sans_infirmier_est_vide_et_ne_cree_rien() throws Exception {
        golden("liste_vide_200", doGet(INFIRMIERS, adminA()));
        assertEquals(0, nombreDeFiches());
    }

    @Test
    void la_liste_est_triee_par_nom_prenom_puis_matricule_garde_les_inactifs_et_ignore_les_autres_centres() throws Exception {
        fiche(CENTRE_A, "INF_BENALI", "M2", "Benali", "Samia", "0550 11 22 33", "MAJOR", true, true, null);
        fiche(CENTRE_A, "INF_AMRANI_SANS_PRENOM", "M1", "Amrani", null, true);
        fiche(CENTRE_A, "INF_AMRANI_NADIA_Z", "Z9", "Amrani", "Nadia", true);
        fiche(CENTRE_A, "INF_AMRANI_NADIA_A", "A1", "Amrani", "Nadia", false);
        fiche(CENTRE_A, "INF_MINUSCULE", "M3", "benali", "Karim", null, "AIDE_SOIGNANT", false, true, null);
        fiche(CENTRE_B, "INF_AUTRE_CENTRE", "M1", "Zerrouki", "Rania", true);
        golden("liste_triee_avec_inactifs_200", doGet(INFIRMIERS, adminA()));
        golden("liste_autre_centre_200", doGet(INFIRMIERS, adminB()));
    }

    @Test
    void la_liste_porte_le_roulement_et_le_compte_relie_ou_null_quand_le_compte_a_disparu() throws Exception {
        UUID salle = salle(CENTRE_A, "SALLE_1", "Salle 1", "NON");
        UUID creneau = creneau(CENTRE_A, "CRENEAU_1", "Matin");
        UUID actif = compte("COMPTE_ACTIF", "actif", "Compte actif", "INFIRMIER", true, CENTRE_A);
        UUID inactif = compte("COMPTE_INACTIF", "inactif", "Compte inactif", "INFIRMIER", false, CENTRE_A);
        UUID fantome = UUID.nameUUIDFromBytes("fantome".getBytes());
        aliasNommes.put(fantome.toString(), "<COMPTE_SUPPRIME>");
        UUID a = fiche(CENTRE_A, "INF_A", "A1", "Aaa", "Un", null, "INFIRMIER", false, true, actif);
        fiche(CENTRE_A, "INF_B", "B1", "Bbb", "Deux", null, "INFIRMIER", false, false, inactif);
        fiche(CENTRE_A, "INF_C", "C1", "Ccc", "Trois", null, "INFIRMIER", false, true, fantome);
        affectation(CENTRE_A, "AFF_2", a, salle, creneau, "MARDI,LUNDI");
        affectation(CENTRE_A, "AFF_1", a, salle, creneau, "SAMEDI");
        golden("liste_avec_roulement_et_comptes_200", doGet(INFIRMIERS, adminA()));
    }

    @Test
    void les_quatre_profils_de_lecture_du_referentiel_recoivent_la_meme_liste() throws Exception {
        fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        String attendu = doGet(INFIRMIERS, adminA()).raw();
        assertEquals(attendu, doGet(INFIRMIERS, as(CENTRE_A, "SECRETAIRE")).raw());
        assertEquals(attendu, doGet(INFIRMIERS, as(CENTRE_A, "MEDECIN", "INFIRMIER")).raw());
        assertEquals(403, doGet(INFIRMIERS, as(CENTRE_A, "MEDECIN")).status());
        assertEquals(403, doGet(INFIRMIERS, as(CENTRE_A, "INFIRMIER")).status());
    }

    @Test
    void la_pagination_est_en_nombre_de_pages_commencant_a_0_sans_borne_de_taille() throws Exception {
        for (int i = 1; i <= 5; i++) fiche(CENTRE_A, "INF_" + i, "M" + i, "Nom" + i, "Prenom", true);
        golden("pagination_page_1_taille_2_200", doGet(INFIRMIERS + "?page=1&size=2", adminA()));
        golden("pagination_derniere_page_incomplete_200", doGet(INFIRMIERS + "?page=2&size=2", adminA()));
        golden("pagination_page_hors_limite_200", doGet(INFIRMIERS + "?page=9&size=2", adminA()));
        golden("pagination_taille_1000_200", doGet(INFIRMIERS + "?size=1000", adminA()));
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String requete : List.of("?size=0", "?size=-1", "?page=-1", "?size=abc", "?page=abc", "?size=", "?page=0&size=1")) {
            Resp r = doGet(INFIRMIERS + requete, adminA());
            cas.put(requete, statutEtCode(r) + (r.status() == 200 ? " items=" + corps(r).path("items").size() + " total="
                    + corps(r).path("total") + " page=" + corps(r).path("page") + " size=" + corps(r).path("size") : ""));
        }
        goldenNode("pagination_cas_limites", mapper.valueToTree(cas));
    }

    // ------------------------------------------------------------------ garde de centre

    @Test
    void la_garde_de_centre_ignore_ou_refuse_un_centre_explicite_different_de_la_session() throws Exception {
        fiche(CENTRE_A, "INF_A", "M1", "Amrani", "Sara", true);
        UUID chezB = fiche(CENTRE_B, "INF_B", "M1", "Zerrouki", "Rania", true);
        goldenSimple("liste_centre_explicite_identique_200", doGet(INFIRMIERS + "?centerId=" + CENTRE_A, adminA()));
        goldenSimple("liste_centre_explicite_autre_centre", doGet(INFIRMIERS + "?centerId=" + CENTRE_B, adminA()));
        goldenSimple("liste_centre_explicite_non_uuid", doGet(INFIRMIERS + "?centerId=pas-un-uuid", adminA()));
        goldenSimple("creation_centre_explicite_autre_centre", doPost(INFIRMIERS + "?centerId=" + CENTRE_B,
                simple("INTRUS", "Intrus"), adminA()));
        goldenSimple("modification_fiche_d_un_autre_centre", doPut(INFIRMIERS + "/" + chezB, simple("M1", "Pirate"), adminA()));
        goldenSimple("desactivation_fiche_d_un_autre_centre", doPost(INFIRMIERS + "/" + chezB + "/desactiver", "{}", adminA()));
        goldenSimple("admin_sans_centre_avec_centre_explicite", doGet(INFIRMIERS + "?centerId=" + CENTRE_A, as(null, "ADMIN")));
        etatInfirmiers("garde_de_centre_etat_base");
    }

    // ------------------------------------------------------------------ création

    @Test
    void la_creation_enregistre_une_fiche_active_sans_compte_ni_roulement_et_normalise_les_champs_texte() throws Exception {
        goldenSimple("creation_nominale_201", doPost(INFIRMIERS,
                fiche("  M-001  ", "  Amrani ", " Sara ", " 0550 11 22 33 ", "MAJOR", true), adminA()));
        goldenSimple("creation_prenom_et_telephone_blancs_201", doPost(INFIRMIERS,
                fiche("M-002", "Benali", "   ", "", "AIDE_SOIGNANT", false), adminA()));
        goldenSimple("creation_prenom_et_telephone_absents_201", doPost(INFIRMIERS,
                json("{'matricule':'M-003','nom':'Chikhi','qualification':'INFIRMIER','habiliteIsolement':false}"), adminA()));
        goldenSimple("creation_champs_inconnus_ignores_201", doPost(INFIRMIERS,
                json("{'matricule':'M-004','nom':'Dahmani','qualification':'INFIRMIER','habiliteIsolement':false,"
                        + "'actif':false,'id':'" + UUID.nameUUIDFromBytes("imposé".getBytes()) + "','userId':'"
                        + UUID.nameUUIDFromBytes("compte".getBytes()) + "'}"), adminA()));
        etatInfirmiers("creation_etat_base");
        assertEquals(4, nombreDeFiches());
    }

    @Test
    void les_champs_obligatoires_et_les_valeurs_illisibles_sont_refuses_sans_rien_ecrire_et_certains_donnent_500_KB_PLN_12() throws Exception {
        Map<String, String> corps = new LinkedHashMap<>();
        corps.put("matricule_absent", json("{'nom':'N','qualification':'INFIRMIER','habiliteIsolement':false}"));
        corps.put("matricule_blanc", fiche("   ", "N", null, null, "INFIRMIER", false));
        corps.put("nom_absent", json("{'matricule':'M','qualification':'INFIRMIER','habiliteIsolement':false}"));
        corps.put("nom_blanc", fiche("M", "  ", null, null, "INFIRMIER", false));
        corps.put("qualification_absente", json("{'matricule':'M','nom':'N','habiliteIsolement':false}"));
        corps.put("qualification_blanche", fiche("M", "N", null, null, " ", false));
        corps.put("qualification_inconnue", fiche("M", "N", null, null, "CHIRURGIEN", false));
        corps.put("qualification_en_minuscules", fiche("M", "N", null, null, "infirmier", false));
        corps.put("habilite_isolement_absent", json("{'matricule':'H1','nom':'N','qualification':'INFIRMIER'}"));
        corps.put("habilite_isolement_null", fiche("H2", "N", null, null, "INFIRMIER", "null"));
        corps.put("habilite_isolement_texte_vrai_accepte", fiche("C1", "N", null, null, "INFIRMIER", "\"true\""));
        corps.put("habilite_isolement_texte_invalide", fiche("H3", "N", null, null, "INFIRMIER", "\"peut-etre\""));
        corps.put("habilite_isolement_nombre_1_accepte", fiche("C2", "N", null, null, "INFIRMIER", 1));
        corps.put("nom_nombre_accepte", json("{'matricule':'C3','nom':12,'qualification':'INFIRMIER','habiliteIsolement':false}"));
        corps.put("json_tronque", "{\"matricule\":");
        corps.put("tableau_json", "[]");
        corps.put("json_null", "null");
        corps.put("objet_vide", "{}");
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, String> c : corps.entrySet()) {
            matrice.put(c.getKey(), statutEtCode(doPost(INFIRMIERS, c.getValue(), adminA())));
        }
        matrice.put("corps_absent", statutEtCode(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(java.net.URI.create(INFIRMIERS)).with(adminA()))));
        matrice.put("type_de_contenu_texte", statutEtCode(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(java.net.URI.create(INFIRMIERS)).contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .content("x").with(adminA()))));
        goldenNode("creation_refus_matrice", mapper.valueToTree(matrice));
        goldenSimple("creation_qualification_inconnue_detail", doPost(INFIRMIERS, corps.get("qualification_inconnue"), adminA()));
        goldenSimple("creation_matricule_absent_detail", doPost(INFIRMIERS, corps.get("matricule_absent"), adminA()));
        etatInfirmiers("creation_refus_etat_base");
    }

    @Test
    void les_longueurs_au_dela_des_colonnes_donnent_une_erreur_technique_500_KB_PLN_13_et_la_borne_exacte_passe() throws Exception {
        Map<String, Object> matrice = new LinkedHashMap<>();
        matrice.put("matricule_50_caracteres", statutEtCode(doPost(INFIRMIERS, simple("M".repeat(50), "N"), adminA())));
        matrice.put("matricule_51_caracteres", statutEtCode(doPost(INFIRMIERS, simple("M".repeat(51), "N"), adminA())));
        matrice.put("nom_255_caracteres", statutEtCode(doPost(INFIRMIERS, simple("L1", "N".repeat(255)), adminA())));
        matrice.put("nom_256_caracteres", statutEtCode(doPost(INFIRMIERS, simple("L2", "N".repeat(256)), adminA())));
        matrice.put("telephone_50_caracteres", statutEtCode(doPost(INFIRMIERS,
                fiche("L3", "N", null, "0".repeat(50), "INFIRMIER", false), adminA())));
        matrice.put("telephone_51_caracteres", statutEtCode(doPost(INFIRMIERS,
                fiche("L4", "N", null, "0".repeat(51), "INFIRMIER", false), adminA())));
        matrice.put("prenom_256_caracteres", statutEtCode(doPost(INFIRMIERS,
                fiche("L5", "N", "P".repeat(256), null, "INFIRMIER", false), adminA())));
        goldenNode("creation_longueurs_matrice", mapper.valueToTree(matrice));
        goldenSql("creation_longueurs_etat_base", "SELECT LENGTH(matricule) AS lm, LENGTH(nom) AS ln, "
                + "COALESCE(LENGTH(telephone), 0) AS lt FROM infirmier WHERE center_id = ? ORDER BY lm, ln, lt", CENTRE_A);
    }

    @Test
    void le_matricule_est_unique_par_centre_sans_tenir_compte_de_la_casse_ni_des_espaces_de_bord() throws Exception {
        goldenSimple("premiere_creation_201", doPost(INFIRMIERS, simple("M-100", "Amrani"), adminA()));
        goldenSimple("matricule_identique_422", doPost(INFIRMIERS, simple("M-100", "Benali"), adminA()));
        goldenSimple("matricule_en_minuscules_422", doPost(INFIRMIERS, simple("m-100", "Benali"), adminA()));
        goldenSimple("matricule_avec_espaces_de_bord_422", doPost(INFIRMIERS, simple("  M-100  ", "Benali"), adminA()));
        goldenSimple("meme_matricule_dans_un_autre_centre_201", doPost(INFIRMIERS + "?centerId=" + CENTRE_B,
                simple("M-100", "Autre"), adminB()));
        etatInfirmiers("matricule_unique_etat_base");
        assertEquals(2, nombreDeFiches());
    }

    @Test
    void un_matricule_refuse_l_est_avant_la_validation_de_la_qualification_mais_apres_les_champs_obligatoires() throws Exception {
        fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        goldenSimple("matricule_existant_et_qualification_inconnue", doPost(INFIRMIERS,
                fiche("M1", "N", null, null, "CHIRURGIEN", false), adminA()));
        goldenSimple("matricule_existant_et_nom_blanc", doPost(INFIRMIERS, fiche("M1", " ", null, null, "INFIRMIER", false), adminA()));
        assertEquals(1, nombreDeFiches());
    }

    @Test
    void deux_creations_simultanees_du_meme_matricule_n_en_gardent_qu_une_les_issues_possibles_sont_201_et_422_ou_500() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch depart = new CountDownLatch(1);
            Callable<Integer> appel = () -> {
                depart.await();
                return doPost(INFIRMIERS, simple("COURSE", "Concurrent"), adminA()).status();
            };
            Future<Integer> a = pool.submit(appel);
            Future<Integer> b = pool.submit(appel);
            depart.countDown();
            List<Integer> statuts = List.of(a.get(), b.get()).stream().sorted().toList();
            assertTrue(statuts.equals(List.of(201, 422)) || statuts.equals(List.of(201, 500)), statuts.toString());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, nombreDeFiches());
    }

    // ------------------------------------------------------------------ modification

    @Test
    void la_modification_remplace_les_champs_en_conservant_l_etat_et_le_compte_et_ne_touche_pas_le_compte() throws Exception {
        UUID compte = compte("COMPTE_1", "lie", "Sara Amrani", "INFIRMIER", true, CENTRE_A);
        UUID id = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", "0550", "INFIRMIER", false, true, compte);
        goldenSimple("modification_nominale_200", doPut(INFIRMIERS + "/" + id,
                fiche(" M1-bis ", " Renomme ", " Nouveau ", " 0661 ", "MAJOR", true), adminA()));
        etatInfirmiers("modification_etat_base");
        etatComptes("modification_etat_compte_inchange_KB_PLN_14");
        goldenSimple("modification_efface_prenom_et_telephone_200", doPut(INFIRMIERS + "/" + id,
                fiche("M1-bis", "Renomme", null, null, "MAJOR", true), adminA()));
        goldenSimple("modification_identique_idempotente_200", doPut(INFIRMIERS + "/" + id,
                fiche("M1-bis", "Renomme", null, null, "MAJOR", true), adminA()));
        etatInfirmiers("modification_idempotente_etat_base");
    }

    @Test
    void la_modification_conserve_le_statut_inactif_et_la_derniere_ecriture_gagne() throws Exception {
        UUID id = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", false);
        goldenSimple("modification_fiche_inactive_200", doPut(INFIRMIERS + "/" + id, simple("M1", "Premier"), adminA()));
        goldenSimple("modification_seconde_ecriture_200", doPut(INFIRMIERS + "/" + id, simple("M1", "Second"), adminA()));
        etatInfirmiers("modification_derniere_ecriture_gagne_etat_base");
    }

    @Test
    void la_modification_garde_le_matricule_unique_par_centre_et_refuse_les_fiches_inconnues_ou_d_un_autre_centre() throws Exception {
        UUID un = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        fiche(CENTRE_A, "INF_2", "M2", "Benali", "Karim", true);
        fiche(CENTRE_B, "INF_B", "M3", "Zerrouki", "Rania", true);
        goldenSimple("modification_meme_matricule_que_soi_en_casse_differente_200", doPut(INFIRMIERS + "/" + un, simple("m1", "Amrani"), adminA()));
        goldenSimple("modification_matricule_d_un_autre_infirmier_422", doPut(INFIRMIERS + "/" + un, simple("M2", "Amrani"), adminA()));
        goldenSimple("modification_matricule_d_un_autre_infirmier_en_minuscules_422", doPut(INFIRMIERS + "/" + un, simple("m2", "Amrani"), adminA()));
        goldenSimple("modification_matricule_libre_ailleurs_200", doPut(INFIRMIERS + "/" + un, simple("M3", "Amrani"), adminA()));
        goldenSimple("modification_fiche_inconnue_422", doPut(INFIRMIERS + "/" + UUID.nameUUIDFromBytes("x".getBytes()),
                simple("M9", "Inconnu"), adminA()));
        goldenSimple("modification_corps_invalide_sur_fiche_inconnue", doPut(INFIRMIERS + "/" + UUID.nameUUIDFromBytes("x".getBytes()),
                json("{}"), adminA()));
        goldenSimple("modification_identifiant_non_uuid", doPut(INFIRMIERS + "/pas-un-uuid", simple("M9", "Inconnu"), adminA()));
        goldenSimple("modification_qualification_inconnue_sur_fiche_inconnue", doPut(INFIRMIERS + "/" + UUID.nameUUIDFromBytes("x".getBytes()),
                fiche("M9", "N", null, null, "CHIRURGIEN", false), adminA()));
        etatInfirmiers("modification_matricule_etat_base");
    }

    // ------------------------------------------------------------------ activation

    @Test
    void la_desactivation_et_la_reactivation_sont_idempotentes_et_conservent_la_fiche() throws Exception {
        UUID id = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        goldenSimple("desactivation_200", doPost(INFIRMIERS + "/" + id + "/desactiver", "{}", adminA()));
        etatInfirmiers("desactivation_etat_base");
        goldenSimple("desactivation_repetee_200", doPost(INFIRMIERS + "/" + id + "/desactiver", "{}", adminA()));
        goldenSimple("reactivation_200", doPost(INFIRMIERS + "/" + id + "/reactiver", "{}", adminA()));
        goldenSimple("reactivation_repetee_200", doPost(INFIRMIERS + "/" + id + "/reactiver", "{}", adminA()));
        etatInfirmiers("reactivation_etat_base");
    }

    @Test
    void l_activation_sans_corps_ni_type_de_contenu_est_acceptee_et_les_fiches_inconnues_donnent_422() throws Exception {
        UUID id = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        goldenSimple("desactivation_sans_corps_200", send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(java.net.URI.create(INFIRMIERS + "/" + id + "/desactiver")).with(adminA())));
        goldenSimple("desactivation_fiche_inconnue_422", doPost(INFIRMIERS + "/" + UUID.nameUUIDFromBytes("x".getBytes())
                + "/desactiver", "{}", adminA()));
        goldenSimple("reactivation_fiche_inconnue_422", doPost(INFIRMIERS + "/" + UUID.nameUUIDFromBytes("x".getBytes())
                + "/reactiver", "{}", adminA()));
        goldenSimple("reactivation_identifiant_non_uuid", doPost(INFIRMIERS + "/pas-un-uuid/reactiver", "{}", adminA()));
        etatInfirmiers("activation_sans_corps_etat_base");
    }

    // ------------------------------------------------------------------ effets sur P1 (profils de planification)

    @Test
    void une_fiche_creee_apparait_dans_les_profils_de_planification_une_fiche_desactivee_en_disparait_sans_perdre_son_profil() throws Exception {
        String profils = PLANNING + "/preferences/infirmiers";
        Resp creation = doPost(INFIRMIERS, fiche("M1", "Amrani", "Sara", null, "MAJOR", true), adminA());
        String id = corps(creation).path("id").asString();
        golden("p1_profils_apres_creation_200", doGet(profils, adminA()));
        goldenSimple("p1_profil_enregistre_200", doPut(profils + "/" + id,
                json("{'tauxActivite':80,'competences':['CATHETER']}"), adminA()));
        doPost(INFIRMIERS + "/" + id + "/desactiver", "{}", adminA());
        golden("p1_profils_apres_desactivation_200", doGet(profils, adminA()));
        goldenSimple("p1_profil_d_une_fiche_inactive", doPut(profils + "/" + id,
                json("{'tauxActivite':50,'competences':[]}"), adminA()));
        doPost(INFIRMIERS + "/" + id + "/reactiver", "{}", adminA());
        golden("p1_profils_apres_reactivation_200", doGet(profils, adminA()));
        goldenSql("p1_profil_etat_base", "SELECT center_id, infirmier_id, taux_activite, competences FROM infirmier_profil_planning "
                + "WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "')");
    }
}
