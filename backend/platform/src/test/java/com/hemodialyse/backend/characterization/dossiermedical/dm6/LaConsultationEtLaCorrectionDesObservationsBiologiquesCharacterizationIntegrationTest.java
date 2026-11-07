package com.hemodialyse.backend.characterization.dossiermedical.dm6;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM6, lot 1 (observations), consultation, correction et suppression : {@code GET}, {@code PUT} et {@code DELETE} sur
 * {@code /api/v1/patients/{id}/observations} et lecture {@code GET .../demandes-examen/{id}/observations}.
 */
class LaConsultationEtLaCorrectionDesObservationsBiologiquesCharacterizationIntegrationTest extends Dm6CharacterizationSupport {

    @Override
    protected String dossier() {
        return "observations/consultation";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm6(CENTRE_A, "PAT_A");
        patientB = patientDm6(CENTRE_B, "PAT_B");
    }

    private void cinqObservations() {
        observationEnBase(CENTRE_A, patientA, "OBS_1", null, "718-7", "Hb", "10", "g/dL", null, fixe(10), "FINAL", "SAISIE_DIRECTE");
        observationEnBase(CENTRE_A, patientA, "OBS_2", null, "718-7", "Hb", "11", "g/dL", null, fixe(20), "PRELIMINAIRE", "SAISIE_DIRECTE");
        observationEnBase(CENTRE_A, patientA, "OBS_3", null, "2160-0", "Creatinine", "7.5", "mg/dL", null, fixe(30), "CORRIGE", "IMPORT");
        observationEnBase(CENTRE_A, patientA, "OBS_4", null, "5195-3", "Antigene", null, null, "Negatif", fixe(40), "FINAL", "DERIVEE_BILAN");
        observationEnBase(CENTRE_A, patientA, "OBS_5", null, "718-7", "Hb", "12", "g/dL", null, fixe(50), "FINAL", "SAISIE_DIRECTE");
        observationEnBase(CENTRE_A, patientB, "OBS_AUTRE_PATIENT", null, "718-7", "Hb", "99", "g/dL", null, fixe(99), "FINAL", "SAISIE_DIRECTE");
        observationEnBase(CENTRE_B, patientB, "OBS_AUTRE_CENTRE", null, "718-7", "Hb", "98", "g/dL", null, fixe(98), "FINAL", "SAISIE_DIRECTE");
    }

    @Test
    void la_liste_est_triee_de_la_date_la_plus_recente_et_paginee() throws Exception {
        deuxCentres();
        cinqObservations();
        String url = observationsApi(patientA);
        String[] champs = {"code", "valeurNum", "datePrelevement"};
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("defaut", page(doGet(url, medecinA()), champs));
        m.put("taille 2, page 0", page(doGet(url + "?size=2&page=0", medecinA()), "code", "datePrelevement"));
        m.put("taille 2, page 2", page(doGet(url + "?size=2&page=2", medecinA()), "code", "datePrelevement"));
        m.put("taille 2, page 9", page(doGet(url + "?size=2&page=9", medecinA()), "code", "datePrelevement"));
        m.put("taille 2, page -1", page(doGet(url + "?size=2&page=-1", medecinA()), "code", "datePrelevement"));
        m.put("taille 0", page(doGet(url + "?size=0", medecinA()), "code"));
        m.put("taille -5", page(doGet(url + "?size=-5", medecinA()), "code"));
        m.put("taille 1000", page(doGet(url + "?size=1000", medecinA()), "code"));
        m.put("taille illisible", refus(doGet(url + "?size=abc", medecinA())));
        m.put("liste pour l'ADMIN", page(doGet(url + "?size=1", as(CENTRE_A, "ADMIN")), "code"));
        m.put("liste d'un patient inconnu", page(doGet(observationsApi(idStable("patient", "inconnu")), medecinA()), "code"));
        m.put("liste du patient du centre B (session A)", page(doGet(observationsApi(patientB), medecinA()), "code"));
        arbre("pages", m);
    }

