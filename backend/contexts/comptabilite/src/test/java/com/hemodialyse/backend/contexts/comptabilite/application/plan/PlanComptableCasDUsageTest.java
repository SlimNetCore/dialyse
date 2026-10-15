package com.hemodialyse.backend.contexts.comptabilite.application.plan;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteAvecEcritures;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteIntrouvable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteUtilise;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompteInvalide;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.doubles.ComptesEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UsagesEnMemoire;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanComptableCasDUsageTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.randomUUID());

    private final ComptesEnMemoire comptes = new ComptesEnMemoire();
    private final UsagesEnMemoire usages = new UsagesEnMemoire();
    private final UniteDeTravailDirecte unite = new UniteDeTravailDirecte();
    private final PlanDuCentre plan = new PlanDuCentre(comptes, usages);
    private final ListerLesComptes lister = new ListerLesComptes(plan, comptes, unite);
    private final EnregistrerUnCompte enregistrer = new EnregistrerUnCompte(comptes, plan, usages, unite);
    private final SupprimerUnCompte supprimer = new SupprimerUnCompte(comptes, plan, usages, unite);
    private final ConsulterLePlan consulter = new ConsulterLePlan(plan);

    // ------------------------------------------------------------------ lecture

    @Test
    void sans_plan_enregistre_la_liste_est_le_plan_de_depart_de_vingt_comptes_et_rien_n_est_enregistre() {
        PageDe<CompteVue> page = lister.executer(CENTRE, null, false, 0, 50);

        assertThat(page.total()).isEqualTo(20);
        assertThat(page.items()).hasSize(20);
        assertThat(page.items().get(0)).isEqualTo(new CompteVue("322", "Fournitures consommables", true));
        assertThat(comptes.nombre(CENTRE)).isZero();
    }

    @Test
    void la_taille_de_page_est_bornee_de_1_a_500_et_la_page_negative_devient_0() {
        assertThat(lister.executer(CENTRE, null, false, 0, 0).items()).hasSize(1);
        assertThat(lister.executer(CENTRE, null, false, 0, -5).items()).hasSize(1);
        assertThat(lister.executer(CENTRE, null, false, 0, 100000).size()).isEqualTo(500);
        PageDe<CompteVue> negative = lister.executer(CENTRE, null, false, -3, 5);
        assertThat(negative.page()).isZero();
        assertThat(negative.items()).hasSize(5);
    }

    @Test
    void une_page_hors_limites_est_vide_et_garde_le_total() {
        PageDe<CompteVue> page = lister.executer(CENTRE, null, false, 9, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(20);
        assertThat(page.page()).isEqualTo(9);
    }

    @Test
    void la_recherche_filtre_le_plan_de_depart_et_les_comptes_du_parametrage_le_completent() {
        usages.mapping("7061");

        assertThat(lister.executer(CENTRE, "706", false, 0, 20).items()).extracting(CompteVue::numero)
                .containsExactly("706", "7061");
        assertThat(lister.executer(CENTRE, "706", false, 0, 20).items().get(1).libelle()).isEqualTo("Compte 7061");
    }

    @Test
    void avec_un_plan_enregistre_la_liste_ne_montre_que_ses_comptes_tries_par_numero() {
        enregistrer.executer(CENTRE, "5121", "Banque 1", true);

        PageDe<CompteVue> page = lister.executer(CENTRE, null, false, 0, 100);

        assertThat(page.total()).isEqualTo(20 + 1);
        assertThat(page.items()).extracting(CompteVue::numero).startsWith("322", "401", "408");
        assertThat(lister.executer(AUTRE_CENTRE, null, false, 0, 100).total()).isEqualTo(20);
    }

    @Test
    void le_filtre_actifs_et_la_recherche_s_appliquent_au_plan_enregistre() {
        enregistrer.executer(CENTRE, "9", "Ancien compte", false);

        assertThat(lister.executer(CENTRE, null, true, 0, 100).items()).extracting(CompteVue::numero).doesNotContain("9");
        assertThat(lister.executer(CENTRE, "ANCIEN", false, 0, 100).items()).extracting(CompteVue::numero).containsExactly("9");
    }

    // ------------------------------------------------------------------ enregistrement

    @Test
    void la_premiere_modification_enregistre_le_plan_de_depart_puis_le_compte() {
        CompteVue enregistre = enregistrer.executer(CENTRE, "4112", "Clients divers", true);

        assertThat(enregistre).isEqualTo(new CompteVue("4112", "Clients divers", true));
        assertThat(comptes.nombre(CENTRE)).isEqualTo(21);
    }

    @Test
    void modifier_un_compte_existant_remplace_son_libelle_et_son_etat() {
        enregistrer.executer(CENTRE, "4112", "Clients divers", true);

        CompteVue modifie = enregistrer.executer(CENTRE, " 4112 ", "  Clients patients ", false);

        assertThat(modifie).isEqualTo(new CompteVue("4112", "Clients patients", false));
        assertThat(comptes.nombre(CENTRE)).isEqualTo(21);
    }

    @Test
    void le_numero_est_normalise_en_majuscules_ecart_decide_KB_CPT_04() {
        enregistrer.executer(CENTRE, "abc9", "Minuscules", true);
        CompteVue meme = enregistrer.executer(CENTRE, "ABC9", "Majuscules", true);

        assertThat(meme.numero()).isEqualTo("ABC9");
        assertThat(comptes.nombre(CENTRE)).isEqualTo(21);
        assertThat(comptes.trouver(CENTRE, NumeroDeCompte.de("abc9")).orElseThrow().libelle()).isEqualTo("Majuscules");
    }

    @Test
    void un_numero_mal_forme_est_refuse_sans_rien_enregistrer() {
        assertThatThrownBy(() -> enregistrer.executer(CENTRE, "41-1", "Tiret", true)).isInstanceOf(NumeroDeCompteInvalide.class);
        assertThat(comptes.nombre(CENTRE)).isZero();
    }

    @Test
    void desactiver_un_compte_du_parametrage_est_refuse() {
        usages.mapping("512");

        assertThatThrownBy(() -> enregistrer.executer(CENTRE, "512", "Banque", false)).isInstanceOf(CompteUtilise.class);
    }

    @Test
    void desactiver_un_compte_rattache_a_un_article_est_refuse_ecart_decide_KB_CPT_13a() {
        usages.rattacheAUnArticle("322");

        assertThatThrownBy(() -> enregistrer.executer(CENTRE, "322", "Stock", false)).isInstanceOf(CompteUtilise.class);
    }

    @Test
    void desactiver_un_compte_avec_ecritures_est_permis() {
        usages.avecEcritures("613");

        assertThat(enregistrer.executer(CENTRE, "613", "Locations", false).actif()).isFalse();
    }

    // ------------------------------------------------------------------ suppression

    @Test
    void supprimer_un_compte_libre_le_retire_du_plan() {
        enregistrer.executer(CENTRE, "4112", "Clients divers", true);

        supprimer.executer(CENTRE, "4112");

        assertThat(comptes.trouver(CENTRE, NumeroDeCompte.de("4112"))).isEmpty();
    }

    @Test
    void supprimer_un_compte_inconnu_est_refuse() {
        assertThatThrownBy(() -> supprimer.executer(CENTRE, "4112")).isInstanceOf(CompteIntrouvable.class)
                .hasMessage("Compte 4112 introuvable");
    }

    @Test
    void supprimer_un_compte_utilise_est_prioritaire_sur_les_ecritures() {
        usages.utiliseParUnPayeurOuUnModele("512").avecEcritures("512");

        assertThatThrownBy(() -> supprimer.executer(CENTRE, "512")).isInstanceOf(CompteUtilise.class);
    }

    @Test
    void supprimer_un_compte_avec_ecritures_est_refuse() {
        usages.avecEcritures("613");

        assertThatThrownBy(() -> supprimer.executer(CENTRE, "613")).isInstanceOf(CompteAvecEcritures.class);
    }

    @Test
    void supprimer_un_compte_mal_forme_est_refuse() {
        assertThatThrownBy(() -> supprimer.executer(CENTRE, "a b")).isInstanceOf(NumeroDeCompteInvalide.class);
    }

    // ------------------------------------------------------------------ langage publié

    @Test
    void le_plan_publie_dit_si_un_numero_est_un_compte_actif_sans_tenir_compte_de_la_casse_ni_des_espaces() {
        enregistrer.executer(CENTRE, "abc9", "Perso", true);
        enregistrer.executer(CENTRE, "4113", "Ancien", false);

        assertThat(consulter.estUnCompteActif(CENTRE.valeur(), " abc9 ")).isTrue();
        assertThat(consulter.estUnCompteActif(CENTRE.valeur(), "4113")).isFalse();
        assertThat(consulter.estUnCompteActif(CENTRE.valeur(), "inconnu")).isFalse();
        assertThat(consulter.estUnCompteActif(CENTRE.valeur(), "pas un compte")).isFalse();
        assertThat(consulter.estUnCompteActif(CENTRE.valeur(), null)).isFalse();
    }

    @Test
    void le_plan_publie_d_un_centre_sans_plan_enregistre_est_le_plan_de_depart() {
        assertThat(consulter.estUnCompteActif(CENTRE.valeur(), "512")).isTrue();
        assertThat(consulter.estUnCompteActif(CENTRE.valeur(), "999")).isFalse();
    }
}
