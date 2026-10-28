package com.hemodialyse.backend.characterization.planification.optimisation;

import com.hemodialyse.backend.characterization.planification.OptimisationCharacterizationSupport;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * Filet de caractérisation P8, lot 1 : lancement, statut, résultat, historique, calendrier, arrêt, suppression, validations et garde
 * de centre de l'optimisation du planning ({@code /api/v1/planning/optimisations}). Le solveur est réel (Timefold) sur des jeux de
 * données à optimum unique ; les durées sont au plancher de l'API (2 s).
 */
class LancementEtSuiviDesOptimisationsCharacterizationIntegrationTest extends OptimisationCharacterizationSupport {

    @Override
    protected String theme() {
        return "p8-optimisation/lancement";
    }

    private void unPatientEnAttente() {
        petitDecor();
        nommerLesSemaines(0, 3);
        patient(CENTRE_A, "ATTENTE", "Attente", "Alice", "PERMANENT", matin, "lundi", "mercredi", "vendredi");
    }

    private Resp supprimer(UUID run, org.springframework.test.web.servlet.request.RequestPostProcessor who) throws Exception {
        return send(delete(java.net.URI.create(OPTIMISATIONS + "/" + run)).with(who));
    }

    // ------------------------------------------------------------------ lancement, statut, résultat

