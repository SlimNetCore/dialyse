package com.hemodialyse.backend.characterization.dossiermedical.dm6;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DM6, lot 1 (observations), enregistrement : {@code POST /api/v1/patients/{id}/observations}. Données fictives, dates nommées.
 */
class LEnregistrementDUneObservationBiologiqueCharacterizationIntegrationTest extends Dm6CharacterizationSupport {

    @Override
    protected String dossier() {
        return "observations/enregistrement";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm6(CENTRE_A, "PAT_A");
        patientB = patientDm6(CENTRE_B, "PAT_B");
    }

    private String obs(String systeme, String code, Object valeurNum, String unite, String texte, Object date, String statut) {
        return corps("centerId", CENTRE_A, "codeSystem", systeme, "code", code, "codeDisplay", "Analyte fictif", "valeurNum", valeurNum,
                "unite", unite, "valeurTexte", texte, "datePrelevement", date, "statut", statut);
    }

    private String simple(Object valeurNum, String unite) {
        return obs("LOINC", "718-7", valeurNum, unite, null, fixe(5), null);
    }

    private String creer(String corps) throws Exception {
        return refus(doPost(observationsApi(patientA), corps, medecinA()));
    }

    @Test
    void une_observation_enregistree_garde_tous_ses_champs_et_prend_le_statut_final_par_defaut() throws Exception {
        deuxCentres();
        Resp numerique = doPost(observationsApi(patientA), obs("LOINC", " 718-7 ", 11.5, "g/dL", null, fixe(5), null), medecinA());
        aliasNommes.put(idDe(numerique), "<OBS_NUM>");
        Resp textuelle = doPost(observationsApi(patientA), obs("LOINC", "5195-3", null, null, "Negatif", fixe(6), "PRELIMINAIRE"), medecinA());
        aliasNommes.put(idDe(textuelle), "<OBS_TXT>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reponse numerique", reponse(numerique));
        m.put("reponse textuelle (statut PRELIMINAIRE demande)", reponse(textuelle));
        m.put("etat", etatDesObservations());
        arbre("observation", m);
    }

