package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.absence.AbsencePatientService;
import com.hemodialyse.backend.domain.absence.model.AbsenceFiltre;
import com.hemodialyse.backend.domain.absence.model.AbsenceLigne;
import com.hemodialyse.backend.domain.absence.model.AbsencePatient;
import com.hemodialyse.backend.domain.absence.model.MotifAbsence;
import com.hemodialyse.backend.domain.absence.model.StatutAbsence;
import com.hemodialyse.backend.domain.absence.model.ValeurAbsence;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.rest.AbsencePatientRestController.AbsenceResponse;
import com.hemodialyse.backend.infrastructure.web.rest.AbsencePatientRestController.AnnulationRequest;
import com.hemodialyse.backend.infrastructure.web.rest.AbsencePatientRestController.DeclarationRequest;
import com.hemodialyse.backend.infrastructure.web.rest.AbsencePatientRestController.QualificationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contrôleur des absences de patients : le centre vient du garde, la liste est paginée, la correction n'est permise
 * qu'à l'administrateur et au médecin, et un motif inconnu est refusé.
 */
class AbsencePatientRestControllerTest {

    private final CenterAccessGuard guard = mock(CenterAccessGuard.class);
    private final AbsencePatientService service = mock(AbsencePatientService.class);
    private final UUID centre = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final LocalDate jour = LocalDate.now().minusDays(1);
    private final AbsencePatientRestController controller = new AbsencePatientRestController(service, guard);

    AbsencePatientRestControllerTest() {
        when(guard.requireCenter(any())).thenReturn(CenterId.of(centre));
    }

    private static Authentication auth(String role) {
        UserPrincipal p = UserPrincipal.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "u", "",
                List.of(role), true);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private AbsencePatient absence() {
        return AbsencePatient.detectee(centre, patient, jour,
                ValeurAbsence.depuisHt(null, null, new BigDecimal("1000"), new BigDecimal("19")), Instant.now());
    }

    @Test
    void the_list_is_paginated_filtered_and_scoped_to_the_guarded_center() {
        AbsencePatient a = absence();
        AbsenceFiltre filtre = new AbsenceFiltre(StatutAbsence.A_QUALIFIER, null, null, null, null);
        when(service.lister(centre, filtre, 2, 10)).thenReturn(PagedResult.of(List.of(new AbsenceLigne(a, "Ali")), 31, 2, 10));

        PagedResult<AbsenceResponse> page =
                controller.lister(null, StatutAbsence.A_QUALIFIER, null, null, null, null, 2, 10).getBody();

        assertEquals(31, page.total());
        assertEquals(2, page.page());
        assertEquals("Ali", page.items().get(0).patientNom());
        assertEquals(0, new BigDecimal("1000.00").compareTo(page.items().get(0).montantHt()));
    }

    @Test
    void declaring_passes_the_user_and_rejects_an_unknown_reason() {
        Authentication nurse = auth("INFIRMIER");
        when(service.declarer(eq(centre), eq(patient), eq(jour), eq(MotifAbsence.MALADIE), eq(null), any()))
                .thenReturn(absence());

        var cree = controller.declarer(null, new DeclarationRequest(patient, jour, "MALADIE", null), nurse);

        assertEquals(201, cree.getStatusCode().value());
        assertThrows(IllegalArgumentException.class, () -> controller.declarer(null,
                new DeclarationRequest(patient, jour, "VACANCES", null), nurse));
    }

    @Test
    void only_admin_and_doctor_may_correct_a_qualified_absence() {
        UUID id = UUID.randomUUID();
        when(service.qualifier(eq(centre), eq(id), eq(MotifAbsence.VOYAGE), eq("x"), any(), eq(true))).thenReturn(absence());
        when(service.qualifier(eq(centre), eq(id), eq(MotifAbsence.VOYAGE), eq("x"), any(), eq(false))).thenReturn(absence());

        for (String role : List.of("ADMIN", "MEDECIN", "SECRETAIRE", "INFIRMIER")) {
            controller.qualifier(null, id, new QualificationRequest("VOYAGE", "x"), auth(role));
        }

        boolean corrige = true;
        verify(service, org.mockito.Mockito.times(2)).qualifier(eq(centre), eq(id), eq(MotifAbsence.VOYAGE), eq("x"),
                any(), eq(corrige));
        verify(service, org.mockito.Mockito.times(2)).qualifier(eq(centre), eq(id), eq(MotifAbsence.VOYAGE), eq("x"),
                any(), eq(false));
    }

    @Test
    void cancelling_uses_the_guarded_center() {
        UUID id = UUID.randomUUID();
        when(service.annuler(eq(centre), eq(id), eq("Erreur"), any(), eq(false))).thenReturn(absence());

        assertEquals(200, controller.annuler(null, id, new AnnulationRequest("Erreur"), auth("SECRETAIRE"))
                .getStatusCode().value());
    }

    @Test
    void the_summary_is_exposed() {
        when(service.synthese(centre)).thenReturn(new AbsencePatientService.Synthese(4, 1));

        var body = controller.synthese(null).getBody();

        assertEquals(4, body.aQualifier());
        assertEquals(1, body.enRetard());
    }
}
