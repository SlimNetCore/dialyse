package com.hemodialyse.backend.characterization.facturation.cloture;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Clôture, réouverture et consultation des mois de facturation (slice F7, ADR 0037, RG-FAC-040 à 045). Fonctionnalité nouvelle :
 * aucun comportement legacy à reproduire, les goldens sont tous {@code *_nouveau_code}.
 */
class ClotureEtReouvertureNouveauCodeIntegrationTest extends ClotureCharacterizationSupport {

    @Override
    protected String theme() {
        return "nouveau_code";
    }

    private void seancesDAout() {
        seance("AMRANI_03", P_AMRANI, CENTRE_A, "2026-08-03", "VALIDEE");
        seance("BELKACEM_04", P_BELKACEM, CENTRE_A, "2026-08-04", "SIGNEE");
        UUID facturee = seance("AMRANI_05_FACTUREE", P_AMRANI, CENTRE_A, "2026-08-05", "VALIDEE");
        UUID facture = factureExistante("AOUT", CENTRE_A, P_AMRANI, "ANCIENNE-0001", "2026-08-01", "2026-08-31");
        jdbc.update("UPDATE seances SET facture_id = ? WHERE id = ?", facture, facturee);
        seance("BELKACEM_06_ABSENT", P_BELKACEM, CENTRE_A, "2026-08-06", "ABSENT");
        seance("BELKACEM_07_EN_COURS", P_BELKACEM, CENTRE_A, "2026-08-07", "EN_COURS");
        seance("AMRANI_SEPTEMBRE", P_AMRANI, CENTRE_A, "2026-09-02", "VALIDEE");
    }

    @Test
    void aucun_mois_n_est_clos_au_depart() throws Exception {
        golden("periodes_2026_sans_cloture", doGet("/periodes?annee=2026", adminA()));
        assertEquals(0, compter("periodes_facturation", CENTRE_A));
    }

    @Test
    void la_cloture_nominale_avertit_du_nombre_de_seances_eligibles_restantes() throws Exception {
        seancesDAout();

        Resp reponse = cloturer(AOUT, adminA());

        golden("cloture_nominale_avertissement", reponse);
        goldenEtatDesMois("cloture_nominale_etat", CENTRE_A);
        goldenJournal("cloture_nominale_journal", CENTRE_A);
        goldenEvenements("cloture_nominale_evenement");
        golden("cloture_nominale_periodes_2026", doGet("/periodes?annee=2026", adminA()));
    }

    @Test
    void la_cloture_accepte_un_corps_avec_le_centre_de_la_session_et_n_exige_aucune_precondition() throws Exception {
        Resp reponse = doPost("/periodes/2027-03/cloturer", json("{'centerId':'" + CENTRE_A + "'}"), adminA());

        golden("cloture_sans_seance_avec_corps", reponse);
        assertEquals(0, json(reponse).path("seancesEligiblesRestantes").asInt());
    }

    @Test
    void clore_deux_fois_le_meme_mois_repond_409_sans_rien_changer() throws Exception {
        seancesDAout();
        assertEquals(200, cloturer(AOUT, adminA()).status());
        evenements.oublier();

        golden("double_cloture_409", cloturer(AOUT, autreAdminA()));

        goldenEtatDesMois("double_cloture_etat", CENTRE_A);
        goldenJournal("double_cloture_journal", CENTRE_A);
        assertEquals(0, evenements.recus().size());
    }

    @Test
    void la_reouverture_avec_motif_trace_l_auteur_le_motif_le_journal_et_l_evenement() throws Exception {
        seancesDAout();
        cloturer(AOUT, adminA());
        evenements.oublier();

        Resp reponse = rouvrir(AOUT, "  Facture oubliee du patient Amrani  ", autreAdminA());

        golden("reouverture_avec_motif_200", reponse);
        goldenEtatDesMois("reouverture_etat", CENTRE_A);
        goldenJournal("reouverture_journal", CENTRE_A);
        goldenEvenements("reouverture_evenement");
        golden("reouverture_periodes_2026", doGet("/periodes?annee=2026", adminA()));
    }

    @Test
    void un_mois_rouvert_peut_etre_clos_a_nouveau_et_le_journal_garde_les_trois_actes() throws Exception {
        cloturer(AOUT, adminA());
        rouvrir(AOUT, "correction", adminA());

        assertEquals(200, cloturer(AOUT, autreAdminA()).status());

        goldenEtatDesMois("recloture_etat", CENTRE_A);
        goldenJournal("recloture_journal", CENTRE_A);
    }

