package com.hemodialyse.backend.contexts.seance.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RechercheDeSeancesTest {

    @Test
    void should_search_everything_by_decreasing_date_by_default() {
        var toutes = RechercheDeSeances.toutes();
        assertThat(toutes.statuts()).isEmpty();
        assertThat(toutes.ordre()).isEqualTo(RechercheDeSeances.OrdreDeTri.DATE);
        assertThat(toutes.decroissant()).isTrue();
        assertThat(toutes.deverrouillee()).isNull();
    }

    @Test
    void should_refuse_a_period_that_ends_before_it_starts() {
        assertThatThrownBy(() -> new RechercheDeSeances(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, null, null, true, null))
                .isInstanceOf(SaisieInvalide.class).hasMessage("La date de debut doit preceder la date de fin");
    }

    @Test
    void should_accept_a_single_day_period() {
        var jour = LocalDate.of(2026, 2, 1);
        assertThat(new RechercheDeSeances(jour, jour, null, null, null, true, null).du()).isEqualTo(jour);
    }

    @Test
    void should_trim_the_text_and_default_the_order() {
        var recherche = new RechercheDeSeances(null, null, null, "  dupont ", null, false, null);
        assertThat(recherche.texte()).isEqualTo("dupont");
        assertThat(recherche.ordre()).isEqualTo(RechercheDeSeances.OrdreDeTri.DATE);
    }

    @Test
    void should_read_a_list_of_statuses_ignoring_case_and_spaces() {
        assertThat(RechercheDeSeances.lireLesStatuts(" cree, Validee ")).containsExactly(StatutDeSeance.CREE, StatutDeSeance.VALIDEE);
        assertThat(RechercheDeSeances.lireLesStatuts(null)).isEmpty();
        assertThat(RechercheDeSeances.lireLesStatuts("  ")).isEmpty();
    }

    @Test
    void should_refuse_an_unknown_status_naming_it() {
        assertThatThrownBy(() -> RechercheDeSeances.lireLesStatuts("CREE,EN_COURS"))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Invalid status: EN_COURS");
    }

    @Test
    void should_read_the_sort_columns_of_the_client() {
        var ordre = RechercheDeSeances.OrdreDeTri.class;
        assertThat(RechercheDeSeances.OrdreDeTri.lire("patientNom")).isEqualTo(ordre.cast(RechercheDeSeances.OrdreDeTri.PATIENT));
        assertThat(RechercheDeSeances.OrdreDeTri.lire("PatientCode")).isEqualTo(RechercheDeSeances.OrdreDeTri.CODE);
        assertThat(RechercheDeSeances.OrdreDeTri.lire("status")).isEqualTo(RechercheDeSeances.OrdreDeTri.STATUT);
        assertThat(RechercheDeSeances.OrdreDeTri.lire("createdAt")).isEqualTo(RechercheDeSeances.OrdreDeTri.CREATION);
        assertThat(RechercheDeSeances.OrdreDeTri.lire("n'importe quoi")).isEqualTo(RechercheDeSeances.OrdreDeTri.DATE);
        assertThat(RechercheDeSeances.OrdreDeTri.lire(null)).isEqualTo(RechercheDeSeances.OrdreDeTri.DATE);
    }

    @Test
    void should_keep_the_statuses_as_an_immutable_set() {
        var recherche = new RechercheDeSeances(null, null, Set.of(StatutDeSeance.CREE), null, null, true, null);
        assertThat(recherche.statuts()).containsExactly(StatutDeSeance.CREE);
    }

    @Test
    void should_bound_the_page_and_the_size() {
        assertThat(new Pagination(-3, 20).page()).isZero();
        assertThat(new Pagination(0, 0).taille()).isEqualTo(1);
        assertThat(new Pagination(0, 100000).taille()).isEqualTo(500);
        assertThat(new Pagination(3, 20).decalage()).isEqualTo(60);
    }
}