    @Test
    void les_filtres_par_code_et_par_periode_sont_inclusifs_et_non_controles() throws Exception {
        deuxCentres();
        cinqObservations();
        String url = observationsApi(patientA);
        String[] champs = {"code", "datePrelevement"};
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code 718-7", page(doGet(url + "?code=718-7", medecinA()), champs));
        m.put("code 2160-0", page(doGet(url + "?code=2160-0", medecinA()), champs));
        m.put("code 5195-3", page(doGet(url + "?code=5195-3", medecinA()), champs));
        m.put("code inconnu", page(doGet(url + "?code=0-0", medecinA()), champs));
        m.put("code blanc (ignore)", page(doGet(url + "?code=%20%20", medecinA()), champs));
        m.put("code vide (ignore)", page(doGet(url + "?code=", medecinA()), champs));
        m.put("code avec motif SQL (egalite stricte)", page(doGet(url + "?code=718-7'%20OR%20'1'='1", medecinA()), champs));
        m.put("code avec joker %", page(doGet(url + "?code=%25", medecinA()), champs));
        m.put("from = date exacte", page(doGet(url + "?from=" + fixe(30), medecinA()), champs));
        m.put("to = date exacte", page(doGet(url + "?to=" + fixe(30), medecinA()), champs));
        m.put("from et to bornes incluses", page(doGet(url + "?from=" + fixe(20) + "&to=" + fixe(40), medecinA()), champs));
        m.put("from apres to (aucun controle)", page(doGet(url + "?from=" + fixe(40) + "&to=" + fixe(20), medecinA()), champs));
        m.put("from illisible", refus(doGet(url + "?from=hier", medecinA())));
        m.put("code et periode combines", page(doGet(url + "?code=718-7&from=" + fixe(15) + "&to=" + fixe(45), medecinA()), champs));
        arbre("filtres", m);
    }

    @Test
    void la_lecture_rend_la_forme_complete_d_une_observation() throws Exception {
        deuxCentres();
        Resp c = doPost(observationsApi(patientA), corps("centerId", CENTRE_A, "codeSystem", "LOINC", "code", "2160-0", "codeDisplay", "Creatinine fictive",
                "valeurNum", 7.25, "unite", "mg/dL", "datePrelevement", fixe(-60), "statut", "PRELIMINAIRE"), medecinA());
        aliasNommes.put(idDe(c), "<OBS_1>");
        observationEnBase(CENTRE_A, patientA, "OBS_2", null, "5195-3", "Antigene fictif", null, null, "Negatif", fixe(-10), "FINAL", "IMPORT");
        lecture("liste", doGet(observationsApi(patientA), medecinA()));
    }

