package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FrequenceMaintenanceTest {

    @ParameterizedTest(name = "{0} depuis {1} => {2}")
    @CsvSource({
            "HEBDOMADAIRE, 2026-01-10T08:00:00Z, 2026-01-17T08:00:00Z",
            "BIMENSUELLE,  2026-01-10T08:00:00Z, 2026-01-25T08:00:00Z",
            "MENSUELLE,    2026-01-10T08:00:00Z, 2026-02-10T08:00:00Z",
            "TRIMESTRIELLE,2026-01-10T08:00:00Z, 2026-04-10T08:00:00Z",
            "SEMESTRIELLE, 2026-01-10T08:00:00Z, 2026-07-10T08:00:00Z",
            "ANNUELLE,     2026-01-10T08:00:00Z, 2027-01-10T08:00:00Z"
    })
    void should_compute_prochaine_date_per_frequence(FrequenceMaintenance frequence, Instant depuis, Instant attendue) {
        assertThat(frequence.prochaineDate(depuis)).isEqualTo(attendue);
    }

    @Test
    void should_use_calendar_month_end_when_mensuelle_from_the_31st() {
        // parité legacy : plusMonths(1) ramène au dernier jour du mois (KB-GMAO-12 reproduit l'arithmétique calendaire)
        Instant le31Janvier = Instant.parse("2026-01-31T08:00:00Z");

        assertThat(FrequenceMaintenance.MENSUELLE.prochaineDate(le31Janvier)).isEqualTo(Instant.parse("2026-02-28T08:00:00Z"));
    }

    @Test
    void should_use_leap_day_when_annuelle_from_february_29() {
        assertThat(FrequenceMaintenance.ANNUELLE.prochaineDate(Instant.parse("2028-02-29T00:00:00Z")))
                .isEqualTo(Instant.parse("2029-02-28T00:00:00Z"));
    }

    @Test
    void should_know_exactly_the_six_legacy_values_in_legacy_order() {
        assertThat(FrequenceMaintenance.values()).extracting(Enum::name).containsExactly(
                "HEBDOMADAIRE", "BIMENSUELLE", "MENSUELLE", "TRIMESTRIELLE", "SEMESTRIELLE", "ANNUELLE");
    }
}
