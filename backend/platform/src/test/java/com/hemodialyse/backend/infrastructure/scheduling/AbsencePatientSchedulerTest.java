package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.absence.AbsencePatientService;
import com.hemodialyse.backend.application.absence.AbsencePatientService.Synthese;
import com.hemodialyse.backend.application.notification.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbsencePatientSchedulerTest {

    private final AbsencePatientService service = mock(AbsencePatientService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AbsencePatientScheduler scheduler = new AbsencePatientScheduler(service, notifications);
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void notifies_only_the_centres_with_absences_to_qualify() {
        when(service.centres()).thenReturn(List.of(a, b));
        when(service.controlerCentre(a, AbsencePatientScheduler.JOURS_A_CONTROLER)).thenReturn(new Synthese(3, 1));
        when(service.controlerCentre(b, AbsencePatientScheduler.JOURS_A_CONTROLER)).thenReturn(new Synthese(0, 0));

        scheduler.controlerAbsences();

        verify(notifications).notifyAbsencesAQualifier(a, 3, 1);
        verify(notifications, never()).notifyAbsencesAQualifier(org.mockito.ArgumentMatchers.eq(b), anyLong(), anyLong());
    }

    @Test
    void a_failing_centre_does_not_block_the_others() {
        when(service.centres()).thenReturn(List.of(a, b));
        when(service.controlerCentre(a, AbsencePatientScheduler.JOURS_A_CONTROLER)).thenThrow(new IllegalStateException("boom"));
        when(service.controlerCentre(b, AbsencePatientScheduler.JOURS_A_CONTROLER)).thenReturn(new Synthese(2, 0));

        scheduler.controlerAbsences();

        verify(notifications).notifyAbsencesAQualifier(b, 2, 0);
        verify(service, org.mockito.Mockito.times(2)).controlerCentre(org.mockito.ArgumentMatchers.any(), anyInt());
    }
}
