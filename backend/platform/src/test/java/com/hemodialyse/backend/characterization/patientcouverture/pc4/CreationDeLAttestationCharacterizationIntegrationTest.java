package com.hemodialyse.backend.characterization.patientcouverture.pc4;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PC4 : {@code POST /api/v1/pec/attestations} (création d'une attestation de droits) : cas nominal et ses effets (ligne, notification,
 * diffusion), bornes de la période, refus de forme, rattachement (patient, centre), doublons et chevauchements.
 */
class CreationDeLAttestationCharacterizationIntegrationTest extends AttestationCharacterizationSupport {

    @Override
    protected String dossier() {
        return "creation";
    }

    @Test
    void une_creation_nominale_rend_l_identifiant_et_la_periode_ecrit_la_ligne_et_notifie_le_centre() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "NOMINALE", false, "ENFANT");
        Resp r = creerAttestation(patient, CENTRE_A, jour(-5), jour(360), adminA());
        ecriture("reponse", r);
        etatDesAttestations("etat");
        notifications("notifications");
        diffusionsCapturees("diffusions");
    }

    @Test
    void les_bornes_de_la_periode_sont_incluses_la_fin_ne_peut_preceder_le_debut_et_rien_d_autre_n_est_controle() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "BORNES", false, "ENFANT");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("début = fin (un seul jour)", refus(creerAttestation(patient, CENTRE_A, jour(0), jour(0), adminA())));
        m.put("fin = veille du début", refus(creerAttestation(patient, CENTRE_A, jour(0), jour(-1), adminA())));
        m.put("fin très antérieure au début", refus(creerAttestation(patient, CENTRE_A, jour(10), jour(-10), adminA())));
        m.put("période entièrement passée (échue)", refus(creerAttestation(patient, CENTRE_A, jour(-400), jour(-300), adminA())));
        m.put("période entièrement future", refus(creerAttestation(patient, CENTRE_A, jour(100), jour(200), adminA())));
        m.put("début ancien, fin lointaine (dix ans)", refus(creerAttestation(patient, CENTRE_A, jour(-4000), jour(3650), adminA())));
        m.put("début le jour même, fin demain", refus(creerAttestation(patient, CENTRE_A, jour(0), jour(1), adminA())));
        m.put("début hier, fin le jour même", refus(creerAttestation(patient, CENTRE_A, jour(-1), jour(0), adminA())));
        m.put("début avant l'admission du patient", refus(creerAttestation(patient, CENTRE_A, jour(-1000), jour(-900), adminA())));
        arbre("bornes", m);
        etatDesAttestations("etat");
    }

    @Test
    void les_refus_de_forme_ne_creent_aucune_attestation() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "FORME", false, "ENFANT");
        String p = patient.toString();
        String a = CENTRE_A.toString();
        String d = jour(-5).toString();
        String f = jour(5).toString();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("corps vide {}", refus(doPost(ATTESTATIONS, "{}", adminA())));
        m.put("corps absent", refus(doPost(ATTESTATIONS, "", adminA())));
        m.put("JSON invalide", refus(doPost(ATTESTATIONS, "{", adminA())));
        m.put("type texte", refus(send(MockMvcRequestBuilders.post(ATTESTATIONS).contentType(MediaType.TEXT_PLAIN).content("x").with(adminA()))));
        m.put("patientId absent", refus(doPost(ATTESTATIONS, json("{'centerId':'" + a + "','dateDebut':'" + d + "','dateFin':'" + f + "'}"), adminA())));
        m.put("centerId absent", refus(doPost(ATTESTATIONS, json("{'patientId':'" + p + "','dateDebut':'" + d + "','dateFin':'" + f + "'}"), adminA())));
        m.put("dateDebut absente", refus(doPost(ATTESTATIONS, json("{'patientId':'" + p + "','centerId':'" + a + "','dateFin':'" + f + "'}"), adminA())));
        m.put("dateFin absente", refus(doPost(ATTESTATIONS, json("{'patientId':'" + p + "','centerId':'" + a + "','dateDebut':'" + d + "'}"), adminA())));
        m.put("dates nulles", refus(creerAttestation(patient, CENTRE_A, null, null, adminA())));
        m.put("dateDebut illisible (abc)", refus(doPost(ATTESTATIONS, json("{'patientId':'" + p + "','centerId':'" + a + "','dateDebut':'abc','dateFin':'" + f + "'}"), adminA())));
        m.put("dateFin au format jj/mm/aaaa", refus(doPost(ATTESTATIONS, json("{'patientId':'" + p + "','centerId':'" + a + "','dateDebut':'" + d + "','dateFin':'31/12/2030'}"), adminA())));
        m.put("dateDebut chaîne vide", refus(doPost(ATTESTATIONS, json("{'patientId':'" + p + "','centerId':'" + a + "','dateDebut':'','dateFin':'" + f + "'}"), adminA())));
        m.put("dateDebut numérique", refus(doPost(ATTESTATIONS, json("{'patientId':'" + p + "','centerId':'" + a + "','dateDebut':20260101,'dateFin':'" + f + "'}"), adminA())));
        m.put("dateFin avec heure", refus(doPost(ATTESTATIONS, json("{'patientId':'" + p + "','centerId':'" + a + "','dateDebut':'" + d + "','dateFin':'" + f + "T10:00:00'}"), adminA())));
        m.put("patientId n'est pas un UUID", refus(doPost(ATTESTATIONS, json("{'patientId':'pas-un-uuid','centerId':'" + a + "','dateDebut':'" + d + "','dateFin':'" + f + "'}"), adminA())));
        m.put("centerId n'est pas un UUID", refus(doPost(ATTESTATIONS, json("{'patientId':'" + p + "','centerId':'xyz','dateDebut':'" + d + "','dateFin':'" + f + "'}"), adminA())));
        m.put("champ inconnu ignoré", refus(doPost(ATTESTATIONS, json("{'patientId':'" + p + "','centerId':'" + a + "','dateDebut':'" + d + "','dateFin':'" + f + "','inconnu':1}"), adminA())));
        arbre("refus_nouveau_code", m);
        etatDesAttestations("etat");
    }

    @Test
    void le_rattachement_au_patient_et_au_centre_n_est_pas_verifie() throws Exception {
        UUID patientA = patientCouvert(CENTRE_A, "DU_CENTRE_A", false, "ENFANT");
        UUID patientB = patientCouvert(CENTRE_B, "DU_CENTRE_B", false, "ENFANT");
        UUID vacancier = patientCouvert(CENTRE_A, "VACANCIER", true, null);
        UUID inconnu = UUID.nameUUIDFromBytes("pc4-patient-inconnu".getBytes());
        aliasNommes.put(inconnu.toString(), "<PATIENT_INCONNU>");
        UUID centreInconnu = CENTRE_INCONNU;
        aliasNommes.put(centreInconnu.toString(), "<CENTRE_INCONNU>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("patient d'un autre centre que celui de la requête", refus(creerAttestation(patientB, CENTRE_A, jour(-5), jour(5), adminA())));
        m.put("patient inexistant", refus(creerAttestation(inconnu, CENTRE_A, jour(-5), jour(5), adminA())));
        m.put("patient vacancier", refus(creerAttestation(vacancier, CENTRE_A, jour(-5), jour(5), adminA())));
        m.put("centre sans aucun patient (identifiant inconnu)", refus(creerAttestation(patientA, centreInconnu, jour(-5), jour(5), adminA())));
        m.put("patient du centre B, centre B, par l'ADMIN du centre A", refus(creerAttestation(patientB, CENTRE_B, jour(-5), jour(5), adminA())));
        arbre("rattachement_nouveau_code", m);
        sql("etat_brut_nouveau_code", "SELECT a.patient_id, a.center_id, a.date_debut, a.date_fin FROM attestation_droit a WHERE a.patient_id IN ('" + patientA + "','"
                + patientB + "','" + vacancier + "','" + inconnu + "') ORDER BY a.patient_id, a.center_id, a.date_debut");
        jdbc.update("DELETE FROM attestation_droit WHERE patient_id IN (?, ?, ?, ?)", patientA, patientB, vacancier, inconnu);
    }

    @Test
    void la_meme_periode_deux_fois_des_chevauchements_et_des_inclusions_ajoutent_chacun_une_ligne() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "DOUBLONS", false, "ENFANT");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("1. première attestation", refus(creerAttestation(patient, CENTRE_A, jour(-5), jour(5), adminA())));
        m.put("2. exactement la même période", refus(creerAttestation(patient, CENTRE_A, jour(-5), jour(5), adminA())));
        m.put("3. chevauchement partiel", refus(creerAttestation(patient, CENTRE_A, jour(0), jour(10), adminA())));
        m.put("4. inclusion dans la première", refus(creerAttestation(patient, CENTRE_A, jour(-1), jour(1), adminA())));
        m.put("5. englobe toutes les autres", refus(creerAttestation(patient, CENTRE_A, jour(-100), jour(100), adminA())));
        m.put("6. contiguë (début = lendemain de la fin)", refus(creerAttestation(patient, CENTRE_A, jour(11), jour(20), adminA())));
        arbre("reponses", m);
        etatDesAttestations("etat");
        Map<String, Object> liste = new LinkedHashMap<>();
        Resp l = attestationsDuPatient(patient, CENTRE_A, adminA());
        liste.put("nombre d'attestations rendues par la liste du patient", corps(l).size());
        arbre("liste", liste);
    }

    @Test
    void chaque_creation_cree_un_identifiant_distinct_et_la_reponse_ne_rend_que_l_id_et_les_dates() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "IDENTIFIANTS", false, "ENFANT");
        Resp r1 = creerAttestation(patient, CENTRE_A, jour(-5), jour(5), adminA());
        Resp r2 = creerAttestation(patient, CENTRE_A, jour(-5), jour(5), adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("champs de la réponse", new java.util.TreeSet<>(corps(r1).propertyNames()));
        m.put("identifiants distincts", !corps(r1).path("id").asString().equals(corps(r2).path("id").asString()));
        m.put("type du contenu", r1.contentType());
        arbre("reponse", m);
    }
}
