package com.hemodialyse.backend.characterization.dossiermedical.dm1;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DM1, revue du 2026-11-06 (KB-MED-02) : scénarios NEUFS de garde de centre sur les routes de séance et de statistiques qui
 * rendaient ou écrivaient des données de santé d'un autre centre. Pour chaque route : la session du centre A demandant ou ciblant
 * le centre B reçoit 403 {@code ACCESS_DENIED} ; la même route pour le bon centre n'est PAS refusée (comportement inchangé).
 * Données fictives uniquement.
 */
class GardeDeCentreDesRoutesDeMesuresCharacterizationIntegrationTest extends ConstantesCharacterizationSupport {

    @Override
    protected String dossier() {
        return "garde";
    }

    private RequestPostProcessor adminDuCentreA() {
        return as(CENTRE_A, "ADMIN");
    }

    private RequestPostProcessor infirmierDuCentreA() {
        return as(CENTRE_A, "INFIRMIER");
    }

    private void paire(Map<String, Object> cas, String route, Resp autreCentre, Resp bonCentre) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centre B demandé par la session A", statutEtCode(autreCentre));
        m.put("centre A demandé par la session A", bonCentre.status() == 403 ? "REFUSE A TORT " + statutEtCode(bonCentre) : "non refusé");
        cas.put(route, m);
    }

    @Test
    void les_lectures_de_seances_sont_gardees() throws Exception {
        UUID patientA = patientDm1(CENTRE_A, "PAT_A");
        UUID sa = seanceDm1(CENTRE_A, "SEANCE_A", patientA, jour(-3), "VALIDEE", 1);
        voletDm1(CENTRE_A, "VOLET_A", sa, "70.50", "68.00", "130/80", "120/70", 300, "2000.00", 240);
        UUID patientB = patientDm1(CENTRE_B, "PAT_B");
        UUID sb = seanceDm1(CENTRE_B, "SEANCE_B", patientB, jour(-3), "VALIDEE", 1);
        voletDm1(CENTRE_B, "VOLET_B", sb, "80.00", "78.00", "140/90", "130/80", 250, "1500.00", 180);
        Map<String, Object> cas = new LinkedHashMap<>();
        paire(cas, "GET /seances (liste)",
                doGet(SEANCES_API + "?centerId=" + CENTRE_B, infirmierDuCentreA()),
                doGet(SEANCES_API + "?centerId=" + CENTRE_A, infirmierDuCentreA()));
        paire(cas, "GET /seances/journal",
                doGet(SEANCES_API + "/journal?centerId=" + CENTRE_B + "&dateSeance=" + jour(-3), infirmierDuCentreA()),
                doGet(SEANCES_API + "/journal?centerId=" + CENTRE_A + "&dateSeance=" + jour(-3), infirmierDuCentreA()));
        paire(cas, "GET /seances/calendar",
                doGet(SEANCES_API + "/calendar?centerId=" + CENTRE_B + "&year=2026&month=6", infirmierDuCentreA()),
                doGet(SEANCES_API + "/calendar?centerId=" + CENTRE_A + "&year=2026&month=6", infirmierDuCentreA()));
        arbre("routes_de_seance", cas);
    }

    @Test
    void les_ecritures_de_seance_sont_gardees_et_n_ecrivent_rien_hors_centre() throws Exception {
        UUID patientB = patientDm1(CENTRE_B, "PAT_B");
        UUID sb = seanceDm1(CENTRE_B, "SEANCE_B", patientB, jour(-3), "CREE", 1);
        UUID patientA = patientDm1(CENTRE_A, "PAT_A");
        UUID sa = seanceDm1(CENTRE_A, "SEANCE_A", patientA, jour(-3), "CREE", 1);
        int avant = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ?", Integer.class, CENTRE_B);
        Map<String, Object> cas = new LinkedHashMap<>();
        paire(cas, "POST /seances",
                doPost(SEANCES_API, "{\"centerId\":\"" + CENTRE_B + "\",\"patientId\":\"" + patientB + "\",\"dateSeance\":\"" + jour(-1) + "\"}",
                        infirmierDuCentreA()),
                doPost(SEANCES_API, "{\"centerId\":\"" + CENTRE_A + "\",\"patientId\":\"" + patientA + "\",\"dateSeance\":\"" + jour(-1) + "\"}",
                        infirmierDuCentreA()));
        paire(cas, "POST /seances/scan",
                doPost(SEANCES_API + "/scan", "{\"centerId\":\"" + CENTRE_B + "\",\"qrCode\":\"inconnu\"}", infirmierDuCentreA()),
                doPost(SEANCES_API + "/scan", "{\"centerId\":\"" + CENTRE_A + "\",\"qrCode\":\"inconnu\"}", infirmierDuCentreA()));
        paire(cas, "PUT /seances/{id}",
                doPut(SEANCES_API + "/" + sb, "{\"centerId\":\"" + CENTRE_B + "\",\"dateSeance\":\"" + jour(-2) + "\"}", adminDuCentreA()),
                doPut(SEANCES_API + "/" + sa, "{\"centerId\":\"" + CENTRE_A + "\",\"dateSeance\":\"" + jour(-2) + "\"}", adminDuCentreA()));
        arbre("ecritures_de_seance", cas);
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ?", Integer.class, CENTRE_B))
                .as("aucune séance créée dans le centre B par la session A").isEqualTo(avant);
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject("SELECT date_seance FROM seances WHERE id = ?", java.sql.Date.class, sb).toLocalDate())
                .as("date de la séance du centre B inchangée").isEqualTo(jour(-3));
    }

    @Test
    void les_statistiques_medicales_et_les_exports_sont_gardes() throws Exception {
        UUID patientA = patientDm1(CENTRE_A, "PAT_A");
        UUID sa = seanceDm1(CENTRE_A, "SEANCE_A", patientA, jour(-3), "VALIDEE", 1);
        voletDm1(CENTRE_A, "VOLET_A", sa, "70.50", "68.00", "130/80", "120/70", 300, "2000.00", 240);
        UUID patientB = patientDm1(CENTRE_B, "PAT_B");
        UUID sb = seanceDm1(CENTRE_B, "SEANCE_B", patientB, jour(-3), "VALIDEE", 1);
        voletDm1(CENTRE_B, "VOLET_B", sb, "80.00", "78.00", "140/90", "130/80", 250, "1500.00", 180);
        Map<String, Object> cas = new LinkedHashMap<>();
        paire(cas, "GET stats/paramedical",
                doGet(PATIENTS_API + "/" + patientB + "/stats/paramedical?centerId=" + CENTRE_B, infirmierDuCentreA()),
                doGet(PATIENTS_API + "/" + patientA + "/stats/paramedical?centerId=" + CENTRE_A, infirmierDuCentreA()));
        paire(cas, "GET stats/medical",
                doGet(PATIENTS_API + "/" + patientB + "/stats/medical?centerId=" + CENTRE_B, infirmierDuCentreA()),
                doGet(PATIENTS_API + "/" + patientA + "/stats/medical?centerId=" + CENTRE_A, infirmierDuCentreA()));
        paire(cas, "GET stats/export (CSV)",
                doGet(PATIENTS_API + "/" + patientB + "/stats/export?format=csv&centerId=" + CENTRE_B, adminDuCentreA()),
                doGet(PATIENTS_API + "/" + patientA + "/stats/export?format=csv&centerId=" + CENTRE_A, adminDuCentreA()));
        paire(cas, "GET stats/export (PDF)",
                doGet(PATIENTS_API + "/" + patientB + "/stats/export?format=pdf&centerId=" + CENTRE_B, adminDuCentreA()),
                doGet(PATIENTS_API + "/" + patientA + "/stats/export?format=pdf&centerId=" + CENTRE_A, adminDuCentreA()));
        paire(cas, "POST stats/paramedical/search",
                doPost(PATIENTS_API + "/stats/paramedical/search", recherche(CENTRE_B, patientB, null), infirmierDuCentreA()),
                doPost(PATIENTS_API + "/stats/paramedical/search", recherche(CENTRE_A, patientA, null), infirmierDuCentreA()));
        paire(cas, "POST stats/medical/search",
                doPost(PATIENTS_API + "/stats/medical/search", recherche(CENTRE_B, patientB, null), infirmierDuCentreA()),
                doPost(PATIENTS_API + "/stats/medical/search", recherche(CENTRE_A, patientA, null), infirmierDuCentreA()));
        paire(cas, "POST stats/export/search (CSV)",
                doPost(PATIENTS_API + "/stats/export/search", recherche(CENTRE_B, patientB, "csv"), adminDuCentreA()),
                doPost(PATIENTS_API + "/stats/export/search", recherche(CENTRE_A, patientA, "csv"), adminDuCentreA()));
        arbre("statistiques_et_exports", cas);
    }

    private Resp supprime(String chemin, RequestPostProcessor qui) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(java.net.URI.create(chemin)).with(qui));
    }

    @Test
    void les_autres_actions_de_seance_sont_gardees_et_n_ecrivent_rien_hors_centre() throws Exception {
        UUID patientB = patientDm1(CENTRE_B, "PAT_B");
        UUID sb = seanceDm1(CENTRE_B, "SEANCE_B", patientB, jour(-3), "VALIDEE", 1);
        voletDm1(CENTRE_B, "VOLET_B", sb, "80.00", "78.00", "140/90", "130/80", 250, "1500.00", 180);
        UUID patientA = patientDm1(CENTRE_A, "PAT_A");
        UUID sa = seanceDm1(CENTRE_A, "SEANCE_A", patientA, jour(-3), "VALIDEE", 1);
        UUID autre = UUID.nameUUIDFromBytes("article".getBytes());
        String statutAvant = jdbc.queryForObject("SELECT statut FROM seances WHERE id = ?", String.class, sb);
        Map<String, Object> cas = new LinkedHashMap<>();
        try {
            String forfaitB = "{\"centerId\":\"" + CENTRE_B + "\",\"forfaitId\":\"" + autre + "\",\"userId\":\"u\"}";
            String forfaitA = "{\"centerId\":\"" + CENTRE_A + "\",\"forfaitId\":\"" + autre + "\",\"userId\":\"u\"}";
            paire(cas, "PUT /seances/{id}/forfait",
                    doPut(SEANCES_API + "/" + sb + "/forfait", forfaitB, adminDuCentreA()),
                    doPut(SEANCES_API + "/" + sa + "/forfait", forfaitA, adminDuCentreA()));
            paire(cas, "POST /seances/{id}/valider",
                    doPost(SEANCES_API + "/" + sb + "/valider", "{\"centerId\":\"" + CENTRE_B + "\",\"userId\":\"u\"}", infirmierDuCentreA()),
                    doPost(SEANCES_API + "/" + sa + "/valider", "{\"centerId\":\"" + CENTRE_A + "\",\"userId\":\"u\"}", infirmierDuCentreA()));
            paire(cas, "POST /seances/{id}/deverrouiller-regularisation",
                    doPost(SEANCES_API + "/" + sb + "/deverrouiller-regularisation?centerId=" + CENTRE_B, "", adminDuCentreA()),
                    doPost(SEANCES_API + "/" + sa + "/deverrouiller-regularisation?centerId=" + CENTRE_A, "", adminDuCentreA()));
            paire(cas, "POST /seances/{id}/signer-medecin",
                    doPost(SEANCES_API + "/" + sb + "/signer-medecin", "{\"centerId\":\"" + CENTRE_B + "\",\"userId\":\"u\"}", as(CENTRE_A, "MEDECIN")),
                    doPost(SEANCES_API + "/" + sa + "/signer-medecin", "{\"centerId\":\"" + CENTRE_A + "\",\"userId\":\"u\"}", as(CENTRE_A, "MEDECIN")));
            paire(cas, "POST /seances/{id}/consommables",
                    doPost(SEANCES_API + "/" + sb + "/consommables", "{\"centerId\":\"" + CENTRE_B + "\",\"articleId\":\"" + autre + "\",\"quantite\":1}", infirmierDuCentreA()),
                    doPost(SEANCES_API + "/" + sa + "/consommables", "{\"centerId\":\"" + CENTRE_A + "\",\"articleId\":\"" + autre + "\",\"quantite\":1}", infirmierDuCentreA()));
            paire(cas, "PUT /seances/{id}/consommables/{articleId}",
                    doPut(SEANCES_API + "/" + sb + "/consommables/" + autre, "{\"centerId\":\"" + CENTRE_B + "\",\"userId\":\"u\",\"quantite\":2}", infirmierDuCentreA()),
                    doPut(SEANCES_API + "/" + sa + "/consommables/" + autre, "{\"centerId\":\"" + CENTRE_A + "\",\"userId\":\"u\",\"quantite\":2}", infirmierDuCentreA()));
            paire(cas, "DELETE /seances/{id}/consommables/{articleId}",
                    supprime(SEANCES_API + "/" + sb + "/consommables/" + autre + "?centerId=" + CENTRE_B, infirmierDuCentreA()),
                    supprime(SEANCES_API + "/" + sa + "/consommables/" + autre + "?centerId=" + CENTRE_A, infirmierDuCentreA()));
            paire(cas, "POST /seances/calendar/holiday",
                    doPost(SEANCES_API + "/calendar/holiday", "{\"centerId\":\"" + CENTRE_B + "\",\"dayDate\":\"2030-01-15\",\"labelOrReason\":\"x\"}", adminDuCentreA()),
                    doPost(SEANCES_API + "/calendar/holiday", "{\"centerId\":\"" + CENTRE_A + "\",\"dayDate\":\"2030-01-15\",\"labelOrReason\":\"x\"}", adminDuCentreA()));
            paire(cas, "DELETE /seances/calendar/holiday/{id}",
                    supprime(SEANCES_API + "/calendar/holiday/" + autre + "?centerId=" + CENTRE_B, adminDuCentreA()),
                    supprime(SEANCES_API + "/calendar/holiday/" + autre + "?centerId=" + CENTRE_A, adminDuCentreA()));
            paire(cas, "POST /seances/calendar/closure",
                    doPost(SEANCES_API + "/calendar/closure", "{\"centerId\":\"" + CENTRE_B + "\",\"dayDate\":\"2030-01-16\",\"labelOrReason\":\"x\"}", adminDuCentreA()),
                    doPost(SEANCES_API + "/calendar/closure", "{\"centerId\":\"" + CENTRE_A + "\",\"dayDate\":\"2030-01-16\",\"labelOrReason\":\"x\"}", adminDuCentreA()));
            paire(cas, "DELETE /seances/calendar/closure/{id}",
                    supprime(SEANCES_API + "/calendar/closure/" + autre + "?centerId=" + CENTRE_B, adminDuCentreA()),
                    supprime(SEANCES_API + "/calendar/closure/" + autre + "?centerId=" + CENTRE_A, adminDuCentreA()));
            paire(cas, "PUT /seances/{id}/volet-medical",
                    doPut(SEANCES_API + "/" + sb + "/volet-medical", "{\"centerId\":\"" + CENTRE_B + "\",\"prescription\":\"x\"}", as(CENTRE_A, "MEDECIN")),
                    doPut(SEANCES_API + "/" + sa + "/volet-medical", "{\"centerId\":\"" + CENTRE_A + "\",\"prescription\":\"x\"}", as(CENTRE_A, "MEDECIN")));
            arbre("autres_actions_de_seance", cas);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT statut FROM seances WHERE id = ?", String.class, sb))
                    .as("statut de la séance du centre B inchangé").isEqualTo(statutAvant);
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM center_holiday WHERE center_id = ?", Integer.class, CENTRE_B))
                    .as("aucun jour férié écrit dans le centre B").isZero();
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM center_closure_day WHERE center_id = ?", Integer.class, CENTRE_B))
                    .as("aucune fermeture écrite dans le centre B").isZero();
        } finally {
            for (UUID centre : java.util.List.of(CENTRE_A, CENTRE_B)) {
                jdbc.update("DELETE FROM center_holiday WHERE center_id = ?", centre);
                jdbc.update("DELETE FROM center_closure_day WHERE center_id = ?", centre);
            }
        }
    }

    private String recherche(UUID centre, UUID patient, String format) {
        return "{\"centerId\":\"" + centre + "\",\"patientId\":\"" + patient + "\""
                + (format == null ? "" : ",\"format\":\"" + format + "\"") + "}";
    }
}
