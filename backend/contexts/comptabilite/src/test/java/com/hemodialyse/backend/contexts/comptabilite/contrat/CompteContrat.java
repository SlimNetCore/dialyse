package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.CompteVue;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.LecturePlan;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat partagé du plan comptable enregistré et de sa page de lecture : exécuté contre le double en mémoire, l'adaptateur
 * JPA sur H2 et l'adaptateur sur PostgreSQL 16 (même suite : le double est fidèle).
 */
public abstract class CompteContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99996200-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99996200-0000-0000-0000-00000000000b"));

    protected abstract CompteRepository repository();

    protected abstract LecturePlan lecture();

    protected void compte(CentreId centre, String numero, String libelle, boolean actif) {
        repository().enregistrer(CompteComptable.reconstitue(centre, NumeroDeCompte.de(numero), libelle, actif));
    }

    @Test
    void should_find_a_saved_account_with_its_label_and_state() {
        compte(CENTRE_A, "4112", "Clients", false);

        CompteComptable trouve = repository().trouver(CENTRE_A, NumeroDeCompte.de("4112")).orElseThrow();

        assertThat(trouve.numero().valeur()).isEqualTo("4112");
        assertThat(trouve.libelle()).isEqualTo("Clients");
        assertThat(trouve.actif()).isFalse();
        assertThat(trouve.centre()).isEqualTo(CENTRE_A);
    }

    @Test
    void should_replace_label_and_state_when_saving_the_same_number_again() {
        compte(CENTRE_A, "4112", "Clients", true);
        compte(CENTRE_A, "4112", "Clients patients", false);

        assertThat(repository().nombre(CENTRE_A)).isEqualTo(1);
        CompteComptable trouve = repository().trouver(CENTRE_A, NumeroDeCompte.de("4112")).orElseThrow();
        assertThat(trouve.libelle()).isEqualTo("Clients patients");
        assertThat(trouve.actif()).isFalse();
    }

    @Test
    void should_keep_the_same_number_in_two_centres_apart() {
        compte(CENTRE_A, "512", "Banque A", true);
        compte(CENTRE_B, "512", "Banque B", false);

        assertThat(repository().nombre(CENTRE_A)).isEqualTo(1);
        assertThat(repository().trouver(CENTRE_A, NumeroDeCompte.de("512")).orElseThrow().libelle()).isEqualTo("Banque A");
        assertThat(repository().trouver(CENTRE_B, NumeroDeCompte.de("512")).orElseThrow().libelle()).isEqualTo("Banque B");
    }

    @Test
    void should_not_find_an_unknown_account_or_one_of_another_centre() {
        compte(CENTRE_A, "512", "Banque", true);

        assertThat(repository().trouver(CENTRE_A, NumeroDeCompte.de("999"))).isEmpty();
        assertThat(repository().trouver(CENTRE_B, NumeroDeCompte.de("512"))).isEmpty();
        assertThat(repository().nombre(CENTRE_B)).isZero();
    }

    @Test
    void should_delete_an_account_in_its_centre_only() {
        compte(CENTRE_A, "512", "Banque A", true);
        compte(CENTRE_B, "512", "Banque B", true);

        repository().supprimer(CENTRE_A, NumeroDeCompte.de("512"));
        repository().supprimer(CENTRE_A, NumeroDeCompte.de("512"));

        assertThat(repository().trouver(CENTRE_A, NumeroDeCompte.de("512"))).isEmpty();
        assertThat(repository().trouver(CENTRE_B, NumeroDeCompte.de("512"))).isPresent();
    }

    @Test
    void should_list_accounts_sorted_alphabetically_by_number() {
        compte(CENTRE_A, "9", "Neuf", true);
        compte(CENTRE_A, "5121", "Banque 1", true);
        compte(CENTRE_A, "10", "Dix", true);
        compte(CENTRE_B, "1", "Autre centre", true);

        PageDe<CompteVue> page = lecture().page(CENTRE_A, null, false, 0, 20);

        assertThat(page.items()).extracting(CompteVue::numero).containsExactly("10", "5121", "9");
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void should_page_with_total_and_return_an_empty_page_beyond_the_end() {
        for (int i = 1; i <= 5; i++) compte(CENTRE_A, "70" + i, "Compte " + i, true);

        assertThat(lecture().page(CENTRE_A, null, false, 1, 2).items()).extracting(CompteVue::numero).containsExactly("703", "704");
        PageDe<CompteVue> horsLimites = lecture().page(CENTRE_A, null, false, 9, 2);
        assertThat(horsLimites.items()).isEmpty();
        assertThat(horsLimites.total()).isEqualTo(5);
        assertThat(horsLimites.page()).isEqualTo(9);
    }

    @Test
    void should_keep_only_active_accounts_when_asked() {
        compte(CENTRE_A, "1", "Actif", true);
        compte(CENTRE_A, "2", "Désactivé", false);

        assertThat(lecture().page(CENTRE_A, null, true, 0, 20).items()).extracting(CompteVue::numero).containsExactly("1");
        assertThat(lecture().page(CENTRE_A, null, false, 0, 20).items()).extracting(CompteVue::numero).containsExactly("1", "2");
    }

    @Test
    void should_search_number_and_label_ignoring_case_and_edge_spaces() {
        compte(CENTRE_A, "512", "Banque", true);
        compte(CENTRE_A, "530", "Caisse centrale", true);

        assertThat(lecture().page(CENTRE_A, "  CAISSE ", false, 0, 20).items()).extracting(CompteVue::numero).containsExactly("530");
        assertThat(lecture().page(CENTRE_A, "51", false, 0, 20).items()).extracting(CompteVue::numero).containsExactly("512");
        assertThat(lecture().page(CENTRE_A, "   ", false, 0, 20).items()).hasSize(2);
        assertThat(lecture().page(CENTRE_A, "zzz", false, 0, 20).total()).isZero();
        assertThat(lecture().page(CENTRE_A, "banque", true, 0, 20).total()).isEqualTo(1);
    }

    @Test
    void should_treat_percent_and_underscore_as_sql_wildcards_KB_CPT_05() {
        compte(CENTRE_A, "41", "Quarante et un", true);
        compte(CENTRE_A, "42", "Quarante-deux", true);
        compte(CENTRE_A, "512", "Banque", true);

        assertThat(lecture().page(CENTRE_A, "4_", false, 0, 20).items()).extracting(CompteVue::numero).containsExactly("41", "42");
        assertThat(lecture().page(CENTRE_A, "%", false, 0, 20).total()).isEqualTo(3);
    }
}
