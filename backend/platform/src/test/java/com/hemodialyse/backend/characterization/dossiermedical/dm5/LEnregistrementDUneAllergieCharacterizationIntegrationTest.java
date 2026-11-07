package com.hemodialyse.backend.characterization.dossiermedical.dm5;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM5, lot 2 (allergies), enregistrement : {@code POST /api/v1/patients/{id}/allergies}. Données fictives, dates nommées.
 */
class LEnregistrementDUneAllergieCharacterizationIntegrationTest extends Dm5CharacterizationSupport {

    @Override
    protected String dossier() {
        return "allergies/enregistrement";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm5(CENTRE_A, "PAT_A");
        patientB = patientDm5(CENTRE_B, "PAT_B");
    }

    private String al(String systeme, String code, String categorie, String criticite, String reaction, String manifestations, Object date) {
        return corps("centerId", CENTRE_A, "codeSystem", systeme, "code", code, "categorie", categorie, "criticite", criticite,
                "typeReaction", reaction, "manifestations", manifestations, "dateConstatation", date);
    }

    private String simple(String code) {
        return al("LOCAL", code, "MEDICAMENT", "BASSE", "ALLERGIE", null, fixe(-30));
    }

    private String creer(String corps) throws Exception {
        return refus(doPost(allergiesApi(patientA), corps, medecinA()));
    }

