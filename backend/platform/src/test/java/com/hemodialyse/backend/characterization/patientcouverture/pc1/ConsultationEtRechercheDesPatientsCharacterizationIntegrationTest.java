package com.hemodialyse.backend.characterization.patientcouverture.pc1;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC1 : lectures de la fiche ({@code GET /api/v1/patients/{id}}), recherche paginée ({@code POST /api/v1/patients/search}) et
 * résumés ({@code GET /api/v1/patients/summary}, {@code /summary/details}). Les noms sont classés par la base de test (H2) : l'ordre
 * des majuscules, minuscules et accents dépend de l'interclassement du SGBD.
 */
class ConsultationEtRechercheDesPatientsCharacterizationIntegrationTest extends PatientCouvertureCharacterizationSupport {

    @Override
    protected String dossier() {
        return "consultation";
    }

    // ------------------------------------------------------------------ fixtures

    private UUID graine(String alias, String nom, String prenom, String sexe, String etat, Integer evenement, int admission, String numero) {
        UUID id = patientSansPlace(alias);
        jdbc.update("UPDATE patients SET nom = ?, prenom = ?, sexe = ?, numero_assurance = ?, code_patient = ?, date_admission = ?, "
                        + "date_naissance = ? WHERE id = ?", nom, prenom, sexe, numero, "PC1-" + alias,
                java.sql.Date.valueOf(jour(admission)), java.sql.Date.valueOf(jour(-20000)), id);
        etat(id, etat, evenement == null ? null : jour(evenement));
        return id;
    }

    private void septPatients() {
        graine("C1", "Alpha", "Zoe", "F", "PERMANENT", null, -50, "N-100");
        graine("C2", "Beta", "Yann", "M", "TRANSFERE", 100, -40, "N-200");
        graine("C3", "Gamma", "Xavier", "M", "DECEDE", 100, -30, "N-300");
        graine("C4", "Delta", "Walid", "M", "GUERRI", 100, -20, "N-400");
        graine("C5", "Epsilon", "Victor", "F", "OCCASIONNEL", 100, -10, "N-500");
        graine("C6", "Eta_Bis", "Ulysse", "M", "VACANCIER_LOCAL", 100, -5, "N-600");
        UUID sommeil = graine("C7", "Theta", "Tess", "F", "PERMANENT", null, -400, "N-700");
        jdbc.update("UPDATE patients SET en_sommeil = TRUE WHERE id = ?", sommeil);
    }

    private List<String> noms(Resp r) {
        List<String> l = new ArrayList<>();
        for (JsonNode i : corps(r).path("items")) l.add(i.path("nom").asString());
        return l;
    }

    private String recherchePar(String filtre) throws Exception {
        Resp r = recherche("{'centerId':'" + CENTRE_A + "','page':0,'size':50" + (filtre.isEmpty() ? "" : "," + filtre) + "}", adminA());
        return r.status() == 200 ? noms(r).toString() : refus(r);
    }

    // ------------------------------------------------------------------ fiche

    @Test
    void la_fiche_rend_l_entite_patient_complete_telle_que_creee() throws Exception {
        Map<String, Object> f = ficheMap("Complet", "PC1-FICHE-1");
        f.put("civilite", "M.");
        f.put("groupeSanguin", "A+");
        f.put("nombreEnfants", 2);
        f.put("lieuNaissance", "Alger");
        f.put("situationFamiliale", "Marié");
        f.put("profession", "Enseignant");
        f.put("adresse", "1 rue X");
        f.put("telPersonnel", "021 12 34 56");
        f.put("telMobile", "0555123456");
        f.put("telBureau", "00213 23 12 34 56");
        f.put("email", "X@Y.zz");
        f.put("sousKt", true);
        f.put("epoEnabled", true);
        f.put("epoDate", jour(-30).toString());
        f.put("ferEnabled", true);
        f.put("ferDate", jour(-20).toString());
        f.put("observation", "Observation");
        f.put("enSommeil", true);
        f.put("piecesJointesJson", "[]");
        f.put("assureHistoryJson", "[]");
        Resp c = creer("COMPLET", f, adminA());
        ecriture("creation_de_la_fiche_complete", sansCodeAleatoire(c));
        UUID id = UUID.fromString(corps(c).path("id").asString());
        lecture("fiche_complete", sansCodeAleatoire(lireLaFiche(id, CENTRE_A, adminA())));
    }

