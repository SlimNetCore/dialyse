package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.IndicateursDIntervention.Indicateurs;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RG-GMA-031 : délai de prise en charge, durée, indisponibilité pendant l'intervention et sur douze mois, part plafonnée. */
class IndicateursDInterventionTest {

    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
    private static final Instant MAINTENANT = Instant.parse("2026-06-01T12:00:00Z");

    private static Instant avant(Duration duree) {
        return MAINTENANT.minus(duree);
    }

    private static Intervention intervention(Instant creation, Instant debut, Instant fin) {
        return Intervention.reconstituer(new InterventionId(UUID.randomUUID()), new EquipementId(UUID.randomUUID()),
                new CentreId(UUID.randomUUID()), TypeIntervention.CURATIVE,
                fin == null ? StatutIntervention.EN_COURS : StatutIntervention.TERMINEE, debut, fin, null, "Panne", null, null,
                null, StatutEquipement.EN_MAINTENANCE, null, PrioriteIntervention.NORMALE, null, null, null,
                new Jalon(PAR, creation), null, null, null, null, List.of(), List.of());
    }

    private static ChangementDeStatut changement(StatutEquipement avant, StatutEquipement apres, Instant le) {
        return new ChangementDeStatut(avant, apres, "test", null, le);
    }

    @Test
    void should_compare_the_downtime_of_the_intervention_to_the_downtime_of_the_equipment_over_twelve_months() {
        Instant debut = avant(Duration.ofDays(10));
        Instant fin = debut.plus(Duration.ofHours(4));
        List<ChangementDeStatut> historique = List.of(
                changement(StatutEquipement.EN_SERVICE, StatutEquipement.EN_MAINTENANCE, debut),
                changement(StatutEquipement.EN_MAINTENANCE, StatutEquipement.EN_SERVICE, fin),
                changement(StatutEquipement.EN_SERVICE, StatutEquipement.HORS_SERVICE, avant(Duration.ofDays(3))),
                changement(StatutEquipement.HORS_SERVICE, StatutEquipement.EN_SERVICE,
                        avant(Duration.ofDays(3)).plus(Duration.ofHours(12))));

        Indicateurs i = IndicateursDIntervention.calculer(intervention(debut.minus(Duration.ofHours(6)), debut, fin),
                historique, StatutEquipement.EN_SERVICE, MAINTENANT);

        assertThat(i.delaiPriseEnChargeMinutes()).isEqualTo(6 * 60);
        assertThat(i.dureeMinutes()).isEqualTo(4 * 60);
        assertThat(i.indisponibiliteInterventionMinutes()).isEqualTo(4 * 60);
        assertThat(i.indisponibiliteEquipement12MoisMinutes()).isEqualTo(16 * 60);
        assertThat(i.partIndisponibilite12MoisPct()).isEqualTo(25.0);
    }

    @Test
    void should_have_no_share_when_the_equipment_was_never_unavailable() {
        Instant debut = avant(Duration.ofDays(1));

        Indicateurs i = IndicateursDIntervention.calculer(intervention(debut, debut, debut.plus(Duration.ofHours(1))),
                List.of(), StatutEquipement.EN_SERVICE, MAINTENANT);

        assertThat(i.indisponibiliteInterventionMinutes()).isZero();
        assertThat(i.partIndisponibilite12MoisPct()).isNull();
    }

    @Test
    void should_have_a_share_of_zero_when_the_unavailability_lies_outside_the_intervention() {
        Instant debut = avant(Duration.ofDays(5));
        List<ChangementDeStatut> historique = List.of(
                changement(StatutEquipement.EN_SERVICE, StatutEquipement.HORS_SERVICE, avant(Duration.ofDays(20))),
                changement(StatutEquipement.HORS_SERVICE, StatutEquipement.EN_SERVICE, avant(Duration.ofDays(19))));

        Indicateurs i = IndicateursDIntervention.calculer(intervention(debut, debut, debut.plus(Duration.ofHours(2))),
                historique, StatutEquipement.EN_SERVICE, MAINTENANT);

        assertThat(i.indisponibiliteEquipement12MoisMinutes()).isEqualTo(24 * 60);
        assertThat(i.partIndisponibilite12MoisPct()).isEqualTo(0.0);
    }

