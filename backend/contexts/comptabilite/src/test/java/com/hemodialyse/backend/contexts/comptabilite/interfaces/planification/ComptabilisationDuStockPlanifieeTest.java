package com.hemodialyse.backend.contexts.comptabilite.interfaces.planification;

import com.hemodialyse.backend.contexts.comptabilite.application.stock.ComptabiliserLeStock;
import com.hemodialyse.backend.contexts.comptabilite.application.stock.ComptabiliserLeStock.Synchronisation;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tâche de nuit : chaque centre est comptabilisé sur la fenêtre glissante qui finit aujourd'hui, et l'échec de l'un
 * n'arrête pas les autres.
 */
class ComptabilisationDuStockPlanifieeTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 9);
    private final ComptabiliserLeStock comptabilisation = mock(ComptabiliserLeStock.class);
    private final ComptabilisationDuStockPlanifiee planifiee = new ComptabilisationDuStockPlanifiee(comptabilisation,
            Clock.fixed(AUJOURDHUI.atTime(3, 15).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @Test
    void should_post_every_centre_over_the_sliding_window_even_if_another_one_fails() {
        CentreId enErreur = new CentreId(UUID.randomUUID());
        CentreId sain = new CentreId(UUID.randomUUID());
        when(comptabilisation.centres()).thenReturn(List.of(enErreur, sain));
        when(comptabilisation.synchroniser(eq(enErreur), any(), any(), any())).thenThrow(new IllegalStateException("base"));
        when(comptabilisation.synchroniser(eq(sain), any(), any(), any()))
                .thenReturn(new Synchronisation(1, 2, 0, 0, 1, List.of()));

        planifiee.comptabiliserLeStock();

        verify(comptabilisation).synchroniser(eq(enErreur), any(), any(), any());
        verify(comptabilisation).synchroniser(sain, AUJOURDHUI.minusDays(
                ComptabilisationDuStockPlanifiee.JOURS_A_COMPTABILISER), AUJOURDHUI, AUJOURDHUI);
    }
}
