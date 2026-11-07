package com.hemodialyse.backend.characterization.dossiermedical.dm6;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DM6, lot 2 (analyses), consultation, recherche et suppression : {@code GET} et {@code DELETE} sur
 * {@code /api/v1/patients/{id}/analyses} et {@code POST /api/v1/patients/analyses/search}.
 */
class LaConsultationEtLaRechercheDesAnalysesCharacterizationIntegrationTest extends Dm6CharacterizationSupport {

    @Override
    protected String dossier() {
        return "analyses/consultation";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm6(CENTRE_A, "PAT_A");
        patientB = patientDm6(CENTRE_B, "PAT_B");
    }

    private void cinqBilans() {
        for (int i = 1; i <= 5; i++) {
            analyseEnBase(CENTRE_A, patientA, "ANALYSE_" + i, fixe(i * 10), "hb_g_dl", String.valueOf(9 + i));
        }
        analyseEnBase(CENTRE_A, patientB, "ANALYSE_AUTRE_PATIENT", fixe(99), "hb_g_dl", "99");
        analyseEnBase(CENTRE_B, patientB, "ANALYSE_AUTRE_CENTRE", fixe(98), "hb_g_dl", "98");
    }

    private String recherche(UUID centre, UUID patient, Object page, Object size, Object from, Object to) {
        return corps("centerId", centre, "patientId", patient, "page", page, "size", size, "dateFrom", from, "dateTo", to);
    }

    @Test
    void la_liste_est_triee_de_la_date_la_plus_recente_et_paginee() throws Exception {
        deuxCentres();
        cinqBilans();
        String url = analysesApi(patientA);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("defaut", page(doGet(url, medecinA()), "datePrelevement", "hbGDl"));
        m.put("taille 2, page 0", page(doGet(url + "?size=2&page=0", medecinA()), "hbGDl"));
        m.put("taille 2, page 2", page(doGet(url + "?size=2&page=2", medecinA()), "hbGDl"));
        m.put("taille 2, page 9", page(doGet(url + "?size=2&page=9", medecinA()), "hbGDl"));
        m.put("taille 2, page -1", page(doGet(url + "?size=2&page=-1", medecinA()), "hbGDl"));
        m.put("taille 0", page(doGet(url + "?size=0", medecinA()), "hbGDl"));
        m.put("taille 1000", page(doGet(url + "?size=1000", medecinA()), "hbGDl"));
        m.put("taille illisible", refus(doGet(url + "?size=abc", medecinA())));
        m.put("liste pour l'ADMIN", page(doGet(url + "?size=1", as(CENTRE_A, "ADMIN")), "hbGDl"));
        m.put("liste d'un patient inconnu", page(doGet(analysesApi(idStable("patient", "inconnu")), medecinA()), "hbGDl"));
        m.put("liste du patient du centre B (session A)", page(doGet(analysesApi(patientB), medecinA()), "hbGDl"));
        arbre("pages", m);
    }

    @Test
    void les_bornes_de_periode_sont_inclusives_et_une_periode_inversee_est_refusee() throws Exception {
        deuxCentres();
        cinqBilans();
        String url = analysesApi(patientA);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from = date exacte", page(doGet(url + "?from=" + fixe(30), medecinA()), "datePrelevement"));
        m.put("to = date exacte", page(doGet(url + "?to=" + fixe(30), medecinA()), "datePrelevement"));
        m.put("from et to bornes incluses", page(doGet(url + "?from=" + fixe(20) + "&to=" + fixe(40), medecinA()), "datePrelevement"));
        m.put("from egal a to", page(doGet(url + "?from=" + fixe(20) + "&to=" + fixe(20), medecinA()), "datePrelevement"));
        m.put("from apres to", refus(doGet(url + "?from=" + fixe(40) + "&to=" + fixe(20), medecinA())));
        m.put("from illisible", refus(doGet(url + "?from=hier", medecinA())));
        arbre("periode", m);
    }

    @Test
    void la_lecture_rend_la_forme_complete_d_un_bilan() throws Exception {
        deuxCentres();
        Resp c = doPost(analysesApi(patientA), corps("centerId", CENTRE_A, "datePrelevement", fixe(-60), "hbGDl", 11.5, "htPct", 34, "plaquettes", 220,
                "ferritineNgMl", 450, "cstfPct", 30, "epoEndogeneMuiMl", 12, "ureePreMgDl", 120, "ureePostMgDl", 40, "creatinineMgDl", 8.5,
                "ktVMensuel", 1.4, "phosphoreMgDl", 5.2, "calciumMgDl", 9.1, "pthPgMl", 300, "albumineGDl", 3.8, "proteinesGDl", 6.9, "crpMgL", 4),
                medecinA());
        aliasNommes.put(idDe(c), "<ANALYSE_1>");
        analyseEnBase(CENTRE_A, patientA, "ANALYSE_2", fixe(-10), "hb_g_dl", "9.5");
        lecture("liste", doGet(analysesApi(patientA), medecinA()));
    }