    @Test
    void la_reouverture_sans_motif_est_refusee_400_et_le_mois_reste_clos() throws Exception {
        cloturer(AOUT, adminA());
        evenements.oublier();

        golden("reouverture_corps_sans_motif_400", rouvrir(AOUT, null, adminA()));
        golden("reouverture_motif_blanc_400", rouvrir(AOUT, "   ", adminA()));
        golden("reouverture_sans_corps_400", doPostSansCorps("/periodes/" + AOUT + "/rouvrir", adminA()));

        goldenEtatDesMois("reouverture_refusee_etat", CENTRE_A);
        assertEquals(0, evenements.recus().size());
    }

    @Test
    void la_reouverture_d_un_mois_non_clos_repond_409() throws Exception {
        golden("reouverture_mois_non_clos_409", rouvrir(AOUT, "motif", adminA()));

        assertEquals(0, compter("periodes_facturation_journal", CENTRE_A));
    }

    @Test
    void la_reouverture_est_refusee_422_quand_la_periode_comptable_du_meme_mois_et_centre_est_close() throws Exception {
        cloturer(AOUT, adminA());
        cloturer(SEPTEMBRE, adminA());
        periodeComptableCloturee(CENTRE_A, 2026, 8);
        // la comptabilité du centre B ou d'un autre mois ne gêne pas
        periodeComptableCloturee(CENTRE_B, 2026, 9);
        evenements.oublier();

        golden("reouverture_comptabilite_close_422", rouvrir(AOUT, "motif", adminA()));
        assertEquals(200, rouvrir(SEPTEMBRE, "motif", adminA()).status());

        goldenEtatDesMois("reouverture_comptabilite_close_etat", CENTRE_A);
        goldenJournal("reouverture_comptabilite_close_journal", CENTRE_A);
        goldenEvenements("reouverture_comptabilite_close_evenement");
    }

    @Test
    void un_mois_invalide_ou_une_annee_invalide_repond_400_avec_un_code_stable() throws Exception {
        golden("mois_invalide_mois_13", cloturer("2026-13", adminA()));
        golden("mois_invalide_un_chiffre", cloturer("2026-8", adminA()));
        golden("mois_invalide_texte", cloturer("aout", adminA()));
        golden("mois_invalide_rouvrir", rouvrir("2026-00", "motif", adminA()));
        golden("annee_invalide", doGet("/periodes?annee=1900", adminA()));
        golden("annee_manquante", doGet("/periodes", adminA()));

        assertEquals(0, compter("periodes_facturation", CENTRE_A));
    }

    @Test
    void les_centres_et_les_mois_sont_independants() throws Exception {
        assertEquals(200, cloturer(AOUT, adminA()).status());

        golden("autre_centre_periodes_2026", doGet("/periodes?annee=2026", adminB()));
        assertEquals(200, cloturer(AOUT, adminB()).status());
        assertEquals(200, cloturer(SEPTEMBRE, adminA()).status());
        assertEquals(1, compter("periodes_facturation_journal", CENTRE_B));
        assertEquals(2, compter("periodes_facturation_journal", CENTRE_A));
    }

    @Test
    void le_centre_de_la_requete_ou_du_corps_doit_etre_celui_de_la_session() throws Exception {
        golden("centre_autre_que_la_session_cloturer_403",
                doPost("/periodes/" + AOUT + "/cloturer", json("{'centerId':'" + CENTRE_B + "'}"), adminA()));
        golden("centre_autre_que_la_session_rouvrir_403",
                doPost("/periodes/" + AOUT + "/rouvrir", json("{'centerId':'" + CENTRE_B + "','motif':'m'}"), adminA()));
        golden("centre_autre_que_la_session_lister_403", doGet("/periodes?annee=2026&centerId=" + CENTRE_B, adminA()));

        assertEquals(0, compter("periodes_facturation", CENTRE_A));
        assertEquals(0, compter("periodes_facturation", CENTRE_B));
    }

    @Test
    void seul_l_administrateur_peut_consulter_clore_ou_rouvrir() throws Exception {
        for (String role : new String[]{"SECRETAIRE", "MEDECIN", "INFIRMIER"}) {
            var who = as(UUID.randomUUID(), CENTRE_A, role);
            assertEquals(403, cloturer(AOUT, who).status(), role + " ne clot pas");
            assertEquals(403, rouvrir(AOUT, "m", who).status(), role + " ne rouvre pas");
            assertEquals(403, doGet("/periodes?annee=2026", who).status(), role + " ne consulte pas");
        }
        assertTrue(est401Ou403(doPostAnonyme("/periodes/" + AOUT + "/cloturer", "{}").status()));
        assertEquals(0, compter("periodes_facturation", CENTRE_A));
    }

    private static boolean est401Ou403(int statut) {
        return statut == 401 || statut == 403;
    }
}
