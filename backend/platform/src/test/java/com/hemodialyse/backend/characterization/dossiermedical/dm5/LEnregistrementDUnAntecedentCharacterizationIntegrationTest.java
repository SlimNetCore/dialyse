package com.hemodialyse.backend.characterization.dossiermedical.dm5;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM5, lot 1 (antécédents), enregistrement : {@code POST /api/v1/patients/{id}/antecedents}. Données fictives, dates nommées.
 */
class LEnregistrementDUnAntecedentCharacterizationIntegrationTest extends Dm5CharacterizationSupport {

    @Override
    protected String dossier() {
        return "antecedents/enregistrement";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm5(CENTRE_A, "PAT_A");
        patientB = patientDm5(CENTRE_B, "PAT_B");
    }

    private String ant(String type, String systeme, String code, String libelle, LocalDate debut) {
        return corps("centerId", CENTRE_A, "type", type, "codeSystem", systeme, "code", code, "libelleLibre", libelle, "dateDebut", debut);
    }

    private String creer(String corps) throws Exception {
        return statutOuRefus(doPost(antecedentsApi(patientA), corps, medecinA()));
    }

    @Test
    void un_antecedent_enregistre_demarre_actif_avec_tous_ses_champs() throws Exception {
        deuxCentres();
        Resp r = doPost(antecedentsApi(patientA), corps("centerId", CENTRE_A, "type", "COMORBIDITE", "codeSystem", "CIM10", "code", " n18.5 ",
                "codeDisplay", "  Maladie fictive de test  ", "libelleLibre", "  precision libre  ", "dateDebut", fixe(-100),
                "dateFin", fixe(-50), "severite", "MODEREE", "note", "Note fictive"), medecinA());
        aliasNommes.put(idDe(r), "<ANT>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reponse", reponse(r));
        m.put("etat", etatDesAntecedents());
        arbre("antecedent", m);
    }

