package com.hemodialyse.backend.characterization.planification.presence;

import com.hemodialyse.backend.characterization.planification.PresenceCharacterizationSupport;
import com.hemodialyse.backend.characterization.planification.JourSemaine;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P4 : {@code GET /api/v1/infirmiers/presence/alertes} (cases en sous-effectif des {@code jours}
 * prochains jours à partir d'aujourd'hui UTC, horizon 1 à 60, défaut 14). Famille d'alias {@code <J+n>} ; les patients sont placés
 * sur les sept jours de la semaine : aucun résultat ne dépend du jour d'exécution. Le contrôle quotidien de 07:15 lit les mêmes
 * alertes : voir {@code ControleDePresenceDe0715...}.
 */
class AlertesDeSousEffectifCharacterizationIntegrationTest extends PresenceCharacterizationSupport {

    @Override
    protected String theme() {
        return "p4-presence/alertes";
    }

    private void amorcer() {
        decor();
        nommerJours(-1, 62);
    }

    private static final JourSemaine[] TOUS = JourSemaine.values();

    // ------------------------------------------------------------------ horizon

    @Test
    void par_defaut_quatorze_jours_une_ligne_par_case_en_sous_effectif_de_la_plus_proche_a_la_plus_lointaine() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, TOUS);
        Resp r = alertes("", adminA());
        arbre("quatorze_jours_par_defaut", Map.of("statut", r.status(), "cacheControl", String.valueOf(r.cacheControl()),
                "contentType", String.valueOf(r.contentType()), "lignes", lignesDAlertes(r)));
    }

    @Test
    void forme_complete_d_une_alerte() throws Exception {
        amorcer();
        UUID benali = infirmierAffecte(CENTRE_A, "INF_B", "Benali", "Karim", false, salle1, matin, tousLesJours());
        absence(CENTRE_A, "ABS_B", benali, jour(0), jour(0), "MALADIE", null);
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, TOUS);
        Resp r = alertes("?jours=1", adminA());
        // le nom du jour dépend du jour d'exécution : on vérifie qu'il est celui de la date, sans le figer
        java.util.List<JsonNode> alertes = new java.util.ArrayList<>();
        for (JsonNode a : corps(r)) {
            tools.jackson.databind.node.ObjectNode copie = ((tools.jackson.databind.node.ObjectNode) a).deepCopy();
            String j = copie.remove("jour").asString();
            copie.put("jourEstCelDeLaDate", j.equals(JourSemaine.de(jour(0).getDayOfWeek()).name()));
            alertes.add(copie);
        }
        arbre("alerte_forme_complete_un_jour", Map.of("statut", r.status(), "contentType", r.contentType(),
                "cacheControl", r.cacheControl(), "alertes", alertes));
    }

    @Test
    void les_bornes_de_l_horizon_un_a_soixante_jours_et_les_valeurs_hors_bornes_ou_illisibles() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, TOUS);
        Map<String, Object> r = new LinkedHashMap<>();
        for (String[] c : new String[][]{{"zero", "0"}, {"negatif", "-1"}, {"un", "1"}, {"soixante", "60"}, {"soixante et un", "61"},
                {"texte", "abc"}, {"vide", ""}, {"decimal", "3.5"}, {"tres grand", "2147483648"}, {"trois", "3"}}) {
            Resp resp = alertes("?jours=" + c[1], adminA());
            String detail = resp.status() == 200 ? " lignes=" + corps(resp).size() : "";
            if (resp.status() != 200 && resp.raw() != null && resp.raw().contains("horizon")) {
                detail = " " + corps(resp).path("detail").asString();
            }
            r.put("jours " + c[0], statutEtCode(resp) + detail);
        }
        Resp soixante = alertes("?jours=60", adminA());
        JsonNode lignes = corps(soixante);
        r.put("jours 60 premiere et derniere date", lignes.get(0).path("date").asString() + " .. " + lignes.get(lignes.size() - 1).path("date").asString());
        arbre("bornes_de_l_horizon", r);
        lecture("horizon_hors_bornes_400", alertes("?jours=61", adminA()));
        lecture("horizon_illisible", alertes("?jours=abc", adminA()));
    }

    @Test
    void sans_sous_effectif_la_liste_est_vide() throws Exception {
        amorcer();
        lecture("aucune_alerte_sans_patient", alertes("", adminA()));
        patientsPlaces(CENTRE_A, "PAT", 4, salle1, matin, TOUS);
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, tousLesJours());
        lecture("aucune_alerte_quand_tout_est_couvert", alertes("", adminA()));
    }

    @Test
    void le_tri_est_par_date_puis_par_ordre_des_creneaux_les_salles_suivent_leur_code() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "P1", 5, salle1, matin, TOUS);
        patientsPlaces(CENTRE_A, "P2", 5, salle2, matin, TOUS);
        patientsPlaces(CENTRE_A, "P3", 5, salle1, apresMidi, TOUS);
        patientsPlaces(CENTRE_A, "P4", 5, salle2, apresMidi, TOUS);
        arbre("tri_date_creneau_salle", lignesDAlertes(alertes("?jours=2", adminA())));
    }

    // ------------------------------------------------------------------ règles

    @Test
    void une_absence_non_remplacee_cree_une_alerte_un_remplacement_la_leve_et_les_absents_sont_nommes() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 4, salle1, matin, TOUS);
        UUID amrani = infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, tousLesJours());
        UUID cherif = inf(CENTRE_A, "INF_C", "Cherif", "Lina", false);
        absence(CENTRE_A, "ABS_A", amrani, jour(1), jour(3), "CONGE", "Conge annuel");
        arbre("absence_non_remplacee", lignesDAlertes(alertes("?jours=6", adminA())));
        remplacement(CENTRE_A, "REMPL_J2", jour(2), salle1, matin, cherif, amrani);
        arbre("absence_remplacee_le_deuxieme_jour", lignesDAlertes(alertes("?jours=6", adminA())));
    }

    @Test
    void le_ratio_du_centre_change_le_nombre_requis_et_donc_les_alertes() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 6, salle1, matin, TOUS);
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, tousLesJours());
        Map<String, Object> r = new LinkedHashMap<>();
        for (Integer ratio : new Integer[]{null, 2, 3, 4, 6, 10}) {
            ratio(CENTRE_A, ratio);
            r.put("ratio=" + ratio, lignesDAlertes(alertes("?jours=1", adminA())));
        }
        arbre("ratio_defaut_et_ratio_configure", r);
    }

    @Test
    void jours_fermes_ferie_fermeture_exceptionnelle_et_jour_non_ouvert_ne_donnent_pas_d_alerte() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, TOUS);
        jourFerme(CENTRE_A, jour(1), "Ferie");
        fermetureExceptionnelle(CENTRE_A, jour(2), "Travaux");
        arbre("ferie_et_fermeture", lignesDAlertes(alertes("?jours=5", adminA())));
        // jours d'ouverture : on retire le jour de J+3 et de J+4 de la semaine
        String sansJ3J4 = "";
        for (JourSemaine j : TOUS) {
            if (j.name().equals(frDe(jour(3))) || j.name().equals(frDe(jour(4)))) continue;
            sansJ3J4 += (sansJ3J4.isEmpty() ? "" : ",") + j.name();
        }
        joursOuverts(CENTRE_A, sansJ3J4);
        arbre("jours_non_ouverts_j3_et_j4_en_plus", lignesDAlertes(alertes("?jours=6", adminA())));
    }

    private static String frDe(LocalDate d) {
        return JourSemaine.de(d.getDayOfWeek()).name();
    }

    @Test
    void en_salle_d_isolement_un_infirmier_non_habilite_ne_compte_pas() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 4, salleIsolement, matin, TOUS);
        infirmierAffecte(CENTRE_A, "INF_NON", "Nonhabilite", "Karim", false, salleIsolement, matin, tousLesJours());
        arbre("isolement_non_habilite", lignesDAlertes(alertes("?jours=1", adminA())));
        infirmierAffecte(CENTRE_A, "INF_HAB", "Habilite", "Sara", true, salleIsolement, matin, tousLesJours());
        arbre("isolement_habilite_present", lignesDAlertes(alertes("?jours=1", adminA())));
    }

    @Test
    void un_patient_qui_quitte_le_centre_dans_l_horizon_ne_donne_plus_d_alerte_apres_sa_sortie() throws Exception {
        amorcer();
        UUID p = patientPlace(CENTRE_A, "PAT_TRANSFERE", salle1, matin, TOUS);
        jdbc.update("UPDATE patients SET etat_patient = 'TRANSFERE', date_evenement_etat = ? WHERE id = ?",
                java.sql.Date.valueOf(jour(2)), p);
        arbre("patient_transfere_a_j2", lignesDAlertes(alertes("?jours=6", adminA())));
    }

    @Test
    void les_alertes_sont_celles_du_centre_de_la_session_et_les_infirmiers_desactives_ne_comptent_pas() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT_A", 5, salle1, matin, TOUS);
        patientsPlaces(CENTRE_B, "PAT_B", 5, salleB, creneauB, TOUS);
        UUID desactive = fiche(CENTRE_A, "INF_OFF", "INF_OFF", "Desactive", "Nour", null, "INFIRMIER", false, false, null);
        affectation(CENTRE_A, "AFF_OFF", desactive, salle1, matin, tousLesJours());
        infirmierAffecte(CENTRE_B, "INF_B", "Brahimi", "Nadia", false, salleB, creneauB, tousLesJours());
        affectation(CENTRE_B, "AFF_B2", UUID.nameUUIDFromBytes("planification-p2-INF_B".getBytes()), salleB, creneauB, tousLesJours());
        arbre("centre_A", lignesDAlertes(alertes("?jours=1", adminA())));
        arbre("centre_B", lignesDAlertes(alertes("?jours=1", as(CENTRE_B, "ADMIN"))));
    }

    /** Nouvelle route de la slice P4 (KB-PLN-50) : le sur-effectif et la double affectation sont lisibles par HTTP. */
    @Test
    void le_surplus_et_la_double_affectation_sont_exposes_par_la_route_des_alertes_de_sureffectif() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 4, salle1, matin, TOUS);
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, tousLesJours());
        UUID benali = infirmierAffecte(CENTRE_A, "INF_B", "Benali", "Karim", false, salle2, apresMidi, tousLesJours());   // salle sans patient
        affectation(CENTRE_A, "AFF_B2", benali, salle1, apresMidi, tousLesJours());                                       // deux salles, même créneau
        Resp r = doGet(PRESENCE + "/alertes-sureffectif?jours=1", adminA());
        JsonNode corps = corps(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        m.put("cacheControl", r.cacheControl());
        List<String> surplus = new java.util.ArrayList<>();
        for (JsonNode a : corps.path("sureffectifs")) {
            surplus.add(a.path("date").asString() + " " + a.path("salleId").asString() + " " + a.path("creneauId").asString()
                    + " patients=" + a.path("patients").asInt() + " requis=" + a.path("requis").asInt() + " surplus=" + a.path("surplus").asInt());
        }
        List<String> doubles = new java.util.ArrayList<>();
        for (JsonNode a : corps.path("doublesAffectations")) {
            doubles.add(a.path("date").asString() + " " + a.path("creneauId").asString() + " " + a.path("infirmier").asString() + " salles="
                    + a.path("salles").size());
        }
        m.put("sureffectifs", surplus);
        m.put("doublesAffectations", doubles);
        arbre("sureffectif_et_double_affectation", m);
        arbre("horizon_hors_bornes", Map.of("statut", alertesDeSureffectif("?jours=61").status()));
    }

    private Resp alertesDeSureffectif(String requete) throws Exception {
        return doGet(PRESENCE + "/alertes-sureffectif" + requete, adminA());
    }

    @Test
    void rien_n_est_ecrit_par_une_lecture_des_alertes() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, TOUS);
        String avant = empreinteP3();
        int notificationsAvant = nombreDe("notification_evenement");
        alertes("", adminA());
        alertes("?jours=60", adminA());
        arbre("lecture_sans_ecriture", Map.of("tablesP3Inchangees", avant.equals(empreinteP3()), "notificationsAvant", notificationsAvant,
                "notificationsApres", nombreDe("notification_evenement"), "diffusions", List.copyOf(diffusions).size()));
    }
}