    @Test
    void la_fiche_d_un_assure_tiers_rend_l_assure_principal_et_celle_d_un_assure_lui_meme_n_en_rend_pas() throws Exception {
        Map<String, Object> f = ficheMap("Enfant", "PC1-FICHE-2");
        f.put("qualiteAssure", "ENFANT");
        f.put("assureNumeroAssurance", "PC1-ASSURE-9");
        f.put("assureNom", "Parent");
        f.put("assurePrenom", "Prenom");
        f.put("assureSexe", "F");
        f.put("assureDateNaissance", jour(-15000).toString());
        f.put("assureGroupeSanguin", "O+");
        Resp c = creer("ENFANT", f, adminA());
        UUID id = UUID.fromString(corps(c).path("id").asString());
        lecture("fiche_enfant", sansCodeAleatoire(lireLaFiche(id, CENTRE_A, adminA())));
        patientSansPlace("LUI_MEME");
        lecture("fiche_lui_meme", lireLaFiche(idDe("LUI_MEME"), CENTRE_A, adminA()));
    }

    @Test
    void la_fiche_d_un_patient_place_rend_le_generateur_du_parc_avec_son_code_sa_marque_et_son_statut() throws Exception {
        decorDePlacement();
        UUID id = place(CENTRE_A, "PLACE", salle1, matin, g1a, semaineLundiMercredi());
        jdbc.update("UPDATE patients SET date_admission = ? WHERE id = ?", java.sql.Date.valueOf(jour(-400)), id);
        lecture("fiche_placee", lireLaFiche(id, CENTRE_A, adminA()));
        jdbc.update("UPDATE gmao_equipements SET statut = 'HORS_SERVICE' WHERE id = ?", g1a);
        lecture("fiche_generateur_hors_service", lireLaFiche(id, CENTRE_A, adminA()));
        jdbc.update("UPDATE gmao_equipements SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", g1a);
        lecture("fiche_generateur_supprime", lireLaFiche(id, CENTRE_A, adminA()));
    }

    private com.hemodialyse.backend.domain.planning.model.JourSemaine[] semaineLundiMercredi() {
        return new com.hemodialyse.backend.domain.planning.model.JourSemaine[]{
                com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI,
                com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI};
    }

    @Test
    void les_refus_de_lecture_de_la_fiche_inconnue_autre_centre_parametres_manquants_ou_illisibles() throws Exception {
        UUID id = patientSansPlace("P1");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fiche connue", lireLaFiche(id, CENTRE_A, adminA()).status());
        m.put("identifiant inconnu", refus(lireLaFiche(UUID.nameUUIDFromBytes("inconnu".getBytes()), CENTRE_A, adminA())));
        m.put("centre B demandé pour un patient du centre A (ADMIN du centre A)", refus(lireLaFiche(id, CENTRE_B, adminA())));
        m.put("centre A demandé par l'ADMIN du centre B", lireLaFiche(id, CENTRE_A, adminB()).status());
        m.put("centre B demandé par l'ADMIN du centre B pour un patient du centre A", refus(lireLaFiche(id, CENTRE_B, adminB())));
        m.put("sans centerId", refus(doGet(PATIENTS + "/" + id, adminA())));
        m.put("centerId illisible", refus(doGet(PATIENTS + "/" + id + "?centerId=x", adminA())));
        m.put("identifiant illisible", refus(doGet(PATIENTS + "/pas-un-uuid?centerId=" + CENTRE_A, adminA())));
        m.put("userId inutilisé accepté", doGet(PATIENTS + "/" + id + "?centerId=" + CENTRE_A + "&userId=n-importe-quoi", adminA()).status());
        arbre("refus_de_lecture_nouveau_code", m);
    }

