package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.PlanningAffectationQueryService;
import com.hemodialyse.backend.application.planning.PlanningAffectationQueryService.Resultat;
import com.hemodialyse.backend.application.planning.PlanningParametresService;
import com.hemodialyse.backend.application.planning.PlanningSemaineQueryService;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.SemainePlanning;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.rest.PlanningParametresRestController.ParametresRequest;
import com.hemodialyse.backend.infrastructure.web.rest.PlanningParametresRestController.ParametresResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanningAffectationRestControllerTest {

    private final PlanningAffectationQueryService service = mock(PlanningAffectationQueryService.class);
    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);
    private final PlanningAffectationRestController controller = new PlanningAffectationRestController(service, guard);

    private static Resultat vide() {
        return new Resultat(List.of(), List.of(), List.of(), List.of(), List.of(), false);
    }

    @Test
    void should_use_the_center_validated_by_the_guard_and_forward_the_request() {
        UUID demande = UUID.randomUUID();
        UUID centre = UUID.randomUUID();
        UUID creneau = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        when(guard.requireCenter(demande)).thenReturn(CenterId.of(centre));
        when(service.proposer(eq(centre), eq(2), any(), eq(creneau), eq(null), eq(8), eq(patient), eq(true)))
                .thenReturn(vide());

        ResponseEntity<Resultat> response = controller.proposer(
                demande, 2, List.of(JourSemaine.LUNDI, JourSemaine.JEUDI), creneau, null, patient, true, 8);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        verify(service).proposer(centre, 2, EnumSet.of(JourSemaine.LUNDI, JourSemaine.JEUDI), creneau, null, 8, patient, true);
    }

    @Test
    void should_default_to_no_imposed_days_and_automatic_risk_when_none_are_given() {
        UUID centre = UUID.randomUUID();
        when(guard.requireCenter(any())).thenReturn(CenterId.of(centre));
        when(service.proposer(eq(centre), eq(3), any(), eq(null), eq(null), eq(12), eq(null), eq(null))).thenReturn(vide());

        controller.proposer(null, 3, null, null, null, null, null, 12);

        verify(service).proposer(centre, 3, Set.of(), null, null, 12, null, null);
    }

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

    @Test
    void the_parameters_endpoint_reads_and_writes_for_the_guarded_center() {
        PlanningParametresService parametresService = mock(PlanningParametresService.class);
        UUID centre = UUID.randomUUID();
        UUID salle = UUID.randomUUID();
        when(guard.requireCenter(null)).thenReturn(CenterId.of(centre));
        PlanningParametres params = new PlanningParametres(EnumSet.of(JourSemaine.LUNDI, JourSemaine.MARDI), Set.of(salle));
        when(parametresService.lire(centre)).thenReturn(params);
        when(parametresService.enregistrer(eq(centre), any(), any())).thenReturn(params);
        PlanningParametresRestController ctrl = new PlanningParametresRestController(parametresService, guard);

        ParametresResponse lu = ctrl.lire(null).getBody();
        ParametresResponse ecrit = ctrl.enregistrer(null,
                new ParametresRequest(List.of(JourSemaine.LUNDI, JourSemaine.MARDI), List.of(salle))).getBody();

        assertEquals(List.of(JourSemaine.LUNDI, JourSemaine.MARDI), lu.joursOuverts());
        assertEquals(List.of(salle), ecrit.sallesIsolement());
        verify(parametresService).enregistrer(centre, EnumSet.of(JourSemaine.LUNDI, JourSemaine.MARDI), Set.of(salle));
    }
}
