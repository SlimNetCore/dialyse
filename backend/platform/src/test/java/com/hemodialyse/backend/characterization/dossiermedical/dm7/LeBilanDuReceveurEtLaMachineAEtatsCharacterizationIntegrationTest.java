package com.hemodialyse.backend.characterization.dossiermedical.dm7;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM7, lot 1 (bilan du receveur) : ouverture à la première lecture (get-or-create, y compris sur une lecture), machine à états du
 * statut, bilan immunologique, notes, ligne illisible. Routes {@code greffe/bilan}, {@code greffe/bilan/statut},
 * {@code greffe/bilan/immunologique}, {@code greffe/bilan/notes}.
 */
class LeBilanDuReceveurEtLaMachineAEtatsCharacterizationIntegrationTest extends Dm7CharacterizationSupport {

    @Override
    protected String dossier() {
        return "bilan/etat";
    }

    private static final String[] STATUTS = {"NON_DEBUTE", "BILAN_EN_COURS", "ELIGIBLE", "INSCRIT_LISTE_ATTENTE",
            "CONTRE_INDICATION_TEMPORAIRE", "CONTRE_INDICATION_DEFINITIVE", "GREFFE_REALISEE"};

    private String statut(UUID centre, String valeur) {
        return corps("centerId", centre, "statut", valeur);
    }

