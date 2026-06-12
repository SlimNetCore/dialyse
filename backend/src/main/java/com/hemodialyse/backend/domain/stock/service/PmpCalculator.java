package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.model.StockMovementType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure (side-effect free) PMP (poids moyen pondere / weighted average cost) calculator.
 * Walks an ordered list of movements and produces, for each movement, the PMP after it.
 *
 * <p>PMP rules:
 * <ul>
 *   <li>ENTREE: newQty = qty + q ; newValue = value + q * unitPrice ; pmp = newValue / newQty</li>
 *   <li>SORTIE: value is reduced at the current PMP ; pmp is unchanged (carried)</li>
 *   <li>AJUSTEMENT: if a unit price is provided behaves like an ENTREE, otherwise the quantity
 *       is adjusted at the current PMP</li>
 * </ul>
 * Kept pure so it can be unit-tested against historical data sets (Phase 6).
 */
public final class PmpCalculator {

    public static final int SCALE = 4;

    private PmpCalculator() {
    }

    public static PmpResult walk(List<StockMovement> orderedMovements, PmpState initial) {
        PmpState state = initial != null ? initial : PmpState.empty();
        List<PmpStep> steps = new ArrayList<>();

        for (StockMovement m : orderedMovements) {
            state = apply(state, m);
            steps.add(new PmpStep(m, state.pmp()));
        }
        return new PmpResult(steps, state);
    }

    /**
     * Like {@link #walk} but returns, for each movement, the state before and after.
     */
    public static List<DetailedStep> explain(List<StockMovement> orderedMovements, PmpState initial) {
        PmpState state = initial != null ? initial : PmpState.empty();
        List<DetailedStep> steps = new ArrayList<>();
        for (StockMovement m : orderedMovements) {
            PmpState before = state;
            state = apply(state, m);
            steps.add(new DetailedStep(m, before, state));
        }
        return steps;
    }

    public static PmpState apply(PmpState state, StockMovement m) {
        BigDecimal qty = state.quantite();
        BigDecimal value = state.valeur();
        BigDecimal pmp = state.pmp();

        BigDecimal q = m.getQuantite() != null ? m.getQuantite() : BigDecimal.ZERO;
        BigDecimal pu = m.getPrixUnitaire();

        StockMovementType type = m.getMovementType();
        boolean isEntry = type == StockMovementType.ENTREE
                || (type == StockMovementType.AJUSTEMENT && pu != null);

        if (isEntry) {
            BigDecimal newQty = qty.add(q);
            BigDecimal newValue = value.add(q.multiply(pu != null ? pu : BigDecimal.ZERO));
            BigDecimal newPmp = newQty.signum() > 0
                    ? newValue.divide(newQty, SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return new PmpState(newQty, newValue, newPmp);
        }

        // SORTIE (or AJUSTEMENT without price): valued at the current PMP,
        // and PMP must remain exactly the previous recalculated PMP.
        BigDecimal newQty = qty.subtract(q);
        BigDecimal newValue = value.subtract(q.multiply(pmp));
        if (newQty.signum() <= 0) {
            // Stock back to zero (or below): reset value, keep last PMP as reference
            return new PmpState(BigDecimal.ZERO, BigDecimal.ZERO, pmp);
        }
        return new PmpState(newQty, newValue, pmp);
    }

    public record PmpState(BigDecimal quantite, BigDecimal valeur, BigDecimal pmp) {
        public static PmpState empty() {
            return new PmpState(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public record PmpStep(StockMovement movement, BigDecimal pmpApres) {
    }

    public record PmpResult(List<PmpStep> steps, PmpState finalState) {
    }

    /**
     * Detailed step exposing the full running state after applying a movement
     * (used to build a human-readable PMP explanation).
     */
    public record DetailedStep(StockMovement movement, PmpState stateBefore, PmpState stateAfter) {
    }
}


