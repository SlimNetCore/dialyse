package com.hemodialyse.backend.characterization.planification.optimisation;

import com.hemodialyse.backend.characterization.planification.OptimisationCharacterizationSupport;
import com.hemodialyse.backend.characterization.planification.JourSemaine;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filet de caractérisation P8, lot 2 : application d'une proposition ({@code POST /optimisations/{id}/application}) : places des
 * patients, roulement, remplacements, déplacements temporaires, refus (empreinte périmée, déjà appliquée, non applicable, place
 * incohérente), effets observables (notifications, diffusions, audit, lectures P4 et P6) et concurrence simple (H2).
 */
class ApplicationDesPropositionsCharacterizationIntegrationTest extends OptimisationCharacterizationSupport {

    @Override
    protected String theme() {
        return "p8-optimisation/application";
    }

    private void unPatientEnAttente() {
        petitDecor();
        nommerLesSemaines(0, 3);
        patient(CENTRE_A, "ATTENTE", "Attente", "Alice", "PERMANENT", matin, "lundi", "mercredi", "vendredi");
    }

    private String etatDeLaBase() {
        return jdbc.queryForList("SELECT code_patient, salle_id, position_id, generateur_id, jour_lundi, jour_mardi FROM patients "
                + "WHERE center_id IN (?, ?) ORDER BY code_patient", CENTRE_A, CENTRE_B).toString()
                + jdbc.queryForList("SELECT infirmier_id, salle_id, creneau_id, jours FROM infirmier_affectation WHERE center_id IN (?, ?) "
                + "ORDER BY infirmier_id, salle_id, creneau_id, jours", CENTRE_A, CENTRE_B)
                + jdbc.queryForList("SELECT date_jour, salle_id, creneau_id, infirmier_id FROM infirmier_remplacement "
                + "WHERE center_id IN (?, ?) ORDER BY date_jour, infirmier_id", CENTRE_A, CENTRE_B)
                + jdbc.queryForList("SELECT patient_id, date_seance, generateur_id FROM deplacement_temporaire WHERE center_id IN (?, ?) "
                + "ORDER BY date_seance, patient_id", CENTRE_A, CENTRE_B);
    }

    // ------------------------------------------------------------------ placement des patients

