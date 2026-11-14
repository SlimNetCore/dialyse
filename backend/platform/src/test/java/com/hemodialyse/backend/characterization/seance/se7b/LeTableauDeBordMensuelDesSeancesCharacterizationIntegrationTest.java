package com.hemodialyse.backend.characterization.seance.se7b;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SE7b : les agrégats du tableau de bord mensuel des séances ({@code GET /api/v1/seances/dashboard}, {@code SeanceRestController}).
 * Absences A4 verrouille déjà les détails par type, les mois 13 et illisible, le centre autre, le mois futur et six profils ; PC1 deux
 * mois (courant et 2099) pour l'effectif. Ici : les formules (attendus, présences, effectif, sexes, tranches d'âge), les bornes du mois,
 * les jours non ouverts, les mois vides et limites, les paramètres, les droits. Décrit le comportement ACTUEL.
 */
class LeTableauDeBordMensuelDesSeancesCharacterizationIntegrationTest extends Se7bCharacterizationSupport {

    @Override
    protected String dossier() {
        return "tableau";
    }

    @Test
    void l_effectif_les_attendus_et_les_repartitions_de_janvier_suivent_l_etat_des_fiches() throws Exception {
        jeuNominalDeJanvier();
        fixe(0);
        fixe(30);
        Resp r = tableau(CENTRE_A, 2026, 1, adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reponse", reponse(r));
        arbre("tableau", m);
    }

    @Test
    void les_presences_et_les_absences_ne_comptent_que_les_statuts_et_les_jours_du_mois_du_centre() throws Exception {
        UUID q1 = patientTb(CENTRE_A, "Q1", "Nom Q1", "Pre Q1", "M", LocalDate.of(1970, 1, 1), ANCIEN, "PERMANENT", null, false, "");
        UUID q2 = patientTb(CENTRE_A, "Q2", "Nom Q2", "Pre Q2", "F", LocalDate.of(1970, 1, 1), ANCIEN, "PERMANENT", null, false, "");
        UUID qb = patientTb(CENTRE_B, "QB", "Nom QB", "Pre QB", "F", LocalDate.of(1970, 1, 1), ANCIEN, "PERMANENT", null, false, "");
        // décembre 2025 : <D-1> ; janvier 2026 : <D+0> à <D+30> ; février 2026 : <D+31> à <D+58>
        seanceTb(CENTRE_A, q1, fixe(-1), "VALIDEE");
        seanceTb(CENTRE_A, q1, fixe(0), "VALIDEE");
        seanceTb(CENTRE_A, q1, fixe(1), "SIGNEE");
        seanceTb(CENTRE_A, q1, fixe(2), "FACTUREE");
        seanceTb(CENTRE_A, q1, fixe(3), "CREE");
        seanceTb(CENTRE_A, q1, fixe(5), "BROUILLON");
        seanceTb(CENTRE_A, q1, fixe(6), "validee");
        seanceTb(CENTRE_A, q1, fixe(30), "VALIDEE");
        seanceTb(CENTRE_A, q1, fixe(31), "SIGNEE");
        seanceTb(CENTRE_A, q2, fixe(0), "VALIDEE");
        seanceTb(CENTRE_B, qb, fixe(0), "VALIDEE");
        seanceTb(CENTRE_B, qb, fixe(1), "SIGNEE");
        absenceTb(CENTRE_A, q1, fixe(-1), "A_QUALIFIER");
        absenceTb(CENTRE_A, q1, fixe(0), "A_QUALIFIER");
        absenceTb(CENTRE_A, q1, fixe(1), "JUSTIFIEE");
        absenceTb(CENTRE_A, q1, fixe(2), "NON_JUSTIFIEE");
        absenceTb(CENTRE_A, q1, fixe(3), "RATTRAPEE");
        absenceTb(CENTRE_A, q1, fixe(4), "ANNULEE");
        absenceTb(CENTRE_A, q1, fixe(30), "A_QUALIFIER");
        absenceTb(CENTRE_A, q1, fixe(31), "JUSTIFIEE");
        absenceTb(CENTRE_B, qb, fixe(0), "NON_JUSTIFIEE");
        fixe(58);
        fixe(-31);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 decembre 2025 (centre A)", resume(tableau(CENTRE_A, 2025, 12, adminA())));
        m.put("02 janvier 2026 (centre A)", resume(tableau(CENTRE_A, 2026, 1, adminA())));
        m.put("03 fevrier 2026 (centre A)", resume(tableau(CENTRE_A, 2026, 2, adminA())));
        m.put("04 janvier 2026 (centre B)", resume(tableau(CENTRE_B, 2026, 1, infirmierB())));
        arbre("compteurs", m);
    }

    /**
     * LEC-PAT (i) : la jointure des présents ne filtre pas le centre du patient (KB-SEA-64, reproduit) ; un patient transféré ou
     * endormi garde sa programmation ; un patient présent compte une seule fois malgré plusieurs séances. Posé AVANT de remplacer la
     * lecture SQL de {@code patients} par le langage publié de Patient et couverture.
     */
    @Test
    void les_presents_d_un_autre_centre_comptent_et_chaque_present_est_compte_une_fois() throws Exception {
        UUID local = patientTb(CENTRE_A, "L1", "Nom L1", "Pre L1", "F", LocalDate.of(1970, 1, 1), ANCIEN, "PERMANENT", null, false, "lundi,jeudi");
        UUID parti = patientTb(CENTRE_A, "L2", "Nom L2", "Pre L2", "M", LocalDate.of(1950, 1, 1), ANCIEN, "TRANSFERE", fixe(-20), false, "lundi");
        UUID endormi = patientTb(CENTRE_A, "L3", "Nom L3", "Pre L3", "M", LocalDate.of(1990, 1, 1), ANCIEN, "PERMANENT", null, true, "jeudi");
        UUID etranger = patientTb(CENTRE_B, "L4", "Nom L4", "Pre L4", "F", LocalDate.of(1985, 1, 1), ANCIEN, "PERMANENT", null, false, "lundi");
        seanceTb(CENTRE_A, local, fixe(1), "VALIDEE");
        seanceTb(CENTRE_A, local, fixe(2), "SIGNEE");
        seanceTb(CENTRE_A, parti, fixe(1), "SIGNEE");
        seanceTb(CENTRE_A, endormi, fixe(3), "FACTUREE");
        seanceTb(CENTRE_A, etranger, fixe(1), "VALIDEE");
        fixe(0);
        fixe(30);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 tableau du centre A", reponse(tableau(CENTRE_A, 2026, 1, adminA())));
        m.put("02 detail des presents du centre A", reponse(details(CENTRE_A, 2026, 1, "presence", adminA())));
        arbre("presents_d_un_autre_centre", m);
    }

    @Test
    void les_jours_feries_et_les_fermetures_du_centre_retirent_les_seances_attendues_sans_double_compte() throws Exception {
        patientTb(CENTRE_A, "T1", "Nom T1", "Pre T1", "M", LocalDate.of(1970, 1, 1), ANCIEN, "PERMANENT", null, false, TOUS_LES_JOURS);
        patientTb(CENTRE_A, "T2", "Nom T2", "Pre T2", "M", LocalDate.of(1970, 1, 1), ANCIEN, "PERMANENT", null, false, "jeudi");
        fixe(0);
        fixe(30);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 sans jour non ouvert", reponse(tableau(CENTRE_A, 2026, 1, adminA())));
        ferieTb(CENTRE_A, fixe(7));
        fermetureTb(CENTRE_A, fixe(7));
        fermetureTb(CENTRE_A, fixe(14));
        ferieTb(CENTRE_A, fixe(-1));
        fermetureTb(CENTRE_A, fixe(31));
        ferieTb(CENTRE_B, fixe(21));
        m.put("02 un ferie et une fermeture le meme jour, une fermeture, deux hors mois, un ferie de l'autre centre",
                reponse(tableau(CENTRE_A, 2026, 1, adminA())));
        arbre("jours_non_ouverts", m);
    }

    @Test
    void les_mois_vides_bissextiles_et_extremes_donnent_un_tableau_de_bord_a_zero() throws Exception {
        fixe(0);
        fixe(30);
        alias(LocalDate.of(2024, 2, 1), "<FEV2024.DEBUT>");
        alias(LocalDate.of(2024, 2, 29), "<FEV2024.FIN>");
        alias(LocalDate.of(9999, 12, 1), "<AN9999.DEBUT>");
        alias(LocalDate.of(1, 1, 1), "<AN1.DEBUT>");
        alias(LocalDate.of(1, 1, 31), "<AN1.FIN>");
        alias(LocalDate.of(0, 1, 1), "<AN0.DEBUT>");
        alias(LocalDate.of(0, 1, 31), "<AN0.FIN>");
        patientTb(CENTRE_A, "V1", "Nom V1", "Pre V1", "M", LocalDate.of(1970, 1, 1), ANCIEN, "PERMANENT", null, false, TOUS_LES_JOURS);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 centre B sans aucune donnee, janvier 2026", reponse(tableau(CENTRE_B, 2026, 1, infirmierB())));
        m.put("02 fevrier 2024 (29 jours) du centre A, un patient tous les jours", reponse(tableau(CENTRE_A, 2024, 2, adminA())));
        m.put("03 decembre 9999", reponse(tableau(CENTRE_A, 9999, 12, adminA())));
        m.put("04 annee 1", resume(tableau(CENTRE_A, 1, 1, adminA())));
        m.put("05 annee 0", resume(tableau(CENTRE_A, 0, 1, adminA())));
        arbre("mois_vides_et_extremes", m);
    }

    @Test
    void les_parametres_du_tableau_de_bord_hors_bornes_ou_absents() throws Exception {
        // A4 verrouille deja : mois 13, annee illisible, centre absent (tableau de bord) ; ici le reste, sur les 3 routes
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 mois 0", refus(tableau(CENTRE_A, 2026, 0, adminA())));
        m.put("02 mois -1", refus(tableau(CENTRE_A, 2026, -1, adminA())));
        m.put("03 annee 1000000000", refus(tableau(CENTRE_A, 1000000000, 1, adminA())));
        m.put("04 annee absente", refus(tableau(CENTRE_A, null, 1, adminA())));
        m.put("05 mois absent", refus(tableau(CENTRE_A, 2026, null, adminA())));
        m.put("06 centre illisible", refus(doGet(SEANCES_API + "/dashboard?centerId=xyz&year=2026&month=1", adminA())));
        m.put("07 detail mois 0", refus(details(CENTRE_A, 2026, 0, "presence", adminA())));
        m.put("08 detail mois 13", refus(details(CENTRE_A, 2026, 13, "presence", adminA())));
        m.put("09 detail annee 1000000000", refus(details(CENTRE_A, 1000000000, 1, "absence", adminA())));
        m.put("10 detail annee absente", refus(details(CENTRE_A, null, 1, "absence", adminA())));
        m.put("11 detail mois absent", refus(details(CENTRE_A, 2026, null, "absence", adminA())));
        m.put("12 detail mois 13 avec un kind inconnu (le kind est controle en premier)", refus(details(CENTRE_A, 2026, 13, "xx", adminA())));
        m.put("13 detail centre illisible", refus(doGet(SEANCES_API + "/dashboard/details?centerId=xyz&year=2026&month=1&kind=presence", adminA())));
        m.put("14 export mois 0", refus(export(CENTRE_A, 2026, 0, "csv", adminA())));
        m.put("15 export mois 13", refus(export(CENTRE_A, 2026, 13, "csv", adminA())));
        m.put("16 export annee 1000000000", refus(export(CENTRE_A, 1000000000, 1, "pdf", adminA())));
        m.put("17 export annee illisible", refus(doGet(SEANCES_API + "/dashboard/export?centerId=" + CENTRE_A + "&year=deux&month=1", adminA())));
        m.put("18 export annee absente", refus(export(CENTRE_A, null, 1, "xlsx", adminA())));
        m.put("19 export centre absent", refus(doGet(SEANCES_API + "/dashboard/export?year=2026&month=1", adminA())));
        m.put("20 export format inconnu et mois 13 (le format est controle apres le centre, avant le mois)", refus(export(CENTRE_A, 2026, 13, "txt", adminA())));
        // KB-SEA-63 corrigé (SE7b, ADR 0095) : 500 -> 400 ; le legacy est archivé sous se7b/_ecarts-legacy/tableau
        arbre("parametres_nouveau_code", m);
    }

    @Test
    void les_droits_du_tableau_de_bord_par_profil_sur_les_trois_routes() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, org.springframework.test.web.servlet.request.RequestPostProcessor> p : profilsSe1().entrySet()) {
            Map<String, String> profil = new LinkedHashMap<>();
            profil.put("GET dashboard", refus(tableau(CENTRE_A, 2026, 1, p.getValue())));
            profil.put("GET dashboard/details", refus(details(CENTRE_A, 2026, 1, "presence", p.getValue())));
            profil.put("GET dashboard/export", refus(export(CENTRE_A, 2026, 1, "csv", p.getValue())));
            m.put(p.getKey(), profil);
        }
        Map<String, String> anonyme = new LinkedHashMap<>();
        anonyme.put("GET dashboard", refus(doGetAnonyme(SEANCES_API + "/dashboard" + requete(CENTRE_A, 2026, 1))));
        anonyme.put("GET dashboard/details", refus(doGetAnonyme(SEANCES_API + "/dashboard/details" + requete(CENTRE_A, 2026, 1) + "&kind=presence")));
        anonyme.put("GET dashboard/export", refus(doGetAnonyme(SEANCES_API + "/dashboard/export" + requete(CENTRE_A, 2026, 1))));
        m.put("anonyme", anonyme);
        arbre("droits", m);
    }

    @Test
    void le_cloisonnement_par_centre_interdit_a_chaque_soignant_la_lecture_d_un_autre_centre_sur_les_trois_routes() throws Exception {
        jeuNominalDeJanvier();
        fixe(0);
        fixe(30);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 infirmier B lit le centre A : tableau de bord", refus(tableau(CENTRE_A, 2026, 1, infirmierB())));
        m.put("02 infirmier B lit le centre A : details", refus(details(CENTRE_A, 2026, 1, "presence", infirmierB())));
        m.put("03 infirmier B lit le centre A : export", refus(export(CENTRE_A, 2026, 1, "csv", infirmierB())));
        m.put("04 secretaire A lit le centre B : tableau de bord", refus(tableau(CENTRE_B, 2026, 1, secretaireA())));
        m.put("05 medecin A lit le centre B : details", refus(details(CENTRE_B, 2026, 1, "absence", medecinA())));
        m.put("06 infirmier B lit son centre : effectif", resume(tableau(CENTRE_B, 2026, 1, infirmierB())));
        m.put("07 centre inexistant", refus(tableau(UUID.fromString("99997800-0000-0000-0000-0000000000ff"), 2026, 1, adminA())));
        arbre("cloisonnement", m);
    }
}
