package com.hemodialyse.backend.characterization.patientcouverture.pc3;

import com.hemodialyse.backend.characterization.patientcouverture.CouvertureCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PC3 : {@code POST /api/v1/pec} (demande de prise en charge) : règle de l'attestation valide du non-vacancier, refus de forme,
 * périodes sans contrôle (chevauchement, fin avant début), forfait demandé.
 */
class CreationDeLaPriseEnChargeCharacterizationIntegrationTest extends CouvertureCharacterizationSupport {

    @Override
    protected String racine() {
        return "../patientcouverture/pc3/";
    }

    @Override
    protected String dossier() {
        return "creation";
    }

    @Test
    void une_demande_nominale_cree_une_pec_au_statut_cree_avec_la_periode_et_le_forfait_demandes() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "VACANCIER", true, "ASSURE_LUI_MEME");
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        Resp r = creerLaPec("PEC_1", patient, CENTRE_A, jour(-5), jour(25), forfait, adminA());
        ecriture("reponse_de_la_creation", r);
        etatDesPec("pec_ecrite");
        notifications("notifications_durables");
        lecture("pec_du_patient_telle_que_relue", pecsDuPatient(patient, CENTRE_A, adminA()));
    }

    @Test
    void un_patient_non_vacancier_exige_une_attestation_couvrant_aujourd_hui_bornes_incluses() throws Exception {
        supposerLeJourDeLaJvmEgalAuJourDuCentre();
        Map<String, LocalDate[]> cas = new LinkedHashMap<>();
        cas.put("aucune attestation", null);
        cas.put("attestation couvrant la période", new LocalDate[]{jour(-5), jour(5)});
        cas.put("attestation débutant aujourd'hui", new LocalDate[]{jour(0), jour(5)});
        cas.put("attestation finissant aujourd'hui", new LocalDate[]{jour(-5), jour(0)});
        cas.put("attestation d'un seul jour (aujourd'hui)", new LocalDate[]{jour(0), jour(0)});
        cas.put("attestation échue hier", new LocalDate[]{jour(-5), jour(-1)});
        cas.put("attestation débutant demain", new LocalDate[]{jour(1), jour(5)});
        cas.put("attestation inversée (fin avant début) couvrant aujourd'hui", new LocalDate[]{jour(5), jour(-5)});
        Map<String, Object> m = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<String, LocalDate[]> e : cas.entrySet()) {
            UUID patient = patientCouvert(CENTRE_A, "NV_" + (++i), false, "ASSURE_LUI_MEME");
            if (e.getValue() != null) attestation(CENTRE_A, patient, e.getValue()[0], e.getValue()[1]);
            m.put(e.getKey(), refus(creerLaPec("PEC_" + i, patient, CENTRE_A, jour(-5), jour(25), null, adminA())));
        }
        UUID vacancier = patientCouvert(CENTRE_A, "VAC", true, null);
        m.put("vacancier sans attestation", refus(creerLaPec("PEC_VAC", vacancier, CENTRE_A, jour(-5), jour(25), null, adminA())));
        UUID autre = patientCouvert(CENTRE_A, "NV_AUTRE", false, null);
        UUID chezB = patientCouvert(CENTRE_B, "NV_B", false, null);
        attestation(CENTRE_B, autre, jour(-5), jour(5));
        attestation(CENTRE_A, chezB, jour(-5), jour(5));
        m.put("attestation posée dans l'autre centre", refus(creerLaPec("PEC_X", autre, CENTRE_A, jour(-5), jour(25), null, adminA())));
        arbre("attestation_du_non_vacancier", m);
        etatDesPec("pec_ecrites");
    }

    @Test
    void les_refus_de_forme_ne_creent_aucune_pec() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "VACANCIER", true, null);
        UUID chezB = patientCouvert(CENTRE_B, "CHEZ_B", true, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("patient inconnu", refus(creerLaPec("X", UUID.nameUUIDFromBytes("inconnu".getBytes()), CENTRE_A, jour(-5), jour(25), null, adminA())));
        m.put("patient d'un autre centre", refus(creerLaPec("X", chezB, CENTRE_A, jour(-5), jour(25), null, adminA())));
        m.put("patient absent", refus(creerLaPec("X", null, CENTRE_A, jour(-5), jour(25), null, adminA())));
        m.put("centre absent", refus(creerLaPec("X", patient, null, jour(-5), jour(25), null, adminA())));
        m.put("corps vide", refus(doPost(PEC, json("{}"), adminA())));
        m.put("corps absent", refus(doPost(PEC, "", adminA())));
        m.put("JSON illisible", refus(doPost(PEC, "{patientId", adminA())));
        m.put("identifiant de patient illisible", refus(doPost(PEC, json("{'patientId':'zzz','centerId':'" + CENTRE_A + "'}"), adminA())));
        m.put("date illisible", refus(doPost(PEC, json("{'patientId':'" + patient + "','centerId':'" + CENTRE_A + "','dateDebutDemande':'31/12'}"), adminA())));
        m.put("type de contenu texte", refus(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(java.net.URI.create(PEC))
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN).content("x").with(adminA()))));
        arbre("refus_nouveau_code", m);
        etatDesPec("aucune_pec_ecrite");
    }

    @Test
    void les_periodes_et_le_forfait_demandes_ne_sont_pas_controles() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "VACANCIER", true, null);
        UUID forfaitB = forfait(CENTRE_B, "HDB", "7000.00");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dates absentes", refus(creerLaPec("P1", patient, CENTRE_A, null, null, null, adminA())));
        m.put("fin seule", refus(creerLaPec("P2", patient, CENTRE_A, null, jour(25), null, adminA())));
        m.put("début seul", refus(creerLaPec("P3", patient, CENTRE_A, jour(-5), null, null, adminA())));
        m.put("fin avant le début", refus(creerLaPec("P4", patient, CENTRE_A, jour(25), jour(-5), null, adminA())));
        m.put("période passée", refus(creerLaPec("P5", patient, CENTRE_A, jour(-100), jour(-50), null, adminA())));
        m.put("forfait inconnu", refus(creerLaPec("P6", patient, CENTRE_A, jour(-5), jour(25), UUID.nameUUIDFromBytes("forfait".getBytes()), adminA())));
        m.put("forfait d'un autre centre", refus(creerLaPec("P7", patient, CENTRE_A, jour(-5), jour(25), forfaitB, adminA())));
        arbre("acceptations_sans_controle", m);
        sql("pec_ecrites", "SELECT c.statut, c.date_debut_demande, c.date_fin_demande, c.forfait_demande_id IS NOT NULL AS avec_forfait FROM prise_en_charge c "
                + "WHERE c.center_id = '" + CENTRE_A + "' ORDER BY c.date_debut_demande NULLS FIRST, c.date_fin_demande NULLS FIRST, c.forfait_demande_id NULLS FIRST");
    }

    @Test
    void plusieurs_pec_du_meme_patient_se_chevauchent_ou_se_dupliquent_sans_controle_et_aucune_ne_ferme_l_autre() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "VACANCIER", true, null);
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("première", refus(creerLaPec("PEC_1", patient, CENTRE_A, jour(-5), jour(25), forfait, adminA())));
        m.put("identique", refus(creerLaPec("PEC_2", patient, CENTRE_A, jour(-5), jour(25), forfait, adminA())));
        m.put("chevauchante", refus(creerLaPec("PEC_3", patient, CENTRE_A, jour(10), jour(60), null, adminA())));
        m.put("incluse", refus(creerLaPec("PEC_4", patient, CENTRE_A, jour(0), jour(1), null, adminA())));
        arbre("toutes_acceptees", m);
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("nombre de PEC", nombreDePec());
        n.put("statuts", jdbc.queryForList("SELECT DISTINCT statut FROM prise_en_charge WHERE center_id = ?", String.class, CENTRE_A));
        arbre("etat", n);
    }
}
