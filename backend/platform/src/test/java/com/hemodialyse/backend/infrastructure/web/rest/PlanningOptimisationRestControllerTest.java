package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.optimisation.OptimisationApplicationService;
import com.hemodialyse.backend.application.planning.optimisation.OptimisationPlanningService;
import com.hemodialyse.backend.domain.planning.optimisation.model.ObjectifInfirmiers;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.TenantScope;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentPrinter;
import com.hemodialyse.backend.infrastructure.reporting.PlanningOptimiseReportService;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.rest.PlanningOptimisationRestController.LancerRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanningOptimisationRestControllerTest {

    private final OptimisationPlanningService planification = mock(OptimisationPlanningService.class);
    private final OptimisationApplicationService application = mock(OptimisationApplicationService.class);
    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);
    private final PlanningOptimiseReportService rapport = mock(PlanningOptimiseReportService.class);
    private final PlanningOptimisationRestController controller =
            new PlanningOptimisationRestController(planification, application, guard, rapport);
    private final UUID centre = UUID.randomUUID();

    private RunOptimisation run() {
        return RunOptimisation.demarrer(centre, ParametresOptimisation.parDefaut(PerimetreOptimisation.PATIENTS,
                LocalDate.of(2026, 9, 27)), "u", "e", Instant.parse("2026-10-01T08:00:00Z"));
    }

    private void sessionDuCentre() {
        when(guard.requireCenter(any())).thenReturn(CenterId.of(centre));
        when(guard.currentScope()).thenReturn(new TenantScope(centre, "user-7", Set.of("ROLE_ADMIN")));
    }

    @Test
    void should_launch_with_the_center_and_user_of_the_session_and_answer_accepted() {
        sessionDuCentre();
        RunOptimisation run = run();
        when(planification.lancer(eq(centre), any(), eq("user-7"))).thenReturn(run);

        ResponseEntity<RunOptimisation> reponse = controller.lancer(UUID.randomUUID(),
                new LancerRequest(PerimetreOptimisation.COMPLET, LocalDate.of(2026, 9, 30), null, 30, 7,
                        ObjectifInfirmiers.ECONOMIE, 1, 5));

        assertThat(reponse.getStatusCode().value()).isEqualTo(202);
        assertThat(reponse.getBody()).isSameAs(run);
        assertThat(reponse.getHeaders().getCacheControl()).contains("no-store");
        org.mockito.ArgumentCaptor<ParametresOptimisation> parametres = org.mockito.ArgumentCaptor.forClass(ParametresOptimisation.class);
        verify(planification).lancer(eq(centre), parametres.capture(), eq("user-7"));
        assertThat(parametres.getValue()).satisfies(p -> {
            assertThat(p.perimetre()).isEqualTo(PerimetreOptimisation.COMPLET);
            assertThat(p.debutSemaine()).isEqualTo(LocalDate.of(2026, 9, 27)); // ramené au dimanche
            assertThat(p.nbSemaines()).isEqualTo(1);
            assertThat(p.dureeMaxSecondes()).isEqualTo(30);
            assertThat(p.stabilite()).isEqualTo(7);
            assertThat(p.objectif()).isEqualTo(ObjectifInfirmiers.ECONOMIE);
            assertThat(p.maxVacationsParJour()).isEqualTo(1);
            assertThat(p.maxVacationsParSemaine()).isEqualTo(5);
        });
    }

    @Test
    void should_apply_defaults_for_the_omitted_parameters() {
        ParametresOptimisation p = new LancerRequest(PerimetreOptimisation.ROULEMENT, null, null, null, null, null, null,
                null).versParametres();

        assertThat(p.nbSemaines()).isEqualTo(1);
        assertThat(p.dureeMaxSecondes()).isEqualTo(ParametresOptimisation.DUREE_PAR_DEFAUT);
        assertThat(p.stabilite()).isEqualTo(ParametresOptimisation.STABILITE_PAR_DEFAUT);
        assertThat(p.objectif()).isEqualTo(ObjectifInfirmiers.EQUITE);
        assertThat(p.maxVacationsParJour()).isEqualTo(ParametresOptimisation.VACATIONS_JOUR_PAR_DEFAUT);
        assertThat(p.maxVacationsParSemaine()).isEqualTo(ParametresOptimisation.VACATIONS_SEMAINE_PAR_DEFAUT);
    }

    @Test
    void should_reject_out_of_range_parameters_as_a_bad_request() {
        LancerRequest trop = new LancerRequest(PerimetreOptimisation.PATIENTS, null, null, 9999, null, null, null, null);

        assertThatThrownBy(trop::versParametres).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_page_the_history_within_the_center_of_the_session() {
        sessionDuCentre();
        when(planification.historique(centre, 2, 5)).thenReturn(PagedResult.of(List.of(run()), 11, 2, 5));

        ResponseEntity<PagedResult<RunOptimisation>> reponse = controller.historique(null, 2, 5);

        assertThat(reponse.getBody().total()).isEqualTo(11);
        assertThat(reponse.getBody().items()).hasSize(1);
    }

    @Test
    void should_consult_stop_and_apply_inside_the_center_of_the_session() {
        sessionDuCentre();
        RunOptimisation run = run();
        when(planification.consulter(centre, run.id())).thenReturn(run);
        when(planification.arreter(centre, run.id())).thenReturn(run);
        when(application.appliquer(centre, run.id())).thenReturn(run);

        assertThat(controller.consulter(null, run.id()).getBody()).isSameAs(run);
        assertThat(controller.arreter(null, run.id()).getStatusCode().value()).isEqualTo(202);
        assertThat(controller.appliquer(null, run.id()).getBody()).isSameAs(run);
    }

    @Test
    void should_delete_a_run_inside_the_center_of_the_session() {
        sessionDuCentre();
        UUID id = UUID.randomUUID();

        assertThat(controller.supprimer(null, id).getStatusCode().value()).isEqualTo(204);

        verify(planification).supprimer(centre, id);
    }

    @Test
    void should_serve_the_calendar_of_the_first_week_by_default_inside_the_center_of_the_session() {
        sessionDuCentre();
        UUID id = UUID.randomUUID();
        LocalDate s1 = LocalDate.of(2026, 9, 27);
        when(planification.semainesCalendrier(centre, id)).thenReturn(List.of(s1, s1.plusWeeks(1)));
        when(planification.calendrier(centre, id, s1)).thenReturn(List.of());

        ResponseEntity<PlanningOptimisationRestController.CalendrierResponse> reponse =
                controller.calendrier(UUID.randomUUID(), id, null);

        assertThat(reponse.getBody().semaines()).containsExactly(s1, s1.plusWeeks(1));
        assertThat(reponse.getBody().semaine()).isEqualTo(s1);
        assertThat(reponse.getHeaders().getCacheControl()).contains("no-store");
        verify(planification).calendrier(centre, id, s1);
    }

    @Test
    void should_serve_the_requested_week_and_nothing_when_the_run_has_no_calendar() {
        sessionDuCentre();
        UUID id = UUID.randomUUID();
        LocalDate s2 = LocalDate.of(2026, 10, 4);
        when(planification.semainesCalendrier(centre, id)).thenReturn(List.of(LocalDate.of(2026, 9, 27), s2));
        controller.calendrier(null, id, s2);
        verify(planification).calendrier(centre, id, s2);

        UUID ancien = UUID.randomUUID();
        when(planification.semainesCalendrier(centre, ancien)).thenReturn(List.of());
        var vide = controller.calendrier(null, ancien, null).getBody();
        assertThat(vide.semaines()).isEmpty();
        assertThat(vide.semaine()).isNull();
        assertThat(vide.cases()).isEmpty();
    }

    @Test
    void should_print_the_calendar_as_a_pdf_html_or_excel_document_of_the_center() {
        sessionDuCentre();
        UUID id = UUID.randomUUID();
        byte[] contenu = {1, 2, 3};
        when(rapport.imprimer(centre, id)).thenReturn(new ModeleDocumentPrinter.Document(contenu, "PDF"));

        ResponseEntity<byte[]> pdf = controller.imprimer(null, id);

        assertThat(pdf.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(pdf.getHeaders().getContentDisposition().getFilename()).isEqualTo("planning-propose-" + id + ".pdf");
        assertThat(pdf.getBody()).isEqualTo(contenu);

        when(rapport.imprimer(centre, id)).thenReturn(new ModeleDocumentPrinter.Document(contenu, "HTML"));
        assertThat(controller.imprimer(null, id).getHeaders().getContentType().toString()).startsWith("text/html");

        when(rapport.imprimer(centre, id)).thenReturn(new ModeleDocumentPrinter.Document(contenu, "EXCEL"));
        assertThat(controller.imprimer(null, id).getHeaders().getContentDisposition().getFilename()).endsWith(".xlsx");
    }

    @Test
    void should_reserve_the_application_to_the_administrator_and_the_rest_to_the_planning_staff() throws Exception {
        PreAuthorize classe = PlanningOptimisationRestController.class.getAnnotation(PreAuthorize.class);
        PreAuthorize application = PlanningOptimisationRestController.class
                .getMethod("appliquer", UUID.class, UUID.class).getAnnotation(PreAuthorize.class);

        assertThat(classe.value()).isEqualTo("hasAnyRole('ADMIN','SECRETAIRE')");
        assertThat(application.value()).isEqualTo("hasRole('ADMIN')");
    }
}
