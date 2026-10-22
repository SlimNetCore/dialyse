package com.hemodialyse.backend.characterization.absencespatients.cyclevie;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * Slice absences-patients-a2-cycle-de-vie, {@code PUT /api/v1/absences-patients/{id}/rattrapage}. Controles dans l'ordre :
 * corps valide, centre, absence du centre, periode facturee, seance realisee a la date de rattrapage, date deja utilisee par une
 * autre absence rattrapee du patient, absence modifiable, date posterieure a la seance manquee, date non future. Tous les
 * profils d'acces (ADMIN, SECRETAIRE, MEDECIN, INFIRMIER) rattrapent, qualifiee ou non (B7).
 */
class RattrapageDUneAbsenceCharacterizationIntegrationTest extends CycleDeVieAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "rattrapage";
    }

    @Test
    void rattraper_une_absence_a_qualifier_la_passe_rattrapee_sans_toucher_ni_au_motif_ni_a_la_valeur() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(8)).poser();
        seance(P_AMRANI, CENTRE_A, jour(5), "VALIDEE");
        ObjectNode o = mapper.createObjectNode();
        o.set("reponse", resume(rattraper(admin(), id, jour(5))));
        o.set("base", etat(CENTRE_A));
        goldenNode("nominale_nouveau_code", o);
    }

    @Test
    void le_rattrapage_exige_le_droit_de_correction_meme_sur_une_absence_qualifiee() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        UUID a = abs(1, P_AMRANI, jour(9)).statut("JUSTIFIEE").motif("MALADIE").commentaire("grippe").declaree().poser();
        UUID b = abs(2, P_BELKACEM, jour(9)).statut("NON_JUSTIFIEE").motif("NON_JUSTIFIEE").poser();
        UUID c = abs(3, P_CHIKHI, jour(9)).statut("JUSTIFIEE").motif("VOYAGE").poser();
        UUID d = abs(4, P_DAHMANI, jour(9)).statut("JUSTIFIEE").motif("VOYAGE").poser();
        for (UUID p : new UUID[]{P_AMRANI, P_BELKACEM, P_CHIKHI, P_DAHMANI}) {
            seance(p, CENTRE_A, jour(3), "SIGNEE");
        }
        o.set("justifiee_par_infirmier", refus(rattraper(infirmier(), a, jour(3))));
        o.set("non_justifiee_par_secretaire", refus(rattraper(secretaire(), b, jour(3))));
        o.set("justifiee_par_medecin", refus(rattraper(medecin(), c, jour(3))));
        o.set("justifiee_par_admin", refus(rattraper(admin(), d, jour(3))));
        o.set("base", etat(CENTRE_A));
        goldenNode("profils_sur_absence_qualifiee_nouveau_code", o);
    }

    @Test
    void la_seance_de_rattrapage_doit_etre_realisee_et_du_meme_patient_et_du_meme_centre() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(9)).poser();
        seance(P_AMRANI, CENTRE_A, jour(7), "CREE");
        seance(P_AMRANI, CENTRE_A, jour(6), "ABSENT");
        seance(P_AMRANI, CENTRE_A, jour(5), "BROUILLON");
        seance(P_BELKACEM, CENTRE_A, jour(4), "VALIDEE");
        seance(P_CENTRE_B, CENTRE_B, jour(3), "VALIDEE");
        ObjectNode o = mapper.createObjectNode();
        o.set("seance_creee", refus(rattraper(admin(), id, jour(7))));
        o.set("seance_absente", refus(rattraper(admin(), id, jour(6))));
        o.set("seance_brouillon", refus(rattraper(admin(), id, jour(5))));
        o.set("seance_d_un_autre_patient", refus(rattraper(admin(), id, jour(4))));
        o.set("seance_d_un_autre_centre", refus(rattraper(admin(), id, jour(3))));
        o.set("aucune_seance", refus(rattraper(admin(), id, jour(2))));
        o.set("base", etat(CENTRE_A));
        goldenNode("seance_de_rattrapage", o);
    }

    @Test
    void les_statuts_realises_valident_ou_non_le_rattrapage_selon_la_seance() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        String[] statuts = {"VALIDEE", "SIGNEE", "FACTUREE"};
        for (int i = 0; i < statuts.length; i++) {
            UUID id = abs(i + 1, P_AMRANI, jour(20 + i)).poser();
            seance(P_AMRANI, CENTRE_A, jour(4 + i), statuts[i]);
            o.set("seance_" + statuts[i], refus(rattraper(admin(), id, jour(4 + i))));
        }
        o.set("base", etat(CENTRE_A));
        goldenNode("statuts_de_seance_realisee", o);
    }

    @Test
    void la_date_de_rattrapage_doit_suivre_la_seance_manquee_et_ne_pas_etre_future() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(6)).poser();
        UUID id2 = abs(2, P_BELKACEM, jour(6)).poser();
        seance(P_AMRANI, CENTRE_A, jour(6), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, jour(8), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, jour(-2), "VALIDEE");
        seance(P_BELKACEM, CENTRE_A, jour(0), "VALIDEE");
        ObjectNode o = mapper.createObjectNode();
        o.set("meme_jour_que_l_absence", refus(rattraper(admin(), id, jour(6))));
        o.set("avant_l_absence", refus(rattraper(admin(), id, jour(8))));
        o.set("dans_le_futur", refus(rattraper(admin(), id, jour(-2))));
        o.set("aujourd_hui", refus(rattraper(admin(), id2, jour(0))));
        o.set("base", etat(CENTRE_A));
        goldenNode("bornes_de_date", o);
    }

    @Test
    void une_seance_ne_rattrape_qu_une_absence_du_patient_et_la_date_reste_prise_car_une_rattrapee_ne_s_annule_pas() throws Exception {
        UUID a = abs(1, P_AMRANI, jour(9)).poser();
        UUID b = abs(2, P_AMRANI, jour(8)).poser();
        UUID c = abs(3, P_AMRANI, jour(7)).poser();
        UUID autrePatient = abs(4, P_BELKACEM, jour(9)).poser();
        seance(P_AMRANI, CENTRE_A, jour(4), "VALIDEE");
        seance(P_BELKACEM, CENTRE_A, jour(4), "VALIDEE");
        ObjectNode o = mapper.createObjectNode();
        o.set("premiere", refus(rattraper(admin(), a, jour(4))));
        o.set("deuxieme_meme_seance", refus(rattraper(admin(), b, jour(4))));
        o.set("autre_patient_meme_jour", refus(rattraper(admin(), autrePatient, jour(4))));
        o.set("annulation_de_la_premiere_rattrapee_refusee", refus(annuler(admin(), a, "rattrapage saisi par erreur")));
        o.set("deuxieme_apres_refus_d_annulation_de_la_premiere", refus(rattraper(admin(), b, jour(4))));
        o.set("troisieme_meme_seance", refus(rattraper(admin(), c, jour(4))));
        o.set("base", etat(CENTRE_A));
        goldenNode("unicite_de_la_seance_nouveau_code", o);
    }

    @Test
    void le_controle_du_statut_precede_celui_de_la_seance_de_l_unicite_et_des_dates() throws Exception {
        UUID rattrapee = abs(1, P_AMRANI, jour(9)).statut("RATTRAPEE").motif("MALADIE").rattrapee(jour(4)).poser();
        UUID annulee = abs(2, P_AMRANI, jour(8)).statut("ANNULEE").commentaire("x").poser();
        seance(P_AMRANI, CENTRE_A, jour(4), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        ObjectNode o = mapper.createObjectNode();
        o.set("rattrapee_vers_une_date_sans_seance", refus(rattraper(admin(), rattrapee, jour(2))));
        o.set("rattrapee_vers_sa_propre_date", refus(rattraper(admin(), rattrapee, jour(4))));
        o.set("rattrapee_vers_une_autre_seance", refus(rattraper(admin(), rattrapee, jour(3))));
        o.set("annulee_vers_une_seance_libre", refus(rattraper(admin(), annulee, jour(3))));
        o.set("annulee_vers_une_date_sans_seance", refus(rattraper(admin(), annulee, jour(2))));
        o.set("base", etat(CENTRE_A));
        goldenNode("ordre_des_controles_nouveau_code", o);
    }

    @Test
    void les_refus_complets_gardent_le_code_metier_le_titre_et_le_message() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(6)).poser();
        UUID annulee = abs(2, P_BELKACEM, jour(6)).statut("ANNULEE").commentaire("x").poser();
        seance(P_AMRANI, CENTRE_A, jour(6), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, jour(8), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, jour(-2), "VALIDEE");
        seance(P_BELKACEM, CENTRE_A, jour(3), "VALIDEE");
        golden("refus_sans_seance", rattraper(admin(), id, jour(2)));
        golden("refus_rattrapage_anterieur", rattraper(admin(), id, jour(8)));
        golden("refus_date_future", rattraper(admin(), id, jour(-2)));
        golden("refus_non_modifiable", rattraper(admin(), annulee, jour(3)));
        golden("refus_absence_introuvable_nouveau_code", rattraper(admin(), UUID.randomUUID(), jour(3)));
    }

    @Test
    void corps_invalide() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(8)).poser();
        String base = ABSENCES + "/" + id + "/rattrapage";
        ObjectNode o = mapper.createObjectNode();
        o.set("date_absente", refus(doPut(base, "{}", admin())));
        o.set("date_nulle", refus(doPut(base, json("{'dateRattrapage':null}"), admin())));
        o.set("date_illisible", refus(doPut(base, json("{'dateRattrapage':'hier'}"), admin())));
        o.set("json_illisible", refus(doPut(base, "{date", admin())));
        o.set("identifiant_non_uuid", refus(doPut(ABSENCES + "/abc/rattrapage", json("{'dateRattrapage':'" + jour(3) + "'}"),
                admin())));
        o.set("base", etat(CENTRE_A));
        goldenNode("corps_invalide_nouveau_code", o);
    }

    @Test
    void la_periode_facturee_ferme_le_rattrapage_apres_le_droit_et_l_etat() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(12)).poser();
        facture(CENTRE_A, P_AMRANI, "FAC-1", jour(15), jour(10));
        ObjectNode o = mapper.createObjectNode();
        o.set("sans_seance", refus(rattraper(admin(), id, jour(2))));
        seance(P_AMRANI, CENTRE_A, jour(4), "VALIDEE");
        o.set("avec_seance", refus(rattraper(admin(), id, jour(4))));
        o.set("base", etat(CENTRE_A));
        goldenNode("periode_facturee", o);
    }

    @Test
    void une_absence_d_un_autre_centre_est_introuvable_et_la_seance_n_est_pas_cherchee_dans_l_autre_centre() throws Exception {
        UUID duB = abs(1, P_CENTRE_B, jour(8)).centre(CENTRE_B).poser();
        seance(P_CENTRE_B, CENTRE_B, jour(4), "VALIDEE");
        ObjectNode o = mapper.createObjectNode();
        o.set("absence_du_centre_B_par_admin_A", refus(rattraper(admin(), duB, jour(4))));
        o.set("absence_du_centre_B_par_admin_B", refus(rattraper(adminB(), duB, jour(4))));
        o.set("base_B", etat(CENTRE_B));
        goldenNode("autre_centre_nouveau_code", o);
    }

    @Test
    void le_rattrapage_diffuse_une_saisie_infirmier_au_medecin_du_centre() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(8)).poser();
        seance(P_AMRANI, CENTRE_A, jour(4), "VALIDEE");
        rattraper(medecin(), id, jour(4));
        rattraper(medecin(), id, jour(3));
        ObjectNode o = mapper.createObjectNode();
        o.set("diffusions", diffusionsCapturees());
        goldenNode("diffusion_temps_reel_nouveau_code", o);
    }
}
