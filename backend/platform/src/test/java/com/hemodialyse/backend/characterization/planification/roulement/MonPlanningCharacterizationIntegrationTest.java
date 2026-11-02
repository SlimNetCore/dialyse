package com.hemodialyse.backend.characterization.planification.roulement;

import com.hemodialyse.backend.characterization.planification.RoulementCharacterizationSupport;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation P3 : « Mon planning » ({@code /api/v1/infirmiers/moi/**}) : planning de la semaine et absences de
 * l'infirmier dont la fiche est reliée au compte connecté. Les règles de dates (absence passée, retrait avant le début)
 * comparent à « aujourd'hui » : scénarios en jours relatifs {@code <J-4>} ; les scénarios de planning hebdomadaire utilisent les
 * semaines nommées {@code <S1.LUN>} (jamais les deux familles dans un même scénario).
 */
class MonPlanningCharacterizationIntegrationTest extends RoulementCharacterizationSupport {

    private static final String MOI = INFIRMIERS + "/moi";
    private UUID compteAmrani;
    private UUID compteBenali;
    private UUID amrani;
    private UUID benali;
    private RequestPostProcessor sara;
    private RequestPostProcessor karim;

    @Override
    protected String theme() {
        return "p3-roulement/mon-planning";
    }

    @BeforeEach
    void amorcer() {
        decor();
        compteAmrani = compte("COMPTE_AMRANI", "amrani", "Sara Amrani", "INFIRMIER", true, CENTRE_A);
        compteBenali = compte("COMPTE_BENALI", "benali", "Karim Benali", "INFIRMIER", true, CENTRE_A);
        amrani = fiche(CENTRE_A, "INF_AMRANI", "AMRANI", "Amrani", "Sara", "0555000001", "MAJOR", true, true, compteAmrani);
        benali = fiche(CENTRE_A, "INF_BENALI", "BENALI", "Benali", "Karim", null, "INFIRMIER", false, true, compteBenali);
        sara = comme(compteAmrani, CENTRE_A, "INFIRMIER");
        karim = comme(compteBenali, CENTRE_A, "INFIRMIER");
    }

    // ------------------------------------------------------------------ planning

    @Test
    void le_planning_de_la_semaine_reunit_roulement_remplacement_absence_patients_et_collegues_de_l_infirmier_connecte() throws Exception {
        semaine(1);
        patientPlace(CENTRE_A, "PAT_LUN_1", salle1, matin, JourSemaine.LUNDI, JourSemaine.MARDI);
        patientPlace(CENTRE_A, "PAT_LUN_2", salle1, matin, JourSemaine.LUNDI);
        patientsPlaces(CENTRE_A, "PAT_ISO", 1, salleIsolement, apresMidi, JourSemaine.VENDREDI);
        affectation(CENTRE_A, "R_AMRANI_1", amrani, salle1, matin, "LUNDI,MARDI");
        affectation(CENTRE_A, "R_BENALI_1", benali, salle1, matin, "LUNDI");
        absence(CENTRE_A, "ABS_AMRANI", amrani, semaine(1, JourSemaine.MARDI), semaine(1, JourSemaine.MARDI), "CONGE", "Congé");
        remplacement(CENTRE_A, "REMPL_AMRANI", semaine(1, JourSemaine.VENDREDI), salleIsolement, apresMidi, amrani, benali);
        Resp r = doGet(MOI + "/planning?date=" + semaine(1, JourSemaine.MERCREDI), sara);
        assertEquals(200, r.status());
        lecture("planning_complet_de_la_semaine", r);
    }

    /**
     * P9, KB-PLN-106 et KB-PLN-50 (écart décidé le 2026-11-02) : l'ancien « Mon planning » comptait un patient en sommeil dans la case de
     * l'infirmier (liste des occupants, nombre {@code patients} et {@code requis}, comme les alertes de sous-effectif et de sur-effectif
     * de P4). Un seul calcul de présence (celui de {@code SejourDuPatient}) : le patient en sommeil est retiré partout, y compris du
     * nombre de patients et de l'effectif requis. Ancien comportement : {@code p3-roulement/_ecarts-legacy/mon-planning/
     * planning_patient_en_sommeil.legacy.json} (rejoué sur le legacy de {@code de7fcd20~1}, identique octet à octet) ; golden
     * {@code _nouveau_code} : la nouvelle règle.
     */
    @Test
    void un_patient_en_sommeil_disparait_de_mon_planning_comme_de_la_presence_KB_PLN_106() throws Exception {
        semaine(1);
        UUID actif = patientPlace(CENTRE_A, "PAT_ACTIF", salle1, matin, JourSemaine.LUNDI);
        UUID dort = patientPlace(CENTRE_A, "PAT_SOMMEIL", salle1, matin, JourSemaine.LUNDI);
        jdbc.update("UPDATE patients SET en_sommeil = TRUE WHERE id = ?", dort);
        affectation(CENTRE_A, "R_AMRANI_1", amrani, salle1, matin, "LUNDI");
        Resp r = doGet(MOI + "/planning?date=" + semaine(1, JourSemaine.LUNDI), sara);
        assertEquals(200, r.status());
        lecture("planning_patient_en_sommeil", r);
    }

    @Test
    void n_importe_quel_jour_de_la_semaine_donne_le_meme_planning_dimanche_a_samedi() throws Exception {
        semaine(1);
        semaine(2);
        affectation(CENTRE_A, "R_AMRANI_1", amrani, salle1, matin, "LUNDI,MARDI");
        String dimanche = doGet(MOI + "/planning?date=" + semaine(1, JourSemaine.DIMANCHE), sara).raw();
        String samedi = doGet(MOI + "/planning?date=" + semaine(1, JourSemaine.SAMEDI), sara).raw();
        String lundiSuivant = doGet(MOI + "/planning?date=" + semaine(2, JourSemaine.DIMANCHE), sara).raw();
        assertEquals(dimanche, samedi);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dimanche = samedi de la même semaine", dimanche.equals(samedi));
        r.put("semaine suivante identique", dimanche.equals(lundiSuivant));
        r.put("début semaine suivante", corps(doGet(MOI + "/planning?date=" + semaine(2, JourSemaine.DIMANCHE), sara)).path("debut").asString());
        r.put("fin semaine suivante", corps(doGet(MOI + "/planning?date=" + semaine(2, JourSemaine.DIMANCHE), sara)).path("fin").asString());
        arbre("planning_bornes_de_semaine", r);
    }

    @Test
    void sans_date_le_planning_est_celui_de_la_semaine_courante() throws Exception {
        semaine(0);
        Resp r = doGet(MOI + "/planning", sara);
        assertEquals(200, r.status());
        Map<String, Object> resume = new LinkedHashMap<>();
        resume.put("début", corps(r).path("debut").asString());
        resume.put("fin", corps(r).path("fin").asString());
        resume.put("cache", r.cacheControl());
        arbre("planning_sans_date_semaine_courante", resume);
    }

    @Test
    void un_infirmier_desactive_garde_son_planning_vide_et_un_compte_sans_fiche_dans_le_centre_est_refuse() throws Exception {
        semaine(1);
        jdbc.update("UPDATE infirmier SET actif = FALSE WHERE id = ?", amrani);
        affectation(CENTRE_A, "R_AMRANI_1", amrani, salle1, matin, "LUNDI");
        lecture("planning_d_un_infirmier_desactive", doGet(MOI + "/planning?date=" + semaine(1, JourSemaine.LUNDI), sara));
        UUID sansFiche = compte("COMPTE_SANS_FICHE", "sansfiche", "Sans Fiche", "INFIRMIER", true, CENTRE_A);
        UUID dansB = compte("COMPTE_CENTRE_B", "centreb", "Centre B", "INFIRMIER", true, CENTRE_B);
        fiche(CENTRE_B, "INF_CENTRE_B", "ZB", "Zerrouki", "Rania", null, "INFIRMIER", false, true, dansB);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("compte sans fiche", statutEtCode(doGet(MOI + "/planning?date=" + semaine(1, JourSemaine.LUNDI), comme(sansFiche, CENTRE_A, "INFIRMIER"))));
        r.put("fiche liée dans un autre centre seulement", statutEtCode(doGet(MOI + "/planning?date=" + semaine(1, JourSemaine.LUNDI), comme(dansB, CENTRE_A, "INFIRMIER"))));
        r.put("utilisateur inconnu", statutEtCode(doGet(MOI + "/planning?date=" + semaine(1, JourSemaine.LUNDI), comme(UUID.nameUUIDFromBytes("x".getBytes()), CENTRE_A, "INFIRMIER"))));
        arbre("planning_refus_de_fiche", r);
    }

    @Test
    void un_compte_disparu_de_l_administration_laisse_la_fiche_liee_et_le_planning_repond_avec_un_compte_nul() throws Exception {
        semaine(1);
        UUID disparu = UUID.nameUUIDFromBytes("compte-disparu".getBytes());
        UUID sansCompte = fiche(CENTRE_A, "INF_COMPTE_DISPARU", "DISPARU", "Dahmani", "Yanis", null, "INFIRMIER", false, true, disparu);
        Resp r = doGet(MOI + "/planning?date=" + semaine(1, JourSemaine.LUNDI), comme(disparu, CENTRE_A, "INFIRMIER"));
        assertEquals(200, r.status());
        arbre("planning_compte_disparu", corps(r).path("infirmier"));
    }

    @Test
    void les_parametres_de_date_illisibles_ou_etranges_du_planning_sont_observes() throws Exception {
        semaine(1);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("date illisible", statutEtCode(doGet(MOI + "/planning?date=demain", sara)));
        r.put("date impossible", statutEtCode(doGet(MOI + "/planning?date=2026-02-30", sara)));
        r.put("date vide", statutEtCode(doGet(MOI + "/planning?date=", sara)));
        r.put("centre illisible", statutEtCode(doGet(MOI + "/planning?centerId=abc", sara)));
        r.put("centre d'un autre centre", statutEtCode(doGet(MOI + "/planning?centerId=" + CENTRE_B + "&date=" + semaine(1, JourSemaine.LUNDI), sara)));
        r.put("même centre", statutEtCode(doGet(MOI + "/planning?centerId=" + CENTRE_A + "&date=" + semaine(1, JourSemaine.LUNDI), sara)));
        arbre("planning_parametres", r);
    }

    @Test
    void une_absence_declaree_pour_la_semaine_apparait_dans_le_planning_de_l_infirmier_et_dans_la_presence() throws Exception {
        semaine(1);
        affectation(CENTRE_A, "R_AMRANI_1", amrani, salle1, matin, "LUNDI,MARDI");
        patientsPlaces(CENTRE_A, "PAT", 1, salle1, matin, JourSemaine.LUNDI, JourSemaine.MARDI);
        Resp d = doPost(MOI + "/absences", json(corpsMonAbsence(semaine(1, JourSemaine.LUNDI), semaine(1, JourSemaine.LUNDI), "MALADIE", "Grippe")), sara);
        assertEquals(201, d.status());
        Resp planning = doGet(MOI + "/planning?date=" + semaine(1, JourSemaine.LUNDI), sara);
        arbre("planning_apres_declaration_mes_creneaux", corps(planning).path("mesCreneaux"));
        arbre("presence_apres_declaration", casesDePresence(presenceDeLaSemaine(1, adminA())));
        lecture("liste_administration_apres_declaration", doGet(INFIRMIERS + "/absences", adminA()));
    }

    // ------------------------------------------------------------------ absences

    @Test
    void mes_absences_ne_listent_que_les_miennes_du_centre_les_plus_recentes_d_abord() throws Exception {
        semaine(1);
        absence(CENTRE_A, "ABS_1", amrani, semaine(1, JourSemaine.LUNDI), semaine(1, JourSemaine.MARDI), "CONGE", "Un");
        absence(CENTRE_A, "ABS_2", amrani, semaine(2, JourSemaine.LUNDI), semaine(2, JourSemaine.LUNDI), "FORMATION", null);
        absence(CENTRE_A, "ABS_3", benali, semaine(1, JourSemaine.MERCREDI), semaine(1, JourSemaine.MERCREDI), "MALADIE", "Pas la sienne");
        lecture("mes_absences_sara", doGet(MOI + "/absences", sara));
        lecture("mes_absences_karim", doGet(MOI + "/absences", karim));
        lecture("mes_absences_page_2_taille_1", doGet(MOI + "/absences?page=1&size=1", sara));
        lecture("mes_absences_taille_0", doGet(MOI + "/absences?size=0", sara));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("page négative", statutEtCode(doGet(MOI + "/absences?page=-1", sara)));
        r.put("taille négative", statutEtCode(doGet(MOI + "/absences?size=-1", sara)));
        r.put("taille 1000", statutEtCode(doGet(MOI + "/absences?size=1000", sara)));
        r.put("sans fiche", statutEtCode(doGet(MOI + "/absences", comme(UUID.nameUUIDFromBytes("x".getBytes()), CENTRE_A, "INFIRMIER"))));
        arbre("mes_absences_pagination_et_refus", r);
    }

    @Test
    void la_declaration_par_l_infirmier_accepte_le_futur_l_en_cours_et_la_fin_aujourd_hui_et_refuse_une_fin_passee() throws Exception {
        Map<String, Object> r = new LinkedHashMap<>();
        // chaque cas est jugé seul : les absences d'un même infirmier ne peuvent plus se chevaucher (KB-PLN-26), on retire donc la précédente
        r.put("à venir (J+3 à J+5)", declarerPuisRetirer(jour(3), jour(5), "CONGE"));
        r.put("aujourd'hui seulement (J à J)", declarerPuisRetirer(jour(0), jour(0), "MALADIE"));
        r.put("en cours (J-2 à J+2)", declarerPuisRetirer(jour(-2), jour(2), "FORMATION"));
        r.put("terminée aujourd'hui (J-3 à J)", declarerPuisRetirer(jour(-3), jour(0), "AUTRE"));
        r.put("terminée hier (J-3 à J-1)", declarerPuisRetirer(jour(-3), jour(-1), "CONGE"));
        r.put("passée (J-10 à J-8)", declarerPuisRetirer(jour(-10), jour(-8), "CONGE"));
        arbre("declaration_regles_de_date", r);
        ecriture("declaration_passee_refusee", doPost(MOI + "/absences", json(corpsMonAbsence(jour(-3), jour(-1), "CONGE", null)), sara));
    }

    private String declarerPuisRetirer(LocalDate debut, LocalDate fin, String type) throws Exception {
        String resultat = statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(debut, fin, type, null)), sara));
        // l'état est contrôlé avant le retrait : une ligne écrite exactement quand la déclaration est acceptée, avec la période demandée
        assertEquals(resultat.startsWith("201") ? 1 : 0, nombreDe("infirmier_absence"));
        if (resultat.startsWith("201")) {
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM infirmier_absence WHERE center_id = ? AND date_debut = ? AND date_fin = ? "
                    + "AND type = ?", Integer.class, CENTRE_A, java.sql.Date.valueOf(debut), java.sql.Date.valueOf(fin), type));
        }
        jdbc.update("DELETE FROM infirmier_absence WHERE center_id = ?", CENTRE_A);
        return resultat;
    }

    @Test
    void la_declaration_par_l_infirmier_respecte_les_bornes_de_duree_et_controle_la_periode_avant_la_date_passee() throws Exception {
        LocalDate j = jour(0);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("366 jours (J à J+365)", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(j, alias(j.plusDays(365), "<J+365>"), "CONGE", null)), sara)));
        r.put("367 jours (J à J+366)", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(j, j.plusDays(366), "CONGE", null)), sara)));
        r.put("fin avant début", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(jour(2), jour(1), "CONGE", null)), sara)));
        r.put("passée ET trop longue (400 avant 422)", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(j.minusDays(400), jour(-1), "CONGE", null)), sara)));
        arbre("declaration_bornes", r);
        etatAbsences("declaration_bornes_base");
    }

    @Test
    void la_declaration_controle_le_corps_puis_le_type_puis_la_fiche_liee() throws Exception {
        LocalDate j = jour(1);
        UUID sansFiche = compte("COMPTE_SANS_FICHE", "sansfiche", "Sans Fiche", "INFIRMIER", true, CENTRE_A);
        RequestPostProcessor orphelin = comme(sansFiche, CENTRE_A, "INFIRMIER");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sans fiche, demande valide", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(j, j, "CONGE", null)), orphelin)));
        r.put("sans fiche, type inconnu", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(j, j, "VACANCES", null)), orphelin)));
        r.put("sans fiche, début absent", statutEtCode(doPost(MOI + "/absences", json("{'fin':'" + j + "','type':'CONGE'}"), orphelin)));
        r.put("sans fiche, période inversée", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(jour(3), j, "CONGE", null)), orphelin)));
        r.put("avec fiche, type inconnu", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(j, j, "VACANCES", null)), sara)));
        r.put("avec fiche, type blanc", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(j, j, " ", null)), sara)));
        r.put("avec fiche, type en minuscules", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(j, j, "conge", null)), sara)));
        r.put("avec fiche, JSON illisible", statutEtCode(doPost(MOI + "/absences", "{", sara)));
        r.put("avec fiche, dates absentes", statutEtCode(doPost(MOI + "/absences", json("{'type':'CONGE'}"), sara)));
        r.put("avec fiche, infirmierId d'un autre dans le corps (ignoré)", statutEtCode(doPost(MOI + "/absences",
                json("{'infirmierId':'" + benali + "','debut':'" + j + "','fin':'" + j + "','type':'CONGE'}"), sara)));
        arbre("declaration_ordre_des_controles", r);
        sql("declaration_ordre_des_controles_base", "SELECT infirmier_id, date_debut, type FROM infirmier_absence WHERE center_id = ? ORDER BY infirmier_id, date_debut", CENTRE_A);
    }

    @Test
    void la_declaration_notifie_l_administration_le_secretariat_et_le_medecin_et_diffuse_en_temps_reel() throws Exception {
        Resp r = doPost(MOI + "/absences", json(corpsMonAbsence(jour(3), jour(5), "CONGE", "Mariage")), sara);
        assertEquals(201, r.status());
        ecriture("declaration_nominale", r);
        auteursDesAbsences("declaration_nominale_auteur");
        notifications("declaration_notification");
        diffusionsCapturees("declaration_diffusions");
    }

    @Test
    void une_declaration_refusee_ne_notifie_ni_ne_diffuse_rien() throws Exception {
        doPost(MOI + "/absences", json(corpsMonAbsence(jour(-3), jour(-1), "CONGE", null)), sara);
        doPost(MOI + "/absences", json(corpsMonAbsence(jour(2), jour(1), "CONGE", null)), sara);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE center_id = ?", Integer.class, CENTRE_A));
        assertEquals(0, diffusions.size());
    }

    @Test
    void les_absences_qui_se_chevauchent_sont_refusees_aussi_a_l_infirmier_KB_PLN_26() throws Exception {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("première (J+2 à J+6)", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(jour(2), jour(6), "CONGE", null)), sara)));
        r.put("doublon exact", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(jour(2), jour(6), "CONGE", null)), sara)));
        r.put("chevauchement (J+5 à J+9)", statutEtCode(doPost(MOI + "/absences", json(corpsMonAbsence(jour(5), jour(9), "MALADIE", null)), sara)));
        arbre("chevauchements_refuses", r);
        etatAbsences("chevauchements_refuses_base");
    }

    @Test
    void l_infirmier_ne_retire_que_ses_absences_qui_commencent_strictement_apres_aujourd_hui() throws Exception {
        absence(CENTRE_A, "ABS_DEMAIN", amrani, jour(1), jour(2), "CONGE", null);
        absence(CENTRE_A, "ABS_AUJOURDHUI", amrani, jour(0), jour(3), "CONGE", null);
        absence(CENTRE_A, "ABS_EN_COURS", amrani, jour(-2), jour(2), "CONGE", null);
        absence(CENTRE_A, "ABS_PASSEE", amrani, jour(-9), jour(-7), "CONGE", null);
        absenceParLAdministration(CENTRE_A, "ABS_FUTURE_LOINTAINE", amrani, jour(30), jour(35), "CONGE", "Saisie par l'administration");
        absence(CENTRE_A, "ABS_DE_KARIM", benali, jour(10), jour(11), "CONGE", null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("commence aujourd'hui", statutEtCode(doDelete(MOI + "/absences/" + absenceId("ABS_AUJOURDHUI"), sara)));
        r.put("en cours", statutEtCode(doDelete(MOI + "/absences/" + absenceId("ABS_EN_COURS"), sara)));
        r.put("passée", statutEtCode(doDelete(MOI + "/absences/" + absenceId("ABS_PASSEE"), sara)));
        r.put("celle d'un collègue", statutEtCode(doDelete(MOI + "/absences/" + absenceId("ABS_DE_KARIM"), sara)));
        r.put("inconnue", statutEtCode(doDelete(MOI + "/absences/" + UUID.nameUUIDFromBytes("inconnue".getBytes()), sara)));
        r.put("identifiant illisible", statutEtCode(doDelete(MOI + "/absences/pas-un-uuid", sara)));
        r.put("commence demain", statutEtCode(doDelete(MOI + "/absences/" + absenceId("ABS_DEMAIN"), sara)));
        r.put("commence demain, deuxième suppression", statutEtCode(doDelete(MOI + "/absences/" + absenceId("ABS_DEMAIN"), sara)));
        r.put("future saisie par l'administration", statutEtCode(doDelete(MOI + "/absences/" + absenceId("ABS_FUTURE_LOINTAINE"), sara)));
        arbre("retrait_regles", r);
        etatAbsences("retrait_base");
        ecriture("retrait_en_cours_refuse", doDelete(MOI + "/absences/" + absenceId("ABS_EN_COURS"), sara));
        ecriture("retrait_inconnu_refuse", doDelete(MOI + "/absences/" + UUID.nameUUIDFromBytes("inconnue".getBytes()), sara));
        assertEquals(1, diffusions.size(), "seul le retrait accepté (commence demain) est notifié et diffusé : KB-PLN-34");
    }

    @Test
    void le_retrait_controle_la_fiche_avant_l_absence_et_ne_notifie_rien() throws Exception {
        absence(CENTRE_A, "ABS_DEMAIN", amrani, jour(1), jour(2), "CONGE", null);
        UUID sansFiche = compte("COMPTE_SANS_FICHE", "sansfiche", "Sans Fiche", "INFIRMIER", true, CENTRE_A);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sans fiche", statutEtCode(doDelete(MOI + "/absences/" + absenceId("ABS_DEMAIN"), comme(sansFiche, CENTRE_A, "INFIRMIER"))));
        r.put("compte lié, session ouverte sur un autre centre", statutEtCode(doDelete(MOI + "/absences/" + absenceId("ABS_DEMAIN"), comme(compteAmrani, CENTRE_B, "INFIRMIER"))));
        arbre("retrait_ordre_des_controles", r);
        assertEquals(1, nombreDe("infirmier_absence"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE center_id = ?", Integer.class, CENTRE_A));
    }

    // ------------------------------------------------------------------ autres profils liés à une fiche

    @Test
    void les_profils_de_lecture_dont_la_fiche_est_liee_utilisent_mon_planning_les_autres_sont_refuses() throws Exception {
        semaine(1);
        Map<String, Object> r = new LinkedHashMap<>();
        Map<String, String[]> profils = new LinkedHashMap<>();
        profils.put("ADMIN", new String[]{"ADMIN"});
        profils.put("SECRETAIRE", new String[]{"SECRETAIRE"});
        profils.put("MEDECIN seul", new String[]{"MEDECIN"});
        profils.put("INFIRMIER seul", new String[]{"INFIRMIER"});
        profils.put("MEDECIN + INFIRMIER", new String[]{"MEDECIN", "INFIRMIER"});
        profils.put("SUPERADMIN", new String[]{"SUPERADMIN"});
        profils.put("DIRECTION", new String[]{"DIRECTION"});
        profils.put("COMPTABLE", new String[]{"COMPTABLE"});
        profils.put("aucun rôle", new String[]{});
        for (Map.Entry<String, String[]> p : profils.entrySet()) {
            RequestPostProcessor who = comme(compteAmrani, CENTRE_A, p.getValue());
            r.put("GET planning [" + p.getKey() + "]", statutEtCode(doGet(MOI + "/planning?date=" + semaine(1, JourSemaine.LUNDI), who)));
            r.put("GET absences [" + p.getKey() + "]", statutEtCode(doGet(MOI + "/absences", who)));
        }
        arbre("profils_avec_fiche_liee", r);
    }
}
