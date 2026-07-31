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

    @Test
    void exit_is_valued_at_previous_recomputed_pmp() {
        // 80 @ 90 -> PMP 90 ; then 12 @ 200 -> PMP = (7200 + 2400) / 92 = 104.3478 ;
        // then SORTIE 4 -> must be valued at the PMP recomputed just before it (104.3478),
        // and the PMP after the exit stays 104.3478.
        var steps = PmpCalculator.explain(
                List.of(entree("80", "90"), entree("12", "200"), sortie("4")),
                PmpCalculator.PmpState.empty());

        var entree2 = steps.get(1);
        assertEquals(0, entree2.stateAfter().pmp().compareTo(new BigDecimal("104.3478")));

        var sortie = steps.get(2);
        // PMP "just before" the exit (valorisation reference)
        assertEquals(0, sortie.stateBefore().pmp().compareTo(new BigDecimal("104.3478")));
        // PMP after the exit is unchanged
        assertEquals(0, sortie.stateAfter().pmp().compareTo(new BigDecimal("104.3478")));
        // Quantity after exit: 92 - 4 = 88
        assertEquals(0, sortie.stateAfter().quantite().compareTo(new BigDecimal("88")));
    }
}

