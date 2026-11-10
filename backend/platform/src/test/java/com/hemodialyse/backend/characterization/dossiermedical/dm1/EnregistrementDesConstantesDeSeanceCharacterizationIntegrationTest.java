package com.hemodialyse.backend.characterization.dossiermedical.dm1;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM1, écriture : le legacy n'a aucune route « enregistrer une constante » ; les mesures s'enregistrent par
 * {@code PUT /api/v1/seances/{id}/volet-paramedical} (propriété de la Séance, écrivain des constantes) et disparaissent avec la séance
 * ({@code DELETE /api/v1/seances/{id}}). Données fictives.
 */
class EnregistrementDesConstantesDeSeanceCharacterizationIntegrationTest extends ConstantesCharacterizationSupport {

    @Override
    protected String dossier() {
        return "ecriture";
    }

    private UUID patientA;
    private UUID seance;

    private void unePremiereSeance(String statut) {
        patientA = patientDm1(CENTRE_A, "PAT_A");
        seance = seanceDm1(CENTRE_A, "SEANCE_1", patientA, jour(-2), statut, 1);
    }

    private Resp put(UUID s, String corps, RequestPostProcessor who) throws Exception {
        return doPut(SEANCES_API + "/" + s + "/volet-paramedical", corps, who);
    }

    private RequestPostProcessor infirmier() {
        return as(CENTRE_A, "INFIRMIER");
    }

    private static final String COMPLET = "\"poidsAvantKg\":70.5,\"poidsApresKg\":68.0,\"taAvant\":\"130/80\",\"taApres\":\"120/70\","
            + "\"dureeMinutes\":240,\"debitSangMlMin\":300,\"ultrafiltrationMl\":2000,\"anticoagulant\":\"HEPARINE\","
            + "\"typeDialysat\":\"STANDARD\",\"incidents\":\"RAS\"";

    @Test
    void la_premiere_saisie_cree_le_volet_et_les_constantes_apparaissent_dans_la_vue_longitudinale() throws Exception {
        unePremiereSeance("CREE");
        Resp r = put(seance, corpsVolet(CENTRE_A, COMPLET), infirmier());
        ecriture("reponse", r);
        // Horodatage de mise à jour : au fuseau du système, récent.
        OffsetDateTime maj = OffsetDateTime.parse(corps(r).path("updatedAt").asString());
        assertThat(Duration.between(maj.toInstant(), java.time.Instant.now()).abs()).isLessThan(Duration.ofMinutes(5));
        etatDuVolet("etat", seance);
        lecture("constantes", doGet(constantes(patientA, null), as(CENTRE_A, "MEDECIN")));
    }

    @Test
    void la_seconde_saisie_remplace_tout_et_efface_les_champs_absents() throws Exception {
        unePremiereSeance("VALIDEE");
        put(seance, corpsVolet(CENTRE_A, COMPLET), infirmier());
        Resp r = put(seance, corpsVolet(CENTRE_A, "\"poidsAvantKg\":71.2"), infirmier());
        ecriture("reponse", r);
        assertThat(nombreDeVolets(seance)).isEqualTo(1);
        etatDuVolet("etat", seance);
    }

