package com.hemodialyse.backend.characterization.dossiermedical.dm7;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM7, lot 5 : droits par profil sur les seize routes, refus sans écriture, cloisonnement par centre (403 / 404 / session), audit,
 * écritures simultanées.
 */
class LesDroitsLeCloisonnementEtLesEffetsDuBilanPreGreffeCharacterizationIntegrationTest extends Dm7CharacterizationSupport {

    @Override
    protected String dossier() {
        return "droits";
    }

    private UUID patientA;
    private UUID patientB;
    private UUID etapeA;
    private UUID donneurA;
    private UUID etapeB;
    private UUID donneurB;

    private void deuxCentres() {
        patientA = patientDm7(CENTRE_A, "PAT_A");
        patientB = patientDm7(CENTRE_B, "PAT_B");
        etapeA = etapeEnBase(CENTRE_A, patientA, "ETAPE_A", "ORL", "Consultation ORL", "A_FAIRE", null, null);
        donneurA = donneurEnBase(CENTRE_A, patientA, "DONNEUR_A", "Donneur A", "PARENT", "CANDIDAT", "NON_FAIT", 0);
        etapeB = etapeEnBase(CENTRE_B, patientB, "ETAPE_B", "ORL", "Consultation ORL", "A_FAIRE", null, null);
        donneurB = donneurEnBase(CENTRE_B, patientB, "DONNEUR_B", "Donneur B", "PARENT", "CANDIDAT", "NON_FAIT", 0);
        bilanSimple(CENTRE_A, patientA, "BILAN_A", "NON_DEBUTE");
        bilanSimple(CENTRE_B, patientB, "BILAN_B", "NON_DEBUTE");
    }

    private Map<String, String> toutesLesRoutes(UUID patient, UUID etape, UUID donneur, UUID centre, RequestPostProcessor qui) throws Exception {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("01 GET bilan", statutEtCode(doGet(bilanApi(patient) + "?centerId=" + centre, qui)));
        r.put("02 PUT bilan/statut", statutEtCode(doPut(bilanApi(patient) + "/statut", corps("centerId", centre, "statut", "BILAN_EN_COURS"), qui)));
        r.put("03 PUT bilan/immunologique", statutEtCode(doPut(bilanApi(patient) + "/immunologique", corps("centerId", centre, "groupeSanguinConfirme", "A+"), qui)));
        r.put("04 PUT bilan/notes", statutEtCode(doPut(bilanApi(patient) + "/notes", corps("centerId", centre, "contreIndications", "Aucune"), qui)));
        r.put("05 POST bilan/decisions-rcp", statutEtCode(doPost(bilanApi(patient) + "/decisions-rcp", corps("centerId", centre, "dateReunion", fixe(10),
                "avis", "FAVORABLE"), qui)));
        r.put("06 GET etapes", statutEtCode(doGet(etapesApi(patient) + "?centerId=" + centre, qui)));
        r.put("07 POST etapes", statutEtCode(doPost(etapesApi(patient), corps("centerId", centre, "categorie", "AUTRE", "libelle", "Etape de test"), qui)));
        r.put("08 POST etapes/generer-standard", statutEtCode(doPost(etapesApi(patient) + "/generer-standard?centerId=" + centre, "", qui)));
        r.put("09 PUT etapes/{id}", statutEtCode(doPut(etapeApi(patient, etape), corps("centerId", centre, "statut", "PLANIFIE"), qui)));
        r.put("10 GET donneurs", statutEtCode(doGet(donneursApi(patient) + "?centerId=" + centre, qui)));
        r.put("11 POST donneurs", statutEtCode(doPost(donneursApi(patient), corps("centerId", centre, "nom", "Donneur test", "lienParente", "PARENT"), qui)));
        r.put("12 PUT donneurs/{id}", statutEtCode(doPut(donneurApi(patient, donneur), corps("centerId", centre, "nom", "Donneur A", "lienParente", "PARENT",
                "statutBilan", "COMPATIBLE", "crossmatchResultat", "NON_FAIT"), qui)));
        r.put("13 GET kdigo", statutEtCode(doGet(greffe(patient, "kdigo") + "?centerId=" + centre, qui)));
        r.put("14 GET export-pdf", statutEtCode(doGet(greffe(patient, "export-pdf") + "?centerId=" + centre, qui)));
        r.put("15 DELETE etapes/{id}", statutEtCode(doDelete(etapeApi(patient, etape) + "?centerId=" + centre, qui)));
        r.put("16 DELETE donneurs/{id}", statutEtCode(doDelete(donneurApi(patient, donneur) + "?centerId=" + centre, qui)));
        return r;
    }