    @Test
    void la_premiere_lecture_ouvre_un_bilan_non_debute_et_la_lecture_est_donc_une_ecriture() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        UUID autre = patientDm7(CENTRE_A, "PAT_B");
        assertThat(nombreDe("bilans_pre_greffe", CENTRE_A)).isZero();
        Map<String, Object> m = new LinkedHashMap<>();
        Resp premiere = doGet(bilanApi(patient), medecinA());
        m.put("01 premiere lecture MEDECIN", reponse(premiere));
        m.put("02 etat apres la premiere lecture", etatDesBilans());
        Resp deuxieme = doGet(bilanApi(patient), medecinA());
        m.put("03 meme identifiant a la deuxieme lecture", idDe(premiere).equals(idDe(deuxieme)));
        m.put("04 une seule ligne", nombreDe("bilans_pre_greffe", CENTRE_A));
        m.put("05 lecture ADMIN d'un autre patient", refus(doGet(bilanApi(autre), as(CENTRE_A, "ADMIN"))));
        m.put("06 lecture d'un patient inconnu (aucune garde de patient)", refus(doGet(bilanApi(idStable("patient", "inconnu")), medecinA())));
        m.put("07 lignes apres les trois ouvertures", nombreDe("bilans_pre_greffe", CENTRE_A));
        m.put("08 lecture refusee INFIRMIER n'ouvre rien", refus(doGet(bilanApi(idStable("patient", "refuse")), infirmierA())));
        m.put("09 lignes apres le refus", nombreDe("bilans_pre_greffe", CENTRE_A));
        arbre("ouverture", m);
    }

    @Test
    void la_matrice_complete_des_transitions_de_statut_et_les_dates_posees() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        relatif(0);
        Map<String, Object> m = new LinkedHashMap<>();
        for (String depuis : STATUTS) {
            Map<String, String> ligne = new LinkedHashMap<>();
            for (String vers : STATUTS) {
                nettoyerLesBilans();
                bilanEnBase(CENTRE_A, patient, "BIL", depuis, fixe(3), fixe(4), fixe(5), null, null, null, null, null, null);
                Resp r = doPut(bilanApi(patient) + "/statut", statut(CENTRE_A, vers), medecinA());
                ligne.put(vers, resume(r, "statut", "dateDebutBilan", "dateInscriptionListeAttente", "dateGreffe"));
            }
            m.put(depuis, ligne);
        }
        arbre("transitions", m);
    }

    @Test
    void les_dates_de_debut_d_inscription_et_de_greffe_suivent_le_statut_atteint() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        relatif(0);
        Map<String, Object> m = new LinkedHashMap<>();
        // Parcours nominal sur un bilan ouvert par la route : les dates sont celles du jour de la JVM.
        for (String vers : List.of("BILAN_EN_COURS", "ELIGIBLE", "INSCRIT_LISTE_ATTENTE", "GREFFE_REALISEE")) {
            m.put("nominal -> " + vers, resume(doPut(bilanApi(patient) + "/statut", statut(CENTRE_A, vers), medecinA()),
                    "statut", "dateDebutBilan", "dateInscriptionListeAttente", "dateGreffe"));
        }
        m.put("etat final", etatDesBilans());
        // Une contre-indication temporaire puis la reprise conserve la date de début existante.
        nettoyerLesBilans();
        bilanEnBase(CENTRE_A, patient, "BIL2", "CONTRE_INDICATION_TEMPORAIRE", fixe(3), null, null, null, null, null, null, null, null);
        m.put("reprise : la date de debut existante est conservee", resume(doPut(bilanApi(patient) + "/statut",
                statut(CENTRE_A, "BILAN_EN_COURS"), medecinA()), "statut", "dateDebutBilan"));
        // Un bilan en cours sans date de début (ligne importée) reçoit la date du jour.
        nettoyerLesBilans();
        bilanSimple(CENTRE_A, patient, "BIL3", "CONTRE_INDICATION_TEMPORAIRE");
        m.put("reprise sans date de debut : date du jour", resume(doPut(bilanApi(patient) + "/statut",
                statut(CENTRE_A, "BILAN_EN_COURS"), medecinA()), "statut", "dateDebutBilan"));
        // Un statut inchangé est une transition permise ; la date d'inscription est ré-écrite à chaque appel.
        nettoyerLesBilans();
        bilanEnBase(CENTRE_A, patient, "BIL4", "INSCRIT_LISTE_ATTENTE", null, fixe(4), null, null, null, null, null, null, null);
        m.put("inscrit -> inscrit : la date d'inscription est ecrasee", resume(doPut(bilanApi(patient) + "/statut",
                statut(CENTRE_A, "INSCRIT_LISTE_ATTENTE"), medecinA()), "statut", "dateInscriptionListeAttente"));
        arbre("dates", m);
    }

    @Test
    void les_statuts_et_les_corps_illisibles() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        bilanSimple(CENTRE_A, patient, "BIL", "NON_DEBUTE");
        String avant = empreinteGreffe();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("01 statut inconnu", refus(doPut(bilanApi(patient) + "/statut", statut(CENTRE_A, "BIDON"), medecinA())));
        cas.put("02 statut en minuscules", refus(doPut(bilanApi(patient) + "/statut", statut(CENTRE_A, "bilan_en_cours"), medecinA())));
        cas.put("03 statut vide", refus(doPut(bilanApi(patient) + "/statut", statut(CENTRE_A, ""), medecinA())));
        cas.put("04 statut absent", refus(doPut(bilanApi(patient) + "/statut", corps("centerId", CENTRE_A), medecinA())));
        cas.put("05 corps vide", refus(doPut(bilanApi(patient) + "/statut", "", medecinA())));
        cas.put("06 corps illisible", refus(doPut(bilanApi(patient) + "/statut", "{pas du json", medecinA())));
        cas.put("07 statut de type nombre", refus(doPut(bilanApi(patient) + "/statut", "{\"statut\": 3}", medecinA())));
        cas.put("08 identifiant de patient illisible", refus(doPut(PATIENTS_API + "/pas-un-uuid/greffe/bilan/statut",
                statut(CENTRE_A, "BILAN_EN_COURS"), medecinA())));
        cas.put("09 lecture avec centerId illisible", refus(doGet(bilanApi(patient) + "?centerId=abc", medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("ecriture", empreinteGreffe().equals(avant) ? "aucune ligne modifiee" : "le bilan a ete modifie");
        arbre("statuts_illisibles", m);
    }

    @Test
    void le_bilan_immunologique_remplace_les_quatre_champs_et_borne_les_valeurs() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        String[] champs = {"groupeSanguinConfirme", "typageHla", "praClasseI", "praClasseII"};
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 saisie complete sur un patient sans bilan (ouvre le bilan)", resume(doPut(bilanApi(patient) + "/immunologique",
                corps("centerId", CENTRE_A, "groupeSanguinConfirme", "A+", "typageHla", "A1 A2 B7 B8 DR1 DR4", "praClasseI", 12.5, "praClasseII", 80),
                medecinA()), champs));
        m.put("02 etat apres la saisie", etatDesBilans());
        m.put("03 saisie partielle : les champs absents sont effaces", resume(doPut(bilanApi(patient) + "/immunologique",
                corps("centerId", CENTRE_A, "groupeSanguinConfirme", "O-"), medecinA()), champs));
        Map<String, String> bornes = new LinkedHashMap<>();
        for (Object pra : new Object[]{100, 0, 100.01, -5, 12.345, 12.355, 999.99, 1000}) {
            Resp r = doPut(bilanApi(patient) + "/immunologique", corps("centerId", CENTRE_A, "praClasseI", pra), medecinA());
            String colonne = jdbc.queryForObject("SELECT CAST(pra_classe_i AS VARCHAR(20)) FROM bilans_pre_greffe WHERE center_id = ?",
                    String.class, CENTRE_A);
            bornes.put("pra " + pra + " (reponse | colonne relue)", resume(r, "praClasseI") + " | colonne " + colonne);
        }
        bornes.put("pra texte", refus(doPut(bilanApi(patient) + "/immunologique", "{\"praClasseI\": \"beaucoup\"}", medecinA())));
        bornes.put("groupe sanguin 5 caracteres", resume(doPut(bilanApi(patient) + "/immunologique", corps("centerId", CENTRE_A, "groupeSanguinConfirme", "ABCDE"),
                medecinA()), champs));
        bornes.put("groupe sanguin 6 caracteres", refus(doPut(bilanApi(patient) + "/immunologique", corps("centerId", CENTRE_A, "groupeSanguinConfirme", "ABCDEF"),
                medecinA())));
        bornes.put("groupe sanguin libre (aucune liste de valeurs)", resume(doPut(bilanApi(patient) + "/immunologique",
                corps("centerId", CENTRE_A, "groupeSanguinConfirme", "Z9"), medecinA()), champs));
        bornes.put("typage 500 caracteres", refus(doPut(bilanApi(patient) + "/immunologique", corps("centerId", CENTRE_A, "typageHla", "H".repeat(500)),
                medecinA())));
        bornes.put("typage 501 caracteres", refus(doPut(bilanApi(patient) + "/immunologique", corps("centerId", CENTRE_A, "typageHla", "H".repeat(501)),
                medecinA())));
        bornes.put("corps vide", refus(doPut(bilanApi(patient) + "/immunologique", "", medecinA())));
        bornes.put("corps illisible", refus(doPut(bilanApi(patient) + "/immunologique", "[1,2", medecinA())));
        m.put("04 bornes", bornes);
        m.put("05 lignes apres les refus", nombreDe("bilans_pre_greffe", CENTRE_A));
        arbre("immunologique", m);
    }

    @Test
    void les_notes_remplacent_les_deux_textes_sans_limite_et_ouvrent_le_bilan() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        String[] champs = {"contreIndications", "conclusionNephrologue"};
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 notes sur un patient sans bilan", resume(doPut(bilanApi(patient) + "/notes",
                corps("centerId", CENTRE_A, "contreIndications", "Aucune connue", "conclusionNephrologue", "Candidat favorable"), medecinA()), champs));
        m.put("02 un seul texte : l'autre est efface", resume(doPut(bilanApi(patient) + "/notes",
                corps("centerId", CENTRE_A, "conclusionNephrologue", "A revoir"), medecinA()), champs));
        m.put("03 deux textes absents", resume(doPut(bilanApi(patient) + "/notes", corps("centerId", CENTRE_A), medecinA()), champs));
        m.put("04 texte de 5000 caracteres (colonne TEXT)", doPut(bilanApi(patient) + "/notes",
                corps("centerId", CENTRE_A, "contreIndications", "x".repeat(5000)), medecinA()).status());
        m.put("05 longueur relue", jdbc.queryForObject("SELECT LENGTH(contre_indications) FROM bilans_pre_greffe WHERE center_id = ?", Integer.class, CENTRE_A));
        m.put("06 texte vide conserve tel quel", resume(doPut(bilanApi(patient) + "/notes", corps("centerId", CENTRE_A, "contreIndications", ""), medecinA()),
                champs));
        m.put("07 etat final", etatDesBilans());
        arbre("notes", m);
    }

    @Test
    void les_lignes_illisibles_ou_en_double_font_echouer_la_lecture_du_bilan() throws Exception {
        UUID patientStatut = patientDm7(CENTRE_A, "PAT_STATUT");
        UUID patientAvis = patientDm7(CENTRE_A, "PAT_AVIS");
        UUID patientDouble = patientDm7(CENTRE_A, "PAT_DOUBLE");
        UUID patientSain = patientDm7(CENTRE_A, "PAT_SAIN");
        bilanSimple(CENTRE_A, patientStatut, "BIL_STATUT_ILLISIBLE", "STATUT_DISPARU");
        UUID bilanAvis = bilanSimple(CENTRE_A, patientAvis, "BIL_AVIS_ILLISIBLE", "ELIGIBLE");
        decisionEnBase(bilanAvis, "DEC_ILLISIBLE", fixe(10), "AVIS_DISPARU", "Compte rendu fictif", null);
        bilanSimple(CENTRE_A, patientDouble, "BIL_DOUBLE_1", "ELIGIBLE");
        bilanSimple(CENTRE_A, patientDouble, "BIL_DOUBLE_2", "NON_DEBUTE");
        bilanSimple(CENTRE_A, patientSain, "BIL_SAIN", "ELIGIBLE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 lecture, statut disparu de l'enumeration", refus(doGet(bilanApi(patientStatut), medecinA())));
        m.put("02 changement de statut sur ce bilan", refus(doPut(bilanApi(patientStatut) + "/statut", statut(CENTRE_A, "BILAN_EN_COURS"), medecinA())));
        m.put("03 lecture, avis de decision disparu", refus(doGet(bilanApi(patientAvis), medecinA())));
        m.put("04 lecture, deux bilans pour le meme patient (aucune unicite en base)", refus(doGet(bilanApi(patientDouble), medecinA())));
        m.put("05 notes, deux bilans pour le meme patient", refus(doPut(bilanApi(patientDouble) + "/notes", corps("centerId", CENTRE_A, "contreIndications", "x"),
                medecinA())));
        m.put("06 le bilan sain voisin reste lisible", refus(doGet(bilanApi(patientSain), medecinA())));
        m.put("07 etat des bilans (rien de modifie par les refus)", etatDesBilans());
        arbre("lignes_illisibles", m);
    }

    private void nettoyerLesBilans() {
        jdbc.update("DELETE FROM decisions_rcp WHERE bilan_id IN (SELECT id FROM bilans_pre_greffe WHERE center_id = ?)", CENTRE_A);
        jdbc.update("DELETE FROM bilans_pre_greffe WHERE center_id = ?", CENTRE_A);
    }
}
