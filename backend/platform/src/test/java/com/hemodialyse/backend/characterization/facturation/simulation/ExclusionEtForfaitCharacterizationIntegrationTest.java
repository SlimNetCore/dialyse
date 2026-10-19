package com.hemodialyse.backend.characterization.facturation.simulation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * Filet de caractérisation F2 — exclusion d'une séance ({@code POST /preview/seances/{id}/exclude}) et changement de
 * forfait ({@code POST /preview/seances/{id}/forfait}) : effet sur la séance (état SQL) et aperçu recalculé.
 */
class ExclusionEtForfaitCharacterizationIntegrationTest extends SimulationCharacterizationSupport {

    private static String exclusion(UUID centre, String userId, String mois) {
        return json("{'centerId':" + (centre == null ? "null" : "'" + centre + "'") + ",'userId':"
                + (userId == null ? "null" : "'" + userId + "'") + (mois == null ? "" : ",'month':'" + mois + "'")
                + ",'regroupementMultiForfait':true}");
    }

    private static String forfait(UUID centre, UUID forfait) {
        return json("{'centerId':'" + centre + "','userId':'medecin','forfaitId':" + (forfait == null ? "null" : "'" + forfait + "'")
                + ",'month':'2026-08','regroupementMultiForfait':true}");
    }

    @Override
    protected String theme() {
        return "exclusion-forfait";
    }

    // ------------------------------------------------------------------ exclusion

    @Test
    void exclure_une_seance_la_passe_absente_et_la_retire_de_l_apercu() throws Exception {
        UUID exclue = seance("A_EXCLURE_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        seance("GARDEE_03", P_EFFECTIF, CENTRE_A, "2026-08-03", "SIGNEE");
        golden("exclusion_nominale_200", doPost("/preview/seances/" + exclue + "/exclude", exclusion(CENTRE_A, "medecin",
                "2026-08"), adminA()));
        goldenSeances("exclusion_nominale_etat_base", CENTRE_A);
        golden("exclusion_deuxieme_fois", doPost("/preview/seances/" + exclue + "/exclude", exclusion(CENTRE_A, "medecin",
                "2026-08"), adminA()));
    }

    @Test
    void les_refus_d_exclusion() throws Exception {
        UUID facturee = seance("FACTUREE_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "FACTUREE");
        UUID brouillon = seance("BROUILLON_03", P_EFFECTIF, CENTRE_A, "2026-08-03", "BROUILLON");
        UUID duB = seance("CENTRE_B_02", P_CENTRE_B, CENTRE_B, "2026-08-02", "VALIDEE");
        golden("exclusion_inconnue", doPost("/preview/seances/" + UUID.randomUUID() + "/exclude",
                exclusion(CENTRE_A, "medecin", "2026-08"), adminA()));
        golden("exclusion_facturee", doPost("/preview/seances/" + facturee + "/exclude", exclusion(CENTRE_A, "medecin",
                "2026-08"), adminA()));
        golden("exclusion_brouillon", doPost("/preview/seances/" + brouillon + "/exclude", exclusion(CENTRE_A, "medecin",
                "2026-08"), adminA()));
        golden("exclusion_seance_du_centre_B_avec_le_centre_A", doPost("/preview/seances/" + duB + "/exclude",
                exclusion(CENTRE_A, "medecin", "2026-08"), adminA()));
        golden("exclusion_utilisateur_absent", doPost("/preview/seances/" + brouillon + "/exclude", exclusion(CENTRE_A, null,
                "2026-08"), adminA()));
        golden("exclusion_centre_absent", doPost("/preview/seances/" + brouillon + "/exclude", exclusion(null, "medecin",
                "2026-08"), adminA()));
        goldenSeances("exclusion_refus_etat_base_A", CENTRE_A);
        goldenSeances("exclusion_refus_etat_base_B", CENTRE_B);
    }

    @Test
    void une_exclusion_sans_periode_echoue_au_recalcul_de_l_apercu() throws Exception {
        UUID seance = seance("SANS_PERIODE_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        golden("exclusion_sans_periode", doPost("/preview/seances/" + seance + "/exclude", exclusion(CENTRE_A, "medecin", null),
                adminA()));
        goldenSeances("exclusion_sans_periode_etat_base", CENTRE_A);
    }

    @Test
    void l_exclusion_d_une_seance_d_un_autre_centre_que_celui_de_la_session() throws Exception {
        UUID deA = seance("CENTRE_A_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        golden("exclusion_seance_A_par_admin_B", doPost("/preview/seances/" + deA + "/exclude", exclusion(CENTRE_A, "intrus",
                "2026-08"), as(CENTRE_B, "ADMIN")));
        goldenSeances("exclusion_seance_A_par_admin_B_etat_base", CENTRE_A);
    }

    // ------------------------------------------------------------------ changement de forfait

    @Test
    void changer_le_forfait_d_une_seance_pose_une_surcharge_et_recalcule_l_apercu() throws Exception {
        UUID seance = seance("A_CHANGER_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        seance("INCHANGEE_03", P_EFFECTIF, CENTRE_A, "2026-08-03", "VALIDEE");
        golden("forfait_nominal_200", doPost("/preview/seances/" + seance + "/forfait", forfait(CENTRE_A, FORFAIT_PREMIUM),
                adminA()));
        goldenSeances("forfait_nominal_etat_base", CENTRE_A);
    }

    @Test
    void les_refus_de_changement_de_forfait() throws Exception {
        UUID seance = seance("VALIDEE_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        UUID facturee = seance("FACTUREE_03", P_EFFECTIF, CENTRE_A, "2026-08-03", "FACTUREE");
        golden("forfait_inconnu", doPost("/preview/seances/" + seance + "/forfait", forfait(CENTRE_A, UUID.randomUUID()),
                adminA()));
        golden("forfait_du_centre_B", doPost("/preview/seances/" + seance + "/forfait", forfait(CENTRE_A, FORFAIT_B), adminA()));
        golden("forfait_absent", doPost("/preview/seances/" + seance + "/forfait", forfait(CENTRE_A, null), adminA()));
        golden("forfait_seance_facturee", doPost("/preview/seances/" + facturee + "/forfait", forfait(CENTRE_A, FORFAIT_PREMIUM),
                adminA()));
        golden("forfait_seance_inconnue", doPost("/preview/seances/" + UUID.randomUUID() + "/forfait",
                forfait(CENTRE_A, FORFAIT_PREMIUM), adminA()));
        goldenSeances("forfait_refus_etat_base", CENTRE_A);
    }

    @Test
    void autorisations_des_modifications() throws Exception {
        UUID seance = seance("VALIDEE_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        golden("exclusion_secretaire", doPost("/preview/seances/" + seance + "/exclude", exclusion(CENTRE_A, "s", "2026-08"),
                as(CENTRE_A, "SECRETAIRE")));
        golden("forfait_medecin", doPost("/preview/seances/" + seance + "/forfait", forfait(CENTRE_A, FORFAIT_PREMIUM),
                as(CENTRE_A, "MEDECIN")));
        golden("forfait_infirmier", doPost("/preview/seances/" + seance + "/forfait", forfait(CENTRE_A, FORFAIT_PREMIUM),
                as(CENTRE_A, "INFIRMIER")));
        goldenSeances("autorisations_etat_base", CENTRE_A);
    }
}
