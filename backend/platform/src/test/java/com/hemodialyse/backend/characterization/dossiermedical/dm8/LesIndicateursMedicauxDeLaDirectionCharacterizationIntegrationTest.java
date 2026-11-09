package com.hemodialyse.backend.characterization.dossiermedical.dm8;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM8, lot 4 : indicateurs médicaux lus par la Direction ({@code GET /api/v1/direction/indicators}, {@code DirectionIndicatorsQueryService}) dans
 * {@code resultats_analyses}, {@code serologies_patient} et {@code bilans_pre_greffe} : dernier résultat de chaque patient sur la période,
 * cibles KDIGO, seuil d'anonymat (5), sérologies positives, greffes, périodes invalides, périmètre de la société, droits.
 */
class LesIndicateursMedicauxDeLaDirectionCharacterizationIntegrationTest extends Dm8CharacterizationSupport {

    private static final String INDICATEURS = "/api/v1/direction/indicators";

    @Override
    protected String dossier() {
        return "direction";
    }

    private final Map<UUID, Map<String, Object>> centresInitiaux = new LinkedHashMap<>();
    private boolean societeCreee;
    private final UUID autreSociete = idStable("societe", "autre");
    private final UUID autreCentre = idStable("centre", "autre-societe");

    /** Les lignes {@code centers} des deux centres de test peuvent préexister (autres filets) : on les rattache à la société de test puis on les rétablit. */
    @BeforeEach
    void societeEtCentres() {
        aliasNommes.put(SOCIETE.toString(), "<SOCIETE>");
        aliasNommes.put(autreCentre.toString(), "<CENTRE_AUTRE_SOCIETE>");
        centresInitiaux.clear();
        societeCreee = jdbc.queryForObject("SELECT COUNT(*) FROM societes WHERE id = ?", Integer.class, SOCIETE) == 0;
        if (societeCreee) {
            jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)", SOCIETE, "ZT-DM8", "Societe DM8");
        }
        jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", U_ADMIN);
        jdbc.update("DELETE FROM app_user WHERE id = ?", U_ADMIN);
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)", U_ADMIN, "zt-dm8-direction", "x", null,
                "Direction DM8");
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", U_ADMIN, SOCIETE);
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            var existants = jdbc.queryForList("SELECT code, name, societe_id, actif FROM centers WHERE id = ?", centre);
            if (existants.isEmpty()) {
                jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", centre, centre.equals(CENTRE_A) ? "ZT-DM8-A" : "ZT-DM8-B",
                        centre.equals(CENTRE_A) ? "Centre A" : "Centre B", SOCIETE);
            } else {
                centresInitiaux.put(centre, existants.get(0));
                jdbc.update("UPDATE centers SET societe_id = ?, name = ?, actif = TRUE WHERE id = ?", SOCIETE, centre.equals(CENTRE_A) ? "Centre A" : "Centre B", centre);
            }
        }
    }

    @AfterEach
    void nettoyerDirection() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            Map<String, Object> avant = centresInitiaux.get(centre);
            if (avant == null) {
                jdbc.update("DELETE FROM centers WHERE id = ?", centre);
            } else {
                jdbc.update("UPDATE centers SET societe_id = ?, name = ?, actif = ? WHERE id = ?", avant.get("SOCIETE_ID"), avant.get("NAME"), avant.get("ACTIF"), centre);
            }
        }
        jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ?", autreCentre);
        jdbc.update("DELETE FROM patients WHERE center_id = ?", autreCentre);
        jdbc.update("DELETE FROM centers WHERE id = ?", autreCentre);
        jdbc.update("DELETE FROM societes WHERE id = ?", autreSociete);
        jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", U_ADMIN);
        jdbc.update("DELETE FROM app_user WHERE id = ?", U_ADMIN);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-DM8%'");
        if (societeCreee) jdbc.update("DELETE FROM societes WHERE id = ?", SOCIETE);
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-DM8%'");
    }

    // ------------------------------------------------------------------ fixtures et projections

    private Resp indicateurs(int du, int au) throws Exception {
        return doGet(INDICATEURS + "?from=" + fixe(du) + "&to=" + fixe(au), direction());
    }

    private final Map<String, UUID> crees = new java.util.HashMap<>();

    /** Patient fictif créé une fois par test : alias commençant par B = centre B, par X = centre d'une autre société, sinon centre A. */
    private UUID patient(String alias) {
        return crees.computeIfAbsent(alias, a -> patientDm8(a.startsWith("B") ? CENTRE_B : a.startsWith("X") ? autreCentre : CENTRE_A, a));
    }

    private Map<String, Object> filtre(Map<String, Object> m, String... debuts) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            for (String d : debuts) if (e.getKey().startsWith(d)) r.put(e.getKey(), e.getValue());
        }
        return r;
    }

    /** Un patient par valeur : une analyse du centre à la date donnée avec la seule colonne demandée. */
    private void analyses(UUID centre, String prefixe, String colonne, int jour, String... valeurs) {
        for (int i = 0; i < valeurs.length; i++) {
            analyseEnBase(centre, patient(prefixe + i), prefixe + "_" + colonne + "_" + jour + "_" + i, fixe(jour), colonne, valeurs[i]);
        }
    }

    private JsonNode centre(Resp r, UUID id) {
        for (JsonNode c : corps(r).path("centres")) {
            if (id.toString().equals(c.path("centerId").asString())) return c;
        }
        return mapper.createObjectNode();
    }

    private String marqueur(JsonNode m) {
        return resume(m, "evalues", "pctDansCible", "pctSousCible", "pctAuDessus");
    }

    /** Les douze indicateurs cliniques d'un bloc, en une ligne par indicateur. */
    private Map<String, Object> clinique(JsonNode bloc) {
        JsonNode c = bloc.path("clinique");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ktV (evalues | dans | sous | au dessus)", marqueur(c.path("ktV")));
        m.put("hemoglobine", marqueur(c.path("hemoglobine")));
        m.put("phosphore", marqueur(c.path("phosphore")));
        m.put("pth", marqueur(c.path("pth")));
        m.put("albumine", marqueur(c.path("albumine")));
        m.put("vhb | vhc | vih", resume(c, "vhbPositifs", "vhcPositifs", "vihPositifs"));
        m.put("observance en retard", resume(c, "patientsObservanceEnRetard"));
        m.put("greffe attente | bilan | greffes de la periode", resume(c, "greffeListeAttente", "greffeBilanEnCours", "greffesPeriode"));
        return m;
    }

    // ------------------------------------------------------------------ scénarios

    @Test
    void la_forme_de_la_reponse_et_les_effectifs_inferieurs_a_cinq_sont_masques_mais_zero_reste_zero() throws Exception {
        for (int i = 0; i < 4; i++) {
            analyseEnBase(CENTRE_A, patient("A" + i), "HB_" + i, fixe(10), "hb_g_dl", "11");
            serologieEnBase(CENTRE_A, patient("A" + i), "VHB_" + i, "AG_HBS", "POSITIF", fixe(10));
        }
        String avant = empreinteLecteurs();
        Resp r = indicateurs(0, 200);
        JsonNode c = corps(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 statut", r.status());
        m.put("02 en-tete Cache-Control", r.cacheControl());
        m.put("03 cles de la reponse", c.propertyNames().stream().toList());
        m.put("04 enveloppe", resume(c, "societeId", "from", "to", "generatedAt", "seuilAnonymat"));
        m.put("05 centres (identifiant | nom | actif)", centresDeLaReponse(c));
        m.put("06 centre A : 4 patients sous le seuil (masques)", clinique(centre(r, CENTRE_A)));
        m.put("07 centre B : aucun patient (zero)", clinique(centre(r, CENTRE_B)));
        m.put("08 totaux : 4 patients sous le seuil", clinique(c.path("totaux")));
        m.put("09 stock des totaux", c.path("totaux").path("stock"));
        m.put("10 alertes", c.path("alertes").toString());
        m.put("11 aucune ecriture", empreinteLecteurs().equals(avant));
        arbre("forme", m);
    }

    private List<String> centresDeLaReponse(JsonNode c) {
        List<String> l = new ArrayList<>();
        c.path("centres").forEach(x -> l.add(resume(x, "centerId", "nom", "actif")));
        return l;
    }

    @Test
    void a_cinq_patients_les_taux_dans_sous_et_au_dessus_de_la_cible_sont_publies() throws Exception {
        analyses(CENTRE_A, "A", "hb_g_dl", 10, "9", "10.5", "11", "12", "14");
        analyses(CENTRE_A, "A", "kt_v_mensuel", 10, "1.0", "1.2", "1.4", "1.5", "1.8");
        analyses(CENTRE_A, "A", "phosphore_mg_dl", 10, "3", "4", "5.5", "6", "8");
        analyses(CENTRE_A, "A", "pth_pg_ml", 10, "100", "200", "300", "600", "900");
        analyses(CENTRE_A, "A", "albumine_g_dl", 10, "3", "3.5", "4", "4.5", "5");
        for (int i = 0; i < 5; i++) {
            serologieEnBase(CENTRE_A, patient("A" + i), "VHB_" + i, "AG_HBS", "POSITIF", fixe(10));
            serologieEnBase(CENTRE_A, patient("A" + i), "VHC_" + i, "AC_VHC", "POSITIF", fixe(10));
        }
        Resp r = indicateurs(0, 200);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 statut", r.status());
        m.put("02 centre A : cinq patients", clinique(centre(r, CENTRE_A)));
        m.put("03 totaux (centre B vide)", clinique(corps(r).path("totaux")));
        m.put("04 alertes", corps(r).path("alertes").toString());
        arbre("au_seuil", m);
    }

    @Test
    void chaque_patient_compte_pour_son_dernier_resultat_non_nul_de_la_periode() throws Exception {
        // patients 0 a 4 : un resultat ancien mauvais (8), un recent bon (12), un tout dernier sans hemoglobine ; un resultat apres la periode.
        for (int i = 0; i < 5; i++) {
            analyseEnBase(CENTRE_A, patient("A" + i), "ANCIEN_" + i, fixe(10), "hb_g_dl", "8");
            analyseEnBase(CENTRE_A, patient("A" + i), "RECENT_" + i, fixe(50), "hb_g_dl", "12");
            analyseEnBase(CENTRE_A, patient("A" + i), "SANS_HB_" + i, fixe(60), "kt_v_mensuel", "1.4");
            analyseEnBase(CENTRE_A, patient("A" + i), "APRES_LA_PERIODE_" + i, fixe(150), "hb_g_dl", "5");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 periode 0 a 100 (hemoglobine 12 retenue, Kt/V dernier non nul)", filtre(clinique(centre(indicateurs(0, 100), CENTRE_A)), "hemo", "ktV"));
        m.put("02 periode 0 a 30 (seul le resultat ancien)", filtre(clinique(centre(indicateurs(0, 30), CENTRE_A)), "hemo", "ktV"));
        m.put("03 periode 0 a 200 (le resultat apres 100 est le dernier)", filtre(clinique(centre(indicateurs(0, 200), CENTRE_A)), "hemo", "ktV"));
        m.put("04 periode 20 a 40 (aucun resultat)", filtre(clinique(centre(indicateurs(20, 40), CENTRE_A)), "hemo", "ktV"));
        m.put("05 borne incluse : du jour du resultat ancien au meme jour", filtre(clinique(centre(indicateurs(10, 10), CENTRE_A)), "hemo"));
        arbre("dernier_resultat", m);
    }

    @Test
    void un_patient_est_positif_si_son_dernier_resultat_du_marqueur_avant_la_fin_de_la_periode_est_positif() throws Exception {
        for (int i = 0; i < 5; i++) {
            // positif puis negatif : positif seulement si la periode s'arrete avant le jour 50
            serologieEnBase(CENTRE_A, patient("VHB" + i), "VHB_P_" + i, "AG_HBS", "POSITIF", fixe(10));
            serologieEnBase(CENTRE_A, patient("VHB" + i), "VHB_N_" + i, "AG_HBS", "NEGATIF", fixe(50));
            // positif a deux reprises le meme jour : un seul patient
            serologieEnBase(CENTRE_A, patient("VHC" + i), "VHC_1_" + i, "AC_VHC", "POSITIF", fixe(10));
            serologieEnBase(CENTRE_A, patient("VHC" + i), "VHC_2_" + i, "AC_VHC", "POSITIF", fixe(10));
            // marqueurs non comptes par la Direction (ARN_VHC, TPHA) et positif apres la periode
            serologieEnBase(CENTRE_A, patient("AUTRE" + i), "ARN_" + i, "ARN_VHC", "POSITIF", fixe(10));
            serologieEnBase(CENTRE_A, patient("AUTRE" + i), "TPHA_" + i, "TPHA", "POSITIF", fixe(10));
            serologieEnBase(CENTRE_A, patient("VIH" + i), "VIH_" + i, "VIH_AC", "POSITIF", fixe(150));
            // resultat douteux plus recent qu'un positif
            serologieEnBase(CENTRE_B, patient("B" + i), "VIH_B_P_" + i, "VIH_AC", "POSITIF", fixe(10));
            serologieEnBase(CENTRE_B, patient("B" + i), "VIH_B_D_" + i, "VIH_AC", "DOUTEUX", fixe(20));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int fin : new int[]{40, 60, 100, 200}) {
            Resp r = indicateurs(0, fin);
            m.put(String.format("%03d periode 0 a %d : centre A (vhb | vhc | vih)", fin, fin), resume(centre(r, CENTRE_A).path("clinique"), "vhbPositifs", "vhcPositifs", "vihPositifs"));
            m.put(String.format("%03d periode 0 a %d : centre B", fin, fin), resume(centre(r, CENTRE_B).path("clinique"), "vhbPositifs", "vhcPositifs", "vihPositifs"));
        }
        arbre("serologies", m);
    }

    @Test
    void les_bilans_de_greffe_sont_comptes_par_statut_et_la_date_de_greffe_dans_la_periode_nouveau_code() throws Exception {
        for (int i = 0; i < 5; i++) {
            bilanEnBase(CENTRE_A, patient("ATTENTE" + i), "ATT_" + i, "INSCRIT_LISTE_ATTENTE", null);
            bilanEnBase(CENTRE_A, patient("ENBILAN" + i), "BIL_" + i, "BILAN_EN_COURS", null);
            bilanEnBase(CENTRE_A, patient("GREFFE" + i), "GRE_" + i, "GREFFE_REALISEE", fixe(50));
        }
        bilanEnBase(CENTRE_A, patient("GREFFE_TARDIVE"), "GRE_TARD", "GREFFE_REALISEE", fixe(150));
        for (int i = 0; i < 4; i++) {
            bilanEnBase(CENTRE_B, patient("BATT" + i), "BATT_" + i, "INSCRIT_LISTE_ATTENTE", null);
            bilanEnBase(CENTRE_B, patient("BGRE" + i), "BGRE_" + i, "GREFFE_REALISEE", fixe(50));
        }
        bilanEnBase(CENTRE_B, patient("BBIL"), "BBIL", "NON_DEBUTE", null);
        Map<String, Object> m = new LinkedHashMap<>();
        for (int fin : new int[]{40, 100, 200}) {
            Resp r = indicateurs(0, fin);
            m.put(String.format("%03d periode 0 a %d : centre A (attente | bilan | greffes)", fin, fin), resume(centre(r, CENTRE_A).path("clinique"),
                    "greffeListeAttente", "greffeBilanEnCours", "greffesPeriode"));
            m.put(String.format("%03d periode 0 a %d : centre B (4 attente, 4 greffes, 0 bilan en cours)", fin, fin), resume(centre(r, CENTRE_B).path("clinique"),
                    "greffeListeAttente", "greffeBilanEnCours", "greffesPeriode"));
            m.put(String.format("%03d periode 0 a %d : totaux", fin, fin), resume(corps(r).path("totaux").path("clinique"), "greffeListeAttente", "greffeBilanEnCours",
                    "greffesPeriode"));
        }
        // KB-MED-104 corrige le 2026-11-09 (L1) : un total est supprime (« - ») des qu'un centre est masque (B : 4 attente, 4 greffes).
        arbre("greffes_nouveau_code", m);
    }

    @Test
    void la_periode_doit_etre_ordonnee_et_ne_pas_depasser_cinq_ans() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 from posterieur a to", statutEtCode(indicateurs(100, 10)));
        m.put("02 plus de cinq ans", statutEtCode(doGet(INDICATEURS + "?from=2020-01-01&to=2026-01-02", direction())));
        m.put("03 exactement cinq ans", statutEtCode(doGet(INDICATEURS + "?from=2021-01-01&to=2026-01-01", direction())));
        m.put("04 cinq ans et un jour", statutEtCode(doGet(INDICATEURS + "?from=2021-01-01&to=2026-01-02", direction())));
        m.put("05 date illisible", statutEtCode(doGet(INDICATEURS + "?from=pas-une-date", direction())));
        m.put("06 sans periode (jour courant : seul le statut est verrouille)", doGet(INDICATEURS, direction()).status());
        m.put("07 from seul", doGet(INDICATEURS + "?from=" + fixe(0), direction()).status());
        m.put("08 to seul posterieur au 1er janvier de son annee", doGet(INDICATEURS + "?to=" + fixe(200), direction()).status());
        m.put("09 to seul sur l'annee precedente", statutEtCode(doGet(INDICATEURS + "?to=" + fixe(-10), direction())));
        arbre("periodes_nouveau_code", m);
    }

    @Test
    void seuls_les_centres_de_la_societe_sont_comptes_et_les_totaux_additionnent_les_centres() throws Exception {
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)", autreSociete, "ZT-DM8-X", "Autre societe");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", autreCentre, "ZT-DM8-XC", "Centre autre societe", autreSociete);
        for (int i = 0; i < 5; i++) {
            analyseEnBase(CENTRE_A, patient("A" + i), "HB_A_" + i, fixe(10), "hb_g_dl", "11");
            analyseEnBase(CENTRE_B, patient("B" + i), "HB_B_" + i, fixe(10), "hb_g_dl", "11");
            analyseEnBase(autreCentre, patient("X" + i), "HB_X_" + i, fixe(10), "hb_g_dl", "11");
        }
        Resp r = indicateurs(0, 200);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 centres de la reponse", centresDeLaReponse(corps(r)));
        m.put("02 centre A", marqueur(centre(r, CENTRE_A).path("clinique").path("hemoglobine")));
        m.put("03 centre B", marqueur(centre(r, CENTRE_B).path("clinique").path("hemoglobine")));
        m.put("04 totaux (10 patients, le centre d'une autre societe est ignore)", marqueur(corps(r).path("totaux").path("clinique").path("hemoglobine")));
        m.put("05 le centre d'une autre societe n'est pas liste", centre(r, autreCentre).isEmpty());
        // Un centre inactif reste liste.
        jdbc.update("UPDATE centers SET actif = FALSE WHERE id = ?", CENTRE_B);
        m.put("06 centre B desactive : liste", centresDeLaReponse(corps(indicateurs(0, 200))));
        arbre("perimetre", m);
    }

    @Test
    void les_indicateurs_sont_reserves_a_la_direction_n_ecrivent_rien_et_ne_sont_pas_journalises() throws Exception {
        for (int i = 0; i < 5; i++) analyseEnBase(CENTRE_A, patient("A" + i), "HB_" + i, fixe(10), "hb_g_dl", "11");
        String avant = empreinteLecteurs();
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm8().entrySet()) {
            profils.put(p.getKey(), statutEtCode(doGet(INDICATEURS + "?from=" + fixe(0) + "&to=" + fixe(200), p.getValue())));
        }
        profils.put("anonyme", statutEtCode(appel("GET", INDICATEURS, null, null)));
        m.put("01 profils", profils);
        m.put("02 aucune ecriture", empreinteLecteurs().equals(avant));
        monterLAudit();
        doGet(INDICATEURS + "?from=" + fixe(0) + "&to=" + fixe(200), direction());
        m.put("03 journal d'audit (la lecture n'est pas tracee)", journalDAudit());
        arbre("droits", m);
    }
}
