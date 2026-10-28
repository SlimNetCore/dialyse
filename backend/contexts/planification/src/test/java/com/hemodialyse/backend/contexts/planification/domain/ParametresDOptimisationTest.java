package com.hemodialyse.backend.contexts.planification.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariant P21 (RG-PLN-082) : paramètres bornés, début ramené au dimanche, horizons.
 */
class ParametresDOptimisationTest {

    private static final LocalDate MERCREDI = LocalDate.of(2026, 11, 4);
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 11, 1);

    private static ParametresDOptimisation avec(int semaines, int duree, int stabilite, int jour, int semaine) {
        return new ParametresDOptimisation(Perimetre.PATIENTS, MERCREDI, semaines, duree, stabilite, null, jour, semaine);
    }

    @Test
    void should_bring_the_start_back_to_the_sunday_of_its_week() {
        assertThat(avec(1, 20, 5, 2, 6).debutSemaine()).isEqualTo(DIMANCHE);
    }

    @Test
    void should_default_the_objective_to_fairness_and_the_staff_settings_to_the_centre_defaults() {
        ParametresDOptimisation p = avec(1, 20, 5, 2, 6);
        assertThat(p.objectif()).isEqualTo(ObjectifDesInfirmiers.EQUITE);
        assertThat(p.heuresParVacation()).isEqualTo(5);
        assertThat(p.heuresHebdoTempsPlein()).isEqualTo(40);
        assertThat(p.reposHebdoMin()).isEqualTo(1);
    }

    @Test
    void should_accept_the_limits() {
        avec(1, 2, 0, 1, 1);
        avec(8, 300, 10, 3, 14);
    }

    @Test
    void should_refuse_values_outside_the_limits() {
        assertThatThrownBy(() -> avec(0, 20, 5, 2, 6)).isInstanceOf(SaisieInvalide.class).hasMessageContaining("horizon");
        assertThatThrownBy(() -> avec(9, 20, 5, 2, 6)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> avec(1, 1, 5, 2, 6)).isInstanceOf(SaisieInvalide.class).hasMessageContaining("durée");
        assertThatThrownBy(() -> avec(1, 301, 5, 2, 6)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> avec(1, 20, -1, 2, 6)).isInstanceOf(SaisieInvalide.class).hasMessageContaining("stabilité");
        assertThatThrownBy(() -> avec(1, 20, 11, 2, 6)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> avec(1, 20, 5, 0, 6)).isInstanceOf(SaisieInvalide.class).hasMessageContaining("par jour");
        assertThatThrownBy(() -> avec(1, 20, 5, 4, 6)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> avec(1, 20, 5, 2, 0)).isInstanceOf(SaisieInvalide.class).hasMessageContaining("par semaine");
        assertThatThrownBy(() -> avec(1, 20, 5, 2, 15)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_refuse_a_missing_perimeter_or_start() {
        assertThatThrownBy(() -> new ParametresDOptimisation(null, MERCREDI, 1, 20, 5, null, 2, 6)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new ParametresDOptimisation(Perimetre.PATIENTS, null, 1, 20, 5, null, 2, 6))
                .isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_refuse_staff_settings_outside_the_centre_limits() {
        assertThatThrownBy(() -> new ParametresDOptimisation(Perimetre.PATIENTS, MERCREDI, 1, 20, 5, null, 2, 6, 0, 40, 1))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new ParametresDOptimisation(Perimetre.PATIENTS, MERCREDI, 1, 20, 5, null, 2, 6, 5, 9, 1))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new ParametresDOptimisation(Perimetre.PATIENTS, MERCREDI, 1, 20, 5, null, 2, 6, 5, 40, 7))
                .isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_take_the_staff_settings_of_the_centre() {
        ParametresDOptimisation p = avec(1, 20, 5, 2, 6).avecReglages(new ReglagesDePlanification(true, 4, 35, 2));
        assertThat(p.heuresParVacation()).isEqualTo(4);
        assertThat(p.heuresHebdoTempsPlein()).isEqualTo(35);
        assertThat(p.reposHebdoMin()).isEqualTo(2);
    }

    @Test
    void should_compute_only_one_week_for_undated_perimeters_and_the_whole_horizon_for_dated_ones() {
        ParametresDOptimisation patients = new ParametresDOptimisation(Perimetre.PATIENTS, DIMANCHE, 3, 20, 5, null, 2, 6);
        ParametresDOptimisation couverture = new ParametresDOptimisation(Perimetre.COUVERTURE, DIMANCHE, 3, 20, 5, null, 2, 6);
        assertThat(patients.semainesCalcul()).isEqualTo(1);
        assertThat(patients.finCalcul()).isEqualTo(DIMANCHE.plusDays(6));
        assertThat(couverture.semainesCalcul()).isEqualTo(3);
        assertThat(couverture.finCalcul()).isEqualTo(DIMANCHE.plusDays(20));
        assertThat(patients.finHorizon()).isEqualTo(DIMANCHE.plusDays(20));
    }

    @Test
    void should_extend_the_horizon_to_the_end_of_the_last_absence_within_the_bounds() {
        assertThat(ParametresDOptimisation.semainesJusqua(MERCREDI, MERCREDI, 2)).isEqualTo(2);
        assertThat(ParametresDOptimisation.semainesJusqua(MERCREDI, DIMANCHE.plusDays(27), 2)).isEqualTo(4);
        assertThat(ParametresDOptimisation.semainesJusqua(MERCREDI, DIMANCHE.plusDays(28), 2)).isEqualTo(5);
        assertThat(ParametresDOptimisation.semainesJusqua(MERCREDI, DIMANCHE.plusDays(400), 2)).isEqualTo(8);
        assertThat(ParametresDOptimisation.semainesJusqua(MERCREDI, DIMANCHE.minusDays(10), 1)).isEqualTo(1);
    }

    @Test
    void should_default_to_one_week_of_the_given_date() {
        ParametresDOptimisation p = ParametresDOptimisation.parDefaut(Perimetre.COMPLET, MERCREDI);
        assertThat(p.nbSemaines()).isEqualTo(1);
        assertThat(p.dureeMaxSecondes()).isEqualTo(20);
        assertThat(p.stabilite()).isEqualTo(5);
        assertThat(p.maxVacationsParJour()).isEqualTo(2);
        assertThat(p.maxVacationsParSemaine()).isEqualTo(6);
        assertThat(p.debutSemaine()).isEqualTo(DIMANCHE);
    }

    @Test
    void should_describe_what_each_perimeter_plans() {
        assertThat(Perimetre.PATIENTS.placeLesPatients()).isTrue();
        assertThat(Perimetre.COMPLET.placeLesPatients()).isTrue();
        assertThat(Perimetre.ROULEMENT.placeLesPatients()).isFalse();
        assertThat(Perimetre.ROULEMENT.planifieLeRoulement()).isTrue();
        assertThat(Perimetre.COMPLET.planifieLeRoulement()).isTrue();
        assertThat(Perimetre.COUVERTURE.planifieLeRoulement()).isFalse();
        assertThat(Perimetre.COUVERTURE.planifieLesInfirmiers()).isTrue();
        assertThat(Perimetre.PATIENTS.planifieLesInfirmiers()).isFalse();
        assertThat(Perimetre.MAINTENANCE.planifieLesInfirmiers()).isFalse();
        assertThat(Perimetre.COMPLET.conjoint()).isTrue();
        assertThat(Perimetre.PATIENTS.conjoint()).isFalse();
        assertThat(Perimetre.COUVERTURE.datee()).isTrue();
        assertThat(Perimetre.MAINTENANCE.datee()).isTrue();
        assertThat(Perimetre.COMPLET.datee()).isFalse();
    }
}
