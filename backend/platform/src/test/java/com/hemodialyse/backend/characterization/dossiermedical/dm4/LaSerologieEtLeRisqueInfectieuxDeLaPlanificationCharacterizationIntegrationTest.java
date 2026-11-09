package com.hemodialyse.backend.characterization.dossiermedical.dm4;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.characterization.planification.JourSemaine.LUNDI;
import static com.hemodialyse.backend.characterization.planification.JourSemaine.MARDI;

/**
 * DM4, lot 4 : effet d'une sérologie sur la Planification (P5), observé par le placement du patient après l'écriture. Lecteur
 * observé : {@code RisquesInfectieuxJdbc} (SQL sur {@code serologies_patient}, marqueurs AG_HBS, AC_VHC, ARN_VHC, VIH_AC, dernière
 * sérologie de chaque marqueur) et l'ACL d'écriture {@code IsolementDesPatientsParLaPlanification} du module dossier-medical (une seule transaction ; avant DM4 : {@code ReaffectationEnIsolementDeLaSerologie}). Le détail
 * du déplacement (jours, créneaux, générateur, alertes) est déjà verrouillé par le filet P5 ({@code p5-placement/isolement}) ; ce lot
 * verrouille la RÈGLE DE POSITIVITÉ côté sérologie.
 */
class LaSerologieEtLeRisqueInfectieuxDeLaPlanificationCharacterizationIntegrationTest extends SerologieCharacterizationSupport {

    @Override
    protected String dossier() {
        return "planification";
    }

    private String lieu(UUID patient) {
        UUID salle = jdbc.queryForObject("SELECT salle_id FROM patients WHERE id = ?", UUID.class, patient);
        return salleIsolement.equals(salle) ? "salle d'isolement" : salle == null ? "aucune salle" : "salle ordinaire";
    }

    private UUID patientPlaceA(String alias) {
        return place(CENTRE_A, alias, salle1, matin, g1a, MARDI);
    }

    @Test
    void seuls_les_marqueurs_AG_HBS_AC_VHC_ARN_VHC_et_VIH_AC_positifs_deplacent_le_patient_en_isolement() throws Exception {
        decorDePlacement();
        Map<String, Object> m = new LinkedHashMap<>();
        for (String marqueur : new String[]{"VIH_AC", "AG_HBS", "AC_HBS", "AC_HBC", "AC_VHC", "ARN_VHC", "TPHA", "CMV_IGG", "CMV_IGM",
                "TOXO_IGG", "TOXO_IGM", "EBV_IGG", "EBV_IGM"}) {
            jdbc.update("DELETE FROM serologies_patient WHERE center_id = ?", CENTRE_A);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", CENTRE_A);
            UUID p = patientPlaceA("PAT_" + marqueur);
            Resp r = doPost(serologiesApi(p), corps(CENTRE_A, marqueur, "POSITIF", fixe(3), CONDUITE), medecinA());
            m.put(marqueur + " POSITIF", refus(r) + " -> " + lieu(p));
        }
        arbre("marqueurs", m);
    }

    @Test
    void les_resultats_negatif_douteux_et_en_cours_ne_deplacent_personne_meme_sur_un_marqueur_a_risque() throws Exception {
        decorDePlacement();
        Map<String, Object> m = new LinkedHashMap<>();
        for (String resultat : new String[]{"NEGATIF", "DOUTEUX", "EN_COURS"}) {
            jdbc.update("DELETE FROM serologies_patient WHERE center_id = ?", CENTRE_A);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", CENTRE_A);
            UUID p = patientPlaceA("PAT_" + resultat);
            Resp r = doPost(serologiesApi(p), corps(CENTRE_A, "VIH_AC", resultat, fixe(3), null), medecinA());
            m.put("VIH_AC " + resultat, refus(r) + " -> " + lieu(p));
        }
        arbre("resultats", m);
    }

