package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeanceTest {

    private static final SeanceId ID = new SeanceId(new UUID(0, 1));
    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private static final PatientId PATIENT = new PatientId(new UUID(1, 1));
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);
    private static final Instant T0 = Instant.parse("2026-06-15T08:00:00Z");

    @Test
    void should_be_created_with_the_status_cree_and_without_derogation() {
        var seance = Seance.creer(ID, CENTRE, PATIENT, JOUR, T0);
        assertThat(seance.statut()).isEqualTo(StatutDeSeance.CREE);
        assertThat(seance.horsPlanning()).isFalse();
        assertThat(seance.derogation()).isEmpty();
        assertThat(seance.attendLaValidation()).isTrue();
        assertThat(seance.jalons()).isEqualTo(Seance.Jalons.AUCUN);
        assertThat(seance.creeLe()).isEqualTo(T0);
    }

    @Test
    void should_carry_the_derogation_of_a_session_outside_the_planning() {
        var derogation = new DerogationHorsPlanning(MotifHorsPlanning.URGENCE, "Urgence un jour ferme", true);
        var seance = Seance.creerHorsPlanning(ID, CENTRE, PATIENT, JOUR, T0, derogation);
        assertThat(seance.horsPlanning()).isTrue();
        assertThat(seance.derogation()).contains(derogation);
        assertThat(seance.statut()).isEqualTo(StatutDeSeance.CREE);
    }

    @Test
    void should_refuse_a_session_without_its_mandatory_values() {
        assertThatThrownBy(() -> Seance.creer(null, CENTRE, PATIENT, JOUR, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Seance.creer(ID, null, PATIENT, JOUR, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Seance.creer(ID, CENTRE, null, JOUR, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Seance.creer(ID, CENTRE, PATIENT, null, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Seance.creer(ID, CENTRE, PATIENT, JOUR, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> Seance.creerHorsPlanning(ID, CENTRE, PATIENT, JOUR, T0, null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_restore_an_existing_line_without_replaying_the_creation_rules() {
        var jalons = new Seance.Jalons(T0, T0, null, null);
        var seance = Seance.restituer(ID, CENTRE, PATIENT, JOUR, StatutDeSeance.VALIDEE, null, null, jalons);
        assertThat(seance.statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(seance.attendLaValidation()).isFalse();
        assertThat(seance.creeLe()).isNull();
        assertThat(seance.jalons().valideeLe()).isEqualTo(T0);
    }

    @Test
    void should_count_validated_signed_and_billed_sessions_as_carried_out() {
        assertThat(StatutDeSeance.CREE.estRealisee()).isFalse();
        assertThat(StatutDeSeance.ABSENT.estRealisee()).isFalse();
        assertThat(StatutDeSeance.VALIDEE.estRealisee()).isTrue();
        assertThat(StatutDeSeance.SIGNEE.estRealisee()).isTrue();
        assertThat(StatutDeSeance.FACTUREE.estRealisee()).isTrue();
    }
}
