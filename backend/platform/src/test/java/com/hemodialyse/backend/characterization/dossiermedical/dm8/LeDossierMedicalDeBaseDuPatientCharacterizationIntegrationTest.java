package com.hemodialyse.backend.characterization.dossiermedical.dm8;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM8, lot 1 : dossier médical de base du patient ({@code GET / POST / PUT /api/v1/patients/{id}/dossier-medical}, table
 * {@code dossier_medical_patient}) : lecture (seule lecture auditée du contexte), enregistrement (POST et PUT sont le même « enregistrer »),
 * valeurs libres et bornes de colonnes, absence de garde de patient, droits, cloisonnement par centre, audit, écritures simultanées.
 */
class LeDossierMedicalDeBaseDuPatientCharacterizationIntegrationTest extends Dm8CharacterizationSupport {

    @Override
    protected String dossier() {
        return "dossier";
    }

    private String complet(UUID centre) {
        return corps("centerId", centre, "nephropathieInitiale", "Glomerulonephrite fictive", "dateMiseEnDialyse", fixe(10), "hepatiteBStatut",
                "NEGATIF", "hepatiteCStatut", "POSITIF", "observationGlobale", "Observation fictive", "conclusionMedicale", "Conclusion fictive");
    }

    @Test
    void la_lecture_du_dossier_de_base_ne_cree_rien_et_un_dossier_absent_est_un_404_sans_corps() throws Exception {
        UUID patient = patientDm8(CENTRE_A, "PAT_A");
        dossierEnBase(CENTRE_A, patient, "DOS_A", "Nephropathie diabetique fictive", fixe(30), "NEGATIF", "POSITIF", "Observation fictive", "Conclusion fictive");
        UUID sans = patientDm8(CENTRE_A, "PAT_SANS");
        UUID patientB = patientDm8(CENTRE_B, "PAT_B");
        dossierEnBase(CENTRE_B, patientB, "DOS_B", "Nephropathie du centre B", null, null, null, null, null);
        String avant = empreinteLecteurs();
        Resp r = doGet(dossierApi(patient), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 reponse MEDECIN", reponse(r));
        m.put("02 en-tete Cache-Control", r.cacheControl());
        m.put("04 avec le centerId de la session", reponse(doGet(dossierApi(patient) + "?centerId=" + CENTRE_A, medecinA())).get("status"));
        m.put("05 patient sans dossier", reponse(doGet(dossierApi(sans), medecinA())));
        m.put("06 patient inconnu", reponse(doGet(dossierApi(idStable("patient", "inconnu")), medecinA())));
        m.put("07 dossier du centre B lu par la session A", reponse(doGet(dossierApi(patientB), medecinA())));
        m.put("08 dossier du centre B lu par la session B (colonnes absentes : nulles)", reponse(doGet(dossierApi(patientB), medecinB())));
        m.put("09 aucune ecriture", empreinteLecteurs().equals(avant));
        arbre("lecture", m);
    }

    @Test
    void le_premier_enregistrement_cree_le_dossier_et_chaque_enregistrement_suivant_le_remplace_en_entier() throws Exception {
        UUID patient = patientDm8(CENTRE_A, "PAT_A");
        Map<String, Object> m = new LinkedHashMap<>();
        Resp c = doPost(dossierApi(patient), complet(CENTRE_A), medecinA());
        aliasNommes.put(idDe(c), "<DOSSIER>");
        m.put("01 premier POST (cree)", reponse(c));
        m.put("02 etat", etatDesDossiers());
        m.put("03 second POST avec un seul champ (les autres sont effaces, l'identifiant est conserve)", reponse(doPost(dossierApi(patient),
                corps("centerId", CENTRE_A, "nephropathieInitiale", "Seul champ"), medecinA())));
        m.put("04 etat", etatDesDossiers());
        m.put("05 PUT complet (meme effet que POST)", reponse(doPut(dossierApi(patient), complet(CENTRE_A), medecinA())));
        m.put("06 relecture", reponse(doGet(dossierApi(patient), medecinA())));
        UUID sans = patientDm8(CENTRE_A, "PAT_PUT");
        Resp p = doPut(dossierApi(sans), complet(CENTRE_A), medecinA());
        aliasNommes.put(idDe(p), "<DOSSIER_PUT>");
        m.put("07 PUT sur un patient sans dossier (cree aussi)", reponse(p));
        UUID corpsVide = patientDm8(CENTRE_A, "PAT_VIDE");
        Resp v = doPost(dossierApi(corpsVide), "{}", medecinA());
        aliasNommes.put(idDe(v), "<DOSSIER_VIDE>");
        m.put("08 POST d'un objet vide (cree un dossier sans aucune valeur)", reponse(v));
        m.put("09 etat final", etatDesDossiers());
        arbre("enregistrement", m);
    }

    @Test
    void le_dossier_conserve_sa_creation_et_son_identifiant_quand_il_est_remplace() throws Exception {
        UUID patient = patientDm8(CENTRE_A, "PAT_A");
        dossierEnBase(CENTRE_A, patient, "DOS_A", "Avant", fixe(5), "NEGATIF", "NEGATIF", "Observation avant", "Conclusion avant");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 etat avant", etatDesDossiers());
        m.put("02 POST de remplacement", reponse(doPost(dossierApi(patient), corps("centerId", CENTRE_A, "nephropathieInitiale", "Apres"), medecinA())));
        m.put("03 etat apres (creation inchangee, ligne modifiee)", etatDesDossiers());
        arbre("remplacement", m);
    }

    @Test
    void les_statuts_d_hepatite_sont_libres_les_colonnes_ont_des_bornes_et_les_corps_illisibles_sont_des_500() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        String[][] cas = {
                {"hepatiteBStatut libre (valeur quelconque)", corps("hepatiteBStatut", "N'IMPORTE QUOI")},
                {"hepatiteBStatut de 20 caracteres", corps("hepatiteBStatut", "x".repeat(20))},
                {"hepatiteBStatut de 21 caracteres", corps("hepatiteBStatut", "x".repeat(21))},
                {"hepatiteCStatut de 21 caracteres", corps("hepatiteCStatut", "x".repeat(21))},
                {"nephropathieInitiale de 255 caracteres", corps("nephropathieInitiale", "n".repeat(255))},
                {"nephropathieInitiale de 256 caracteres", corps("nephropathieInitiale", "n".repeat(256))},
                {"observationGlobale de 20 000 caracteres (TEXT)", corps("observationGlobale", "o".repeat(20000))},
                {"conclusionMedicale vide (chaine vide conservee)", corps("conclusionMedicale", "")},
                {"dateMiseEnDialyse tres lointaine dans le futur", "{\"dateMiseEnDialyse\":\"2999-01-01\"}"},
                {"dateMiseEnDialyse illisible", "{\"dateMiseEnDialyse\":\"pas une date\"}"},
                {"dateMiseEnDialyse inexistante (30 fevrier)", "{\"dateMiseEnDialyse\":\"2026-02-30\"}"},
                {"champ inconnu ignore", "{\"champInconnu\":\"x\",\"nephropathieInitiale\":\"Connue\"}"},
                {"corps illisible", "{pas du json"},
                {"corps absent", ""},
                {"corps null", "null"},
                {"corps tableau", "[]"},
                {"centerId illisible", "{\"centerId\":\"abc\"}"},
                {"nephropathieInitiale de type nombre", "{\"nephropathieInitiale\":12}"},
        };
        int i = 0;
        for (String[] c : cas) {
            nettoyerDm8();
            UUID patient = patientDm8(CENTRE_A, "PAT_A");
            Resp r = doPost(dossierApi(patient), c[1], medecinA());
            m.put(String.format("%02d %s", ++i, c[0]), refus(r) + " ; lignes=" + nombreDe("dossier_medical_patient", CENTRE_A));
        }
        arbre("valeurs_et_bornes", m);
    }

