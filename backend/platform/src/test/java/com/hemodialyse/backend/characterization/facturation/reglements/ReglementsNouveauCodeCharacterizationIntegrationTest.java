package com.hemodialyse.backend.characterization.facturation.reglements;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * F4, comportements AJOUTÉS par rapport au legacy (décisions du 2026-10-21, ADR 0033, à valider par le métier) : mode de
 * règlement et référence de paiement, clé d'idempotence, remboursement refusé au-delà du total réglé, écriture de caisse et de
 * remboursement à la reconstruction, auteur = utilisateur de la session. Aucun scénario du filet d'origine ne les couvrait
 * (le legacy ignorait le mode et la clé).
 */
class ReglementsNouveauCodeCharacterizationIntegrationTest extends ReglementsCharacterizationSupport {

    @Override
    protected String theme() {
        return "nouveau-code";
    }

    private String avec(String montant, String date, String complement) {
        return json("{'centerId':'" + CENTRE_A + "','montant':" + montant + ",'dateReglement':'" + date + "'," + complement + "}");
    }

    private Resp rebuild() throws Exception {
        var r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(java.net.URI.create("/api/v1/comptabilite/rebuild")).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json("{'centerId':'" + CENTRE_A + "','from':'2026-02-01','to':'2026-02-28'}")).with(adminA())).andReturn().getResponse();
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8), r.getContentAsByteArray(), null);
    }

    @Test
    void un_reglement_en_especes_est_ecrit_au_journal_de_caisse_et_garde_son_mode_et_sa_reference() throws Exception {
        UUID facture = factureMille();

        golden("especes_200", enregistrer(facture, avec("400.00", "2026-02-10", "'modeReglement':'ESPECES','referencePaiement':'RECU-12'")));

        golden("especes_historique", doGet("/" + facture + "/paiements?centerId=" + CENTRE_A, adminA()));
        goldenEvenements("especes_evenements");
        goldenEcritures("especes_ecritures", CENTRE_A);
    }

    @Test
    void un_reglement_par_cheque_est_ecrit_en_banque_et_un_reglement_en_caisse_au_journal_de_caisse() throws Exception {
        UUID facture = factureMille();

        enregistrer(facture, avec("100.00", "2026-02-10", "'modeReglement':'cheque','referencePaiement':'CHQ-001'"));
        enregistrer(facture, avec("200.00", "2026-02-11", "'modeReglement':'CAISSE'"));
        enregistrer(facture, avec("300.00", "2026-02-12", "'modeReglement':'BANQUE'"));

        assertEquals(List.of("BQ", "CA", "BQ"), jdbc.queryForList("SELECT journal_code FROM ecritures_comptables WHERE center_id = ? "
                + "ORDER BY date_ecriture", String.class, CENTRE_A));
        assertEquals("CHEQUE", jdbc.queryForObject("SELECT mode_reglement FROM facture_reglements WHERE center_id = ? AND montant = 100",
                String.class, CENTRE_A));
    }

    @Test
    void un_reglement_sans_mode_reste_comptabilise_en_banque_et_sans_champ_additif_dans_l_historique() throws Exception {
        UUID facture = factureMille();

        enregistrer(facture, corps(CENTRE_A, "400.00", "2026-02-10", "u"));

        golden("sans_mode_historique", doGet("/" + facture + "/paiements?centerId=" + CENTRE_A, adminA()));
        assertEquals("BQ", jdbc.queryForObject("SELECT journal_code FROM ecritures_comptables WHERE center_id = ?", String.class, CENTRE_A));
        assertNull(jdbc.queryForObject("SELECT mode_reglement FROM facture_reglements WHERE center_id = ?", String.class, CENTRE_A));
    }

    @Test
    void un_mode_inconnu_est_refuse_en_400_sans_rien_enregistrer() throws Exception {
        UUID facture = factureMille();

        golden("mode_inconnu_400", enregistrer(facture, avec("400.00", "2026-02-10", "'modeReglement':'BITCOIN'")));

        assertEquals(0, compter("facture_reglements", CENTRE_A));
        assertEquals(0, evenements.recus().size());
    }

    @Test
    void un_renvoi_avec_la_meme_cle_ne_cree_ni_doublon_ni_evenement_ni_ecriture() throws Exception {
        UUID facture = factureMille();
        String corps = avec("400.00", "2026-02-10", "'cleIdempotence':'envoi-0001'");

        Resp premier = enregistrer(facture, corps);
        Resp renvoi = enregistrer(facture, corps);

        assertEquals(200, premier.status());
        assertEquals(premier.raw(), renvoi.raw());
        golden("idempotence_renvoi", renvoi);
        assertEquals(1, compter("facture_reglements", CENTRE_A));
        assertEquals(1, evenements.recus().size());
        assertEquals(1, compter("ecritures_comptables", CENTRE_A));
    }

    @Test
    void la_cle_est_propre_a_la_facture_et_sans_cle_le_doublon_reste_possible() throws Exception {
        UUID facture = factureMille();
        UUID autre = facture(CENTRE_A, P_BELKACEM, "FAC-0002", "2026-01-31", "500.00", PAYEUR_2, AGENCE_2);
        String corps = avec("100.00", "2026-02-10", "'cleIdempotence':'envoi-0001'");

        enregistrer(facture, corps);
        enregistrer(autre, corps);
        enregistrer(facture, avec("100.00", "2026-02-10", "'cleIdempotence':'envoi-0002'"));
        enregistrer(facture, avec("100.00", "2026-02-10", "'x':1"));
        enregistrer(facture, avec("100.00", "2026-02-10", "'x':1"));

        assertEquals(5, compter("facture_reglements", CENTRE_A));
        assertEquals(5, evenements.recus().size());
    }

    @Test
    void un_remboursement_superieur_au_total_regle_est_refuse_en_422_sans_rien_enregistrer() throws Exception {
        UUID facture = factureMille();
        enregistrer(facture, corps(CENTRE_A, "100.00", "2026-02-10", "u"));
        evenements.oublier();

        golden("remboursement_trop_eleve_422", enregistrer(facture, corps(CENTRE_A, "-100.01", "2026-02-11", "u")));

        assertEquals(1, compter("facture_reglements", CENTRE_A));
        assertEquals(0, evenements.recus().size());
        assertEquals(1, compter("ecritures_comptables", CENTRE_A));
    }

    @Test
    void un_remboursement_egal_au_total_regle_est_accepte_et_la_facture_redevient_non_reglee() throws Exception {
        UUID facture = factureMille();
        enregistrer(facture, corps(CENTRE_A, "100.00", "2026-02-10", "u"));

        golden("remboursement_total_200", enregistrer(facture, corps(CENTRE_A, "-100.00", "2026-02-11", "u")));
    }

    @Test
    void un_remboursement_apres_un_trop_percu_peut_atteindre_le_total_encaisse() throws Exception {
        UUID facture = factureMille();
        enregistrer(facture, corps(CENTRE_A, "1500.00", "2026-02-10", "u"));

        golden("remboursement_du_trop_percu_200", enregistrer(facture, corps(CENTRE_A, "-1500.00", "2026-02-11", "u")));
    }

    @Test
    void l_auteur_est_l_utilisateur_de_la_session_et_le_user_id_du_corps_est_ignore() throws Exception {
        UUID facture = factureMille();
        UUID utilisateur = UUID.fromString("99997000-0000-0000-0000-00000000cc01");

        doPost("/" + facture + "/paiements", corps(CENTRE_A, "100.00", "2026-02-10", "quelqu-un-d-autre"),
                user(UserPrincipal.create(utilisateur.toString(), CENTRE_A.toString(), "u", "", List.of("SECRETAIRE"), true)));

        assertEquals(utilisateur.toString(), jdbc.queryForObject("SELECT saisi_par FROM facture_reglements WHERE center_id = ?",
                String.class, CENTRE_A));
        assertEquals(utilisateur.toString(), evenements.recus().getFirst().userId());
        assertEquals(utilisateur.toString(), json(doGet("/" + facture + "/paiements?centerId=" + CENTRE_A, adminA())).path(0).path("saisiPar").asString());
    }

    @Test
    void la_reconstruction_ecrit_un_reglement_en_caisse_et_un_remboursement_de_sens_inverse() throws Exception {
        UUID facture = factureMille();
        UUID encaisse = paiement(facture, CENTRE_A, "400.00", "2026-02-10", "migration", "REG-0000000A", "2026-02-10 08:00:00");
        UUID rembourse = paiement(facture, CENTRE_A, "-150.00", "2026-02-11", "migration", "REG-0000000B", "2026-02-11 08:00:00");
        jdbc.update("UPDATE facture_reglements SET mode_reglement = 'ESPECES' WHERE id = ?", encaisse);
        jdbc.update("UPDATE facture_reglements SET mode_reglement = 'CAISSE' WHERE id = ?", rembourse);

        golden("rebuild_caisse_200", rebuild());

        goldenEcritures("rebuild_caisse_ecritures", CENTRE_A);
    }

    @Test
    void la_reconstruction_garde_la_banque_pour_un_reglement_sans_mode() throws Exception {
        UUID facture = factureMille();
        paiement(facture, CENTRE_A, "400.00", "2026-02-10", "migration", "REP-0000000A", "2026-02-10 08:00:00");

        rebuild();

        assertEquals("BQ", jdbc.queryForObject("SELECT journal_code FROM ecritures_comptables WHERE center_id = ?", String.class, CENTRE_A));
    }

    @Test
    void la_lecture_de_l_historique_et_l_enregistrement_refusent_un_centre_autre_que_la_session_y_compris_pour_la_secretaire() throws Exception {
        UUID factureB = facture(CENTRE_B, P_CENTRE_B, "FAC-B-0001", "2026-01-31", "500.00", PAYEUR_B, AGENCE_B);

        assertEquals(403, doGet("/" + factureB + "/paiements?centerId=" + CENTRE_B, secretaireA()).status());
        assertEquals(403, doPost("/" + factureB + "/paiements", corps(CENTRE_B, "10.00", "2026-02-10", "u"), secretaireA()).status());
        assertEquals(403, doGet("?centerId=" + CENTRE_B + "&year=2026", secretaireA()).status());
        assertEquals(0, compter("facture_reglements", CENTRE_B));
    }
}
