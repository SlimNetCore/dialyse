package com.hemodialyse.backend.domain.gmao.service;

import com.hemodialyse.backend.domain.gmao.model.EquipementStatutHistorique;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndisponibiliteCalculatorTest {

    private final UUID equipementId = UUID.randomUUID();
    private final UUID centreId = UUID.randomUUID();
    private final OffsetDateTime day0 = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void returns_zero_when_always_en_service() {
        Duration result = IndisponibiliteCalculator.calculer(
                List.of(), StatutEquipement.EN_SERVICE, day0, day0.plusDays(10));

        assertEquals(Duration.ZERO, result);
    }

    @Test
    void counts_full_period_when_statut_courant_is_down_and_no_history() {
        Duration result = IndisponibiliteCalculator.calculer(
                List.of(), StatutEquipement.HORS_SERVICE, day0, day0.plusHours(5));

        assertEquals(Duration.ofHours(5), result);
    }

    @Test
    void sums_multiple_down_windows_between_transitions() {
        List<EquipementStatutHistorique> historique = List.of(
                entree(StatutEquipement.EN_SERVICE, StatutEquipement.EN_MAINTENANCE, day0.plusHours(2)),
                entree(StatutEquipement.EN_MAINTENANCE, StatutEquipement.EN_SERVICE, day0.plusHours(5)),
                entree(StatutEquipement.EN_SERVICE, StatutEquipement.HORS_SERVICE, day0.plusHours(8))
        );

        Duration result = IndisponibiliteCalculator.calculer(
                historique, StatutEquipement.HORS_SERVICE, day0, day0.plusHours(10));

        // [2h-5h] en maintenance (3h) + [8h-10h] hors service (2h) = 5h
        assertEquals(Duration.ofHours(5), result);
    }

    @Test
    void clips_history_entries_outside_the_requested_period() {
        List<EquipementStatutHistorique> historique = List.of(
                entree(StatutEquipement.EN_SERVICE, StatutEquipement.EN_MAINTENANCE, day0.minusDays(1)),
                entree(StatutEquipement.EN_MAINTENANCE, StatutEquipement.EN_SERVICE, day0.plusHours(3))
        );

        Duration result = IndisponibiliteCalculator.calculer(
                historique, StatutEquipement.EN_SERVICE, day0, day0.plusHours(10));

        assertEquals(Duration.ofHours(3), result);
    }

    @Test
    void excludes_reforme_and_desactif_from_downtime() {
        List<EquipementStatutHistorique> historique = List.of(
                entree(StatutEquipement.EN_SERVICE, StatutEquipement.REFORME, day0.plusHours(2))
        );

        Duration result = IndisponibiliteCalculator.calculer(
                historique, StatutEquipement.REFORME, day0, day0.plusHours(10));

        assertEquals(Duration.ZERO, result);
    }

    private EquipementStatutHistorique entree(StatutEquipement before, StatutEquipement after, OffsetDateTime at) {
        return EquipementStatutHistorique.reconstruct(
                UUID.randomUUID(), equipementId, centreId, before, after, "test", at, UUID.randomUUID());
    }
}