    @Test
    void le_lancement_repond_202_avec_l_execution_en_cours_puis_la_proposition_apparait_dans_le_statut() throws Exception {
        unPatientEnAttente();
        Resp lancement = lancer("PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1, planifAdmin());
        UUID run = UUID.fromString(corps(lancement).path("id").asString());
        aliasNommes.put(run.toString(), "<RUN>");
        execution("lancement_202", lancement);
        assertEquals("EN_COURS", corps(consulter(run)).path("statut").asString());
        attendreLaFin(run);
        arbre("statut_apres_le_calcul", projectionDeLExecution(consulter(run)));
        etatDesExecutions("executions_apres_le_calcul");
        etatDesPlacesDesPatients("patients_apres_le_calcul_rien_n_est_applique");
    }

    @Test
    void chaque_perimetre_calcule_sa_proposition_sur_un_centre_avec_un_patient_en_attente() throws Exception {
        unPatientEnAttente();
        Map<String, Object> propositions = new LinkedHashMap<>();
        for (String perimetre : List.of("PATIENTS", "COUVERTURE", "ROULEMENT", "COMPLET", "MAINTENANCE")) {
            UUID run = calculer("RUN_" + perimetre, perimetre, semaine(1, JourSemaine.DIMANCHE), 1);
            propositions.put(perimetre, projectionDeLExecution(consulter(run)));
        }
        arbre("propositions_par_perimetre", propositions);
    }

    @Test
    void un_calcul_sans_aucune_donnee_se_termine_aussitot_avec_une_proposition_vide() throws Exception {
        nommerLesSemaines(0, 3);
        UUID run = calculer("RUN_VIDE", "PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1);
        arbre("proposition_vide", projectionDeLExecution(consulter(run)));
    }

    @Test
    void les_parametres_omis_prennent_les_valeurs_par_defaut_et_la_semaine_en_cours() throws Exception {
        nommerLesSemaines(0, 3);
        Resp r = doPost(OPTIMISATIONS, json("{'perimetre':'ROULEMENT'}"), planifAdmin());
        UUID run = UUID.fromString(corps(r).path("id").asString());
        aliasNommes.put(run.toString(), "<RUN>");
        attendreLaFin(run);
        execution("lancement_par_defaut", r);
    }

    @Test
    void les_reglages_de_personnel_du_centre_sont_lus_au_lancement() throws Exception {
        nommerLesSemaines(0, 3);
        jdbc.update("DELETE FROM planification_reglages WHERE center_id = ?", CENTRE_A);
        jdbc.update("INSERT INTO planification_reglages (center_id, replanification_auto, heures_par_vacation, heures_hebdo_temps_plein, "
                + "repos_hebdo_min) VALUES (?, FALSE, 3, 35, 2)", CENTRE_A);
        Resp r = lancer("ROULEMENT", semaine(1, JourSemaine.DIMANCHE), 1, planifAdmin());
        aliasNommes.put(corps(r).path("id").asString(), "<RUN>");
        attendreLaFin(UUID.fromString(corps(r).path("id").asString()));
        execution("lancement_avec_reglages_du_centre", r);
    }

    // ------------------------------------------------------------------ un seul calcul par centre

    @Test
    void un_deuxieme_lancement_pendant_le_calcul_est_refuse_mais_pas_dans_un_autre_centre_et_redevient_possible_apres() throws Exception {
        unPatientEnAttente();
        salle(CENTRE_B, "SALLE_B2", "Salle B2", "NON");
        Map<String, Object> issues = new LinkedHashMap<>();
        Resp premier = lancer("PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1, planifAdmin());
        UUID run = UUID.fromString(corps(premier).path("id").asString());
        aliasNommes.put(run.toString(), "<RUN_1>");
        issues.put("1er lancement du centre A", statutEtCode(premier));
        Resp second = lancer("COUVERTURE", semaine(1, JourSemaine.DIMANCHE), 1, planifSecretaire());
        issues.put("2e lancement du centre A pendant le calcul (autre perimetre, autre profil)", statutEtCode(second));
        execution("deuxieme_lancement_refuse", second);
        Resp autreCentre = lancer("PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1, adminB());
        issues.put("lancement du centre B pendant le calcul du centre A", statutEtCode(autreCentre));
        attendreLaFin(run);
        attendreLaFinDeTousLesCalculs();
        Resp apres = lancer("COUVERTURE", semaine(1, JourSemaine.DIMANCHE), 1, planifSecretaire());
        issues.put("lancement du centre A apres la fin du calcul", statutEtCode(apres));
        attendreLaFinDeTousLesCalculs();
        arbre("lancements", issues);
        etatDesExecutions("executions_des_deux_centres");
    }

    @Test
    void deux_lancements_simultanes_n_en_laissent_passer_qu_un() throws Exception {
        unPatientEnAttente();
        List<Resp> r = enParallele(
                () -> lancer("PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1, planifAdmin()),
                () -> lancer("PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1, planifSecretaire()));
        List<String> issues = new ArrayList<>(List.of(statutEtCode(r.get(0)), statutEtCode(r.get(1))));
        issues.sort(String::compareTo);
        attendreLaFinDeTousLesCalculs();
        arbre("deux_lancements_simultanes", issues);
        etatDesExecutions("executions_apres_deux_lancements_simultanes");
    }

    // ------------------------------------------------------------------ validations

    @Test
    void les_parametres_invalides_sont_refuses_sans_creer_d_execution() throws Exception {
        nommerLesSemaines(0, 3);
        Map<String, String> issues = new LinkedHashMap<>();
        String debut = semaine(1, JourSemaine.DIMANCHE).toString();
        Map<String, String> corps = new LinkedHashMap<>();
        corps.put("corps vide", "");
        corps.put("objet vide", "{}");
        corps.put("perimetre inconnu", "{'perimetre':'TOUT'}");
        corps.put("perimetre en minuscules", "{'perimetre':'patients'}");
        corps.put("json illisible", "{'perimetre':");
        corps.put("debutSemaine mal forme", "{'perimetre':'ROULEMENT','debutSemaine':'demain'}");
        corps.put("nbSemaines 0", "{'perimetre':'ROULEMENT','nbSemaines':0}");
        corps.put("nbSemaines 9", "{'perimetre':'ROULEMENT','nbSemaines':9}");
        corps.put("dureeMaxSecondes 1", "{'perimetre':'ROULEMENT','dureeMaxSecondes':1}");
        corps.put("dureeMaxSecondes 301", "{'perimetre':'ROULEMENT','dureeMaxSecondes':301}");
        corps.put("stabilite -1", "{'perimetre':'ROULEMENT','stabilite':-1}");
        corps.put("stabilite 11", "{'perimetre':'ROULEMENT','stabilite':11}");
        corps.put("objectif inconnu", "{'perimetre':'ROULEMENT','objectif':'RAPIDE'}");
        corps.put("maxVacationsParJour 0", "{'perimetre':'ROULEMENT','maxVacationsParJour':0}");
        corps.put("maxVacationsParJour 4", "{'perimetre':'ROULEMENT','maxVacationsParJour':4}");
        corps.put("maxVacationsParSemaine 0", "{'perimetre':'ROULEMENT','maxVacationsParSemaine':0}");
        corps.put("maxVacationsParSemaine 15", "{'perimetre':'ROULEMENT','maxVacationsParSemaine':15}");
        corps.put("nbSemaines texte", "{'perimetre':'ROULEMENT','nbSemaines':'deux'}");
        for (Map.Entry<String, String> c : corps.entrySet()) {
            issues.put(c.getKey(), statutEtCode(doPost(OPTIMISATIONS, json(c.getValue()), planifAdmin())));
        }
        issues.put("limites acceptees (nbSemaines 8, duree 2, stabilite 0 et 10, vacations 1/1 et 3/14)", "voir ci-dessous");
        for (String limite : List.of("{'perimetre':'ROULEMENT','nbSemaines':8,'dureeMaxSecondes':2,'stabilite':0,'maxVacationsParJour':1,"
                + "'maxVacationsParSemaine':1}", "{'perimetre':'ROULEMENT','nbSemaines':1,'dureeMaxSecondes':300,'stabilite':10,"
                + "'maxVacationsParJour':3,'maxVacationsParSemaine':14,'objectif':'ECONOMIE'}")) {
            Resp r = doPost(OPTIMISATIONS, json(limite), planifAdmin());
            issues.put(limite, statutEtCode(r));
            attendreLaFinDeTousLesCalculs();
        }
        assertTrue(debut.length() > 0);
        arbre("validations_du_lancement", issues);
        etatDesExecutions("executions_apres_les_refus");
    }

    @Test
    void les_dates_et_les_pages_mal_formees_de_la_consultation_donnent_500_ou_une_page_vide() throws Exception {
        unPatientEnAttente();
        UUID run = calculer("RUN", "PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1);
        Map<String, String> issues = new LinkedHashMap<>();
        issues.put("calendrier semaine=abc", statutEtCode(doGet(OPTIMISATIONS + "/" + run + "/calendrier?semaine=abc", adminA())));
        issues.put("consultation d'un identifiant non UUID", statutEtCode(doGet(OPTIMISATIONS + "/pas-un-uuid", adminA())));
        issues.put("historique page=-1&size=0", doGet(OPTIMISATIONS + "?page=-1&size=0", adminA()).raw());
        issues.put("historique page=abc", statutEtCode(doGet(OPTIMISATIONS + "?page=abc", adminA())));
        issues.put("historique size=1000", String.valueOf(corps(doGet(OPTIMISATIONS + "?size=1000", adminA())).path("size").asInt()));
        issues.put("historique page=5 (au-dela)", doGet(OPTIMISATIONS + "?page=5&size=20", adminA()).raw());
        arbre("consultations_mal_formees", issues);
    }

    // ------------------------------------------------------------------ arrêt

    @Test
    void l_arret_anticipe_garde_la_meilleure_solution_et_n_a_aucun_effet_sur_un_calcul_termine() throws Exception {
        unPatientEnAttente();
        Map<String, Object> issues = new LinkedHashMap<>();
        Resp lancement = lancer("PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1, planifAdmin());
        UUID run = UUID.fromString(corps(lancement).path("id").asString());
        aliasNommes.put(run.toString(), "<RUN>");
        Resp arret = doPost(OPTIMISATIONS + "/" + run + "/arret", "", planifAdmin());
        issues.put("arret demande pendant le calcul", statutEtCode(arret));
        execution("arret_pendant_le_calcul", arret);
        attendreLaFin(run);
        issues.put("statut apres l'arret", corps(consulter(run)).path("statut").asString());
        Resp encore = doPost(OPTIMISATIONS + "/" + run + "/arret", "", planifAdmin());
        issues.put("arret d'un calcul termine", statutEtCode(encore));
        execution("arret_d_un_calcul_termine", encore);
        issues.put("arret d'une execution inconnue", statutEtCode(doPost(OPTIMISATIONS + "/" + UUID.randomUUID() + "/arret", "",
                planifAdmin())));
        arbre("arrets", issues);
    }

    // ------------------------------------------------------------------ historique, purge

    @Test
    void l_historique_est_pagine_du_plus_recent_au_plus_ancien_sans_le_detail_des_propositions() throws Exception {
        unPatientEnAttente();
        calculer("RUN_1", "ROULEMENT", semaine(1, JourSemaine.DIMANCHE), 1);
        Thread.sleep(1100);
        calculer("RUN_2", "COUVERTURE", semaine(1, JourSemaine.DIMANCHE), 1);
        Thread.sleep(1100);
        calculer("RUN_3", "MAINTENANCE", semaine(2, JourSemaine.DIMANCHE), 2);
        lecture("historique_page_0_taille_2", sansEmpreinte(doGet(OPTIMISATIONS + "?page=0&size=2", adminA())));
        lecture("historique_page_1_taille_2", sansEmpreinte(doGet(OPTIMISATIONS + "?page=1&size=2", adminA())));
        Resp parDefaut = doGet(OPTIMISATIONS, adminA());
        arbre("historique_par_defaut", Map.of("statut", parDefaut.status(), "page", corps(parDefaut).path("page").asInt(),
                "size", corps(parDefaut).path("size").asInt(), "total", corps(parDefaut).path("total").asInt(),
                "items", corps(parDefaut).path("items").size()));
        lecture("historique_autre_centre", doGet(OPTIMISATIONS, adminB()));
    }

    @Test
    void seules_les_vingt_dernieres_executions_du_centre_sont_conservees_au_lancement_d_une_nouvelle() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        List<UUID> anciennes = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            anciennes.add(executionEcrite(CENTRE_A, "ANCIENNE_" + (i < 10 ? "0" : "") + i, "TERMINEE", "ROULEMENT", 21 - i, null));
        }
        executionEcrite(CENTRE_B, "AUTRE_CENTRE", "TERMINEE", "ROULEMENT", 30, null);
        calculer("NOUVELLE", "ROULEMENT", semaine(1, JourSemaine.DIMANCHE), 1);
        arbre("apres_le_21e_lancement", Map.of(
                "executions du centre A", jdbc.queryForObject("SELECT COUNT(*) FROM planification_optimisation WHERE center_id = ?",
                        Integer.class, CENTRE_A),
                "la plus ancienne subsiste", jdbc.queryForObject("SELECT COUNT(*) FROM planification_optimisation WHERE id = ?",
                        Integer.class, anciennes.get(0)),
                "la deuxieme plus ancienne subsiste", jdbc.queryForObject("SELECT COUNT(*) FROM planification_optimisation WHERE id = ?",
                        Integer.class, anciennes.get(1)),
                "executions du centre B", jdbc.queryForObject("SELECT COUNT(*) FROM planification_optimisation WHERE center_id = ?",
                        Integer.class, CENTRE_B)));
    }

    // ------------------------------------------------------------------ calendrier et impression

    @Test
    void le_calendrier_fige_de_la_proposition_se_lit_par_semaine() throws Exception {
        unPatientEnAttente();
        UUID run = calculer("RUN", "PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1);
        lecture("calendrier_par_defaut", doGet(OPTIMISATIONS + "/" + run + "/calendrier", adminA()));
        lecture("calendrier_d_un_mercredi_de_la_semaine",
                doGet(OPTIMISATIONS + "/" + run + "/calendrier?semaine=" + semaine(1, JourSemaine.MERCREDI), adminA()));
        lecture("calendrier_d_une_semaine_hors_proposition",
                doGet(OPTIMISATIONS + "/" + run + "/calendrier?semaine=" + semaine(3, JourSemaine.LUNDI), adminA()));
        UUID sansCalendrier = executionEcrite(CENTRE_A, "SANS_CALENDRIER", "TERMINEE", "ROULEMENT", 1, null);
        lecture("calendrier_d_une_execution_sans_calendrier", doGet(OPTIMISATIONS + "/" + sansCalendrier + "/calendrier", adminA()));
        lecture("calendrier_d_une_execution_inconnue", doGet(OPTIMISATIONS + "/" + UUID.randomUUID() + "/calendrier", adminA()));
        lecture("calendrier_vu_d_un_autre_centre", doGet(OPTIMISATIONS + "/" + run + "/calendrier", adminB()));
    }

    @Test
    void l_impression_rend_un_pdf_pour_une_proposition_calculee_et_refuse_une_execution_sans_calendrier() throws Exception {
        unPatientEnAttente();
        UUID run = calculer("RUN", "PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1);
        var pdf = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(java.net.URI.create(OPTIMISATIONS + "/" + run + "/impression")).with(adminA())).andReturn().getResponse();
        Map<String, Object> issues = new LinkedHashMap<>();
        issues.put("statut", pdf.getStatus());
        issues.put("type", pdf.getContentType());
        issues.put("debut du contenu", new String(pdf.getContentAsByteArray(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1));
        issues.put("en-tete Content-Disposition", String.valueOf(pdf.getHeader("Content-Disposition")).replace(run.toString(), "<RUN>"));
        UUID sansCalendrier = executionEcrite(CENTRE_A, "SANS_CALENDRIER", "TERMINEE", "ROULEMENT", 1, null);
        issues.put("execution sans calendrier", statutEtCode(doGet(OPTIMISATIONS + "/" + sansCalendrier + "/impression", adminA())));
        issues.put("execution inconnue", statutEtCode(doGet(OPTIMISATIONS + "/" + UUID.randomUUID() + "/impression", adminA())));
        issues.put("autre centre", statutEtCode(doGet(OPTIMISATIONS + "/" + run + "/impression", adminB())));
        arbre("impression", issues);
    }

    // ------------------------------------------------------------------ suppression

    @Test
    void la_suppression_est_refusee_pendant_le_calcul_puis_retire_l_execution_et_son_calendrier() throws Exception {
        unPatientEnAttente();
        Map<String, Object> issues = new LinkedHashMap<>();
        Resp lancement = lancer("PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1, planifAdmin());
        UUID run = UUID.fromString(corps(lancement).path("id").asString());
        aliasNommes.put(run.toString(), "<RUN>");
        issues.put("suppression pendant le calcul", statutEtCode(supprimer(run, planifAdmin())));
        attendreLaFin(run);
        issues.put("lignes du calendrier avant", jdbc.queryForObject("SELECT COUNT(*) FROM planification_calendrier_case WHERE run_id = ?",
                Integer.class, run) > 0);
        issues.put("suppression par un secretaire", statutEtCode(supprimer(run, planifSecretaire())));
        issues.put("suppression par l'administrateur d'un autre centre", statutEtCode(supprimer(run, adminB())));
        issues.put("suppression par l'administrateur", statutEtCode(supprimer(run, planifAdmin())));
        issues.put("execution restante", jdbc.queryForObject("SELECT COUNT(*) FROM planification_optimisation WHERE id = ?", Integer.class,
                run));
        issues.put("lignes du calendrier apres", jdbc.queryForObject("SELECT COUNT(*) FROM planification_calendrier_case WHERE run_id = ?",
                Integer.class, run));
        issues.put("seconde suppression", statutEtCode(supprimer(run, planifAdmin())));
        arbre("suppression", issues);
    }

    // ------------------------------------------------------------------ garde de centre

    @Test
    void une_execution_n_est_visible_et_modifiable_que_depuis_son_centre() throws Exception {
        unPatientEnAttente();
        UUID run = calculer("RUN", "PATIENTS", semaine(1, JourSemaine.DIMANCHE), 1);
        Map<String, String> issues = new LinkedHashMap<>();
        issues.put("consulter depuis B", statutEtCode(doGet(OPTIMISATIONS + "/" + run, adminB())));
        issues.put("arreter depuis B", statutEtCode(doPost(OPTIMISATIONS + "/" + run + "/arret", "", adminB())));
        issues.put("appliquer depuis B", statutEtCode(appliquer(run, adminB())));
        issues.put("supprimer depuis B", statutEtCode(supprimer(run, adminB())));
        issues.put("consulter A en demandant le centre B", statutEtCode(doGet(OPTIMISATIONS + "/" + run + "?centerId=" + CENTRE_B, adminA())));
        issues.put("historique de A en demandant le centre B", statutEtCode(doGet(OPTIMISATIONS + "?centerId=" + CENTRE_B, adminA())));
        issues.put("lancer pour le centre B depuis A", statutEtCode(doPost(OPTIMISATIONS + "?centerId=" + CENTRE_B,
                lancement("ROULEMENT", semaine(1, JourSemaine.DIMANCHE), 1), adminA())));
        issues.put("consulter depuis A avec son propre centre", statutEtCode(doGet(OPTIMISATIONS + "/" + run + "?centerId=" + CENTRE_A, adminA())));
        arbre("garde_de_centre", issues);
        etatDesExecutions("executions_apres_les_refus_de_centre");
    }
}
