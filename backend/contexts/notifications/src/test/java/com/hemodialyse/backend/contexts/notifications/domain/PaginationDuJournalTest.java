package com.hemodialyse.backend.contexts.notifications.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationDuJournalTest {

    @Test
    void la_page_part_de_zero_et_la_taille_est_bornee_de_un_a_cent() {
        assertThat(PaginationDuJournal.de(0, 50)).isEqualTo(new PaginationDuJournal(0, 50));
        assertThat(PaginationDuJournal.de(-3, 2)).isEqualTo(new PaginationDuJournal(0, 2));
        assertThat(PaginationDuJournal.de(1, 0)).isEqualTo(new PaginationDuJournal(1, 1));
        assertThat(PaginationDuJournal.de(1, -5)).isEqualTo(new PaginationDuJournal(1, 1));
        assertThat(PaginationDuJournal.de(1, 100)).isEqualTo(new PaginationDuJournal(1, 100));
        assertThat(PaginationDuJournal.de(1, 101)).isEqualTo(new PaginationDuJournal(1, 100));
        assertThat(PaginationDuJournal.de(1, 5000)).isEqualTo(new PaginationDuJournal(1, 100));
    }

    @Test
    void la_taille_par_defaut_est_cinquante() {
        assertThat(PaginationDuJournal.TAILLE_PAR_DEFAUT).isEqualTo(50);
    }
}
