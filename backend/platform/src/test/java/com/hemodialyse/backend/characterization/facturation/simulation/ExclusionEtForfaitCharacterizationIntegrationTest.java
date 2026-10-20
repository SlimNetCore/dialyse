package com.hemodialyse.backend.characterization.facturation.simulation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filet de caractérisation F2 — exclusion d'une séance ({@code POST /preview/seances/{id}/exclude}) et changement de
 * forfait ({@code POST /preview/seances/{id}/forfait}) : effet sur la séance (état SQL) et aperçu recalculé.
 * <p>
 * Décisions F2 du 2026-10-19 (parité du nouveau code) : l'exclusion exige un motif d'absence, déclare une absence de patient
 * et se réinclut (KB-FAC-13, ADR 0031) ; le centre demandé doit être celui de la session (KB-FAC-14). Ces scénarios portent des
 * goldens {@code *_nouveau_code} ; les goldens des autres scénarios sont ceux du legacy, inchangés. Les exclusions réussies
 * utilisent des dates relatives au jour d'exécution (le module Absences refuse une absence de plus de 60 jours ou future),
 * remplacées par des alias dans les goldens ({@code <J-3>}…). Retouche de fixture : la requête d'exclusion porte désormais un
 * {@code motif} (champ obligatoire du nouveau contrat) ; les refus du legacy y répondent avant le motif, leurs goldens sont
 * inchangés.
 */
class ExclusionEtForfaitCharacterizationIntegrationTest extends SimulationCharacterizationSupport {

    private static final UUID UTILISATEUR = UUID.fromString("99997000-0000-0000-0000-0000000c0001");

    private static String exclusion(UUID centre, String userId, String mois) {
        return json("{'centerId':" + (centre == null ? "null" : "'" + centre + "'") + ",'userId':"
                + (userId == null ? "null" : "'" + userId + "'") + ",'motif':'MALADIE'" + (mois == null ? "" : ",'month':'" + mois + "'")
                + ",'regroupementMultiForfait':true}");
    }

    /**
     * Exclusion sur la période des dix derniers jours (dates relatives), avec le motif et le commentaire donnés.
     */
    private static String exclusionRecente(UUID centre, String motif, String commentaire) {
        return json("{'centerId':'" + centre + "','userId':'medecin'" + (motif == null ? "" : ",'motif':'" + motif + "'")
                + (commentaire == null ? "" : ",'commentaire':'" + commentaire + "'") + ",'periodStart':'" + jour(10)
                + "','periodEnd':'" + jour(0) + "','regroupementMultiForfait':true}");
    }

    private static String reinclusionRecente(UUID centre) {
        return json("{'centerId':'" + centre + "','periodStart':'" + jour(10) + "','periodEnd':'" + jour(0)
                + "','regroupementMultiForfait':true}");
    }

    private static String jour(int joursAvant) {
        return LocalDate.now().minusDays(joursAvant).toString();
    }

    private static String forfait(UUID centre, UUID forfait) {
        return json("{'centerId':'" + centre + "','userId':'medecin','forfaitId':" + (forfait == null ? "null" : "'" + forfait + "'")
                + ",'month':'2026-08','regroupementMultiForfait':true}");
    }

    @Override
    protected String theme() {
        return "exclusion-forfait";
    }

    private void nommerLesJours() {
        nommerTexte(jour(10), "<J-10>");
        nommerTexte(jour(3), "<J-3>");
        nommerTexte(jour(2), "<J-2>");
        nommerTexte(jour(0), "<AUJOURDHUI>");
        nommerTexte(jour(90), "<J-90>");
        nommer(UTILISATEUR, "<UTILISATEUR>");
    }

    // ------------------------------------------------------------------ exclusion

