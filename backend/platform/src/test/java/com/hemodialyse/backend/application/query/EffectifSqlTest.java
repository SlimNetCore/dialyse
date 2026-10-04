package com.hemodialyse.backend.application.query;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Effectif d'une période : un patient dont la sortie tombe pendant ou après la période est compté, un patient sorti
 * avant son début ne l'est pas ; un patient admis après la fin ne l'est pas non plus.
 */
class EffectifSqlTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 30);
    private static final LocalDate ADMIS = LocalDate.of(2025, 1, 10);

    private static boolean present(String etat, LocalDate evenement, LocalDate admission) {
        return EffectifSql.present(etat, evenement, admission, DEBUT, FIN);
    }

    @Test
    void a_permanent_patient_is_counted_when_admitted_before_the_end_of_the_period() {
        assertThat(present("PERMANENT", null, ADMIS)).isTrue();
        assertThat(present("PERMANENT", null, FIN)).isTrue();
        assertThat(present("PERMANENT", null, FIN.plusDays(1))).as("admis après la période").isFalse();
        assertThat(present(null, null, null)).isTrue();
    }

    @Test
    void an_exit_after_or_during_the_period_keeps_the_patient_in_the_headcount() {
        assertThat(present("TRANSFERE", FIN.plusMonths(2), ADMIS)).as("transfert après la période").isTrue();
        assertThat(present("TRANSFERE", LocalDate.of(2026, 9, 15), ADMIS)).as("transfert pendant la période").isTrue();
        assertThat(present("TRANSFERE", DEBUT, ADMIS)).as("dernière séance le 1er").isTrue();
        assertThat(present("GUERRI", LocalDate.of(2026, 10, 3), ADMIS)).isTrue();
    }

    @Test
    void an_exit_before_the_period_removes_the_patient_from_the_headcount() {
        assertThat(present("TRANSFERE", DEBUT.minusDays(1), ADMIS)).isFalse();
        assertThat(present("GUERRI", LocalDate.of(2026, 3, 1), ADMIS)).isFalse();
        assertThat(present("DECEDE", DEBUT, ADMIS)).as("décès le 1er : plus présent ce jour-là").isFalse();
        assertThat(present("GREFFE", DEBUT.minusDays(20), ADMIS)).isFalse();
    }

    @Test
    void a_death_or_graft_during_the_period_still_counts_the_patient() {
        assertThat(present("DECEDE", DEBUT.plusDays(1), ADMIS)).isTrue();
        assertThat(present("GREFFE", FIN, ADMIS)).isTrue();
        assertThat(present("DECEDE", FIN.plusDays(5), ADMIS)).isTrue();
    }

    @Test
    void a_limited_stay_is_counted_until_its_end_date_inclusive() {
        assertThat(present("VACANCIER_LOCAL", DEBUT, ADMIS)).isTrue();
        assertThat(present("VACANCIER_ETRANGER", DEBUT.minusDays(1), ADMIS)).isFalse();
        assertThat(present("OCCASIONNEL", null, ADMIS)).as("séjour non borné").isTrue();
        assertThat(present("OCCASIONNEL", FIN.plusDays(10), ADMIS)).isTrue();
    }

    @Test
    void an_exit_state_without_date_is_never_counted() {
        assertThat(present("TRANSFERE", null, ADMIS)).isFalse();
        assertThat(present("DECEDE", null, ADMIS)).isFalse();
    }

    @Test
    void the_sql_fragment_binds_the_end_then_the_start_three_times() {
        EffectifSql.Fragment f = EffectifSql.presentSur("p", DEBUT, FIN);

        assertThat(f.args()).containsExactly(java.sql.Date.valueOf(FIN), java.sql.Date.valueOf(DEBUT),
                java.sql.Date.valueOf(DEBUT), java.sql.Date.valueOf(DEBUT));
        assertThat(f.sql().chars().filter(c -> c == '?').count()).isEqualTo(4);
        assertThat(f.sql()).contains("p.date_admission", "p.etat_patient", "p.date_evenement_etat");
    }
}
