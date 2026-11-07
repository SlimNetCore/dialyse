package com.hemodialyse.backend.characterization.dossiermedical.dm6;

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
 * DM6, lot 1 (observations) : droits par profil, cloisonnement des centres et des patients, audit, export FHIR (Observation),
 * diffusions et notifications, lien avec la demande d'examen.
 */
class LesDroitsEtLesEffetsDesObservationsBiologiquesCharacterizationIntegrationTest extends Dm6CharacterizationSupport {

    @Override
    protected String dossier() {
        return "observations/droits";
    }

    private UUID patientA;
    private UUID patientB;
    private UUID observationA;
    private UUID demandeA;

    private void deuxCentresEtUneObservation() {
        patientA = patientDm6(CENTRE_A, "PAT_A");
        patientB = patientDm6(CENTRE_B, "PAT_B");
        demandeA = demandeEnBase(CENTRE_A, patientA, "DEM_A", "PRELEVE");
        observationA = observationSimple(CENTRE_A, patientA, "OBS_A", fixe(10));
    }

    private String nouvelle(UUID centre, int valeur) {
        return corps("centerId", centre, "codeSystem", "LOINC", "code", "718-7", "valeurNum", valeur, "unite", "g/dL", "datePrelevement", fixe(20));
    }

    private String correction(UUID centre) {
        return corps("centerId", centre, "valeurNum", 14, "unite", "g/dL");
    }