    @Test
    void exclure_une_seance_la_passe_absente_declare_une_absence_et_la_retire_de_l_apercu_nouveau_code() throws Exception {
        nommerLesJours();
        UUID exclue = seance("A_EXCLURE_02", P_EFFECTIF, CENTRE_A, jour(3), "VALIDEE");
        seance("GARDEE_03", P_EFFECTIF, CENTRE_A, jour(2), "SIGNEE");
        golden("exclusion_nominale_200_nouveau_code", doPost("/preview/seances/" + exclue + "/exclude",
                exclusionRecente(CENTRE_A, "MALADIE", "grippe"), adminA(UTILISATEUR)));
        goldenExclusions("exclusion_nominale_etat_base_nouveau_code", CENTRE_A);
        golden("exclusion_deuxieme_fois", doPost("/preview/seances/" + exclue + "/exclude",
                exclusionRecente(CENTRE_A, "MALADIE", "grippe"), adminA(UTILISATEUR)));
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
    void l_exclusion_d_une_seance_d_un_autre_centre_que_celui_de_la_session_nouveau_code() throws Exception {
        UUID deA = seance("CENTRE_A_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        golden("exclusion_seance_A_par_admin_B_nouveau_code", doPost("/preview/seances/" + deA + "/exclude",
                exclusion(CENTRE_A, "intrus", "2026-08"), as(CENTRE_B, "ADMIN")));
        goldenSeances("exclusion_seance_A_par_admin_B_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void le_motif_d_exclusion_est_obligatoire_et_autre_exige_un_commentaire_nouveau_code() throws Exception {
        nommerLesJours();
        UUID seance = seance("MOTIF_02", P_EFFECTIF, CENTRE_A, jour(3), "VALIDEE");
        String url = "/preview/seances/" + seance + "/exclude";
        golden("exclusion_motif_absent_nouveau_code", doPost(url, exclusionRecente(CENTRE_A, null, null), adminA(UTILISATEUR)));
        golden("exclusion_motif_inconnu_nouveau_code", doPost(url, exclusionRecente(CENTRE_A, "PARESSE", null),
                adminA(UTILISATEUR)));
        golden("exclusion_autre_sans_commentaire_nouveau_code", doPost(url, exclusionRecente(CENTRE_A, "AUTRE", null),
                adminA(UTILISATEUR)));
        goldenExclusions("exclusion_motif_refus_etat_base_nouveau_code", CENTRE_A);
        golden("exclusion_autre_avec_commentaire_200_nouveau_code", doPost(url, exclusionRecente(CENTRE_A, "AUTRE", "déménagement"),
                adminA(UTILISATEUR)));
        goldenExclusions("exclusion_autre_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void le_refus_du_module_absences_annule_l_exclusion_nouveau_code() throws Exception {
        nommerLesJours();
        UUID ancienne = seance("ANCIENNE_90", P_EFFECTIF, CENTRE_A, jour(90), "VALIDEE");
        golden("exclusion_delai_de_declaration_depasse_nouveau_code", doPost("/preview/seances/" + ancienne + "/exclude",
                json("{'centerId':'" + CENTRE_A + "','userId':'medecin','motif':'MALADIE','periodStart':'" + jour(100)
                        + "','periodEnd':'" + jour(0) + "','regroupementMultiForfait':true}"), adminA(UTILISATEUR)));
        goldenExclusions("exclusion_delai_etat_base_nouveau_code", CENTRE_A);
    }

    // ------------------------------------------------------------------ réinclusion

    @Test
    void reinclure_une_seance_exclue_lui_rend_son_statut_et_retire_l_absence_nouveau_code() throws Exception {
        nommerLesJours();
        UUID signee = seance("SIGNEE_03", P_EFFECTIF, CENTRE_A, jour(3), "SIGNEE");
        seance("GARDEE_02", P_EFFECTIF, CENTRE_A, jour(2), "VALIDEE");
        doPost("/preview/seances/" + signee + "/exclude", exclusionRecente(CENTRE_A, "VOYAGE", null), adminA(UTILISATEUR));

        golden("reinclusion_nominale_200_nouveau_code", doPost("/preview/seances/" + signee + "/include",
                reinclusionRecente(CENTRE_A), adminA(UTILISATEUR)));
        goldenExclusions("reinclusion_nominale_etat_base_nouveau_code", CENTRE_A);

        golden("reexclusion_apres_reinclusion_200_nouveau_code", doPost("/preview/seances/" + signee + "/exclude",
                exclusionRecente(CENTRE_A, "MALADIE", "fièvre"), adminA(UTILISATEUR)));
        goldenExclusions("reexclusion_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void les_refus_de_reinclusion_nouveau_code() throws Exception {
        nommerLesJours();
        UUID validee = seance("VALIDEE_03", P_EFFECTIF, CENTRE_A, jour(3), "VALIDEE");
        UUID absenteReelle = seance("ABSENTE_REELLE_02", P_EFFECTIF, CENTRE_A, jour(2), "ABSENT");
        UUID facturee = seance("FACTUREE_04", P_EFFECTIF, CENTRE_A, jour(4), "FACTUREE");
        UUID duB = seance("CENTRE_B_03", P_CENTRE_B, CENTRE_B, jour(3), "VALIDEE");
        golden("reinclusion_non_exclue_nouveau_code", doPost("/preview/seances/" + validee + "/include",
                reinclusionRecente(CENTRE_A), adminA(UTILISATEUR)));
        golden("reinclusion_absence_reelle_nouveau_code", doPost("/preview/seances/" + absenteReelle + "/include",
                reinclusionRecente(CENTRE_A), adminA(UTILISATEUR)));
        golden("reinclusion_facturee_nouveau_code", doPost("/preview/seances/" + facturee + "/include",
                reinclusionRecente(CENTRE_A), adminA(UTILISATEUR)));
        golden("reinclusion_inconnue_nouveau_code", doPost("/preview/seances/" + UUID.randomUUID() + "/include",
                reinclusionRecente(CENTRE_A), adminA(UTILISATEUR)));
        golden("reinclusion_seance_du_centre_B_avec_le_centre_A_nouveau_code", doPost("/preview/seances/" + duB + "/include",
                reinclusionRecente(CENTRE_A), adminA(UTILISATEUR)));
        golden("reinclusion_centre_absent_nouveau_code", doPost("/preview/seances/" + validee + "/include",
                json("{'periodStart':'" + jour(10) + "','periodEnd':'" + jour(0) + "','regroupementMultiForfait':true}"),
                adminA(UTILISATEUR)));
        goldenExclusions("reinclusion_refus_etat_base_A_nouveau_code", CENTRE_A);
        goldenExclusions("reinclusion_refus_etat_base_B_nouveau_code", CENTRE_B);
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
    void le_changement_de_forfait_et_la_reinclusion_refusent_un_centre_different_de_la_session_nouveau_code() throws Exception {
        UUID seance = seance("VALIDEE_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        golden("forfait_seance_A_par_admin_B_nouveau_code", doPost("/preview/seances/" + seance + "/forfait",
                forfait(CENTRE_A, FORFAIT_PREMIUM), as(CENTRE_B, "ADMIN")));
        golden("reinclusion_seance_A_par_admin_B_nouveau_code", doPost("/preview/seances/" + seance + "/include",
                json("{'centerId':'" + CENTRE_A + "','month':'2026-08','regroupementMultiForfait':true}"), as(CENTRE_B, "ADMIN")));
        goldenSeances("centre_different_etat_base_nouveau_code", CENTRE_A);
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

    @Test
    void autorisations_de_la_reinclusion_nouveau_code() throws Exception {
        UUID seance = seance("VALIDEE_02", P_EFFECTIF, CENTRE_A, "2026-08-02", "VALIDEE");
        String corps = json("{'centerId':'" + CENTRE_A + "','month':'2026-08','regroupementMultiForfait':true}");
        golden("reinclusion_secretaire_nouveau_code", doPost("/preview/seances/" + seance + "/include", corps,
                as(CENTRE_A, "SECRETAIRE")));
        golden("reinclusion_medecin_nouveau_code", doPost("/preview/seances/" + seance + "/include", corps, as(CENTRE_A, "MEDECIN")));
        golden("reinclusion_anonyme_nouveau_code", doPostAnonyme("/preview/seances/" + seance + "/include", corps));
    }
}
