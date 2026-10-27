package com.hemodialyse.backend.characterization.planification.presence;

import com.hemodialyse.backend.characterization.planification.PresenceCharacterizationSupport;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P4 : {@code GET /api/v1/infirmiers/presence/semaine} (grille salle x créneau x jour d'une semaine du
 * dimanche au samedi) : statuts de case (couvert, sous-effectif, sans patient, fermé), ratio de sécurité par défaut et configuré,
 * salle d'isolement et habilitation, conflits, absences, remplaçants, patients sortis, jours fermés, périodes et paramètres.
 * Toutes les dates sont des alias de la semaine S1 (future, entière), sans dépendance au jour d'exécution.
 */
class SemaineDePresenceCharacterizationIntegrationTest extends PresenceCharacterizationSupport {

    private static final JourSemaine DIM = JourSemaine.DIMANCHE;
    private static final JourSemaine LUN = JourSemaine.LUNDI;
    private static final JourSemaine MAR = JourSemaine.MARDI;
    private static final JourSemaine MER = JourSemaine.MERCREDI;

    @Override
    protected String theme() {
        return "p4-presence/semaine";
    }

    private void amorcer() {
        decor();
        semaine(1);
        semaine(2);
    }

    private Resp lireS1() throws Exception {
        return semaineAu(semaine(1, MER), adminA());
    }

    private void sortie(UUID patient, String etat, LocalDate date) {
        jdbc.update("UPDATE patients SET etat_patient = ?, date_evenement_etat = ? WHERE id = ?", etat,
                date == null ? null : java.sql.Date.valueOf(date), patient);
    }

    // ------------------------------------------------------------------ forme de la réponse

