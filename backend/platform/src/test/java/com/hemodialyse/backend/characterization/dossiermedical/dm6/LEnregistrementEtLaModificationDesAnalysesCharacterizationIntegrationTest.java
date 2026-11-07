package com.hemodialyse.backend.characterization.dossiermedical.dm6;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DM6, lot 2 (analyses, bilan à colonnes fixes), enregistrement et modification : {@code POST} et {@code PUT} sur
 * {@code /api/v1/patients/{id}/analyses}. Données fictives, dates nommées.
 */
class LEnregistrementEtLaModificationDesAnalysesCharacterizationIntegrationTest extends Dm6CharacterizationSupport {

    @Override
    protected String dossier() {
        return "analyses/enregistrement";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm6(CENTRE_A, "PAT_A");
        patientB = patientDm6(CENTRE_B, "PAT_B");
    }

    private String creer(String corps) throws Exception {
        return refus(doPost(analysesApi(patientA), corps, medecinA()));
    }

    private String complet(UUID centre, Object date) {
        return corps("centerId", centre, "datePrelevement", date, "hbGDl", 11.5, "htPct", 34, "plaquettes", 220, "ferritineNgMl", 450, "cstfPct", 30,
                "epoEndogeneMuiMl", 12, "ureePreMgDl", 120, "ureePostMgDl", 40, "creatinineMgDl", 8.5, "ktVMensuel", 1.4, "phosphoreMgDl", 5.2,
                "calciumMgDl", 9.1, "pthPgMl", 300, "albumineGDl", 3.8, "proteinesGDl", 6.9, "crpMgL", 4);
    }

