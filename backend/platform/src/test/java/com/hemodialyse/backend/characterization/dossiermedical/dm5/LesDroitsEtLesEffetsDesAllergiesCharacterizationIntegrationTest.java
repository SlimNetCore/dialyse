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
 * DM5, lot 2 (allergies) : droits par profil, cloisonnement des centres, audit, export FHIR (AllergyIntolerance), diffusions et
 * notifications.
 */
class LesDroitsEtLesEffetsDesAllergiesCharacterizationIntegrationTest extends Dm5CharacterizationSupport {

    @Override
    protected String dossier() {
        return "allergies/droits";
    }

    private UUID patientA;
    private UUID patientB;
    private UUID allergieA;

    private void deuxCentresEtUneAllergie() {
        patientA = patientDm5(CENTRE_A, "PAT_A");
        patientB = patientDm5(CENTRE_B, "PAT_B");
        allergieA = allergieEnBase(CENTRE_A, patientA, "ALG_A", "LOCAL", "SUBSTANCE_A", "ALIMENT", "BASSE", "ALLERGIE", null, fixe(10), "SUSPECTEE");
    }

    private String nouvelle(UUID centre, String code) {
        return corps("centerId", centre, "codeSystem", "LOCAL", "code", code, "categorie", "ALIMENT", "criticite", "BASSE", "typeReaction", "ALLERGIE",
                "dateConstatation", fixe(20));
    }

    private String correction(UUID centre) {
        return corps("centerId", centre, "criticite", "BASSE", "statutVerification", "CONFIRMEE");
    }

