package com.hemodialyse.backend.characterization.facturation.referentiel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation F1 — paramètres de facturation du centre : {@code GET|PUT /facturation/settings} (table
 * {@code facturation_settings}) : modèle de numérotation (RG-FAC-010) et regroupement multi-forfait (RG-FAC-011). Écrit sur le
 * legacy, rejoué sur le contexte {@code facturation} ({@code ParametresFacturationControleur}) ; les écarts décidés
 * (KB-FAC-01, 08, 09) sont verrouillés par les goldens {@code _nouveau_code}.
 */
class ParametresFacturationCharacterizationIntegrationTest extends ReferentielFacturationCharacterizationSupport {

    private static String parametres(UUID centre, String userId, String format, String regroupement) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("'centerId':").append(centre == null ? "null" : "'" + centre + "'");
        sb.append(",'userId':").append(userId == null ? "null" : "'" + userId + "'");
        sb.append(",'codeFormat':").append(format == null ? "null" : "'" + format + "'");
        if (regroupement != null) sb.append(",'regroupementMultiForfait':").append(regroupement);
        return json(sb.append("}").toString());
    }

    @Override
    protected String theme() {
        return "parametres";
    }

    @Test
    void la_premiere_lecture_rend_les_valeurs_par_defaut_sans_rien_ecrire_nouveau_code() throws Exception {
        // KB-FAC-08 corrigé : valeurs par défaut sans date de mise à jour, aucune ligne écrite
        assertEquals(0, count("facturation_settings", CENTRE_A));
        golden("lecture_centre_sans_parametres_200_nouveau_code", doGet("/facturation/settings?centerId=" + CENTRE_A, adminA()));
        goldenParametres("lecture_centre_sans_parametres_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void la_modification_remplace_les_parametres_et_se_relit() throws Exception {
        golden("modification_nominale_200", doPut("/facturation/settings",
                parametres(CENTRE_A, "admin", "F{YY}-{CENTER}-{SEQ6}", "false"), adminA()));
        goldenParametres("modification_nominale_etat_base", CENTRE_A);
        golden("modification_nominale_relue_200", doGet("/facturation/settings?centerId=" + CENTRE_A, adminA()));
    }

    @Test
    void le_modele_de_numerotation_exige_un_jeton_de_sequence_et_des_jetons_connus_nouveau_code() throws Exception {
        // KB-FAC-09 corrigé : sans jeton de séquence ou avec un jeton inconnu, 400 et rien d'écrit
        golden("format_sans_jeton_400_nouveau_code", doPut("/facturation/settings",
                parametres(CENTRE_A, "admin", "FACTURE", "true"), adminA()));
        golden("format_jeton_inconnu_400_nouveau_code", doPut("/facturation/settings",
                parametres(CENTRE_A, "admin", "FAC-{MOIS}-{SEQ}", "true"), adminA()));
        golden("format_vide_400", doPut("/facturation/settings", parametres(CENTRE_A, "admin", "", "true"), adminA()));
        golden("format_absent_400", doPut("/facturation/settings", parametres(CENTRE_A, "admin", null, "true"), adminA()));
        goldenParametres("format_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void les_autres_champs() throws Exception {
        golden("regroupement_absent", doPut("/facturation/settings", parametres(CENTRE_A, "admin", "FAC-{SEQ}", null),
                adminA()));
        golden("utilisateur_absent_400", doPut("/facturation/settings", parametres(CENTRE_A, null, "FAC-{SEQ}", "true"),
                adminA()));
        golden("centre_absent_400", doPut("/facturation/settings", parametres(null, "admin", "FAC-{SEQ}", "true"),
                adminA()));
        goldenParametres("autres_champs_etat_base", CENTRE_A);
    }

    @Test
    void les_centres_et_la_session_nouveau_code() throws Exception {
        // KB-FAC-01 corrigé : lire ou modifier les paramètres d'un autre centre que celui de la session est refusé (403)
        golden("lecture_centre_B_par_admin_A_403_nouveau_code", doGet("/facturation/settings?centerId=" + CENTRE_B, adminA()));
        golden("modification_centre_B_par_admin_A_403_nouveau_code", doPut("/facturation/settings",
                parametres(CENTRE_B, "intrus", "INTRUS-{SEQ}", "false"), adminA()));
        goldenParametres("centres_etat_base_B_nouveau_code", CENTRE_B);
        golden("lecture_sans_centre", doGet("/facturation/settings", adminA()));
    }

    @Test
    void autorisations_par_profil_nouveau_code() throws Exception {
        // KB-FAC-11 reproduit ; la lecture de la secrétaire n'écrit plus rien (KB-FAC-08)
        String lecture = "/facturation/settings?centerId=" + CENTRE_A;
        golden("autorisation_medecin_lecture", doGet(lecture, as(CENTRE_A, "MEDECIN")));
        golden("autorisation_secretaire_lecture_nouveau_code", doGet(lecture, as(CENTRE_A, "SECRETAIRE")));
        golden("autorisation_infirmier_lecture", doGet(lecture, as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_medecin_modification", doPut("/facturation/settings",
                parametres(CENTRE_A, "m", "M-{SEQ}", "true"), as(CENTRE_A, "MEDECIN")));
        golden("autorisation_anonyme_lecture", doGetAnonymous(lecture));
        goldenParametres("autorisations_etat_base_nouveau_code", CENTRE_A);
    }
}
