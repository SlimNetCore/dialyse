package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.infirmier.AffectationInfirmierService;
import com.hemodialyse.backend.application.infirmier.CompteInfirmierService;
import com.hemodialyse.backend.application.infirmier.CompteInfirmierService.CompteCree;
import com.hemodialyse.backend.application.infirmier.InfirmierService;
import com.hemodialyse.backend.application.infirmier.InfirmierService.InfirmierDetail;
import com.hemodialyse.backend.application.infirmier.MonPlanningInfirmierService;
import com.hemodialyse.backend.application.infirmier.MonPlanningInfirmierService.MonPlanning;
import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Infirmier;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.infirmier.port.ComptesInfirmierPort.CompteRef;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.rest.InfirmierRestController.CompteCreeResponse;
import com.hemodialyse.backend.infrastructure.web.rest.InfirmierRestController.CompteResponse;
import com.hemodialyse.backend.infrastructure.web.rest.InfirmierRestController.CreerCompteRequest;
import com.hemodialyse.backend.infrastructure.web.rest.InfirmierRestController.InfirmierResponse;
import com.hemodialyse.backend.infrastructure.web.rest.InfirmierRestController.LierCompteRequest;
import com.hemodialyse.backend.infrastructure.web.rest.MonPlanningInfirmierRestController.MonAbsenceRequest;
import com.hemodialyse.backend.infrastructure.web.rest.MonPlanningInfirmierRestController.MonPlanningResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contrôleurs de la relation infirmier ↔ compte et de « mon planning » : le centre vient du garde, le compte de
 * l'authentification, jamais du corps de la requête.
 */
class InfirmierCompteRestControllersTest {

    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);
    private final UUID centre = UUID.randomUUID();
    private final UUID utilisateur = UUID.randomUUID();

    InfirmierCompteRestControllersTest() {
        when(guard.requireCenter(any())).thenReturn(CenterId.of(centre));
    }

    private Authentication authentification() {
        UserPrincipal principal = UserPrincipal.create(utilisateur.toString(), centre.toString(), "sara", "x",
                List.of("INFIRMIER"), true);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private InfirmierRestController referentiel(CompteInfirmierService comptes) {
        return new InfirmierRestController(mock(InfirmierService.class), mock(AffectationInfirmierService.class), comptes,
                guard);
    }

    private Infirmier fiche(UUID userId) {
        return new Infirmier(UUID.randomUUID(), centre, "M1", "Amrani", "Sara", null, QualificationInfirmier.INFIRMIER,
                false, true, userId);
    }

    @Test
    void linkable_accounts_are_paginated_and_the_response_exposes_the_linked_account() {
        CompteInfirmierService comptes = mock(CompteInfirmierService.class);
        CompteRef compte = new CompteRef(UUID.randomUUID(), "sara", "Sara Amrani", true);
        when(comptes.comptesLiables(centre, 1, 5)).thenReturn(PagedResult.of(List.of(compte), 6, 1, 5));
        Infirmier fiche = fiche(compte.id());
        when(comptes.lier(centre, fiche.id(), compte.id())).thenReturn(new InfirmierDetail(fiche, List.of(), compte));
        var controller = referentiel(comptes);

        PagedResult<CompteResponse> page = controller.comptesLiables(null, 1, 5).getBody();
        InfirmierResponse lie = controller.lierCompte(null, fiche.id(), new LierCompteRequest(compte.id())).getBody();

        assertEquals(6, page.total());
        assertEquals("sara", page.items().get(0).username());
        assertEquals("sara", lie.compte().username());
    }

    @Test
    void creating_an_account_returns_the_temporary_password_once_and_is_never_cached() {
        CompteInfirmierService comptes = mock(CompteInfirmierService.class);
        CompteRef compte = new CompteRef(UUID.randomUUID(), "sara", "Sara Amrani", true);
        Infirmier fiche = fiche(compte.id());
        when(comptes.creerEtLier(centre, fiche.id(), "sara", "sara@example.dz"))
                .thenReturn(new CompteCree(new InfirmierDetail(fiche, List.of(), compte), "Tmp-secret-1"));
        var controller = referentiel(comptes);

        ResponseEntity<CompteCreeResponse> response = controller.creerCompte(null, fiche.id(),
                new CreerCompteRequest("sara", "sara@example.dz"));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("Tmp-secret-1", response.getBody().motDePasseTemporaire());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
    }

    @Test
    void unlinking_an_account_returns_the_file_without_account() {
        CompteInfirmierService comptes = mock(CompteInfirmierService.class);
        Infirmier fiche = fiche(null);
        when(comptes.delier(centre, fiche.id())).thenReturn(new InfirmierDetail(fiche, List.of()));

        InfirmierResponse r = referentiel(comptes).delierCompte(null, fiche.id()).getBody();

        assertNull(r.compte());
    }

    @Test
    void my_planning_uses_the_authenticated_account_and_the_guarded_center() {
        MonPlanningInfirmierService service = mock(MonPlanningInfirmierService.class);
        Infirmier fiche = fiche(utilisateur);
        LocalDate date = LocalDate.of(2026, 9, 30);
        when(service.planning(centre, utilisateur, date)).thenReturn(new MonPlanning(
                new InfirmierDetail(fiche, List.of()), LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 3), List.of(),
                List.of(), List.of(), List.of(), List.of()));
        var controller = new MonPlanningInfirmierRestController(service, guard);

        ResponseEntity<MonPlanningResponse> response = controller.planning(null, date, authentification());

        assertEquals("Amrani", response.getBody().infirmier().nom());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        verify(service).planning(centre, utilisateur, date);
    }

    @Test
    void my_absences_are_listed_declared_and_cancelled_for_my_own_file() {
        MonPlanningInfirmierService service = mock(MonPlanningInfirmierService.class);
        LocalDate debut = LocalDate.of(2026, 10, 12);
        AbsenceInfirmier a = AbsenceInfirmier.creer(centre, UUID.randomUUID(), debut, debut.plusDays(1),
                TypeAbsence.CONGE, null);
        when(service.mesAbsences(centre, utilisateur, 0, 20)).thenReturn(PagedResult.of(List.of(a), 1, 0, 20));
        when(service.declarer(eq(centre), eq(utilisateur), any(), eq(debut), eq(debut.plusDays(1)), eq(TypeAbsence.CONGE),
                eq(null))).thenReturn(a);
        var controller = new MonPlanningInfirmierRestController(service, guard);

        var page = controller.absences(null, 0, 20, authentification()).getBody();
        var cree = controller.declarer(null, new MonAbsenceRequest(debut, debut.plusDays(1), "CONGE", null),
                authentification());
        controller.annuler(null, a.id(), authentification());

        assertEquals(1, page.total());
        assertEquals(201, cree.getStatusCode().value());
        verify(service).annuler(eq(centre), eq(utilisateur), any(), eq(a.id()));
        assertThrows(IllegalArgumentException.class, () -> controller.declarer(null,
                new MonAbsenceRequest(debut, debut, "VACANCES", null), authentification()));
    }
}
