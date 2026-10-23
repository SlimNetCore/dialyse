package com.hemodialyse.backend.contexts.absencespatients.infrastructure;

import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.AbsencesDuPilotageJdbc;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KB-ABS-34 : le jour courant du pilotage est celui du fuseau des centres, pas celui de l'UTC.
 */
class AujourdhuiDuPilotageTest {

    private static AbsencesDuPilotageJdbc pilotage(String instant) {
        return new AbsencesDuPilotageJdbc(null, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), ZoneId.of("Africa/Algiers"));
    }

    @Test
    void a_23h30_utc_le_jour_du_centre_est_deja_le_lendemain() {
        assertThat(pilotage("2026-03-10T23:30:00Z").aujourdhui()).isEqualTo(LocalDate.of(2026, 3, 11));
    }

    @Test
    void en_milieu_de_journee_le_jour_du_centre_est_celui_de_l_utc() {
        assertThat(pilotage("2026-03-10T12:00:00Z").aujourdhui()).isEqualTo(LocalDate.of(2026, 3, 10));
    }
}
