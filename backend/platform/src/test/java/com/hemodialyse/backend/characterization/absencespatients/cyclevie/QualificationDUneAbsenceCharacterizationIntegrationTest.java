package com.hemodialyse.backend.characterization.absencespatients.cyclevie;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Slice absences-patients-a2-cycle-de-vie, {@code PUT /api/v1/absences-patients/{id}/qualification}. Controles dans l'ordre :
 * corps valide, centre de la session, absence du centre, periode facturee, droit de correction (ADMIN ou MEDECIN pour une
 * absence deja qualifiee, y compris rattrapee ou annulee), absence modifiable, motif, commentaire (AUTRE, requalification).
 */
class QualificationDUneAbsenceCharacterizationIntegrationTest extends CycleDeVieAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "qualification";
    }

    @Test
    void qualifier_une_absence_a_qualifier_la_passe_justifiee_ou_non_justifiee_et_trace_le_qualificateur() throws Exception {
        UUID justifiee = abs(1, P_AMRANI, jour(8)).poser();
        UUID injustifiee = abs(2, P_BELKACEM, jour(8)).poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("justifiee", resume(qualifier(medecin(), justifiee, "VOYAGE", "mariage")));
        o.set("non_justifiee", resume(qualifier(infirmier(), injustifiee, "NON_JUSTIFIEE", null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("nominale", o);
    }

    @Test
    void les_quatre_profils_peuvent_qualifier_une_absence_a_qualifier() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        Map<String, RequestPostProcessor> profils = new LinkedHashMap<>();
        profils.put("ADMIN", admin());
        profils.put("SECRETAIRE", secretaire());
        profils.put("MEDECIN", medecin());
        profils.put("INFIRMIER", infirmier());
        int rang = 1;
        for (var p : profils.entrySet()) {
            UUID id = abs(rang, P_AMRANI, jour(10 + rang)).poser();
            Resp r = qualifier(p.getValue(), id, "MALADIE", null);
            o.set(p.getKey(), refus(r));
            rang++;
        }
        o.set("base", etat(CENTRE_A));
        goldenNode("profils_sur_absence_a_qualifier", o);
    }

    @Test
    void requalifier_une_absence_deja_qualifiee_exige_un_commentaire_et_le_droit_de_correction() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        UUID id = abs(1, P_AMRANI, jour(8)).statut("JUSTIFIEE").motif("MALADIE").commentaire("note d'origine").declaree().poser();
        o.set("secretaire_sans_commentaire", refus(qualifier(secretaire(), id, "VOYAGE", null)));
        o.set("secretaire_avec_commentaire", refus(qualifier(secretaire(), id, "VOYAGE", "correction")));
        o.set("infirmier_avec_commentaire", refus(qualifier(infirmier(), id, "VOYAGE", "correction")));
        o.set("medecin_et_infirmier_cumules", refus(qualifier(as(U_MEDECIN, CENTRE_A, "MEDECIN", "INFIRMIER"), id, "TRANSPORT",
                "cumul")));
        o.set("base_apres_refus_et_cumul", etat(CENTRE_A));
        o.set("admin_sans_commentaire", refus(qualifier(admin(), id, "VOYAGE", null)));
        o.set("admin_commentaire_blanc", refus(qualifier(admin(), id, "VOYAGE", "   ")));
        o.set("admin_avec_commentaire", resume(qualifier(admin(), id, "NON_JUSTIFIEE", "  absence non excusee  ")));
        o.set("medecin_avec_commentaire_meme_motif", refus(qualifier(medecin(), id, "NON_JUSTIFIEE", "reconfirme")));
        o.set("base_finale", etat(CENTRE_A));
        goldenNode("requalification", o);
    }

    @Test
    void le_motif_autre_exige_un_commentaire_meme_a_la_premiere_qualification() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(8)).poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("autre_sans_commentaire", refus(qualifier(admin(), id, "AUTRE", null)));
        o.set("autre_commentaire_blanc", refus(qualifier(admin(), id, "AUTRE", "  ")));
        o.set("base_apres_refus", etat(CENTRE_A));
        o.set("autre_avec_commentaire", refus(qualifier(admin(), id, "AUTRE", "precision")));
        o.set("base_finale", etat(CENTRE_A));
        goldenNode("motif_autre", o);
    }

    @Test
    void la_qualification_sans_commentaire_garde_le_commentaire_present_kb_abs_14() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(8)).commentaire("note initiale de detection").poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("commentaire_avant", mapper.createObjectNode().put("valeur", etat(CENTRE_A).get(0).path("commentaire").asString()));
        o.set("qualification_sans_commentaire", refus(qualifier(admin(), id, "VOYAGE", null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("commentaire_conserve_nouveau_code", o);
    }

    @Test
    void une_absence_rattrapee_ou_annulee_n_est_plus_modifiable_et_le_droit_de_correction_est_controle_avant() throws Exception {
        UUID rattrapee = abs(1, P_AMRANI, jour(9)).statut("RATTRAPEE").motif("MALADIE").rattrapee(jour(5)).poser();
        UUID annulee = abs(2, P_BELKACEM, jour(9)).statut("ANNULEE").commentaire("erreur").poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("rattrapee_par_admin", refus(qualifier(admin(), rattrapee, "VOYAGE", "x")));
        o.set("rattrapee_par_medecin", refus(qualifier(medecin(), rattrapee, "VOYAGE", "x")));
        o.set("rattrapee_par_secretaire", refus(qualifier(secretaire(), rattrapee, "VOYAGE", "x")));
        o.set("annulee_par_admin", refus(qualifier(admin(), annulee, "VOYAGE", "x")));
        o.set("annulee_par_infirmier", refus(qualifier(infirmier(), annulee, "VOYAGE", "x")));
        o.set("base", etat(CENTRE_A));
        goldenNode("non_modifiable", o);
    }

    @Test
    void les_refus_complets_gardent_le_code_metier_le_titre_et_le_message() throws Exception {
        UUID a = abs(1, P_AMRANI, jour(8)).statut("JUSTIFIEE").motif("MALADIE").poser();
        UUID b = abs(2, P_BELKACEM, jour(8)).statut("ANNULEE").commentaire("x").poser();
        golden("refus_correction_interdite", qualifier(secretaire(), a, "VOYAGE", "c"));
        golden("refus_commentaire_requis", qualifier(admin(), a, "VOYAGE", null));
        golden("refus_non_modifiable", qualifier(admin(), b, "VOYAGE", "c"));
        golden("refus_absence_introuvable_nouveau_code", qualifier(admin(), UUID.randomUUID(), "VOYAGE", "c"));
    }

    @Test
    void motif_obligatoire_et_illisible_corps_invalide() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(8)).poser();
        String base = ABSENCES + "/" + id + "/qualification";
        ObjectNode o = mapper.createObjectNode();
        o.set("motif_absent", refus(doPut(base, "{}", admin())));
        o.set("motif_nul", refus(doPut(base, json("{'motif':null}"), admin())));
        o.set("motif_vide", refus(doPut(base, json("{'motif':''}"), admin())));
        o.set("motif_blanc", refus(doPut(base, json("{'motif':'  '}"), admin())));
        o.set("motif_inconnu", refus(doPut(base, json("{'motif':'PARESSE'}"), admin())));
        o.set("motif_en_minuscules", refus(doPut(base, json("{'motif':'voyage'}"), admin())));
        o.set("json_illisible", refus(doPut(base, "{motif", admin())));
        o.set("identifiant_non_uuid", refus(doPut(ABSENCES + "/abc/qualification", json("{'motif':'VOYAGE'}"), admin())));
        o.set("base", etat(CENTRE_A));
        goldenNode("corps_invalide_nouveau_code", o);
    }

    @Test
    void la_periode_facturee_ferme_la_qualification_apres_le_droit_et_l_etat() throws Exception {
        UUID a = abs(1, P_AMRANI, jour(12)).poser();
        UUID b = abs(2, P_BELKACEM, jour(12)).statut("RATTRAPEE").motif("MALADIE").rattrapee(jour(5)).poser();
        facture(CENTRE_A, P_AMRANI, "FAC-1", jour(15), jour(10));
        facture(CENTRE_A, P_BELKACEM, "FAC-2", jour(15), jour(10));
        ObjectNode o = mapper.createObjectNode();
        o.set("a_qualifier_par_admin", refus(qualifier(admin(), a, "VOYAGE", null)));
        o.set("rattrapee_par_admin_non_modifiable_prime_sur_periode_facturee", refus(qualifier(admin(), b, "VOYAGE", "x")));
        o.set("a_qualifier_par_infirmier", refus(qualifier(infirmier(), a, "VOYAGE", null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("periode_facturee_nouveau_code", o);
    }

    @Test
    void une_absence_d_un_autre_centre_ou_inconnue_est_introuvable() throws Exception {
        UUID duB = abs(1, P_CENTRE_B, jour(8)).centre(CENTRE_B).poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("absence_du_centre_B_par_admin_A", refus(qualifier(admin(), duB, "VOYAGE", "c")));
        o.set("absence_inconnue", refus(qualifier(admin(), UUID.randomUUID(), "VOYAGE", "c")));
        o.set("absence_du_centre_B_par_admin_B", refus(qualifier(adminB(), duB, "VOYAGE", "c")));
        o.set("base_B", etat(CENTRE_B));
        goldenNode("introuvable_et_autre_centre_nouveau_code", o);
    }

    @Test
    void la_reponse_d_une_qualification_n_est_jamais_en_retard_et_ne_porte_pas_le_nom_du_patient() throws Exception {
        UUID retard = abs(1, P_AMRANI, jour(4)).poser();
        UUID borne = abs(2, P_BELKACEM, jour(3)).poser();
        ObjectNode o = mapper.createObjectNode();
        o.set("qualification_d_une_absence_a_qualifier_depuis_4_jours", resume(qualifier(admin(), retard, "TRANSPORT", null)));
        o.set("qualification_d_une_absence_a_qualifier_depuis_3_jours", resume(qualifier(admin(), borne, "TRANSPORT", null)));
        goldenNode("reponse_complete", o);
    }

    @Test
    void la_qualification_diffuse_une_saisie_infirmier_au_medecin_du_centre() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(8)).poser();
        qualifier(secretaire(), id, "MALADIE", null);
        qualifier(secretaire(), UUID.randomUUID(), "MALADIE", null);
        ObjectNode o = mapper.createObjectNode();
        o.set("diffusions", diffusionsCapturees());
        goldenNode("diffusion_temps_reel", o);
    }
}
