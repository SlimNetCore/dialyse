package com.hemodialyse.backend.characterization.patientcouverture.pc4;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC4 : {@code GET /api/v1/pec/attestations/{patientId}?centerId=} (liste d'un patient, mise en cache par centre et patient) et
 * {@code DELETE /api/v1/pec/attestations/{id}?centerId=&patientId=} (suppression).
 */
class LectureEtSuppressionDesAttestationsCharacterizationIntegrationTest extends AttestationCharacterizationSupport {

    @Override
    protected String dossier() {
        return "lecture";
    }

    /** Projection triée (l'ordre de l'API n'est pas garanti : aucun tri dans la requête) des périodes rendues. */
    private List<String> periodes(Resp r) {
        List<String> l = new ArrayList<>();
        for (JsonNode a : corps(r)) l.add(a.path("dateDebut").asString() + " -> " + a.path("dateFin").asString());
        l.sort(Comparator.naturalOrder());
        return l;
    }

    @Test
    void la_liste_d_un_patient_rend_l_entite_de_domaine_telle_quelle_pour_un_seul_centre_et_un_seul_patient() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "LISTE", false, "ENFANT");
        attestationEnBase(CENTRE_A, "ATT_UNIQUE", patient, jour(-5), jour(5));
        lecture("une_attestation_forme_brute", attestationsDuPatient(patient, CENTRE_A, adminA()));

        UUID autre = patientCouvert(CENTRE_A, "AUTRE", false, "ENFANT");
        attestationEnBase(CENTRE_A, "ATT_2", patient, jour(10), jour(20));
        attestationEnBase(CENTRE_A, "ATT_3", patient, jour(-100), jour(-50));
        attestationEnBase(CENTRE_A, "ATT_AUTRE_PATIENT", autre, jour(-5), jour(5));
        attestationEnBase(CENTRE_B, "ATT_AUTRE_CENTRE", patient, jour(-7), jour(7));
        viderLesCaches();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trois attestations du patient dans le centre A (triées par début)", periodes(attestationsDuPatient(patient, CENTRE_A, adminA())));
        m.put("attestations du même patient lues avec le centre B", periodes(attestationsDuPatient(patient, CENTRE_B, adminA())));
        m.put("autre patient", periodes(attestationsDuPatient(autre, CENTRE_A, adminA())));
        m.put("patient inconnu", periodes(attestationsDuPatient(UUID.nameUUIDFromBytes("pc4-inconnu".getBytes()), CENTRE_A, adminA())));
        m.put("centre inconnu", periodes(attestationsDuPatient(patient, CENTRE_INCONNU, adminA())));
        m.put("lue par la secrétaire", periodes(attestationsDuPatient(patient, CENTRE_A, as(CENTRE_A, "SECRETAIRE"))));
        arbre("isolement", m);
        lecture("patient_inconnu_liste_vide", attestationsDuPatient(UUID.nameUUIDFromBytes("pc4-inconnu".getBytes()), CENTRE_A, adminA()));
    }

    @Test
    void les_refus_de_forme_de_la_liste() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "FORME_LISTE", false, "ENFANT");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centerId absent", refus(doGet(ATTESTATIONS + "/" + patient, adminA())));
        m.put("centerId n'est pas un UUID", refus(doGet(ATTESTATIONS + "/" + patient + "?centerId=abc", adminA())));
        m.put("centerId vide", refus(doGet(ATTESTATIONS + "/" + patient + "?centerId=", adminA())));
        m.put("patientId n'est pas un UUID", refus(doGet(ATTESTATIONS + "/abc?centerId=" + CENTRE_A, adminA())));
        m.put("patientId absent de l'URL", refus(doGet(ATTESTATIONS + "/?centerId=" + CENTRE_A, adminA())));
        m.put("route POST sur la liste", refus(doPost(ATTESTATIONS + "/" + patient + "?centerId=" + CENTRE_A, "{}", adminA())));
        m.put("route PUT sur la liste", refus(doPut(ATTESTATIONS + "/" + patient + "?centerId=" + CENTRE_A, "{}", adminA())));
        arbre("refus", m);
    }

    @Test
    void la_liste_est_gardee_en_cache_par_centre_et_patient_une_ecriture_de_l_api_la_renouvelle_une_ecriture_directe_reste_ignoree() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "CACHE", false, "ENFANT");
        UUID autre = patientCouvert(CENTRE_A, "CACHE_AUTRE", false, "ENFANT");
        attestationEnBase(CENTRE_A, "C1", patient, jour(-5), jour(5));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("1. première lecture", periodes(attestationsDuPatient(patient, CENTRE_A, adminA())));
        attestationEnBase(CENTRE_A, "C2", patient, jour(10), jour(20));
        m.put("2. après une ligne ajoutée en base : liste périmée", periodes(attestationsDuPatient(patient, CENTRE_A, adminA())));
        creerAttestation(autre, CENTRE_A, jour(1), jour(2), adminA());
        m.put("3. après la création d'une attestation d'un AUTRE patient : toujours périmée", periodes(attestationsDuPatient(patient, CENTRE_A, adminA())));
        creerAttestation(patient, CENTRE_A, jour(30), jour(40), adminA());
        m.put("4. après la création d'une attestation du même patient : renouvelée", periodes(attestationsDuPatient(patient, CENTRE_A, adminA())));
        attestationEnBase(CENTRE_A, "C3", patient, jour(50), jour(60));
        m.put("5. nouvelle ligne en base : périmée", periodes(attestationsDuPatient(patient, CENTRE_A, adminA())));
        attestationsDuPatient(autre, CENTRE_A, adminA());
        attestationEnBase(CENTRE_A, "C4", autre, jour(70), jour(80));
        UUID aSupprimer = attestationEnBase(CENTRE_A, "C5", autre, jour(90), jour(95));
        supprimerAttestation(aSupprimer, CENTRE_A, autre, adminA());
        m.put("6. après une suppression (toute entrée du cache effacée) : renouvelée", periodes(attestationsDuPatient(patient, CENTRE_A, adminA())));
        m.put("7. liste de l'autre patient après la suppression", periodes(attestationsDuPatient(autre, CENTRE_A, adminA())));
        arbre("cache", m);
    }

    @Test
    void la_suppression_efface_la_ligne_notifie_le_centre_et_ne_verifie_ni_le_centre_ni_le_patient() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "SUPPR", false, "ENFANT");
        UUID autre = patientCouvert(CENTRE_A, "SUPPR_AUTRE", false, "ENFANT");
        UUID a1 = attestationEnBase(CENTRE_A, "S1", patient, jour(-5), jour(5));
        UUID a2 = attestationEnBase(CENTRE_A, "S2", patient, jour(10), jour(20));
        UUID a3 = attestationEnBase(CENTRE_A, "S3", patient, jour(30), jour(40));
        UUID aB = attestationEnBase(CENTRE_B, "SB", patient, jour(-7), jour(7));
        Map<String, Object> m = new LinkedHashMap<>();
        ecriture("nominale", supprimerAttestation(a1, CENTRE_A, patient, adminA()));
        m.put("nominale : lignes restantes", nombreDAttestations());
        m.put("sans patientId", refus(supprimerAttestation(a2, CENTRE_A, null, adminA())));
        m.put("patientId d'un autre patient (ignoré)", refus(supprimerAttestation(a3, CENTRE_A, autre, adminA())));
        m.put("attestation du centre B supprimée avec le centre A de la requête", refus(supprimerAttestation(aB, CENTRE_A, patient, adminA())));
        m.put("seconde suppression de la même attestation", refus(supprimerAttestation(a1, CENTRE_A, patient, adminA())));
        m.put("identifiant inconnu", refus(supprimerAttestation(UUID.nameUUIDFromBytes("pc4-att-inconnue".getBytes()), CENTRE_A, patient, adminA())));
        m.put("lignes restantes", nombreDAttestations());
        arbre("reponses", m);
        etatDesAttestations("etat");
        notifications("notifications");
        diffusionsCapturees("diffusions");
    }

    @Test
    void les_refus_de_forme_de_la_suppression_laissent_les_lignes() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "SUPPR_FORME", false, "ENFANT");
        UUID a1 = attestationEnBase(CENTRE_A, "F1", patient, jour(-5), jour(5));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centerId absent", refus(doDelete(ATTESTATIONS + "/" + a1, adminA())));
        m.put("centerId n'est pas un UUID", refus(doDelete(ATTESTATIONS + "/" + a1 + "?centerId=abc", adminA())));
        m.put("identifiant n'est pas un UUID", refus(doDelete(ATTESTATIONS + "/abc?centerId=" + CENTRE_A, adminA())));
        m.put("patientId n'est pas un UUID", refus(doDelete(ATTESTATIONS + "/" + a1 + "?centerId=" + CENTRE_A + "&patientId=abc", adminA())));
        m.put("identifiant absent de l'URL", refus(doDelete(ATTESTATIONS + "?centerId=" + CENTRE_A, adminA())));
        m.put("lignes restantes", nombreDAttestations());
        arbre("refus", m);
    }
}
