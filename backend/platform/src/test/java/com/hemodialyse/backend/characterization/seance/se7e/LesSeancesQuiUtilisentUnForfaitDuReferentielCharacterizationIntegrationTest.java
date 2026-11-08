package com.hemodialyse.backend.characterization.seance.se7e;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SE7e lot e5 : lecture de {@code seances} par la suppression d'un forfait du référentiel. {@code ReferentialAdminTables.java:23} déclare
 * l'usage {@code seances.forfait_override_id} ; {@code ReferentialAdminJdbcAdapter.countUsages} ({@code :201}) compte
 * {@code SELECT COUNT(*) FROM seances WHERE forfait_override_id = ?} (nom de table CALCULÉ, invisible à la garde), SANS centre ni statut,
 * ajouté aux usages {@code prise_en_charge} et {@code facture_lignes}. Observé par {@code DELETE /api/v1/admin/referentials/forfaits/{id}}
 * (ADMIN, centre de la session) : 422 {@code REFERENTIAL_IN_USE} avec le nombre de références, sinon 204.
 */
class LesSeancesQuiUtilisentUnForfaitDuReferentielCharacterizationIntegrationTest extends Se7eCharacterizationSupport {

    @Override
    protected String dossier() {
        return "referentiel";
    }

    private Map<String, Object> suppression(UUID forfait) throws Exception {
        Resp r = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete(java.net.URI.create(REFERENTIELS_API + "/forfaits/" + forfait)).with(adminA()));
        Map<String, Object> m = refusOuStatut(r.status(), r.raw());
        m.put("forfait encore en base", jdbc.queryForObject("SELECT COUNT(*) FROM forfait WHERE id = ?", Integer.class, forfait));
        return m;
    }

    @Test
    void un_forfait_surcharge_sur_une_seance_ne_se_supprime_pas_quels_que_soient_le_centre_et_le_statut_de_la_seance() throws Exception {
        UUID pa = patientSe1(CENTRE_A, "PF_A");
        UUID pb = patientSe1(CENTRE_B, "PF_B");
        UUID surA = forfait(CENTRE_A, "F_SURCHARGE_CENTRE_A", "F-A", "100.00");
        UUID surB = forfait(CENTRE_A, "F_SURCHARGE_AU_CENTRE_B", "F-B", "100.00");
        UUID annulee = forfait(CENTRE_A, "F_SURCHARGE_ANNULEE_DEUX_FOIS", "F-ANN", "100.00");
        UUID libre = forfait(CENTRE_A, "F_LIBRE", "F-LIBRE", "100.00");
        UUID s1 = seanceEnBase(CENTRE_A, pa, "U1_VALIDEE", fixe(31), "VALIDEE");
        UUID s2 = seanceEnBase(CENTRE_B, pb, "U2_CENTRE_B", fixe(31), "VALIDEE");
        UUID s3 = seanceEnBase(CENTRE_A, pa, "U3_ANNULEE", fixe(32), "ANNULEE");
        UUID s4 = seanceEnBase(CENTRE_A, pa, "U4_ANNULEE", fixe(33), "ANNULEE");
        jdbc.update("UPDATE seances SET forfait_override_id = ? WHERE id = ?", surA, s1);
        jdbc.update("UPDATE seances SET forfait_override_id = ? WHERE id = ?", surB, s2);
        jdbc.update("UPDATE seances SET forfait_override_id = ? WHERE id IN (?, ?)", annulee, s3, s4);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("forfait surcharge sur une seance VALIDEE du centre", suppression(surA));
        m.put("forfait du centre A surcharge sur une seance du centre B", suppression(surB));
        m.put("forfait surcharge sur deux seances ANNULEE", suppression(annulee));
        m.put("forfait sans usage", suppression(libre));
        arbre("usages_par_les_seances", m);
    }
}
