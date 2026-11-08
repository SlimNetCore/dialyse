package com.hemodialyse.backend.characterization.seance.se4;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE4, lot 1 : le rappel quotidien à l'administrateur des séances « créées » restées sans validation (RG-SEA-046,
 * {@code SeanceRegularisationScheduler.rappelerCentre} + {@code SeanceRegularisationService.aRegulariser}) : fenêtre de sept jours
 * passés (aujourd'hui exclu), statuts comptés, séances déjà déverrouillées, date de la plus ancienne, cloisonnement par centre, absence
 * de dédoublonnage, lecture seule. Le jour de la demande est un jour FIXE nommé ; aucune dépendance à l'heure.
 */
class LeRappelDesSeancesARegulariserCharacterizationIntegrationTest extends Se4CharacterizationSupport {

    @Override
    protected String dossier() {
        return "rappel";
    }

    @Test
    void l_administrateur_est_rappele_des_seances_creees_oubliees_des_sept_derniers_jours_du_centre() throws Exception {
        LocalDate aujourdhui = jourDeLaDemande(30);
        UUID p1 = patientCouvert(CENTRE_A, "PAT_A1");
        UUID p2 = patientCouvert(CENTRE_A, "PAT_A2");
        UUID pb = patientCouvert(CENTRE_B, "PAT_B");
        seanceEnBase(CENTRE_A, p1, "HIER", fixe(29), "CREE");
        seanceEnBase(CENTRE_A, p1, "IL_Y_A_4_JOURS", fixe(26), "CREE");
        seanceEnBase(CENTRE_A, p1, "IL_Y_A_7_JOURS", fixe(23), "CREE");
        seanceEnBase(CENTRE_A, p1, "IL_Y_A_8_JOURS", fixe(22), "CREE");
        seanceEnBase(CENTRE_A, p1, "AUJOURDHUI", fixe(30), "CREE");
        seanceEnBase(CENTRE_A, p1, "DEMAIN", fixe(31), "CREE");
        seanceValidee(CENTRE_A, p1, "VALIDEE", fixe(28), "VALIDEE");
        seanceValidee(CENTRE_A, p1, "SIGNEE", fixe(27), "SIGNEE");
        seanceValidee(CENTRE_A, p1, "FACTUREE", fixe(25), "FACTUREE");
        seanceValidee(CENTRE_A, p1, "ABSENT", fixe(24), "ABSENT");
        UUID deverrouillee = seanceEnBase(CENTRE_A, p2, "DEJA_DEVERROUILLEE", fixe(26), "CREE");
        deverrouillerEnBase(deverrouillee);
        seanceEnBase(CENTRE_B, pb, "AUTRE_CENTRE", fixe(27), "CREE");
        String avant = empreinteDesSeances();
        Map<String, Object> m = new LinkedHashMap<>();
        rappeler(CENTRE_A, aujourdhui);
        m.put("01 rappel du centre A (3 oubliees : hier, 4 jours, 7 jours)", rappelLu());
        m.put("02 le rappel n'ecrit aucune seance", empreinteDesSeances().equals(avant));
        m.put("03 lignes de derogations_regularisation (-1 : table absente du schema H2, creee par Flyway V50 seulement)", lignesDeDerogation());
        arbre("nominal", m);
    }

    @Test
    void la_fenetre_est_de_sept_jours_passes_la_veille_comprise_le_jour_de_la_demande_et_l_avenir_exclus() throws Exception {
        LocalDate aujourdhui = jourDeLaDemande(40);
        Map<String, Object> m = new LinkedHashMap<>();
        for (int decalage : new int[]{-9, -8, -7, -6, -1, 0, 1, 30}) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(40 + decalage), "CREE");
            viderLesRappels();
            rappeler(CENTRE_A, aujourdhui);
            m.put(String.format("%+03d jours", decalage), rappelLu());
        }
        arbre("fenetre_de_sept_jours", m);
    }

    @Test
    void seules_les_seances_creees_comptent_quel_que_soit_le_statut_des_autres_et_un_statut_illisible_ne_bloque_rien() throws Exception {
        LocalDate aujourdhui = jourDeLaDemande(40);
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT", "EN_COURS", "cree", "INCONNU")) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            seanceValidee(CENTRE_A, patient, "SEANCE", fixe(38), statut);
            viderLesRappels();
            rappeler(CENTRE_A, aujourdhui);
            m.put(statut, rappelLu());
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        seanceEnBase(CENTRE_A, patient, "CREEE_OUBLIEE", fixe(38), "CREE");
        seanceEnBase(CENTRE_A, patient, "STATUT_ILLISIBLE", fixe(37), "INCONNU");
        seanceEnBase(CENTRE_A, patient, "STATUT_MINUSCULE", fixe(36), "cree");
        viderLesRappels();
        rappeler(CENTRE_A, aujourdhui);
        m.put("une creee, un statut inconnu et un statut en minuscules dans le meme centre", rappelLu());
        arbre("statuts_comptes", m);
    }

    @Test
    void la_plus_ancienne_est_la_premiere_par_date_quel_que_soit_l_ordre_de_creation_et_le_nombre_de_patients() throws Exception {
        LocalDate aujourdhui = jourDeLaDemande(40);
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID autre = patientCouvert(CENTRE_A, "PAT_B");
        seanceEnBase(CENTRE_A, patient, "RECENTE", fixe(39), "CREE");
        seanceEnBase(CENTRE_A, patient, "ANCIENNE", fixe(34), "CREE");
        seanceEnBase(CENTRE_A, patient, "MILIEU", fixe(36), "CREE");
        seanceEnBase(CENTRE_A, autre, "AUTRE_PATIENT_MEME_JOUR_QUE_ANCIENNE", fixe(34), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        rappeler(CENTRE_A, aujourdhui);
        m.put("01 quatre oubliees, la plus ancienne est la 34e", rappelLu());
        arbre("la_plus_ancienne", m);
    }

    @Test
    void le_rappel_est_cloisonne_par_centre_et_chaque_appel_renvoie_un_rappel_sans_dedoublonnage() throws Exception {
        LocalDate aujourdhui = jourDeLaDemande(40);
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        seanceEnBase(CENTRE_A, patientA, "A_1", fixe(38), "CREE");
        seanceEnBase(CENTRE_A, patientA, "A_2", fixe(37), "CREE");
        seanceEnBase(CENTRE_B, patientB, "B_1", fixe(35), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        rappeler(CENTRE_A, aujourdhui);
        m.put("01 centre A", rappelLu());
        viderLesRappels();
        rappeler(CENTRE_B, aujourdhui);
        m.put("02 centre B", rappelLu());
        viderLesRappels();
        rappeler(idStable("centre", "inconnu"), aujourdhui);
        m.put("03 centre sans aucune seance", rappelLu());
        viderLesRappels();
        rappeler(CENTRE_A, aujourdhui);
        rappeler(CENTRE_A, aujourdhui);
        m.put("04 deux appels du meme jour pour le meme centre : deux rappels et deux lignes de journal", rappelLu());
        arbre("cloisonnement_et_repetition", m);
    }

    @Test
    void le_rappel_est_une_lecture_les_seances_et_les_derogations_ne_bougent_pas_et_un_jour_de_demande_ancien_ne_voit_rien() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        seanceEnBase(CENTRE_A, patient, "OUBLIEE", fixe(10), "CREE");
        String avant = empreinteDesSeances();
        Map<String, Object> m = new LinkedHashMap<>();
        rappeler(CENTRE_A, jourDeLaDemande(5));
        m.put("01 jour de la demande avant la seance (seance a venir)", rappelLu());
        viderLesRappels();
        rappeler(CENTRE_A, jourDeLaDemande(11));
        m.put("02 jour de la demande le lendemain de la seance", rappelLu());
        m.put("03 aucune seance modifiee", empreinteDesSeances().equals(avant));
        m.put("04 lignes de derogations_regularisation (-1 : table absente du schema H2)", lignesDeDerogation());
        arbre("lecture_seule", m);
    }
}
