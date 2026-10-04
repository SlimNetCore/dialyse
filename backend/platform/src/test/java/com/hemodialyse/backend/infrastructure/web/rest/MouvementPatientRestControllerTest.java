package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.patient.MouvementPatientQueryService;
import com.hemodialyse.backend.domain.patient.model.MouvementLigne;
import com.hemodialyse.backend.domain.patient.model.MouvementPatient;
import com.hemodialyse.backend.domain.patient.model.TypeMouvementPatient;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.rest.MouvementPatientRestController.MouvementResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contrôleur des mouvements : le centre vient du garde, la liste est paginée et filtrée.
 */
class MouvementPatientRestControllerTest {

    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);
    private final MouvementPatientQueryService service = mock(MouvementPatientQueryService.class);
    private final UUID centre = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final MouvementPatientRestController controller = new MouvementPatientRestController(service, guard);

    MouvementPatientRestControllerTest() {
        when(guard.requireCenter(any())).thenReturn(CenterId.of(centre));
    }

    @Test
    void the_list_is_scoped_to_the_guarded_center_filtered_and_paginated() {
        LocalDate jour = LocalDate.of(2026, 10, 4);
        MouvementPatient m = new MouvementPatient(UUID.randomUUID(), centre, patient, TypeMouvementPatient.DECES, jour,
                "PERMANENT", "DECEDE", null, null, null, "LUNDI", false, Instant.now());
        when(service.lister(centre, patient, TypeMouvementPatient.DECES, jour, null, 1, 10)).thenReturn(
                PagedResult.of(List.of(new MouvementLigne(m, "Ali Ben", "PAT-1", "Salle 1", "Matin", "G01")), 11, 1, 10));

        PagedResult<MouvementResponse> page =
                controller.lister(null, patient, TypeMouvementPatient.DECES, jour, null, 1, 10).getBody();

        assertEquals(11, page.total());
        assertEquals(1, page.page());
        MouvementResponse r = page.items().getFirst();
        assertEquals("Ali Ben", r.patientNom());
        assertEquals("Salle 1", r.salle());
        assertEquals(TypeMouvementPatient.DECES, r.type());
        assertEquals(jour, r.dateEffet());
    }
}
