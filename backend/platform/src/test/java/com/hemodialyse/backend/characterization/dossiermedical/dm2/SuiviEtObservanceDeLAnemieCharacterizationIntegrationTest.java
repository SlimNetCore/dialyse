package com.hemodialyse.backend.characterization.dossiermedical.dm2;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM2, lot 2 : les deux lectures de l'anémie qui dérivent de la prescription, {@code GET /patients/{id}/suivi-anemie} (vue agrégée) et
 * {@code GET /patients/{id}/observance-anemie} (période en cours, doses attendues, administrées, restantes). L'observance lit
 * {@code LocalDate.now()} (jour de la JVM, sans horloge injectable) : les dates sont relatives, nommées {@code <J-3>}. Données fictives.
 */
class SuiviEtObservanceDeLAnemieCharacterizationIntegrationTest extends PrescriptionsCharacterizationSupport {

    @Override
    protected String dossier() {
        return "suivi";
    }

    private UUID patientA;
    private UUID patientB;
    private UUID epo;
    private UUID fer;

    private void decorAnemie() {
        patientA = patientDm2(CENTRE_A, "PAT_A");
        patientB = patientDm2(CENTRE_B, "PAT_B");
        epo = article(CENTRE_A, "EPO", "EPO-FICT", "EPO", "4000", "UI");
        fer = article(CENTRE_A, "FER", "FER-FICT", "FER_INJECTABLE", "100", "mg");
        for (int n = -40; n <= 40; n++) relatif(n);
        relatif(324); // fin de la période annuelle ancrée à J-40
        relatif(324 - 365);
    }

    private String suivi(UUID patient) {
        return PATIENTS_API + "/" + patient + "/suivi-anemie";
    }

    private String observance(UUID patient) {
        return PATIENTS_API + "/" + patient + "/observance-anemie";
    }

    // ------------------------------------------------------------------ suivi de l'anémie

    @Test
    void sans_aucune_donnee_le_suivi_est_vide() throws Exception {
        decorAnemie();
        lecture("suivi", doGet(suivi(patientA), as(CENTRE_A, "MEDECIN")));
    }

    @Test
    void le_suivi_agrege_les_derniers_bilans_la_derniere_prescription_et_les_administrations() throws Exception {
        decorAnemie();
        analyse(CENTRE_A, patientA, "B1", fixe(1), "9.0", "150", "12", "3.0", "1.0");
        analyse(CENTRE_A, patientA, "B2", fixe(20), "10.8", "420", "28", "3.8", "1.5");
        analyse(CENTRE_A, patientA, "B3", fixe(10), "11.5", "800", "35", "4.2", "1.8");
        analyse(CENTRE_B, patientB, "BB", fixe(30), "13.0", "300", "30", "4.0", "1.4");
        prescriptionAnemie(CENTRE_A, patientA, "ANCIENNE", fixe(5), epo, 4000, 1, "SEMAINE", null, null, null, null);
        prescriptionAnemie(CENTRE_A, patientA, "RECENTE", fixe(15), epo, 8000, 2, "SEMAINE", fer, 100, 1, "MOIS");
        administration(CENTRE_A, patientA, "ADM1", "EPO", fixe(16), true, 8000, "UI");
        administration(CENTRE_A, patientA, "ADM2", "FER_INJECTABLE", fixe(17), false, null, null);
        lecture("suivi", doGet(suivi(patientA), as(CENTRE_A, "ADMIN")));
    }

    @Test
    void les_valeurs_hors_cibles_sont_evaluees_et_une_valeur_absente_n_est_pas_evaluee() throws Exception {
        decorAnemie();
        Map<String, Object> cas = new LinkedHashMap<>();
        String[][] bilans = {
                {"valeurs courantes (albumine 3.8 sous la cible 4.0)", "11.0", "400", "30", "3.8", "1.4"},
                {"tout sous les cibles", "8.0", "100", "10", "2.5", "0.8"},
                {"hemoglobine au-dessus ; ferritine, CST, albumine et Kt/V sans borne haute", "14.5", "1500", "60", "5.5", "3.0"},
                {"valeurs absentes", null, null, null, null, null},
        };
        for (String[] b : bilans) {
            nettoyerDm2();
            decorAnemie();
            analyse(CENTRE_A, patientA, "B", fixe(1), b[1], b[2], b[3], b[4], b[5]);
            Resp r = doGet(suivi(patientA), as(CENTRE_A, "MEDECIN"));
            List<String> evaluations = new ArrayList<>();
            corps(r).path("evaluations").forEach(e -> evaluations.add(e.toString()));
            cas.put(b[0], evaluations);
        }
        arbre("evaluations", cas);
    }