    @Test
    void should_have_no_share_when_the_unavailability_is_older_than_twelve_months() {
        // la panne comptée pendant l'intervention n'est pas bornée à la fenêtre de douze mois (KB-GMAO-79, reproduit)
        Instant debut = avant(Duration.ofDays(420));
        List<ChangementDeStatut> historique = List.of(
                changement(StatutEquipement.EN_SERVICE, StatutEquipement.HORS_SERVICE, debut),
                changement(StatutEquipement.HORS_SERVICE, StatutEquipement.EN_SERVICE, debut.plus(Duration.ofDays(30))));

        Indicateurs i = IndicateursDIntervention.calculer(
                intervention(debut, debut, debut.plus(Duration.ofDays(30))), historique, StatutEquipement.EN_SERVICE, MAINTENANT);

        assertThat(i.indisponibiliteInterventionMinutes()).isEqualTo(30 * 24 * 60);
        assertThat(i.indisponibiliteEquipement12MoisMinutes()).isZero();
        assertThat(i.partIndisponibilite12MoisPct()).isNull();
    }

    @Test
    void should_never_exceed_one_hundred_when_the_intervention_downtime_is_larger_than_the_twelve_month_downtime() {
        Instant debut = avant(Duration.ofDays(366)).minus(Duration.ofDays(10));
        Instant fin = avant(Duration.ofDays(364));
        List<ChangementDeStatut> historique = List.of(
                changement(StatutEquipement.EN_SERVICE, StatutEquipement.HORS_SERVICE, debut),
                changement(StatutEquipement.HORS_SERVICE, StatutEquipement.EN_SERVICE, fin));

        Indicateurs i = IndicateursDIntervention.calculer(intervention(debut, debut, fin), historique,
                StatutEquipement.EN_SERVICE, MAINTENANT);

        assertThat(i.partIndisponibilite12MoisPct()).isEqualTo(100.0);
    }

    @Test
    void should_never_report_a_negative_delay_for_an_intervention_created_after_its_start() {
        Instant debut = avant(Duration.ofDays(5));

        Indicateurs i = IndicateursDIntervention.calculer(
                intervention(avant(Duration.ofDays(1)), debut, debut.plus(Duration.ofHours(2))), List.of(),
                StatutEquipement.EN_SERVICE, MAINTENANT);

        assertThat(i.delaiPriseEnChargeMinutes()).isZero();
    }

    @Test
    void should_truncate_the_durations_to_the_minute() {
        Instant debut = avant(Duration.ofDays(2));

        Indicateurs i = IndicateursDIntervention.calculer(
                intervention(debut.minusSeconds(119), debut, debut.plusSeconds(179)), List.of(),
                StatutEquipement.EN_SERVICE, MAINTENANT);

        assertThat(i.delaiPriseEnChargeMinutes()).isEqualTo(1);
        assertThat(i.dureeMinutes()).isEqualTo(2);
    }

    @Test
    void should_have_no_duration_when_the_end_is_not_after_the_start() {
        Instant debut = avant(Duration.ofDays(2));

        Indicateurs i = IndicateursDIntervention.calculer(intervention(debut, debut, debut.minusSeconds(3600)), List.of(),
                StatutEquipement.HORS_SERVICE, MAINTENANT);

        assertThat(i.dureeMinutes()).isZero();
        assertThat(i.indisponibiliteInterventionMinutes()).isZero();
    }

    @Test
    void should_measure_an_open_intervention_up_to_now() {
        Instant debut = avant(Duration.ofHours(3));

        Indicateurs i = IndicateursDIntervention.calculer(intervention(debut, debut, null), List.of(),
                StatutEquipement.EN_MAINTENANCE, MAINTENANT);

        assertThat(i.dureeMinutes()).isEqualTo(180);
        assertThat(i.indisponibiliteInterventionMinutes()).isEqualTo(180);
        assertThat(i.indisponibiliteEquipement12MoisMinutes()).isEqualTo(365 * 24 * 60);
        assertThat(i.partIndisponibilite12MoisPct()).isEqualTo(180 * 100.0 / (365 * 24 * 60));
    }

    @Test
    void should_have_no_duration_for_an_intervention_planned_in_the_future() {
        Instant debut = MAINTENANT.plus(Duration.ofDays(2));

        Indicateurs i = IndicateursDIntervention.calculer(intervention(avant(Duration.ofDays(1)), debut, null), List.of(),
                StatutEquipement.EN_SERVICE, MAINTENANT);

        assertThat(i.dureeMinutes()).isZero();
        assertThat(i.delaiPriseEnChargeMinutes()).isEqualTo(3 * 24 * 60);
    }
}
