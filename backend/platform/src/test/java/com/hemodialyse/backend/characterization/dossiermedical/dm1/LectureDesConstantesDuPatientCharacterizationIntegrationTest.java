package com.hemodialyse.backend.characterization.dossiermedical.dm1;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM1, lecture : {@code GET /api/v1/patients/{id}/constantes} (vue longitudinale, seule route « constantes » du legacy) et les autres
 * lecteurs des mêmes mesures (statistiques paramédicales, détail de séance). Données fictives.
 */
class LectureDesConstantesDuPatientCharacterizationIntegrationTest extends ConstantesCharacterizationSupport {

    @Override
    protected String dossier() {
        return "lecture";
    }

    private UUID patientA;
    private UUID s1;
    private UUID s2;
    private UUID s3;
    private UUID s4;
    private UUID s5;

    /** Cinq séances tous statuts : volet complet, partiel, absent, valeurs brutes étranges, égalité de date. */
    private void historique() {
        patientA = patientDm1(CENTRE_A, "PAT_A");
        s1 = seanceDm1(CENTRE_A, "SEANCE_1", patientA, jour(-9), "VALIDEE", 1);
        s2 = seanceDm1(CENTRE_A, "SEANCE_2", patientA, jour(-6), "FACTUREE", 2);
        s3 = seanceDm1(CENTRE_A, "SEANCE_3", patientA, jour(-3), "CREE", 3);
        s4 = seanceDm1(CENTRE_A, "SEANCE_4", patientA, jour(-3), "ABSENT", 4);
        s5 = seanceDm1(CENTRE_A, "SEANCE_5", patientA, jour(-1), "SIGNEE", 5);
        voletDm1(CENTRE_A, "VOLET_1", s1, "70.50", "68.00", "130/80", "120/70", 300, "2000.00", 240);
        voletDm1(CENTRE_A, "VOLET_2", s2, "71.00", null, null, null, null, null, null);
        voletDm1(CENTRE_A, "VOLET_4", s4, "0.00", "-3.00", "n/a", "12/8", -5, "-100.00", 0);
        voletDm1(CENTRE_A, "VOLET_5", s5, "69.99", "67.50", "12/8", "11,5/7", 280, "2500.00", 235);
    }

    @Test
    void l_historique_du_patient_est_liste_du_plus_recent_au_plus_ancien_tous_statuts_et_valeurs_brutes() throws Exception {
        historique();
        Resp r = doGet(constantes(patientA, "centerId=" + CENTRE_A), as(CENTRE_A, "MEDECIN"));
        lecture("liste", r);
        // Aucune identité du patient dans la réponse : seules les mesures et l'identifiant de séance.
        assertThat(r.raw()).doesNotContain("Patient").doesNotContain("PAT_A").doesNotContain(CENTRE_A.toString());
    }

    @Test
    void la_pagination_decoupe_l_historique_et_une_page_hors_limite_est_vide() throws Exception {
        historique();
        Map<String, Object> pages = new LinkedHashMap<>();
        for (int page = 0; page <= 3; page++) {
            Resp r = doGet(constantes(patientA, "page=" + page + "&size=2"), as(CENTRE_A, "MEDECIN"));
            JsonNode c = corps(r);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("statut", r.status());
            m.put("total", c.path("total").asInt());
            m.put("page", c.path("page").asInt());
            m.put("size", c.path("size").asInt());
            java.util.List<String> dates = new java.util.ArrayList<>();
            c.path("items").forEach(i -> dates.add(i.path("dateSeance").asString() + " " + i.path("seanceId").asString()));
            m.put("items", dates);
            pages.put("page " + page, m);
        }
        arbre("pages", pages);
    }