    @Test
    void la_recherche_est_une_liste_filtree_par_patient_et_periode_sans_autre_critere() throws Exception {
        deuxCentres();
        cinqBilans();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 patient seul, taille 2", page(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA, 0, 2, null, null), medecinA()), "datePrelevement"));
        m.put("02 page 2 de taille 2", page(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA, 2, 2, null, null), medecinA()), "datePrelevement"));
        m.put("03 page au dela", page(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA, 9, 2, null, null), medecinA()), "datePrelevement"));
        m.put("04 periode", page(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA, 0, 10, fixe(20), fixe(40)), medecinA()), "datePrelevement"));
        m.put("05 taille 200 (maximum)", page(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA, 0, 200, null, null), medecinA()), "datePrelevement"));
        m.put("06 taille 201", refus(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA, 0, 201, null, null), medecinA())));
        m.put("07 taille 0", refus(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA, 0, 0, null, null), medecinA())));
        m.put("08 page negative", refus(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA, -1, 10, null, null), medecinA())));
        m.put("09 page et taille absentes (entiers obligatoires du corps)", refus(doPost(RECHERCHE_ANALYSES, corps("centerId", CENTRE_A, "patientId", patientA),
                medecinA())));
        m.put("10 patient absent", refus(doPost(RECHERCHE_ANALYSES, corps("centerId", CENTRE_A, "page", 0, "size", 10), medecinA())));
        m.put("11 centre absent", refus(doPost(RECHERCHE_ANALYSES, corps("patientId", patientA, "page", 0, "size", 10), medecinA())));
        m.put("12 periode inversee", refus(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA, 0, 10, fixe(40), fixe(20)), medecinA())));
        m.put("13 critere inconnu (motif de recherche) ignore", page(doPost(RECHERCHE_ANALYSES,
                "{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientA + "\",\"page\":0,\"size\":10,\"q\":\"' OR '1'='1\",\"sort\":\"hbGDl,asc\"}",
                medecinA()), "hbGDl"));
        m.put("14 patient du centre B (session A, centre A dans le corps)", page(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientB, 0, 10, null, null),
                medecinA()), "hbGDl"));
        m.put("15 centre B dans le corps (session A)", refus(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_B, patientB, 0, 10, null, null), medecinA())));
        m.put("16 patient inconnu", page(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, idStable("patient", "inconnu"), 0, 10, null, null), medecinA()),
                "hbGDl"));
        m.put("17 corps illisible", refus(doPost(RECHERCHE_ANALYSES, "{pas du json", medecinA())));
        m.put("18 ADMIN", page(doPost(RECHERCHE_ANALYSES, recherche(CENTRE_A, patientA, 0, 1, null, null), as(CENTRE_A, "ADMIN")), "hbGDl"));
        arbre("recherche", m);
    }

    @Test
    void la_suppression_rend_204_meme_quand_rien_n_est_supprime() throws Exception {
        deuxCentres();
        UUID a = analyseEnBase(CENTRE_A, patientA, "ANALYSE_A", fixe(10), "hb_g_dl", "10");
        UUID autrePatient = analyseEnBase(CENTRE_A, patientB, "ANALYSE_AUTRE_PATIENT", fixe(11), "hb_g_dl", "20");
        UUID autreCentre = analyseEnBase(CENTRE_B, patientB, "ANALYSE_AUTRE_CENTRE", fixe(12), "hb_g_dl", "30");
        aliasNommes.put(idStable("analyse", "inconnue").toString(), "<ANALYSE_INCONNUE>");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 bilan d'un autre patient du meme centre", refus(doDelete(analyseApi(patientA, autrePatient), medecinA())));
        cas.put("02 bilan d'un autre centre", refus(doDelete(analyseApi(patientB, autreCentre), medecinA())));
        cas.put("03 bilan inconnu", refus(doDelete(analyseApi(patientA, idStable("analyse", "inconnue")), medecinA())));
        cas.put("04 nombre de lignes apres les trois suppressions sans effet", String.valueOf(nombreDe("resultats_analyses", CENTRE_A)
                + nombreDe("resultats_analyses", CENTRE_B)));
        cas.put("05 bilan du patient", refus(doDelete(analyseApi(patientA, a), medecinA())));
        cas.put("06 meme suppression rejouee", refus(doDelete(analyseApi(patientA, a), medecinA())));
        cas.put("07 centerId du centre B (session A)", refus(doDelete(analyseApi(patientB, autreCentre) + "?centerId=" + CENTRE_B, medecinA())));
        cas.put("08 identifiant illisible", refus(doDelete(PATIENTS_API + "/" + patientA + "/analyses/pas-un-uuid", medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAnalyses());
        arbre("suppression", m);
    }
}
