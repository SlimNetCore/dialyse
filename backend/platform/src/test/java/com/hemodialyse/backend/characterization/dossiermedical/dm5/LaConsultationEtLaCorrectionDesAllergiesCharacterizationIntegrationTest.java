package com.hemodialyse.backend.characterization.dossiermedical.dm5;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM5, lot 2 (allergies), consultation, allergies critiques, correction et suppression :
 * {@code GET}, {@code GET .../critiques}, {@code PUT} et {@code DELETE} sur {@code /api/v1/patients/{id}/allergies}.
 */
class LaConsultationEtLaCorrectionDesAllergiesCharacterizationIntegrationTest extends Dm5CharacterizationSupport {

    @Override
    protected String dossier() {
        return "allergies/consultation";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm5(CENTRE_A, "PAT_A");
        patientB = patientDm5(CENTRE_B, "PAT_B");
    }

    private String lire(Resp r) {
        return resume(r, "codeSystem", "code", "categorie", "criticite", "typeReaction", "manifestations", "dateConstatation", "statutVerification");
    }

    private Object critiques(UUID patient) throws Exception {
        Resp r = doGet(allergiesApi(patient) + "/critiques", medecinA());
        if (r.status() != 200) return refus(r);
        List<String> codes = new ArrayList<>();
        corps(r).forEach(a -> codes.add(resume(a, "code", "criticite", "statutVerification", "dateConstatation")));
        return codes;
    }

    @Test
    void la_liste_est_triee_de_la_constatation_la_plus_recente_et_paginee() throws Exception {
        deuxCentres();
        for (int i = 1; i <= 5; i++) {
            allergieEnBase(CENTRE_A, patientA, "ALG_" + i, "LOCAL", "SUBSTANCE_" + i, "ALIMENT", i % 2 == 0 ? "HAUTE" : "BASSE", "ALLERGIE",
                    i % 2 == 0 ? "Manifestation " + i : null, fixe(i * 10), "SUSPECTEE");
        }
        allergieEnBase(CENTRE_A, patientB, "ALG_AUTRE_PATIENT", "LOCAL", "AUTRE_PATIENT", "ALIMENT", "BASSE", "ALLERGIE", null, fixe(99), "SUSPECTEE");
        allergieEnBase(CENTRE_B, patientB, "ALG_AUTRE_CENTRE", "LOCAL", "AUTRE_CENTRE", "ALIMENT", "BASSE", "ALLERGIE", null, fixe(98), "SUSPECTEE");
        String url = allergiesApi(patientA);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("defaut", page(doGet(url, medecinA()), "code", "criticite", "dateConstatation"));
        m.put("taille 2, page 0", page(doGet(url + "?size=2&page=0", medecinA()), "code"));
        m.put("taille 2, page 2", page(doGet(url + "?size=2&page=2", medecinA()), "code"));
        m.put("taille 2, page 9", page(doGet(url + "?size=2&page=9", medecinA()), "code"));
        m.put("taille 2, page -1", page(doGet(url + "?size=2&page=-1", medecinA()), "code"));
        m.put("taille 0", page(doGet(url + "?size=0", medecinA()), "code"));
        m.put("taille 1000", page(doGet(url + "?size=1000", medecinA()), "code"));
        m.put("taille illisible", refus(doGet(url + "?size=abc", medecinA())));
        m.put("liste pour l'ADMIN", page(doGet(url + "?size=1", as(CENTRE_A, "ADMIN")), "code"));
        // KB-MED-58 corrigé : pagination illisible = 400 (avant : 500). Legacy archivé dans dm5/allergies/_ecarts-legacy.
        arbre("pages_nouveau_code", m);
    }

    @Test
    void la_lecture_rend_la_forme_complete_d_une_allergie() throws Exception {
        deuxCentres();
        Resp c = doPost(allergiesApi(patientA), corps("centerId", CENTRE_A, "codeSystem", "ATC", "code", "J01CA04", "codeDisplay", "Substance fictive",
                "categorie", "MEDICAMENT", "criticite", "HAUTE", "typeReaction", "ALLERGIE", "manifestations", "Reaction fictive",
                "dateConstatation", fixe(-60), "statutVerification", "CONFIRMEE"), medecinA());
        aliasNommes.put(idDe(c), "<ALG_1>");
        allergieEnBase(CENTRE_A, patientA, "ALG_2", "LOCAL", "arachide", "ALIMENT", "BASSE", "INTOLERANCE", null, fixe(-10), "REFUTEE");
        lecture("liste", doGet(allergiesApi(patientA), medecinA()));
    }

