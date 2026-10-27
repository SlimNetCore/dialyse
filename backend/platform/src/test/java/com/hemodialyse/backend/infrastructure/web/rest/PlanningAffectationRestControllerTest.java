package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.PlanningSemaineQueryService;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.SemainePlanning;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P5 : l'aide au placement ({@code GET /planning/affectations}) est servie par {@code PropositionsControleur} du module
 * Planification (ses tests sont dans le module) ; il ne reste ici que le test de la route de la semaine, qui n'est pas migrée.
 */
class PlanningAffectationRestControllerTest {

    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);

    @Test
    void the_week_endpoint_uses_the_guarded_center() {
        PlanningSemaineQueryService semaineService = mock(PlanningSemaineQueryService.class);
        UUID centre = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 30);
        SemainePlanning semaine = new SemainePlanning(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 3),
                List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        when(guard.requireCenter(null)).thenReturn(CenterId.of(centre));
        when(semaineService.semaine(centre, date)).thenReturn(semaine);

        ResponseEntity<SemainePlanning> response = new PlanningSemaineRestController(semaineService, guard).semaine(null, date);

        assertEquals(semaine, response.getBody());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
    }
}
