package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.infirmier.PresenceInfirmierQueryService;
import com.hemodialyse.backend.application.infirmier.PresenceInfirmierQueryService.ChargePage;
import com.hemodialyse.backend.domain.infirmier.model.Presence.AlertePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.ChargeInfirmier;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.reporting.PresenceInfirmierReportService;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.rest.PresenceInfirmierRestController.ChargeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contrôleur de présence des infirmiers (slice P4) : le centre vient toujours du garde, les paramètres invalides sont refusés. Les
 * contrôleurs du roulement, des absences, de « Mon planning » et des remplacements sont ceux du contexte Planification (slice P3).
 */
class PresenceInfirmierRestControllerTest {

    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);
    private final UUID centre = UUID.randomUUID();

    PresenceInfirmierRestControllerTest() {
        when(guard.requireCenter(any())).thenReturn(CenterId.of(centre));
    }

    @Test
    void the_presence_endpoints_use_the_guarded_center_and_the_default_horizon() {
        PresenceInfirmierQueryService presence = mock(PresenceInfirmierQueryService.class);
        var controller = new PresenceInfirmierRestController(presence, mock(PresenceInfirmierReportService.class), guard);
        AlertePresence alerte = new AlertePresence(LocalDate.of(2026, 10, 5), JourSemaine.LUNDI, UUID.randomUUID(),
                UUID.randomUUID(), 5, 2, 1, List.of());
        when(presence.alertes(eq(centre), any(), eq(14))).thenReturn(List.of(alerte));

        ResponseEntity<List<AlertePresence>> alertes = controller.alertes(null, 14);

        assertEquals(List.of(alerte), alertes.getBody());
        assertTrue(alertes.getHeaders().getCacheControl().contains("no-store"));
    }

    @Test
    void the_workload_is_paginated_and_an_invalid_month_is_rejected() {
        PresenceInfirmierQueryService presence = mock(PresenceInfirmierQueryService.class);
        var controller = new PresenceInfirmierRestController(presence, mock(PresenceInfirmierReportService.class), guard);
        ChargeInfirmier c = new ChargeInfirmier(UUID.randomUUID(), "Amrani", 8, 1, 0, 9);
        when(presence.charge(centre, YearMonth.of(2026, 10), 0, 20))
                .thenReturn(new ChargePage(PagedResult.of(List.of(c), 1, 0, 20), 9.0));

        ChargeResponse r = controller.charge(null, "2026-10", 0, 20).getBody();

        assertEquals(1, r.total());
        assertEquals(9.0, r.moyenne());
        assertThrows(IllegalArgumentException.class, () -> controller.charge(null, "octobre", 0, 20));
    }
}