    @Test
    void les_droits_par_profil_sur_les_six_routes() throws Exception {
        deuxCentresEtUneAllergie();
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm5().entrySet()) {
            nettoyerDm5();
            deuxCentresEtUneAllergie();
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("liste", statutEtCode(doGet(allergiesApi(patientA), p.getValue())));
            ligne.put("critiques", statutEtCode(doGet(allergiesApi(patientA) + "/critiques", p.getValue())));
            ligne.put("creation", statutEtCode(doPost(allergiesApi(patientA), nouvelle(CENTRE_A, "CREEE"), p.getValue())));
            ligne.put("correction", statutEtCode(doPut(allergieApi(patientA, allergieA), correction(CENTRE_A), p.getValue())));
            ligne.put("suppression", statutEtCode(doDelete(allergieApi(patientA, allergieA), p.getValue())));
            profils.put(p.getKey(), ligne);
        }
        nettoyerDm5();
        deuxCentresEtUneAllergie();
        Map<String, Object> anonyme = new LinkedHashMap<>();
        anonyme.put("liste", statutEtCode(doGetAnonymous(allergiesApi(patientA))));
        anonyme.put("critiques", statutEtCode(doGetAnonymous(allergiesApi(patientA) + "/critiques")));
        anonyme.put("creation", statutEtCode(appel("POST", allergiesApi(patientA), nouvelle(CENTRE_A, "CREEE"), null)));
        anonyme.put("correction", statutEtCode(appel("PUT", allergieApi(patientA, allergieA), correction(CENTRE_A), null)));
        anonyme.put("suppression", statutEtCode(appel("DELETE", allergieApi(patientA, allergieA), null, null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
        assertThat(nombreDe("allergies_patient", CENTRE_A)).isEqualTo(1);
    }

    @Test
    void un_refus_de_droit_n_ecrit_rien_et_precede_ou_suit_la_lecture_du_corps() throws Exception {
        deuxCentresEtUneAllergie();
        String avant = empreinte("allergies_patient");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("INFIRMIER : creation", statutEtCode(doPost(allergiesApi(patientA), nouvelle(CENTRE_A, "X"), as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : creation avec un corps illisible", statutEtCode(doPost(allergiesApi(patientA), "{pas du json", as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : creation avec un corps vide {}", statutEtCode(doPost(allergiesApi(patientA), "{}", as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : correction avec un corps absent", statutEtCode(doPut(allergieApi(patientA, allergieA), "", as(CENTRE_A, "INFIRMIER"))));
        cas.put("ADMIN : creation", statutEtCode(doPost(allergiesApi(patientA), nouvelle(CENTRE_A, "X"), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : correction", statutEtCode(doPut(allergieApi(patientA, allergieA), correction(CENTRE_A), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : suppression", statutEtCode(doDelete(allergieApi(patientA, allergieA), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : liste avec une pagination illisible", statutEtCode(doGet(allergiesApi(patientA) + "?size=abc", as(CENTRE_A, "ADMIN"))));
        cas.put("INFIRMIER : liste avec une pagination illisible", statutEtCode(doGet(allergiesApi(patientA) + "?size=abc", as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : critiques", statutEtCode(doGet(allergiesApi(patientA) + "/critiques", as(CENTRE_A, "INFIRMIER"))));
        cas.put("DIRECTION : creation", statutEtCode(doPost(allergiesApi(patientA), nouvelle(CENTRE_A, "X"), direction())));
        // KB-MED-58 corrigé : 403 AVANT 400 (un profil sans droit n'apprend plus rien, avant : 500) ; ADMIN (droit de lecture) : 400. Legacy archivé dans dm5/allergies/_ecarts-legacy.
        arbre("refus_nouveau_code", cas);
        assertThat(empreinte("allergies_patient")).isEqualTo(avant);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session_et_la_recherche_par_centre() throws Exception {
        deuxCentresEtUneAllergie();
        UUID allergieB = allergieEnBase(CENTRE_B, patientB, "ALG_B", "LOCAL", "SUBSTANCE_B", "ALIMENT", "HAUTE", "ALLERGIE", "Manifestation", fixe(11),
                "SUSPECTEE");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 liste avec centerId du centre B (session A)", statutEtCode(doGet(allergiesApi(patientB) + "?centerId=" + CENTRE_B, medecinA())));
        cas.put("02 critiques avec centerId du centre B (session A)", statutEtCode(doGet(allergiesApi(patientB) + "/critiques?centerId=" + CENTRE_B,
                medecinA())));
        cas.put("03 creation avec le centre B dans le corps (session A)", statutEtCode(doPost(allergiesApi(patientB), nouvelle(CENTRE_B, "B1"), medecinA())));
        cas.put("04 creation sans centre dans le corps (centre de la session)", statutEtCode(doPost(allergiesApi(patientA),
                corps("codeSystem", "LOCAL", "code", "SANS_CENTRE", "categorie", "ALIMENT", "criticite", "BASSE", "typeReaction", "ALLERGIE",
                        "dateConstatation", fixe(20)), medecinA())));
        cas.put("05 correction avec le centre B (session A)", statutEtCode(doPut(allergieApi(patientB, allergieB), correction(CENTRE_B), medecinA())));
        cas.put("06 suppression avec centerId B (session A)", statutEtCode(doDelete(allergieApi(patientB, allergieB) + "?centerId=" + CENTRE_B,
                medecinA())));
        cas.put("07 MEDECIN sans centre de session : liste", statutEtCode(doGet(allergiesApi(patientA), as(null, "MEDECIN"))));
        cas.put("08 MEDECIN sans centre de session : creation", statutEtCode(doPost(allergiesApi(patientA), nouvelle(CENTRE_A, "SANS_SESSION"),
                as(null, "MEDECIN"))));
        cas.put("09 SUPERADMIN + MEDECIN lit le centre B depuis la session A", statutEtCode(doGet(allergiesApi(patientB) + "?centerId=" + CENTRE_B,
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("10 SUPERADMIN + MEDECIN cree dans le centre B", statutEtCode(doPost(allergiesApi(patientB), nouvelle(CENTRE_B, "SUPER_B"),
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("11 session B lit le patient du centre A (liste vide)", statutEtCode(doGet(allergiesApi(patientA), as(CENTRE_B, "MEDECIN"))));
        cas.put("12 session B ecrit pour le patient du centre A", statutEtCode(doPost(allergiesApi(patientA), nouvelle(CENTRE_B, "B_SUR_A"),
                as(CENTRE_B, "MEDECIN"))));
        cas.put("13 session B corrige l'allergie du centre A", statutEtCode(doPut(allergieApi(patientA, allergieA), correction(CENTRE_B),
                as(CENTRE_B, "MEDECIN"))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAllergies());
        // KB-MED-61 corrigé : la session B n'écrit plus pour un patient du centre A (404, avant : 200 et ligne du centre B). Legacy archivé dans dm5/allergies/_ecarts-legacy.
        arbre("cloisonnement_nouveau_code", m);
    }

    @Test
    void les_ecritures_et_leurs_refus_sont_journalises_sous_PATIENTS_et_les_lectures_ne_le_sont_pas() throws Exception {
        deuxCentresEtUneAllergie();
        monterLAudit();
        Resp c = doPost(allergiesApi(patientA), nouvelle(CENTRE_A, "JOURNAL"), medecinA());
        UUID s = UUID.fromString(idDe(c));
        aliasNommes.put(s.toString(), "<ALG>");
        aliasNommes.put(idStable("allergie", "inconnue").toString(), "<ALG_INCONNUE>");
        doPost(allergiesApi(patientA), corps("centerId", CENTRE_A, "codeSystem", "LOCAL", "code", "SANS_MANIFESTATION", "categorie", "ALIMENT",
                "criticite", "HAUTE", "typeReaction", "ALLERGIE"), medecinA());
        doPut(allergieApi(patientA, s), correction(CENTRE_A), medecinA());
        doPut(allergieApi(patientA, idStable("allergie", "inconnue")), correction(CENTRE_A), medecinA());
        doGet(allergiesApi(patientA), medecinA());
        doGet(allergiesApi(patientA) + "/critiques", as(CENTRE_A, "ADMIN"));
        doPost(allergiesApi(patientA), nouvelle(CENTRE_A, "REFUSEE"), as(CENTRE_A, "INFIRMIER"));
        doDelete(allergieApi(patientA, s), as(CENTRE_A, "ADMIN"));
        doDelete(allergieApi(patientA, s), medecinA());
        doDelete(allergieApi(patientA, s), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        // KB-MED-60 partiel : un DELETE qui n'a rien supprimé (204) n'est plus audité. Legacy archivé dans dm5/allergies/_ecarts-legacy.
        arbre("audit_nouveau_code", m);
    }

    @Test
    void les_allergies_sont_exportees_en_allergy_intolerance_fhir_sans_diffusion_ni_notification() throws Exception {
        deuxCentresEtUneAllergie();
        monterLAudit();
        diffusions.clear();
        Resp c = doPost(allergiesApi(patientA), corps("centerId", CENTRE_A, "codeSystem", "ATC", "code", "J01CA04", "codeDisplay", "Substance fictive",
                "categorie", "MEDICAMENT", "criticite", "HAUTE", "typeReaction", "ALLERGIE", "manifestations", "Reaction fictive",
                "dateConstatation", fixe(-60), "statutVerification", "CONFIRMEE"), medecinA());
        aliasNommes.put(idDe(c), "<ALG_COMPLETE>");
        allergieEnBase(CENTRE_A, patientA, "ALG_REFUTEE", "LOCAL", "arachide", "ALIMENT", "BASSE", "INTOLERANCE", null, fixe(-10), "REFUTEE");
        allergieEnBase(CENTRE_B, patientB, "ALG_AUTRE_CENTRE", "LOCAL", "AUTRE", "ALIMENT", "BASSE", "ALLERGIE", null, fixe(-20), "SUSPECTEE");
        String export = PATIENTS_API + "/" + patientA + "/dossier-medical/export-fhir";
        Resp bundle = doGet(export, medecinA());
        List<JsonNode> allergies = new ArrayList<>();
        List<String> types = new ArrayList<>();
        corps(bundle).path("entry").forEach(e -> {
            types.add(e.path("resource").path("resourceType").asString());
            if ("AllergyIntolerance".equals(e.path("resource").path("resourceType").asString())) allergies.add(e.path("resource"));
        });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("export MEDECIN", bundle.status());
        m.put("types des ressources", types);
        m.put("allergies", allergies);
        m.put("diffusions", new ArrayList<>(diffusions));
        m.put("notifications", notificationsDurables());
        arbre("fhir", m);
    }
}
