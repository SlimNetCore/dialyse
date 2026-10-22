package com.hemodialyse.backend.characterization.absencespatients.cyclevie;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * Slice absences-patients-a2-cycle-de-vie : effets des ecritures sur les lectures de la slice A1 (liste, synthese, seances de
 * rattrapage), enchainement d'un cycle complet de bout en bout en HTTP, et traces (auteur, instant) de chaque etape.
 * La route {@code /semaine} n'est pas rejouee ici : sa semaine (dimanche a samedi) depend du jour d'execution ; elle est verrouillee
 * par les goldens ancres de A1 sur la meme table.
 */
class EffetsDesEcrituresSurLesLecturesCharacterizationIntegrationTest extends CycleDeVieAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "effets-sur-les-lectures";
    }

    private ObjectNode lectures(UUID absenceDuRattrapage) throws Exception {
        ObjectNode o = mapper.createObjectNode();
        o.set("liste", resume(doGet(ABSENCES, admin())));
        o.set("synthese", resume(doGet(ABSENCES + "/synthese", admin())));
        if (absenceDuRattrapage != null) {
            o.set("seances_de_rattrapage", resume(doGet(ABSENCES + "/" + absenceDuRattrapage + "/seances-rattrapage", admin())));
        }
        return o;
    }

    @Test
    void un_cycle_complet_declaration_qualification_rattrapage_annulation_vu_par_les_lectures() throws Exception {
        seance(P_AMRANI, CENTRE_A, jour(4), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, jour(2), "SIGNEE");
        Resp d1 = declarer(infirmier(), P_AMRANI, jour(8), null, null);
        Resp d2 = declarer(secretaire(), P_BELKACEM, jour(7), "MALADIE", "grippe");
        Resp d3 = declarer(admin(), P_CHIKHI, jour(1), null, null);
        UUID a1 = idCree(d1);
        UUID a2 = idCree(d2);
        UUID a3 = idCree(d3);
        ObjectNode o = mapper.createObjectNode();
        o.set("1_apres_les_trois_declarations", lectures(a1));
        o.set("2_qualification_de_a1", refus(qualifier(medecin(), a1, "VOYAGE", null)));
        o.set("2_lectures", lectures(a1));
        o.set("3_rattrapage_de_a1", refus(rattraper(medecin(), a1, jour(4))));
        o.set("3_lectures", lectures(a1));
        o.set("4_annulation_de_a3", refus(annuler(admin(), a3, "patient present")));
        o.set("4_lectures", lectures(a1));
        o.set("5_annulation_refusee_du_rattrapage_de_a1", refus(annuler(medecin(), a1, "rattrapage invalide")));
        o.set("5_lectures_apres_le_refus_d_annulation_du_rattrapage", lectures(a1));
        o.set("6_base", etat(CENTRE_A));
        o.set("7_absence_a2_declaree_avec_motif", refus(qualifier(admin(), a2, "VOYAGE", "correction du motif")));
        goldenNode("cycle_complet_nouveau_code", o);
    }

    @Test
    void la_declaration_est_visible_avec_le_nom_du_patient_et_l_indicateur_en_retard_dans_la_liste() throws Exception {
        declarer(admin(), P_AMRANI, jour(4), null, null);
        declarer(admin(), P_BELKACEM, jour(3), null, null);
        declarer(admin(), P_CHIKHI, jour(0), "AUTRE", "precision");
        ObjectNode o = mapper.createObjectNode();
        o.set("liste", resume(doGet(ABSENCES, admin())));
        o.set("synthese", resume(doGet(ABSENCES + "/synthese", admin())));
        goldenNode("liste_apres_declarations", o);
    }

    @Test
    void une_declaration_dans_le_centre_B_n_apparait_pas_dans_les_lectures_du_centre_A() throws Exception {
        declarer(adminB(), P_CENTRE_B, jour(3), "MALADIE", null);
        ObjectNode o = mapper.createObjectNode();
        o.set("liste_A", resume(doGet(ABSENCES, admin())));
        o.set("synthese_A", resume(doGet(ABSENCES + "/synthese", admin())));
        o.set("liste_B", resume(doGet(ABSENCES, adminB())));
        goldenNode("isolation_des_centres", o);
    }

    @Test
    void les_traces_de_chaque_etape_sont_ecrites_par_l_utilisateur_de_la_session() throws Exception {
        seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        Resp d = declarer(secretaire(), P_AMRANI, jour(8), null, null);
        UUID id = idCree(d);
        ObjectNode o = mapper.createObjectNode();
        o.set("apres_declaration_par_la_secretaire", etat(CENTRE_A));
        qualifier(infirmier(), id, "VOYAGE", null);
        o.set("apres_qualification_par_l_infirmier", etat(CENTRE_A));
        rattraper(medecin(), id, jour(3));
        o.set("apres_rattrapage_par_le_medecin", etat(CENTRE_A));
        annuler(admin(), id, "erreur");
        o.set("apres_refus_d_annulation_du_rattrapage_par_l_administrateur", etat(CENTRE_A));
        goldenNode("traces_nouveau_code", o);
    }
}
