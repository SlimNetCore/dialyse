package com.hemodialyse.backend.contexts.comptabilite.application.mapping;

import com.hemodialyse.backend.contexts.comptabilite.application.journal.JournauxDuCentreVus;
import com.hemodialyse.backend.contexts.comptabilite.doubles.ComptesEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.JournauxEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.MappingsEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UsagesEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.Journal;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.MappingComptableDuCentre.MappingPublie;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MappingCasDUsageTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.randomUUID());

    private final MappingsEnMemoire mappings = new MappingsEnMemoire();
    private final JournauxEnMemoire journaux = new JournauxEnMemoire();
    private final ComptesEnMemoire comptes = new ComptesEnMemoire();
    private final MappingDuCentreVu vus = new MappingDuCentreVu(mappings);
    private final ConsulterLeMapping consulter = new ConsulterLeMapping(vus, new UniteDeTravailDirecte());
    private final ModifierLeMapping modifier = new ModifierLeMapping(mappings, vus, new JournauxDuCentreVus(journaux),
            new PlanDuCentre(comptes, new UsagesEnMemoire()), new UniteDeTravailDirecte());

    private static ModifierLeMapping.Commande commande(String ventes, String tva, Map<String, String> choix) {
        return new ModifierLeMapping.Commande(ventes, "411100", "411500", "512", "530", tva, null, null, null, null, null, choix);
    }

    // ------------------------------------------------------------------ lecture

    @Test
    void sans_mapping_enregistre_la_lecture_donne_les_valeurs_par_defaut_et_n_enregistre_rien() {
        MappingVue vue = consulter.executer(CENTRE);

        assertThat(vue.centre()).isEqualTo(CENTRE.valeur());
        assertThat(vue.compteVentes()).isEqualTo("706");
        assertThat(vue.compteTvaCollectee()).isEqualTo("44571");
        assertThat(vue.compteStock()).isEqualTo("322");
        assertThat(vue.journaux()).containsExactly(Map.entry("VENTE", "VE"), Map.entry("REGLEMENT_BANQUE", "BQ"),
                Map.entry("REGLEMENT_CAISSE", "CA"), Map.entry("STOCK_RECEPTION", "AC"), Map.entry("STOCK_SORTIE", "ST"),
                Map.entry("STOCK_INVENTAIRE", "ST"));
        assertThat(mappings.nombre()).isZero();
    }

    @Test
    void la_lecture_d_un_centre_ne_voit_pas_le_mapping_d_un_autre() {
        modifier.executer(CENTRE, commande("706", "44571", null));

        assertThat(consulter.executer(AUTRE_CENTRE).compteVentes()).isEqualTo("706");
        assertThat(mappings.duCentre(AUTRE_CENTRE)).isEmpty();
    }

    // ------------------------------------------------------------------ modification

    @Test
    void la_modification_enregistre_le_mapping_et_rend_le_mapping_enregistre() {
        MappingVue vue = modifier.executer(CENTRE, new ModifierLeMapping.Commande("706", "411100", "411500", "512", "530",
                "44571", "322", "602", "408", "757", "657", Map.of("VENTE", "VE")));

        assertThat(vue.compteVentes()).isEqualTo("706");
        assertThat(mappings.duCentre(CENTRE)).isPresent();
        assertThat(consulter.executer(CENTRE)).isEqualTo(vue);
    }

    @Test
    void la_tva_vide_est_montree_vide_par_la_reponse_mais_relue_44571_comme_avant() {
        MappingVue vue = modifier.executer(CENTRE, commande("706", "  ", null));

        assertThat(vue.compteTvaCollectee()).isEmpty();
        assertThat(consulter.executer(CENTRE).compteTvaCollectee()).isEqualTo("44571");
    }

    @Test
    void les_comptes_du_stock_et_les_journaux_non_precises_gardent_leur_valeur_actuelle() {
        modifier.executer(CENTRE, new ModifierLeMapping.Commande("706", "411100", "411500", "512", "530", "44571",
                "322", "602", "408", "757", "657", Map.of("REGLEMENT_CAISSE", "BQ")));

        MappingVue vue = modifier.executer(CENTRE, commande("706", "44571", Map.of()));

        assertThat(vue.compteStock()).isEqualTo("322");
        assertThat(vue.journaux().get("REGLEMENT_CAISSE")).isEqualTo("BQ");
    }

    @Test
    void les_journaux_sont_controles_avant_les_comptes() {
        assertThatThrownBy(() -> modifier.executer(CENTRE, commande("9999", "44571", Map.of("VENTE", "OD"))))
                .hasMessageContaining("Le journal OD choisi pour VENTE");
        assertThat(mappings.nombre()).isZero();
    }

    @Test
    void un_compte_hors_du_plan_est_refuse_et_rien_n_est_enregistre() {
        assertThatThrownBy(() -> modifier.executer(CENTRE, commande("9999", "44571", null)))
                .hasMessageContaining("Le compte 9999 ").extracting("code").isEqualTo("COMPTE_INCONNU");
        assertThat(mappings.nombre()).isZero();
    }

    @Test
    void un_mapping_deja_enregistre_est_revalide_en_entier_meme_pour_ce_qui_ne_change_pas() {
        modifier.executer(CENTRE, commande("706", "44571", null));
        journaux.enregistrer(Journal.reconstitue(CENTRE, CodeJournal.VE, "Ventes", true));

        assertThatThrownBy(() -> modifier.executer(CENTRE, commande("706", "44571", null)))
                .hasMessageContaining("Le journal BQ choisi pour REGLEMENT_BANQUE");
    }

    @Test
    void le_plan_enregistre_du_centre_s_applique() {
        comptes.enregistrer(CompteComptable.reconstitue(CENTRE, NumeroDeCompte.de("706"), "Ventes", true));

        assertThatThrownBy(() -> modifier.executer(CENTRE, commande("706", "44571", null))).hasMessageContaining("Le compte 411100 ");
    }

    @Test
    void une_operation_inconnue_ou_un_code_de_journal_mal_forme_est_refuse_avant_tout_enregistrement() {
        assertThatThrownBy(() -> modifier.executer(CENTRE, commande("706", "44571", Map.of("vente", "VE"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Opération comptable inconnue : vente");
        Map<String, String> sansCode = new HashMap<>();
        sansCode.put("VENTE", null);
        assertThatThrownBy(() -> modifier.executer(CENTRE, commande("706", "44571", sansCode)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> modifier.executer(CENTRE, commande("706", "44571", Map.of("VENTE", "TROP-LONG-CODE"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mappings.nombre()).isZero();
    }

    // ------------------------------------------------------------------ langage publié

    @Test
    void le_langage_publie_donne_le_mapping_effectif_du_centre() {
        modifier.executer(CENTRE, new ModifierLeMapping.Commande("706", "411100", "411500", "512", "530", "  ", "322", "602",
                "408", "757", "657", Map.of("VENTE", "VE")));
        ConsulterLeMappingDuCentre publie = new ConsulterLeMappingDuCentre(vus);

        MappingPublie enregistre = publie.mapping(CENTRE.valeur());
        MappingPublie parDefaut = publie.mapping(AUTRE_CENTRE.valeur());

        assertThat(enregistre.compteTvaCollectee()).isEqualTo("44571");
        assertThat(enregistre.journaux()).hasSize(6).containsEntry("VENTE", "VE");
        assertThat(parDefaut.compteTvaCollectee()).isEqualTo("44571");
        assertThat(parDefaut.compteBanque()).isEqualTo("512");
        assertThat(parDefaut.compteMaliInventaire()).isEqualTo("657");
        assertThat(parDefaut.centre()).isEqualTo(AUTRE_CENTRE.valeur());
    }
}
