package com.hemodialyse.backend.characterization.facturation.reglements;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F4 : autorisations par profil sur les cinq routes (avec le filtre {@code RoleScopeFilter}), isolation de centre
 * constatée, routes absentes (annulation, suppression, modification d'un règlement) et parcours complet.
 */
class AutorisationsEtParcoursCharacterizationIntegrationTest extends ReglementsCharacterizationSupport {

    @Override
    protected String theme() {
        return "autorisations";
    }

    private void toutesLesRoutes(String prefixe, UUID facture, RequestPostProcessor qui) throws Exception {
        golden(prefixe + "_recherche", doGet("?centerId=" + CENTRE_A + "&year=2026", qui));
        golden(prefixe + "_dashboard", doGet("/dashboard?centerId=" + CENTRE_A + "&year=2026", qui));
        golden(prefixe + "_export", doGet("/export?centerId=" + CENTRE_A + "&year=2026&format=csv", qui));
        golden(prefixe + "_historique", doGet("/" + facture + "/paiements?centerId=" + CENTRE_A, qui));
        golden(prefixe + "_enregistrement", doPost("/" + facture + "/paiements", corps(CENTRE_A, "10.00", "2026-02-01", "u"), qui));
    }

    @Test
    void un_administrateur_accede_aux_cinq_routes() throws Exception {
        toutesLesRoutes("admin", factureMille(), adminA());
    }

    @Test
    void une_secretaire_accede_aux_cinq_routes() throws Exception {
        toutesLesRoutes("secretaire", factureMille(), secretaireA());
    }

    @Test
    void un_medecin_seul_est_refuse_par_le_filtre_des_profils_sur_les_cinq_routes() throws Exception {
        UUID facture = factureMille();

        toutesLesRoutes("medecin", facture, as(CENTRE_A, "MEDECIN"));

        assertEquals(0, compter("facture_reglements", CENTRE_A));
    }

    @Test
    void un_infirmier_seul_est_refuse_par_la_securite_des_methodes() throws Exception {
        UUID facture = factureMille();

        toutesLesRoutes("infirmier", facture, as(CENTRE_A, "INFIRMIER"));

        assertEquals(0, compter("facture_reglements", CENTRE_A));
    }

    @Test
    void un_medecin_qui_est_aussi_secretaire_est_servi() throws Exception {
        UUID facture = factureMille();

        golden("medecin_secretaire_enregistrement",
                doPost("/" + facture + "/paiements", corps(CENTRE_A, "10.00", "2026-02-01", "u"), as(CENTRE_A, "MEDECIN", "SECRETAIRE")));
    }

    @Test
    void les_comptes_direction_et_superadmin_sont_refuses_par_le_filtre() throws Exception {
        UUID facture = factureMille();

        golden("direction_recherche", doGet("?centerId=" + CENTRE_A + "&year=2026", as(CENTRE_A, "DIRECTION")));
        golden("superadmin_enregistrement",
                doPost("/" + facture + "/paiements", corps(CENTRE_A, "10.00", "2026-02-01", "u"), as(CENTRE_A, "SUPERADMIN")));
    }

    @Test
    void sans_authentification_toutes_les_routes_sont_refusees() throws Exception {
        UUID facture = factureMille();

        golden("anonyme_recherche", doGetAnonyme("?centerId=" + CENTRE_A + "&year=2026"));
        golden("anonyme_enregistrement", doPostAnonyme("/" + facture + "/paiements", corps(CENTRE_A, "10.00", "2026-02-01", "u")));
        assertEquals(0, compter("facture_reglements", CENTRE_A));
    }

    @Test
    void un_administrateur_du_centre_a_agit_sur_le_centre_b_en_le_nommant_dans_la_requete() throws Exception {
        // KB-FAC-37 : aucune comparaison entre le centre de la session et le centre de la requête (lecture comme écriture).
        UUID factureB = facture(CENTRE_B, P_CENTRE_B, "FAC-B-0001", "2026-01-31", "500.00", PAYEUR_B, AGENCE_B);

        // Nouveau code F4 (KB-FAC-37 corrigé) : 403, le centre du corps n'est pas celui de la session.
        golden("a_ecrit_chez_b_nouveau_code", doPost("/" + factureB + "/paiements", corps(CENTRE_B, "500.00", "2026-02-01", "u"), adminA()));
        golden("a_lit_le_dashboard_de_b_nouveau_code", doGet("/dashboard?centerId=" + CENTRE_B + "&year=2026", adminA()));
        golden("a_exporte_b_nouveau_code", doGet("/export?centerId=" + CENTRE_B + "&year=2026&format=csv", adminA()));
        // Nouveau code F4 : rien n'a été écrit au centre B (le legacy y avait enregistré le règlement : 1).
        assertEquals(0, compter("facture_reglements", CENTRE_B));
    }

    @Test
    void un_centre_inconnu_donne_des_resultats_vides_en_lecture_et_un_refus_en_ecriture() throws Exception {
        UUID facture = factureMille();
        UUID inconnu = UUID.fromString("99997000-0000-0000-0000-0000000000ff");

        // Nouveau code F4 (KB-FAC-37 corrigé) : un centre autre que celui de la session, inconnu ou non, donne 403.
        golden("centre_inconnu_recherche_nouveau_code", doGet("?centerId=" + inconnu + "&year=2026", adminA()));
        // Nouveau code F4 (KB-FAC-37 corrigé) : 403 (le legacy répondait 400 « facture introuvable »).
        golden("centre_inconnu_enregistrement_nouveau_code", doPost("/" + facture + "/paiements", corps(inconnu, "10.00", "2026-02-01", "u"), adminA()));
    }

    @Test
    void les_routes_d_annulation_de_suppression_et_de_modification_d_un_reglement_n_existent_pas() throws Exception {
        // Un règlement est immuable et non annulable : la correction passe par un règlement négatif.
        UUID facture = factureMille();
        enregistrer(facture, corps(CENTRE_A, "100.00", "2026-02-01", "u"));
        UUID paiement = jdbc.queryForObject("SELECT id FROM facture_reglements", UUID.class);

        var mvcDelete = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete(java.net.URI.create(BASE + "/" + facture + "/paiements/" + paiement)).with(adminA())).andReturn().getResponse();
        var mvcPut = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(java.net.URI.create(BASE + "/" + facture + "/paiements/" + paiement))
                .contentType("application/json").content(corps(CENTRE_A, "5.00", "2026-02-01", "u")).with(adminA())).andReturn().getResponse();
        var mvcAnnule = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(java.net.URI.create(BASE + "/" + facture + "/paiements/" + paiement + "/annuler")).with(adminA())).andReturn().getResponse();
        var mvcGetUn = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(java.net.URI.create(BASE + "/" + facture)).with(adminA())).andReturn().getResponse();

        goldenNode("routes_absentes", mapper.createObjectNode()
                .put("delete", mvcDelete.getStatus()).put("put", mvcPut.getStatus())
                .put("annuler", mvcAnnule.getStatus()).put("get_une_facture", mvcGetUn.getStatus()));
        assertEquals(1, compter("facture_reglements", CENTRE_A));
    }

    @Test
    void parcours_complet_de_la_facture_non_reglee_au_trop_percu_rembourse() throws Exception {
        UUID facture = factureMille();
        golden("parcours_1_recherche_initiale", doGet("?centerId=" + CENTRE_A + "&year=2026&month=1", adminA()));

        regler(facture, "400.00", "2026-02-10");
        regler(facture, "600.00", "2026-02-12");
        regler(facture, "100.00", "2026-02-15");
        golden("parcours_2_apres_trop_percu", doGet("?centerId=" + CENTRE_A + "&year=2026&month=1", adminA()));
        golden("parcours_3_dashboard_trop_percu", doGet("/dashboard?centerId=" + CENTRE_A + "&year=2026&month=1", adminA()));

        regler(facture, "-100.00", "2026-02-20");
        // Nouveau code F4 : l'auteur de chaque règlement est l'utilisateur de la session (identifiant), non le userId du corps.
        golden("parcours_4_historique_nouveau_code", doGet("/" + facture + "/paiements?centerId=" + CENTRE_A, adminA()));
        golden("parcours_5_dashboard_final", doGet("/dashboard?centerId=" + CENTRE_A + "&year=2026&month=1", adminA()));
        // Nouveau code F4 : auteur = utilisateur de la session (événements) ; remboursement = écriture de sens inverse (écritures).
        goldenEvenements("parcours_6_evenements_nouveau_code");
        goldenEcritures("parcours_7_ecritures_nouveau_code", CENTRE_A);
    }

    @Test
    void le_json_des_montants_garde_deux_decimales_comme_des_nombres() throws Exception {
        UUID facture = factureMille();

        Resp r = regler(facture, "400.5", "2026-02-10");

        assertEquals(true, r.raw().contains("\"montantFacture\":1000.00"));
        assertEquals(true, r.raw().contains("\"montantRegle\":400.50"));
        assertEquals(true, r.raw().contains("\"reste\":599.50"));
        assertEquals(true, r.raw().contains("\"tropPercu\":0.00"));
    }
}