    @Test
    void la_regle_porte_sur_la_derniere_serologie_de_chaque_marqueur_une_positive_antidatee_ne_deplace_pas() throws Exception {
        decorDePlacement();
        UUID p = patientPlaceA("PAT_1");
        Map<String, Object> m = new LinkedHashMap<>();
        serologieEnBase(CENTRE_A, p, "ANCIENNE", "AG_HBS", "NEGATIF", fixe(20), null);
        m.put("positive plus ancienne que la negative", refus(doPost(serologiesApi(p), corps(CENTRE_A, "AG_HBS", "POSITIF", fixe(10), CONDUITE),
                medecinA())) + " -> " + lieu(p));
        m.put("positive plus recente que la negative", refus(doPost(serologiesApi(p), corps(CENTRE_A, "AG_HBS", "POSITIF", fixe(30), CONDUITE),
                medecinA())) + " -> " + lieu(p));
        arbre("derniere_serologie", m);
    }

    @Test
    void un_patient_positif_depuis_longtemps_est_deplace_par_toute_ecriture_qui_le_laisse_a_risque_meme_negative_sur_un_autre_marqueur() throws Exception {
        decorDePlacement();
        UUID p = patientPlaceA("PAT_1");
        serologieEnBase(CENTRE_A, p, "ANCIENNE_POSITIVE", "VIH_AC", "POSITIF", fixe(5), CONDUITE);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("avant", lieu(p));
        m.put("saisie negative d'un marqueur sans risque", refus(doPost(serologiesApi(p), corps(CENTRE_A, "TPHA", "NEGATIF", fixe(6), null),
                medecinA())) + " -> " + lieu(p));
        arbre("positif_prealable", m);
    }

    @Test
    void la_correction_en_positif_deplace_et_la_correction_en_negatif_ou_la_suppression_ne_ramenent_pas() throws Exception {
        decorDePlacement();
        UUID p = patientPlaceA("PAT_1");
        UUID s = serologieEnBase(CENTRE_A, p, "S1", "AC_VHC", "NEGATIF", fixe(3), null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("correction en positif", refus(doPut(serologieApi(p, s), corpsMaj(CENTRE_A, "POSITIF", CONDUITE), medecinA())) + " -> " + lieu(p));
        m.put("correction en negatif", refus(doPut(serologieApi(p, s), corpsMaj(CENTRE_A, "NEGATIF", null), medecinA())) + " -> " + lieu(p));
        UUID q = patientPlaceA("PAT_2");
        UUID t = serologieEnBase(CENTRE_A, q, "S2", "ARN_VHC", "POSITIF", fixe(3), CONDUITE);
        m.put("suppression d'une positive (patient non deplace)", refus(doDelete(serologieApi(q, t), medecinA())) + " -> " + lieu(q));
        arbre("correction_et_suppression", m);
    }

    @Test
    void la_serologie_et_la_reaffectation_sont_dans_la_meme_transaction_un_refus_de_placement_annule_la_serologie() throws Exception {
        decorDePlacement();
        UUID deB = patientDm4(CENTRE_B, "PAT_B");
        UUID inconnu = UUID.nameUUIDFromBytes("dm4-inconnu".getBytes());
        UUID p = patientPlaceA("PAT_1");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("positive, patient inconnu", refus(doPost(serologiesApi(inconnu), corps(CENTRE_A, "VIH_AC", "POSITIF", fixe(3), CONDUITE), medecinA())));
        m.put("positive, patient du centre B", refus(doPost(serologiesApi(deB), corps(CENTRE_A, "VIH_AC", "POSITIF", fixe(3), CONDUITE), medecinA())));
        m.put("positive hors risque, patient du centre B", refus(doPost(serologiesApi(deB), corps(CENTRE_A, "TPHA", "POSITIF", fixe(3), CONDUITE),
                medecinA())));
        m.put("serologies ecrites apres ces trois refus ou acceptations", nombreDeSerologies());
        m.put("positive, patient du centre", refus(doPost(serologiesApi(p), corps(CENTRE_A, "VIH_AC", "POSITIF", fixe(3), CONDUITE), medecinA()))
                + " -> " + lieu(p));
        arbre("transaction", m);
        etatDesSerologies("serologies");
    }
}
