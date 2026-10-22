package com.hemodialyse.backend.characterization.absencespatients.cyclevie;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * Slice absences-patients-a2-cycle-de-vie, {@code PUT /api/v1/absences-patients/{id}/annulation}. Controles dans l'ordre : corps
 * valide (commentaire non vide), centre, absence du centre, periode facturee, droit de correction (ADMIN ou MEDECIN des qu'elle
 * n'est plus « a qualifier »), absence deja annulee. Une absence RATTRAPEE peut etre annulee (B1 / KB-ABS-10), l'annulation ecrase le
 * commentaire (B4) et conserve motif et date de rattrapage.
 */
class AnnulationDUneAbsenceCharacterizationIntegrationTest extends CycleDeVieAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "annulation";
    }

    @Test
    void annuler_une_absence_a_qualifier_par_n_importe_quel_profil_la_passe_annulee_avec_le_commentaire() throws Exception {
        UUID a = abs(1, P_AMRANI, jour(8)).poser();
        UUID b = abs(2, P_BELKACEM, jour(8)).poser();
        UUID c = abs(3, P_CHIKHI, jour(8)).poser();
        UUID d = abs(4, P_DAHMANI, jour(8)).poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("admin", resume(annuler(admin(), a, "  patient present  ")));
        o.set("secretaire", refus(annuler(secretaire(), b, "erreur de saisie")));
        o.set("medecin", refus(annuler(medecin(), c, "erreur de saisie")));
        o.set("infirmier", refus(annuler(infirmier(), d, "erreur de saisie")));
        o.set("base", etat(CENTRE_A));
        goldenNode("a_qualifier", o);
    }

    @Test
    void annuler_une_absence_deja_qualifiee_exige_le_droit_de_correction() throws Exception {
        UUID a = abs(1, P_AMRANI, jour(8)).statut("JUSTIFIEE").motif("MALADIE").commentaire("grippe").declaree().poser();
        UUID b = abs(2, P_BELKACEM, jour(8)).statut("NON_JUSTIFIEE").motif("NON_JUSTIFIEE").poser();
        UUID c = abs(3, P_CHIKHI, jour(8)).statut("JUSTIFIEE").motif("VOYAGE").poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("justifiee_par_secretaire", refus(annuler(secretaire(), a, "erreur")));
        o.set("justifiee_par_infirmier", refus(annuler(infirmier(), a, "erreur")));
        o.set("base_apres_refus", etat(CENTRE_A));
        o.set("justifiee_par_medecin", refus(annuler(medecin(), a, "erreur de declaration")));
        o.set("non_justifiee_par_admin", refus(annuler(admin(), b, "patient finalement present")));
        o.set("justifiee_par_medecin_et_infirmier_cumules", refus(annuler(as(U_MEDECIN, CENTRE_A, "MEDECIN", "INFIRMIER"), c,
                "cumul")));
        o.set("base_finale", etat(CENTRE_A));
        goldenNode("deja_qualifiee", o);
    }

    @Test
    void une_absence_rattrapee_ne_s_annule_pas_et_garde_sa_date_de_rattrapage_kb_abs_10() throws Exception {
        UUID a = abs(1, P_AMRANI, jour(9)).statut("RATTRAPEE").motif("MALADIE").commentaire("grippe").rattrapee(jour(5)).poser();
        UUID b = abs(2, P_BELKACEM, jour(9)).statut("RATTRAPEE").motif("VOYAGE").rattrapee(jour(5)).poser();
        UUID c = abs(3, P_CHIKHI, jour(9)).statut("RATTRAPEE").motif("VOYAGE").rattrapee(jour(5)).poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("rattrapee_par_secretaire", refus(annuler(secretaire(), c, "erreur")));
        o.set("rattrapee_par_admin", resume(annuler(admin(), a, "rattrapage invalide")));
        o.set("rattrapee_par_medecin", refus(annuler(medecin(), b, "rattrapage invalide")));
        o.set("base", etat(CENTRE_A));
        goldenNode("rattrapee_nouveau_code", o);
    }

    @Test
    void une_absence_annulee_est_definitive() throws Exception {
        UUID a = abs(1, P_AMRANI, jour(9)).statut("ANNULEE").commentaire("erreur").poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("annulee_par_admin", refus(annuler(admin(), a, "encore")));
        o.set("annulee_par_secretaire_droit_controle_avant", refus(annuler(secretaire(), a, "encore")));
        o.set("annulee_qualification", refus(qualifier(admin(), a, "VOYAGE", "x")));
        o.set("annulee_rattrapage_sans_seance", refus(rattraper(admin(), a, jour(2))));
        o.set("base", etat(CENTRE_A));
        goldenNode("annulation_definitive_nouveau_code", o);
        golden("refus_deja_annulee", annuler(admin(), a, "encore"));
        golden("refus_correction_interdite", annuler(secretaire(), abs(2, P_BELKACEM, jour(9)).statut("JUSTIFIEE").motif("VOYAGE")
                .poser(), "x"));
    }

    @Test
    void le_commentaire_est_obligatoire_et_remplace_le_commentaire_precedent() throws Exception {
        UUID a = abs(1, P_AMRANI, jour(9)).statut("JUSTIFIEE").motif("MALADIE").commentaire("commentaire d'origine").poser();
        String base = ABSENCES + "/" + a + "/annulation";
        ObjectNode o = mapper.createObjectNode();
        o.set("commentaire_absent", refus(doPut(base, "{}", admin())));
        o.set("commentaire_nul", refus(doPut(base, json("{'commentaire':null}"), admin())));
        o.set("commentaire_vide", refus(doPut(base, json("{'commentaire':''}"), admin())));
        o.set("commentaire_blanc", refus(doPut(base, json("{'commentaire':'   '}"), admin())));
        o.set("json_illisible", refus(doPut(base, "{commentaire", admin())));
        o.set("identifiant_non_uuid", refus(doPut(ABSENCES + "/abc/annulation", json("{'commentaire':'x'}"), admin())));
        o.set("base_apres_refus", etat(CENTRE_A));
        o.set("commentaire_de_1001_caracteres", refus(annuler(admin(), a, "y".repeat(1001))));
        o.set("base_apres_commentaire_trop_long", etat(CENTRE_A));
        o.set("commentaire_valide", refus(annuler(admin(), a, "nouveau commentaire")));
        o.set("base_finale", etat(CENTRE_A));
        goldenNode("commentaire_nouveau_code", o);
    }

    @Test
    void le_droit_de_correction_precede_la_periode_facturee_pour_l_annulation() throws Exception {
        UUID a = abs(1, P_AMRANI, jour(12)).poser();
        UUID b = abs(2, P_BELKACEM, jour(12)).statut("JUSTIFIEE").motif("VOYAGE").poser();
        facture(CENTRE_A, P_AMRANI, "FAC-1", jour(15), jour(10));
        facture(CENTRE_A, P_BELKACEM, "FAC-2", jour(15), jour(10));
        ObjectNode o = mapper.createObjectNode();
        o.set("a_qualifier_par_admin", refus(annuler(admin(), a, "x")));
        o.set("justifiee_par_secretaire_correction_interdite_prime_sur_periode_facturee", refus(annuler(secretaire(), b, "x")));
        o.set("base", etat(CENTRE_A));
        goldenNode("periode_facturee_nouveau_code", o);
    }

    @Test
    void une_absence_d_un_autre_centre_ou_inconnue_est_introuvable() throws Exception {
        UUID duB = abs(1, P_CENTRE_B, jour(8)).centre(CENTRE_B).poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("absence_du_centre_B_par_admin_A", refus(annuler(admin(), duB, "x")));
        o.set("absence_inconnue", refus(annuler(admin(), UUID.randomUUID(), "x")));
        o.set("absence_du_centre_B_par_admin_B", refus(annuler(adminB(), duB, "x")));
        o.set("base_B", etat(CENTRE_B));
        goldenNode("introuvable_et_autre_centre_nouveau_code", o);
    }

    @Test
    void l_annulation_diffuse_une_saisie_infirmier_au_medecin_du_centre() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(8)).poser();
        annuler(admin(), id, "erreur");
        annuler(admin(), id, "encore");
        ObjectNode o = mapper.createObjectNode();
        o.set("diffusions", diffusionsCapturees());
        goldenNode("diffusion_temps_reel", o);
    }
}
