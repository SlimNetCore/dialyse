package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.planning.port.GenerateurImpactPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Un générateur de dialyse en service qui devient indisponible alerte l'administration avec ses patients ; rien dans les
 * autres cas.
 */
class GenerateurIndisponibleServiceTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private static final UUID GENERATEUR = UUID.randomUUID();

    private final GenerateurImpactPort impact = mock(GenerateurImpactPort.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final GenerateurIndisponibleService service = new GenerateurIndisponibleService(impact, notifications);

    @Test
    void should_alert_with_the_patients_placed_on_a_dialysis_generator_that_goes_out_of_service() {
        when(impact.patientsPlacesSur(eq(CENTRE), eq(GENERATEUR), any())).thenReturn(List.of("BENALI Karim"));

        service.signaler("GENERATEUR_DIALYSE", CENTRE, GENERATEUR, "A-G1", "HORS_SERVICE", "EN_SERVICE");

        verify(notifications).notifyGenerateurIndisponible(CENTRE, "A-G1", "HORS_SERVICE", List.of("BENALI Karim"));
    }

    @Test
    void should_ignore_other_equipment_and_changes_that_do_not_make_a_working_generator_unavailable() {
        service.signaler("RO_REVERSE_OSMOSIS", CENTRE, GENERATEUR, "A-G1", "HORS_SERVICE", "EN_SERVICE");
        service.signaler("GENERATEUR_DIALYSE", CENTRE, GENERATEUR, "A-G1", "EN_SERVICE", "EN_SERVICE");
        service.signaler("GENERATEUR_DIALYSE", CENTRE, GENERATEUR, "A-G1", "EN_SERVICE", null);

        verifyNoInteractions(notifications);
    }

    @Test
    void should_not_alert_again_for_a_generator_that_was_already_unavailable() {
        service.signaler("GENERATEUR_DIALYSE", CENTRE, GENERATEUR, "A-G1", "EN_MAINTENANCE", "HORS_SERVICE");

        verify(notifications, never()).notifyGenerateurIndisponible(any(), any(), any(), any());
    }

    @Test
    void should_never_fail_the_status_change_when_the_alert_cannot_be_built() {
        doThrow(new IllegalStateException("base inaccessible")).when(impact).patientsPlacesSur(any(), any(), any());

        service.signaler("GENERATEUR_DIALYSE", CENTRE, GENERATEUR, "A-G1", "HORS_SERVICE", "EN_SERVICE");

        verify(notifications, never()).notifyGenerateurIndisponible(any(), any(), any(), any());
    }
}
