package com.hemodialyse.backend.characterization.dossiermedical.dm7;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DM7, lot 4 (donneurs vivants) : routes {@code greffe/donneurs} (liste, création, mise à jour, suppression). Données d'identité
 * (nom, téléphone, date de naissance) : toutes fictives.
 */
class LesDonneursVivantsCandidatsCharacterizationIntegrationTest extends Dm7CharacterizationSupport {

    @Override
    protected String dossier() {
        return "donneurs";
    }

    private static final String[] CHAMPS = {"nom", "prenom", "dateNaissance", "lienParente", "telephone", "groupeSanguin", "typageHla",
            "statutBilan", "crossmatchResultat", "dateCrossmatch", "bilanRealise", "contreIndications", "decisionFinale", "dateDecision"};

    /** Corps de mise à jour ; le crossmatch vaut NON_FAIT sauf indication contraire (un crossmatch absent fait échouer la route en 500). */
    private String miseAJour(Object... paires) {
        Object[] complet = new Object[paires.length + 4];
        complet[0] = "centerId";
        complet[1] = CENTRE_A;
        complet[2] = "crossmatchResultat";
        complet[3] = "NON_FAIT";
        System.arraycopy(paires, 0, complet, 4, paires.length);
        return corps(complet);
    }

    @Test
    void l_enregistrement_d_un_candidat_le_met_au_statut_candidat_sans_crossmatch() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        Map<String, Object> m = new LinkedHashMap<>();
        Resp r = doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", "Donneur Un", "prenom", "Fictif", "dateNaissance", fixe(-9000),
                "lienParente", "FRERE_SOEUR", "telephone", "0000000001", "groupeSanguin", "A+", "typageHla", "A1 B8 DR3"), medecinA());
        m.put("01 creation complete", resume(r, CHAMPS));
        m.put("02 reponse complete", reponse(r));
        m.put("03 etat en base", etatDesDonneurs());
        m.put("04 creation minimale (nom et lien)", resume(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", "Donneur Deux", "lienParente",
                "NON_APPARENTE"), medecinA()), CHAMPS));
        m.put("05 patient inconnu (aucune garde de patient)", resume(doPost(donneursApi(idStable("patient", "inconnu")), corps("centerId", CENTRE_A,
                "nom", "Donneur Orphelin", "lienParente", "PARENT"), medecinA()), "nom", "statutBilan"));
        m.put("06 meme donneur deux fois (aucune unicite)", resume(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", "Donneur Deux",
                "lienParente", "NON_APPARENTE"), medecinA()), "nom", "statutBilan"));
        m.put("07 compte pour le patient", jdbc.queryForObject("SELECT COUNT(*) FROM donneurs_vivants WHERE patient_id = ?", Integer.class, patient));
        // KB-MED-86 : patient inconnu 404 (avant : donneur orphelin) ; legacy archivé dans donneurs/_ecarts-legacy
        arbre("creation_nouveau_code", m);
    }

    @Test
    void les_refus_de_creation_et_les_bornes_des_colonnes() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("01 nom absent", refus(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "lienParente", "PARENT"), medecinA())));
        cas.put("02 nom blanc", refus(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", " ", "lienParente", "PARENT"), medecinA())));
        cas.put("03 lien absent", refus(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", "Donneur"), medecinA())));
        cas.put("04 lien inconnu", refus(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", "Donneur", "lienParente", "VOISIN"), medecinA())));
        cas.put("05 nom de 100 caracteres", refus(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", "n".repeat(100), "lienParente", "PARENT"),
                medecinA())));
        cas.put("06 nom de 101 caracteres", refus(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", "n".repeat(101), "lienParente", "PARENT"),
                medecinA())));
        cas.put("07 telephone de 30 caracteres", refus(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", "T30", "lienParente", "PARENT",
                "telephone", "0".repeat(30)), medecinA())));
        cas.put("08 telephone de 31 caracteres", refus(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", "T31", "lienParente", "PARENT",
                "telephone", "0".repeat(31)), medecinA())));
        cas.put("09 groupe sanguin de 6 caracteres", refus(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", "G6", "lienParente", "PARENT",
                "groupeSanguin", "ABCDEF"), medecinA())));
        cas.put("10 typage de 501 caracteres", refus(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", "H501", "lienParente", "PARENT",
                "typageHla", "H".repeat(501)), medecinA())));
        cas.put("11 date de naissance dans le futur (aucun controle)", refus(doPost(donneursApi(patient), corps("centerId", CENTRE_A, "nom", "Futur",
                "lienParente", "PARENT", "dateNaissance", alias(java.time.LocalDate.of(2099, 1, 1), "<2099>")), medecinA())));
        cas.put("12 corps vide", refus(doPost(donneursApi(patient), "", medecinA())));
        cas.put("13 corps illisible", refus(doPost(donneursApi(patient), "{", medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("noms enregistres", jdbc.queryForList("SELECT nom FROM donneurs_vivants WHERE patient_id = ? ORDER BY nom", String.class, patient)
                .stream().map(n -> n.length() > 20 ? n.length() + " caracteres" : n).toList());
        // KB-MED-87 : bornes de colonne et entrées illisibles en 400 (avant : 500) ; legacy archivé dans donneurs/_ecarts-legacy
        arbre("creation_refus_nouveau_code", m);
    }

    @Test
    void la_mise_a_jour_remplace_tout_le_donneur_et_ramene_un_crossmatch_absent_a_non_fait() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        UUID donneur = donneurEnBase(CENTRE_A, patient, "D1", "Donneur Un", "PARENT", "CANDIDAT", "NON_FAIT", 0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 bilan complet : compatible, crossmatch negatif, decision", resume(doPut(donneurApi(patient, donneur),
                miseAJour("nom", "Donneur Un", "prenom", "Fictif", "dateNaissance", fixe(-9000), "lienParente", "PARENT", "telephone", "0000000002",
                        "groupeSanguin", "O+", "typageHla", "A2 B7", "statutBilan", "RETENU", "crossmatchResultat", "NEGATIF", "dateCrossmatch", fixe(20),
                        "bilanRealise", "Bilan cardiologique fait", "contreIndications", "Aucune", "decisionFinale", "Retenu pour la greffe",
                        "dateDecision", fixe(30)), medecinA()), CHAMPS));
        m.put("02 etat en base", etatDesDonneurs());
        m.put("03 mise a jour minimale : tout le reste est efface", resume(doPut(donneurApi(patient, donneur),
                miseAJour("nom", "Donneur Un", "lienParente", "ENFANT", "statutBilan", "EXCLU", "crossmatchResultat", "NON_FAIT"), medecinA()), CHAMPS));
        m.put("03b crossmatch absent : 500 (le defaut NON_FAIT du domaine est inatteignable par HTTP)", refus(doPut(donneurApi(patient, donneur),
                corps("centerId", CENTRE_A, "nom", "Donneur Un", "lienParente", "ENFANT", "statutBilan", "EXCLU"), medecinA())));
        m.put("04 crossmatch positif sans date (aucune regle croisee statut / crossmatch)", resume(doPut(donneurApi(patient, donneur),
                miseAJour("nom", "Donneur Un", "lienParente", "ENFANT", "statutBilan", "RETENU", "crossmatchResultat", "POSITIF"), medecinA()),
                "statutBilan", "crossmatchResultat", "dateCrossmatch"));
        // KB-MED-87 : crossmatch absent 400 (avant : 500) ; legacy archivé dans donneurs/_ecarts-legacy
        arbre("mise_a_jour_nouveau_code", m);
    }

    @Test
    void les_refus_de_mise_a_jour_n_ecrivent_rien() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        UUID autrePatient = patientDm7(CENTRE_A, "PAT_B");
        UUID donneur = donneurEnBase(CENTRE_A, patient, "D1", "Donneur Un", "PARENT", "CANDIDAT", "NON_FAIT", 0);
        String avant = empreinteGreffe();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("01 nom absent", refus(doPut(donneurApi(patient, donneur), miseAJour("lienParente", "PARENT", "statutBilan", "CANDIDAT"), medecinA())));
        cas.put("02 lien absent", refus(doPut(donneurApi(patient, donneur), miseAJour("nom", "Donneur Un", "statutBilan", "CANDIDAT"), medecinA())));
        cas.put("03 statut de bilan absent", refus(doPut(donneurApi(patient, donneur), miseAJour("nom", "Donneur Un", "lienParente", "PARENT"), medecinA())));
        cas.put("04 statut de bilan inconnu", refus(doPut(donneurApi(patient, donneur), miseAJour("nom", "Donneur Un", "lienParente", "PARENT",
                "statutBilan", "ACCEPTE"), medecinA())));
        cas.put("10 crossmatch absent", refus(doPut(donneurApi(patient, donneur), corps("centerId", CENTRE_A, "nom", "Donneur Un", "lienParente", "PARENT",
                "statutBilan", "CANDIDAT"), medecinA())));
        cas.put("05 crossmatch inconnu", refus(doPut(donneurApi(patient, donneur), miseAJour("nom", "Donneur Un", "lienParente", "PARENT",
                "statutBilan", "CANDIDAT", "crossmatchResultat", "INDETERMINE"), medecinA())));
        cas.put("06 donneur inconnu", refus(doPut(donneurApi(patient, idStable("donneur", "inconnu")), miseAJour("nom", "X", "lienParente", "PARENT",
                "statutBilan", "CANDIDAT"), medecinA())));
        cas.put("07 donneur d'un autre patient", refus(doPut(donneurApi(autrePatient, donneur), miseAJour("nom", "X", "lienParente", "PARENT",
                "statutBilan", "CANDIDAT"), medecinA())));
        cas.put("08 corps vide", refus(doPut(donneurApi(patient, donneur), "", medecinA())));
        cas.put("09 identifiant illisible", refus(doPut(donneursApi(patient) + "/pas-un-uuid", miseAJour("nom", "X"), medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("aucune ecriture", empreinteGreffe().equals(avant));
        // KB-MED-88 : donneur introuvable 404 (avant : 400) ; KB-MED-87 : entrées illisibles en 400 ; legacy archivé dans donneurs/_ecarts-legacy
        arbre("mise_a_jour_refus_nouveau_code", m);
    }

    @Test
    void la_liste_est_servie_du_plus_recent_au_plus_ancien_et_la_suppression_est_silencieuse() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        UUID autre = patientDm7(CENTRE_A, "PAT_B");
        UUID ancien = donneurEnBase(CENTRE_A, patient, "D1", "Donneur Ancien", "PARENT", "CANDIDAT", "NON_FAIT", 0);
        donneurEnBase(CENTRE_A, patient, "D2", "Donneur Recent", "ENFANT", "BILAN_EN_COURS", "NEGATIF", 20);
        donneurEnBase(CENTRE_A, patient, "D3", "Donneur Milieu", "CONJOINT", "COMPATIBLE", "NEGATIF", 10);
        UUID chezLAutre = donneurEnBase(CENTRE_A, autre, "D4", "Donneur Autre", "PARENT", "CANDIDAT", "NON_FAIT", 5);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 liste MEDECIN", liste(doGet(donneursApi(patient), medecinA()), "nom", "lienParente", "statutBilan", "crossmatchResultat"));
        m.put("02 liste ADMIN", liste(doGet(donneursApi(patient), as(CENTRE_A, "ADMIN")), "nom"));
        m.put("03 liste d'un patient sans donneur", liste(doGet(donneursApi(patientDm7(CENTRE_A, "PAT_C")), medecinA()), "nom"));
        m.put("04 suppression", doDelete(donneurApi(patient, ancien), medecinA()).status());
        m.put("05 suppression rejouee (204 silencieux)", doDelete(donneurApi(patient, ancien), medecinA()).status());
        m.put("06 suppression d'un donneur inconnu (204 silencieux)", doDelete(donneurApi(patient, idStable("donneur", "inconnu")), medecinA()).status());
        m.put("07 suppression d'un donneur d'un autre patient (204 silencieux, rien supprime)", doDelete(donneurApi(patient, chezLAutre), medecinA()).status());
        m.put("08 apres les suppressions", liste(doGet(donneursApi(patient), medecinA()), "nom"));
        m.put("09 donneurs de l'autre patient", liste(doGet(donneursApi(autre), medecinA()), "nom"));
        m.put("10 suppression ADMIN (refusee)", refus(doDelete(donneurApi(autre, chezLAutre), as(CENTRE_A, "ADMIN"))));
        m.put("11 liste INFIRMIER (refusee)", refus(doGet(donneursApi(patient), infirmierA())));
        m.put("12 restants", nombreDe("donneurs_vivants", CENTRE_A));
        arbre("liste_et_suppression", m);
    }

    @Test
    void une_ligne_illisible_fait_echouer_la_liste_du_patient() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        donneurEnBase(CENTRE_A, patient, "SAIN", "Donneur Sain", "PARENT", "CANDIDAT", "NON_FAIT", 0);
        UUID illisible = donneurEnBase(CENTRE_A, patient, "ILLISIBLE", "Donneur Ancien", "LIEN_DISPARU", "CANDIDAT", "NON_FAIT", 5);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 liste", refus(doGet(donneursApi(patient), medecinA())));
        m.put("02 mise a jour de la ligne illisible", refus(doPut(donneurApi(patient, illisible), miseAJour("nom", "X", "lienParente", "PARENT",
                "statutBilan", "CANDIDAT"), medecinA())));
        m.put("03 suppression de la ligne illisible", doDelete(donneurApi(patient, illisible), medecinA()).status());
        m.put("04 liste apres la suppression", liste(doGet(donneursApi(patient), medecinA()), "nom"));
        // KB-MED-90 : une ligne illisible est ignorée et journalisée (plus de 400 pour toute la liste) ; legacy archivé dans donneurs/_ecarts-legacy
        arbre("ligne_illisible_nouveau_code", m);
    }
}