    /** Regroupe les routes par issue (statut et code) : « 200 » -&gt; numéros des routes ; garde le golden lisible. */
    private Map<String, String> regrouper(Map<String, String> parRoute) {
        Map<String, List<String>> groupes = new java.util.TreeMap<>();
        parRoute.forEach((route, issue) -> groupes.computeIfAbsent(issue, k -> new java.util.ArrayList<>()).add(route.substring(0, 2)));
        Map<String, String> sortie = new LinkedHashMap<>();
        groupes.forEach((issue, routes) -> sortie.put(issue, String.join(" ", routes)));
        return sortie;
    }

    @Test
    void les_droits_par_profil_sur_les_seize_routes() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm7().entrySet()) {
            nettoyerDm7();
            deuxCentres();
            profils.put(p.getKey(), regrouper(toutesLesRoutes(patientA, etapeA, donneurA, CENTRE_A, p.getValue())));
        }
        nettoyerDm7();
        deuxCentres();
        Map<String, String> anonyme = new LinkedHashMap<>();
        anonyme.put("lecture du bilan", statutEtCode(appel("GET", bilanApi(patientA), null, null)));
        anonyme.put("ecriture du statut", statutEtCode(appel("PUT", bilanApi(patientA) + "/statut", corps("statut", "BILAN_EN_COURS"), null)));
        anonyme.put("liste des etapes", statutEtCode(appel("GET", etapesApi(patientA), null, null)));
        anonyme.put("suppression d'un donneur", statutEtCode(appel("DELETE", donneurApi(patientA, donneurA), null, null)));
        anonyme.put("kdigo", statutEtCode(appel("GET", greffe(patientA, "kdigo"), null, null)));
        anonyme.put("export pdf", statutEtCode(appel("GET", greffe(patientA, "export-pdf"), null, null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
    }

    @Test
    void un_refus_de_droit_n_ecrit_rien_et_precede_la_lecture_du_corps() throws Exception {
        deuxCentres();
        String avant = empreinteGreffe();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("INFIRMIER : statut", statutEtCode(doPut(bilanApi(patientA) + "/statut", corps("centerId", CENTRE_A, "statut", "BILAN_EN_COURS"), infirmierA())));
        cas.put("INFIRMIER : statut avec un corps illisible", statutEtCode(doPut(bilanApi(patientA) + "/statut", "{pas du json", infirmierA())));
        cas.put("INFIRMIER : decision de RCP avec un corps absent", statutEtCode(doPost(bilanApi(patientA) + "/decisions-rcp", "", infirmierA())));
        cas.put("INFIRMIER : creation de donneur", statutEtCode(doPost(donneursApi(patientA), corps("centerId", CENTRE_A, "nom", "X", "lienParente", "PARENT"),
                infirmierA())));
        cas.put("INFIRMIER : suppression d'etape", statutEtCode(doDelete(etapeApi(patientA, etapeA), infirmierA())));
        cas.put("ADMIN : statut", statutEtCode(doPut(bilanApi(patientA) + "/statut", corps("centerId", CENTRE_A, "statut", "BILAN_EN_COURS"), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : creation d'etape", statutEtCode(doPost(etapesApi(patientA), corps("centerId", CENTRE_A, "categorie", "AUTRE", "libelle", "x"),
                as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : mise a jour d'etape avec un corps illisible", statutEtCode(doPut(etapeApi(patientA, etapeA), "[", as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : suppression de donneur", statutEtCode(doDelete(donneurApi(patientA, donneurA), as(CENTRE_A, "ADMIN"))));
        cas.put("SECRETAIRE : lecture du bilan", statutEtCode(doGet(bilanApi(patientA), as(CENTRE_A, "SECRETAIRE"))));
        cas.put("INFIRMIER : lecture du bilan", statutEtCode(doGet(bilanApi(patientA), infirmierA())));
        cas.put("INFIRMIER : lecture des donneurs", statutEtCode(doGet(donneursApi(patientA), infirmierA())));
        cas.put("INFIRMIER : lecture KDIGO", statutEtCode(doGet(greffe(patientA, "kdigo"), infirmierA())));
        cas.put("ADMIN : export pdf", statutEtCode(doGet(greffe(patientA, "export-pdf"), as(CENTRE_A, "ADMIN"))));
        cas.put("DIRECTION : lecture des etapes", statutEtCode(doGet(etapesApi(patientA), direction())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("aucune ecriture", empreinteGreffe().equals(avant));
        // KB-MED-89 : 403 AVANT la lecture du corps (avant : 500) ; legacy archivé dans droits/_ecarts-legacy
        arbre("refus_nouveau_code", m);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session_et_aucune_garde_de_patient() throws Exception {
        deuxCentres();
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> a = regrouper(toutesLesRoutes(patientB, etapeB, donneurB, CENTRE_B, medecinA()));
        m.put("01 session A (MEDECIN) vise le centre B sur les seize routes (numeros de routes par issue)", a);
        Map<String, String> b = new LinkedHashMap<>();
        b.put("01 session B lit le bilan du patient du centre A (ouvre un bilan dans B)", refus(doGet(bilanApi(patientA), as(CENTRE_B, "MEDECIN"))));
        b.put("02 session B liste les etapes du patient du centre A", liste(doGet(etapesApi(patientA), as(CENTRE_B, "MEDECIN")), "libelle").toString());
        b.put("03 session B liste les donneurs du patient du centre A", liste(doGet(donneursApi(patientA), as(CENTRE_B, "MEDECIN")), "nom").toString());
        b.put("04 session B met a jour l'etape du centre A", refus(doPut(etapeApi(patientA, etapeA), corps("centerId", CENTRE_B, "statut", "PLANIFIE"),
                as(CENTRE_B, "MEDECIN"))));
        b.put("05 session B supprime l'etape du centre A (204, rien supprime)", String.valueOf(doDelete(etapeApi(patientA, etapeA), as(CENTRE_B, "MEDECIN")).status()));
        b.put("06 session B supprime le donneur du centre A (204, rien supprime)", String.valueOf(doDelete(donneurApi(patientA, donneurA),
                as(CENTRE_B, "MEDECIN")).status()));
        b.put("07 session B cree un donneur pour le patient du centre A", refus(doPost(donneursApi(patientA), corps("centerId", CENTRE_B, "nom", "Donneur croise",
                "lienParente", "PARENT"), as(CENTRE_B, "MEDECIN"))));
        b.put("08 session B lit le KDIGO du patient du centre A", refus(doGet(greffe(patientA, "kdigo"), as(CENTRE_B, "MEDECIN"))));
        b.put("09 session B exporte le PDF du patient du centre A", String.valueOf(doGet(greffe(patientA, "export-pdf"), as(CENTRE_B, "MEDECIN")).status()));
        m.put("02 session B vise le patient du centre A", b);
        Map<String, String> c = new LinkedHashMap<>();
        c.put("01 MEDECIN sans centre de session : lecture", refus(doGet(bilanApi(patientA), as(null, "MEDECIN"))));
        c.put("02 MEDECIN sans centre de session : creation d'etape", refus(doPost(etapesApi(patientA), corps("centerId", CENTRE_A, "categorie", "AUTRE",
                "libelle", "x"), as(null, "MEDECIN"))));
        c.put("03 MEDECIN sans centre de session avec centerId A : lecture", refus(doGet(bilanApi(patientA) + "?centerId=" + CENTRE_A, as(null, "MEDECIN"))));
        c.put("04 SUPERADMIN + MEDECIN (session A) lit le centre B", refus(doGet(bilanApi(patientB) + "?centerId=" + CENTRE_B, as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        c.put("05 SUPERADMIN + MEDECIN (session A) ecrit dans le centre B", refus(doPut(bilanApi(patientB) + "/statut", corps("centerId", CENTRE_B,
                "statut", "BILAN_EN_COURS"), as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        c.put("06 centerId inconnu", refus(doGet(bilanApi(patientA) + "?centerId=" + idStable("centre", "inconnu"), medecinA())));
        m.put("03 sessions particulieres", c);
        m.put("04 bilans", etatDesBilans());
        m.put("05 etapes", etatDesEtapes());
        m.put("06 donneurs", etatDesDonneurs());
        // KB-MED-86 : plus de lecture ni d'écriture croisée entre centres (404 PATIENT_INTROUVABLE), étape d'un autre centre 404 (KB-MED-88) ; legacy archivé dans droits/_ecarts-legacy
        arbre("cloisonnement_nouveau_code", m);
    }

    @Test
    void les_ecritures_sont_journalisees_et_les_lectures_ou_refus_comme_le_reglent_les_regles_d_audit() throws Exception {
        deuxCentres();
        monterLAudit();
        Resp rcp = doPost(bilanApi(patientA) + "/decisions-rcp", corps("centerId", CENTRE_A, "dateReunion", fixe(10), "avis", "FAVORABLE"), medecinA());
        doPut(bilanApi(patientA) + "/statut", corps("centerId", CENTRE_A, "statut", "BILAN_EN_COURS"), medecinA());
        doPut(bilanApi(patientA) + "/immunologique", corps("centerId", CENTRE_A, "groupeSanguinConfirme", "O+"), medecinA());
        doPut(bilanApi(patientA) + "/notes", corps("centerId", CENTRE_A, "contreIndications", "Aucune"), medecinA());
        doPut(bilanApi(patientA) + "/statut", corps("centerId", CENTRE_A, "statut", "GREFFE_REALISEE"), medecinA());
        doGet(bilanApi(patientA), medecinA());
        doGet(greffe(patientA, "kdigo"), medecinA());
        doGet(greffe(patientA, "export-pdf"), medecinA());
        doGet(etapesApi(patientA), infirmierA());
        doGet(donneursApi(patientA), as(CENTRE_A, "ADMIN"));
        Resp etape = doPost(etapesApi(patientA), corps("centerId", CENTRE_A, "categorie", "AUTRE", "libelle", "Etape journalisee"), infirmierA());
        aliasNommes.put(idDe(etape), "<ETAPE_CREEE>");
        doPost(etapesApi(patientA) + "/generer-standard", "", medecinA());
        doPut(etapeApi(patientA, etapeA), corps("centerId", CENTRE_A, "statut", "PLANIFIE"), infirmierA());
        doPut(etapeApi(patientA, idStable("etape", "inconnue")), corps("centerId", CENTRE_A, "statut", "PLANIFIE"), medecinA());
        doDelete(etapeApi(patientA, etapeA), medecinA());
        doDelete(etapeApi(patientA, etapeA), medecinA());
        doDelete(etapeApi(patientA, idStable("etape", "inconnue")), infirmierA());
        Resp donneur = doPost(donneursApi(patientA), corps("centerId", CENTRE_A, "nom", "Donneur journalise", "lienParente", "PARENT"), medecinA());
        aliasNommes.put(idDe(donneur), "<DONNEUR_CREE>");
        doPut(donneurApi(patientA, donneurA), corps("centerId", CENTRE_A, "nom", "Donneur A", "lienParente", "PARENT", "statutBilan", "RETENU",
                "crossmatchResultat", "NON_FAIT"), medecinA());
        doDelete(donneurApi(patientA, donneurA), medecinA());
        doDelete(donneurApi(patientA, donneurA), medecinA());
        doPost(bilanApi(patientA) + "/decisions-rcp", corps("centerId", CENTRE_A, "dateReunion", fixe(11), "avis", "BIDON"), medecinA());
        doPut(bilanApi(patientA) + "/statut", corps("centerId", CENTRE_A, "statut", "BILAN_EN_COURS"), as(CENTRE_A, "ADMIN"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("decision creee", rcp.status());
        m.put("journal", journalDAudit());
        m.put("notifications durables", notificationsVides());
        // KB-MED-88 : une suppression sans effet n'est plus journalisée, une étape introuvable est un 404 ; legacy archivé dans droits/_ecarts-legacy
        arbre("audit_nouveau_code", m);
    }

    private int notificationsVides() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE center_id IN (?, ?)", Integer.class, CENTRE_A, CENTRE_B);
    }

    @Test
    void deux_lectures_simultanees_d_un_patient_sans_bilan_peuvent_ouvrir_deux_bilans_et_bloquer_les_lectures_suivantes() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        List<Resp> reponses = enParallele(() -> doGet(bilanApi(patient), medecinA()), () -> doGet(bilanApi(patient), as(CENTRE_A, "ADMIN")));
        int bilans = nombreDe("bilans_pre_greffe", CENTRE_A);
        Resp suivante = doGet(bilanApi(patient), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 statuts des deux lectures simultanees", reponses.stream().map(Resp::status).toList());
        m.put("02 une ou deux lignes (aucune unicite en base)", bilans == 1 || bilans == 2);
        // KB-MED-90 : un doublon éventuel (H2 n'a pas de verrou consultatif) est lu sans erreur, le plus ancien est retenu ; legacy archivé dans droits/_ecarts-legacy
        m.put("03 la lecture suivante reussit toujours (un doublon est lu sans erreur)", suivante.status() == 200);
        arbre("lectures_simultanees_nouveau_code", m);
    }

    @Test
    void deux_decisions_de_rcp_simultanees_peuvent_s_ecraser_car_le_bilan_est_reecrit_en_entier() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        bilanSimple(CENTRE_A, patient, "BIL", "ELIGIBLE");
        List<Resp> reponses = enParallele(
                () -> doPost(bilanApi(patient) + "/decisions-rcp", corps("centerId", CENTRE_A, "dateReunion", fixe(10), "avis", "FAVORABLE"), medecinA()),
                () -> doPost(bilanApi(patient) + "/decisions-rcp", corps("centerId", CENTRE_A, "dateReunion", fixe(11), "avis", "AJOURNE"), medecinA()));
        int decisions = etatDesDecisions().size();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 statuts", reponses.stream().map(Resp::status).toList());
        m.put("02 une ou deux decisions conservees (la perte d'une decision est possible)", decisions == 1 || decisions == 2);
        arbre("decisions_simultanees", m);
    }

    @Test
    void une_modification_apres_l_autre_garde_la_derniere_valeur_sans_controle_de_version() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        bilanSimple(CENTRE_A, patient, "BIL", "ELIGIBLE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 premiere saisie immunologique", resume(doPut(bilanApi(patient) + "/immunologique", corps("centerId", CENTRE_A, "groupeSanguinConfirme", "A+",
                "praClasseI", 10), medecinA()), "groupeSanguinConfirme", "praClasseI"));
        m.put("02 seconde saisie sans lecture prealable : la derniere ecriture gagne", resume(doPut(bilanApi(patient) + "/immunologique",
                corps("centerId", CENTRE_A, "groupeSanguinConfirme", "B+"), medecinA()), "groupeSanguinConfirme", "praClasseI"));
        m.put("03 relecture", resume(doGet(bilanApi(patient), medecinA()), "groupeSanguinConfirme", "praClasseI"));
        arbre("derniere_ecriture", m);
    }
}
