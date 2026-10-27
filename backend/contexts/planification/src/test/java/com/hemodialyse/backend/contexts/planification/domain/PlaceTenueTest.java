package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue.PlaceQuittee;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La politique de libération (RG-PLN-080 à 083) : une place est libérée quand son dernier jour est dépassé, jamais avant.
 */
class PlaceTenueTest {

    static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    static final LocalDate J = LocalDate.of(2026, 10, 27);
    static final Instant MAINTENANT = Instant.parse("2026-10-27T03:00:00Z");

    static PlaceQuittee complete() {
        return new PlaceQuittee(new SalleId(UUID.randomUUID()), new CreneauId(UUID.randomUUID()), new GenerateurId(UUID.randomUUID()),
                Set.of(JourDeLaSemaine.MERCREDI, JourDeLaSemaine.LUNDI));
    }

    static PlaceTenue tenue(LocalDate dernierJour, PlaceQuittee place) {
        return new PlaceTenue(CENTRE, PATIENT, "TRANSFERE", dernierJour, place);
    }

    @Test
    void should_free_the_place_the_day_after_the_last_day() {
        assertThat(tenue(J.minusDays(1), complete()).liberer(J, J, MAINTENANT)).isPresent();
    }

    @Test
    void should_keep_the_place_on_the_last_day_itself() {
        assertThat(tenue(J, complete()).liberer(J, J, MAINTENANT)).isEmpty();
    }

    @Test
    void should_never_free_a_place_without_a_last_day() {
        assertThat(tenue(null, complete()).liberer(J, J, MAINTENANT)).isEmpty();
    }

    @Test
    void should_free_at_once_a_leaving_patient_without_date() {
        assertThat(tenue(LocalDate.MIN, complete()).liberer(J, J, MAINTENANT)).isPresent();
    }

    @Test
    void should_ignore_a_patient_without_any_place() {
        assertThat(tenue(J.minusDays(5), new PlaceQuittee(null, null, null, Set.of())).liberer(J, J, MAINTENANT)).isEmpty();
    }

    @Test
    void should_free_a_partial_place_and_remember_what_was_held() {
        SalleId salle = new SalleId(UUID.randomUUID());
        var liberee = tenue(J.minusDays(1), new PlaceQuittee(salle, null, null, Set.of())).liberer(J, J, MAINTENANT).orElseThrow();
        assertThat(liberee.placeQuittee().salle()).isEqualTo(salle);
        assertThat(liberee.placeQuittee().creneau()).isNull();
    }

    @Test
    void should_date_the_movement_with_the_real_day_even_when_the_deadline_is_judged_the_day_before() {
        var liberee = tenue(J.minusDays(2), complete()).liberer(J.minusDays(1), J, MAINTENANT).orElseThrow();
        assertThat(liberee.dateDEffet()).isEqualTo(J);
        assertThat(liberee.automatique()).isTrue();
        assertThat(liberee.survenueLe()).isEqualTo(MAINTENANT);
        assertThat(liberee.etat()).isEqualTo("TRANSFERE");
    }

    @Test
    void should_judge_the_deadline_the_day_before_when_a_record_is_saved() {
        PlaceTenue sortiHier = tenue(J.minusDays(1), complete());
        assertThat(sortiHier.liberer(J.minusDays(1), J, MAINTENANT)).as("dernier jour = veille : encore tenue à J-1").isEmpty();
        assertThat(sortiHier.liberer(J, J, MAINTENANT)).isPresent();
    }

    @Test
    void should_write_the_days_in_the_week_order_starting_on_sunday() {
        PlaceQuittee p = new PlaceQuittee(null, null, null, Set.of(JourDeLaSemaine.SAMEDI, JourDeLaSemaine.DIMANCHE, JourDeLaSemaine.MERCREDI));
        assertThat(p.joursEnTexte()).isEqualTo("DIMANCHE,MERCREDI,SAMEDI");
        assertThat(new PlaceQuittee(null, null, null, null).joursEnTexte()).isEmpty();
    }

    @Test
    void should_refuse_an_incomplete_held_place() {
        assertThatThrownBy(() -> new PlaceTenue(null, PATIENT, "X", null, complete())).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new PlaceTenue(CENTRE, PATIENT, "X", null, null)).isInstanceOf(SaisieInvalide.class);
    }
}
