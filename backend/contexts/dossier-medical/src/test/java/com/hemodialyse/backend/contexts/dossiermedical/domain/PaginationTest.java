package com.hemodialyse.backend.contexts.dossiermedical.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationTest {

    @Test
    void should_accept_sizes_from_one_to_one_hundred() {
        assertThat(new Pagination(0, 1).rang()).isZero();
        assertThat(new Pagination(0, 100).taille()).isEqualTo(100);
    }

    @Test
    void should_reject_a_size_above_one_hundred() {
        assertThatThrownBy(() -> new Pagination(0, 101)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new Pagination(0, 1000)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_reject_a_negative_page() {
        assertThatThrownBy(() -> new Pagination(-1, 20)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_reject_a_size_below_one() {
        assertThatThrownBy(() -> new Pagination(0, 0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new Pagination(0, -1)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_compute_the_rank_of_the_first_item_without_overflow() {
        assertThat(new Pagination(3, 2).rang()).isEqualTo(6);
        assertThat(new Pagination(Integer.MAX_VALUE, 100).rang()).isEqualTo((long) Integer.MAX_VALUE * 100);
    }

    @Test
    void should_freeze_the_items_of_a_page() {
        var items = new java.util.ArrayList<ConstantesDeSeance>();
        var page = new PageDeConstantes(items, 0, 0, 20);
        items.add(ConstantesDeSeance.sansSaisie(new SeanceId(java.util.UUID.randomUUID()), java.time.LocalDate.of(2026, 1, 1)));
        assertThat(page.items()).isEqualTo(List.of());
    }
}
