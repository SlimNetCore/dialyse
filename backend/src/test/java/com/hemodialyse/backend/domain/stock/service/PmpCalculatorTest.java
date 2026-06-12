package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.model.StockMovementType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PMP (weighted average cost) edge-case unit tests (Phase 6).
 */
class PmpCalculatorTest {

    private static final UUID CENTER = UUID.randomUUID();
    private static final UUID ARTICLE = UUID.randomUUID();

    private static StockMovement entree(String qte, String pu) {
        return StockMovement.entree(CENTER, ARTICLE, UUID.randomUUID(), new BigDecimal(qte), new BigDecimal(pu), "test");
    }

    private static StockMovement sortie(String qte) {
        StockMovement m = StockMovement.sortie(CENTER, ARTICLE, UUID.randomUUID(), new BigDecimal(qte), "test");
        m.setMovementType(StockMovementType.SORTIE);
        return m;
    }

    @Test
    void weighted_average_after_two_receptions() {
        // 10 @ 100  then 10 @ 200  => PMP = (1000 + 2000) / 20 = 150
        var result = PmpCalculator.walk(List.of(entree("10", "100"), entree("10", "200")),
                PmpCalculator.PmpState.empty());

        assertEquals(0, result.finalState().pmp().compareTo(new BigDecimal("150.0000")));
        assertEquals(0, result.finalState().quantite().compareTo(new BigDecimal("20")));
    }

    @Test
    void exit_keeps_pmp_unchanged() {
        // 10 @ 100, exit 4 => qty 6, value 600, pmp stays 100
        var result = PmpCalculator.walk(List.of(entree("10", "100"), sortie("4")),
                PmpCalculator.PmpState.empty());

        assertEquals(0, result.finalState().pmp().compareTo(new BigDecimal("100.0000")));
        assertEquals(0, result.finalState().quantite().compareTo(new BigDecimal("6")));
    }

    @Test
    void reception_at_zero_price_dilutes_pmp() {
        // 10 @ 100 then 10 @ 0 => PMP = 1000 / 20 = 50
        var result = PmpCalculator.walk(List.of(entree("10", "100"), entree("10", "0")),
                PmpCalculator.PmpState.empty());

        assertEquals(0, result.finalState().pmp().compareTo(new BigDecimal("50.0000")));
    }

    @Test
    void exit_down_to_zero_resets_value_but_keeps_last_pmp() {
        // 10 @ 100, exit 10 => qty 0, value 0, pmp reference 100
        var result = PmpCalculator.walk(List.of(entree("10", "100"), sortie("10")),
                PmpCalculator.PmpState.empty());

        assertEquals(0, result.finalState().quantite().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.finalState().pmp().compareTo(new BigDecimal("100")));
    }

    @Test
    void cascade_recompute_after_price_correction() {
        // Initial: 10 @ 100, 10 @ 200 => 150. Correct the 2nd reception price to 300 => (1000+3000)/20 = 200
        var corrected = PmpCalculator.walk(List.of(entree("10", "100"), entree("10", "300")),
                PmpCalculator.PmpState.empty());

        assertEquals(0, corrected.finalState().pmp().compareTo(new BigDecimal("200.0000")));
    }

    @Test
    void rounding_is_applied_with_scale_four() {
        // 3 @ 100 then 0 (no), use 7 @ 10 + 3 @ 20 => (70+60)/10 = 13.0000
        var result = PmpCalculator.walk(List.of(entree("7", "10"), entree("3", "20")),
                PmpCalculator.PmpState.empty());

        assertEquals(0, result.finalState().pmp().compareTo(new BigDecimal("13.0000")));
    }
}

