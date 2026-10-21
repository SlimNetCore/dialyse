package com.hemodialyse.backend.characterization.facturation.validation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filet de caractérisation F3 — événement {@code FacturationValideeEvent} publié par la validation et écritures de ventes
 * produites par la Comptabilité (écouteur de production, après commit) : contenu, période, cohérence avec les factures,
 * clôture comptable, rejeu (RG-FAC-032).
 */
class EvenementEtComptabiliteCharacterizationIntegrationTest extends ValidationCharacterizationSupport {

    @Override
    protected String theme() {
        return "evenement";
    }

    private void troisPatients() {
        seance("AMRANI_05", P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE");
        seance("AMRANI_12", P_AMRANI, CENTRE_A, "2026-01-12", "VALIDEE");
        seance("BELKACEM_06", P_BELKACEM, CENTRE_A, "2026-01-06", "VALIDEE");
        seance("CHIKHI_07", P_CHIKHI, CENTRE_A, "2026-01-07", "VALIDEE");
    }

    @Test
    void une_validation_publie_un_evenement_avec_les_comptes_mais_sans_montant() throws Exception {
        // Nouveau code F3 (KB-FAC-27 corrigé) : l'événement porte le total TTC exact et les factures émises (identifiants, montants).
        // KB-FAC-27 : le montant TTC de l'événement vaut toujours zéro (« calculé par les écouteurs via la base »).
        troisPatients();

        valider(mois(CENTRE_A, JANVIER, true));

        goldenEvenements("nominal_evenements_nouveau_code");
    }

    @Test
    void la_comptabilite_ecrit_une_vente_par_facture_a_la_date_de_fin_de_periode() throws Exception {
        troisPatients();

        valider(mois(CENTRE_A, JANVIER, true));

        goldenEcritures("nominal_ecritures", CENTRE_A);
        goldenEtat("nominal_factures", CENTRE_A);
    }

    @Test
    void le_payeur_de_la_facture_oriente_le_compte_client_de_l_ecriture() throws Exception {
        troisPatients();
        jdbc.update("INSERT INTO comptes_payeurs (center_id, payeur_id, compte) VALUES (?, ?, '411210')", CENTRE_A, CNAS);

        valider(mois(CENTRE_A, JANVIER, true));

        goldenLignesEcritures("payeur_lignes_ecritures", CENTRE_A);
    }

    @Test
    void l_evenement_porte_les_dates_du_corps_meme_quand_le_mois_a_choisi_les_seances() throws Exception {
        // Nouveau code F3 (KB-FAC-28 corrigé) : l'événement porte la période réellement facturée (le mois, qui l'emporte) ; la Comptabilité écrit les ventes de janvier.
        // KB-FAC-28 : mois = janvier (séances et factures) mais dates = février dans le corps : l'événement annonce février,
        // la Comptabilité cherche les factures incluses dans février et n'écrit rien pour les factures de janvier.
        troisPatients();

        valider(json("{'centerId':'" + CENTRE_A + "','userId':'u1','month':'2026-01','periodStart':'2026-02-01',"
                + "'periodEnd':'2026-02-28','regroupementMultiForfait':true}"));

        goldenEvenements("mois_et_dates_divergents_evenements_nouveau_code");
        goldenEtat("mois_et_dates_divergents_etat", CENTRE_A);
        goldenEcritures("mois_et_dates_divergents_ecritures_nouveau_code", CENTRE_A);
    }

    @Test
    void une_periode_par_dates_publie_ces_dates_et_la_comptabilite_ne_retient_que_les_factures_incluses() throws Exception {
        // Nouveau code F3 : même période, nouveau contrat d'événement (factures émises, total TTC exact).
        troisPatients();

        valider(dates(CENTRE_A, "2026-01-01", "2026-01-31", true));

        goldenEvenements("periode_par_dates_evenements_nouveau_code");
        goldenEcritures("periode_par_dates_ecritures", CENTRE_A);
    }

    @Test
    void une_validation_sans_seance_republie_un_evenement_vide_qui_regenere_les_ventes_manquantes() throws Exception {
        // Nouveau code F3 (KB-FAC-29 corrigé) : aucun événement quand rien n'est émis ; les ventes manquantes se régénèrent par /rebuild.
        // KB-FAC-29 : l'événement est publié même sans facture émise ; l'écouteur comptabilise alors les factures de la
        // période encore sans écriture (rattrapage involontaire).
        UUID existante = factureExistante(CENTRE_A, P_AMRANI, "ANCIENNE-0001", "2026-01-01", "2026-01-31");
        nommer(existante, "<FACTURE_EXISTANTE>");

        golden("sans_seance_200", valider(mois(CENTRE_A, JANVIER, true)));

        goldenEvenements("sans_seance_evenements_nouveau_code");
        goldenEcritures("sans_seance_ecritures_nouveau_code", CENTRE_A);
    }

    @Test
    void un_mois_comptable_cloture_n_empeche_ni_la_facture_ni_la_validation_et_aucune_ecriture_n_est_produite() throws Exception {
        // Nouveau code F3 : même comportement, nouveau contrat d'événement.
        troisPatients();
        jdbc.update("INSERT INTO periodes_comptables (id, annee, center_id, cloturee, cloturee_at, cloturee_by, mois) "
                + "VALUES (?, 2026, ?, TRUE, CURRENT_TIMESTAMP, 'compta', 1)", UUID.randomUUID(), CENTRE_A);

        golden("periode_cloturee_200", valider(mois(CENTRE_A, JANVIER, true)));

        assertEquals(3, compter("factures", CENTRE_A));
        assertEquals(0, compter("ecritures_comptables", CENTRE_A));
        goldenEvenements("periode_cloturee_evenements_nouveau_code");
    }

    @Test
    void une_facture_dont_le_montant_est_nul_produit_une_ecriture_vide() throws Exception {
        // Nouveau code F3 (KB-FAC-32) : sans forfait la validation est refusée, donc aucune écriture (le forfait à zéro défini reste facturable).
        jdbc.update("DELETE FROM prise_en_charge WHERE patient_id = ?", P_CHIKHI);
        seance("CHIKHI_07", P_CHIKHI, CENTRE_A, "2026-01-07", "VALIDEE");

        valider(mois(CENTRE_A, JANVIER, true));

        goldenLignesEcritures("montant_nul_lignes_ecritures_nouveau_code", CENTRE_A);
    }

    @Test
    void la_validation_d_une_periode_deja_ecrite_en_comptabilite_ne_double_pas_les_ecritures() throws Exception {
        // Nouveau code F3 : mêmes écritures ; l'événement de la seconde validation ne décrit que la facture nouvellement émise.
        troisPatients();
        valider(mois(CENTRE_A, JANVIER, true));
        seance("BELKACEM_20", P_BELKACEM, CENTRE_A, "2026-01-20", "VALIDEE");

        valider(mois(CENTRE_A, JANVIER, true));

        goldenEcritures("rejeu_ecritures", CENTRE_A);
        goldenEvenements("rejeu_evenements_nouveau_code");
    }

    @Test
    void la_validation_de_trois_cents_factures_reussit_et_l_evenement_reste_de_taille_bornee() throws Exception {
        // KB-FAC-72 : l'ancien événement (une ligne par facture) dépassait VARCHAR(4000) de event_publication en H2.
        for (int i = 0; i < 300; i++) {
            UUID patient = UUID.randomUUID();
            patient(patient, CENTRE_A, "GRD-" + i, "Patient" + i, "Test", null);
            pec(patient, CENTRE_A, FORFAIT_HD);
            seance("GRD_" + i, patient, CENTRE_A, "2026-01-05", "VALIDEE");
        }

        assertEquals(200, valider(mois(CENTRE_A, JANVIER, true)).status());

        assertEquals(300, compter("factures", CENTRE_A));
        assertEquals(1, evenements.recus().size());
        assertEquals(300, evenements.recus().getFirst().nombreDeFactures());
        assertEquals(300, compter("ecritures_comptables", CENTRE_A));

    }

    private void goldenLignesEcritures(String name, UUID centre) {
        var arr = mapper.createArrayNode();
        for (var l : jdbc.queryForList("SELECT e.numero_piece, l.compte_scf, l.libelle_ligne, l.montant_debit, "
                + "l.montant_credit, l.tiers_id FROM lignes_ecriture l JOIN ecritures_comptables e ON e.id = l.ecriture_id "
                + "WHERE e.center_id = ? ORDER BY e.numero_piece, l.compte_scf, l.montant_debit", centre)) {
            var o = mapper.createObjectNode();
            l.forEach((k, v) -> o.put(k.toLowerCase(), v == null ? null : String.valueOf(v)));
            arr.add(o);
        }
        goldenNode(name, arr);
    }
}
