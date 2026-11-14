package com.hemodialyse.backend.characterization.seance.se7;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SE7a : le journal d'un jour du centre ({@code GET /api/v1/seances/journal?centerId=&dateSeance=}, servi par {@code SeanceRestController}
 * en SQL direct sur {@code seances} et {@code patients}, plus le langage publié de Stock pour les sorties d'articles) : contenu, tri,
 * cloisonnement, paramètres, droits. Le garde de centre est déjà verrouillé par DM1 ; seul son contenu l'est ici. Décrit le comportement
 * ACTUEL.
 */
class LeJournalDuJourCharacterizationIntegrationTest extends Se7CharacterizationSupport {

    @Override
    protected String dossier() {
        return "journal";
    }

    @Test
    void le_journal_liste_les_patients_du_jour_par_nom_et_les_sorties_d_articles_sans_les_autres_jours_ni_l_autre_centre() throws Exception {
        UUID beta = patientCouvert(CENTRE_A, "BETA");
        UUID alpha = patientCouvert(CENTRE_A, "ALPHA");
        UUID gamma = patientCouvert(CENTRE_A, "GAMMA");
        UUID autreCentre = patientCouvert(CENTRE_B, "DELTA");
        UUID article1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID lot1 = lotSe2(CENTRE_A, article1, "LOT_1", "10");
        UUID article2 = articleSe2(CENTRE_A, "ART_2", true, "10");
        UUID lot2 = lotSe2(CENTRE_A, article2, "LOT_2", "10");
        UUID articleB = articleSe2(CENTRE_B, "ART_B", true, "10");
        UUID lotB = lotSe2(CENTRE_B, articleB, "LOT_B", "10");
        UUID sBeta = seanceValidee(CENTRE_A, beta, "S_BETA", jour(0), "VALIDEE");
        seanceValidee(CENTRE_A, alpha, "S_ALPHA", jour(0), "SIGNEE");
        seanceValidee(CENTRE_A, gamma, "S_GAMMA", jour(0), "CREE");
        UUID sHier = seanceValidee(CENTRE_A, gamma, "S_GAMMA_HIER", jour(-1), "SIGNEE");
        UUID sDelta = seanceValidee(CENTRE_B, autreCentre, "S_DELTA", jour(0), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 journal sans aucun bon de sortie : liste d'articles vide", reponse(journal(CENTRE_A, jour(0), infirmierA())));
        // « sortiesArticles » lit les BONS DE SORTIE de Stock datés du jour (bons_sortie), pas les consommables déclarés sur les séances.
        bonDeSortie(CENTRE_A, sBeta, beta, jour(0), article2, lot2, "4");
        bonDeSortie(CENTRE_A, sBeta, beta, jour(0), article1, lot1, "2");
        bonDeSortie(CENTRE_A, sBeta, beta, jour(0), article1, lot1, "1");
        bonDeSortie(CENTRE_A, sHier, gamma, jour(-1), article1, lot1, "7");
        bonDeSortie(CENTRE_B, sDelta, autreCentre, jour(0), articleB, lotB, "9");
        m.put("02 consommable declare sur BETA : n'alimente pas les sorties", refus(ajouter(sBeta, article1, 3, infirmierA())));
        m.put("03 journal du jour avec trois bons (cumul par article, tri par code)", reponse(journal(CENTRE_A, jour(0), infirmierA())));
        m.put("04 journal de la veille", reponse(journal(CENTRE_A, jour(-1), infirmierA())));
        m.put("05 journal d'un jour sans seance", reponse(journal(CENTRE_A, jour(5), infirmierA())));
        m.put("06 journal du centre B lu par la session B", reponse(journal(CENTRE_B, jour(0), infirmierB())));
        arbre("contenu", m);
    }

    /**
     * LEC-PAT (i) : cas limites du tri et de la jointure, posés AVANT de remplacer la lecture SQL de {@code patients} par le langage publié
     * de Patient et couverture. Homonymes (même nom, prénoms différents ; mêmes nom et prénom : départage par l'identifiant de séance),
     * patient transféré, patient d'un autre centre ayant une séance dans ce centre (jointure sur l'identifiant seul, reproduit).
     */
    @Test
    void le_journal_depart_les_homonymes_par_prenom_puis_identifiant_de_seance_et_garde_les_patients_sortis_ou_d_un_autre_centre() throws Exception {
        UUID marie = patientCouvert(CENTRE_A, "HOM_MARIE");
        UUID anne = patientCouvert(CENTRE_A, "HOM_ANNE");
        UUID jumeau1 = patientCouvert(CENTRE_A, "HOM_JUMEAU1");
        UUID jumeau2 = patientCouvert(CENTRE_A, "HOM_JUMEAU2");
        UUID jumeau3 = patientCouvert(CENTRE_A, "HOM_JUMEAU3");
        UUID transfere = patientCouvert(CENTRE_A, "HOM_TRANSFERE");
        UUID autreCentre = patientCouvert(CENTRE_B, "HOM_AUTRE_CENTRE");
        UUID zed = patientCouvert(CENTRE_A, "HOM_ZED");
        nommer(marie, "Dupont", "Marie");
        nommer(anne, "Dupont", "Anne");
        nommer(jumeau1, "Martin", "Paul");
        nommer(jumeau2, "Martin", "Paul");
        nommer(jumeau3, "Martin", "Paul");
        nommer(transfere, "Bernard", "Luc");
        nommer(autreCentre, "Aubert", "Eve");
        nommer(zed, "Zed", "");
        jdbc.update("UPDATE patients SET etat_patient = 'TRANSFERE', date_evenement_etat = ? WHERE id = ?", java.sql.Date.valueOf(jour(-30)), transfere);
        seanceValidee(CENTRE_A, marie, "S_HOM_MARIE", jour(0), "VALIDEE");
        seanceValidee(CENTRE_A, anne, "S_HOM_ANNE", jour(0), "SIGNEE");
        seanceValidee(CENTRE_A, jumeau3, "S_HOM_JUMEAU3", jour(0), "CREE");
        seanceValidee(CENTRE_A, jumeau1, "S_HOM_JUMEAU1", jour(0), "FACTUREE");
        seanceValidee(CENTRE_A, jumeau2, "S_HOM_JUMEAU2", jour(0), "VALIDEE");
        seanceValidee(CENTRE_A, transfere, "S_HOM_TRANSFERE", jour(0), "VALIDEE");
        seanceValidee(CENTRE_A, autreCentre, "S_HOM_AUTRE_CENTRE", jour(0), "VALIDEE");
        seanceValidee(CENTRE_A, zed, "S_HOM_ZED", jour(0), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 journal du centre A", reponse(journal(CENTRE_A, jour(0), infirmierA())));
        m.put("02 journal du centre B : la seance du patient B est dans le centre A", reponse(journal(CENTRE_B, jour(0), infirmierB())));
        arbre("homonymes_et_patients_sortis", m);
    }

    private void nommer(UUID patient, String nom, String prenom) {
        jdbc.update("UPDATE patients SET nom = ?, prenom = ? WHERE id = ?", nom, prenom, patient);
    }

    @Test
    void les_parametres_du_journal_manquants_ou_illisibles() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 date absente", refus(journal(CENTRE_A, null, infirmierA())));
        m.put("02 date illisible", refus(journal(CENTRE_A, "demain", infirmierA())));
        m.put("03 date au format jour/mois/annee", refus(journal(CENTRE_A, "31%2F12%2F2026", infirmierA())));
        m.put("04 date vide", refus(journal(CENTRE_A, "", infirmierA())));
        m.put("05 centre absent", refus(doGet(SEANCES_API + "/journal?dateSeance=" + jour(0), infirmierA())));
        m.put("06 centre illisible", refus(doGet(SEANCES_API + "/journal?centerId=xx&dateSeance=" + jour(0), infirmierA())));
        m.put("07 date lointaine (an 9999)", refus(journal(CENTRE_A, "9999-12-31", infirmierA())));
        arbre("parametres", m);
    }

    @Test
    void les_droits_du_journal_par_profil() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        Map<String, String> issues = new LinkedHashMap<>();
        for (Map.Entry<String, org.springframework.test.web.servlet.request.RequestPostProcessor> p : profilsSe1().entrySet()) {
            issues.put(p.getKey(), refus(journal(CENTRE_A, jour(0), p.getValue())));
        }
        issues.put("anonyme", refus(doGetAnonyme(SEANCES_API + "/journal?centerId=" + CENTRE_A + "&dateSeance=" + jour(0))));
        arbre("droits", issues);
    }
}