    @Test
    void les_bornes_de_pagination_ne_sont_pas_validees_par_la_route() throws Exception {
        historique();
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String parametres : new String[]{"", "size=0", "size=-1", "page=-1", "size=1000", "size=abc", "page=abc", "size=1&page=4"}) {
            Resp r = doGet(constantes(patientA, parametres), as(CENTRE_A, "MEDECIN"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("statut", statutEtCode(r));
            if (r.status() == 200) {
                JsonNode c = corps(r);
                m.put("items", c.path("items").size());
                m.put("total", c.path("total").asInt());
                m.put("page", c.path("page").asInt());
                m.put("size", c.path("size").asInt());
            }
            cas.put(parametres.isEmpty() ? "(defauts)" : parametres, m);
        }
        arbre("bornes", cas);
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_rend_une_liste_vide_sans_404() throws Exception {
        historique();
        UUID patientB = patientDm1(CENTRE_B, "PAT_B");
        UUID sb = seanceDm1(CENTRE_B, "SEANCE_B", patientB, jour(-2), "VALIDEE", 1);
        voletDm1(CENTRE_B, "VOLET_B", sb, "80.00", "78.00", "140/90", "130/80", 250, "1500.00", 180);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("patient du centre B lu par le centre A", statutEtCorps(doGet(constantes(patientB, null), as(CENTRE_A, "MEDECIN"))));
        cas.put("patient inconnu", statutEtCorps(doGet(constantes(UUID.nameUUIDFromBytes("inconnu".getBytes()), null), as(CENTRE_A, "MEDECIN"))));
        cas.put("identifiant illisible", statutEtCode(doGet(PATIENTS_API + "/pas-un-uuid/constantes", as(CENTRE_A, "MEDECIN"))));
        cas.put("patient du centre A lu par le centre B", statutEtCorps(doGet(constantes(patientA, null), as(CENTRE_B, "MEDECIN"))));
        arbre("cloisonnement", cas);
    }

    private Object statutEtCorps(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        if (r.status() == 200) {
            m.put("total", corps(r).path("total").asInt());
            m.put("items", corps(r).path("items").size());
        }
        return m;
    }

    @Test
    void un_volet_ou_une_seance_rattaches_a_un_autre_centre_ne_sont_pas_lus() throws Exception {
        patientA = patientDm1(CENTRE_A, "PAT_A");
        UUID sa = seanceDm1(CENTRE_A, "SEANCE_1", patientA, jour(-5), "VALIDEE", 1);
        // Volet de la séance A posé sous le centre B : ignoré (constantes nulles).
        voletDm1(CENTRE_B, "VOLET_ETRANGER", sa, "90.00", "88.00", "150/100", "140/90", 200, "1000.00", 120);
        // Séance du patient A rattachée au centre B : absente de la liste du centre A.
        seanceDm1(CENTRE_B, "SEANCE_ETRANGERE", patientA, jour(-4), "VALIDEE", 2);
        lecture("liste", doGet(constantes(patientA, null), as(CENTRE_A, "MEDECIN")));
    }

    @Test
    void les_droits_de_lecture_par_profil_et_la_garde_de_centre() throws Exception {
        historique();
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsEtRolePersonnalise().entrySet()) {
            matrice.put(p.getKey(), statutEtCode(doGet(constantes(patientA, null), p.getValue())));
        }
        matrice.put("anonyme", statutEtCode(doGetAnonymous(constantes(patientA, null))));
        arbre("profils", matrice);

        Map<String, Object> garde = new LinkedHashMap<>();
        garde.put("MEDECIN, centerId absent", statutEtCode(doGet(constantes(patientA, null), as(CENTRE_A, "MEDECIN"))));
        garde.put("MEDECIN, centerId du centre", statutEtCode(doGet(constantes(patientA, "centerId=" + CENTRE_A), as(CENTRE_A, "MEDECIN"))));
        garde.put("MEDECIN, centerId d'un autre centre", statutEtCode(doGet(constantes(patientA, "centerId=" + CENTRE_B), as(CENTRE_A, "MEDECIN"))));
        garde.put("ADMIN, centerId d'un autre centre", statutEtCode(doGet(constantes(patientA, "centerId=" + CENTRE_B), as(CENTRE_A, "ADMIN"))));
        garde.put("MEDECIN, centerId illisible", statutEtCode(doGet(constantes(patientA, "centerId=xx"), as(CENTRE_A, "MEDECIN"))));
        garde.put("MEDECIN sans centre", statutEtCode(doGet(constantes(patientA, null), as(null, "MEDECIN"))));
        garde.put("SUPERADMIN avec centerId", statutEtCode(doGet(constantes(patientA, "centerId=" + CENTRE_A), as(CENTRE_A, "SUPERADMIN"))));
        arbre("garde_de_centre", garde);
    }

    @Test
    void la_lecture_des_constantes_n_est_ni_journalisee_ni_diffusee_ni_notifiee() throws Exception {
        historique();
        monterLAudit();
        Resp r = doGet(constantes(patientA, null), as(CENTRE_A, "MEDECIN"));
        assertThat(r.status()).isEqualTo(200);
        viderLAudit();
        audit("audit");
        diffusionsCapturees("diffusions");
        notifications("notifications");
    }

    @Test
    void la_lecture_est_toujours_fraiche_sans_cache_et_porte_son_en_tete_de_cache() throws Exception {
        historique();
        Resp avant = doGet(constantes(patientA, "size=1"), as(CENTRE_A, "MEDECIN"));
        jdbc.update("UPDATE volet_paramedical SET poids_avant_kg = 99.90 WHERE seance_id = ?", s5);
        Resp apres = doGet(constantes(patientA, "size=1"), as(CENTRE_A, "MEDECIN"));
        lecture("avant", avant);
        lecture("apres", apres);
    }

    @Test
    void les_statistiques_paramedicales_et_le_detail_de_seance_lisent_les_memes_mesures_sans_garde_de_centre() throws Exception {
        historique();
        UUID patientB = patientDm1(CENTRE_B, "PAT_B");
        UUID sb = seanceDm1(CENTRE_B, "SEANCE_B", patientB, jour(-2), "VALIDEE", 1);
        voletDm1(CENTRE_B, "VOLET_B", sb, "80.00", "78.00", "140/90", "130/80", 250, "1500.00", 180);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("stats du centre", stat(doGet(PATIENTS_API + "/" + patientA + "/stats/paramedical?centerId=" + CENTRE_A, as(CENTRE_A, "INFIRMIER"))));
        cas.put("stats filtrees", stat(doGet(PATIENTS_API + "/" + patientA + "/stats/paramedical?centerId=" + CENTRE_A + "&from=" + jour(-6) + "&to=" + jour(-3), as(CENTRE_A, "INFIRMIER"))));
        cas.put("stats du patient d'un autre centre (centerId du centre B, session A)",
                stat(doGet(PATIENTS_API + "/" + patientB + "/stats/paramedical?centerId=" + CENTRE_B, as(CENTRE_A, "INFIRMIER"))));
        cas.put("stats MEDECIN seul", statutEtCode(doGet(PATIENTS_API + "/" + patientA + "/stats/paramedical?centerId=" + CENTRE_A, as(CENTRE_A, "MEDECIN"))));
        cas.put("detail de seance du centre B lu par la session A", detail(doGet(SEANCES_API + "/" + sb + "?centerId=" + CENTRE_B, as(CENTRE_A, "INFIRMIER"))));
        cas.put("detail de seance du centre A", detail(doGet(SEANCES_API + "/" + s5 + "?centerId=" + CENTRE_A, as(CENTRE_A, "INFIRMIER"))));
        arbre("autres_lecteurs", cas);
    }

    private Object stat(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", statutEtCode(r));
        if (r.status() == 200) {
            m.put("corps", mapper.readTree(dates(datesDeLaStatistique(r.raw()))));
        }
        return m;
    }

    private Object detail(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", statutEtCode(r));
        if (r.status() == 200) {
            JsonNode c = corps(r);
            m.put("paramedical", mapper.readTree(dates(c.path("paramedical").toString())));
            m.put("cles", c.properties().stream().map(java.util.Map.Entry::getKey).toList());
        }
        return m;
    }
}
