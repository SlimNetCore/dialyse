package com.hemodialyse.backend.characterization.dossiermedical.dm5;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DM5, lot 1 (antécédents), consultation, correction, résolution et suppression :
 * {@code GET}, {@code PUT}, {@code PUT .../resoudre} et {@code DELETE} sur {@code /api/v1/patients/{id}/antecedents}.
 */
class LaConsultationEtLaResolutionDesAntecedentsCharacterizationIntegrationTest extends Dm5CharacterizationSupport {

    private static final String CHAMPS = "libelleLibre";

    @Override
    protected String dossier() {
        return "antecedents/consultation";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm5(CENTRE_A, "PAT_A");
        patientB = patientDm5(CENTRE_B, "PAT_B");
    }

    private String lire(Resp r) {
        return resume(r, "type", "codeSystem", "code", "libelleLibre", "dateDebut", "dateFin", "statutClinique", "severite", "note");
    }

    @Test
    void la_liste_est_triee_du_debut_le_plus_recent_et_paginee() throws Exception {
        deuxCentres();
        for (int i = 1; i <= 5; i++) {
            antecedentEnBase(CENTRE_A, patientA, "ANT_" + i, "MEDICAL", null, null, "Antecedent " + i, fixe(i * 10),
                    i % 2 == 0 ? fixe(i * 10 + 5) : null, i % 2 == 0 ? "RESOLU" : "ACTIF");
        }
        antecedentEnBase(CENTRE_A, patientB, "ANT_AUTRE_PATIENT", "MEDICAL", null, null, "Autre patient", fixe(99), null, "ACTIF");
        antecedentEnBase(CENTRE_B, patientB, "ANT_AUTRE_CENTRE", "MEDICAL", null, null, "Autre centre", fixe(98), null, "ACTIF");
        String url = antecedentsApi(patientA);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("defaut", page(doGet(url, medecinA()), CHAMPS, "dateDebut", "statutClinique"));
        m.put("taille 2, page 0", page(doGet(url + "?size=2&page=0", medecinA()), CHAMPS));
        m.put("taille 2, page 2", page(doGet(url + "?size=2&page=2", medecinA()), CHAMPS));
        m.put("taille 2, page 9", page(doGet(url + "?size=2&page=9", medecinA()), CHAMPS));
        m.put("taille 2, page -1", page(doGet(url + "?size=2&page=-1", medecinA()), CHAMPS));
        m.put("taille 0", page(doGet(url + "?size=0", medecinA()), CHAMPS));
        m.put("taille -3", page(doGet(url + "?size=-3", medecinA()), CHAMPS));
        m.put("taille 1000", page(doGet(url + "?size=1000", medecinA()), CHAMPS));
        m.put("taille illisible", refus(doGet(url + "?size=abc", medecinA())));
        m.put("liste pour l'ADMIN", page(doGet(url + "?size=1", as(CENTRE_A, "ADMIN")), CHAMPS));
        arbre("pages", m);
    }

    @Test
    void la_lecture_rend_la_forme_complete_d_un_antecedent() throws Exception {
        deuxCentres();
        Resp c = doPost(antecedentsApi(patientA), corps("centerId", CENTRE_A, "type", "CHIRURGICAL", "codeSystem", "CIM10", "code", "Z94.0",
                "codeDisplay", "Greffe fictive", "libelleLibre", "Libelle", "dateDebut", fixe(-60), "dateFin", fixe(-30),
                "severite", "FORTE", "note", "Note fictive"), medecinA());
        aliasNommes.put(idDe(c), "<ANT_1>");
        antecedentEnBase(CENTRE_A, patientA, "ANT_2", "FAMILIAL", null, null, "Antecedent minimal", fixe(-10), null, "INACTIF");
        lecture("liste", doGet(antecedentsApi(patientA), medecinA()));
    }

