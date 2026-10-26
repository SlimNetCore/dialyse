package com.hemodialyse.backend.contexts.planification.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglagesDePlanificationTest {

    @Test
    void should_default_to_5_hours_a_vacation_40_hours_full_time_1_rest_day_and_no_night_replanning() {
        ReglagesDePlanification parDefaut = ReglagesDePlanification.parDefaut();

        assertThat(parDefaut).isEqualTo(new ReglagesDePlanification(false, 5, 40, 1));
    }

    @Test
    void should_accept_the_bounds() {
        assertThat(new ReglagesDePlanification(true, 1, 10, 0).heuresParVacation()).isEqualTo(1);
        assertThat(new ReglagesDePlanification(true, 12, 60, 6).heuresHebdoTempsPlein()).isEqualTo(60);
    }

    @Test
    void should_refuse_a_vacation_outside_1_to_12_hours() {
        for (int heures : new int[]{0, 13, -1}) {
            assertThatThrownBy(() -> new ReglagesDePlanification(false, heures, 40, 1)).isInstanceOf(SaisieInvalide.class)
                    .hasMessage("La durée d'une vacation doit être comprise entre 1 et 12 heures");
        }
    }

    @Test
    void should_refuse_a_full_time_outside_10_to_60_hours() {
        for (int heures : new int[]{9, 61}) {
            assertThatThrownBy(() -> new ReglagesDePlanification(false, 5, heures, 1)).isInstanceOf(SaisieInvalide.class)
                    .hasMessage("Les heures hebdomadaires d'un temps plein doivent être comprises entre 10 et 60");
        }
    }

    @Test
    void should_refuse_a_rest_outside_0_to_6_days() {
        for (int jours : new int[]{-1, 7}) {
            assertThatThrownBy(() -> new ReglagesDePlanification(false, 5, 40, jours)).isInstanceOf(SaisieInvalide.class)
                    .hasMessage("Le repos hebdomadaire doit être compris entre 0 et 6 jours");
        }
    }

    @Test
    void should_report_the_vacation_first_then_the_full_time_then_the_rest() {
        assertThatThrownBy(() -> new ReglagesDePlanification(false, 0, 9, -1)).hasMessageContaining("vacation");
        assertThatThrownBy(() -> new ReglagesDePlanification(false, 5, 9, -1)).hasMessageContaining("temps plein");
    }
}
