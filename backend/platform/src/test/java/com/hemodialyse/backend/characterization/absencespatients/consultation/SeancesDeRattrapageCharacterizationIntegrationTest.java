package com.hemodialyse.backend.characterization.absencespatients.consultation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * {@code GET /api/v1/absences-patients/{id}/seances-rattrapage} : dates des seances realisees du patient apres la seance manquee et
 * jusqu'a aujourd'hui (UTC), sans celles qui rattrapent deja une absence rattrapee ; aucune verification du statut de l'absence.
 */
class SeancesDeRattrapageCharacterizationIntegrationTest extends ConsultationAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "seances-rattrapage";
    }

    /**
     * Absence 1 (AMRANI, J-10, a qualifier) ; seances d'AMRANI : meme jour, VALIDEE, SIGNEE, FACTUREE, CREE, ABSENT, deja utilisee
     * (J-2, par l'absence rattrapee 2), aujourd'hui, future, deux seances le meme jour ; seance d'un autre patient et du centre B.
     */
    private void jeuDeDonnees() {
        abs(1, P_AMRANI, jour(10)).poser();
        abs(2, P_AMRANI, jour(20)).statut("RATTRAPEE").motif("MALADIE").rattrapee(jour(2)).poser();
        abs(3, P_AMRANI, jour(30)).statut("ANNULEE").commentaire("Erreur").rattrapee(jour(5)).poser();
        seance(P_AMRANI, CENTRE_A, jour(10), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, jour(9), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, jour(9), "SIGNEE");
        seance(P_AMRANI, CENTRE_A, jour(7), "SIGNEE");
        seance(P_AMRANI, CENTRE_A, jour(5), "FACTUREE");
        seance(P_AMRANI, CENTRE_A, jour(4), "CREE");
        seance(P_AMRANI, CENTRE_A, jour(3), "ABSENT");
        seance(P_AMRANI, CENTRE_A, jour(2), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, jour(0), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, jour(-2), "VALIDEE");
        seance(P_BELKACEM, CENTRE_A, jour(6), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, jour(15), "VALIDEE");
        seance(P_CENTRE_B, CENTRE_B, jour(6), "VALIDEE");
        abs(4, P_BELKACEM, jour(12)).statut("JUSTIFIEE").motif("MALADIE").poser();
        abs(5, P_CHIKHI, jour(12)).poser();
        abs(6, P_DAHMANI, jour(0)).poser();
        abs(7, P_DAHMANI, jour(-3)).poser();
        abs(8, P_CENTRE_B, jour(12)).centre(CENTRE_B).poser();
        seance(P_DAHMANI, CENTRE_A, jour(0), "VALIDEE");
        seance(P_DAHMANI, CENTRE_A, jour(-3), "VALIDEE");
        seance(P_CENTRE_B, CENTRE_B, jour(1), "VALIDEE");
    }

    private String route(int rang) {
        return ABSENCES + "/" + idAbsence(rang) + "/seances-rattrapage";
    }

    @Test
    void les_seances_proposees_sont_les_seances_realisees_apres_la_seance_manquee_jusqu_a_aujourd_hui_dans_l_ordre_chronologique() throws Exception {
        jeuDeDonnees();

        golden("absence_a_qualifier_du_patient_avec_seances", doGet(route(1), adminA()));
    }

    @Test
    void une_seance_qui_rattrape_deja_une_absence_rattrapee_est_exclue_meme_pour_cette_absence_et_une_annulee_ne_reserve_rien() throws Exception {
        jeuDeDonnees();

        golden("absence_rattrapee_ne_propose_pas_sa_propre_date_nouveau_code", doGet(route(2), adminA()));
        golden("absence_annulee_est_proposee_comme_une_autre_nouveau_code", doGet(route(3), adminA()));
    }

    @Test
    void aucune_verification_du_statut_ni_de_la_periode_facturee_et_les_bornes_du_jour_sont_incluses_ou_exclues() throws Exception {
        jeuDeDonnees();

        golden("absence_justifiee_d_un_patient_sans_autre_seance", doGet(route(4), adminA()));
        golden("absence_d_un_patient_sans_seance", doGet(route(5), adminA()));
        golden("absence_du_jour_pas_de_seance_apres_aujourd_hui", doGet(route(6), adminA()));
        golden("absence_future_aucune_seance", doGet(route(7), adminA()));
    }

    @Test
    void une_absence_inconnue_ou_d_un_autre_centre_est_introuvable_et_un_identifiant_illisible_est_refuse() throws Exception {
        jeuDeDonnees();

        golden("absence_inconnue_nouveau_code", doGet(ABSENCES + "/" + UUID.fromString("99998100-0000-0000-0000-00000000ffff") + "/seances-rattrapage", adminA()));
        golden("absence_d_un_autre_centre_nouveau_code", doGet(route(8), adminA()));
        golden("absence_vue_depuis_son_centre", doGet(route(8), adminB()));
        golden("identifiant_non_uuid_nouveau_code", doGet(ABSENCES + "/abc/seances-rattrapage", adminA()));
        golden("centre_demande_different_de_la_session", doGet(route(1) + "?centerId=" + CENTRE_B, adminA()));
    }
}
