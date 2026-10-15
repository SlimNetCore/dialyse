package com.hemodialyse.backend.contexts.comptabilite.domain.mapping;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.Journal;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteInconnu;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.doubles.ComptesEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UsagesEnMemoire;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MappingComptableTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final ComptesDuStockSaisis STOCK_NON_SAISI = new ComptesDuStockSaisis(null, null, null, null, null);

    private final ComptesEnMemoire comptes = new ComptesEnMemoire();
    private final PlanDuCentre plan = new PlanDuCentre(comptes, new UsagesEnMemoire());

    private static MappingComptable mapping() {
        return MappingComptable.parDefaut(CENTRE);
    }

    private static MappingComptable redefini(MappingComptable base, String ventes, String tva) {
        return base.redefini(ventes, "411100", "411500", "512", "530", tva, STOCK_NON_SAISI, Map.of());
    }

    private static Journal journal(String code, boolean actif) {
        return Journal.reconstitue(CENTRE, CodeJournal.de(code), code, actif);
    }

    @Test
    void le_mapping_par_defaut_porte_les_comptes_et_les_journaux_du_plan_de_depart() {
        MappingComptable m = mapping();

        assertThat(m.compteVentes()).isEqualTo("706");
        assertThat(m.compteClientPatient()).isEqualTo("411100");
        assertThat(m.compteClientDefaut()).isEqualTo("411500");
        assertThat(m.compteBanque()).isEqualTo("512");
        assertThat(m.compteCaisse()).isEqualTo("530");
        assertThat(m.compteTvaCollectee()).contains("44571");
        assertThat(m.stock()).isEqualTo(new ComptesDuStock("322", "602", "408", "757", "657"));
        assertThat(m.journalDe(OperationComptable.VENTE)).isEqualTo(CodeJournal.VE);
        assertThat(m.journalDe(OperationComptable.REGLEMENT_BANQUE)).isEqualTo(CodeJournal.BQ);
        assertThat(m.journalDe(OperationComptable.REGLEMENT_CAISSE)).isEqualTo(CodeJournal.CA);
        assertThat(m.journalDe(OperationComptable.STOCK_RECEPTION)).isEqualTo(CodeJournal.AC);
        assertThat(m.journalDe(OperationComptable.STOCK_SORTIE)).isEqualTo(CodeJournal.ST);
        assertThat(m.journalDe(OperationComptable.STOCK_INVENTAIRE)).isEqualTo(CodeJournal.ST);
    }

    @Test
    void chacun_des_cinq_comptes_obligatoires_est_requis() {
        MappingComptable m = mapping();
        for (String vide : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> m.redefini(vide, "a", "b", "c", "d", null, STOCK_NON_SAISI, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Le compte ventes est obligatoire");
            assertThatThrownBy(() -> m.redefini("a", vide, "b", "c", "d", null, STOCK_NON_SAISI, Map.of()))
                    .hasMessage("Le compte client patient est obligatoire");
            assertThatThrownBy(() -> m.redefini("a", "b", vide, "c", "d", null, STOCK_NON_SAISI, Map.of()))
                    .hasMessage("Le compte client par défaut est obligatoire");
            assertThatThrownBy(() -> m.redefini("a", "b", "c", vide, "d", null, STOCK_NON_SAISI, Map.of()))
                    .hasMessage("Le compte banque est obligatoire");
            assertThatThrownBy(() -> m.redefini("a", "b", "c", "d", vide, null, STOCK_NON_SAISI, Map.of()))
                    .hasMessage("Le compte caisse est obligatoire");
        }
    }

    @Test
    void le_centre_est_obligatoire() {
        assertThatThrownBy(() -> MappingComptable.parDefaut(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le centre est obligatoire");
    }

    @Test
    void les_comptes_sont_rognes() {
        MappingComptable m = redefini(mapping(), "  7061  ", "  44570 ");

        assertThat(m.compteVentes()).isEqualTo("7061");
        assertThat(m.compteTvaCollectee()).contains("44570");
    }

    @Test
    void la_tva_collectee_est_facultative_et_une_tva_blanche_l_efface() {
        assertThat(redefini(mapping(), "706", null).compteTvaCollectee()).isEmpty();
        assertThat(redefini(mapping(), "706", "   ").compteTvaCollectee()).isEmpty();
        assertThat(redefini(mapping(), "706", "").compteTvaCollectee()).isEmpty();
    }

    @Test
    void les_comptes_du_stock_absents_ou_blancs_gardent_le_compte_actuel() {
        MappingComptable personnalise = mapping().redefini("706", "411100", "411500", "512", "530", "44571",
                new ComptesDuStockSaisis("3221", "6021", "4081", "7571", "6571"), Map.of());

        MappingComptable partiel = personnalise.redefini("706", "411100", "411500", "512", "530", "44571",
                new ComptesDuStockSaisis("  ", null, "4082", "", " 7572 "), Map.of());

        assertThat(partiel.stock()).isEqualTo(new ComptesDuStock("3221", "6021", "4082", "7571", "7572"));
    }

    @Test
    void les_comptes_du_stock_ne_reviennent_pas_aux_valeurs_par_defaut_sans_renvoi_explicite() {
        MappingComptable personnalise = mapping().redefini("706", "411100", "411500", "512", "530", "44571",
                new ComptesDuStockSaisis("3221", null, null, null, null), Map.of());

        assertThat(personnalise.redefini("706", "411100", "411500", "512", "530", "44571", STOCK_NON_SAISI, Map.of())
                .stock().stock()).isEqualTo("3221");
    }

    @Test
    void chaque_compte_du_stock_est_obligatoire_dans_le_domaine() {
        assertThatThrownBy(() -> new ComptesDuStock(null, "602", "408", "757", "657")).hasMessage("Le compte de stock est obligatoire");
        assertThatThrownBy(() -> new ComptesDuStock("322", " ", "408", "757", "657"))
                .hasMessage("Le compte de consommation est obligatoire");
        assertThatThrownBy(() -> new ComptesDuStock("322", "602", "", "757", "657"))
                .hasMessage("Le compte des factures non parvenues est obligatoire");
        assertThatThrownBy(() -> new ComptesDuStock("322", "602", "408", null, "657"))
                .hasMessage("Le compte de boni d'inventaire est obligatoire");
        assertThatThrownBy(() -> new ComptesDuStock("322", "602", "408", "757", " "))
                .hasMessage("Le compte de mali d'inventaire est obligatoire");
    }

    @Test
    void un_journal_choisi_remplace_celui_de_l_operation_et_les_autres_gardent_le_leur() {
        MappingComptable m = mapping().redefini("706", "411100", "411500", "512", "530", "44571", STOCK_NON_SAISI,
                Map.of(OperationComptable.VENTE, CodeJournal.de("od")));
        MappingComptable ensuite = m.redefini("706", "411100", "411500", "512", "530", "44571", STOCK_NON_SAISI,
                Map.of(OperationComptable.REGLEMENT_CAISSE, CodeJournal.BQ));

        assertThat(ensuite.journalDe(OperationComptable.VENTE)).isEqualTo(CodeJournal.de("OD"));
        assertThat(ensuite.journalDe(OperationComptable.REGLEMENT_CAISSE)).isEqualTo(CodeJournal.BQ);
        assertThat(ensuite.journalDe(OperationComptable.STOCK_SORTIE)).isEqualTo(CodeJournal.ST);
    }

    @Test
    void une_opération_sans_journal_dans_la_base_garde_son_journal_par_defaut() {
        MappingComptable m = MappingComptable.reconstitue(CENTRE, "706", "411100", "411500", "512", "530", null, null, null);

        assertThat(m.journalDe(OperationComptable.STOCK_RECEPTION)).isEqualTo(CodeJournal.AC);
        assertThat(m.stock()).isEqualTo(ComptesDuStock.parDefaut());
        assertThat(m.compteTvaCollectee()).isEmpty();
    }

    // ------------------------------------------------------------------ journaux

    @Test
    void les_comptes_du_mapping_sont_listes_sans_doublon_dans_l_ordre_de_controle() {
        MappingComptable m = redefini(mapping(), "706", null);

        assertThat(m.comptes()).containsExactly("706", "411100", "411500", "512", "530", "322", "602", "408", "757", "657");
        assertThat(mapping().comptes()).endsWith("44571").hasSize(11);
        assertThat(mapping().redefini("512", "512", "512", "512", "512", null,
                new ComptesDuStockSaisis("512", "512", "512", "512", "512"), Map.of()).comptes()).containsExactly("512");
    }

    @Test
    void les_journaux_par_defaut_sont_acceptes_tant_que_rien_n_est_enregistre() {
        assertThatCode(() -> mapping().exigerJournauxActifs(Journal.parDefaut(CENTRE))).doesNotThrowAnyException();
    }

    @Test
    void un_journal_absent_des_journaux_du_centre_est_refuse_avec_l_operation_citee() {
        assertThatThrownBy(() -> mapping().exigerJournauxActifs(List.of(journal("VE", true), journal("BQ", true),
                journal("CA", true), journal("AC", true))))
                .isInstanceOf(JournalInconnu.class)
                .hasMessage("Le journal ST choisi pour STOCK_SORTIE n'existe pas ou est désactivé")
                .extracting("code").isEqualTo("JOURNAL_INCONNU");
    }

    @Test
    void un_journal_desactive_est_refuse() {
        assertThatThrownBy(() -> mapping().exigerJournauxActifs(List.of(journal("VE", false), journal("BQ", true),
                journal("CA", true), journal("AC", true), journal("ST", true))))
                .hasMessage("Le journal VE choisi pour VENTE n'existe pas ou est désactivé");
    }

    @Test
    void l_operation_citee_est_toujours_la_premiere_dans_l_ordre_des_operations() {
        MappingComptable m = mapping();
        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() -> m.exigerJournauxActifs(List.of(journal("VE", true), journal("BQ", true),
                    journal("CA", true), journal("AC", true)))).hasMessageContaining("STOCK_SORTIE");
        }
    }

    // ------------------------------------------------------------------ plan

    @Test
    void les_comptes_du_plan_de_depart_sont_acceptes() {
        assertThatCode(() -> mapping().exigerComptesDuPlan(plan)).doesNotThrowAnyException();
    }

    @Test
    void un_compte_hors_du_plan_est_refuse_et_le_premier_fautif_est_cite() {
        MappingComptable m = mapping().redefini("7069", "411100", "411500", "512", "530", "4457X", STOCK_NON_SAISI, Map.of());

        assertThatThrownBy(() -> m.exigerComptesDuPlan(plan)).isInstanceOf(CompteInconnu.class)
                .hasMessage("Le compte 7069 n'existe pas dans le plan comptable du centre ou est désactivé")
                .extracting("code").isEqualTo("COMPTE_INCONNU");
    }

    @Test
    void un_compte_desactive_est_refuse() {
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("706"), "Ventes", false));
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("411100"), "Clients", true));

        assertThatThrownBy(() -> mapping().exigerComptesDuPlan(plan)).hasMessageContaining("Le compte 706 ");
    }

    @Test
    void un_numero_en_minuscules_est_refuse_meme_si_le_plan_a_sa_version_en_majuscules() {
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("ABC"), "Compte ABC", true));
        MappingComptable m = redefini(mapping(), "abc", null);

        assertThatThrownBy(() -> m.exigerComptesDuPlan(plan)).hasMessage(
                "Le compte abc n'existe pas dans le plan comptable du centre ou est désactivé");
    }

    @Test
    void un_numero_qui_n_a_pas_le_format_d_un_compte_est_un_compte_inconnu() {
        MappingComptable m = redefini(mapping(), "70-6", null);

        assertThatThrownBy(() -> m.exigerComptesDuPlan(plan)).isInstanceOf(CompteInconnu.class)
                .hasMessageContaining("Le compte 70-6 ");
    }

    @Test
    void la_tva_renseignee_est_controlee_et_une_tva_vide_ne_l_est_pas() {
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("706"), "Ventes", true));
        for (String c : new String[]{"411100", "411500", "512", "530", "322", "602", "408", "757", "657"}) {
            comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de(c), c, true));
        }

        assertThatCode(() -> redefini(mapping(), "706", null).exigerComptesDuPlan(plan)).doesNotThrowAnyException();
        assertThatThrownBy(() -> redefini(mapping(), "706", "44571").exigerComptesDuPlan(plan))
                .hasMessageContaining("Le compte 44571 ");
    }
}