    @Test
    void le_diagnostic_est_un_code_normalise_ou_un_libelle_libre() throws Exception {
        deuxCentres();
        LocalDate d = fixe(-30);
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("CIM10 N18", creer(ant("MEDICAL", "CIM10", "N18", null, d)));
        cas.put("CIM10 e11.9 (minuscule)", creer(ant("MEDICAL", "CIM10", "e11.9", null, d)));
        cas.put("CIM10 I10.ABCD (quatre caracteres apres le point)", creer(ant("MEDICAL", "CIM10", "I10.ABCD", null, d)));
        cas.put("CIM10 I10.ABCDE (cinq)", creer(ant("MEDICAL", "CIM10", "I10.ABCDE", null, d)));
        cas.put("CIM10 N1", creer(ant("MEDICAL", "CIM10", "N1", null, d)));
        cas.put("CIM10 118 (commence par un chiffre)", creer(ant("MEDICAL", "CIM10", "118", null, d)));
        cas.put("LOINC 718-7", creer(ant("MEDICAL", "LOINC", "718-7", null, d)));
        cas.put("LOINC abc", creer(ant("MEDICAL", "LOINC", "abc", null, d)));
        cas.put("ATC j01ca04", creer(ant("MEDICAL", "ATC", "j01ca04", null, d)));
        cas.put("LOCAL x", creer(ant("MEDICAL", "LOCAL", "x", null, d)));
        cas.put("code sans systeme", creer(ant("MEDICAL", null, "B20", null, d)));
        cas.put("systeme inconnu ICD10", creer(ant("MEDICAL", "ICD10", "B20", null, d)));
        cas.put("systeme en minuscules cim10", creer(ant("MEDICAL", "cim10", "B20", null, d)));
        cas.put("libelle libre seul", creer(ant("MEDICAL", null, null, "Libelle libre seul", d)));
        cas.put("libelle libre avec systeme sans code", creer(ant("MEDICAL", "CIM10", null, "Libelle avec systeme", d)));
        cas.put("code et libelle libre ensemble", creer(ant("MEDICAL", "CIM10", "K70", "Libelle en plus du code", d)));
        cas.put("ni code ni libelle", creer(ant("MEDICAL", null, null, null, d)));
        cas.put("code blanc et libelle blanc", creer(ant("MEDICAL", "CIM10", "   ", "   ", d)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAntecedents());
        arbre("diagnostic", m);
    }

    @Test
    void les_cinq_types_d_antecedent_et_toute_autre_valeur_refusee() throws Exception {
        deuxCentres();
        LocalDate d = fixe(-20);
        Map<String, String> cas = new LinkedHashMap<>();
        for (String type : new String[]{"MEDICAL", "CHIRURGICAL", "FAMILIAL", "OBSTETRICAL", "COMORBIDITE", "AUTRE", "medical", ""}) {
            cas.put("type '" + type + "'", creer(ant(type, null, null, "Libelle " + type, d)));
        }
        cas.put("type absent", creer(ant(null, null, null, "Libelle sans type", d)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAntecedents());
        arbre("types", m);
    }

    @Test
    void les_dates_de_debut_et_de_fin_et_la_limite_du_futur() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("debut sans fin", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "A", "dateDebut", fixe(-10))));
        cas.put("debut absent", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "B")));
        cas.put("fin avant le debut", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "C", "dateDebut", fixe(-10),
                "dateFin", fixe(-20))));
        cas.put("fin le meme jour que le debut", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "D",
                "dateDebut", fixe(-10), "dateFin", fixe(-10))));
        cas.put("debut aujourd'hui (jour de la JVM)", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "E",
                "dateDebut", relatif(0))));
        cas.put("debut demain (jour de la JVM)", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "F",
                "dateDebut", relatif(1))));
        cas.put("fin dans le futur", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "G", "dateDebut", fixe(-10),
                "dateFin", relatif(30))));
        cas.put("debut en 1900", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "H",
                "dateDebut", alias(LocalDate.of(1900, 1, 1), "<1900>"))));
        cas.put("debut en 2200", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "I",
                "dateDebut", alias(LocalDate.of(2200, 1, 1), "<2200>"))));
        cas.put("debut illisible", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "J", "dateDebut", "demain")));
        cas.put("fin illisible", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "K", "dateDebut", fixe(-10),
                "dateFin", "bientot")));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAntecedents());
        arbre("dates", m);
    }

    @Test
    void un_diagnostic_actif_ne_peut_pas_etre_enregistre_deux_fois() throws Exception {
        deuxCentres();
        UUID autrePatient = patientDm5(CENTRE_A, "PAT_A2");
        LocalDate d = fixe(-40);
        Map<String, String> cas = new LinkedHashMap<>();
        Resp premier = doPost(antecedentsApi(patientA), ant("MEDICAL", "CIM10", "N18", null, d), medecinA());
        String id = idDe(premier);
        aliasNommes.put(id, "<ANT_1>");
        cas.put("1 premier CIM10 N18", refus(premier));
        cas.put("2 meme code actif", creer(ant("MEDICAL", "CIM10", "N18", null, fixe(-39))));
        cas.put("3 meme code en minuscules", creer(ant("MEDICAL", "CIM10", "n18", null, fixe(-38))));
        cas.put("4 meme code dans le systeme LOCAL", creer(ant("MEDICAL", "LOCAL", "N18", null, fixe(-37))));
        cas.put("5 meme libelle libre une fois", creer(ant("MEDICAL", null, null, "Libelle identique", fixe(-36))));
        cas.put("6 meme libelle libre une seconde fois", creer(ant("MEDICAL", null, null, "Libelle identique", fixe(-35))));
        cas.put("7 meme code, autre type d'antecedent", creer(ant("FAMILIAL", "CIM10", "N18", null, fixe(-34))));
        cas.put("8 meme code, autre patient du centre", refus(doPost(antecedentsApi(autrePatient),
                ant("MEDICAL", "CIM10", "N18", null, d), medecinA())));
        cas.put("9 meme code, patient de l'autre centre", refus(doPost(antecedentsApi(patientB),
                corps("centerId", CENTRE_B, "type", "MEDICAL", "codeSystem", "CIM10", "code", "N18", "dateDebut", d),
                as(CENTRE_B, "MEDECIN"))));
        cas.put("10 resolution du premier", refus(doPut(antecedentApi(patientA, UUID.fromString(id)) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", fixe(-30)), medecinA())));
        cas.put("11 meme code apres resolution", creer(ant("MEDICAL", "CIM10", "N18", null, fixe(-20))));
        cas.put("12 meme code une fois de plus (actif)", creer(ant("MEDICAL", "CIM10", "N18", null, fixe(-10))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAntecedents());
        arbre("doublons", m);
    }

    @Test
    void les_bornes_de_colonnes_ne_sont_pas_controlees_avant_la_base() throws Exception {
        deuxCentres();
        LocalDate d = fixe(-10);
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("severite 50 caracteres", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "S50", "dateDebut", d,
                "severite", "s".repeat(50))));
        cas.put("severite 51 caracteres", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "S51", "dateDebut", d,
                "severite", "s".repeat(51))));
        cas.put("libelle 255 caracteres", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "l".repeat(255),
                "dateDebut", d)));
        cas.put("libelle 256 caracteres", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "m".repeat(256),
                "dateDebut", d)));
        cas.put("affichage du code 255 caracteres", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "codeSystem", "LOCAL", "code", "C1",
                "codeDisplay", "a".repeat(255), "dateDebut", d)));
        cas.put("affichage du code 256 caracteres", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "codeSystem", "LOCAL", "code", "C2",
                "codeDisplay", "a".repeat(256), "dateDebut", d)));
        cas.put("code LOCAL 20 caracteres", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "codeSystem", "LOCAL", "code", "c".repeat(20),
                "dateDebut", d)));
        cas.put("code LOCAL 21 caracteres", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "codeSystem", "LOCAL", "code", "d".repeat(21),
                "dateDebut", d)));
        cas.put("note de 10000 caracteres (TEXT)", creer(corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "NOTE", "dateDebut", d,
                "note", "n".repeat(10000))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("lignes enregistrees", jdbc.queryForObject("SELECT COUNT(*) FROM antecedents_medicaux WHERE center_id = ?", Integer.class,
                CENTRE_A));
        arbre("bornes", m);
    }

    @Test
    void un_corps_illisible_ou_un_patient_inconnu() throws Exception {
        deuxCentres();
        UUID inconnu = idStable("patient", "inconnu");
        aliasNommes.put(inconnu.toString(), "<PAT_INCONNU>");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("corps absent", refus(doPost(antecedentsApi(patientA), "", medecinA())));
        cas.put("corps vide {}", creer("{}"));
        cas.put("corps tableau []", creer("[]"));
        cas.put("corps illisible", creer("{pas du json"));
        cas.put("centre illisible", creer("{\"centerId\":\"pas-un-uuid\",\"type\":\"MEDICAL\",\"libelleLibre\":\"X\"}"));
        cas.put("identifiant de patient illisible", refus(doPost(PATIENTS_API + "/abc/antecedents",
                ant("MEDICAL", null, null, "Patient illisible", fixe(-5)), medecinA())));
        cas.put("patient inconnu", refus(doPost(antecedentsApi(inconnu), ant("MEDICAL", null, null, "Patient inconnu", fixe(-5)), medecinA())));
        cas.put("patient d'un autre centre (session A)", refus(doPost(antecedentsApi(patientB),
                ant("MEDICAL", null, null, "Patient du centre B", fixe(-5)), medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAntecedents());
        arbre("corps_et_patient", m);
        assertThat(nombreDe("antecedents_medicaux", CENTRE_B)).isZero();
    }
}
