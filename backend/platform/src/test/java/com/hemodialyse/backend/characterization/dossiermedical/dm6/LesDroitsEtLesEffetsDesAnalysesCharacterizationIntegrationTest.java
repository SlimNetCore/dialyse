package com.hemodialyse.backend.characterization.dossiermedical.dm6;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM6, lot 2 (analyses) : droits par profil, cloisonnement des centres et des patients, audit, diffusions, notifications, et les
 * lecteurs du bilan observés sans les modifier (suivi de l'anémie DM2, statistiques médicales et export DM8, KDIGO DM7, export FHIR DM8).
 */
class LesDroitsEtLesEffetsDesAnalysesCharacterizationIntegrationTest extends Dm6CharacterizationSupport {

    @Override
    protected String dossier() {
        return "analyses/droits";
    }

    private UUID patientA;
    private UUID patientB;
    private UUID analyseA;

    private void deuxCentresEtUnBilan() {
        patientA = patientDm6(CENTRE_A, "PAT_A");
        patientB = patientDm6(CENTRE_B, "PAT_B");
        analyseA = analyseEnBase(CENTRE_A, patientA, "ANALYSE_A", fixe(10), "hb_g_dl", "10");
    }

    private String nouveau(UUID centre, int hb) {
        return corps("centerId", centre, "datePrelevement", fixe(20), "hbGDl", hb);
    }

    private String recherche(UUID centre, UUID patient) {
        return corps("centerId", centre, "patientId", patient, "page", 0, "size", 10);
    }

    @Test
    void les_droits_par_profil_sur_les_cinq_routes() throws Exception {
        deuxCentresEtUnBilan();
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm6().entrySet()) {
            nettoyerDm6();
            deuxCentresEtUnBilan();
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("liste", statutEtCode(doGet(analysesApi(patientA), p.getValue())));
            ligne.put("recherche", statutEtCode(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA), p.getValue())));
            ligne.put("creation", statutEtCode(doPost(analysesApi(patientA), nouveau(CENTRE_A, 1), p.getValue())));
            ligne.put("modification", statutEtCode(doPut(analyseApi(patientA, analyseA), nouveau(CENTRE_A, 2), p.getValue())));
            ligne.put("suppression", statutEtCode(doDelete(analyseApi(patientA, analyseA), p.getValue())));
            profils.put(p.getKey(), ligne);
        }
        nettoyerDm6();
        deuxCentresEtUnBilan();
        Map<String, Object> anonyme = new LinkedHashMap<>();
        anonyme.put("liste", statutEtCode(doGetAnonymous(analysesApi(patientA))));
        anonyme.put("recherche", statutEtCode(appel("POST", RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA), null)));
        anonyme.put("creation", statutEtCode(appel("POST", analysesApi(patientA), nouveau(CENTRE_A, 1), null)));
        anonyme.put("modification", statutEtCode(appel("PUT", analyseApi(patientA, analyseA), nouveau(CENTRE_A, 2), null)));
        anonyme.put("suppression", statutEtCode(appel("DELETE", analyseApi(patientA, analyseA), null, null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
        assertThat(nombreDe("resultats_analyses", CENTRE_A)).isEqualTo(1);
    }

    @Test
    void un_refus_de_droit_n_ecrit_rien_et_precede_la_lecture_du_corps() throws Exception {
        deuxCentresEtUnBilan();
        String avant = empreinte("resultats_analyses");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("INFIRMIER : creation", statutEtCode(doPost(analysesApi(patientA), nouveau(CENTRE_A, 3), as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : creation avec un corps illisible", statutEtCode(doPost(analysesApi(patientA), "{pas du json", as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : modification avec un corps absent", statutEtCode(doPut(analyseApi(patientA, analyseA), "", as(CENTRE_A, "INFIRMIER"))));
        cas.put("ADMIN : creation", statutEtCode(doPost(analysesApi(patientA), nouveau(CENTRE_A, 4), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : modification", statutEtCode(doPut(analyseApi(patientA, analyseA), nouveau(CENTRE_A, 5), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : suppression", statutEtCode(doDelete(analyseApi(patientA, analyseA), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : recherche avec un corps illisible", statutEtCode(doPost(RECHERCHE_ANALYSES, "{pas du json", as(CENTRE_A, "ADMIN"))));
        cas.put("INFIRMIER : recherche avec un corps illisible", statutEtCode(doPost(RECHERCHE_ANALYSES, "{pas du json", as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : liste avec une pagination illisible", statutEtCode(doGet(analysesApi(patientA) + "?size=abc", as(CENTRE_A, "INFIRMIER"))));
        cas.put("DIRECTION : creation", statutEtCode(doPost(analysesApi(patientA), nouveau(CENTRE_A, 6), direction())));
        arbre("refus", cas);
        assertThat(empreinte("resultats_analyses")).isEqualTo(avant);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session_et_aucune_garde_de_patient() throws Exception {
        deuxCentresEtUnBilan();
        UUID analyseB = analyseEnBase(CENTRE_B, patientB, "ANALYSE_B", fixe(11), "hb_g_dl", "20");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 liste avec centerId du centre B (session A)", statutEtCode(doGet(analysesApi(patientB) + "?centerId=" + CENTRE_B, medecinA())));
        cas.put("02 recherche avec le centre B (session A)", statutEtCode(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_B, patientB), medecinA())));
        cas.put("03 creation avec le centre B dans le corps (session A)", statutEtCode(doPost(analysesApi(patientB), nouveau(CENTRE_B, 7), medecinA())));
        cas.put("04 creation sans centre dans le corps (centre de la session)", statutEtCode(doPost(analysesApi(patientA),
                corps("datePrelevement", fixe(20), "hbGDl", 8), medecinA())));
        cas.put("05 modification avec le centre B (session A)", statutEtCode(doPut(analyseApi(patientB, analyseB), nouveau(CENTRE_B, 9), medecinA())));
        cas.put("06 suppression avec centerId B (session A)", statutEtCode(doDelete(analyseApi(patientB, analyseB) + "?centerId=" + CENTRE_B, medecinA())));
        cas.put("07 MEDECIN sans centre de session : liste", statutEtCode(doGet(analysesApi(patientA), as(null, "MEDECIN"))));
        cas.put("08 MEDECIN sans centre de session : creation", statutEtCode(doPost(analysesApi(patientA), nouveau(CENTRE_A, 10), as(null, "MEDECIN"))));
        cas.put("09 SUPERADMIN + MEDECIN lit le centre B depuis la session A", statutEtCode(doGet(analysesApi(patientB) + "?centerId=" + CENTRE_B,
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("10 SUPERADMIN + MEDECIN cree dans le centre B", statutEtCode(doPost(analysesApi(patientB), nouveau(CENTRE_B, 11),
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("11 session B lit le patient du centre A (liste vide)", statutEtCode(doGet(analysesApi(patientA), as(CENTRE_B, "MEDECIN"))));
        cas.put("12 session B ecrit pour le patient du centre A", statutEtCode(doPost(analysesApi(patientA), nouveau(CENTRE_B, 12), as(CENTRE_B, "MEDECIN"))));
        cas.put("13 session A ecrit pour un patient inconnu (aucune garde de patient)", statutEtCode(doPost(analysesApi(idStable("patient", "inconnu")),
                nouveau(CENTRE_A, 13), medecinA())));
        cas.put("14 session A ecrit pour le patient du centre B (aucune garde de patient)", statutEtCode(doPost(analysesApi(patientB),
                nouveau(CENTRE_A, 14), medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAnalyses());
        arbre("cloisonnement", m);
    }

    @Test
    void les_ecritures_et_leurs_refus_sont_journalises_sous_PATIENTS_et_les_lectures_ne_le_sont_pas() throws Exception {
        deuxCentresEtUnBilan();
        monterLAudit();
        Resp c = doPost(analysesApi(patientA), nouveau(CENTRE_A, 15), medecinA());
        UUID s = UUID.fromString(idDe(c));
        aliasNommes.put(s.toString(), "<ANALYSE>");
        aliasNommes.put(idStable("analyse", "inconnue").toString(), "<ANALYSE_INCONNUE>");
        doPost(analysesApi(patientA), "{\"centerId\":\"" + CENTRE_A + "\",\"hbGDl\":\"onze\"}", medecinA());
        doPut(analyseApi(patientA, s), nouveau(CENTRE_A, 16), medecinA());
        doPut(analyseApi(patientA, idStable("analyse", "inconnue")), nouveau(CENTRE_A, 17), medecinA());
        doGet(analysesApi(patientA), medecinA());
        doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA), medecinA());
        doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA), as(CENTRE_A, "ADMIN"));
        doPost(analysesApi(patientA), nouveau(CENTRE_A, 18), as(CENTRE_A, "INFIRMIER"));
        doDelete(analyseApi(patientA, s), as(CENTRE_A, "ADMIN"));
        doDelete(analyseApi(patientA, s), medecinA());
        doDelete(analyseApi(patientA, s), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }

    @Test
    void le_bilan_alimente_le_suivi_de_l_anemie_les_statistiques_medicales_et_l_export_csv() throws Exception {
        deuxCentresEtUnBilan();
        jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ?", CENTRE_A);
        analyseEnBase(CENTRE_A, patientA, "ANALYSE_1", fixe(10), "hb_g_dl", "10", "ferritine_ng_ml", "300", "cstf_pct", "20", "albumine_g_dl", "3.5",
                "kt_v_mensuel", "1.1", "phosphore_mg_dl", "5.5", "calcium_mg_dl", "9");
        analyseEnBase(CENTRE_A, patientA, "ANALYSE_2", fixe(40), "hb_g_dl", "12", "ferritine_ng_ml", "500", "kt_v_mensuel", "1.5");
        analyseEnBase(CENTRE_A, patientB, "ANALYSE_AUTRE_PATIENT", fixe(50), "hb_g_dl", "99");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 suivi de l'anemie", reponse(doGet(PATIENTS_API + "/" + patientA + "/suivi-anemie", medecinA())));
        m.put("02 statistiques medicales", reponse(doGet(PATIENTS_API + "/" + patientA + "/stats/medical?centerId=" + CENTRE_A, medecinA())));
        m.put("03 statistiques medicales sur une periode", reponse(doGet(PATIENTS_API + "/" + patientA + "/stats/medical?centerId=" + CENTRE_A + "&from="
                + fixe(30) + "&to=" + fixe(45), medecinA())));
        Resp csv = doGet(PATIENTS_API + "/" + patientA + "/stats/export?centerId=" + CENTRE_A + "&format=csv", adminA());
        m.put("04 export csv (ADMIN)", Map.of("status", csv.status(), "type", String.valueOf(csv.contentType()), "texte", csv.raw()));
        m.put("05 export csv pour le MEDECIN", refus(doGet(PATIENTS_API + "/" + patientA + "/stats/export?centerId=" + CENTRE_A + "&format=csv", medecinA())));
        arbre("lecteurs", m);
    }

    @Test
    void chaque_ecriture_du_bilan_change_aussitot_ce_que_lisent_l_anemie_kdigo_et_les_statistiques() throws Exception {
        deuxCentresEtUnBilan();
        jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ?", CENTRE_A);
        jdbc.update("UPDATE patients SET date_naissance = ?, sexe = 'M' WHERE id = ?", java.sql.Date.valueOf(java.time.LocalDate.now().minusYears(50).minusDays(30)),
                patientA);
        List<Object> etapes = new ArrayList<>();
        etapes.add(Map.of("00 avant toute saisie", lecteurs()));
        Resp c = doPost(analysesApi(patientA), corps("centerId", CENTRE_A, "datePrelevement", fixe(10), "hbGDl", 10, "ferritineNgMl", 300,
                "creatinineMgDl", 8), medecinA());
        UUID id = UUID.fromString(idDe(c));
        aliasNommes.put(id.toString(), "<ANALYSE>");
        etapes.add(Map.of("01 apres la creation (creatinine 8)", lecteurs()));
        doPut(analyseApi(patientA, id), corps("centerId", CENTRE_A, "datePrelevement", fixe(10), "hbGDl", 12), medecinA());
        etapes.add(Map.of("02 apres la modification (hemoglobine seule : creatinine et ferritine effacees)", lecteurs()));
        doDelete(analyseApi(patientA, id), medecinA());
        etapes.add(Map.of("03 apres la suppression", lecteurs()));
        arbre("effets", etapes);
    }

    private Map<String, Object> lecteurs() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("anemie", reponse(doGet(PATIENTS_API + "/" + patientA + "/suivi-anemie", medecinA())));
        Resp stats = doGet(PATIENTS_API + "/" + patientA + "/stats/medical?centerId=" + CENTRE_A, medecinA());
        m.put("statistiques", reponse(stats));
        m.put("kdigo", reponse(doGet(PATIENTS_API + "/" + patientA + "/greffe/kdigo", medecinA())));
        return m;
    }

    @Test
    void le_bilan_est_exporte_en_observations_fhir_locales_avec_les_observations_loinc() throws Exception {
        deuxCentresEtUnBilan();
        jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ?", CENTRE_A);
        monterLAudit();
        diffusions.clear();
        Resp c = doPost(analysesApi(patientA), corps("centerId", CENTRE_A, "datePrelevement", fixe(-60), "hbGDl", 11.5, "htPct", 34, "plaquettes", 220,
                "ferritineNgMl", 450, "cstfPct", 30, "ktVMensuel", 1.4, "phosphoreMgDl", 5.2, "calciumMgDl", 9.1, "pthPgMl", 300, "albumineGDl", 3.8,
                "crpMgL", 4, "creatinineMgDl", 8.5, "ureePreMgDl", 120), medecinA());
        aliasNommes.put(idDe(c), "<ANALYSE>");
        String export = PATIENTS_API + "/" + patientA + "/dossier-medical/export-fhir";
        Resp bundle = doGet(export, medecinA());
        List<String> observations = new ArrayList<>();
        corps(bundle).path("entry").forEach(e -> {
            JsonNode r = e.path("resource");
            if ("Observation".equals(r.path("resourceType").asString())) {
                observations.add(r.path("id").asString() + " | " + r.path("code").path("coding").path(0).path("system").asString() + " | "
                        + r.path("code").path("coding").path(0).path("code").asString() + " | " + r.path("valueQuantity").path("value").asString() + " "
                        + r.path("valueQuantity").path("unit").asString() + " | " + r.path("effectiveDateTime").asString());
            }
        });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("export MEDECIN", bundle.status());
        m.put("observations issues du bilan (creatinine, uree, plaquettes, proteines et EPO non exportees)", observations);
        m.put("diffusions", new ArrayList<>(diffusions));
        m.put("notifications", notificationsDurables());
        arbre("fhir", m);
    }
}