    @Test
    void la_tension_est_normalisee_en_mmhg_et_ses_bornes_sont_controlees() throws Exception {
        unePremiereSeance("CREE");
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String ta : new String[]{"130/80", "12/8", "11,5/7", " 120 / 80 ", "30/20", "31/20", "20/20", "400/200", "401/200", "19/10",
                "10/0", "120/130", "120", "120/80/60", "abc/80", "120/xyz", "", "   ", "-120/80", "1e2/80", "12.5/8.5", "30,5/20"}) {
            jdbc.update("DELETE FROM volet_paramedical WHERE seance_id = ?", seance);
            Resp r = put(seance, corpsVolet(CENTRE_A, "\"taAvant\":" + quote(ta)), infirmier());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("statut", statutEtCode(r));
            if (r.status() != 200) m.put("detail", corps(r).path("detail").asString());
            m.put("taAvantEnBase", jdbc.queryForList("SELECT ta_avant FROM volet_paramedical WHERE seance_id = ?", seance).stream()
                    .map(l -> String.valueOf(l.get("ta_avant"))).findFirst().orElse("(aucun volet)"));
            cas.put("[" + ta + "]", m);
        }
        arbre("tensions", cas);
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    @Test
    void les_poids_sont_bornes_de_zero_exclu_a_cinq_cents_kilogrammes() throws Exception {
        unePremiereSeance("CREE");
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String champ : new String[]{"poidsAvantKg", "poidsApresKg"}) {
            for (String poids : new String[]{"0", "-1", "0.001", "0.01", "70.555", "70.554", "500", "500.01", "99999999999"}) {
                jdbc.update("DELETE FROM volet_paramedical WHERE seance_id = ?", seance);
                Resp r = put(seance, corpsVolet(CENTRE_A, "\"" + champ + "\":" + poids), infirmier());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("statut", statutEtCode(r));
                if (r.status() != 200) m.put("detail", corps(r).path("detail").asString());
                m.put("enBase", jdbc.queryForList("SELECT poids_avant_kg, poids_apres_kg FROM volet_paramedical WHERE seance_id = ?", seance)
                        .toString());
                cas.put(champ + "=" + poids, m);
            }
        }
        arbre("poids", cas);
    }

    @Test
    void duree_debit_et_ultrafiltration_n_ont_aucune_borne_et_aucune_coherence_n_est_controlee() throws Exception {
        unePremiereSeance("CREE");
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String champs : new String[]{"\"dureeMinutes\":-5", "\"dureeMinutes\":0", "\"dureeMinutes\":99999", "\"debitSangMlMin\":-1",
                "\"debitSangMlMin\":100000", "\"ultrafiltrationMl\":-100", "\"ultrafiltrationMl\":99999999999999",
                "\"ultrafiltrationMl\":2000.555", "\"poidsAvantKg\":60,\"poidsApresKg\":90",
                "\"dureeMinutes\":\"abc\"", "\"dureeMinutes\":1.5", "\"dureeMinutes\":99999999999",
                "\"anticoagulant\":\"" + "X".repeat(101) + "\"", "\"typeDialysat\":\"" + "Y".repeat(101) + "\""}) {
            jdbc.update("DELETE FROM volet_paramedical WHERE seance_id = ?", seance);
            Resp r = put(seance, corpsVolet(CENTRE_A, champs), infirmier());
            if (champs.startsWith("\"dureeMinutes\":\"abc\"") || champs.equals("\"dureeMinutes\":99999999999")) {
                assertThat(r.status()).as(champs).isEqualTo(400); // KB-SEA-99 (corrigé, L4)
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("statut", statutEtCode(r));
            m.put("enBase", jdbc.queryForList("SELECT poids_avant_kg, poids_apres_kg, duree_minutes, debit_sang_ml_min, ultrafiltration_ml, "
                    + "length(coalesce(anticoagulant,'')) AS l_anticoag, length(coalesce(type_dialysat,'')) AS l_dialysat "
                    + "FROM volet_paramedical WHERE seance_id = ?", seance).toString());
            cas.put(champs.length() > 60 ? champs.substring(0, 60) + "..." : champs, m);
        }
        arbre("autres_mesures_nouveau_code", cas);
    }

    @Test
    void seule_la_seance_facturee_est_non_modifiable_et_les_autres_statuts_acceptent_la_saisie() throws Exception {
        patientA = patientDm1(CENTRE_A, "PAT_A");
        Map<String, Object> cas = new LinkedHashMap<>();
        int i = 0;
        for (String statut : new String[]{"CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT"}) {
            UUID s = seanceDm1(CENTRE_A, "SEANCE_" + statut, patientA, jour(-10 + (++i)), statut, i);
            Resp r = put(s, corpsVolet(CENTRE_A, COMPLET), infirmier());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("statut", statutEtCode(r));
            if (r.status() != 200) m.put("corps", mapper.readTree(dates(r.raw())));
            m.put("volets", nombreDeVolets(s));
            cas.put(statut, m);
        }
        arbre("statuts", cas);
    }

    @Test
    void seance_inconnue_corps_invalide_et_centre_du_corps_manquant() throws Exception {
        unePremiereSeance("CREE");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("seance inconnue", reponse(put(UUID.nameUUIDFromBytes("inconnue".getBytes()), corpsVolet(CENTRE_A, COMPLET), infirmier())));
        cas.put("corps vide {}", reponse(put(seance, "{}", infirmier())));
        cas.put("centerId null", reponse(put(seance, corpsVolet(null, COMPLET), infirmier())));
        cas.put("centerId illisible", reponse(put(seance, "{\"centerId\":\"xx\"}", infirmier())));
        Resp malForme = put(seance, "{", infirmier());
        Resp absent = doPut(SEANCES_API + "/" + seance + "/volet-paramedical", "", infirmier());
        Resp identifiantIllisible = doPut(SEANCES_API + "/xx/volet-paramedical", corpsVolet(CENTRE_A, COMPLET), infirmier());
        cas.put("JSON mal forme", reponse(malForme));
        cas.put("corps absent", reponse(absent));
        cas.put("identifiant de seance illisible", reponse(identifiantIllisible));
        cas.put("volets en base", nombreDeVolets(seance));
        // KB-SEA-99 (corrigé, L4) : un corps ou un identifiant illisible est un 400 BAD_REQUEST, plus un 500
        assertThat(malForme.status()).isEqualTo(400);
        assertThat(absent.status()).isEqualTo(400);
        assertThat(identifiantIllisible.status()).isEqualTo(400);
        arbre("erreurs_nouveau_code", cas);
    }

    private Object reponse(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", statutEtCode(r));
        m.put("corps", r.raw() == null || r.raw().isBlank() ? "" : sansTechnique(r));
        return m;
    }

    /** Corps JSON relu en arbre (clés triées : l'ordre des clés d'une Map de réponse n'est pas déterministe). */
    private Object sansTechnique(Resp r) {
        String code = corps(r).path("code").asString("");
        if ("INTERNAL_ERROR".equals(code) || "DATA_ACCESS_ERROR".equals(code)) return "<message technique non verrouille>";
        return mapper.readTree(dates(r.raw()));
    }

    @Test
    void les_droits_d_ecriture_par_profil() throws Exception {
        unePremiereSeance("CREE");
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsEtRolePersonnalise().entrySet()) {
            matrice.put(p.getKey(), statutEtCode(put(seance, corpsVolet(CENTRE_A, "\"poidsAvantKg\":70"), p.getValue())));
        }
        matrice.put("anonyme", statutEtCode(appel("PUT", SEANCES_API + "/" + seance + "/volet-paramedical", corpsVolet(CENTRE_A, "\"poidsAvantKg\":70"), null)));
        arbre("profils", matrice);
    }

    @Test
    void le_centre_du_corps_n_est_pas_confronte_a_la_session_de_l_utilisateur() throws Exception {
        unePremiereSeance("CREE");
        UUID patientB = patientDm1(CENTRE_B, "PAT_B");
        UUID sb = seanceDm1(CENTRE_B, "SEANCE_B", patientB, jour(-2), "CREE", 1);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("infirmier du centre A, corps centre B, seance B",
                reponse(put(sb, corpsVolet(CENTRE_B, "\"poidsAvantKg\":81.5,\"taAvant\":\"140/90\""), infirmier())));
        cas.put("infirmier du centre A, corps centre A, seance B", reponse(put(sb, corpsVolet(CENTRE_A, "\"poidsAvantKg\":82"), infirmier())));
        cas.put("infirmier du centre B, corps centre A, seance A", reponse(put(seance, corpsVolet(CENTRE_A, "\"poidsAvantKg\":83"), as(CENTRE_B, "INFIRMIER"))));
        arbre("reponses", cas);
        etatDuVolet("etat_seance_B", sb);
        etatDuVolet("etat_seance_A", seance);
    }

    @Test
    void l_enregistrement_est_journalise_notifie_et_diffuse_avec_l_identite_du_patient() throws Exception {
        unePremiereSeance("CREE");
        monterLAudit();
        Resp r = put(seance, corpsVolet(CENTRE_A, COMPLET), infirmier());
        assertThat(r.status()).isEqualTo(200);
        viderLAudit();
        audit("audit");
        notifications("notifications");
        diffusionsCapturees("diffusions");
    }

    @Test
    void deux_premieres_saisies_simultanees_laissent_un_seul_volet() throws Exception {
        unePremiereSeance("CREE");
        List<Resp> reponses = enParallele(
                () -> put(seance, corpsVolet(CENTRE_A, "\"poidsAvantKg\":70"), infirmier()),
                () -> put(seance, corpsVolet(CENTRE_A, "\"poidsAvantKg\":71"), infirmier()));
        List<Integer> statuts = new ArrayList<>();
        reponses.forEach(r -> statuts.add(r.status()));
        // Invariants seulement (l'entrelacement n'est pas déterministe) : au moins une saisie aboutit, un seul volet.
        assertThat(statuts).contains(200);
        assertThat(nombreDeVolets(seance)).isEqualTo(1);
        assertThat(statuts).allMatch(s -> s == 200 || s == 409 || s == 500);
    }

    @Test
    void la_suppression_de_la_seance_retire_ses_constantes_de_la_vue_longitudinale() throws Exception {
        unePremiereSeance("CREE");
        put(seance, corpsVolet(CENTRE_A, COMPLET), infirmier());
        Resp suppression = doDeleteAvecCorps(SEANCES_API + "/" + seance + "?centerId=" + CENTRE_A, "{\"motif\":\"Erreur de saisie\"}", adminA());
        ecriture("suppression", suppression);
        assertThat(nombreDeVolets(seance)).isZero();
        lecture("constantes", doGet(constantes(patientA, null), as(CENTRE_A, "MEDECIN")));
        Map<String, Object> droits = new LinkedHashMap<>();
        droits.put("INFIRMIER", statutEtCode(doDeleteAvecCorps(SEANCES_API + "/" + seance + "?centerId=" + CENTRE_A, "{\"motif\":\"x\"}", infirmier())));
        arbre("droits", droits);
    }

    @Test
    void les_autres_lecteurs_voient_la_saisie_des_qu_elle_est_enregistree() throws Exception {
        unePremiereSeance("CREE");
        put(seance, corpsVolet(CENTRE_A, COMPLET), infirmier());
        Resp detail = doGet(SEANCES_API + "/" + seance + "?centerId=" + CENTRE_A, infirmier());
        JsonNode p = corps(detail).path("paramedical");
        arbre("detail_seance_paramedical", mapper.readTree(dates(p.toString())));
        Resp stats = doGet(PATIENTS_API + "/" + patientA + "/stats/paramedical?centerId=" + CENTRE_A, infirmier());
        // L5a (2026-11-10, ADR 0117, KB-SEA-86) : clés de poidsEvolution toujours en minuscules ; ancien golden archivé sous _ecarts-legacy
        arbre("stats_nouveau_code", mapper.readTree(dates(datesDeLaStatistique(stats.raw()))));
    }
}
