package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.PlacementPatientService;
import com.hemodialyse.backend.domain.medical.serologie.aggregate.Serologie;
import com.hemodialyse.backend.domain.medical.serologie.port.SerologieUseCase;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.MarqueurSerologique;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.ResultatSerologique;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateSerologieRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateSerologieRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Un résultat sérologique enregistré ou corrigé redonne la main au placement : un patient devenu à risque est replacé
 * en salle d'isolement pour le centre du garde.
 */
class SerologiePlacementHookTest {

    private final UUID centre = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final SerologieUseCase useCase = mock(SerologieUseCase.class);
    private final PlacementPatientService placement = mock(PlacementPatientService.class);
    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);
    private final SerologieRestController controller = new SerologieRestController(useCase, guard, placement);

    SerologiePlacementHookTest() {
        when(guard.requireCenter(any())).thenReturn(CenterId.of(centre));
    }

    private Serologie positive() {
        return Serologie.enregistrer(patient, centre, MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, null,
                null, LocalDate.of(2026, 9, 1), null, null, "Isolement");
    }

    @Test
    void creating_a_result_re_evaluates_the_placement_of_the_patient() {
        Serologie resultat = positive();
        when(useCase.create(any(), eq(patient), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(resultat);

        controller.create(patient, new CreateSerologieRequest(centre, "AG_HBS", "POSITIF", null, null,
                LocalDate.of(2026, 9, 1), null, null, "Isolement"));

        verify(placement).reaffecterSiRisque(centre, patient);
    }

    @Test
    void correcting_a_result_re_evaluates_the_placement_of_the_patient() {
        UUID serologie = UUID.randomUUID();
        Serologie resultat = positive();
        when(useCase.update(any(), eq(patient), eq(serologie), any(), any())).thenReturn(resultat);

        controller.update(patient, serologie, new UpdateSerologieRequest(centre, "POSITIF", null));

        verify(placement).reaffecterSiRisque(centre, patient);
    }
}