    @Test
    void une_allergie_enregistree_garde_tous_ses_champs_et_prend_des_valeurs_par_defaut() throws Exception {
        deuxCentres();
        Resp complete = doPost(allergiesApi(patientA), corps("centerId", CENTRE_A, "codeSystem", "ATC", "code", " j01ca04 ",
                "codeDisplay", "  Substance fictive  ", "categorie", "MEDICAMENT", "criticite", "HAUTE", "typeReaction", "ALLERGIE",
                "manifestations", "Reaction fictive", "dateConstatation", fixe(-100), "statutVerification", "CONFIRMEE"), medecinA());
        aliasNommes.put(idDe(complete), "<ALG_COMPLETE>");
        Resp minimale = doPost(allergiesApi(patientA), al("LOCAL", "arachide", "ALIMENT", "BASSE", "INTOLERANCE", null, null), medecinA());
        aliasNommes.put(idDe(minimale), "<ALG_MINIMALE>");
        relatif(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reponse complete", reponse(complete));
        m.put("reponse minimale (date et statut par defaut)", reponse(minimale));
        m.put("etat", etatDesAllergies());
        arbre("allergie", m);
    }

    @Test
    void la_substance_est_un_code_normalise_obligatoire_dans_un_systeme_connu() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("ATC j01ca04 (minuscules)", creer(al("ATC", "j01ca04", "MEDICAMENT", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("CIM10 T88.7", creer(al("CIM10", "T88.7", "MEDICAMENT", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("CIM10 invalide", creer(al("CIM10", "T8", "MEDICAMENT", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("LOINC 718-7", creer(al("LOINC", "718-7", "BIOLOGIQUE", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("LOINC invalide", creer(al("LOINC", "zz", "BIOLOGIQUE", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("LOCAL latex", creer(al("LOCAL", "latex", "ENVIRONNEMENT", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("systeme absent", creer(al(null, "latex2", "ENVIRONNEMENT", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("systeme inconnu", creer(al("SNOMED", "latex3", "ENVIRONNEMENT", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("code absent", creer(al("LOCAL", null, "ENVIRONNEMENT", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("code blanc", creer(al("LOCAL", "   ", "ENVIRONNEMENT", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("code de 20 caracteres", creer(al("LOCAL", "c".repeat(20), "ENVIRONNEMENT", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("code de 21 caracteres", creer(al("LOCAL", "d".repeat(21), "ENVIRONNEMENT", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("affichage de 255 caracteres", creer(corps("centerId", CENTRE_A, "codeSystem", "LOCAL", "code", "AFF255", "codeDisplay", "a".repeat(255),
                "categorie", "ALIMENT", "criticite", "BASSE", "typeReaction", "ALLERGIE", "dateConstatation", fixe(-30))));
        cas.put("affichage de 256 caracteres", creer(corps("centerId", CENTRE_A, "codeSystem", "LOCAL", "code", "AFF256", "codeDisplay", "a".repeat(256),
                "categorie", "ALIMENT", "criticite", "BASSE", "typeReaction", "ALLERGIE", "dateConstatation", fixe(-30))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("lignes", lignes("SELECT substance_code_system, substance_code, categorie FROM allergies_patient WHERE center_id = ? "
                + "ORDER BY substance_code, substance_code_system", CENTRE_A));
        arbre("substance", m);
    }

    @Test
    void les_categories_les_criticites_les_reactions_et_les_statuts_sont_des_listes_fermees() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        int i = 0;
        for (String categorie : new String[]{"MEDICAMENT", "ALIMENT", "ENVIRONNEMENT", "BIOLOGIQUE", "AUTRE", "medicament", ""}) {
            cas.put("categorie '" + categorie + "'", creer(al("LOCAL", "CAT" + i++, categorie, "BASSE", "ALLERGIE", null, fixe(-30))));
        }
        cas.put("categorie absente", creer(al("LOCAL", "CAT_ABSENTE", null, "BASSE", "ALLERGIE", null, fixe(-30))));
        for (String criticite : new String[]{"BASSE", "HAUTE", "MOYENNE", "haute"}) {
            cas.put("criticite '" + criticite + "'", creer(al("LOCAL", "CRI" + i++, "ALIMENT", criticite, "ALLERGIE", "Manifestation", fixe(-30))));
        }
        cas.put("criticite absente", creer(al("LOCAL", "CRI_ABSENTE", "ALIMENT", null, "ALLERGIE", null, fixe(-30))));
        for (String reaction : new String[]{"ALLERGIE", "INTOLERANCE", "ANAPHYLAXIE"}) {
            cas.put("type de reaction '" + reaction + "'", creer(al("LOCAL", "REA" + i++, "ALIMENT", "BASSE", reaction, null, fixe(-30))));
        }
        cas.put("type de reaction absent", creer(al("LOCAL", "REA_ABSENT", "ALIMENT", "BASSE", null, null, fixe(-30))));
        for (String statut : new String[]{"SUSPECTEE", "CONFIRMEE", "REFUTEE", "PROBABLE", ""}) {
            cas.put("statut '" + statut + "'", creer(corps("centerId", CENTRE_A, "codeSystem", "LOCAL", "code", "STA" + i++, "categorie", "ALIMENT",
                    "criticite", "BASSE", "typeReaction", "ALLERGIE", "dateConstatation", fixe(-30), "statutVerification", statut)));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("lignes", lignes("SELECT substance_code, categorie, criticite, type_reaction, statut_verification FROM allergies_patient "
                + "WHERE center_id = ? ORDER BY substance_code", CENTRE_A));
        arbre("listes_fermees", m);
    }

    @Test
    void une_criticite_haute_exige_la_description_des_manifestations() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("HAUTE sans manifestations", creer(al("LOCAL", "H1", "ALIMENT", "HAUTE", "ALLERGIE", null, fixe(-30))));
        cas.put("HAUTE avec manifestations vides", creer(al("LOCAL", "H2", "ALIMENT", "HAUTE", "ALLERGIE", "", fixe(-30))));
        cas.put("HAUTE avec manifestations blanches", creer(al("LOCAL", "H3", "ALIMENT", "HAUTE", "ALLERGIE", "   ", fixe(-30))));
        cas.put("HAUTE avec manifestations", creer(al("LOCAL", "H4", "ALIMENT", "HAUTE", "ALLERGIE", "Choc fictif", fixe(-30))));
        cas.put("BASSE sans manifestations", creer(al("LOCAL", "B1", "ALIMENT", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("BASSE avec manifestations", creer(al("LOCAL", "B2", "ALIMENT", "BASSE", "ALLERGIE", "Rougeur fictive", fixe(-30))));
        cas.put("HAUTE avec manifestations de 10000 caracteres (TEXT)", creer(al("LOCAL", "H5", "ALIMENT", "HAUTE", "ALLERGIE", "m".repeat(10000),
                fixe(-30))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("lignes", lignes("SELECT substance_code, criticite, LENGTH(manifestations) AS longueur FROM allergies_patient WHERE center_id = ? "
                + "ORDER BY substance_code", CENTRE_A));
        arbre("manifestations", m);
    }

    @Test
    void la_date_de_constatation_par_defaut_est_le_jour_de_la_jvm_et_le_futur_est_refuse() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("date absente", creer(al("LOCAL", "D1", "ALIMENT", "BASSE", "ALLERGIE", null, null)));
        cas.put("date fixe passee", creer(al("LOCAL", "D2", "ALIMENT", "BASSE", "ALLERGIE", null, fixe(-30))));
        cas.put("aujourd'hui", creer(al("LOCAL", "D3", "ALIMENT", "BASSE", "ALLERGIE", null, relatif(0))));
        cas.put("demain", creer(al("LOCAL", "D4", "ALIMENT", "BASSE", "ALLERGIE", null, relatif(1))));
        cas.put("en 1900", creer(al("LOCAL", "D5", "ALIMENT", "BASSE", "ALLERGIE", null, alias(LocalDate.of(1900, 1, 1), "<1900>"))));
        cas.put("en 2200", creer(al("LOCAL", "D6", "ALIMENT", "BASSE", "ALLERGIE", null, alias(LocalDate.of(2200, 1, 1), "<2200>"))));
        cas.put("illisible", creer(al("LOCAL", "D7", "ALIMENT", "BASSE", "ALLERGIE", null, "demain")));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("lignes", lignes("SELECT substance_code, date_constatation FROM allergies_patient WHERE center_id = ? ORDER BY date_constatation, "
                + "substance_code", CENTRE_A));
        arbre("dates", m);
    }

    @Test
    void la_meme_substance_peut_etre_declaree_plusieurs_fois_sans_controle_de_doublon() throws Exception {
        deuxCentres();
        UUID autrePatient = patientDm5(CENTRE_A, "PAT_A2");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("1 premiere declaration", creer(simple("PENI")));
        cas.put("2 declaration identique", creer(simple("PENI")));
        cas.put("3 meme substance en minuscules", creer(simple("peni")));
        cas.put("4 meme substance, autre criticite et autre categorie", creer(al("LOCAL", "PENI", "ALIMENT", "HAUTE", "INTOLERANCE", "Manifestation",
                fixe(-20))));
        cas.put("5 autre patient du centre", refus(doPost(allergiesApi(autrePatient), simple("PENI"), medecinA())));
        cas.put("6 patient de l'autre centre", refus(doPost(allergiesApi(patientB), corps("centerId", CENTRE_B, "codeSystem", "LOCAL", "code", "PENI",
                "categorie", "MEDICAMENT", "criticite", "BASSE", "typeReaction", "ALLERGIE", "dateConstatation", fixe(-30)),
                as(CENTRE_B, "MEDECIN"))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAllergies());
        arbre("doublons", m);
    }

    @Test
    void un_corps_illisible_ou_un_patient_inconnu() throws Exception {
        deuxCentres();
        UUID inconnu = idStable("patient", "inconnu");
        aliasNommes.put(inconnu.toString(), "<PAT_INCONNU>");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("corps absent", refus(doPost(allergiesApi(patientA), "", medecinA())));
        cas.put("corps vide {}", creer("{}"));
        cas.put("corps tableau []", creer("[]"));
        cas.put("corps illisible", creer("{pas du json"));
        cas.put("centre illisible", creer("{\"centerId\":\"pas-un-uuid\"}"));
        cas.put("identifiant de patient illisible", refus(doPost(PATIENTS_API + "/abc/allergies", simple("X1"), medecinA())));
        cas.put("patient inconnu", refus(doPost(allergiesApi(inconnu), simple("X2"), medecinA())));
        cas.put("patient d'un autre centre (session A)", refus(doPost(allergiesApi(patientB), simple("X3"), medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAllergies());
        arbre("corps_et_patient", m);
        assertThat(nombreDe("allergies_patient", CENTRE_B)).isZero();
    }
}