    @Test
    void les_allergies_critiques_sont_les_criticites_hautes_quel_que_soit_le_statut_de_verification() throws Exception {
        deuxCentres();
        UUID confirmee = allergieEnBase(CENTRE_A, patientA, "ALG_CONFIRMEE", "LOCAL", "C10", "ALIMENT", "HAUTE", "ALLERGIE", "Manifestation", fixe(10),
                "CONFIRMEE");
        allergieEnBase(CENTRE_A, patientA, "ALG_REFUTEE", "LOCAL", "C20", "ALIMENT", "HAUTE", "ALLERGIE", "Manifestation", fixe(20), "REFUTEE");
        allergieEnBase(CENTRE_A, patientA, "ALG_SUSPECTEE", "LOCAL", "C30", "ALIMENT", "HAUTE", "INTOLERANCE", "Manifestation", fixe(30), "SUSPECTEE");
        allergieEnBase(CENTRE_A, patientA, "ALG_BASSE", "LOCAL", "C40", "ALIMENT", "BASSE", "ALLERGIE", null, fixe(40), "CONFIRMEE");
        allergieEnBase(CENTRE_A, patientB, "ALG_AUTRE_PATIENT", "LOCAL", "C50", "ALIMENT", "HAUTE", "ALLERGIE", "Manifestation", fixe(50), "CONFIRMEE");
        allergieEnBase(CENTRE_B, patientB, "ALG_AUTRE_CENTRE", "LOCAL", "C60", "ALIMENT", "HAUTE", "ALLERGIE", "Manifestation", fixe(60), "CONFIRMEE");
        lecture("critiques", doGet(allergiesApi(patientA) + "/critiques", medecinA()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("1 etat initial", critiques(patientA));
        doPut(allergieApi(patientA, confirmee), corps("centerId", CENTRE_A, "criticite", "BASSE"), medecinA());
        m.put("2 apres abaissement de C10 a BASSE", critiques(patientA));
        doPost(allergiesApi(patientA), corps("centerId", CENTRE_A, "codeSystem", "LOCAL", "code", "C70", "categorie", "ALIMENT", "criticite", "HAUTE",
                "typeReaction", "ALLERGIE", "manifestations", "Manifestation", "dateConstatation", fixe(70)), medecinA());
        m.put("3 apres declaration de C70 HAUTE", critiques(patientA));
        m.put("4 autre patient du centre (sa propre allergie critique)", critiques(patientB));
        m.put("5 patient inconnu", critiques(idStable("patient", "inconnu")));
        m.put("6 lecture par l'ADMIN", statutEtCode(doGet(allergiesApi(patientA) + "/critiques", as(CENTRE_A, "ADMIN"))));
        m.put("7 lecture par l'INFIRMIER", statutEtCode(doGet(allergiesApi(patientA) + "/critiques", as(CENTRE_A, "INFIRMIER"))));
        m.put("8 centre B demande depuis la session A", statutEtCode(doGet(allergiesApi(patientB) + "/critiques?centerId=" + CENTRE_B, medecinA())));
        arbre("critiques_evolution", m);
    }

    @Test
    void la_correction_ne_change_que_la_criticite_les_manifestations_et_le_statut() throws Exception {
        deuxCentres();
        UUID a = allergieEnBase(CENTRE_A, patientA, "ALG_1", "ATC", "J01CA04", "MEDICAMENT", "BASSE", "ALLERGIE", "Manifestation initiale", fixe(-100),
                "SUSPECTEE");
        UUID autre = allergieEnBase(CENTRE_A, patientB, "ALG_AUTRE_PATIENT", "LOCAL", "AUTRE", "ALIMENT", "BASSE", "ALLERGIE", null, fixe(1), "SUSPECTEE");
        String url = allergieApi(patientA, a);
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("01 HAUTE avec manifestations", lire(doPut(url, corps("centerId", CENTRE_A, "criticite", "HAUTE", "manifestations", "Choc fictif"),
                medecinA())));
        cas.put("02 HAUTE sans manifestations", lire(doPut(url, corps("centerId", CENTRE_A, "criticite", "HAUTE"), medecinA())));
        cas.put("03 HAUTE avec manifestations blanches", lire(doPut(url, corps("centerId", CENTRE_A, "criticite", "HAUTE", "manifestations", "  "),
                medecinA())));
        cas.put("04 HAUTE reste HAUTE, manifestations changees, statut CONFIRMEE", lire(doPut(url, corps("centerId", CENTRE_A, "criticite", "HAUTE",
                "manifestations", "Autre choc fictif", "statutVerification", "CONFIRMEE"), medecinA())));
        cas.put("05 BASSE sans manifestations : les manifestations sont effacees, le statut est conserve", lire(doPut(url, corps("centerId", CENTRE_A,
                "criticite", "BASSE"), medecinA())));
        cas.put("06 statut REFUTEE", lire(doPut(url, corps("centerId", CENTRE_A, "criticite", "BASSE", "statutVerification", "REFUTEE"), medecinA())));
        cas.put("07 statut inconnu", lire(doPut(url, corps("centerId", CENTRE_A, "criticite", "BASSE", "statutVerification", "PROBABLE"), medecinA())));
        cas.put("08 criticite absente", lire(doPut(url, corps("centerId", CENTRE_A, "manifestations", "X"), medecinA())));
        cas.put("09 criticite inconnue", lire(doPut(url, corps("centerId", CENTRE_A, "criticite", "MOYENNE"), medecinA())));
        cas.put("10 les autres champs du corps sont ignores", lire(doPut(url, corps("centerId", CENTRE_A, "criticite", "BASSE", "codeSystem", "LOCAL",
                "code", "AUTRE_CODE", "categorie", "ALIMENT", "typeReaction", "INTOLERANCE", "dateConstatation", fixe(-5)), medecinA())));
        cas.put("11 allergie inconnue", lire(doPut(allergieApi(patientA, idStable("allergie", "inconnue")), corps("centerId", CENTRE_A,
                "criticite", "BASSE"), medecinA())));
        cas.put("12 allergie d'un autre patient", lire(doPut(allergieApi(patientA, autre), corps("centerId", CENTRE_A, "criticite", "BASSE"),
                medecinA())));
        cas.put("13 corps absent", lire(doPut(url, "", medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAllergies());
        // KB-MED-58 et 59 corrigés : criticité absente et corps absent = 400 (avant : 500), valeurs inconnues sans nom de classe interne. Legacy archivé dans dm5/allergies/_ecarts-legacy.
        arbre("correction_nouveau_code", m);
    }

    @Test
    void la_suppression_rend_204_meme_quand_rien_n_est_supprime() throws Exception {
        deuxCentres();
        UUID a1 = allergieEnBase(CENTRE_A, patientA, "ALG_1", "LOCAL", "A1", "ALIMENT", "BASSE", "ALLERGIE", null, fixe(10), "SUSPECTEE");
        allergieEnBase(CENTRE_A, patientA, "ALG_2", "LOCAL", "A2", "ALIMENT", "HAUTE", "ALLERGIE", "Manifestation", fixe(20), "CONFIRMEE");
        UUID autrePatient = allergieEnBase(CENTRE_A, patientB, "ALG_AUTRE_PATIENT", "LOCAL", "A3", "ALIMENT", "BASSE", "ALLERGIE", null, fixe(30),
                "SUSPECTEE");
        UUID autreCentre = allergieEnBase(CENTRE_B, patientB, "ALG_AUTRE_CENTRE", "LOCAL", "A4", "ALIMENT", "BASSE", "ALLERGIE", null, fixe(40),
                "SUSPECTEE");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("1 suppression d'une allergie", refus(doDelete(allergieApi(patientA, a1), medecinA())));
        cas.put("2 meme suppression rejouee", refus(doDelete(allergieApi(patientA, a1), medecinA())));
        cas.put("3 identifiant inconnu", refus(doDelete(allergieApi(patientA, idStable("allergie", "inconnue")), medecinA())));
        cas.put("4 allergie d'un autre patient du centre", refus(doDelete(allergieApi(patientA, autrePatient), medecinA())));
        cas.put("5 allergie d'un autre centre (centre B demande)", refus(doDelete(allergieApi(patientB, autreCentre) + "?centerId=" + CENTRE_B,
                medecinA())));
        cas.put("6 identifiant illisible", refus(doDelete(allergiesApi(patientA) + "/abc", medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAllergies());
        // KB-MED-58 corrigé : identifiant illisible = 400 (avant : 500). Le DELETE d'une allergie inexistante reste 204 (reproduit). Legacy archivé dans dm5/allergies/_ecarts-legacy.
        arbre("suppression_nouveau_code", m);
    }
}
