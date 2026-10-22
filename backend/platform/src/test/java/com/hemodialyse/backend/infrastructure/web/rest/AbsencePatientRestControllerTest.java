package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.absence.AbsencePatientService;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contrôleur de la détection des absences de patients (slice A3 à venir) : le centre vient du garde. La déclaration, la
 * qualification, le rattrapage et l'annulation sont testés dans le contexte absences-patients (slice A2).
 */
class AbsencePatientRestControllerTest {

    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);
    private final AbsencePatientService service = mock(AbsencePatientService.class);
    private final UUID centre = UUID.randomUUID();
    private final LocalDate jour = LocalDate.now().minusDays(1);
    private final AbsencePatientRestController controller = new AbsencePatientRestController(service, guard);

    AbsencePatientRestControllerTest() {
        when(guard.requireCenter(any())).thenReturn(CenterId.of(centre));
    }

    @Test
    void the_catch_up_runs_on_the_guarded_center_and_reports_the_counts() {
        LocalDate debut = jour.minusDays(30);
        when(service.rattraperPeriode(centre, debut, jour)).thenReturn(new AbsencePatientService.Rattrapage(12, 2));

        var body = controller.rattraperDetection(null, new AbsencePatientRestController.PeriodeRequest(debut, jour)).getBody();

        assertEquals(12, body.creees());
        assertEquals(2, body.annulees());
    }
}