    @Test
    void appliquer_un_placement_range_le_patient_signale_les_deplacements_et_rafraichit_les_lectures() throws Exception {
        unPatientEnAttente();
        UUID run = calculer("RUN", "PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1);
        arbre("semaine_avant_l_application", projectionDeLaSemaine(lireLaSemaine(1)));
        Resp r = appliquer(run, planifAdmin());
        execution("application_du_placement", r);
        etatDesPlacesDesPatients("patients_apres_l_application");
        notifications("notifications_apres_l_application");
        diffusionsCapturees("diffusions_apres_l_application");
        etatDesExecutions("executions_apres_l_application");
        arbre("semaine_apres_l_application", projectionDeLaSemaine(lireLaSemaine(1)));
        nommerLesSemaines(1, 2);
        arbre("presence_apres_l_application", casesDePresence(presenceDeLaSemaine(1, adminA())));
    }

    @Test
    void une_proposition_ne_s_applique_qu_une_fois_la_seconde_application_ne_change_rien() throws Exception {
        unPatientEnAttente();
        UUID run = calculer("RUN", "PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1);
        assertEquals(200, appliquer(run, planifAdmin()).status());
        String avant = etatDeLaBase();
        int notifications = nombreDe("notification_evenement");
        Resp encore = appliquer(run, planifAdmin());
        arbre("seconde_application_du_placement", Map.of("reponse", statutEtCode(encore), "base inchangee", avant.equals(etatDeLaBase()),
                "notifications supplementaires", nombreDe("notification_evenement") - notifications));
        Resp parLeSecretaire = appliquer(run, planifSecretaire());
        arbre("application_par_le_secretaire", statutEtCode(parLeSecretaire));
    }

    @Test
    void une_execution_qui_n_est_pas_terminee_avec_un_resultat_n_est_pas_applicable() throws Exception {
        unPatientEnAttente();
        Map<String, String> issues = new LinkedHashMap<>();
        Resp lancement = lancer("PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1, planifAdmin());
        UUID enCours = UUID.fromString(corps(lancement).path("id").asString());
        issues.put("calcul en cours", statutEtCode(appliquer(enCours, planifAdmin())));
        attendreLaFin(enCours);
        UUID enEchec = executionEcrite(CENTRE_A, "EN_ECHEC", "ECHEC", "PATIENTS", 5, "Moteur d'optimisation indisponible");
        issues.put("calcul en echec", statutEtCode(appliquer(enEchec, planifAdmin())));
        UUID sansResultat = executionEcrite(CENTRE_A, "SANS_RESULTAT", "TERMINEE", "PATIENTS", 4, null);
        issues.put("terminee sans resultat", statutEtCode(appliquer(sansResultat, planifAdmin())));
        issues.put("execution inconnue", statutEtCode(appliquer(UUID.randomUUID(), planifAdmin())));
        arbre("executions_non_applicables", issues);
        etatDesPlacesDesPatients("patients_apres_les_refus");
    }

    @Test
    void une_nouvelle_place_qui_enfreint_les_regles_de_la_planification_est_refusee_sans_rien_ecrire() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        UUID salleSansGenerateur = salle(CENTRE_A, "SALLE_2", "Salle 2", "NON");
        UUID generateurEnPanne = generateur(CENTRE_A, "G2X", salleSansGenerateur, "EN_PANNE");
        patient(CENTRE_A, "ATTENTE", "Attente", "Alice", "PERMANENT", matin, "lundi", "mercredi", "vendredi");
        UUID run = calculer("RUN", "PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1);
        arbre("proposition_calculee", projectionDeLExecution(consulter(run)).get("deplacements"));
        String avant = etatDeLaBase();
        Map<String, Object> issues = new LinkedHashMap<>();
        // la proposition est modifiée à la main : le générateur proposé est celui d'une autre salle (hors service)
        jdbc.update("UPDATE planification_optimisation SET resultat = REPLACE(resultat, ?, ?) WHERE id = ?",
                g1a.toString(), generateurEnPanne.toString(), run);
        Resp r = appliquer(run, planifAdmin());
        issues.put("generateur d'une autre salle", statutEtCode(r));
        issues.put("detail", corps(r).path("detail").asString());
        issues.put("base inchangee", avant.equals(etatDeLaBase()));
        issues.put("execution toujours non appliquee", jdbc.queryForObject(
                "SELECT COUNT(*) FROM planification_optimisation WHERE id = ? AND applique_le IS NULL", Integer.class, run));
        // une place proposée sur un générateur qui n'existe plus
        UUID inconnu = UUID.nameUUIDFromBytes("generateur-inconnu".getBytes());
        aliasNommes.put(inconnu.toString(), "<GENERATEUR_INCONNU>");
        jdbc.update("UPDATE planification_optimisation SET resultat = REPLACE(resultat, ?, ?) WHERE id = ?",
                generateurEnPanne.toString(), inconnu.toString(), run);
        Resp r2 = appliquer(run, planifAdmin());
        issues.put("generateur inconnu", statutEtCode(r2));
        issues.put("detail du generateur inconnu", corps(r2).path("detail").asString());
        issues.put("base inchangee apres le second refus", avant.equals(etatDeLaBase()));
        arbre("places_incoherentes", issues);
    }

    // ------------------------------------------------------------------ roulement, couverture, maintenance, complet

    @Test
    void appliquer_un_roulement_garde_modifie_supprime_et_cree_les_affectations_des_seuls_infirmiers_actifs() throws Exception {
        decorDePlacement();
        nommerLesSemaines(0, 3);
        UUID a = inf(CENTRE_A, "INF_A", "Alpha", "Anne", false);
        UUID inactif = fiche(CENTRE_A, "INF_INACTIF", "INF_INACTIF", "Inactif", "Ines", false);
        place(CENTRE_A, "P1", salle1, matin, g1a, JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI);
        affectation(CENTRE_A, "AFF_A_LUNDI", a, salle1, matin, "LUNDI");
        affectation(CENTRE_A, "AFF_A_MARDI_SALLE_2", a, salle2, apresMidi, "MARDI");
        affectation(CENTRE_A, "AFF_INACTIF", inactif, salle2, matin, "JEUDI");
        etatAffectations("roulement_avant");
        UUID run = calculer("RUN", "ROULEMENT", semaine(1, JourSemaine.DIMANCHE), 1);
        arbre("proposition_de_roulement", projectionDeLExecution(consulter(run)));
        Resp r = appliquer(run, planifAdmin());
        arbre("reponse_du_roulement", statutEtCode(r));
        etatAffectations("roulement_apres");
        notifications("notifications_apres_le_roulement");
        nommerLesSemaines(1, 2);
        arbre("presence_apres_le_roulement", casesDePresence(presenceDeLaSemaine(1, adminA())));
    }

    @Test
    void appliquer_une_couverture_cree_les_remplacements_des_vacations_sans_infirmier() throws Exception {
        decorDePlacement();
        nommerLesSemaines(0, 3);
        UUID a = inf(CENTRE_A, "INF_A", "Alpha", "Anne", false);
        inf(CENTRE_A, "INF_B", "Beta", "Bob", false);
        place(CENTRE_A, "P1", salle1, matin, g1a, JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI);
        affectation(CENTRE_A, "AFF_A", a, salle1, matin, "LUNDI,MERCREDI,VENDREDI");
        absence(CENTRE_A, "ABS_A", a, semaine(1, JourSemaine.LUNDI), semaine(1, JourSemaine.MERCREDI), "CONGE", "Conge");
        UUID run = calculer("RUN", "COUVERTURE", semaine(1, JourSemaine.DIMANCHE), 1);
        arbre("proposition_de_couverture", projectionDeLExecution(consulter(run)));
        arbre("presence_avant_la_couverture", casesDePresence(presenceDeLaSemaine(1, adminA())));
        Resp r = appliquer(run, planifAdmin());
        arbre("reponse_de_la_couverture", statutEtCode(r));
        etatRemplacements("remplacements_apres_la_couverture");
        notifications("notifications_apres_la_couverture");
        arbre("presence_apres_la_couverture", casesDePresence(presenceDeLaSemaine(1, adminA())));
        arbre("seconde_application_de_la_couverture", statutEtCode(appliquer(run, planifAdmin())));
        etatRemplacements("remplacements_apres_la_seconde_application");
    }

    @Test
    void appliquer_une_maintenance_enregistre_les_deplacements_temporaires_sans_changer_la_place_habituelle() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        generateur(CENTRE_A, "G1B", salle1, "EN_SERVICE");
        place(CENTRE_A, "P1", salle1, matin, g1a, JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI);
        intervention(CENTRE_A, g1a, "PLANIFIEE", semaine(1, JourSemaine.MERCREDI), null);
        etatDesPlacesDesPatients("patients_avant");
        arbre("semaine_avant_la_maintenance", projectionDeLaSemaine(lireLaSemaine(1)));
        UUID run = calculer("RUN", "MAINTENANCE", semaine(1, JourSemaine.DIMANCHE), 1);
        arbre("proposition_de_maintenance", projectionDeLExecution(consulter(run)));
        Resp r = appliquer(run, planifAdmin());
        arbre("reponse_de_la_maintenance", statutEtCode(r));
        etatDesDeplacementsTemporaires("deplacements_temporaires_apres_l_application");
        etatDesPlacesDesPatients("patients_apres_la_maintenance");
        notifications("notifications_apres_la_maintenance");
        arbre("semaine_apres_la_maintenance", projectionDeLaSemaine(lireLaSemaine(1)));
        // un nouveau calcul tient compte du déplacement déjà enregistré ; son application remplace la ligne (un par patient et par date)
        UUID run2 = calculer("RUN_2", "MAINTENANCE", semaine(1, JourSemaine.DIMANCHE), 1);
        arbre("seconde_proposition", projectionDeLExecution(consulter(run2)));
        arbre("seconde_application_de_la_maintenance", statutEtCode(appliquer(run2, planifAdmin())));
        arbre("deplacements_temporaires_apres_la_seconde_application", Map.of("lignes", nombreDe("deplacement_temporaire")));
    }

    @Test
    void appliquer_un_calcul_complet_place_les_patients_puis_ecrit_le_roulement() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        inf(CENTRE_A, "INF_A", "Alpha", "Anne", false);
        patient(CENTRE_A, "ATTENTE", "Attente", "Alice", "PERMANENT", matin, "lundi", "mercredi", "vendredi");
        UUID run = calculer("RUN", "COMPLET", semaine(1, JourSemaine.DIMANCHE), 1);
        arbre("proposition_complete", projectionDeLExecution(consulter(run)));
        Resp r = appliquer(run, planifAdmin());
        arbre("reponse_du_calcul_complet", statutEtCode(r));
        etatDesPlacesDesPatients("patients_apres_le_calcul_complet");
        etatAffectations("roulement_apres_le_calcul_complet");
        notifications("notifications_apres_le_calcul_complet");
    }

    // ------------------------------------------------------------------ empreinte

    private interface Changement {
        void faire() throws Exception;
    }

    private String appliqueApres(Changement changement) throws Exception {
        reinitialiser();
        petitDecor();
        nommerLesSemaines(0, 3);
        inf(CENTRE_A, "INF_A", "Alpha", "Anne", false);
        patient(CENTRE_A, "ATTENTE", "Attente", "Alice", "PERMANENT", matin, "lundi", "mercredi", "vendredi");
        UUID run = calculer("RUN", "COUVERTURE", semaine(1, JourSemaine.DIMANCHE), 1);
        changement.faire();
        Resp r = appliquer(run, planifAdmin());
        Integer appliquee = jdbc.queryForObject("SELECT COUNT(*) FROM planification_optimisation WHERE id = ? AND applique_le IS NOT NULL",
                Integer.class, run);
        return statutEtCode(r) + (appliquee == 1 ? " (appliquee)" : " (non appliquee)");
    }

    @Test
    void la_proposition_n_est_applicable_que_si_l_empreinte_du_centre_n_a_pas_change() throws Exception {
        Map<String, String> issues = new LinkedHashMap<>();
        issues.put("aucun changement", appliqueApres(() -> {
        }));
        issues.put("nom du patient modifie", appliqueApres(() -> jdbc.update("UPDATE patients SET nom = 'Autre' WHERE center_id = ?", CENTRE_A)));
        issues.put("nom de l'infirmier modifie", appliqueApres(() -> jdbc.update("UPDATE infirmier SET nom = 'Autre' WHERE center_id = ?", CENTRE_A)));
        issues.put("jour de dialyse du patient modifie", appliqueApres(() -> jdbc.update("UPDATE patients SET jour_mardi = TRUE WHERE center_id = ?",
                CENTRE_A)));
        issues.put("patient place", appliqueApres(() -> jdbc.update("UPDATE patients SET salle_id = ?, generateur_id = ? WHERE center_id = ?",
                salle1, g1a, CENTRE_A)));
        issues.put("nouveau patient en attente", appliqueApres(() -> patient(CENTRE_A, "AUTRE", "Autre", "Bob", "PERMANENT", matin, "mardi")));
        issues.put("patient sans jours de dialyse", appliqueApres(() -> patient(CENTRE_A, "SANS_JOURS", "Sans", "Jours", "PERMANENT", matin)));
        issues.put("patient d'un autre centre", appliqueApres(() -> {
            UUID salleB = salle(CENTRE_B, "SALLE_B3", "Salle B3", "NON");
            UUID creneauB = creneau(CENTRE_B, "CRENEAU_B3", "Creneau B3");
            patient(CENTRE_B, "PATIENT_B", "PatientB", "Zoe", "PERMANENT", creneauB, "lundi");
        }));
        issues.put("nouveau generateur", appliqueApres(() -> generateur(CENTRE_A, "G1C", salle1, "EN_SERVICE")));
        issues.put("generateur hors service", appliqueApres(() -> jdbc.update("UPDATE gmao_equipements SET statut = 'EN_PANNE' WHERE centre_id = ?",
                CENTRE_A)));
        issues.put("intervention de maintenance planifiee", appliqueApres(() -> intervention(CENTRE_A, g1a, "PLANIFIEE",
                semaine(1, JourSemaine.MERCREDI), null)));
        issues.put("intervention terminee", appliqueApres(() -> intervention(CENTRE_A, g1a, "TERMINEE",
                semaine(1, JourSemaine.MERCREDI), null)));
        issues.put("nouvelle salle", appliqueApres(() -> salle(CENTRE_A, "SALLE_X", "Salle X", "NON")));
        issues.put("salle declaree en isolement", appliqueApres(() -> jdbc.update("UPDATE salle SET isolement = 'OUI' WHERE center_id = ?", CENTRE_A)));
        issues.put("nouveau creneau", appliqueApres(() -> creneau(CENTRE_A, "CRENEAU_X", "Creneau X")));
        issues.put("jours d'ouverture du centre", appliqueApres(() -> joursOuvertsDuCentre(CENTRE_A, "LUNDI,MARDI")));
        issues.put("ratio patients par infirmier", appliqueApres(() -> {
            jdbc.update("DELETE FROM planning_parametres WHERE center_id = ?", CENTRE_A);
            jdbc.update("INSERT INTO planning_parametres (center_id, jours_ouverts, patients_par_infirmier, patients_par_poste_serie, "
                    + "updated_at) VALUES (?, '', 3, NULL, CURRENT_TIMESTAMP)", CENTRE_A);
        }));
        issues.put("fermeture exceptionnelle dans l'horizon", appliqueApres(() -> fermetureExceptionnelle(CENTRE_A,
                semaine(1, JourSemaine.MARDI), "Travaux")));
        issues.put("jour ferie dans l'horizon", appliqueApres(() -> jourFerme(CENTRE_A, semaine(1, JourSemaine.MARDI), "Ferie")));
        issues.put("jour ferie hors horizon", appliqueApres(() -> jourFerme(CENTRE_A, semaine(3, JourSemaine.MARDI), "Ferie")));
        issues.put("nouvel infirmier", appliqueApres(() -> inf(CENTRE_A, "INF_B", "Beta", "Bob", false)));
        issues.put("nouvel infirmier inactif", appliqueApres(() -> fiche(CENTRE_A, "INF_C", "INF_C", "Gamma", "Cam", false)));
        issues.put("infirmier desactive", appliqueApres(() -> jdbc.update("UPDATE infirmier SET actif = FALSE WHERE center_id = ?", CENTRE_A)));
        issues.put("habilitation isolement de l'infirmier", appliqueApres(() -> jdbc.update(
                "UPDATE infirmier SET habilite_isolement = TRUE WHERE center_id = ?", CENTRE_A)));
        issues.put("qualification de l'infirmier", appliqueApres(() -> jdbc.update(
                "UPDATE infirmier SET qualification = 'AIDE_SOIGNANT' WHERE center_id = ?", CENTRE_A)));
        issues.put("profil de planning de l'infirmier", appliqueApres(() -> {
            UUID inf = jdbc.queryForObject("SELECT id FROM infirmier WHERE center_id = ?", UUID.class, CENTRE_A);
            jdbc.update("INSERT INTO infirmier_profil_planning (center_id, infirmier_id, taux_activite, competences) VALUES (?, ?, 50, "
                    + "'PEDIATRIE')", CENTRE_A, inf);
        }));
        issues.put("roulement ajoute", appliqueApres(() -> affectation(CENTRE_A, "AFF", jdbc.queryForObject(
                "SELECT id FROM infirmier WHERE center_id = ?", UUID.class, CENTRE_A), salle1, matin, "LUNDI")));
        issues.put("absence dans l'horizon", appliqueApres(() -> absence(CENTRE_A, "ABS", jdbc.queryForObject(
                "SELECT id FROM infirmier WHERE center_id = ?", UUID.class, CENTRE_A), semaine(1, JourSemaine.MARDI),
                semaine(1, JourSemaine.MARDI), "CONGE", "Conge")));
        issues.put("absence hors horizon", appliqueApres(() -> absence(CENTRE_A, "ABS", jdbc.queryForObject(
                "SELECT id FROM infirmier WHERE center_id = ?", UUID.class, CENTRE_A), semaine(3, JourSemaine.MARDI),
                semaine(3, JourSemaine.MARDI), "CONGE", "Conge")));
        issues.put("remplacement ajoute", appliqueApres(() -> remplacement(CENTRE_A, "REMPL", semaine(1, JourSemaine.MARDI), salle1, matin,
                jdbc.queryForObject("SELECT id FROM infirmier WHERE center_id = ?", UUID.class, CENTRE_A), null)));
        issues.put("deplacement temporaire ajoute", appliqueApres(() -> deplacementTemporaire(CENTRE_A,
                jdbc.queryForObject("SELECT id FROM patients WHERE center_id = ?", UUID.class, CENTRE_A), semaine(1, JourSemaine.LUNDI),
                salle1, matin, g1a)));
        issues.put("seance enregistree", appliqueApres(() -> seance(CENTRE_A, jdbc.queryForObject("SELECT id FROM patients WHERE center_id = ?",
                UUID.class, CENTRE_A), semaine(1, JourSemaine.LUNDI), "PLANIFIEE", salle1, matin, g1a)));
        issues.put("patient sorti", appliqueApres(() -> etat(jdbc.queryForObject("SELECT id FROM patients WHERE center_id = ?", UUID.class,
                CENTRE_A), "TRANSFERE", semaine(0, JourSemaine.LUNDI))));
        arbre("effet_de_chaque_changement_sur_l_application", issues);
    }

    // ------------------------------------------------------------------ audit, diffusions

    @Test
    void le_lancement_et_l_application_sont_audites() throws Exception {
        unPatientEnAttente();
        monterLAudit();
        UUID run = calculer("RUN", "PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1);
        assertEquals(200, appliquer(run, planifAdmin()).status());
        appliquer(run, planifAdmin());
        doGet(OPTIMISATIONS, adminA());
        audit("audit_du_lancement_et_de_l_application");
    }

    // ------------------------------------------------------------------ concurrence (H2 uniquement)

    @Test
    void l_application_et_l_enregistrement_d_une_fiche_en_meme_temps_ne_laissent_jamais_un_etat_mixte() throws Exception {
        unPatientEnAttente();
        UUID patient = jdbc.queryForObject("SELECT id FROM patients WHERE center_id = ?", UUID.class, CENTRE_A);
        UUID run = calculer("RUN", "PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1);
        List<Resp> r = enParallele(() -> appliquer(run, planifAdmin()),
                () -> doPut(PATIENTS + "/" + patient, fiche(CENTRE_A, "Attente", "ASS-ATTENTE", null, matin, null, "LUNDI", "MARDI",
                        "MERCREDI", "VENDREDI"), planifAdmin()));
        int appliquer = r.get(0).status();
        int fiche = r.get(1).status();
        String salleDuPatient = jdbc.queryForObject("SELECT CAST(salle_id AS VARCHAR) FROM patients WHERE id = ?", String.class, patient);
        Boolean mardi = jdbc.queryForObject("SELECT jour_mardi FROM patients WHERE id = ?", Boolean.class, patient);
        assertTrue(appliquer == 200 || appliquer == 422, "application : " + appliquer + " " + r.get(0).raw());
        assertNotEquals(500, fiche, "fiche : " + r.get(1).raw());
        if (appliquer == 422) {
            assertTrue(r.get(0).raw().contains("OPTIMISATION_PERIMEE"), r.get(0).raw());
            assertEquals(null, salleDuPatient);
        }
        // la fiche a toujours été enregistrée (aucune erreur) : ses jours sont ceux de la fiche
        assertTrue(fiche == 200 || fiche / 100 == 4, "fiche : " + fiche + " " + r.get(1).raw());
        if (fiche == 200) assertEquals(Boolean.TRUE, mardi);
    }
}