    @Test
    void la_valeur_est_soit_numerique_avec_unite_soit_textuelle_jamais_les_deux_ni_aucune() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("numerique avec unite", creer(simple(12, "g/dL")));
        cas.put("numerique zero", creer(simple(0, "g/dL")));
        cas.put("numerique negative", creer(simple(-3, "g/dL")));
        cas.put("numerique sans unite", creer(simple(12, null)));
        cas.put("numerique avec unite blanche", creer(simple(12, "   ")));
        cas.put("textuelle seule", creer(obs("LOINC", "718-7", null, null, "Trace", fixe(5), null)));
        cas.put("textuelle blanche seule", creer(obs("LOINC", "718-7", null, null, "   ", fixe(5), null)));
        cas.put("numerique ET textuelle", creer(obs("LOINC", "718-7", 12, "g/dL", "Trace", fixe(5), null)));
        cas.put("numerique avec texte blanc", creer(obs("LOINC", "718-7", 12, "g/dL", "   ", fixe(5), null)));
        cas.put("ni l'une ni l'autre", creer(obs("LOINC", "718-7", null, null, null, fixe(5), null)));
        cas.put("unite sans valeur numerique ni texte", creer(obs("LOINC", "718-7", null, "g/dL", null, fixe(5), null)));
        cas.put("unite avec texte (unite ignoree)", creer(obs("LOINC", "718-7", null, "g/dL", "Trace", fixe(5), null)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("lignes", lignes("SELECT valeur_num, unite, valeur_texte FROM observations_biologiques WHERE center_id = ? "
                + "ORDER BY valeur_num, valeur_texte, unite", CENTRE_A));
        arbre("valeurs", m);
    }

    @Test
    void les_bornes_de_la_valeur_et_des_textes_sont_celles_des_colonnes() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("4 decimales", creer(simple(new java.math.BigDecimal("1.2345"), "u")));
        cas.put("5 decimales (arrondi par la base)", creer(simple(new java.math.BigDecimal("1.23456"), "u")));
        cas.put("10 chiffres entiers + 4 decimales", creer(simple(new java.math.BigDecimal("9999999999.9999"), "u")));
        cas.put("11 chiffres entiers + 4 decimales", creer(simple(new java.math.BigDecimal("99999999999.9999"), "u")));
        cas.put("unite de 20 caracteres", creer(simple(1, "u".repeat(20))));
        cas.put("unite de 21 caracteres", creer(simple(2, "v".repeat(21))));
        cas.put("texte de 500 caracteres", creer(obs("LOINC", "718-7", null, null, "t".repeat(500), fixe(5), null)));
        cas.put("texte de 501 caracteres", creer(obs("LOINC", "718-7", null, null, "u".repeat(501), fixe(5), null)));
        cas.put("affichage de 255 caracteres", creer(corps("centerId", CENTRE_A, "codeSystem", "LOINC", "code", "718-7", "codeDisplay", "a".repeat(255),
                "valeurNum", 3, "unite", "u", "datePrelevement", fixe(5))));
        cas.put("affichage de 256 caracteres", creer(corps("centerId", CENTRE_A, "codeSystem", "LOINC", "code", "718-7", "codeDisplay", "a".repeat(256),
                "valeurNum", 4, "unite", "u", "datePrelevement", fixe(5))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("nombre de lignes", nombreDe("observations_biologiques", CENTRE_A));
        m.put("valeurs", lignes("SELECT valeur_num FROM observations_biologiques WHERE center_id = ? AND valeur_num IS NOT NULL ORDER BY valeur_num",
                CENTRE_A));
        // KB-MED-72 corrigé : bornes de colonnes en 400 stable (golden _nouveau_code ; legacy archivé dans dm6/observations/_ecarts-legacy)
        arbre("bornes_nouveau_code", m);
    }

    @Test
    void l_analyte_doit_etre_un_code_loinc_normalise() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("LOINC 718-7", creer(obs("LOINC", "718-7", 1, "u", null, fixe(5), null)));
        cas.put("LOINC en minuscules et espaces", creer(obs("loinc", "718-7", 1, "u", null, fixe(5), null)));
        cas.put("LOINC 1-0 (forme minimale)", creer(obs("LOINC", "1-0", 1, "u", null, fixe(5), null)));
        cas.put("LOINC 12345-6 (5 chiffres)", creer(obs("LOINC", "12345-6", 1, "u", null, fixe(5), null)));
        cas.put("LOINC 123456-7 (6 chiffres)", creer(obs("LOINC", "123456-7", 1, "u", null, fixe(5), null)));
        cas.put("LOINC sans tiret", creer(obs("LOINC", "7187", 1, "u", null, fixe(5), null)));
        cas.put("LOINC avec controle a 2 chiffres", creer(obs("LOINC", "718-77", 1, "u", null, fixe(5), null)));
        cas.put("CIM10 valide", creer(obs("CIM10", "N18.5", 1, "u", null, fixe(5), null)));
        cas.put("ATC", creer(obs("ATC", "B03XA01", 1, "u", null, fixe(5), null)));
        cas.put("LOCAL", creer(obs("LOCAL", "HB_LOCAL", 1, "u", null, fixe(5), null)));
        cas.put("systeme absent", creer(obs(null, "718-7", 1, "u", null, fixe(5), null)));
        cas.put("systeme inconnu", creer(obs("SNOMED", "718-7", 1, "u", null, fixe(5), null)));
        cas.put("systeme vide", creer(obs("", "718-7", 1, "u", null, fixe(5), null)));
        cas.put("code absent", creer(obs("LOINC", null, 1, "u", null, fixe(5), null)));
        cas.put("code blanc", creer(obs("LOINC", "   ", 1, "u", null, fixe(5), null)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("lignes", lignes("SELECT analyte_code_system, analyte_code FROM observations_biologiques WHERE center_id = ? "
                + "ORDER BY analyte_code, analyte_code_system", CENTRE_A));
        // KB-MED-72 corrigé : système de codage inconnu ou absent en 400 stable, sans nom de classe interne (legacy archivé dans _ecarts-legacy)
        arbre("analyte_nouveau_code", m);
    }

    @Test
    void le_statut_est_une_liste_fermee_et_la_date_ne_peut_pas_etre_dans_le_futur() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        for (String statut : new String[]{"PRELIMINAIRE", "FINAL", "CORRIGE", "final", "ANNULE", ""}) {
            cas.put("statut '" + statut + "'", creer(obs("LOINC", "718-7", 1, "u", null, fixe(5), statut)));
        }
        cas.put("date absente", creer(obs("LOINC", "718-7", 2, "u", null, null, null)));
        cas.put("date 1900", creer(obs("LOINC", "718-7", 3, "u", null, alias(java.time.LocalDate.of(1900, 1, 1), "<1900>"), null)));
        cas.put("date du jour", creer(obs("LOINC", "718-7", 4, "u", null, relatif(0), null)));
        cas.put("date de demain", creer(obs("LOINC", "718-7", 5, "u", null, relatif(1), null)));
        cas.put("date illisible", creer("{\"centerId\":\"" + CENTRE_A + "\",\"codeSystem\":\"LOINC\",\"code\":\"718-7\",\"valeurNum\":6,\"unite\":\"u\","
                + "\"datePrelevement\":\"31/12/2025\"}"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("lignes", lignes("SELECT statut, date_prelevement, valeur_num FROM observations_biologiques WHERE center_id = ? "
                + "ORDER BY valeur_num, statut", CENTRE_A));
        // KB-MED-72 corrigé : statut inconnu et date illisible en 400 stable (legacy archivé dans _ecarts-legacy)
        arbre("statut_et_date_nouveau_code", m);
    }

    @Test
    void les_envois_identiques_creent_deux_observations_et_la_demande_n_est_pas_controlee() throws Exception {
        deuxCentres();
        UUID demandeB = demandeEnBase(CENTRE_B, patientB, "DEM_B", "DEMANDE");
        UUID demandeA = demandeEnBase(CENTRE_A, patientA, "DEM_A", "DEMANDE");
        aliasNommes.put(idStable("demande", "INCONNUE").toString(), "<DEM_INCONNUE>");
        Map<String, String> cas = new LinkedHashMap<>();
        String identique = simple(7, "u");
        cas.put("premier envoi", creer(identique));
        cas.put("second envoi identique", creer(identique));
        cas.put("avec la demande du patient", creer(corps("centerId", CENTRE_A, "demandeExamenId", demandeA, "codeSystem", "LOINC", "code", "718-7",
                "valeurNum", 8, "unite", "u", "datePrelevement", fixe(5))));
        cas.put("avec une demande inconnue", creer(corps("centerId", CENTRE_A, "demandeExamenId", idStable("demande", "INCONNUE"), "codeSystem", "LOINC",
                "code", "718-7", "valeurNum", 9, "unite", "u", "datePrelevement", fixe(5))));
        cas.put("avec la demande d'un patient du centre B", creer(corps("centerId", CENTRE_A, "demandeExamenId", demandeB, "codeSystem", "LOINC",
                "code", "718-7", "valeurNum", 10, "unite", "u", "datePrelevement", fixe(5))));
        cas.put("avec un identifiant de demande illisible", creer("{\"centerId\":\"" + CENTRE_A + "\",\"demandeExamenId\":\"pas-un-uuid\","
                + "\"codeSystem\":\"LOINC\",\"code\":\"718-7\",\"valeurNum\":11,\"unite\":\"u\",\"datePrelevement\":\"" + fixe(5) + "\"}"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesObservations());
        m.put("statut de la demande A apres la saisie", statutDeLaDemande(demandeA));
        // KB-MED-72 corrigé : identifiant de demande illisible en 400 ; la référence de demande reste souple en écriture (legacy archivé dans _ecarts-legacy)
        arbre("doublons_et_demande_nouveau_code", m);
    }

    @Test
    void les_corps_illisibles_et_les_patients_inconnus_ou_d_un_autre_centre() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("corps vide {}", creer("{}"));
        cas.put("corps illisible", creer("{pas du json"));
        cas.put("corps absent", refus(doPost(observationsApi(patientA), "", medecinA())));
        cas.put("valeur numerique en texte", creer("{\"centerId\":\"" + CENTRE_A + "\",\"codeSystem\":\"LOINC\",\"code\":\"718-7\","
                + "\"valeurNum\":\"abc\",\"unite\":\"u\",\"datePrelevement\":\"" + fixe(5) + "\"}"));
        cas.put("champ inconnu ignore", creer("{\"centerId\":\"" + CENTRE_A + "\",\"codeSystem\":\"LOINC\",\"code\":\"718-7\",\"valeurNum\":1,"
                + "\"unite\":\"u\",\"datePrelevement\":\"" + fixe(5) + "\",\"interpretation\":\"CRITIQUE\"}"));
        cas.put("patient inconnu", refus(doPost(observationsApi(idStable("patient", "inconnu")), simple(1, "u"), medecinA())));
        cas.put("patient du centre B (session A)", refus(doPost(observationsApi(patientB), simple(2, "u"), medecinA())));
        cas.put("identifiant de patient illisible", refus(doPost(PATIENTS_API + "/pas-un-uuid/observations", simple(3, "u"), medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesObservations());
        // KB-MED-72 et KB-MED-73 corrigés : corps illisible en 400, patient inconnu ou d'un autre centre en 404 PATIENT_INTROUVABLE sans écriture (legacy archivé dans _ecarts-legacy)
        arbre("corps_et_patients_nouveau_code", m);
    }
}