    @Test
    void les_administrations_recentes_ne_sont_pas_triees_et_la_courbe_est_limitee_a_dix_bilans() throws Exception {
        decorAnemie();
        for (int i = 1; i <= 12; i++) {
            analyse(CENTRE_A, patientA, "B" + i, fixe(i), "10." + (i % 10), "300", "25", "3.5", null);
            administration(CENTRE_A, patientA, "ADM" + i, "EPO", fixe(i), true, 4000, "UI");
        }
        JsonNode c = corps(doGet(suivi(patientA), as(CENTRE_A, "MEDECIN")));
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> courbe = new ArrayList<>();
        c.path("courbe").forEach(p -> courbe.add(p.path("date").asString()));
        m.put("dates de la courbe (dix plus recentes, de la plus recente a la plus ancienne)", courbe);
        List<String> administrations = new ArrayList<>();
        c.path("administrationsRecentes").forEach(a -> administrations.add(a.path("dateAdministration").asString()));
        administrations.sort(String::compareTo);
        m.put("dates des administrations rendues (triees ici pour la lecture ; l'API ne les trie pas)", administrations);
        m.put("nombre d'administrations rendues", administrations.size());
        arbre("limites", m);
    }

    // ------------------------------------------------------------------ observance

    private Map<String, Object> cas(String titre, Runnable fixtures) throws Exception {
        nettoyerDm2();
        decorAnemie();
        fixtures.run();
        Resp r = doGet(observance(patientA), as(CENTRE_A, "INFIRMIER"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        m.put("observance", corps(r));
        return m;
    }

    @Test
    void l_observance_de_l_epo_compte_les_doses_attendues_administrees_et_restantes_de_la_periode_en_cours() throws Exception {
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("aucune prescription", cas("", () -> {
        }));
        cas.put("EPO 8000 UI par semaine, ancree a J-2, rien d'administre",
                cas("", () -> prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-2), epo, 8000, 1, "SEMAINE", null, null, null, null)));
        cas.put("une administration de 4000 UI sur 8000 attendues",
                cas("", () -> {
                    prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-2), epo, 8000, 1, "SEMAINE", null, null, null, null);
                    administration(CENTRE_A, patientA, "A1", "EPO", relatif(0), true, 4000, "UI");
                }));
        cas.put("dose soldee (2 x 4000 UI) : plus rien a administrer",
                cas("", () -> {
                    prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-2), epo, 8000, 1, "SEMAINE", null, null, null, null);
                    administration(CENTRE_A, patientA, "A1", "EPO", relatif(-1), true, 4000, "UI");
                    administration(CENTRE_A, patientA, "A2", "EPO", relatif(0), true, 4000, "UI");
                }));
        cas.put("prescription sans dose : on compte les administrations (1 par semaine)",
                cas("", () -> {
                    prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-2), epo, null, 1, "SEMAINE", null, null, null, null);
                    administration(CENTRE_A, patientA, "A1", "EPO", relatif(0), true, 4000, "UI");
                }));
        cas.put("3 par semaine avec 1 administration de 8000 UI : le reste est une quantite, en nombre d'administrations arrondi au-dessus",
                cas("", () -> {
                    prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-2), epo, 8000, 3, "SEMAINE", null, null, null, null);
                    administration(CENTRE_A, patientA, "A1", "EPO", relatif(0), true, 8000, "UI");
                }));
        arbre("epo", cas);
    }

    @Test
    void les_administrations_qui_ne_comptent_pas_sont_ecartees() throws Exception {
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("hors periode, non administree, autre unite, autre traitement, autre patient, autre centre : rien ne compte",
                cas("", () -> {
                    prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-2), epo, 8000, 1, "SEMAINE", null, null, null, null);
                    administration(CENTRE_A, patientA, "AVANT", "EPO", relatif(-3), true, 8000, "UI");
                    administration(CENTRE_A, patientA, "APRES", "EPO", relatif(5), true, 8000, "UI");
                    administration(CENTRE_A, patientA, "REFUSEE", "EPO", relatif(0), false, null, null);
                    administration(CENTRE_A, patientA, "MG", "EPO", relatif(0), true, 8000, "mg");
                    administration(CENTRE_A, patientA, "FER", "FER_INJECTABLE", relatif(0), true, 100, "mg");
                    administration(CENTRE_A, UUID.nameUUIDFromBytes("autre-patient".getBytes()), "AUTRE_PATIENT", "EPO", relatif(0), true, 8000, "UI");
                    administration(CENTRE_B, patientA, "AUTRE_CENTRE", "EPO", relatif(0), true, 8000, "UI");
                }));
        cas.put("bornes de la periode incluses (premier et dernier jour)",
                cas("", () -> {
                    prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-2), epo, 8000, 1, "SEMAINE", null, null, null, null);
                    administration(CENTRE_A, patientA, "PREMIER", "EPO", relatif(-2), true, 4000, "UI");
                    administration(CENTRE_A, patientA, "DERNIER", "EPO", relatif(4), true, 4000, "UI");
                }));
        cas.put("unite d'administration en minuscules acceptee (comparaison sans casse)",
                cas("", () -> {
                    prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-2), epo, 8000, 1, "SEMAINE", null, null, null, null);
                    administration(CENTRE_A, patientA, "MINUSCULES", "EPO", relatif(0), true, 8000, "ui");
                }));
        arbre("ecartees", cas);
    }

    @Test
    void les_periodes_suivent_l_unite_de_frequence_et_seule_la_derniere_prescription_compte() throws Exception {
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String unite : List.of("JOUR", "SEMAINE", "MOIS", "ANNEE", "HEURE")) {
            cas.put("EPO tous les 1 " + unite + ", ancree a J-40",
                    cas("", () -> prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-40), epo, 4000, 1, unite, null, null, null, null)));
        }
        cas.put("prescription ancree dans le futur (J+3) : la periode commence a la date de prescription",
                cas("", () -> prescriptionAnemie(CENTRE_A, patientA, "P", relatif(3), epo, 4000, 1, "SEMAINE", null, null, null, null)));
        cas.put("seule la derniere prescription compte (l'ancienne dose 4000 est ignoree)",
                cas("", () -> {
                    prescriptionAnemie(CENTRE_A, patientA, "ANCIENNE", relatif(-20), epo, 4000, 1, "SEMAINE", null, null, null, null);
                    prescriptionAnemie(CENTRE_A, patientA, "RECENTE", relatif(-2), epo, 8000, 1, "SEMAINE", null, null, null, null);
                }));
        cas.put("la derniere prescription sans article EPO : plus d'observance EPO",
                cas("", () -> {
                    prescriptionAnemie(CENTRE_A, patientA, "ANCIENNE", relatif(-20), epo, 4000, 1, "SEMAINE", null, null, null, null);
                    prescription(CENTRE_A, patientA, "RECENTE", relatif(-2), 300, null);
                }));
        cas.put("frequence nulle ou inconnue : pas d'observance",
                cas("", () -> prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-2), epo, 4000, 0, "SEMAINE", null, null, null, null)));
        arbre("periodes", cas);
    }

    @Test
    void l_observance_du_fer_se_mesure_en_milligrammes_et_en_parallele_de_l_epo() throws Exception {
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("fer 200 mg par mois, 100 mg administres ; EPO 4000 UI par semaine, rien d'administre",
                cas("", () -> {
                    prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-5), epo, 4000, 1, "SEMAINE", fer, 200, 1, "MOIS");
                    administration(CENTRE_A, patientA, "F1", "FER_INJECTABLE", relatif(-1), true, 100, "mg");
                }));
        arbre("fer", cas);
    }

    // ------------------------------------------------------------------ droits et cloisonnement

    @Test
    void les_droits_par_profil_sur_les_deux_lectures() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm2().entrySet()) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("suivi-anemie", statutEtCode(doGet(suivi(idStable("patient", "DROITS")), p.getValue())));
            ligne.put("observance-anemie", statutEtCode(doGet(observance(idStable("patient", "DROITS")), p.getValue())));
            profils.put(p.getKey(), ligne);
        }
        Map<String, Object> anonyme = new LinkedHashMap<>();
        anonyme.put("suivi-anemie", statutEtCode(doGetAnonymous(suivi(idStable("patient", "DROITS")))));
        anonyme.put("observance-anemie", statutEtCode(doGetAnonymous(observance(idStable("patient", "DROITS")))));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session() throws Exception {
        decorAnemie();
        prescriptionAnemie(CENTRE_B, patientB, "PB", relatif(-2), epo, 8000, 1, "SEMAINE", null, null, null, null);
        analyse(CENTRE_B, patientB, "BB", fixe(2), "11.0", "400", "30", "3.8", "1.4");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("suivi du patient B par le centre A", resume(doGet(suivi(patientB), as(CENTRE_A, "MEDECIN"))));
        cas.put("observance du patient B par le centre A", resume(doGet(observance(patientB), as(CENTRE_A, "MEDECIN"))));
        cas.put("suivi avec centerId B (session A)", statutEtCode(doGet(suivi(patientB) + "?centerId=" + CENTRE_B, as(CENTRE_A, "MEDECIN"))));
        cas.put("observance avec centerId B (session A)", statutEtCode(doGet(observance(patientB) + "?centerId=" + CENTRE_B, as(CENTRE_A, "MEDECIN"))));
        cas.put("suivi du patient B par le centre B (temoin)", resume(doGet(suivi(patientB), as(CENTRE_B, "MEDECIN"))));
        cas.put("identifiant de patient illisible", statutEtCode(doGet(PATIENTS_API + "/pas-un-uuid/suivi-anemie", as(CENTRE_A, "MEDECIN"))));
        arbre("cloisonnement", cas);
    }

    private Object resume(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        JsonNode c = corps(r);
        m.put("a une prescription", c.has("prescriptionActive") ? !c.path("prescriptionActive").isNull() : null);
        m.put("a une courbe", c.has("courbe") ? c.path("courbe").size() : null);
        m.put("epo", c.has("epo") ? (c.path("epo").isNull() ? "absente" : "presente") : null);
        return m;
    }
}
