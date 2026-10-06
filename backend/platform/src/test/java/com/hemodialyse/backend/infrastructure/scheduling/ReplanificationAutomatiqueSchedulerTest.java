package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.planning.optimisation.ReplanificationAutomatiqueService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplanificationAutomatiqueSchedulerTest {

    private final ReplanificationAutomatiqueService service = mock(ReplanificationAutomatiqueService.class);
    private final ReplanificationAutomatiqueScheduler scheduler = new ReplanificationAutomatiqueScheduler(service);

    @Test
    void should_replan_every_enabled_center_even_when_one_fails() {
        UUID enErreur = UUID.randomUUID();
        UUID suivant = UUID.randomUUID();
        when(service.centres()).thenReturn(List.of(enErreur, suivant));
        doThrow(new IllegalStateException("base indisponible")).when(service).replanifier(eq(enErreur), any());

        scheduler.replanifier();

        verify(service).replanifier(eq(suivant), any());
    }
}