    @Test
    void la_forme_de_la_reponse_une_case_complete_les_sept_jours_les_salles_et_les_creneaux() throws Exception {
        amorcer();
        UUID amrani = infirmierAffecte(CENTRE_A, "INF_AMRANI", "Amrani", "Sara", true, salle1, matin, "LUNDI");
        absence(CENTRE_A, "ABS_X", inf(CENTRE_A, "INF_BENALI", "Benali", "Karim", false), semaine(1, LUN), semaine(1, LUN), "CONGE", null);
        affectation(CENTRE_A, "AFF_BENALI", UUID.nameUUIDFromBytes("planification-p2-INF_BENALI".getBytes()), salle1, matin, "LUNDI");
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, LUN);
        Resp r = lireS1();
        JsonNode corps = corps(r);
        ObjectNode forme = mapper.createObjectNode();
        forme.put("statut", r.status());
        forme.put("contentType", r.contentType());
        forme.put("cacheControl", r.cacheControl());
        forme.put("nombreDeCases", corps.path("cases").size());
        corps.properties().forEach(e -> {
            if (!e.getKey().equals("cases")) forme.set(e.getKey(), e.getValue());
        });
        for (JsonNode c : corps.path("cases")) {
            if (c.path("jour").asString().equals("LUNDI") && c.path("salleId").asString().equals(salle1.toString())
                    && c.path("creneauId").asString().equals(matin.toString())) {
                forme.set("caseLundiSalle1Matin", c);
            }
        }
        arbre("forme_de_la_reponse", forme);
        arbre("ordre_des_cases_salle_creneau_jour", corps.path("cases").valueStream().limit(9)
                .map(c -> c.path("creneauId").asString() + "|" + c.path("salleId").asString() + "|" + c.path("jour").asString())
                .toList());
    }

    @Test
    void sans_aucun_patient_ni_roulement_toutes_les_cases_sont_sans_patient() throws Exception {
        amorcer();
        Resp r = lireS1();
        arbre("semaine_vide_projection", projection(r));
        JsonNode c = corps(r);
        arbre("semaine_vide_effectifs", Map.of("cases", c.path("cases").size(), "casesSousEffectif", c.path("casesSousEffectif").asInt(),
                "statuts", c.path("cases").valueStream().map(x -> x.path("statut").asString()).distinct().toList()));
    }

    // ------------------------------------------------------------------ ratio de sécurité

    @Test
    void ratio_par_defaut_un_infirmier_pour_quatre_patients_arrondi_a_l_entier_superieur() throws Exception {
        amorcer();
        for (int n : new int[]{1, 4, 5, 8, 9}) {
            nettoyerPresence();
            jdbc.update("DELETE FROM patients WHERE center_id = ?", CENTRE_A);
            patientsPlaces(CENTRE_A, "PAT" + n, n, salle1, matin, LUN);
            Map<String, Object> p = projection(lireS1());
            arbre("ratio_defaut_" + n + "_patients", p);
        }
    }

    @Test
    void ratio_configure_dans_les_parametres_du_centre_et_ratio_nul_ou_invalide_en_base() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 6, salle1, matin, LUN);
        infirmierAffecte(CENTRE_A, "INF_AMRANI", "Amrani", "Sara", false, salle1, matin, "LUNDI");
        Map<String, Object> r = new LinkedHashMap<>();
        for (Integer ratio : new Integer[]{1, 2, 3, 6, 7, 100, null, 0, -3, 1000}) {
            ratio(CENTRE_A, ratio);
            Resp resp = lireS1();
            if (resp.status() != 200) {
                r.put("ratio en base=" + ratio, statutEtCode(resp));
                continue;
            }
            JsonNode lundi = null;
            for (JsonNode c : corps(resp).path("cases")) {
                if (c.path("patients").asInt() > 0) lundi = c;
            }
            r.put("ratio en base=" + ratio, "requis=" + lundi.path("requis").asInt() + " statut=" + lundi.path("statut").asString()
                    + " manque=" + lundi.path("manque").asInt() + " casesSousEffectif=" + corps(resp).path("casesSousEffectif").asInt());
        }
        arbre("ratio_configure_et_ratio_invalide_en_base", r);
        ratio(CENTRE_B, 2);
        patientsPlaces(CENTRE_B, "PAT_B", 3, salleB, creneauB, LUN);
        Resp b = doGet(PRESENCE + "/semaine?centerId=" + CENTRE_B + "&date=" + semaine(1, MER), as(CENTRE_B, "ADMIN"));
        arbre("ratio_du_centre_B_independant_du_centre_A", projection(b));
    }

    // ------------------------------------------------------------------ statuts de case

    @Test
    void couvert_sous_effectif_sans_patient_et_surplus_d_une_case_sans_patient() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT_S1", 5, salle1, matin, LUN);                 // requis 2
        patientsPlaces(CENTRE_A, "PAT_S2", 4, salle2, matin, LUN);                 // requis 1
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, "LUNDI");
        infirmierAffecte(CENTRE_A, "INF_B", "Benali", "Karim", false, salle2, matin, "LUNDI");
        infirmierAffecte(CENTRE_A, "INF_C", "Cherif", "Lina", false, salle2, matin, "LUNDI");   // 2 pour 1 requis : surplus
        infirmierAffecte(CENTRE_A, "INF_D", "Djaballah", "Omar", false, salle1, apresMidi, "LUNDI,MARDI");   // salle sans patient : surplus
        arbre("statuts_et_surplus", projection(lireS1()));
    }

    @Test
    void jours_fermes_jour_non_ouvert_jour_ferie_et_fermeture_exceptionnelle_ne_comptent_ni_manque_ni_surplus() throws Exception {
        amorcer();
        joursOuverts(CENTRE_A, "DIMANCHE,LUNDI,MARDI,MERCREDI,JEUDI,SAMEDI");   // vendredi non ouvert
        jourFerme(CENTRE_A, semaine(1, MAR), "Fete nationale");
        fermetureExceptionnelle(CENTRE_A, semaine(1, MER), "Travaux");
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, DIM, LUN, MAR, MER, JourSemaine.VENDREDI);
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, "LUNDI,MARDI,MERCREDI,VENDREDI");
        Resp r = lireS1();
        arbre("jours_fermes_projection", projection(r));
        arbre("jours_fermes_en_tete", Map.of("jours", corps(r).path("jours")));
    }

    @Test
    void les_jours_ouverts_vides_en_base_valent_tous_les_jours_et_un_centre_sans_ligne_aussi() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, DIM, JourSemaine.SAMEDI);
        arbre("sans_ligne_de_parametres", projection(lireS1()));
        joursOuverts(CENTRE_A, "");
        arbre("jours_ouverts_vides", projection(lireS1()));
        joursOuverts(CENTRE_A, "LUNDI");
        arbre("un_seul_jour_ouvert_lundi", projection(lireS1()));
    }

    // ------------------------------------------------------------------ isolement, conflits

    @Test
    void en_salle_d_isolement_seuls_les_infirmiers_habilites_comptent_et_un_non_habilite_est_signale() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT_ISO", 5, salleIsolement, matin, LUN);
        infirmierAffecte(CENTRE_A, "INF_HAB", "Habilite", "Sara", true, salleIsolement, matin, "LUNDI");
        infirmierAffecte(CENTRE_A, "INF_NON", "Nonhabilite", "Karim", false, salleIsolement, matin, "LUNDI");
        infirmierAffecte(CENTRE_A, "INF_NON2", "Nonhabilite2", "Lina", false, salleIsolement, matin, "LUNDI");
        arbre("salle_isolement_habilites_et_non_habilites", projection(lireS1()));
    }

    @Test
    void un_infirmier_dans_deux_salles_au_meme_creneau_et_au_meme_jour_declenche_un_conflit_de_double_affectation() throws Exception {
        amorcer();
        UUID a = infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, "LUNDI,MARDI");
        affectation(CENTRE_A, "AFF_A_SALLE2", a, salle2, matin, "LUNDI");
        affectation(CENTRE_A, "AFF_A_APRES_MIDI", a, salle2, apresMidi, "LUNDI");   // autre créneau : pas de conflit
        patientsPlaces(CENTRE_A, "PAT", 3, salle1, matin, LUN);
        arbre("double_affectation", projection(lireS1()));
    }

    @Test
    void deux_lignes_de_roulement_du_meme_infirmier_sur_la_meme_case_ne_le_comptent_qu_une_fois() throws Exception {
        amorcer();
        UUID a = infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, "LUNDI");
        affectation(CENTRE_A, "AFF_A_DOUBLON", a, salle1, matin, "LUNDI,MARDI");
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, LUN, MAR);
        arbre("lignes_de_roulement_en_double", projection(lireS1()));
    }

    // ------------------------------------------------------------------ absences et remplaçants

    @Test
    void un_absent_n_est_pas_compte_un_remplacant_l_est_et_un_remplacant_absent_ou_desactive_est_ignore() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, LUN, MAR, MER);
        UUID amrani = infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, "LUNDI,MARDI,MERCREDI");
        UUID benali = infirmierAffecte(CENTRE_A, "INF_B", "Benali", "Karim", false, salle1, matin, "LUNDI,MARDI,MERCREDI");
        UUID cherif = inf(CENTRE_A, "INF_C", "Cherif", "Lina", false);
        UUID dahmani = inf(CENTRE_A, "INF_D", "Dahmani", "Omar", false);
        UUID eddine = fiche(CENTRE_A, "INF_E", "INF_E", "Eddine", "Nour", null, "INFIRMIER", false, false, null);   // désactivé
        absence(CENTRE_A, "ABS_A", amrani, semaine(1, LUN), semaine(1, MAR), "CONGE", "Conge annuel");
        absence(CENTRE_A, "ABS_B", benali, semaine(1, MAR), semaine(1, MAR), "MALADIE", null);
        absence(CENTRE_A, "ABS_D", dahmani, semaine(1, MER), semaine(1, MER), "FORMATION", null);
        remplacement(CENTRE_A, "REMPL_C_LUN", semaine(1, LUN), salle1, matin, cherif, amrani);
        remplacement(CENTRE_A, "REMPL_C_MAR", semaine(1, MAR), salle1, matin, cherif, benali);
        remplacement(CENTRE_A, "REMPL_D_MER", semaine(1, MER), salle1, matin, dahmani, null);   // remplaçant absent ce jour-là : ignoré
        remplacement(CENTRE_A, "REMPL_E_MER", semaine(1, MER), salle1, matin, eddine, null);    // fiche désactivée : ignorée
        arbre("absents_remplacants_et_ignores", projection(lireS1()));
    }

    @Test
    void un_remplacant_deja_prevu_sur_la_case_n_est_pas_compte_deux_fois() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 9, salle1, matin, LUN);
        UUID a = infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, "LUNDI");
        remplacement(CENTRE_A, "REMPL_DOUBLON", semaine(1, LUN), salle1, matin, a, null);
        arbre("remplacant_deja_prevu", projection(lireS1()));
    }

    @Test
    void les_absences_qui_chevauchent_deux_semaines_et_celles_d_un_autre_centre_sont_prises_comme_telles() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, DIM, LUN, JourSemaine.SAMEDI);
        UUID a = infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, "DIMANCHE,LUNDI,SAMEDI");
        absence(CENTRE_A, "ABS_LONGUE", a, semaine(1, JourSemaine.SAMEDI), semaine(2, JourSemaine.LUNDI), "CONGE", null);
        arbre("absence_a_cheval_semaine_1", projection(lireS1()));
        arbre("absence_a_cheval_semaine_2", projection(semaineAu(semaine(2, MER), adminA())));
    }

    // ------------------------------------------------------------------ patients

    @Test
    void un_patient_sorti_libere_sa_place_a_la_date_de_l_evenement_transfert_inclus_deces_la_veille() throws Exception {
        amorcer();
        UUID transfere = patientPlace(CENTRE_A, "PAT_TRANSFERE", salle1, matin, DIM, LUN, MAR, MER, JourSemaine.JEUDI);
        UUID decede = patientPlace(CENTRE_A, "PAT_DECEDE", salle1, matin, DIM, LUN, MAR, MER, JourSemaine.JEUDI);
        UUID greffe = patientPlace(CENTRE_A, "PAT_GREFFE", salle1, matin, DIM, LUN, MAR, MER, JourSemaine.JEUDI);
        UUID gueri = patientPlace(CENTRE_A, "PAT_GUERI", salle1, matin, DIM, LUN, MAR, MER, JourSemaine.JEUDI);
        UUID sansDate = patientPlace(CENTRE_A, "PAT_TRANSFERE_SANS_DATE", salle1, matin, DIM, LUN, MAR, MER, JourSemaine.JEUDI);
        UUID occasionnel = patientPlace(CENTRE_A, "PAT_OCCASIONNEL", salle1, matin, DIM, LUN, MAR, MER, JourSemaine.JEUDI);
        sortie(transfere, "TRANSFERE", semaine(1, MER));
        sortie(decede, "DECEDE", semaine(1, MER));
        sortie(greffe, "GREFFE", semaine(1, MER));
        sortie(gueri, "GUERRI", semaine(1, MER));
        sortie(sansDate, "TRANSFERE", null);
        sortie(occasionnel, "OCCASIONNEL", semaine(1, MAR));
        arbre("patients_sortis", projection(lireS1()));
    }

    @Test
    void un_patient_sans_salle_ni_creneau_ou_sans_jour_ou_en_sommeil_ou_d_un_autre_centre() throws Exception {
        amorcer();
        patientPlace(CENTRE_A, "PAT_NORMAL", salle1, matin, LUN);
        UUID sansJour = patientPlace(CENTRE_A, "PAT_SANS_JOUR", salle1, matin);
        UUID sansSalle = patientPlace(CENTRE_A, "PAT_SANS_SALLE", salle1, matin, LUN);
        jdbc.update("UPDATE patients SET salle_id = NULL WHERE id = ?", sansSalle);
        UUID sommeil = patientPlace(CENTRE_A, "PAT_EN_SOMMEIL", salle1, matin, LUN);
        jdbc.update("UPDATE patients SET en_sommeil = TRUE WHERE id = ?", sommeil);
        patientPlace(CENTRE_B, "PAT_AUTRE_CENTRE", salleB, creneauB, LUN);
        arbre("patients_pris_en_compte", projection(lireS1()));
    }

    // ------------------------------------------------------------------ période, paramètres, isolation

    @Test
    void n_importe_quel_jour_de_la_semaine_donne_la_meme_semaine_dimanche_a_samedi() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, LUN);
        Map<String, Object> r = new LinkedHashMap<>();
        String reference = null;
        for (JourSemaine j : JourSemaine.values()) {
            Resp resp = semaineAu(semaine(1, j), adminA());
            if (reference == null) reference = resp.raw();
            r.put("S1." + j + " donne la meme reponse que S1.DIMANCHE", reference.equals(resp.raw()));
        }
        Resp suivante = semaineAu(semaine(2, DIM), adminA());
        r.put("debut de la semaine du dimanche suivant", corps(suivante).path("debut").asString());
        r.put("fin", corps(suivante).path("fin").asString());
        arbre("jours_de_la_meme_semaine", r);
    }

    @Test
    void sans_date_la_semaine_courante_est_servie_et_les_dates_illisibles_sont_refusees() throws Exception {
        amorcer();
        semaine(0);
        Resp courante = doGet(PRESENCE + "/semaine", adminA());
        arbre("semaine_par_defaut", Map.of("statut", courante.status(), "debut", corps(courante).path("debut").asString(),
                "fin", corps(courante).path("fin").asString(), "jours", corps(courante).path("jours").size()));
        Map<String, Object> r = new LinkedHashMap<>();
        String[][] cas = {{"texte", "abc"}, {"mois 13", "2026-13-01"}, {"30 fevrier", "2026-02-30"}, {"format jj/mm/aaaa", "27/09/2026"},
                {"vide", ""}, {"an 1", "0001-01-01"}, {"an 9999", "9999-12-31"}, {"an 999999999", "+999999999-01-01"}};
        for (String[] c : cas) {
            Resp resp = doGet(PRESENCE + "/semaine?date=" + c[1], adminA());
            r.put("date " + c[0], statutEtCode(resp));
        }
        arbre("dates_illisibles_et_bornes", r);
        lecture("date_illisible_400", doGet(PRESENCE + "/semaine?date=abc", adminA()));
    }

    @Test
    void la_semaine_ne_montre_que_le_centre_demande_et_ne_depend_pas_de_l_ordre_de_creation() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, "LUNDI");
        infirmierAffecte(CENTRE_B, "INF_B", "Brahimi", "Nadia", false, salleB, creneauB, "LUNDI");
        patientsPlaces(CENTRE_A, "PAT_A", 3, salle1, matin, LUN);
        patientsPlaces(CENTRE_B, "PAT_B", 9, salleB, creneauB, LUN);
        arbre("centre_A", projection(lireS1()));
        arbre("centre_B", projection(doGet(PRESENCE + "/semaine?date=" + semaine(1, MER), as(CENTRE_B, "ADMIN"))));
    }

    @Test
    void les_noms_sans_prenom_et_l_ordre_alphabetique_des_presents() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 12, salle1, matin, LUN);
        infirmierAffecte(CENTRE_A, "INF_Z", "Zerrouki", "Anis", false, salle1, matin, "LUNDI");
        infirmierAffecte(CENTRE_A, "INF_B", "Benali", null, false, salle1, matin, "LUNDI");
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, "LUNDI");
        arbre("noms_et_ordre_des_presents", projection(lireS1()));
    }
}
