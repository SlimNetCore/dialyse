package com.hemodialyse.backend.contexts.pilotagedirection.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** KB-DIR-03 : « aujourd'hui » suit le fuseau de l'horloge injectée (celui de la JVM en production), l'horodatage de constat reste en UTC. */
class HorlogeDuPilotageTest {

    private static final Instant TARD_LE_SOIR_EN_UTC = Instant.parse("2026-11-09T23:30:00Z");

    @Test
    void aujourdhui_est_la_date_dans_le_fuseau_de_lhorloge() {
        assertThat(new HorlogeDuPilotage(Clock.fixed(TARD_LE_SOIR_EN_UTC, ZoneOffset.UTC)).aujourdhui()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(new HorlogeDuPilotage(Clock.fixed(TARD_LE_SOIR_EN_UTC, ZoneOffset.ofHours(1))).aujourdhui())
                .isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void lhorodatage_de_constat_est_en_utc_quel_que_soit_le_fuseau_de_lhorloge() {
        var maintenant = new HorlogeDuPilotage(Clock.fixed(TARD_LE_SOIR_EN_UTC, ZoneOffset.ofHours(1))).maintenant();

        assertThat(maintenant.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(maintenant.toInstant()).isEqualTo(TARD_LE_SOIR_EN_UTC);
    }
}
