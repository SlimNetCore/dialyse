package com.hemodialyse.backend.characterization.facturation.reglements;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * F4 : situation de la facture après chaque règlement (RG-REG-002) : NON_REGLEE, PARTIELLEMENT_REGLEE, REGLEE (soldée
 * ou en trop-perçu), règlements négatifs, arrondis. La situation n'est jamais stockée : elle est recalculée à chaque
 * lecture à partir du total TTC de la facture et de la somme des règlements.
 */
class SituationDeLaFactureCharacterizationIntegrationTest extends ReglementsCharacterizationSupport {

    @Override
    protected String theme() {
        return "situation";
    }

    /**
     * Rejoue des montants successifs et note, pour chacun, l'état de la réponse.
     */
    private void parcours(String name, UUID facture, String... montants) throws Exception {
        ArrayNode pas = mapper.createArrayNode();
        int jour = 0;
        for (String montant : montants) {
            jour++;
            Resp r = regler(facture, montant, String.format("2026-02-%02d", jour));
            ObjectNode o = pas.addObject();
            o.put("montant", montant);
            o.put("status", r.status());
            JsonNode corps = json(r);
            for (String champ : new String[]{"montantFacture", "montantRegle", "reste", "tropPercu", "etat", "soldeType", "paymentCount"}) {
                o.set(champ, corps.path(champ));
            }
        }
        goldenNode(name, pas);
    }

    @Test
    void une_facture_sans_reglement_est_non_reglee_avec_tout_le_montant_a_regler() throws Exception {
        factureMille();

        golden("sans_reglement", doGet("?centerId=" + CENTRE_A + "&year=2026", adminA()));
    }

    @Test
    void la_situation_passe_de_non_reglee_a_partiellement_reglee_puis_reglee() throws Exception {
        parcours("progression_jusqu_au_solde", factureMille(), "250.00", "250.00", "500.00");
    }

    @Test
    void un_reglement_superieur_au_reste_est_accepte_et_laisse_un_trop_percu() throws Exception {
        // KB-FAC-41 : aucun contrôle de dépassement ; l'état reste REGLEE, c'est le type de solde qui porte TROP_PERCU.
        parcours("trop_percu_en_un_reglement", factureMille(), "1200.00");
    }

    @Test
    void un_reglement_apres_le_solde_aggrave_le_trop_percu() throws Exception {
        parcours("trop_percu_apres_solde", factureMille(), "1000.00", "0.01", "99.99");
    }

    @Test
    void un_remboursement_ramene_un_trop_percu_au_solde_exact() throws Exception {
        parcours("remboursement_du_trop_percu", factureMille(), "1300.00", "-300.00");
    }

    @Test
    void un_remboursement_total_ramene_la_facture_a_non_reglee_en_gardant_l_historique() throws Exception {
        // KB-FAC-42 : la somme signée vaut zéro donc NON_REGLEE, alors que deux règlements existent (paymentCount = 2).
        parcours("remboursement_total", factureMille(), "400.00", "-400.00");
    }

    @Test
    void un_remboursement_partiel_rend_une_facture_soldee_partiellement_reglee() throws Exception {
        parcours("remboursement_partiel", factureMille(), "1000.00", "-250.00");
    }

    @Test
    void un_remboursement_sans_reglement_prealable_rend_un_total_negatif_et_une_facture_partiellement_reglee() throws Exception {
        // KB-FAC-43 : -100 sur une facture de 1000 donne montantRegle = -100, reste = 1100, état PARTIELLEMENT_REGLEE.
        // Nouveau code F4 (KB-FAC-43 corrigé) : 422, un remboursement ne dépasse pas le total déjà réglé ; la facture reste inchangée.
        parcours("remboursement_sans_reglement_nouveau_code", factureMille(), "-100.00");
    }

    @Test
    void les_montants_a_trois_decimales_cumules_sont_arrondis_un_par_un() throws Exception {
        // 3 x 333.333 -> 3 x 333.33 = 999.99 : reste 0.01 ; puis 0.005 -> 0.01 solde.
        parcours("arrondis_cumules", factureMille(), "333.333", "333.333", "333.333", "0.005");
    }

    @Test
    void trois_tiers_de_mille_ne_soldent_pas_la_facture() throws Exception {
        parcours("trois_tiers", factureMille(), "333.33", "333.33", "333.33", "0.01");
    }

    @Test
    void une_facture_a_zero_est_non_reglee_et_tout_reglement_devient_un_trop_percu() throws Exception {
        UUID zero = facture(CENTRE_A, P_AMRANI, "FAC-ZERO", "2026-01-31", "0.00", PAYEUR_1, AGENCE_1);
        golden("facture_a_zero_sans_reglement", doGet("?centerId=" + CENTRE_A + "&year=2026", adminA()));

        parcours("facture_a_zero_avec_reglement", zero, "50.00", "-50.00");
    }

    @Test
    void le_montant_de_la_facture_est_celui_du_ttc_fige_et_pas_celui_des_lignes() throws Exception {
        // La situation lit factures.total_ttc ; une facture dont le TTC diffère de HT + TVA est réglée sur le TTC.
        UUID facture = facture(CENTRE_A, P_AMRANI, "FAC-INCOHERENTE", "2026-01-31", "300.00", PAYEUR_1, AGENCE_1);
        jdbc.update("UPDATE factures SET total_ht = 100.00, total_tva = 19.00 WHERE id = ?", facture);

        parcours("ttc_fige", facture, "300.00");
    }

    @Test
    void la_situation_est_recalculee_a_la_lecture_apres_un_reglement_pose_en_base() throws Exception {
        UUID facture = factureMille();
        paiement(facture, CENTRE_A, "700.00", "2026-02-01", "migration", "REG-SEED-001", "2026-02-01 10:00:00");

        golden("reglement_pose_en_base", doGet("?centerId=" + CENTRE_A + "&year=2026", adminA()));
    }
}
