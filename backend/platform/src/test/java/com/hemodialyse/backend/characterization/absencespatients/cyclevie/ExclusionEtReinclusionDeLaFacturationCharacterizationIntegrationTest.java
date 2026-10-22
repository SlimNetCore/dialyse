package com.hemodialyse.backend.characterization.absencespatients.cyclevie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Slice absences-patients-a2-cycle-de-vie, ACL {@code AbsencesDesPatientsLegacy} (langage publie {@code DeclarationDesAbsences}
 * de la Facturation) observee par ses deux routes d'appel : exclusion d'une seance
 * ({@code POST /api/v1/facturation/preview/seances/{id}/exclude}, declare une absence DECLAREE qualifiee du motif, dans la
 * transaction de l'exclusion) et reinclusion ({@code .../include}, supprime la ligne d'absence). L'aperçu de facturation et les
 * refus propres a la Facturation sont verrouilles par F2 ({@code ExclusionEtForfaitCharacterizationIntegrationTest}) ; ici on
 * verrouille ce que la Facturation fait AUX ABSENCES : valeur figee, motif, auteur, refus du module Absences qui annule
 * l'exclusion, suppression physique a la reinclusion quelle que soit l'evolution de l'absence (B11).
 */
class ExclusionEtReinclusionDeLaFacturationCharacterizationIntegrationTest extends CycleDeVieAbsencesCharacterizationSupport {

    private static final String PREVIEW = "/api/v1/facturation/preview/seances/";

    @Override
    protected String theme() {
        return "exclusion-facturation";
    }

    @BeforeEach
    void parametrageDeLaFacturation() {
        jdbc.update("INSERT INTO facturation_settings (center_id, code_format, regroupement_multi_forfait, updated_at, updated_by) "
                + "VALUES (?, 'SIM-{SEQ}', TRUE, CURRENT_TIMESTAMP, 'fixture')", CENTRE_A);
        jdbc.update("INSERT INTO facture_sequence (center_id, seq_year, seq_value) VALUES (?, ?, 41)", CENTRE_A,
                aujourdhui().getYear());
    }

    private String exclusion(String motif, String commentaire) {
        return json("{'centerId':'" + CENTRE_A + "','userId':'medecin'" + (motif == null ? "" : ",'motif':'" + motif + "'")
                + (commentaire == null ? "" : ",'commentaire':'" + commentaire + "'") + ",'periodStart':'" + jour(10)
                + "','periodEnd':'" + jour(0) + "','regroupementMultiForfait':true}");
    }

    private String reinclusion() {
        return json("{'centerId':'" + CENTRE_A + "','periodStart':'" + jour(10) + "','periodEnd':'" + jour(0)
                + "','regroupementMultiForfait':true}");
    }