    @Test
    void une_ligne_heritee_aux_colonnes_nulles_se_lit_se_liste_et_se_modifie() throws Exception {
        UUID id = UUID.nameUUIDFromBytes("pc1-ligne-heritee".getBytes());
        aliasNommes.put(id.toString(), "<LIGNE_HERITEE>");
        jdbc.update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient) "
                + "VALUES (?, ?, 'Heritee', 'Ligne', 'F', ?, 'PC1-HERITEE', 'NON_VACANCIER')", id, CENTRE_A, java.sql.Date.valueOf(jour(-400)));
        Map<String, Object> m = new LinkedHashMap<>();
        Resp lecture = lireLaFiche(id, CENTRE_A, adminA());
        m.put("lecture de la fiche", refus(lecture));
        if (lecture.status() == 200) {
            JsonNode c = corps(lecture);
            m.put("enSommeil", c.path("enSommeil").toString());
            m.put("etatPatient", c.path("etatPatient").toString());
            m.put("nombreEnfants", c.path("nombreEnfants").toString());
            m.put("joursDialyse", c.path("joursDialyse").toString());
            m.put("qualiteAssure", c.path("qualiteAssure").toString());
            m.put("sousKt", c.path("sousKt").toString());
        }
        Resp liste = recherche("{'centerId':'" + CENTRE_A + "','page':0,'size':20}", adminA());
        m.put("recherche", refus(liste));
        if (liste.status() == 200) {
            JsonNode i = corps(liste).path("items").path(0);
            m.put("recherche : etatPatient", i.path("etatPatient").toString());
            m.put("recherche : jourLundi", i.path("jourLundi").toString());
            m.put("recherche : nonFacturable", i.path("nonFacturable").toString());
        }
        m.put("résumé", refus(doGet(PATIENTS + "/summary?centerId=" + CENTRE_A, adminA())));
        m.put("détail du résumé", refus(doGet(PATIENTS + "/summary/details?centerId=" + CENTRE_A, adminA())));
        Resp mod = modifier(id, ficheMap("Heritee", "PC1-HERITEE"), adminA());
        m.put("modification", refus(mod));
        arbre("ligne_heritee_nouveau_code", m);
        etatDesMouvements("mouvements_nouveau_code");
    }

    // ------------------------------------------------------------------ recherche

    @Test
    void la_recherche_rend_les_lignes_de_la_liste_pagine_et_trie_par_nom_puis_prenom_par_defaut() throws Exception {
        septPatients();
        lecture("page_0_taille_3", recherche("{'centerId':'" + CENTRE_A + "','page':0,'size':3}", adminA()));
        Map<String, Object> m = new LinkedHashMap<>();
        for (int page = 0; page < 4; page++) {
            Resp r = recherche("{'centerId':'" + CENTRE_A + "','page':" + page + ",'size':3}", adminA());
            m.put("page " + page + " taille 3", Map.of("noms", noms(r), "total", corps(r).path("total").asInt(),
                    "page", corps(r).path("page").asInt(), "size", corps(r).path("size").asInt()));
        }
        arbre("pages", m);
    }

    @Test
    void les_bornes_de_pagination_et_les_corps_invalides_de_la_recherche() throws Exception {
        septPatients();
        Map<String, Object> m = new LinkedHashMap<>();
        String c = "'centerId':'" + CENTRE_A + "'";
        String[] corps = {"{" + c + "}", "{" + c + ",'page':0,'size':0}", "{" + c + ",'page':0,'size':1}", "{" + c + ",'page':0,'size':200}",
                "{" + c + ",'page':0,'size':201}", "{" + c + ",'page':-1,'size':20}", "{" + c + ",'page':9,'size':20}",
                "{'page':0,'size':20}", "{'centerId':null,'page':0,'size':20}", "{'centerId':'x','page':0,'size':20}", "", "{", "[]",
                "{" + c + ",'page':'a','size':20}", "{" + c + ",'page':0,'size':20,'nonFacturable':'peut-etre'}",
                "{" + c + ",'page':0,'size':20,'dateAdmissionFrom':'pas une date'}"};
        for (String b : corps) {
            Resp r = doPost(PATIENTS + "/search", json(b), adminA());
            m.put("corps " + (b.isEmpty() ? "vide" : b.replace(CENTRE_A.toString(), "<CENTRE_A>")),
                    r.status() == 200 ? "200 total=" + corps(r).path("total").asInt() + " items=" + corps(r).path("items").size() : refus(r));
        }
        arbre("bornes_nouveau_code", m);
    }

    @Test
    void les_tris_de_la_liste_par_colonne_et_leur_repli_sur_le_tri_par_defaut() throws Exception {
        septPatients();
        Map<String, Object> m = new LinkedHashMap<>();
        for (String col : List.of("code", "nom", "prenom", "sexe", "dateAdmission", "numeroAssurance", "etatPatient", "nonFacturable",
                "pecStatus", "joursDialyse", "medecinTraitantId", "positionId", "inconnue", "NOM")) {
            for (String sens : List.of("asc", "desc", "DESC", "n-importe-quoi")) {
                Resp r = recherche("{'centerId':'" + CENTRE_A + "','page':0,'size':50,'sortBy':'" + col + "','sortDirection':'" + sens + "'}", adminA());
                m.put(col + " " + sens, noms(r));
            }
        }
        m.put("sortBy seul", noms(recherche("{'centerId':'" + CENTRE_A + "','page':0,'size':50,'sortBy':'nom'}", adminA())));
        m.put("sortDirection seul", noms(recherche("{'centerId':'" + CENTRE_A + "','page':0,'size':50,'sortDirection':'desc'}", adminA())));
        arbre("tris", m);
    }

    @Test
    void les_filtres_de_la_recherche_sont_des_contenus_sans_casse_ou_des_listes_exactes_a_virgules() throws Exception {
        septPatients();
        Map<String, Object> m = new LinkedHashMap<>();
        String[] filtres = {"", "'nom':'alp'", "'nom':'ALPHA'", "'nom':'  alpha  '", "'nom':'a'", "'nom':'_'", "'nom':'%'", "'nom':'eta_'",
                "'nom':'eta%bis'", "'nom':''", "'nom':'   '", "'prenom':'ee'", "'prenom':'Z'", "'code':'pc1-c3'", "'code':'PC1'",
                "'numeroAssurance':'n-5'", "'numeroAssurance':'N-'", "'sexe':'F'", "'sexe':'f'", "'sexe':'M,F'", "'sexe':'m, f'",
                "'sexe':'M,'", "'sexe':','", "'sexe':'MASCULIN'", "'etatPatient':'PERMANENT'", "'etatPatient':'perm'",
                "'etatPatient':'PERMANENT,TRANSFERE'", "'etatPatient':'TRANSFERE,DECEDE,GREFFE,GUERRI'", "'etatPatient':'occasionnel,vacancier_local'",
                "'etatPatient':'VACANCIER'", "'etatPatient':'PERM,TRANSFERE'", "'nom':'a','sexe':'F','etatPatient':'PERMANENT'",
                "'nonFacturable':true", "'nonFacturable':false"};
        for (String f : filtres) m.put(f.isEmpty() ? "aucun filtre" : f, recherchePar(f));
        arbre("filtres", m);
    }

    @Test
    void le_filtre_de_date_d_admission_est_inclusif_et_les_bornes_inversees_sont_echangees() throws Exception {
        septPatients();
        Map<String, Object> m = new LinkedHashMap<>();
        String[][] bornes = {{"-30", null}, {null, "-30"}, {"-40", "-20"}, {"-20", "-40"}, {"-30", "-30"}, {"-29", "-21"}, {"1", null}, {null, "-401"}, {"-400", "-400"}};
        for (String[] b : bornes) {
            StringBuilder f = new StringBuilder();
            if (b[0] != null) f.append("'dateAdmissionFrom':'").append(jour(Integer.parseInt(b[0]))).append("',");
            if (b[1] != null) f.append("'dateAdmissionTo':'").append(jour(Integer.parseInt(b[1]))).append("',");
            f.setLength(f.length() - 1);
            m.put("du " + (b[0] == null ? "-" : "J" + b[0]) + " au " + (b[1] == null ? "-" : "J" + b[1]), recherchePar(f.toString()));
        }
        arbre("dates_d_admission", m);
    }

    @Test
    void le_filtre_facturable_s_appuie_sur_une_prise_en_charge_validee_seulement() throws Exception {
        septPatients();
        String[][] pec = {{"C1", "VALIDEE"}, {"C2", "validee"}, {"C3", "CREEE"}, {"C4", "REFUSEE"}, {"C5", "VALIDEE"}, {"C5", "CREEE"}};
        for (String[] p : pec) {
            jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, statut, date_debut_demande, date_fin_demande, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)", UUID.randomUUID(), CENTRE_A, idDe(p[0]), p[1],
                    java.sql.Date.valueOf(jour(-30)), java.sql.Date.valueOf(jour(300)));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nonFacturable=true", recherchePar("'nonFacturable':true"));
        m.put("nonFacturable=false", recherchePar("'nonFacturable':false"));
        Resp r = recherche("{'centerId':'" + CENTRE_A + "','page':0,'size':50}", adminA());
        List<String> lignes = new ArrayList<>();
        for (JsonNode i : corps(r).path("items")) {
            lignes.add(i.path("nom").asString() + " nonFacturable=" + i.path("nonFacturable") + " pecStatus=" + i.path("pecStatus"));
        }
        m.put("colonnes de la liste", lignes);
        arbre("facturable", m);
    }

    // ------------------------------------------------------------------ résumés

    /** Le mois courant dépend de l'exécution : masqué ; le statut et le corps restent verrouillés. */
    private void lectureSansMois(String name, Resp r) {
        String mois = YearMonth.now(FUSEAU_DU_CENTRE).toString();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        m.put("cacheControl", r.cacheControl());
        m.put("body", mapper.readTree(r.raw().replace(mois, "<MOIS_COURANT>")));
        arbre(name, m);
    }

    @Test
    void le_resume_compte_l_effectif_du_mois_par_sexe_age_et_catheter() throws Exception {
        septPatients();
        lectureSansMois("resume_du_mois_courant", doGet(PATIENTS + "/summary?centerId=" + CENTRE_A, adminA()));
        lectureSansMois("resume_du_mois_courant_explicite", doGet(PATIENTS + "/summary?centerId=" + CENTRE_A + "&month="
                + YearMonth.now(FUSEAU_DU_CENTRE), adminA()));
        lecture("resume_d_un_mois_avant_toute_admission", doGet(PATIENTS + "/summary?centerId=" + CENTRE_A + "&month=2020-01", adminA()));
        lecture("resume_d_un_mois_lointain", doGet(PATIENTS + "/summary?centerId=" + CENTRE_A + "&month=2099-12", adminA()));
        lecture("resume_de_l_autre_centre_nouveau_code",doGet(PATIENTS + "/summary?centerId=" + CENTRE_B, adminA()));
    }

    @Test
    void le_detail_du_resume_donne_la_raison_d_inclusion_de_chaque_patient() throws Exception {
        septPatients();
        lectureSansMois("detail_du_mois_courant", doGet(PATIENTS + "/summary/details?centerId=" + CENTRE_A, adminA()));
        lecture("detail_d_un_mois_lointain", doGet(PATIENTS + "/summary/details?centerId=" + CENTRE_A + "&month=2099-12", adminA()));
    }

    @Test
    void les_parametres_du_resume_manquants_ou_illisibles_sont_refuses() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("résumé sans centerId", refus(doGet(PATIENTS + "/summary", adminA())));
        m.put("résumé centerId illisible", refus(doGet(PATIENTS + "/summary?centerId=x", adminA())));
        m.put("résumé mois illisible", refus(doGet(PATIENTS + "/summary?centerId=" + CENTRE_A + "&month=abc", adminA())));
        m.put("résumé mois vide", doGet(PATIENTS + "/summary?centerId=" + CENTRE_A + "&month=", adminA()).status());
        m.put("résumé mois 13", refus(doGet(PATIENTS + "/summary?centerId=" + CENTRE_A + "&month=2026-13", adminA())));
        m.put("détail sans centerId", refus(doGet(PATIENTS + "/summary/details", adminA())));
        m.put("détail mois illisible", refus(doGet(PATIENTS + "/summary/details?centerId=" + CENTRE_A + "&month=abc", adminA())));
        arbre("parametres_nouveau_code", m);
    }

    @Test
    void le_resume_est_garde_en_cache_par_centre_et_par_mois_et_vide_par_une_creation_ou_une_modification() throws Exception {
        septPatients();
        String url = PATIENTS + "/summary?centerId=" + CENTRE_A;
        int avant = corps(doGet(url, adminA())).path("totalPatients").asInt();
        jdbc.update("UPDATE patients SET en_sommeil = FALSE, sous_kt = TRUE WHERE center_id = ?", CENTRE_A);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("après une écriture directe en base, résumé périmé (cache)", doGet(url, adminA()).raw().equals(doGet(url, adminA()).raw())
                && corps(doGet(url, adminA())).path("ktDistribution").path(0).path("count").asInt() == 0);
        creer("N", ficheMap("Nouveau", "PC1-CACHE-1"), adminA());
        m.put("après une création, total + 1", corps(doGet(url, adminA())).path("totalPatients").asInt() - avant);
        m.put("après une création, sous KT relu", corps(doGet(url, adminA())).path("ktDistribution").path(0).path("count").asInt());
        jdbc.update("UPDATE patients SET sous_kt = FALSE WHERE center_id = ?", CENTRE_A);
        modifier(idDe("C1"), ficheDuPatient("C1"), adminA());
        m.put("après une modification, sous KT relu", corps(doGet(url, adminA())).path("ktDistribution").path(0).path("count").asInt());
        arbre("cache_du_resume", m);
    }
}
