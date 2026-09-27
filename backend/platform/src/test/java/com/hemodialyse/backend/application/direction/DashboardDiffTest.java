package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DashboardDiff.Change;
import com.hemodialyse.backend.application.direction.DashboardDiff.Snapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardDiffTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void identical_snapshots_produce_no_change() {
        Snapshot s = new Snapshot().put(A, "A", DashboardDiff.SEANCES, "seances", 12);
        assertTrue(DashboardDiff.diff(s, new Snapshot().put(A, "A", DashboardDiff.SEANCES, "seances", 12)).isEmpty());
    }

    @Test
    void numerically_equal_values_are_not_a_change() {
        Snapshot before = new Snapshot().put(A, "A", DashboardDiff.FINANCE, "caTtc", new BigDecimal("100.0"));
        Snapshot after = new Snapshot().put(A, "A", DashboardDiff.FINANCE, "caTtc", new BigDecimal("100.00"));
        assertTrue(DashboardDiff.diff(before, after).isEmpty());
    }

    @Test
    void reports_each_changed_indicator_with_before_and_after() {
        Snapshot before = new Snapshot()
                .put(A, "A", DashboardDiff.SEANCES, "seances", 12)
                .put(B, "B", DashboardDiff.FINANCE, "caTtc", new BigDecimal("500"));
        Snapshot after = new Snapshot()
                .put(A, "A", DashboardDiff.SEANCES, "seances", 13)
                .put(B, "B", DashboardDiff.FINANCE, "caTtc", new BigDecimal("500"));
        List<Change> changes = DashboardDiff.diff(before, after);
        assertEquals(1, changes.size());
        assertEquals("seances", changes.get(0).name());
        assertEquals(new BigDecimal("12"), changes.get(0).before());
        assertEquals(new BigDecimal("13"), changes.get(0).after());
        assertEquals("A", changes.get(0).centre());
    }

    @Test
    void a_masked_indicator_that_becomes_public_is_not_reported() {
        Snapshot before = new Snapshot(); // patients masqués (moins de 5) : absents de l'instantané
        Snapshot after = new Snapshot().put(A, "A", DashboardDiff.PATIENTS, "patients", 5);
        assertTrue(DashboardDiff.diff(before, after).isEmpty());
        assertTrue(DashboardDiff.diff(after, before).isEmpty());
    }

    @Test
    void alerts_raised_and_cleared_are_reported() {
        Snapshot before = new Snapshot().put(A, "A", DashboardDiff.ALERTES, "LOTS_PERIMES", 1);
        Snapshot after = new Snapshot().put(A, "A", DashboardDiff.ALERTES, "STOCK_SOUS_SEUIL", 2);
        List<Change> changes = DashboardDiff.diff(before, after);
        assertEquals(2, changes.size());
        Change raised = changes.stream().filter(c -> c.name().equals("STOCK_SOUS_SEUIL")).findFirst().orElseThrow();
        Change cleared = changes.stream().filter(c -> c.name().equals("LOTS_PERIMES")).findFirst().orElseThrow();
        assertEquals(0, raised.before().signum());
        assertEquals(new BigDecimal("2"), raised.after());
        assertEquals(0, cleared.after().signum());
    }
}