    @Test
    void un_bilan_enregistre_garde_ses_vingt_colonnes_et_la_date_par_defaut_est_le_jour() throws Exception {
        deuxCentres();
        Resp complet = doPost(analysesApi(patientA), complet(CENTRE_A, fixe(5)), medecinA());
        aliasNommes.put(idDe(complet), "<ANALYSE_COMPLETE>");
        Resp vide = doPost(analysesApi(patientA), "{}", medecinA());
        aliasNommes.put(idDe(vide), "<ANALYSE_VIDE>");
        relatif(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reponse complete (seulement id, patient, modification)", reponse(complet));
        m.put("reponse du corps vide", reponse(vide));
        m.put("etat", etatDesAnalyses());
        arbre("analyse", m);
    }

    @Test
    void aucune_valeur_n_est_controlee_et_les_decimaux_sont_arrondis_par_la_colonne() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("hemoglobine negative", creer(corps("centerId", CENTRE_A, "datePrelevement", fixe(1), "hbGDl", -5)));
        cas.put("hemoglobine zero", creer(corps("centerId", CENTRE_A, "datePrelevement", fixe(2), "hbGDl", 0)));
        cas.put("hemoglobine absurde (999)", creer(corps("centerId", CENTRE_A, "datePrelevement", fixe(3), "hbGDl", 999)));
        cas.put("hematocrite au dessus de 100 %", creer(corps("centerId", CENTRE_A, "datePrelevement", fixe(4), "htPct", 250)));
        cas.put("trois decimales", creer(corps("centerId", CENTRE_A, "datePrelevement", fixe(5), "hbGDl", 11.555)));
        cas.put("quatre decimales", creer(corps("centerId", CENTRE_A, "datePrelevement", fixe(6), "ktVMensuel", 1.2345)));
        cas.put("plaquettes negatives", creer(corps("centerId", CENTRE_A, "datePrelevement", fixe(7), "plaquettes", -1)));
        cas.put("plaquettes decimales (1.5)", creer(corps("centerId", CENTRE_A, "datePrelevement", fixe(8), "plaquettes", 1.5)));
        cas.put("plaquettes en texte", creer("{\"centerId\":\"" + CENTRE_A + "\",\"datePrelevement\":\"" + fixe(9) + "\",\"plaquettes\":\"abc\"}"));
        cas.put("plaquettes 2147483647", creer(corps("centerId", CENTRE_A, "datePrelevement", fixe(10), "plaquettes", 2147483647)));
        cas.put("plaquettes 2147483648", creer(corps("centerId", CENTRE_A, "datePrelevement", fixe(11), "plaquettes", 2147483648L)));
        cas.put("valeur numerique en texte", creer("{\"centerId\":\"" + CENTRE_A + "\",\"datePrelevement\":\"" + fixe(12) + "\",\"hbGDl\":\"onze\"}"));
        cas.put("valeur tres grande (1e30)", creer("{\"centerId\":\"" + CENTRE_A + "\",\"datePrelevement\":\"" + fixe(13) + "\",\"hbGDl\":1e30}"));
        cas.put("date dans le futur (2200)", creer(corps("centerId", CENTRE_A, "datePrelevement", alias(java.time.LocalDate.of(2200, 1, 1), "<2200>"),
                "hbGDl", 1)));
        cas.put("date illisible", creer("{\"centerId\":\"" + CENTRE_A + "\",\"datePrelevement\":\"31/12/2025\",\"hbGDl\":1}"));
        cas.put("deux bilans le meme jour", creer(corps("centerId", CENTRE_A, "datePrelevement", fixe(1), "hbGDl", 7)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("lignes", lignes("SELECT date_prelevement, hb_g_dl, ht_pct, kt_v_mensuel, plaquettes FROM resultats_analyses WHERE center_id = ? "
                + "ORDER BY date_prelevement, hb_g_dl", CENTRE_A));
        arbre("valeurs", m);
    }

    @Test
    void les_corps_illisibles_et_les_patients_inconnus_ou_d_un_autre_centre_sont_acceptes_sans_garde() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("corps illisible", creer("{pas du json"));
        cas.put("corps absent", refus(doPost(analysesApi(patientA), "", medecinA())));
        cas.put("champ inconnu ignore", creer("{\"centerId\":\"" + CENTRE_A + "\",\"datePrelevement\":\"" + fixe(1) + "\",\"interpretation\":\"CRITIQUE\"}"));
        cas.put("patient inconnu", refus(doPost(analysesApi(idStable("patient", "inconnu")), complet(CENTRE_A, fixe(2)), medecinA())));
        cas.put("patient du centre B (session A)", refus(doPost(analysesApi(patientB), complet(CENTRE_A, fixe(3)), medecinA())));
        cas.put("identifiant de patient illisible", refus(doPost(PATIENTS_API + "/pas-un-uuid/analyses", complet(CENTRE_A, fixe(4)), medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAnalyses());
        arbre("corps_et_patients", m);
    }

    @Test
    void une_modification_remplace_le_bilan_en_entier_et_recree_sa_date_de_creation() throws Exception {
        deuxCentres();
        UUID id = analyseEnBase(CENTRE_A, patientA, "ANALYSE_1", fixe(10), "hb_g_dl", "10", "ferritine_ng_ml", "300", "creatinine_mg_dl", "8",
                "plaquettes", "200");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 seule l'hemoglobine est envoyee", resume(doPut(analyseApi(patientA, id), corps("centerId", CENTRE_A, "hbGDl", 12), medecinA()),
                "id", "patientId"));
        relatif(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat apres le PUT partiel (autres colonnes effacees, date du jour, creation recente)", etatDesAnalyses());
        arbre("remplacement", m);
    }

    @Test
    void une_modification_sur_un_identifiant_inconnu_cree_le_bilan_et_deplace_celui_d_un_autre_patient_ou_centre() throws Exception {
        deuxCentres();
        UUID duPatientA = analyseEnBase(CENTRE_A, patientA, "ANALYSE_A", fixe(10), "hb_g_dl", "10");
        UUID duPatientBCentreA = analyseEnBase(CENTRE_A, patientB, "ANALYSE_AUTRE_PATIENT", fixe(11), "hb_g_dl", "20");
        UUID duCentreB = analyseEnBase(CENTRE_B, patientB, "ANALYSE_AUTRE_CENTRE", fixe(12), "hb_g_dl", "30");
        aliasNommes.put(idStable("analyse", "inconnue").toString(), "<ANALYSE_INCONNUE>");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 identifiant inconnu (cree avec cet identifiant)", resume(doPut(analyseApi(patientA, idStable("analyse", "inconnue")),
                corps("centerId", CENTRE_A, "datePrelevement", fixe(13), "hbGDl", 13), medecinA()), "id", "patientId"));
        cas.put("02 bilan du patient B (centre A) modifie via le patient A : il change de patient", resume(doPut(analyseApi(patientA, duPatientBCentreA),
                corps("centerId", CENTRE_A, "datePrelevement", fixe(14), "hbGDl", 14), medecinA()), "id", "patientId"));
        cas.put("03 bilan du centre B modifie par la session A", resume(doPut(analyseApi(patientA, duCentreB),
                corps("centerId", CENTRE_A, "datePrelevement", fixe(15), "hbGDl", 15), medecinA()), "id", "patientId"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAnalyses());
        arbre("upsert_et_deplacement", m);
        org.assertj.core.api.Assertions.assertThat(duPatientA).isNotNull();
    }

    @Test
    void les_modifications_refusees_par_le_centre_ou_le_corps() throws Exception {
        deuxCentres();
        UUID id = analyseEnBase(CENTRE_A, patientA, "ANALYSE_1", fixe(10), "hb_g_dl", "10");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 centre B dans le corps (session A)", refus(doPut(analyseApi(patientA, id), complet(CENTRE_B, fixe(1)), medecinA())));
        cas.put("02 corps illisible", refus(doPut(analyseApi(patientA, id), "{pas du json", medecinA())));
        cas.put("03 corps absent", refus(doPut(analyseApi(patientA, id), "", medecinA())));
        cas.put("04 identifiant d'analyse illisible", refus(doPut(PATIENTS_API + "/" + patientA + "/analyses/pas-un-uuid", complet(CENTRE_A, fixe(1)),
                medecinA())));
        cas.put("05 session B modifie le bilan du centre A (le bilan change de centre)", resume(doPut(analyseApi(patientA, id),
                corps("centerId", CENTRE_B, "datePrelevement", fixe(2), "hbGDl", 2), as(CENTRE_B, "MEDECIN")), "id", "patientId"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAnalyses());
        arbre("refus_et_deplacement_de_centre", m);
    }
}
