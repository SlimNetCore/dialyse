package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.ReaffectationEnIsolement;
import com.hemodialyse.backend.domain.medical.serologie.aggregate.Serologie;
import com.hemodialyse.backend.domain.medical.serologie.port.SerologieUseCase;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.MarqueurSerologique;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.ResultatSerologique;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.acl.planification.ReaffectationEnIsolementDeLaSerologie;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateSerologieRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateSerologieRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Un résultat sérologique enregistré ou corrigé redonne la main à la Planification (port publié {@code ReaffectationEnIsolement}) :
 * un patient devenu à risque est replacé en salle d'isolement pour le centre du garde, dans la transaction de la sérologie.
 */
class SerologiePlacementHookTest {

    private final UUID centre = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final SerologieUseCase useCase = mock(SerologieUseCase.class);
    private final ReaffectationEnIsolement reaffectation = mock(ReaffectationEnIsolement.class);
    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);
    private final PlatformTransactionManager gestionnaire = mock(PlatformTransactionManager.class);
    private final SerologieRestController controller;

    SerologiePlacementHookTest() {
        when(guard.requireCenter(any())).thenReturn(CenterId.of(centre));
        when(gestionnaire.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        controller = new SerologieRestController(useCase, guard, new ReaffectationEnIsolementDeLaSerologie(reaffectation, gestionnaire,
                mock(jakarta.persistence.EntityManager.class)));
    }

    private Serologie positive() {
        return Serologie.enregistrer(patient, centre, MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, null,
                null, LocalDate.of(2026, 9, 1), null, null, "Isolement");
    }

    @Test
    void creating_a_result_re_evaluates_the_placement_of_the_patient() {
        when(useCase.create(any(), eq(patient), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(positive());

        controller.create(patient, new CreateSerologieRequest(centre, "AG_HBS", "POSITIF", null, null,
                LocalDate.of(2026, 9, 1), null, null, "Isolement"));

        verify(reaffectation).siRisque(centre, patient);
    }

    @Test
    void correcting_a_result_re_evaluates_the_placement_of_the_patient() {
        UUID serologie = UUID.randomUUID();
        when(useCase.update(any(), eq(patient), eq(serologie), any(), any())).thenReturn(positive());

        controller.update(patient, serologie, new UpdateSerologieRequest(centre, "POSITIF", null));

        verify(reaffectation).siRisque(centre, patient);
    }
}
