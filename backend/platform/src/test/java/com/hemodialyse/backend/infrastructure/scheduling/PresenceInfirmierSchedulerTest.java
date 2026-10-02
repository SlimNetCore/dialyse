package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.infirmier.PresenceInfirmierQueryService;
import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.infirmier.model.Presence.AlertePresence;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceInfirmierSchedulerTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 2);

    private final PresenceInfirmierQueryService presence = mock(PresenceInfirmierQueryService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final PresenceInfirmierScheduler scheduler =
            new PresenceInfirmierScheduler(mock(JdbcTemplate.class), presence, notifications);

    private static AlertePresence alerte(LocalDate date) {
        return new AlertePresence(date, JourSemaine.LUNDI, UUID.randomUUID(), UUID.randomUUID(), 5, 2, 1, List.of());
    }

    @Test
    void should_notify_the_center_with_the_count_and_the_first_date_of_understaffed_slots() {
        UUID centre = UUID.randomUUID();
        when(presence.alertes(centre, AUJOURDHUI, PresenceInfirmierScheduler.HORIZON_JOURS))
                .thenReturn(List.of(alerte(AUJOURDHUI.plusDays(2)), alerte(AUJOURDHUI.plusDays(5))));

        scheduler.controlerCentre(centre, AUJOURDHUI);

        verify(notifications).notifyPresenceSousEffectif(centre, 2, AUJOURDHUI.plusDays(2));
    }

    @Test
    void should_stay_silent_when_every_slot_is_covered() {
        UUID centre = UUID.randomUUID();
        when(presence.alertes(centre, AUJOURDHUI, PresenceInfirmierScheduler.HORIZON_JOURS)).thenReturn(List.of());

        scheduler.controlerCentre(centre, AUJOURDHUI);

        verify(notifications, never()).notifyPresenceSousEffectif(any(), anyInt(), any());
    }

    @Test
    void a_failing_center_does_not_raise_and_notifies_nobody() {
        UUID centre = UUID.randomUUID();
        when(presence.alertes(centre, AUJOURDHUI, PresenceInfirmierScheduler.HORIZON_JOURS))
                .thenThrow(new IllegalStateException("boom"));

        scheduler.controlerCentre(centre, AUJOURDHUI);

        verify(notifications, never()).notifyPresenceSousEffectif(any(), anyInt(), any());
    }
}
