package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.comptabilite.ComptabiliteStockApplicationService;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteStockUseCase.Synchronisation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tâche de nuit : chaque centre est comptabilisé sur la fenêtre glissante, et l'échec de l'un n'arrête pas les autres.
 */
class ComptabiliteStockSchedulerTest {

    private final ComptabiliteStockApplicationService service = mock(ComptabiliteStockApplicationService.class);
    private final ComptabiliteStockScheduler scheduler = new ComptabiliteStockScheduler(service);

    @Test
    void every_centre_is_posted_over_the_sliding_window_even_if_another_one_fails() {
        UUID enErreur = UUID.randomUUID();
        UUID sain = UUID.randomUUID();
        when(service.centres()).thenReturn(List.of(enErreur, sain));
        when(service.synchroniser(eq(enErreur), any(), any(), any())).thenThrow(new IllegalStateException("base"));
        when(service.synchroniser(eq(sain), any(), any(), any())).thenReturn(new Synchronisation(1, 2, 0, 0, 1));

        scheduler.comptabiliserStock();

        verify(service).synchroniser(eq(enErreur), any(), any(), any());
        verify(service).synchroniser(eq(sain), any(), any(), any());
    }

    @Test
    void the_window_ends_today_and_covers_the_configured_number_of_days() {
        UUID centre = UUID.randomUUID();
        LocalDate aujourdhui = LocalDate.of(2026, 10, 9);
        when(service.synchroniser(any(), any(), any(), any())).thenReturn(new Synchronisation(0, 0, 0, 0, 0));

        scheduler.comptabiliserCentre(centre, aujourdhui);

        verify(service).synchroniser(centre, aujourdhui.minusDays(ComptabiliteStockScheduler.JOURS_A_COMPTABILISER),
                aujourdhui, aujourdhui);
    }
}
