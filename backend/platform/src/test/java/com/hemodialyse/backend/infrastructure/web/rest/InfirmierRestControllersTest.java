package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.infirmier.AbsenceInfirmierService;
import com.hemodialyse.backend.application.infirmier.AffectationInfirmierService;
import com.hemodialyse.backend.application.infirmier.CompteInfirmierService;
import com.hemodialyse.backend.application.infirmier.InfirmierService;
import com.hemodialyse.backend.application.infirmier.InfirmierService.InfirmierDetail;
import com.hemodialyse.backend.application.infirmier.PresenceInfirmierQueryService;
import com.hemodialyse.backend.application.infirmier.PresenceInfirmierQueryService.ChargePage;
import com.hemodialyse.backend.application.infirmier.RemplacementInfirmierService;
import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Infirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.AlertePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.ChargeInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.reporting.PresenceInfirmierReportService;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.rest.AbsenceInfirmierRestController.AbsenceRequest;
import com.hemodialyse.backend.infrastructure.web.rest.AbsenceInfirmierRestController.AbsenceResponse;
import com.hemodialyse.backend.infrastructure.web.rest.InfirmierRestController.AffectationRequest;
import com.hemodialyse.backend.infrastructure.web.rest.InfirmierRestController.InfirmierRequest;
import com.hemodialyse.backend.infrastructure.web.rest.InfirmierRestController.InfirmierResponse;
import com.hemodialyse.backend.infrastructure.web.rest.PresenceInfirmierRestController.ChargeResponse;
import com.hemodialyse.backend.infrastructure.web.rest.PresenceInfirmierRestController.RemplacementRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contrôleurs du personnel soignant : le centre vient toujours du garde (jamais du corps de la requête), les listes
 * sont paginées et les paramètres invalides sont refusés.
 */
