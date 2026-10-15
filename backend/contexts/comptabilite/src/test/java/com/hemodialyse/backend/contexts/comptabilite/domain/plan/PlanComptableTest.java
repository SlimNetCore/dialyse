package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanComptableTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());

    private static PlanComptable depart(String... comptesDuParametrage) {
        return PlanComptable.deDepart(CENTRE, Set.of(comptesDuParametrage).stream().map(NumeroDeCompte::de)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void le_plan_de_depart_propose_les_vingt_comptes_du_scf_tries_par_numero() {
        PlanComptable plan = depart();

        assertThat(plan.comptes()).hasSize(20).allMatch(CompteComptable::actif);
        assertThat(plan.comptes().stream().map(c -> c.numero().valeur()).toList()).containsExactly("322", "401", "408",
                "411100", "411500", "421", "431", "4456", "44571", "512", "530", "602", "613", "615", "626", "631", "635", "657",
                "706", "757");
        assertThat(plan.comptes().get(0).libelle()).isEqualTo("Fournitures consommables");
        assertThat(plan.comptes().get(19).libelle()).isEqualTo("Produits exceptionnels sur opérations de gestion");
    }

    @Test
    void les_comptes_du_scf_dans_le_parametrage_ne_sont_pas_dupliques() {
        assertThat(depart("512", "706").comptes()).hasSize(20);
    }

    @Test
    void un_compte_du_parametrage_hors_scf_complete_le_plan_avec_un_libelle_technique() {
        PlanComptable plan = depart("7061");

        assertThat(plan.comptes()).hasSize(21);
        CompteComptable ajoute = plan.comptes().stream().filter(c -> c.numero().valeur().equals("7061")).findFirst().orElseThrow();
        assertThat(ajoute.libelle()).isEqualTo("Compte 7061");
        assertThat(ajoute.actif()).isTrue();
        assertThat(plan.comptes().stream().map(c -> c.numero().valeur()).toList()).containsSubsequence("706", "7061", "757");
    }

    @Test
    void la_recherche_porte_sur_le_numero_et_le_libelle_sans_tenir_compte_de_la_casse() {
        PlanComptable plan = depart();

        assertThat(plan.rechercher("BANQUE")).extracting(c -> c.numero().valeur()).containsExactly("512");
        assertThat(plan.rechercher("  4115 ")).extracting(c -> c.numero().valeur()).containsExactly("411500");
        assertThat(plan.rechercher(null)).hasSize(20);
        assertThat(plan.rechercher("   ")).hasSize(20);
    }

    @Test
    void les_jokers_sql_sont_litteraux_dans_le_plan_de_depart_KB_CPT_05() {
        assertThat(depart().rechercher("%")).isEmpty();
        assertThat(depart().rechercher("4_")).isEmpty();
    }

    @Test
    void le_plan_de_depart_contient_ses_comptes_actifs_et_rien_d_autre() {
        assertThat(depart().contientUnCompteActif(NumeroDeCompte.de("512"))).isTrue();
        assertThat(depart().contientUnCompteActif(NumeroDeCompte.de("999"))).isFalse();
    }
}