    @Test
    void la_correction_remplace_les_champs_du_corps_et_efface_les_absents() throws Exception {
        deuxCentres();
        UUID a = UUID.fromString(idDe(doPost(antecedentsApi(patientA), corps("centerId", CENTRE_A, "type", "MEDICAL", "codeSystem", "CIM10",
                "code", "N18", "codeDisplay", "Affichage", "dateDebut", fixe(-100), "severite", "MODEREE", "note", "Note initiale"),
                medecinA())));
        aliasNommes.put(a.toString(), "<ANT_1>");
        UUID autre = antecedentEnBase(CENTRE_A, patientB, "ANT_AUTRE_PATIENT", "MEDICAL", null, null, "Autre patient", fixe(1), null, "ACTIF");
        String url = antecedentApi(patientA, a);
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("1 libelle libre sans code ni severite ni note", lire(doPut(url, corps("centerId", CENTRE_A, "libelleLibre", "Libre",
                "dateDebut", fixe(-90)), medecinA())));
        cas.put("2 le type du corps est ignore", lire(doPut(url, corps("centerId", CENTRE_A, "type", "CHIRURGICAL", "libelleLibre", "Libre",
                "dateDebut", fixe(-90)), medecinA())));
        cas.put("3 statut INACTIF", lire(doPut(url, corps("centerId", CENTRE_A, "libelleLibre", "Libre", "dateDebut", fixe(-90),
                "statutClinique", "INACTIF"), medecinA())));
        cas.put("4 statut absent : conserve INACTIF", lire(doPut(url, corps("centerId", CENTRE_A, "libelleLibre", "Libre",
                "dateDebut", fixe(-90), "severite", "FAIBLE"), medecinA())));
        cas.put("5 statut RESOLU sans date de fin", lire(doPut(url, corps("centerId", CENTRE_A, "libelleLibre", "Libre",
                "dateDebut", fixe(-90), "statutClinique", "RESOLU"), medecinA())));
        cas.put("6 retour a ACTIF avec une date de fin", lire(doPut(url, corps("centerId", CENTRE_A, "libelleLibre", "Libre",
                "dateDebut", fixe(-90), "dateFin", fixe(-80), "statutClinique", "ACTIF"), medecinA())));
        cas.put("7 statut inconnu", lire(doPut(url, corps("centerId", CENTRE_A, "libelleLibre", "Libre", "dateDebut", fixe(-90),
                "statutClinique", "GUERI"), medecinA())));
        cas.put("8 statut en minuscules", lire(doPut(url, corps("centerId", CENTRE_A, "libelleLibre", "Libre", "dateDebut", fixe(-90),
                "statutClinique", "actif"), medecinA())));
        cas.put("9 ni code ni libelle", lire(doPut(url, corps("centerId", CENTRE_A, "dateDebut", fixe(-90)), medecinA())));
        cas.put("10 debut absent", lire(doPut(url, corps("centerId", CENTRE_A, "libelleLibre", "Libre"), medecinA())));
        cas.put("11 fin avant le debut", lire(doPut(url, corps("centerId", CENTRE_A, "libelleLibre", "Libre", "dateDebut", fixe(-90),
                "dateFin", fixe(-95)), medecinA())));
        cas.put("12 debut dans le futur (pas de limite a la correction)", lire(doPut(url, corps("centerId", CENTRE_A, "libelleLibre", "Libre",
                "dateDebut", relatif(30)), medecinA())));
        cas.put("13 code CIM10 invalide", lire(doPut(url, corps("centerId", CENTRE_A, "codeSystem", "CIM10", "code", "zzz",
                "dateDebut", fixe(-90)), medecinA())));
        cas.put("14 code sans systeme", lire(doPut(url, corps("centerId", CENTRE_A, "code", "N18", "dateDebut", fixe(-90)), medecinA())));
        cas.put("15 antecedent inconnu", lire(doPut(antecedentApi(patientA, idStable("antecedent", "inconnu")),
                corps("centerId", CENTRE_A, "libelleLibre", "Libre", "dateDebut", fixe(-90)), medecinA())));
        cas.put("16 antecedent d'un autre patient", lire(doPut(antecedentApi(patientA, autre),
                corps("centerId", CENTRE_A, "libelleLibre", "Libre", "dateDebut", fixe(-90)), medecinA())));
        cas.put("17 corps absent", lire(doPut(url, "", medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAntecedents());
        arbre("correction", m);
    }

    @Test
    void la_correction_ne_garde_l_unicite_que_pour_les_autres_antecedents_actifs() throws Exception {
        deuxCentres();
        UUID n18 = antecedentEnBase(CENTRE_A, patientA, "ANT_N18", "MEDICAL", "CIM10", "N18", null, fixe(10), null, "ACTIF");
        UUID e11 = antecedentEnBase(CENTRE_A, patientA, "ANT_E11", "MEDICAL", "CIM10", "E11", null, fixe(20), null, "ACTIF");
        UUID i10 = antecedentEnBase(CENTRE_A, patientA, "ANT_I10", "MEDICAL", "CIM10", "I10", null, fixe(30), fixe(35), "RESOLU");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("1 E11 prend le code de N18 (actif)", lire(doPut(antecedentApi(patientA, e11), corps("centerId", CENTRE_A, "codeSystem", "CIM10",
                "code", "N18", "dateDebut", fixe(20)), medecinA())));
        cas.put("2 E11 garde son propre code", lire(doPut(antecedentApi(patientA, e11), corps("centerId", CENTRE_A, "codeSystem", "CIM10",
                "code", "E11", "dateDebut", fixe(20), "note", "corrigee"), medecinA())));
        cas.put("3 E11 prend le code de I10 (resolu)", lire(doPut(antecedentApi(patientA, e11), corps("centerId", CENTRE_A, "codeSystem", "CIM10",
                "code", "I10", "dateDebut", fixe(20)), medecinA())));
        cas.put("4 I10 resolu rouvert alors que E11 porte deja I10", lire(doPut(antecedentApi(patientA, i10), corps("centerId", CENTRE_A,
                "codeSystem", "CIM10", "code", "I10", "dateDebut", fixe(30), "statutClinique", "ACTIF"), medecinA())));
        cas.put("5 N18 en LOCAL N18 (autre systeme)", lire(doPut(antecedentApi(patientA, n18), corps("centerId", CENTRE_A, "codeSystem", "LOCAL",
                "code", "N18", "dateDebut", fixe(10)), medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAntecedents());
        arbre("unicite", m);
    }

    @Test
    void la_resolution_fixe_la_fin_refuse_le_rejeu_et_la_fin_avant_le_debut() throws Exception {
        deuxCentres();
        UUID autre = antecedentEnBase(CENTRE_A, patientB, "ANT_AUTRE_PATIENT", "MEDICAL", null, null, "Autre patient", fixe(1), null, "ACTIF");
        Map<String, UUID> a = new LinkedHashMap<>();
        for (String alias : new String[]{"ACTIF", "INACTIF", "AVEC_FIN", "DEJA_RESOLU", "SANS_DATE", "FUTUR", "MEME_JOUR", "AVANT", "INCONNU"}) {
            a.put(alias, antecedentEnBase(CENTRE_A, patientA, "ANT_" + alias, "MEDICAL", null, null, "Antecedent " + alias, fixe(a.size() * 10 + 10),
                    alias.equals("AVEC_FIN") ? fixe(95) : null, alias.equals("DEJA_RESOLU") ? "RESOLU" : alias.equals("INACTIF") ? "INACTIF" : "ACTIF"));
        }
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("1 actif resolu apres son debut", lire(doPut(antecedentApi(patientA, a.get("ACTIF")) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", fixe(15)), medecinA())));
        cas.put("2 rejeu sur le meme antecedent", lire(doPut(antecedentApi(patientA, a.get("ACTIF")) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", fixe(16)), medecinA())));
        cas.put("3 inactif resolu", lire(doPut(antecedentApi(patientA, a.get("INACTIF")) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", fixe(25)), medecinA())));
        cas.put("4 la date de resolution remplace la fin existante", lire(doPut(antecedentApi(patientA, a.get("AVEC_FIN")) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", fixe(35)), medecinA())));
        cas.put("5 deja resolu a la creation de la fixture", lire(doPut(antecedentApi(patientA, a.get("DEJA_RESOLU")) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", fixe(45)), medecinA())));
        cas.put("6 sans date de resolution", lire(doPut(antecedentApi(patientA, a.get("SANS_DATE")) + "/resoudre",
                corps("centerId", CENTRE_A), medecinA())));
        cas.put("7 date de resolution dans le futur", lire(doPut(antecedentApi(patientA, a.get("FUTUR")) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", relatif(30)), medecinA())));
        cas.put("8 resolution le jour du debut", lire(doPut(antecedentApi(patientA, a.get("MEME_JOUR")) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", fixe(70)), medecinA())));
        cas.put("9 resolution avant le debut", lire(doPut(antecedentApi(patientA, a.get("AVANT")) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", fixe(10)), medecinA())));
        cas.put("10 antecedent inconnu", lire(doPut(antecedentApi(patientA, idStable("antecedent", "inconnu")) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", fixe(15)), medecinA())));
        cas.put("11 antecedent d'un autre patient", lire(doPut(antecedentApi(patientA, autre) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", fixe(15)), medecinA())));
        cas.put("12 corps absent", lire(doPut(antecedentApi(patientA, a.get("INCONNU")) + "/resoudre", "", medecinA())));
        cas.put("13 date illisible", lire(doPut(antecedentApi(patientA, a.get("INCONNU")) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", "demain"), medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAntecedents());
        arbre("resolution", m);
    }

    @Test
    void la_suppression_rend_204_meme_quand_rien_n_est_supprime() throws Exception {
        deuxCentres();
        UUID a1 = antecedentEnBase(CENTRE_A, patientA, "ANT_1", "MEDICAL", "CIM10", "N18", null, fixe(10), null, "ACTIF");
        antecedentEnBase(CENTRE_A, patientA, "ANT_2", "MEDICAL", null, null, "Resolu", fixe(20), fixe(25), "RESOLU");
        UUID autrePatient = antecedentEnBase(CENTRE_A, patientB, "ANT_AUTRE_PATIENT", "MEDICAL", null, null, "Autre patient", fixe(30), null, "ACTIF");
        UUID autreCentre = antecedentEnBase(CENTRE_B, patientB, "ANT_AUTRE_CENTRE", "MEDICAL", null, null, "Autre centre", fixe(40), null, "ACTIF");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("1 suppression d'un antecedent", refus(doDelete(antecedentApi(patientA, a1), medecinA())));
        cas.put("2 meme suppression rejouee", refus(doDelete(antecedentApi(patientA, a1), medecinA())));
        cas.put("3 identifiant inconnu", refus(doDelete(antecedentApi(patientA, idStable("antecedent", "inconnu")), medecinA())));
        cas.put("4 antecedent d'un autre patient du centre", refus(doDelete(antecedentApi(patientA, autrePatient), medecinA())));
        cas.put("5 antecedent d'un autre centre (centre B demande)", refus(doDelete(antecedentApi(patientB, autreCentre) + "?centerId=" + CENTRE_B,
                medecinA())));
        cas.put("6 identifiant illisible", refus(doDelete(antecedentsApi(patientA) + "/abc", medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAntecedents());
        arbre("suppression", m);
    }
}
