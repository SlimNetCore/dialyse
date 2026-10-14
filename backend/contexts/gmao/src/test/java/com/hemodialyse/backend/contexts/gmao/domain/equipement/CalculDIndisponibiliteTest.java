package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement.DESACTIF;
import static com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement.EN_ATTENTE_PIECE;
import static com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement.EN_MAINTENANCE;
import static com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement.EN_SERVICE;
import static com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement.HORS_SERVICE;
import static com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement.A_REFORMER;
import static com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement.REFORME;
import static org.assertj.core.api.Assertions.assertThat;

/** R29 : indisponibilité = temps cumulé en maintenance, en attente de pièce ou hors service, rejoué sur l'historique. */
class CalculDIndisponibiliteTest {

    private static final Instant J0 = Instant.parse("2026-01-01T00:00:00Z");

    private static Instant h(long heures) {
        return J0.plus(Duration.ofHours(heures));
    }

    private static ChangementDeStatut changement(StatutEquipement avant, StatutEquipement apres, Instant le) {
        return new ChangementDeStatut(avant, apres, "test", null, le);
    }

    @Test
    void should_have_no_downtime_when_the_equipment_was_always_in_service() {
        assertThat(CalculDIndisponibilite.calculer(List.of(), EN_SERVICE, J0, h(240))).isEqualTo(Duration.ZERO);
    }

    @Test
    void should_count_the_whole_period_when_the_current_status_is_unavailable_and_there_is_no_history() {
        assertThat(CalculDIndisponibilite.calculer(List.of(), HORS_SERVICE, J0, h(5))).isEqualTo(Duration.ofHours(5));
        assertThat(CalculDIndisponibilite.calculer(List.of(), EN_MAINTENANCE, J0, h(5))).isEqualTo(Duration.ofHours(5));
        assertThat(CalculDIndisponibilite.calculer(List.of(), EN_ATTENTE_PIECE, J0, h(5))).isEqualTo(Duration.ofHours(5));
    }

    @Test
    void should_sum_the_unavailable_windows_between_changes_and_close_the_open_one_at_the_end_of_the_period() {
        List<ChangementDeStatut> historique = List.of(
                changement(EN_SERVICE, EN_MAINTENANCE, h(2)),
                changement(EN_MAINTENANCE, EN_SERVICE, h(5)),
                changement(EN_SERVICE, HORS_SERVICE, h(8)));

        // [2h, 5h] en maintenance (3 h) + [8h, 10h] hors service, intervalle ouvert (2 h)
        assertThat(CalculDIndisponibilite.calculer(historique, HORS_SERVICE, J0, h(10))).isEqualTo(Duration.ofHours(5));
    }

    @Test
    void should_clip_the_windows_to_the_period() {
        List<ChangementDeStatut> historique = List.of(
                changement(EN_SERVICE, EN_MAINTENANCE, h(-24)),
                changement(EN_MAINTENANCE, EN_SERVICE, h(3)));

        assertThat(CalculDIndisponibilite.calculer(historique, EN_SERVICE, J0, h(10))).isEqualTo(Duration.ofHours(3));
    }

    @Test
    void should_ignore_the_changes_after_the_end_of_the_period() {
        List<ChangementDeStatut> historique = List.of(
                changement(EN_SERVICE, EN_MAINTENANCE, h(2)),
                changement(EN_MAINTENANCE, EN_SERVICE, h(50)));

        assertThat(CalculDIndisponibilite.calculer(historique, EN_SERVICE, J0, h(10))).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void should_count_a_change_exactly_at_the_end_of_the_period_without_adding_time() {
        List<ChangementDeStatut> historique = List.of(
                changement(EN_SERVICE, EN_MAINTENANCE, h(4)),
                changement(EN_MAINTENANCE, EN_SERVICE, h(10)));

        assertThat(CalculDIndisponibilite.calculer(historique, EN_SERVICE, J0, h(10))).isEqualTo(Duration.ofHours(6));
    }

    @Test
    void should_not_count_the_end_of_life_statuses_nor_the_proposal_to_retire() {
        for (StatutEquipement statut : List.of(REFORME, DESACTIF, A_REFORMER, EN_SERVICE)) {
            List<ChangementDeStatut> historique = List.of(changement(EN_SERVICE, statut, h(2)));
            assertThat(CalculDIndisponibilite.calculer(historique, statut, J0, h(10))).isEqualTo(Duration.ZERO);
        }
    }

    @Test
    void should_use_the_previous_status_of_the_first_change_for_the_time_before_it() {
        List<ChangementDeStatut> historique = List.of(changement(HORS_SERVICE, EN_SERVICE, h(4)));

        assertThat(CalculDIndisponibilite.calculer(historique, EN_SERVICE, J0, h(10))).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void should_treat_a_first_change_without_previous_status_as_available_before_it() {
        List<ChangementDeStatut> historique = List.of(changement(null, EN_MAINTENANCE, h(4)));

        assertThat(CalculDIndisponibilite.calculer(historique, EN_MAINTENANCE, J0, h(10))).isEqualTo(Duration.ofHours(6));
    }

    @Test
    void should_have_no_downtime_for_an_empty_or_reversed_period() {
        assertThat(CalculDIndisponibilite.calculer(List.of(), HORS_SERVICE, J0, J0)).isEqualTo(Duration.ZERO);
        assertThat(CalculDIndisponibilite.calculer(List.of(), HORS_SERVICE, h(5), J0)).isEqualTo(Duration.ZERO);
    }

    @Test
    void should_keep_the_seconds_of_a_downtime() {
        List<ChangementDeStatut> historique = List.of(
                changement(EN_SERVICE, HORS_SERVICE, J0.plusSeconds(30)),
                changement(HORS_SERVICE, EN_SERVICE, J0.plusSeconds(90)));

        assertThat(CalculDIndisponibilite.calculer(historique, EN_SERVICE, J0, h(1))).isEqualTo(Duration.ofSeconds(60));
    }
}