    @Test
    void aucune_garde_de_patient_un_patient_inconnu_ou_d_un_autre_centre_recoit_un_dossier() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        UUID inconnu = idStable("patient", "inconnu");
        aliasNommes.put(inconnu.toString(), "<PATIENT_INCONNU>");
        Resp o = doPost(dossierApi(inconnu), complet(CENTRE_A), medecinA());
        aliasNommes.put(idDe(o), "<DOSSIER_ORPHELIN>");
        m.put("01 POST pour un patient inconnu", reponse(o));
        UUID patientA = patientDm8(CENTRE_A, "PAT_A");
        UUID patientB = patientDm8(CENTRE_B, "PAT_B");
        dossierEnBase(CENTRE_A, patientA, "DOS_A", "Dossier du centre A", null, null, null, null, null);
        m.put("02 la session B ecrit pour un patient du centre A qui a deja un dossier", refus(doPost(dossierApi(patientA), complet(CENTRE_B), medecinB())));
        m.put("03 etat apres l'ecriture croisee", etatDesDossiers());
        UUID patientA2 = patientDm8(CENTRE_A, "PAT_A2");
        Resp x = doPost(dossierApi(patientA2), complet(CENTRE_B), medecinB());
        aliasNommes.put(idDe(x), "<DOSSIER_CROISE>");
        m.put("04 la session B ecrit pour un patient du centre A sans dossier", reponse(x));
        m.put("05 la session A relit ce patient (le dossier est dans B : 404)", reponse(doGet(dossierApi(patientA2), medecinA())).get("status"));
        m.put("06 la session B relit ce patient du centre A (200)", reponse(doGet(dossierApi(patientA2), medecinB())).get("status"));
        m.put("07 la session A ecrit ensuite pour ce patient (second dossier, meme patient)", refus(doPost(dossierApi(patientA2), complet(CENTRE_A), medecinA())));
        m.put("08 etat final", etatDesDossiers());
        m.put("09 patient du centre B inchange", nombreDe("dossier_medical_patient", CENTRE_B));
        arbre("sans_garde_de_patient", m);
    }

    @Test
    void les_droits_par_profil_sur_les_trois_routes() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm8().entrySet()) {
            nettoyerDm8();
            UUID patient = patientDm8(CENTRE_A, "PAT_A");
            dossierEnBase(CENTRE_A, patient, "DOS_A", "Dossier fictif", null, null, null, null, null);
            Map<String, String> routes = new LinkedHashMap<>();
            routes.put("01 GET dossier-medical", statutEtCode(doGet(dossierApi(patient) + "?centerId=" + CENTRE_A, p.getValue())));
            routes.put("02 POST dossier-medical", statutEtCode(doPost(dossierApi(patient), complet(CENTRE_A), p.getValue())));
            routes.put("03 PUT dossier-medical", statutEtCode(doPut(dossierApi(patient), complet(CENTRE_A), p.getValue())));
            profils.put(p.getKey(), regrouper(routes));
        }
        nettoyerDm8();
        UUID patient = patientDm8(CENTRE_A, "PAT_A");
        Map<String, String> anonyme = new LinkedHashMap<>();
        anonyme.put("lecture", statutEtCode(appel("GET", dossierApi(patient), null, null)));
        anonyme.put("ecriture POST", statutEtCode(appel("POST", dossierApi(patient), complet(CENTRE_A), null)));
        anonyme.put("ecriture PUT", statutEtCode(appel("PUT", dossierApi(patient), complet(CENTRE_A), null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
    }

    @Test
    void un_refus_de_droit_n_ecrit_rien_et_le_403_est_rendu_avant_ou_apres_la_lecture_du_corps() throws Exception {
        UUID patient = patientDm8(CENTRE_A, "PAT_A");
        dossierEnBase(CENTRE_A, patient, "DOS_A", "Dossier fictif", null, null, null, null, null);
        String avant = empreinteLecteurs();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("INFIRMIER : POST valide", statutEtCode(doPost(dossierApi(patient), complet(CENTRE_A), infirmierA())));
        cas.put("INFIRMIER : POST avec un corps illisible", statutEtCode(doPost(dossierApi(patient), "{pas du json", infirmierA())));
        cas.put("INFIRMIER : PUT avec un corps absent", statutEtCode(doPut(dossierApi(patient), "", infirmierA())));
        cas.put("ADMIN : POST valide", statutEtCode(doPost(dossierApi(patient), complet(CENTRE_A), adminA2())));
        cas.put("ADMIN : PUT avec un corps illisible", statutEtCode(doPut(dossierApi(patient), "[", adminA2())));
        cas.put("SECRETAIRE : lecture", statutEtCode(doGet(dossierApi(patient), as(CENTRE_A, "SECRETAIRE"))));
        cas.put("DIRECTION : lecture", statutEtCode(doGet(dossierApi(patient), direction())));
        cas.put("INFIRMIER : lecture (autorisee : isolation machine)", statutEtCode(doGet(dossierApi(patient), infirmierA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("aucune ecriture", empreinteLecteurs().equals(avant));
        arbre("refus", m);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session() throws Exception {
        UUID patientA = patientDm8(CENTRE_A, "PAT_A");
        UUID patientB = patientDm8(CENTRE_B, "PAT_B");
        dossierEnBase(CENTRE_A, patientA, "DOS_A", "Dossier A", null, null, null, null, null);
        dossierEnBase(CENTRE_B, patientB, "DOS_B", "Dossier B", null, null, null, null, null);
        String avant = empreinteLecteurs();
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> a = new LinkedHashMap<>();
        a.put("01 GET centerId=B", refus(doGet(dossierApi(patientB) + "?centerId=" + CENTRE_B, medecinA())));
        a.put("02 POST centerId=B", refus(doPost(dossierApi(patientB), complet(CENTRE_B), medecinA())));
        a.put("03 PUT centerId=B", refus(doPut(dossierApi(patientB), complet(CENTRE_B), medecinA())));
        a.put("04 GET centerId inconnu", refus(doGet(dossierApi(patientA) + "?centerId=" + idStable("centre", "inconnu"), medecinA())));
        a.put("05 POST sans centerId (le centre de la session est retenu)", refus(doPost(dossierApi(patientA), "{\"nephropathieInitiale\":\"Sans centre\"}", medecinA())));
        m.put("01 session A vise le centre B", a);
        Map<String, String> b = new LinkedHashMap<>();
        b.put("01 MEDECIN sans centre de session : lecture", refus(doGet(dossierApi(patientA), as(null, "MEDECIN"))));
        b.put("02 MEDECIN sans centre de session avec centerId A : lecture", refus(doGet(dossierApi(patientA) + "?centerId=" + CENTRE_A, as(null, "MEDECIN"))));
        b.put("03 MEDECIN sans centre de session : ecriture", refus(doPost(dossierApi(patientA), complet(CENTRE_A), as(null, "MEDECIN"))));
        b.put("04 SUPERADMIN + MEDECIN (session A) lit le centre B", refus(doGet(dossierApi(patientB) + "?centerId=" + CENTRE_B,
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        b.put("05 SUPERADMIN + MEDECIN (session A) ecrit dans le centre B", refus(doPost(dossierApi(patientB), complet(CENTRE_B),
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        m.put("02 sessions particulieres", b);
        m.put("03 le dossier du centre B est intact", etatDesDossiers().stream().filter(l -> CENTRE_B.toString().equals(l.get("center_id"))).toList());
        m.put("04 le seul changement est celui de la requete 05 (centre de la session)", !empreinteLecteurs().equals(avant));
        arbre("cloisonnement", m);
    }

    @Test
    void la_lecture_du_dossier_est_la_seule_lecture_journalisee_et_les_ecritures_et_refus_le_sont() throws Exception {
        UUID patient = patientDm8(CENTRE_A, "PAT_A");
        UUID sans = patientDm8(CENTRE_A, "PAT_SANS");
        dossierEnBase(CENTRE_A, patient, "DOS_A", "Dossier fictif", null, null, null, null, null);
        monterLAudit();
        doGet(dossierApi(patient), medecinA());
        doGet(dossierApi(patient), infirmierA());
        doGet(dossierApi(sans), medecinA());
        doGet(dossierApi(idStable("patient", "inconnu")), medecinA());
        doGet(dossierApi(patient), as(CENTRE_A, "SECRETAIRE"));
        doPost(dossierApi(patient), complet(CENTRE_A), medecinA());
        doPut(dossierApi(patient), complet(CENTRE_A), medecinA());
        doPut(dossierApi(sans), complet(CENTRE_A), medecinA());
        doPost(dossierApi(patient), complet(CENTRE_A), infirmierA());
        doPut(dossierApi(patient), "{pas du json", medecinA());
        doGet(statsApi(patient, "medical") + "?centerId=" + CENTRE_A, medecinA());
        doGet(fhirApi(patient), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }

    @Test
    void deux_enregistrements_simultanes_d_un_patient_sans_dossier_et_la_derniere_ecriture_gagnante() throws Exception {
        UUID patient = patientDm8(CENTRE_A, "PAT_A");
        List<Resp> reponses = enParallele(() -> doPost(dossierApi(patient), corps("centerId", CENTRE_A, "nephropathieInitiale", "Premier"), medecinA()),
                () -> doPost(dossierApi(patient), corps("centerId", CENTRE_A, "nephropathieInitiale", "Second"), medecinA()));
        List<Integer> statuts = reponses.stream().map(Resp::status).toList();
        int lignes = nombreDe("dossier_medical_patient", CENTRE_A);
        Map<String, Object> m = new LinkedHashMap<>();
        // Invariants seulement : H2 n'est pas le SGBD de production ; l'ordre d'arrivee des deux requetes n'est pas deterministe.
        m.put("01 chaque requete rend 200 ou une erreur 5xx (aucun controle de version)", statuts.stream().allMatch(s -> s == 200 || s >= 500));
        m.put("02 au moins un enregistrement a reussi", statuts.stream().anyMatch(s -> s == 200));
        m.put("03 une seule ligne par patient", lignes == 1);
        UUID autre = patientDm8(CENTRE_A, "PAT_B");
        m.put("04 premiere ecriture", resume(doPost(dossierApi(autre), corps("centerId", CENTRE_A, "nephropathieInitiale", "Premiere", "observationGlobale", "Observation 1"),
                medecinA()), "patientId"));
        m.put("05 seconde ecriture sans lecture prealable : la derniere gagne, les champs absents sont effaces", resume(doPost(dossierApi(autre),
                corps("centerId", CENTRE_A, "nephropathieInitiale", "Seconde"), medecinA()), "patientId"));
        m.put("06 relecture", resume(doGet(dossierApi(autre), medecinA()), "nephropathieInitiale", "observationGlobale"));
        arbre("ecritures_simultanees", m);
    }

    @Test
    void un_doublon_de_dossier_pour_un_patient_et_un_centre_est_refuse_par_la_base_ou_lu_sans_erreur() throws Exception {
        UUID patient = patientDm8(CENTRE_A, "PAT_A");
        dossierEnBase(CENTRE_A, patient, "DOS_1", "Premier", null, null, null, null, null);
        Map<String, Object> m = new LinkedHashMap<>();
        boolean accepte;
        try {
            dossierEnBase(CENTRE_A, patient, "DOS_2", "Second", null, null, null, null, null);
            accepte = true;
        } catch (org.springframework.dao.DataAccessException e) {
            accepte = false;
        }
        m.put("01 la base accepte un second dossier pour le meme patient", accepte);
        m.put("02 lecture", reponse(doGet(dossierApi(patient), medecinA())).get("status"));
        m.put("03 lignes", nombreDe("dossier_medical_patient", CENTRE_A));
        List<String> colonnesNulles = new ArrayList<>();
        UUID autre = patientDm8(CENTRE_A, "PAT_B");
        jdbc.update("INSERT INTO dossier_medical_patient (id, patient_id, center_id) VALUES (?,?,?)", idStable("dossier", "SANS_DATES"), autre, CENTRE_A);
        colonnesNulles.add(String.valueOf(reponse(doGet(dossierApi(autre), medecinA()))));
        m.put("04 ligne sans aucune date ni valeur (creation et modification nulles)", colonnesNulles);
        arbre("lignes_illisibles", m);
    }
}
