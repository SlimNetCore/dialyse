package com.hemodialyse.backend.domain.planning.optimisation.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParametresOptimisationTest {

    private static final LocalDate MERCREDI = LocalDate.of(2026, 9, 30);

    @Test
    void should_align_the_horizon_on_the_sunday_of_the_week() {
        ParametresOptimisation p = ParametresOptimisation.parDefaut(PerimetreOptimisation.PATIENTS, MERCREDI);

        assertThat(p.debutSemaine()).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(p.debutSemaine().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(p.finHorizon()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(p.objectif()).isEqualTo(ObjectifInfirmiers.EQUITE);
    }

    @Test
    void should_end_the_horizon_after_the_requested_number_of_weeks() {
        ParametresOptimisation p = new ParametresOptimisation(PerimetreOptimisation.COUVERTURE, MERCREDI, 3, 10, 5,
                null, 2, 6);

        assertThat(p.finHorizon()).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    @Test
    void should_reject_a_multi_week_horizon_outside_the_coverage() {
        assertThatThrownBy(() -> new ParametresOptimisation(PerimetreOptimisation.ROULEMENT, MERCREDI, 2, 10, 5, null, 2, 6))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("couverture");
    }

    @Test
    void should_reject_out_of_range_values() {
        assertThatThrownBy(() -> new ParametresOptimisation(PerimetreOptimisation.PATIENTS, MERCREDI, 1, 1, 5, null, 2, 6))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("durée");
        assertThatThrownBy(() -> new ParametresOptimisation(PerimetreOptimisation.PATIENTS, MERCREDI, 1, 10, 11, null, 2, 6))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stabilité");
        assertThatThrownBy(() -> new ParametresOptimisation(PerimetreOptimisation.PATIENTS, MERCREDI, 1, 10, 5, null, 4, 6))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("par jour");
        assertThatThrownBy(() -> new ParametresOptimisation(PerimetreOptimisation.PATIENTS, MERCREDI, 1, 10, 5, null, 2, 15))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("par semaine");
        assertThatThrownBy(() -> new ParametresOptimisation(PerimetreOptimisation.PATIENTS, MERCREDI, 5, 10, 5, null, 2, 6))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParametresOptimisation(null, MERCREDI, 1, 10, 5, null, 2, 6))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Périmètre");
    }

    @Test
    void should_accept_a_coverage_horizon_up_to_eight_weeks_and_refuse_beyond() {
        var huit = new ParametresOptimisation(PerimetreOptimisation.COUVERTURE, MERCREDI, 8, 10, 5, null, 2, 6);

        assertThat(huit.finHorizon()).isEqualTo(huit.debutSemaine().plusDays(8 * 7 - 1));
        assertThatThrownBy(() -> new ParametresOptimisation(PerimetreOptimisation.COUVERTURE, MERCREDI, 9, 10, 5, null, 2, 6))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("8 semaines");
    }

    @Test
    void should_compute_the_weeks_needed_to_reach_a_date() {
        LocalDate mardi = LocalDate.of(2026, 9, 29); // la semaine commence le dimanche 27 septembre
        // même semaine → le minimum
        assertThat(ParametresOptimisation.semainesJusqua(mardi, LocalDate.of(2026, 10, 3), 2)).isEqualTo(2);
        // samedi 3 octobre = fin de la 1re semaine ; dimanche 4 octobre ouvre la 2e
        assertThat(ParametresOptimisation.semainesJusqua(mardi, LocalDate.of(2026, 10, 4), 1)).isEqualTo(2);
        assertThat(ParametresOptimisation.semainesJusqua(mardi, LocalDate.of(2026, 10, 24), 1)).isEqualTo(4);
        assertThat(ParametresOptimisation.semainesJusqua(mardi, LocalDate.of(2026, 10, 25), 1)).isEqualTo(5);
        // plafonné, et jamais moins de 1
        assertThat(ParametresOptimisation.semainesJusqua(mardi, LocalDate.of(2028, 1, 1), 2))
                .isEqualTo(ParametresOptimisation.SEMAINES_MAX);
        assertThat(ParametresOptimisation.semainesJusqua(mardi, LocalDate.of(2026, 9, 1), 0)).isEqualTo(1);
    }

    @Test
    void should_tell_which_stages_each_scope_runs() {
        assertThat(PerimetreOptimisation.PATIENTS.placePatients()).isTrue();
        assertThat(PerimetreOptimisation.PATIENTS.planifieInfirmiers()).isFalse();
        assertThat(PerimetreOptimisation.COMPLET.placePatients()).isTrue();
        assertThat(PerimetreOptimisation.COMPLET.planifieRoulement()).isTrue();
        assertThat(PerimetreOptimisation.COUVERTURE.placePatients()).isFalse();
        assertThat(PerimetreOptimisation.COUVERTURE.planifieRoulement()).isFalse();
        assertThat(PerimetreOptimisation.COUVERTURE.planifieInfirmiers()).isTrue();
    }
}