    private ObjectNode statut(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status());
        if (r.status() >= 400 && r.raw() != null && !r.raw().isBlank()) {
            o.set("refus", refus(r));
        }
        return o;
    }

    private Resp exclure(UUID seance, String motif, String commentaire) throws Exception {
        Resp r = doPost(PREVIEW + seance + "/exclude", exclusion(motif, commentaire), admin());
        nommerLesAbsences(CENTRE_A);
        return r;
    }

    private Resp reinclure(UUID seance) throws Exception {
        return doPost(PREVIEW + seance + "/include", reinclusion(), admin());
    }

    private ObjectNode base() {
        ObjectNode o = mapper.createObjectNode();
        o.set("seances", etatSeances(CENTRE_A));
        o.set("absences", etat(CENTRE_A));
        return o;
    }

    @Test
    void exclure_une_seance_declare_une_absence_qualifiee_du_motif_au_nom_de_la_session_valorisee_au_forfait_de_la_prise_en_charge() throws Exception {
        UUID s = seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        ObjectNode o = mapper.createObjectNode();
        o.set("exclusion", statut(exclure(s, "MALADIE", "  grippe  ")));
        o.set("base", base());
        goldenNode("exclusion_nominale", o);
    }

    @Test
    void chaque_motif_d_exclusion_qualifie_l_absence_comme_a_la_declaration() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        String[] motifs = {"HOSPITALISATION", "VOYAGE", "NON_JUSTIFIEE", "AUTRE"};
        for (int i = 0; i < motifs.length; i++) {
            UUID s = seance(P_AMRANI, CENTRE_A, jour(3 + i), "SIGNEE");
            o.set(motifs[i], statut(exclure(s, motifs[i], "AUTRE".equals(motifs[i]) ? "precision" : null)));
        }
        o.set("base", base());
        goldenNode("motifs", o);
    }

    @Test
    void la_valeur_de_l_absence_est_celle_du_forfait_de_la_prise_en_charge_et_non_celui_surcharge_de_la_seance_b9() throws Exception {
        UUID s = seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        jdbc.update("UPDATE seances SET forfait_override_id = ?, forfait_override_nom = ?, forfait_override_prix = ? WHERE id = ?",
                F_PREMIUM, "Forfait Premium", new BigDecimal("15000.00"), s);
        ObjectNode o = mapper.createObjectNode();
        o.set("exclusion", statut(exclure(s, "VOYAGE", null)));
        o.set("base", base());
        goldenNode("forfait_surcharge_de_la_seance", o);
    }

    @Test
    void une_absence_existante_ce_jour_la_refuse_l_exclusion_et_annule_tout() throws Exception {
        UUID s = seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        abs(1, P_AMRANI, jour(3)).poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("exclusion", statut(exclure(s, "MALADIE", null)));
        o.set("base", base());
        goldenNode("refus_absence_existante", o);
    }

    @Test
    void une_absence_annulee_ce_jour_la_refuse_aussi_l_exclusion() throws Exception {
        UUID s = seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        abs(1, P_AMRANI, jour(3)).statut("ANNULEE").commentaire("erreur").poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("exclusion", statut(exclure(s, "MALADIE", null)));
        o.set("base", base());
        goldenNode("refus_absence_annulee_existante", o);
    }

    @Test
    void une_periode_deja_facturee_pour_le_patient_refuse_l_exclusion_et_annule_tout() throws Exception {
        UUID s = seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        facture(CENTRE_A, P_AMRANI, "FAC-1", jour(6), jour(1));
        ObjectNode o = mapper.createObjectNode();
        o.set("exclusion", statut(exclure(s, "MALADIE", null)));
        o.set("base", base());
        goldenNode("refus_periode_facturee", o);
    }

    @Test
    void le_delai_de_declaration_de_soixante_jours_refuse_l_exclusion_et_annule_tout() throws Exception {
        UUID s = seance(P_AMRANI, CENTRE_A, jour(61), "VALIDEE");
        ObjectNode o = mapper.createObjectNode();
        o.set("exclusion", statut(doPost(PREVIEW + s + "/exclude", json("{'centerId':'" + CENTRE_A
                + "','userId':'medecin','motif':'MALADIE','periodStart':'" + jour(70) + "','periodEnd':'" + jour(0)
                + "','regroupementMultiForfait':true}"), admin())));
        o.set("base", base());
        goldenNode("refus_delai_de_declaration", o);
    }

    @Test
    void reinclure_supprime_la_ligne_d_absence_et_permet_d_exclure_de_nouveau() throws Exception {
        UUID s = seance(P_AMRANI, CENTRE_A, jour(3), "SIGNEE");
        ObjectNode o = mapper.createObjectNode();
        o.set("exclusion", statut(exclure(s, "VOYAGE", null)));
        o.set("base_apres_exclusion", base());
        o.set("reinclusion", statut(reinclure(s)));
        o.set("base_apres_reinclusion", base());
        o.set("seconde_exclusion", statut(exclure(s, "MALADIE", "fievre")));
        o.set("base_apres_seconde_exclusion", base());
        goldenNode("reinclusion_nominale", o);
    }

    @Test
    void la_reinclusion_retire_l_absence_meme_travaillee_et_l_annulation_est_refusee_b11() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        UUID s1 = seance(P_AMRANI, CENTRE_A, jour(9), "VALIDEE");
        UUID s2 = seance(P_BELKACEM, CENTRE_A, jour(9), "VALIDEE");
        UUID s3 = seance(P_CHIKHI, CENTRE_A, jour(9), "VALIDEE");
        UUID s4 = seance(P_DAHMANI, CENTRE_A, jour(9), "VALIDEE");
        exclure(s1, "VOYAGE", null);
        exclure(s2, "VOYAGE", null);
        exclure(s3, "VOYAGE", null);
        exclure(s4, "VOYAGE", null);
        // evolution de chaque absence apres l'exclusion
        UUID a1 = idAbsence(P_AMRANI);
        UUID a2 = idAbsence(P_BELKACEM);
        UUID a3 = idAbsence(P_CHIKHI);
        seance(P_BELKACEM, CENTRE_A, jour(4), "VALIDEE");
        o.set("a1_requalifiee", statut(qualifier(admin(), a1, "NON_JUSTIFIEE", "finalement non excusee")));
        o.set("a2_rattrapee", statut(rattraper(admin(), a2, jour(4))));
        o.set("a3_annulation_refusee", statut(annuler(admin(), a3, "erreur")));
        o.set("base_avant_reinclusions", base());
        o.set("reinclusion_s1", statut(reinclure(s1)));
        o.set("reinclusion_s2", statut(reinclure(s2)));
        o.set("reinclusion_s3", statut(reinclure(s3)));
        o.set("reinclusion_s4_inchangee", statut(reinclure(s4)));
        o.set("base_apres_reinclusions", base());
        goldenNode("reinclusion_apres_evolution_de_l_absence_nouveau_code", o);
    }

    private UUID idAbsence(UUID patient) {
        return jdbc.queryForObject("SELECT id FROM absence_patient WHERE center_id = ? AND patient_id = ?", UUID.class,
                CENTRE_A, patient);
    }

    @Test
    void l_absence_d_une_exclusion_ne_s_annule_pas_seule_la_reinclusion_la_retire() throws Exception {
        UUID s = seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        exclure(s, "MALADIE", null);
        ObjectNode o = mapper.createObjectNode();
        o.set("annulation_de_l_absence", statut(annuler(admin(), idAbsence(P_AMRANI), "erreur")));
        o.set("base_apres_annulation", base());
        o.set("nouvelle_exclusion_seance_deja_absente", statut(exclure(s, "MALADIE", null)));
        o.set("reinclusion", statut(reinclure(s)));
        o.set("base_apres_reinclusion", base());
        o.set("nouvelle_exclusion_apres_reinclusion", statut(exclure(s, "VOYAGE", null)));
        o.set("base_finale", base());
        goldenNode("annulation_de_l_absence_d_exclusion_nouveau_code", o);
    }

    @Test
    void reinclure_quand_la_ligne_d_absence_a_deja_disparu_reussit() throws Exception {
        UUID s = seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        exclure(s, "MALADIE", null);
        jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", CENTRE_A);
        ObjectNode o = mapper.createObjectNode();
        o.set("reinclusion", statut(reinclure(s)));
        o.set("base", base());
        goldenNode("reinclusion_sans_ligne", o);
    }

    @Test
    void l_absence_d_une_exclusion_apparait_dans_les_lectures_de_suivi_comme_une_declaration() throws Exception {
        UUID s = seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        UUID s2 = seance(P_BELKACEM, CENTRE_A, jour(2), "VALIDEE");
        exclure(s, "MALADIE", "grippe");
        exclure(s2, "NON_JUSTIFIEE", null);
        ObjectNode o = mapper.createObjectNode();
        o.set("liste", resume(doGet(ABSENCES, admin())));
        o.set("synthese", resume(doGet(ABSENCES + "/synthese", admin())));
        reinclure(s);
        o.set("liste_apres_reinclusion", resume(doGet(ABSENCES, admin())));
        goldenNode("lectures_apres_exclusion", o);
    }

    @Test
    void l_exclusion_diffuse_une_saisie_infirmier_pour_l_absence_declaree_kb_abs_19() throws Exception {
        UUID s = seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        diffusions.clear();
        exclure(s, "MALADIE", null);
        ObjectNode o = mapper.createObjectNode();
        o.put("nombre_de_diffusions_de_saisie_absence", diffusions.stream()
                .filter(d -> String.valueOf(d.get("message")).contains("\"saisie\":\"ABSENCE\"")).count());
        goldenNode("diffusion_temps_reel_nouveau_code", o);
    }
}
