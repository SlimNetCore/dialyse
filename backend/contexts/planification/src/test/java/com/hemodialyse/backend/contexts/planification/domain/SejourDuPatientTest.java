package com.hemodialyse.backend.contexts.planification.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** L'unique calcul de présence (RG-PLN-010, RG-PLN-045, RG-PAT-032) partagé par le planning, les Absences et la Direction. */
class SejourDuPatientTest {

    private static final LocalDate J = LocalDate.of(2026, 3, 10);

    @Test
    void should_hold_the_place_without_limit_for_a_permanent_patient() {
        assertThat(SejourDuPatient.SANS_LIMITE.occupeSaPlaceLe(J)).isTrue();
        assertThat(SejourDuPatient.SANS_LIMITE.estAttenduLe(J)).isTrue();
        assertThat(SejourDuPatient.SANS_LIMITE.estPresentSur(J, J)).isTrue();
    }

    @Test
    void should_keep_the_place_of_a_sleeping_patient_but_never_show_or_expect_him() {
        SejourDuPatient sommeil = new SejourDuPatient(null, null, false, true);
        assertThat(sommeil.occupeSaPlaceLe(J)).isTrue();
        assertThat(sommeil.estPresentLe(J)).isFalse();
        assertThat(sommeil.estAttenduLe(J)).isFalse();
        assertThat(sommeil.estPresentSur(J, J)).isFalse();
    }

    @Test
    void should_not_hold_the_place_before_the_admission() {
        SejourDuPatient admis = new SejourDuPatient(J, null, false, false);
        assertThat(admis.occupeSaPlaceLe(J.minusDays(1))).isFalse();
        assertThat(admis.occupeSaPlaceLe(J)).isTrue();
        assertThat(admis.estPresentSur(J.minusDays(5), J.minusDays(1))).isFalse();
        assertThat(admis.estPresentSur(J.minusDays(5), J)).isTrue();
    }

    @Test
    void should_hold_the_place_up_to_the_last_day_and_expect_no_session_the_day_of_departure() {
        SejourDuPatient transfere = new SejourDuPatient(null, J, true, false);
        assertThat(transfere.occupeSaPlaceLe(J)).isTrue();
        assertThat(transfere.estPresentLe(J)).isTrue();
        assertThat(transfere.estAttenduLe(J)).isFalse();
        assertThat(transfere.estAttenduLe(J.minusDays(1))).isTrue();
        assertThat(transfere.occupeSaPlaceLe(J.plusDays(1))).isFalse();
        assertThat(transfere.placeLibereeAvant(J.plusDays(1))).isTrue();
        assertThat(transfere.placeLibereeAvant(J)).isFalse();
    }

    @Test
    void should_expect_a_limited_stay_patient_until_the_last_day_included() {
        SejourDuPatient vacancier = new SejourDuPatient(null, J, false, false);
        assertThat(vacancier.estAttenduLe(J)).isTrue();
        assertThat(vacancier.estAttenduLe(J.plusDays(1))).isFalse();
    }

    @Test
    void should_count_a_patient_who_left_during_or_after_the_period_but_not_before() {
        SejourDuPatient sorti = new SejourDuPatient(null, J, true, false);
        assertThat(sorti.estPresentSur(J, J.plusDays(30))).isTrue();
        assertThat(sorti.estPresentSur(J.plusDays(1), J.plusDays(30))).isFalse();
    }

    @Test
    void should_never_hold_the_place_of_a_released_patient() {
        SejourDuPatient libre = new SejourDuPatient(null, LocalDate.MIN, true, false);
        assertThat(libre.occupeSaPlaceLe(J)).isFalse();
        assertThat(libre.estPresentSur(LocalDate.MIN.plusDays(1), J)).isFalse();
    }
}
