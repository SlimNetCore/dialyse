package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationRunRepositoryPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OptimisationPlanningRecoveryTest {

    private final OptimisationRunRepositoryPort runs = mock(OptimisationRunRepositoryPort.class);
    private final OptimisationPlanningRecovery recovery = new OptimisationPlanningRecovery(runs);

    @Test
    void should_fail_the_runs_left_in_progress_by_a_previous_server() {
        recovery.interrompreLesCalculsOrphelins();

        verify(runs).interrompreEnCours(OptimisationPlanningRecovery.MOTIF);
    }

    @Test
    void should_not_prevent_the_startup_when_the_history_is_unreachable() {
        when(runs.interrompreEnCours(OptimisationPlanningRecovery.MOTIF)).thenThrow(new IllegalStateException("db"));

        assertDoesNotThrow(recovery::interrompreLesCalculsOrphelins);
    }
}
