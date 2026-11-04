package com.hemodialyse.backend.characterization.patientcouverture.pc4;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * PC4 : droits d'accès des quatre routes d'attestation (onze profils, rôles réels hors des onze, rôle personnalisé, anonyme) et
 * garde de centre (le centre de la requête est-il comparé à celui de la session ?).
 */
class AutorisationsEtCentresDesAttestationsCharacterizationIntegrationTest extends AttestationCharacterizationSupport {

    @Override
    protected String dossier() {
        return "autorisations";
    }

    private UUID patient;
    private UUID attestation;

    private void decorDeDroits() {
        jdbc.update("DELETE FROM attestation_droit WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        viderLesCaches();
        if (patient == null) patient = patientCouvert(CENTRE_A, "DROITS", false, "ENFANT");
        attestation = attestationEnBase(CENTRE_A, "ATT_1", patient, jour(-5), jour(5));
    }

    private Resp sans(java.util.concurrent.Callable<Resp> c) {
        try {
            return c.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void matrice(String name, Function<RequestPostProcessor, Resp> appel) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profils().entrySet()) {
            decorDeDroits();
            m.put(p.getKey(), statutEtCode(appel.apply(p.getValue())) + " (" + nombreDAttestations() + " ligne(s))");
        }
        for (String role : new String[]{"PHARMACIEN", "DIRECTEUR_ADMINISTRATIF", "CHEF_DE_SERVICE"}) {
            decorDeDroits();
            m.put(role + (role.equals("CHEF_DE_SERVICE") ? " (rôle personnalisé)" : ""), statutEtCode(appel.apply(as(CENTRE_A, role)))
                    + " (" + nombreDAttestations() + " ligne(s))");
        }
        arbre(name, m);
    }

    @Test
    void les_lectures_d_attestation_sont_ouvertes_a_tout_compte_de_centre() throws Exception {
        matrice("matrice_liste_du_patient_nouveau_code",p -> sans(() -> attestationsDuPatient(patient, CENTRE_A, p)));
        matrice("matrice_recherche_nouveau_code",p -> sans(() -> rechercher("{'centerId':'" + CENTRE_A + "','page':0,'size':10}", p)));
        decorDeDroits();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("liste du patient", statutEtCode(anonyme("GET", ATTESTATIONS + "/" + patient + "?centerId=" + CENTRE_A, null)));
        m.put("recherche", statutEtCode(anonyme("POST", ATTESTATIONS + "-center/search", "{\"centerId\":\"" + CENTRE_A + "\",\"page\":0,\"size\":10}")));
        arbre("anonyme_lectures", m);
    }

    @Test
    void la_creation_et_la_suppression_d_attestation_sont_ouvertes_a_tout_compte_de_centre() throws Exception {
        matrice("matrice_creation_nouveau_code",p -> sans(() -> creerAttestation(patient, CENTRE_A, jour(10), jour(20), p)));
        matrice("matrice_suppression_nouveau_code",p -> sans(() -> supprimerAttestation(attestation, CENTRE_A, patient, p)));
        decorDeDroits();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("création", statutEtCode(anonyme("POST", ATTESTATIONS, "{\"patientId\":\"" + patient + "\",\"centerId\":\"" + CENTRE_A
                + "\",\"dateDebut\":\"" + jour(10) + "\",\"dateFin\":\"" + jour(20) + "\"}")));
        m.put("suppression", statutEtCode(anonyme("DELETE", ATTESTATIONS + "/" + attestation + "?centerId=" + CENTRE_A, null)));
        arbre("anonyme_ecritures", m);
        etatDesAttestations("etat_apres_les_refus_anonymes");
    }

    @Test
    void le_squelette_d_attestation_du_module_n_est_pas_servi_et_les_routes_voisines_ne_sont_pas_celles_des_attestations() throws Exception {
        decorDeDroits();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("POST /api/v1/patient-couverture/attestations", refus(doPost("/api/v1/patient-couverture/attestations", json("{'patientId':'" + patient
                + "','centreId':'" + CENTRE_A + "','dateDebut':'" + jour(1) + "','dateFin':'" + jour(2) + "'}"), adminA())));
        m.put("GET /api/v1/patient-couverture/attestations", refus(doGet("/api/v1/patient-couverture/attestations?centreId=" + CENTRE_A, adminA())));
        m.put("GET /api/v1/pec/attestations/{id} (consultation d'une attestation par son identifiant)", refus(doGet(ATTESTATIONS + "/" + attestation
                + "?centerId=" + CENTRE_A, adminA())));
        m.put("PUT /api/v1/pec/attestations/{id} (modification)", refus(doPut(ATTESTATIONS + "/" + attestation + "?centerId=" + CENTRE_A, "{}", adminA())));
        m.put("GET /api/v1/pec/attestations-center/search", refus(doGet(ATTESTATIONS + "-center/search", adminA())));
        arbre("routes_voisines", m);
        etatDesAttestations("etat");
    }

    @Test
    void le_centre_de_la_requete_n_est_jamais_compare_a_celui_de_la_session_les_quatre_routes_servent_un_autre_centre() throws Exception {
        decorDeDroits();
        UUID autre = attestationEnBase(CENTRE_B, "ATT_B", patientCouvert(CENTRE_B, "DROITS_B", false, "ENFANT"), jour(-5), jour(5));
        Map<String, Object> m = new LinkedHashMap<>();
        Resp liste = attestationsDuPatient(patient, CENTRE_A, adminB());
        m.put("liste des attestations du patient A par l'ADMIN du centre B", refus(liste) + (liste.status() == 200 ? " " + corps(liste).size() + " attestation(s)" : ""));
        Resp recherche = rechercher("{'centerId':'" + CENTRE_A + "','page':0,'size':10}", adminB());
        m.put("recherche du centre A par l'ADMIN du centre B", refus(recherche) + (recherche.status() == 200 ? " total " + corps(recherche).path("total") : ""));
        Resp sansCentre = attestationsDuPatient(patient, CENTRE_A, as(null, "ADMIN"));
        m.put("liste du patient A sans centre de session", refus(sansCentre) + (sansCentre.status() == 200 ? " " + corps(sansCentre).size() + " attestation(s)" : ""));
        m.put("création dans le centre A par l'ADMIN du centre B", refus(creerAttestation(patient, CENTRE_A, jour(10), jour(20), adminB())));
        m.put("création par un ADMIN sans centre de session", refus(creerAttestation(patient, CENTRE_A, jour(30), jour(40), as(null, "ADMIN"))));
        m.put("suppression de l'attestation du centre A par l'ADMIN du centre B", refus(supprimerAttestation(attestation, CENTRE_A, patient, adminB())));
        m.put("suppression de l'attestation du centre B par l'ADMIN du centre A (centre A de la requête)", refus(supprimerAttestation(autre, CENTRE_A, null, adminA())));
        m.put("suppression par un ADMIN sans centre de session", refus(supprimerAttestation(autre, CENTRE_B, null, as(null, "ADMIN"))));
        arbre("sans_garde_de_centre_nouveau_code", m);
        etatDesAttestations("etat_final_nouveau_code");
    }

    /**
     * Revue PC4 : la garde de centre passe AVANT la lecture complète du corps, comme les droits de rôle : un appel d'un autre
     * centre (ou d'une session sans centre) reçoit 403 même si le reste du corps est illisible. Un corps dont le centre ne se
     * lit pas suit le chemin normal (400 / 415).
     */
    @Test
    void la_garde_de_centre_passe_avant_la_lecture_du_corps_des_routes_de_creation_et_de_recherche() throws Exception {
        decorDeDroits();
        Map<String, Object> m = new LinkedHashMap<>();
        String dateIllisible = "'dateDebut':'abc','dateFin':'def'";
        m.put("création, autre centre (B) et dates illisibles", refus(doPost(ATTESTATIONS,
                json("{'patientId':'" + patient + "','centerId':'" + CENTRE_B + "'," + dateIllisible + "}"), adminA())));
        m.put("création, centre de la session et dates illisibles", refus(doPost(ATTESTATIONS,
                json("{'patientId':'" + patient + "','centerId':'" + CENTRE_A + "'," + dateIllisible + "}"), adminA())));
        m.put("création, ADMIN sans centre et dates illisibles", refus(doPost(ATTESTATIONS,
                json("{'patientId':'" + patient + "','centerId':'" + CENTRE_A + "'," + dateIllisible + "}"), as(null, "ADMIN"))));
        m.put("création, JSON invalide", refus(doPost(ATTESTATIONS, "{patientId", adminA())));
        m.put("recherche, autre centre (B) et page illisible", refus(doPost(ATTESTATIONS + "-center/search",
                json("{'centerId':'" + CENTRE_B + "','page':'x','size':10}"), adminA())));
        m.put("recherche, centre de la session et page illisible", refus(doPost(ATTESTATIONS + "-center/search",
                json("{'centerId':'" + CENTRE_A + "','page':'x','size':10}"), adminA())));
        m.put("recherche, corps absent", refus(doPost(ATTESTATIONS + "-center/search", "", adminA())));
        arbre("garde_avant_le_corps", m);
        etatDesAttestations("etat");
    }
}
