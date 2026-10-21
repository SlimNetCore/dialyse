package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodeDeFacturationTest {

    @Test
    void should_cover_the_whole_month_when_a_month_is_given() {
        PeriodeDeFacturation periode = PeriodeDeFacturation.deMois(YearMonth.of(2026, 2));

        assertThat(periode.debut()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(periode.fin()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void should_prefer_the_month_over_the_two_dates() {
        PeriodeDeFacturation periode = PeriodeDeFacturation.de(YearMonth.of(2026, 8), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));

        assertThat(periode).isEqualTo(new PeriodeDeFacturation(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
    }

    @Test
    void should_use_the_two_dates_when_there_is_no_month() {
        PeriodeDeFacturation periode = PeriodeDeFacturation.de(null, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 20));

        assertThat(periode.debut()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(periode.fin()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void should_accept_a_one_day_period() {
        assertThat(new PeriodeDeFacturation(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10)).fin())
                .isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void should_reject_an_end_before_the_start() {
        assertThatThrownBy(() -> new PeriodeDeFacturation(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 9)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La date de fin ne peut pas etre avant la date de debut");
    }

    @Test
    void should_reject_a_missing_period() {
        assertThatThrownBy(() -> PeriodeDeFacturation.de(null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La periode de facturation est obligatoire");
        assertThatThrownBy(() -> PeriodeDeFacturation.de(null, LocalDate.of(2026, 8, 1), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La periode de facturation est obligatoire");
        assertThatThrownBy(() -> PeriodeDeFacturation.de(null, null, LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La periode de facturation est obligatoire");
    }

    @Test
    void should_cover_only_its_month_when_it_is_a_whole_month() {
        assertThat(PeriodeDeFacturation.deMois(YearMonth.of(2026, 8)).moisCouverts()).containsExactly(YearMonth.of(2026, 8));
    }

    @Test
    void should_cover_every_month_the_interval_touches_even_by_one_day() {
        PeriodeDeFacturation periode = new PeriodeDeFacturation(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 10, 1));

        assertThat(periode.moisCouverts()).containsExactly(YearMonth.of(2026, 7), YearMonth.of(2026, 8),
                YearMonth.of(2026, 9), YearMonth.of(2026, 10));
    }

    @Test
    void should_cover_the_months_of_two_years_in_order() {
        PeriodeDeFacturation periode = new PeriodeDeFacturation(LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15));

        assertThat(periode.moisCouverts()).containsExactly(YearMonth.of(2026, 12), YearMonth.of(2027, 1));
    }

    @Test
    void should_cover_one_month_for_a_one_day_interval() {
        LocalDate jour = LocalDate.of(2026, 2, 28);

        assertThat(new PeriodeDeFacturation(jour, jour).moisCouverts()).containsExactly(YearMonth.of(2026, 2));
    }

    @Test
    void should_reject_a_missing_month() {
        assertThatThrownBy(() -> PeriodeDeFacturation.deMois(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le mois est obligatoire");
    }
}
