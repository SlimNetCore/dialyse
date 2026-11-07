package com.hemodialyse.backend.characterization.dossiermedical.dm5;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM5, lot 1 (antécédents) : droits par profil, cloisonnement des centres, audit, export FHIR (Condition), diffusions et notifications.
 */
class LesDroitsEtLesEffetsDesAntecedentsCharacterizationIntegrationTest extends Dm5CharacterizationSupport {

    @Override
    protected String dossier() {
        return "antecedents/droits";
    }

    private UUID patientA;
    private UUID patientB;
    private UUID antecedentA;

    private void deuxCentresEtUnAntecedent() {
        patientA = patientDm5(CENTRE_A, "PAT_A");
        patientB = patientDm5(CENTRE_B, "PAT_B");
        antecedentA = antecedentEnBase(CENTRE_A, patientA, "ANT_A", "MEDICAL", null, null, "Antecedent A", fixe(10), null, "ACTIF");
    }

    private String nouveau(UUID centre, String libelle) {
        return corps("centerId", centre, "type", "MEDICAL", "libelleLibre", libelle, "dateDebut", fixe(20));
    }

    @Test
    void les_droits_par_profil_sur_les_cinq_routes() throws Exception {
        deuxCentresEtUnAntecedent();
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm5().entrySet()) {
            nettoyerDm5();
            deuxCentresEtUnAntecedent();
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("liste", statutEtCode(doGet(antecedentsApi(patientA), p.getValue())));
            ligne.put("creation", statutEtCode(doPost(antecedentsApi(patientA), nouveau(CENTRE_A, "Cree"), p.getValue())));
            ligne.put("correction", statutEtCode(doPut(antecedentApi(patientA, antecedentA),
                    corps("centerId", CENTRE_A, "libelleLibre", "Corrige", "dateDebut", fixe(10)), p.getValue())));
            ligne.put("resolution", statutEtCode(doPut(antecedentApi(patientA, antecedentA) + "/resoudre",
                    corps("centerId", CENTRE_A, "dateResolution", fixe(15)), p.getValue())));
            ligne.put("suppression", statutEtCode(doDelete(antecedentApi(patientA, antecedentA), p.getValue())));
            profils.put(p.getKey(), ligne);
        }
        nettoyerDm5();
        deuxCentresEtUnAntecedent();
        Map<String, Object> anonyme = new LinkedHashMap<>();
        anonyme.put("liste", statutEtCode(doGetAnonymous(antecedentsApi(patientA))));
        anonyme.put("creation", statutEtCode(appel("POST", antecedentsApi(patientA), nouveau(CENTRE_A, "Cree"), null)));
        anonyme.put("correction", statutEtCode(appel("PUT", antecedentApi(patientA, antecedentA),
                corps("centerId", CENTRE_A, "libelleLibre", "Corrige", "dateDebut", fixe(10)), null)));
        anonyme.put("resolution", statutEtCode(appel("PUT", antecedentApi(patientA, antecedentA) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", fixe(15)), null)));
        anonyme.put("suppression", statutEtCode(appel("DELETE", antecedentApi(patientA, antecedentA), null, null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
        assertThat(nombreDe("antecedents_medicaux", CENTRE_A)).isEqualTo(1);
    }

    @Test
    void un_refus_de_droit_n_ecrit_rien_et_precede_ou_suit_la_lecture_du_corps() throws Exception {
        deuxCentresEtUnAntecedent();
        String avant = empreinte("antecedents_medicaux");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("INFIRMIER : creation", statutEtCode(doPost(antecedentsApi(patientA), nouveau(CENTRE_A, "X"), as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : creation avec un corps illisible", statutEtCode(doPost(antecedentsApi(patientA), "{pas du json",
                as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : creation avec un corps vide {}", statutEtCode(doPost(antecedentsApi(patientA), "{}", as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : correction avec un corps absent", statutEtCode(doPut(antecedentApi(patientA, antecedentA), "",
                as(CENTRE_A, "INFIRMIER"))));
        cas.put("ADMIN : creation", statutEtCode(doPost(antecedentsApi(patientA), nouveau(CENTRE_A, "X"), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : correction", statutEtCode(doPut(antecedentApi(patientA, antecedentA),
                corps("centerId", CENTRE_A, "libelleLibre", "X", "dateDebut", fixe(10)), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : resolution", statutEtCode(doPut(antecedentApi(patientA, antecedentA) + "/resoudre",
                corps("centerId", CENTRE_A, "dateResolution", fixe(15)), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : suppression", statutEtCode(doDelete(antecedentApi(patientA, antecedentA), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : liste avec une pagination illisible", statutEtCode(doGet(antecedentsApi(patientA) + "?size=abc", as(CENTRE_A, "ADMIN"))));
        cas.put("INFIRMIER : liste avec une pagination illisible", statutEtCode(doGet(antecedentsApi(patientA) + "?size=abc",
                as(CENTRE_A, "INFIRMIER"))));
        cas.put("DIRECTION : creation", statutEtCode(doPost(antecedentsApi(patientA), nouveau(CENTRE_A, "X"), direction())));
        // KB-MED-58 corrigé : 403 AVANT 400 (un profil sans droit n'apprend plus rien, avant : 500) ; ADMIN (droit de lecture) : 400. Legacy archivé dans dm5/antecedents/_ecarts-legacy.
        arbre("refus_nouveau_code", cas);
        assertThat(empreinte("antecedents_medicaux")).isEqualTo(avant);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session_et_la_recherche_par_centre() throws Exception {
        deuxCentresEtUnAntecedent();
        UUID antecedentB = antecedentEnBase(CENTRE_B, patientB, "ANT_B", "MEDICAL", null, null, "Antecedent B", fixe(11), null, "ACTIF");
        String corrige = corps("centerId", CENTRE_B, "libelleLibre", "Corrige par A", "dateDebut", fixe(11));
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 liste avec centerId du centre B (session A)", statutEtCode(doGet(antecedentsApi(patientB) + "?centerId=" + CENTRE_B, medecinA())));
        cas.put("02 creation avec le centre B dans le corps (session A)", statutEtCode(doPost(antecedentsApi(patientB), nouveau(CENTRE_B, "B1"),
                medecinA())));
        cas.put("03 creation sans centre dans le corps (centre de la session)", statutEtCode(doPost(antecedentsApi(patientA),
                corps("type", "MEDICAL", "libelleLibre", "Sans centre", "dateDebut", fixe(20)), medecinA())));
        cas.put("04 correction avec le centre B (session A)", statutEtCode(doPut(antecedentApi(patientB, antecedentB), corrige, medecinA())));
        cas.put("05 resolution avec le centre B (session A)", statutEtCode(doPut(antecedentApi(patientB, antecedentB) + "/resoudre",
                corps("centerId", CENTRE_B, "dateResolution", fixe(15)), medecinA())));
        cas.put("06 suppression avec centerId B (session A)", statutEtCode(doDelete(antecedentApi(patientB, antecedentB) + "?centerId=" + CENTRE_B,
                medecinA())));
        cas.put("07 MEDECIN sans centre de session : liste", statutEtCode(doGet(antecedentsApi(patientA), as(null, "MEDECIN"))));
        cas.put("08 MEDECIN sans centre de session : creation", statutEtCode(doPost(antecedentsApi(patientA), nouveau(CENTRE_A, "Sans session"),
                as(null, "MEDECIN"))));
        cas.put("09 SUPERADMIN + MEDECIN lit le centre B depuis la session A", statutEtCode(doGet(antecedentsApi(patientB) + "?centerId=" + CENTRE_B,
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("10 SUPERADMIN + MEDECIN cree dans le centre B pour un patient du centre B", statutEtCode(doPost(antecedentsApi(patientB),
                nouveau(CENTRE_B, "Super B"), as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("11 SUPERADMIN + MEDECIN cree dans le centre B pour un patient du centre A", statutEtCode(doPost(antecedentsApi(patientA),
                nouveau(CENTRE_B, "Super B sur patient A"), as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("12 SUPERADMIN + MEDECIN corrige l'antecedent du centre B", statutEtCode(doPut(antecedentApi(patientB, antecedentB), corrige,
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("13 session B lit le patient du centre A (liste vide)", statutEtCode(doGet(antecedentsApi(patientA), as(CENTRE_B, "MEDECIN"))));
        cas.put("14 session B ecrit pour le patient du centre A", statutEtCode(doPost(antecedentsApi(patientA), nouveau(CENTRE_B, "B sur patient A"),
                as(CENTRE_B, "MEDECIN"))));
        cas.put("15 session B corrige l'antecedent du centre A", statutEtCode(doPut(antecedentApi(patientA, antecedentA),
                corps("centerId", CENTRE_B, "libelleLibre", "Corrige par B", "dateDebut", fixe(10)), as(CENTRE_B, "MEDECIN"))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAntecedents());
        // KB-MED-61 corrigé : la session B n'écrit plus pour un patient du centre A (404, avant : 200 et ligne du centre B). Legacy archivé dans dm5/antecedents/_ecarts-legacy.
        arbre("cloisonnement_nouveau_code", m);
    }

    @Test
    void les_ecritures_et_leurs_refus_sont_journalises_sous_PATIENTS_et_les_lectures_ne_le_sont_pas() throws Exception {
        deuxCentresEtUnAntecedent();
        monterLAudit();
        Resp c = doPost(antecedentsApi(patientA), nouveau(CENTRE_A, "Journal"), medecinA());
        UUID s = UUID.fromString(idDe(c));
        aliasNommes.put(s.toString(), "<ANT>");
        aliasNommes.put(idStable("antecedent", "inconnu").toString(), "<ANT_INCONNU>");
        doPost(antecedentsApi(patientA), corps("centerId", CENTRE_A, "type", "MEDICAL", "codeSystem", "CIM10", "code", "N18", "dateDebut", fixe(3)),
                medecinA());
        doPost(antecedentsApi(patientA), corps("centerId", CENTRE_A, "type", "MEDICAL", "codeSystem", "CIM10", "code", "N18", "dateDebut", fixe(4)),
                medecinA());
        doPut(antecedentApi(patientA, s), corps("centerId", CENTRE_A, "libelleLibre", "Corrige", "dateDebut", fixe(20)), medecinA());
        doPut(antecedentApi(patientA, idStable("antecedent", "inconnu")), corps("centerId", CENTRE_A, "libelleLibre", "X", "dateDebut", fixe(20)),
                medecinA());
        doPut(antecedentApi(patientA, s) + "/resoudre", corps("centerId", CENTRE_A, "dateResolution", fixe(25)), medecinA());
        doPut(antecedentApi(patientA, s) + "/resoudre", corps("centerId", CENTRE_A, "dateResolution", fixe(26)), medecinA());
        doGet(antecedentsApi(patientA), medecinA());
        doGet(antecedentsApi(patientA), as(CENTRE_A, "ADMIN"));
        doPost(antecedentsApi(patientA), nouveau(CENTRE_A, "Refuse"), as(CENTRE_A, "INFIRMIER"));
        doDelete(antecedentApi(patientA, s), as(CENTRE_A, "ADMIN"));
        doDelete(antecedentApi(patientA, s), medecinA());
        doDelete(antecedentApi(patientA, s), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        // KB-MED-60 partiel : un DELETE qui n'a rien supprimé (204) n'est plus audité. Legacy archivé dans dm5/antecedents/_ecarts-legacy.
        arbre("audit_nouveau_code", m);
    }

    @Test
    void les_antecedents_sont_exportes_en_condition_fhir_sans_diffusion_ni_notification() throws Exception {
        deuxCentresEtUnAntecedent();
        monterLAudit();
        diffusions.clear();
        Resp c = doPost(antecedentsApi(patientA), corps("centerId", CENTRE_A, "type", "COMORBIDITE", "codeSystem", "CIM10", "code", "N18",
                "codeDisplay", "Maladie fictive", "dateDebut", fixe(-60), "severite", "FORTE", "note", "Note fictive"), medecinA());
        aliasNommes.put(idDe(c), "<ANT_COMPLET>");
        UUID r = UUID.fromString(idDe(doPost(antecedentsApi(patientA), corps("centerId", CENTRE_A, "type", "MEDICAL", "libelleLibre", "Resolu libre",
                "dateDebut", fixe(-50)), medecinA())));
        aliasNommes.put(r.toString(), "<ANT_RESOLU>");
        doPut(antecedentApi(patientA, r) + "/resoudre", corps("centerId", CENTRE_A, "dateResolution", fixe(-40)), medecinA());
        antecedentEnBase(CENTRE_A, patientA, "ANT_INACTIF", "FAMILIAL", "LOCAL", "F1", null, fixe(-30), null, "INACTIF");
        antecedentEnBase(CENTRE_B, patientB, "ANT_AUTRE_CENTRE", "MEDICAL", null, null, "Autre centre", fixe(-20), null, "ACTIF");
        String export = PATIENTS_API + "/" + patientA + "/dossier-medical/export-fhir";
        Resp bundle = doGet(export, medecinA());
        List<JsonNode> conditions = new ArrayList<>();
        List<String> types = new ArrayList<>();
        corps(bundle).path("entry").forEach(e -> {
            types.add(e.path("resource").path("resourceType").asString());
            if ("Condition".equals(e.path("resource").path("resourceType").asString())) conditions.add(e.path("resource"));
        });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("export MEDECIN", bundle.status());
        m.put("types des ressources", types);
        m.put("conditions", conditions);
        m.put("export ADMIN", statutEtCode(doGet(export, as(CENTRE_A, "ADMIN"))));
        m.put("export INFIRMIER", statutEtCode(doGet(export, as(CENTRE_A, "INFIRMIER"))));
        m.put("diffusions", new ArrayList<>(diffusions));
        m.put("notifications", notificationsDurables());
        arbre("fhir", m);
    }
}