class InfirmierRestControllersTest {

    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);
    private final UUID centre = UUID.randomUUID();

    InfirmierRestControllersTest() {
        when(guard.requireCenter(any())).thenReturn(CenterId.of(centre));
    }

    @Test
    void the_nurse_list_is_paginated_and_scoped_to_the_guarded_center() {
        InfirmierService infirmiers = mock(InfirmierService.class);
        Infirmier i = Infirmier.creer(centre, "M1", "Amrani", null, null, QualificationInfirmier.MAJOR, true);
        AffectationInfirmier a = AffectationInfirmier.creer(centre, i.id(), UUID.randomUUID(), UUID.randomUUID(),
                EnumSet.of(JourSemaine.MERCREDI, JourSemaine.LUNDI));
        when(infirmiers.lister(centre, 1, 5)).thenReturn(PagedResult.of(List.of(new InfirmierDetail(i, List.of(a))), 6, 1, 5));
        var controller = new InfirmierRestController(infirmiers, mock(AffectationInfirmierService.class),
                mock(CompteInfirmierService.class), guard);

        PagedResult<InfirmierResponse> page = controller.lister(null, 1, 5).getBody();

        assertEquals(6, page.total());
        assertEquals(1, page.page());
        assertEquals("Amrani", page.items().get(0).nom());
        assertEquals(List.of(JourSemaine.LUNDI, JourSemaine.MERCREDI), page.items().get(0).affectations().get(0).jours());
    }

    @Test
    void creating_a_nurse_uses_the_guarded_center_and_rejects_an_unknown_qualification() {
        InfirmierService infirmiers = mock(InfirmierService.class);
        Infirmier i = Infirmier.creer(centre, "M1", "Amrani", "Sara", null, QualificationInfirmier.INFIRMIER, false);
        when(infirmiers.creer(eq(centre), eq("M1"), eq("Amrani"), eq("Sara"), eq(null),
                eq(QualificationInfirmier.INFIRMIER), eq(false))).thenReturn(new InfirmierDetail(i, List.of()));
        var controller = new InfirmierRestController(infirmiers, mock(AffectationInfirmierService.class),
                mock(CompteInfirmierService.class), guard);

        ResponseEntity<InfirmierResponse> ok = controller.creer(null,
                new InfirmierRequest("M1", "Amrani", "Sara", null, "INFIRMIER", false));

        assertEquals(201, ok.getStatusCode().value());
        assertThrows(IllegalArgumentException.class, () -> controller.creer(null,
                new InfirmierRequest("M2", "Benali", null, null, "MAGICIEN", false)));
    }

    @Test
    void an_assignment_is_added_for_the_guarded_center() {
        AffectationInfirmierService affectations = mock(AffectationInfirmierService.class);
        UUID infirmier = UUID.randomUUID();
        UUID salle = UUID.randomUUID();
        UUID creneau = UUID.randomUUID();
        when(affectations.ajouter(centre, infirmier, salle, creneau, EnumSet.of(JourSemaine.LUNDI)))
                .thenReturn(AffectationInfirmier.creer(centre, infirmier, salle, creneau, EnumSet.of(JourSemaine.LUNDI)));
        var controller = new InfirmierRestController(mock(InfirmierService.class), affectations,
                mock(CompteInfirmierService.class), guard);

        var response = controller.ajouterAffectation(null, infirmier, new AffectationRequest(salle, creneau, List.of(JourSemaine.LUNDI)));

        assertEquals(201, response.getStatusCode().value());
        assertEquals(salle, response.getBody().salleId());
    }

    @Test
    void absences_are_listed_by_page_and_declared_for_the_guarded_center() {
        AbsenceInfirmierService absences = mock(AbsenceInfirmierService.class);
        UUID infirmier = UUID.randomUUID();
        LocalDate debut = LocalDate.of(2026, 10, 5);
        AbsenceInfirmier a = AbsenceInfirmier.creer(centre, infirmier, debut, debut.plusDays(1), TypeAbsence.CONGE, null);
        when(absences.lister(centre, 0, 20)).thenReturn(PagedResult.of(List.of(a), 1, 0, 20));
        when(absences.declarer(centre, infirmier, debut, debut.plusDays(1), TypeAbsence.CONGE, null)).thenReturn(a);
        var controller = new AbsenceInfirmierRestController(absences, guard);

        PagedResult<AbsenceResponse> page = controller.lister(null, 0, 20).getBody();
        var cree = controller.declarer(null, new AbsenceRequest(infirmier, debut, debut.plusDays(1), "CONGE", null));

        assertEquals(1, page.total());
        assertEquals(201, cree.getStatusCode().value());
        assertThrows(IllegalArgumentException.class, () -> controller.declarer(null,
                new AbsenceRequest(infirmier, debut, debut, "VACANCES", null)));
        controller.supprimer(null, a.id());
        verify(absences).supprimer(centre, a.id());
    }

    @Test
    void the_presence_endpoints_use_the_guarded_center_and_the_default_horizon() {
        PresenceInfirmierQueryService presence = mock(PresenceInfirmierQueryService.class);
        RemplacementInfirmierService remplacements = mock(RemplacementInfirmierService.class);
        var controller = new PresenceInfirmierRestController(presence, remplacements,
                mock(PresenceInfirmierReportService.class), guard);
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
        var controller = new PresenceInfirmierRestController(presence, mock(RemplacementInfirmierService.class),
                mock(PresenceInfirmierReportService.class), guard);
        ChargeInfirmier c = new ChargeInfirmier(UUID.randomUUID(), "Amrani", 8, 1, 0, 9);
        when(presence.charge(centre, YearMonth.of(2026, 10), 0, 20))
                .thenReturn(new ChargePage(PagedResult.of(List.of(c), 1, 0, 20), 9.0));

        ChargeResponse r = controller.charge(null, "2026-10", 0, 20).getBody();

        assertEquals(1, r.total());
        assertEquals(9.0, r.moyenne());
        assertThrows(IllegalArgumentException.class, () -> controller.charge(null, "octobre", 0, 20));
    }

    @Test
    void a_replacement_is_assigned_and_cancelled_for_the_guarded_center() {
        RemplacementInfirmierService remplacements = mock(RemplacementInfirmierService.class);
        var controller = new PresenceInfirmierRestController(mock(PresenceInfirmierQueryService.class), remplacements,
                mock(PresenceInfirmierReportService.class), guard);
        UUID salle = UUID.randomUUID();
        UUID creneau = UUID.randomUUID();
        UUID infirmier = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 5);
        RemplacementInfirmier r = RemplacementInfirmier.creer(centre, date, salle, creneau, infirmier, null);
        when(remplacements.affecter(centre, date, salle, creneau, infirmier, null)).thenReturn(r);

        var cree = controller.affecter(null, new RemplacementRequest(date, salle, creneau, infirmier, null));
        controller.annuler(null, r.id());

        assertEquals(201, cree.getStatusCode().value());
        assertEquals(r.id(), cree.getBody().id());
        verify(remplacements).annuler(centre, r.id());
    }
}