    @Test
    void les_droits_par_profil_sur_les_six_routes() throws Exception {
        deuxCentresEtUneObservation();
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm6().entrySet()) {
            nettoyerDm6();
            deuxCentresEtUneObservation();
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("liste", statutEtCode(doGet(observationsApi(patientA), p.getValue())));
            ligne.put("par demande", statutEtCode(doGet(observationsDeLaDemandeApi(patientA, demandeA), p.getValue())));
            ligne.put("creation", statutEtCode(doPost(observationsApi(patientA), nouvelle(CENTRE_A, 1), p.getValue())));
            ligne.put("correction", statutEtCode(doPut(observationApi(patientA, observationA), correction(CENTRE_A), p.getValue())));
            ligne.put("suppression", statutEtCode(doDelete(observationApi(patientA, observationA), p.getValue())));
            profils.put(p.getKey(), ligne);
        }
        nettoyerDm6();
        deuxCentresEtUneObservation();
        Map<String, Object> anonyme = new LinkedHashMap<>();
        anonyme.put("liste", statutEtCode(doGetAnonymous(observationsApi(patientA))));
        anonyme.put("par demande", statutEtCode(doGetAnonymous(observationsDeLaDemandeApi(patientA, demandeA))));
        anonyme.put("creation", statutEtCode(appel("POST", observationsApi(patientA), nouvelle(CENTRE_A, 1), null)));
        anonyme.put("correction", statutEtCode(appel("PUT", observationApi(patientA, observationA), correction(CENTRE_A), null)));
        anonyme.put("suppression", statutEtCode(appel("DELETE", observationApi(patientA, observationA), null, null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
        assertThat(nombreDe("observations_biologiques", CENTRE_A)).isEqualTo(1);
    }

    @Test
    void un_refus_de_droit_n_ecrit_rien_et_precede_la_lecture_du_corps() throws Exception {
        deuxCentresEtUneObservation();
        String avant = empreinte("observations_biologiques");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("INFIRMIER : creation", statutEtCode(doPost(observationsApi(patientA), nouvelle(CENTRE_A, 2), as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : creation avec un corps illisible", statutEtCode(doPost(observationsApi(patientA), "{pas du json", as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : correction avec un corps absent", statutEtCode(doPut(observationApi(patientA, observationA), "", as(CENTRE_A, "INFIRMIER"))));
        cas.put("ADMIN : creation", statutEtCode(doPost(observationsApi(patientA), nouvelle(CENTRE_A, 3), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : creation avec un corps illisible", statutEtCode(doPost(observationsApi(patientA), "{pas du json", as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : correction", statutEtCode(doPut(observationApi(patientA, observationA), correction(CENTRE_A), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : suppression", statutEtCode(doDelete(observationApi(patientA, observationA), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : liste avec une pagination illisible", statutEtCode(doGet(observationsApi(patientA) + "?size=abc", as(CENTRE_A, "ADMIN"))));
        cas.put("INFIRMIER : liste avec une pagination illisible", statutEtCode(doGet(observationsApi(patientA) + "?size=abc", as(CENTRE_A, "INFIRMIER"))));
        cas.put("DIRECTION : creation", statutEtCode(doPost(observationsApi(patientA), nouvelle(CENTRE_A, 4), direction())));
        arbre("refus", cas);
        assertThat(empreinte("observations_biologiques")).isEqualTo(avant);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session_et_aucune_garde_de_patient() throws Exception {
        deuxCentresEtUneObservation();
        UUID observationB = observationSimple(CENTRE_B, patientB, "OBS_B", fixe(11));
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 liste avec centerId du centre B (session A)", statutEtCode(doGet(observationsApi(patientB) + "?centerId=" + CENTRE_B, medecinA())));
        cas.put("02 par demande avec centerId du centre B (session A)", statutEtCode(doGet(observationsDeLaDemandeApi(patientB, demandeA) + "?centerId="
                + CENTRE_B, medecinA())));
        cas.put("03 creation avec le centre B dans le corps (session A)", statutEtCode(doPost(observationsApi(patientB), nouvelle(CENTRE_B, 5), medecinA())));
        cas.put("04 creation sans centre dans le corps (centre de la session)", statutEtCode(doPost(observationsApi(patientA),
                corps("codeSystem", "LOINC", "code", "718-7", "valeurNum", 6, "unite", "u", "datePrelevement", fixe(20)), medecinA())));
        cas.put("05 correction avec le centre B (session A)", statutEtCode(doPut(observationApi(patientB, observationB), correction(CENTRE_B), medecinA())));
        cas.put("06 suppression avec centerId B (session A)", statutEtCode(doDelete(observationApi(patientB, observationB) + "?centerId=" + CENTRE_B,
                medecinA())));
        cas.put("07 MEDECIN sans centre de session : liste", statutEtCode(doGet(observationsApi(patientA), as(null, "MEDECIN"))));
        cas.put("08 MEDECIN sans centre de session : creation", statutEtCode(doPost(observationsApi(patientA), nouvelle(CENTRE_A, 7), as(null, "MEDECIN"))));
        cas.put("09 SUPERADMIN + MEDECIN lit le centre B depuis la session A", statutEtCode(doGet(observationsApi(patientB) + "?centerId=" + CENTRE_B,
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("10 SUPERADMIN + MEDECIN cree dans le centre B", statutEtCode(doPost(observationsApi(patientB), nouvelle(CENTRE_B, 8),
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("11 session B lit le patient du centre A (liste vide)", statutEtCode(doGet(observationsApi(patientA), as(CENTRE_B, "MEDECIN"))));
        cas.put("12 session B ecrit pour le patient du centre A", statutEtCode(doPost(observationsApi(patientA), nouvelle(CENTRE_B, 9),
                as(CENTRE_B, "MEDECIN"))));
        cas.put("13 session B corrige l'observation du centre A", statutEtCode(doPut(observationApi(patientA, observationA), correction(CENTRE_B),
                as(CENTRE_B, "MEDECIN"))));
        cas.put("14 session A ecrit pour un patient inconnu (aucune garde de patient)", statutEtCode(doPost(observationsApi(idStable("patient", "inconnu")),
                nouvelle(CENTRE_A, 10), medecinA())));
        cas.put("15 session A ecrit pour le patient du centre B (aucune garde de patient)", statutEtCode(doPost(observationsApi(patientB),
                nouvelle(CENTRE_A, 11), medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesObservations());
        arbre("cloisonnement", m);
    }

    @Test
    void les_ecritures_et_leurs_refus_sont_journalises_sous_PATIENTS_et_les_lectures_ne_le_sont_pas() throws Exception {
        deuxCentresEtUneObservation();
        monterLAudit();
        Resp c = doPost(observationsApi(patientA), nouvelle(CENTRE_A, 12), medecinA());
        UUID s = UUID.fromString(idDe(c));
        aliasNommes.put(s.toString(), "<OBS>");
        aliasNommes.put(idStable("observation", "inconnue").toString(), "<OBS_INCONNUE>");
        doPost(observationsApi(patientA), corps("centerId", CENTRE_A, "codeSystem", "CIM10", "code", "N18.5", "valeurNum", 1, "unite", "u",
                "datePrelevement", fixe(20)), medecinA());
        doPut(observationApi(patientA, s), correction(CENTRE_A), medecinA());
        doPut(observationApi(patientA, idStable("observation", "inconnue")), correction(CENTRE_A), medecinA());
        doGet(observationsApi(patientA), medecinA());
        doGet(observationsDeLaDemandeApi(patientA, demandeA), as(CENTRE_A, "ADMIN"));
        doPost(observationsApi(patientA), nouvelle(CENTRE_A, 13), as(CENTRE_A, "INFIRMIER"));
        doDelete(observationApi(patientA, s), as(CENTRE_A, "ADMIN"));
        doDelete(observationApi(patientA, s), medecinA());
        doDelete(observationApi(patientA, s), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }

    @Test
    void les_observations_sont_exportees_en_fhir_sans_diffusion_ni_notification_et_le_statut_de_la_demande_ne_bouge_pas() throws Exception {
        deuxCentresEtUneObservation();
        monterLAudit();
        diffusions.clear();
        Resp c = doPost(observationsApi(patientA), corps("centerId", CENTRE_A, "demandeExamenId", demandeA, "codeSystem", "LOINC", "code", "2160-0",
                "codeDisplay", "Creatinine fictive", "valeurNum", 7.25, "unite", "mg/dL", "datePrelevement", fixe(-60), "statut", "PRELIMINAIRE"),
                medecinA());
        aliasNommes.put(idDe(c), "<OBS_COMPLETE>");
        observationEnBase(CENTRE_A, patientA, "OBS_TEXTE", demandeA, "5195-3", "Antigene fictif", null, null, "Negatif", fixe(-10), "CORRIGE", "IMPORT");
        observationEnBase(CENTRE_A, patientA, "OBS_DERIVEE", null, "718-7", "Hb derivee", "9", "g/dL", null, fixe(-20), "FINAL", "DERIVEE_BILAN");
        observationEnBase(CENTRE_B, patientB, "OBS_AUTRE_CENTRE", null, "718-7", "Hb", "98", "g/dL", null, fixe(-30), "FINAL", "SAISIE_DIRECTE");
        String export = PATIENTS_API + "/" + patientA + "/dossier-medical/export-fhir";
        Resp bundle = doGet(export, medecinA());
        List<JsonNode> observations = new ArrayList<>();
        List<String> types = new ArrayList<>();
        corps(bundle).path("entry").forEach(e -> {
            types.add(e.path("resource").path("resourceType").asString());
            if ("Observation".equals(e.path("resource").path("resourceType").asString())) observations.add(e.path("resource"));
        });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("export MEDECIN", bundle.status());
        m.put("types des ressources", types);
        m.put("observations", observations);
        m.put("diffusions", new ArrayList<>(diffusions));
        m.put("notifications", notificationsDurables());
        m.put("statut de la demande apres les saisies (KB-MED-37)", statutDeLaDemande(demandeA));
        arbre("fhir", m);
    }
}