    @Test
    void une_correction_remplace_la_valeur_et_passe_le_statut_a_corrige_sans_toucher_au_reste() throws Exception {
        deuxCentres();
        UUID num = observationEnBase(CENTRE_A, patientA, "OBS_NUM", null, "718-7", "Hb", "10", "g/dL", null, fixe(10), "FINAL", "SAISIE_DIRECTE");
        UUID txt = observationEnBase(CENTRE_A, patientA, "OBS_TXT", null, "5195-3", "Antigene", null, null, "Negatif", fixe(11), "PRELIMINAIRE", "IMPORT");
        UUID derivee = observationEnBase(CENTRE_A, patientA, "OBS_DERIVEE", null, "718-7", "Hb", "9", "g/dL", null, fixe(12), "FINAL", "DERIVEE_BILAN");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 numerique -> numerique (unite changee)", resume(doPut(observationApi(patientA, num),
                corps("centerId", CENTRE_A, "valeurNum", 13, "unite", "g/L"), medecinA()), "statut", "valeurNum", "unite", "valeurTexte", "source"));
        cas.put("02 numerique -> textuelle", resume(doPut(observationApi(patientA, num),
                corps("centerId", CENTRE_A, "valeurTexte", "Indosable"), medecinA()), "statut", "valeurNum", "unite", "valeurTexte"));
        cas.put("03 textuelle -> numerique", resume(doPut(observationApi(patientA, txt),
                corps("centerId", CENTRE_A, "valeurNum", 1.5, "unite", "u"), medecinA()), "statut", "valeurNum", "unite", "valeurTexte"));
        cas.put("04 observation derivee d'un bilan (immuable)", refus(doPut(observationApi(patientA, derivee),
                corps("centerId", CENTRE_A, "valeurNum", 1, "unite", "u"), medecinA())));
        cas.put("05 numerique ET textuelle", refus(doPut(observationApi(patientA, num),
                corps("centerId", CENTRE_A, "valeurNum", 1, "unite", "u", "valeurTexte", "x"), medecinA())));
        cas.put("06 ni l'une ni l'autre ({})", refus(doPut(observationApi(patientA, num), "{}", medecinA())));
        cas.put("07 numerique sans unite", refus(doPut(observationApi(patientA, num), corps("centerId", CENTRE_A, "valeurNum", 1), medecinA())));
        cas.put("08 texte de 501 caracteres", refus(doPut(observationApi(patientA, num),
                corps("centerId", CENTRE_A, "valeurTexte", "t".repeat(501)), medecinA())));
        cas.put("09 champs non modifiables ignores (code, date, statut)", resume(doPut(observationApi(patientA, num),
                "{\"centerId\":\"" + CENTRE_A + "\",\"valeurTexte\":\"Ignore\",\"code\":\"2160-0\",\"datePrelevement\":\"" + fixe(99)
                        + "\",\"statut\":\"FINAL\"}", medecinA()), "statut", "code", "datePrelevement", "valeurTexte"));
        cas.put("10 observation inconnue", refus(doPut(observationApi(patientA, idStable("observation", "inconnue")),
                corps("centerId", CENTRE_A, "valeurTexte", "x"), medecinA())));
        cas.put("11 corps illisible", refus(doPut(observationApi(patientA, num), "{pas du json", medecinA())));
        cas.put("12 corps absent", refus(doPut(observationApi(patientA, num), "", medecinA())));
        aliasNommes.put(idStable("observation", "inconnue").toString(), "<OBS_INCONNUE>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesObservations());
        arbre("correction", m);
    }

    @Test
    void la_correction_est_cherchee_par_identifiant_patient_et_centre() throws Exception {
        deuxCentres();
        UUID a = observationEnBase(CENTRE_A, patientA, "OBS_A", null, "718-7", "Hb", "10", "g/dL", null, fixe(10), "FINAL", "SAISIE_DIRECTE");
        UUID autrePatient = observationEnBase(CENTRE_A, patientB, "OBS_AUTRE_PATIENT", null, "718-7", "Hb", "20", "g/dL", null, fixe(10), "FINAL",
                "SAISIE_DIRECTE");
        UUID autreCentre = observationEnBase(CENTRE_B, patientB, "OBS_AUTRE_CENTRE", null, "718-7", "Hb", "30", "g/dL", null, fixe(10), "FINAL",
                "SAISIE_DIRECTE");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 observation du patient A via le patient B (meme centre)", refus(doPut(observationApi(patientB, a),
                corps("centerId", CENTRE_A, "valeurTexte", "x"), medecinA())));
        cas.put("02 observation du patient B (centre A) via le patient A", refus(doPut(observationApi(patientA, autrePatient),
                corps("centerId", CENTRE_A, "valeurTexte", "x"), medecinA())));
        cas.put("03 observation du centre B, session A", refus(doPut(observationApi(patientB, autreCentre),
                corps("centerId", CENTRE_A, "valeurTexte", "x"), medecinA())));
        cas.put("04 observation du centre B avec le centre B dans le corps", refus(doPut(observationApi(patientB, autreCentre),
                corps("centerId", CENTRE_B, "valeurTexte", "x"), medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesObservations());
        arbre("introuvables", m);
    }

    @Test
    void la_suppression_rend_204_meme_quand_rien_n_est_supprime() throws Exception {
        deuxCentres();
        UUID a = observationEnBase(CENTRE_A, patientA, "OBS_A", null, "718-7", "Hb", "10", "g/dL", null, fixe(10), "FINAL", "SAISIE_DIRECTE");
        UUID derivee = observationEnBase(CENTRE_A, patientA, "OBS_DERIVEE", null, "718-7", "Hb", "9", "g/dL", null, fixe(12), "FINAL", "DERIVEE_BILAN");
        UUID autrePatient = observationEnBase(CENTRE_A, patientB, "OBS_AUTRE_PATIENT", null, "718-7", "Hb", "20", "g/dL", null, fixe(10), "FINAL",
                "SAISIE_DIRECTE");
        UUID autreCentre = observationEnBase(CENTRE_B, patientB, "OBS_AUTRE_CENTRE", null, "718-7", "Hb", "30", "g/dL", null, fixe(10), "FINAL",
                "SAISIE_DIRECTE");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 observation d'un autre patient du meme centre", refus(doDelete(observationApi(patientA, autrePatient), medecinA())));
        cas.put("02 observation d'un autre centre", refus(doDelete(observationApi(patientB, autreCentre), medecinA())));
        cas.put("03 observation inconnue", refus(doDelete(observationApi(patientA, idStable("observation", "inconnue")), medecinA())));
        aliasNommes.put(idStable("observation", "inconnue").toString(), "<OBS_INCONNUE>");
        int apresLesRefus = nombreDe("observations_biologiques", CENTRE_A) + nombreDe("observations_biologiques", CENTRE_B);
        cas.put("04 nombre de lignes apres les trois suppressions sans effet", String.valueOf(apresLesRefus));
        cas.put("05 observation du patient", refus(doDelete(observationApi(patientA, a), medecinA())));
        cas.put("06 meme suppression rejouee", refus(doDelete(observationApi(patientA, a), medecinA())));
        cas.put("07 observation derivee d'un bilan (supprimable)", refus(doDelete(observationApi(patientA, derivee), medecinA())));
        cas.put("08 centerId du centre B dans la requete", refus(doDelete(observationApi(patientB, autreCentre) + "?centerId=" + CENTRE_B, medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesObservations());
        arbre("suppression", m);
    }

    @Test
    void les_observations_d_une_demande_sont_lues_sans_controle_du_patient() throws Exception {
        deuxCentres();
        UUID demandeA = demandeEnBase(CENTRE_A, patientA, "DEM_A", "PRELEVE");
        UUID demandeB = demandeEnBase(CENTRE_B, patientB, "DEM_B", "DEMANDE");
        observationEnBase(CENTRE_A, patientA, "OBS_1", demandeA, "718-7", "Hb", "10", "g/dL", null, fixe(10), "FINAL", "SAISIE_DIRECTE");
        observationEnBase(CENTRE_A, patientA, "OBS_2", demandeA, "2160-0", "Creatinine", "7", "mg/dL", null, fixe(11), "FINAL", "SAISIE_DIRECTE");
        observationEnBase(CENTRE_A, patientB, "OBS_AUTRE_PATIENT", demandeA, "718-7", "Hb", "20", "g/dL", null, fixe(12), "FINAL", "SAISIE_DIRECTE");
        observationEnBase(CENTRE_A, patientA, "OBS_SANS_DEMANDE", null, "718-7", "Hb", "30", "g/dL", null, fixe(13), "FINAL", "SAISIE_DIRECTE");
        observationEnBase(CENTRE_B, patientB, "OBS_CENTRE_B", demandeB, "718-7", "Hb", "40", "g/dL", null, fixe(14), "FINAL", "SAISIE_DIRECTE");
        aliasNommes.put(idStable("demande", "INCONNUE").toString(), "<DEM_INCONNUE>");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 demande du patient", lues(doGet(observationsDeLaDemandeApi(patientA, demandeA), medecinA())));
        cas.put("02 meme demande lue via le patient B (aucun controle du patient, KB-MED-44)",
                lues(doGet(observationsDeLaDemandeApi(patientB, demandeA), medecinA())));
        cas.put("03 meme demande lue via un patient inconnu", lues(doGet(observationsDeLaDemandeApi(idStable("patient", "inconnu"), demandeA), medecinA())));
        cas.put("04 demande inconnue", lues(doGet(observationsDeLaDemandeApi(patientA, idStable("demande", "INCONNUE")), medecinA())));
        cas.put("05 demande du centre B, session A", lues(doGet(observationsDeLaDemandeApi(patientB, demandeB), medecinA())));
        cas.put("06 demande du centre B, session B", lues(doGet(observationsDeLaDemandeApi(patientB, demandeB), as(CENTRE_B, "MEDECIN"))));
        cas.put("07 demande du centre A, session B", lues(doGet(observationsDeLaDemandeApi(patientA, demandeA), as(CENTRE_B, "MEDECIN"))));
        cas.put("08 lecture ADMIN", lues(doGet(observationsDeLaDemandeApi(patientA, demandeA), as(CENTRE_A, "ADMIN"))));
        cas.put("09 identifiant de demande illisible", refus(doGet(PATIENTS_API + "/" + patientA + "/demandes-examen/pas-un-uuid/observations", medecinA())));
        cas.put("10 statut de la demande A inchange", statutDeLaDemande(demandeA));
        arbre("par_demande", cas);
    }

    private Object lues(Resp r) {
        if (r.status() != 200) return refus(r);
        JsonNode c = corps(r);
        List<String> lues = new ArrayList<>();
        c.forEach(o -> lues.add(resume(o, "patientId", "code", "valeurNum", "demandeExamenId")));
        lues.sort(String::compareTo);
        assertThat(c.isArray()).isTrue();
        return Map.of("status", 200, "observations", lues);
    }
}
