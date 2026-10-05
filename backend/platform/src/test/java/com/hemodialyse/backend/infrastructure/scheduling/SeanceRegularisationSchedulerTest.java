package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.application.seance.SeanceRegularisationService;
import com.hemodialyse.backend.application.seance.SeanceRegularisationService.Resume;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeanceRegularisationSchedulerTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 5);

    private final SeanceRegularisationService regularisation = mock(SeanceRegularisationService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final SeanceRegularisationScheduler scheduler =
            new SeanceRegularisationScheduler(mock(JdbcTemplate.class), regularisation, notifications);

    @Test
    void reminds_the_center_with_the_count_and_the_oldest_forgotten_day() {
        UUID centre = UUID.randomUUID();
        when(regularisation.aRegulariser(centre, AUJOURDHUI)).thenReturn(new Resume(3, AUJOURDHUI.minusDays(4)));

        scheduler.rappelerCentre(centre, AUJOURDHUI);

        verify(notifications).notifySeancesARegulariser(centre, 3, AUJOURDHUI.minusDays(4));
    }

    @Test
    void stays_silent_when_no_session_was_forgotten() {
        UUID centre = UUID.randomUUID();
        when(regularisation.aRegulariser(centre, AUJOURDHUI)).thenReturn(new Resume(0, null));

        scheduler.rappelerCentre(centre, AUJOURDHUI);

        verify(notifications, never()).notifySeancesARegulariser(any(), anyLong(), any());
    }

    @Test
    void a_failing_center_does_not_break_the_job() {
        UUID centre = UUID.randomUUID();
        when(regularisation.aRegulariser(eq(centre), any())).thenThrow(new IllegalStateException("boom"));

        scheduler.rappelerCentre(centre, AUJOURDHUI);

        verify(notifications, never()).notifySeancesARegulariser(any(), anyLong(), any());
    }
}
