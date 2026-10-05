package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceDetails;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.model.SeanceSearch;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du SeanceRestController (mock des dépendances, pas de Spring Context).
 * Pour les tests d'intégration HTTP complets (@SpringBootTest), voir SeanceIntegrationTest.
 */
class SeanceRestControllerTest {

    private static final UUID CENTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID SEANCE_ID = UUID.randomUUID();

    private SeanceRestController buildController(SeanceUseCase useCase) {
        return new SeanceRestController(
                useCase,
                mock(NotificationService.class),
                mock(JdbcTemplate.class)
        );
    }

    // ─── list ────────────────────────────────────────────────────────────────

    @Test
    void list_should_return_empty_list_when_no_seances() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        com.hemodialyse.backend.domain.shared.PagedResult<com.hemodialyse.backend.domain.seance.model.SeanceListItem> pagedResult =
                new com.hemodialyse.backend.domain.shared.PagedResult<>(List.of(), 0, 0, 20);
        when(useCase.search(eq(CenterId.of(CENTER_ID)), any(SeanceSearch.class), eq(0), eq(20))).thenReturn(pagedResult);

        SeanceRestController controller = buildController(useCase);
        ResponseEntity<?> response = controller.list(CENTER_ID, 0, 20, null, null, null, null, null, "desc", null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        verify(useCase).search(eq(CenterId.of(CENTER_ID)), any(SeanceSearch.class), eq(0), eq(20));
    }

    @Test
    void list_should_return_seance_items_for_center() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        SeanceListItem item = new SeanceListItem(
                SEANCE_ID, CENTER_ID, PATIENT_ID,
                "PAT-001", "Dupont", "Jean",
                LocalDate.now(), SeanceStatus.FACTUREE,
                null, null, null, null, null, false, null
        );
        com.hemodialyse.backend.domain.shared.PagedResult<com.hemodialyse.backend.domain.seance.model.SeanceListItem> pagedResult =
                new com.hemodialyse.backend.domain.shared.PagedResult<>(List.of(item), 1, 0, 20);
        when(useCase.search(eq(CenterId.of(CENTER_ID)), any(SeanceSearch.class), eq(0), eq(20))).thenReturn(pagedResult);

        SeanceRestController controller = buildController(useCase);
        ResponseEntity<?> response = controller.list(CENTER_ID, 0, 20, null, null, null, null, null, "desc", null);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("items");
        assertEquals(1, rows.size());
        assertEquals(SeanceStatus.FACTUREE, rows.getFirst().get("status"));
        verify(useCase).search(eq(CenterId.of(CENTER_ID)), any(SeanceSearch.class), eq(0), eq(20));
    }

    @Test
    void list_should_include_current_forfait_when_available() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        NotificationService notif = mock(NotificationService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        SeanceListItem item = new SeanceListItem(
                SEANCE_ID, CENTER_ID, PATIENT_ID,
                "PAT-001", "Dupont", "Jean",
                LocalDate.of(2026, 7, 25), SeanceStatus.CREE,
                null, null, null, null, null, false, null
        );
        com.hemodialyse.backend.domain.shared.PagedResult<com.hemodialyse.backend.domain.seance.model.SeanceListItem> pagedResult =
                new com.hemodialyse.backend.domain.shared.PagedResult<>(List.of(item), 1, 0, 20);
        when(useCase.search(eq(CenterId.of(CENTER_ID)), any(SeanceSearch.class), eq(0), eq(20))).thenReturn(pagedResult);
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.ResultSetExtractor.class), eq(SEANCE_ID), eq(CENTER_ID)))
                .thenReturn(null);
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of(
                        "id", UUID.fromString("40000000-0000-0000-0000-000000000001"),
                        "code", "F001",
                        "nom", "Forfait HD",
                        "prix", new java.math.BigDecimal("3500.00"),
                        "nombreSeances", 0
                )));

        SeanceRestController controller = new SeanceRestController(useCase, notif, jdbc);
        ResponseEntity<?> response = controller.list(CENTER_ID, 0, 20, null, null, null, null, null, "desc", null);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("items");
        assertEquals(1, rows.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> forfait = (Map<String, Object>) rows.getFirst().get("forfait");
        assertNotNull(forfait);
        assertEquals("Forfait HD", forfait.get("nom"));
    }

    @Test
    void list_should_only_use_centerId_from_request_param() {
        // Règle multi-centre : la liste est toujours scoped par centerId
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        UUID otherCenter = UUID.randomUUID();
        com.hemodialyse.backend.domain.shared.PagedResult<com.hemodialyse.backend.domain.seance.model.SeanceListItem> emptyResult =
                new com.hemodialyse.backend.domain.shared.PagedResult<>(List.of(), 0, 0, 20);
        when(useCase.search(eq(CenterId.of(CENTER_ID)), any(SeanceSearch.class), eq(0), eq(20))).thenReturn(emptyResult);
        when(useCase.search(eq(CenterId.of(otherCenter)), any(SeanceSearch.class), eq(0), eq(20))).thenReturn(emptyResult);

        SeanceRestController controller = buildController(useCase);
        controller.list(CENTER_ID, 0, 20, null, null, null, null, null, "desc", null);
        controller.list(otherCenter, 0, 20, null, null, null, null, null, "desc", null);

        verify(useCase).search(eq(CenterId.of(CENTER_ID)), any(SeanceSearch.class), eq(0), eq(20));
        verify(useCase).search(eq(CenterId.of(otherCenter)), any(SeanceSearch.class), eq(0), eq(20));
        // Chaque appel utilise strictement le centerId fourni
        verify(useCase, never()).search(argThat(c ->
                !c.value().equals(CENTER_ID) && !c.value().equals(otherCenter)
        ), any(SeanceSearch.class), eq(0), eq(20));
    }

    @Test
    void list_should_translate_the_query_params_into_search_criteria() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        when(useCase.search(any(), any(SeanceSearch.class), eq(2), eq(50)))
                .thenReturn(new com.hemodialyse.backend.domain.shared.PagedResult<>(List.of(), 0, 2, 50));

        ResponseEntity<?> response = buildController(useCase).list(CENTER_ID, 2, 50,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "validee, cree", "dupont jean", "patient", "asc", true);

        assertEquals(200, response.getStatusCode().value());
        org.mockito.ArgumentCaptor<SeanceSearch> captor = org.mockito.ArgumentCaptor.forClass(SeanceSearch.class);
        verify(useCase).search(eq(CenterId.of(CENTER_ID)), captor.capture(), eq(2), eq(50));
        SeanceSearch criteria = captor.getValue();
        assertEquals(LocalDate.of(2026, 10, 1), criteria.from());
        assertEquals(LocalDate.of(2026, 10, 31), criteria.to());
        assertEquals(java.util.Set.of(SeanceStatus.VALIDEE, SeanceStatus.CREE), criteria.statuses());
        assertEquals("dupont jean", criteria.text());
        assertEquals(SeanceSearch.Sort.PATIENT, criteria.sort());
        assertFalse(criteria.desc());
        assertEquals(true, criteria.deverrouillee());
    }

    @Test
    void list_should_default_to_newest_first_and_refuse_an_unknown_status() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        when(useCase.search(any(), any(SeanceSearch.class), eq(0), eq(20)))
                .thenReturn(new com.hemodialyse.backend.domain.shared.PagedResult<>(List.of(), 0, 0, 20));
        SeanceRestController controller = buildController(useCase);

        controller.list(CENTER_ID, 0, 20, null, null, null, null, null, "desc", null);
        org.mockito.ArgumentCaptor<SeanceSearch> captor = org.mockito.ArgumentCaptor.forClass(SeanceSearch.class);
        verify(useCase).search(any(), captor.capture(), eq(0), eq(20));
        assertEquals(SeanceSearch.Sort.DATE, captor.getValue().sort());
        assertTrue(captor.getValue().desc());

        ResponseEntity<?> bad = controller.list(CENTER_ID, 0, 20, null, null, "NOPE", null, null, "desc", null);
        assertEquals(400, bad.getStatusCode().value());
    }

    // ─── create ──────────────────────────────────────────────────────────────

    @Test
    void create_should_call_useCase_and_notify() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        NotificationService notif = mock(NotificationService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        Seance seance = new Seance(SEANCE_ID, PATIENT_ID, CENTER_ID, LocalDate.now());
        Patient patient = new Patient();
        patient.setId(PatientId.of(PATIENT_ID));
        patient.setNom("Dupont");
        patient.setPrenom("Jean");

        when(useCase.create(eq(CenterId.of(CENTER_ID)), eq(PATIENT_ID), any())).thenReturn(seance);
        when(useCase.getDetails(eq(CenterId.of(CENTER_ID)), eq(SEANCE_ID)))
                .thenReturn(new SeanceDetails(seance, patient, null, null));

        SeanceRestController controller = new SeanceRestController(useCase, notif, jdbc);

        var request = new com.hemodialyse.backend.infrastructure.web.dto.request.CreateSeanceRequest(
                CENTER_ID, PATIENT_ID, LocalDate.now()
        );
        ResponseEntity<?> response = controller.create(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(SEANCE_ID, body.get("id"));
        assertEquals(SeanceStatus.CREE, body.get("status"));

        verify(useCase).create(CenterId.of(CENTER_ID), PATIENT_ID, LocalDate.now());
        verify(notif).notifySeanceCreated(
                eq(CENTER_ID), eq(SEANCE_ID), eq(PATIENT_ID),
                eq("Dupont"), eq("Jean"), any()
        );
    }

    @Test
    void scan_should_call_useCase_without_client_date() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        NotificationService notif = mock(NotificationService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        Seance seance = new Seance(SEANCE_ID, PATIENT_ID, CENTER_ID, LocalDate.now());
        Patient patient = new Patient();
        patient.setId(PatientId.of(PATIENT_ID));
        patient.setNom("Dupont");
        patient.setPrenom("Jean");

        when(useCase.createFromQr(eq(CenterId.of(CENTER_ID)), eq("PAT-001"))).thenReturn(seance);
        when(useCase.getDetails(eq(CenterId.of(CENTER_ID)), eq(SEANCE_ID)))
                .thenReturn(new SeanceDetails(seance, patient, null, null));

        SeanceRestController controller = new SeanceRestController(useCase, notif, jdbc);

        var request = new com.hemodialyse.backend.infrastructure.web.dto.request.ScanSeanceQrRequest(
                CENTER_ID,
                "PAT-001", null, null
        );
        ResponseEntity<?> response = controller.scanQr(request);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(Map.class, response.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(SEANCE_ID, body.get("id"));
        assertNull(body.get("generateurId"));
        assertNull(body.get("generateurNom"));
        assertNull(body.get("generateurMarque"));
        assertNull(body.get("generateurEtat"));
        verify(useCase).createFromQr(CenterId.of(CENTER_ID), "PAT-001");
    }

    // ─── valider ─────────────────────────────────────────────────────────────

    @Test
    void validate_should_transition_seance_to_VALIDEE() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        NotificationService notif = mock(NotificationService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        Seance seance = new Seance(SEANCE_ID, PATIENT_ID, CENTER_ID, LocalDate.now());
        seance.validerParInfirmier("inf-01");
        Patient patient = new Patient();
        patient.setId(PatientId.of(PATIENT_ID));
        patient.setNom("Test");
        patient.setPrenom("Patient");

        when(useCase.validate(eq(CenterId.of(CENTER_ID)), eq(SEANCE_ID), eq("inf-01"), any()))
                .thenReturn(seance);
        when(useCase.getDetails(CenterId.of(CENTER_ID), SEANCE_ID))
                .thenReturn(new SeanceDetails(seance, patient, null, null));

        SeanceRestController controller = new SeanceRestController(useCase, notif, jdbc);

        var request = new com.hemodialyse.backend.infrastructure.web.dto.request.ValidateSeanceRequest(
                CENTER_ID, "inf-01", null
        );
        ResponseEntity<?> response = controller.validate(SEANCE_ID, request);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(SeanceStatus.VALIDEE, body.get("status"));
    }

    private ResponseEntity<?> validatePastSeanceAs(String role, boolean deverrouillee) {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        Seance seance = new Seance(SEANCE_ID, PATIENT_ID, CENTER_ID, LocalDate.now().minusDays(2));
        if (deverrouillee) seance.setRegularisationDeverrouilleeAt(java.time.OffsetDateTime.now());
        seance.validerParInfirmier("u");
        Patient patient = new Patient();
        patient.setId(PatientId.of(PATIENT_ID));
        patient.setNom("Test");
        patient.setPrenom("Patient");
        when(useCase.getDetails(CenterId.of(CENTER_ID), SEANCE_ID)).thenReturn(new SeanceDetails(seance, patient, null, null));
        when(useCase.validate(eq(CenterId.of(CENTER_ID)), eq(SEANCE_ID), any(), any())).thenReturn(seance);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "u", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))));
        try {
            return new SeanceRestController(useCase, mock(NotificationService.class), mock(JdbcTemplate.class))
                    .validate(SEANCE_ID, new com.hemodialyse.backend.infrastructure.web.dto.request.ValidateSeanceRequest(
                            CENTER_ID, "u", null));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void validate_should_refuse_a_nurse_on_a_past_day_session_the_admin_has_not_unlocked() {
        var e = assertThrows(com.hemodialyse.backend.domain.shared.exception.BusinessException.class,
                () -> validatePastSeanceAs("INFIRMIER", false));
        assertEquals("SEANCE_REGULARISATION_NON_DEVERROUILLEE", e.getCode());
    }

    @Test
    void validate_should_let_a_nurse_regularise_a_past_day_session_once_unlocked() {
        assertEquals(200, validatePastSeanceAs("INFIRMIER", true).getStatusCode().value());
    }

    @Test
    void validate_should_always_let_the_administrator_regularise_a_past_day_session() {
        assertEquals(200, validatePastSeanceAs("ADMIN", false).getStatusCode().value());
    }

    @Test
    void unlock_should_unlock_the_session_and_warn_the_nurses_of_the_center() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        NotificationService notif = mock(NotificationService.class);
        Seance seance = new Seance(SEANCE_ID, PATIENT_ID, CENTER_ID, LocalDate.now().minusDays(2));
        seance.setRegularisationDeverrouilleeAt(java.time.OffsetDateTime.now());
        Patient patient = new Patient();
        patient.setId(PatientId.of(PATIENT_ID));
        patient.setNom("Dupont");
        patient.setPrenom("Jean");
        when(useCase.unlockForRegularisation(eq(CenterId.of(CENTER_ID)), eq(SEANCE_ID), any())).thenReturn(seance);
        when(useCase.getDetails(CenterId.of(CENTER_ID), SEANCE_ID)).thenReturn(new SeanceDetails(seance, patient, null, null));

        ResponseEntity<?> response = new SeanceRestController(useCase, notif, mock(JdbcTemplate.class))
                .unlockForRegularisation(SEANCE_ID, CENTER_ID);

        assertEquals(200, response.getStatusCode().value());
        verify(notif).notifySeanceDeverrouillee(eq(CENTER_ID), eq(SEANCE_ID), eq("Dupont"), eq("Jean"),
                eq(LocalDate.now().minusDays(2).toString()));
    }

    @Test
    void updateForfait_should_return_updated_forfait_payload() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        NotificationService notif = mock(NotificationService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID forfaitId = UUID.fromString("40000000-0000-0000-0000-000000000001");

        Seance seance = new Seance(SEANCE_ID, PATIENT_ID, CENTER_ID, LocalDate.of(2026, 7, 25));
        seance.setForfaitOverrideId(forfaitId);
        seance.setForfaitOverrideCode("F-HD");
        seance.setForfaitOverrideNom("Forfait HD");
        seance.setForfaitOverridePrix(new java.math.BigDecimal("3500.00"));

        when(useCase.updateForfait(CenterId.of(CENTER_ID), SEANCE_ID, forfaitId, "inf-01")).thenReturn(seance);
        Map<String, Object> forfaitPayload = new java.util.LinkedHashMap<>();
        forfaitPayload.put("id", forfaitId);
        forfaitPayload.put("code", "F-HD");
        forfaitPayload.put("nom", "Forfait HD");
        forfaitPayload.put("prix", new java.math.BigDecimal("3500.00"));
        forfaitPayload.put("nombreSeances", null);
        forfaitPayload.put("updatedBy", "inf-01");
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.ResultSetExtractor.class), eq(SEANCE_ID), eq(CENTER_ID)))
                .thenReturn(forfaitPayload);

        SeanceRestController controller = new SeanceRestController(useCase, notif, jdbc);
        var request = new com.hemodialyse.backend.infrastructure.web.dto.request.UpdateSeanceForfaitRequest(
                CENTER_ID, forfaitId, "inf-01"
        );

        ResponseEntity<?> response = controller.updateForfait(SEANCE_ID, request);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> forfait = (Map<String, Object>) body.get("forfait");
        assertEquals("Forfait HD", forfait.get("nom"));
        assertEquals(forfaitId, forfait.get("id"));
    }

    // ─── signer-medecin ──────────────────────────────────────────────────────

    @Test
    void signByMedecin_should_return_SIGNEE_status() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);

        Seance seance = new Seance(SEANCE_ID, PATIENT_ID, CENTER_ID, LocalDate.now());
        seance.validerParInfirmier("inf-01");
        seance.signerParMedecin("med-01");

        when(useCase.signByMedecin(CenterId.of(CENTER_ID), SEANCE_ID, "med-01"))
                .thenReturn(seance);

        SeanceRestController controller = buildController(useCase);

        var request = new com.hemodialyse.backend.infrastructure.web.dto.request.SignSeanceMedecinRequest(
                CENTER_ID, "med-01"
        );
        ResponseEntity<?> response = controller.signByMedecin(SEANCE_ID, request);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(SeanceStatus.SIGNEE, body.get("status"));
        assertNotNull(body.get("signedByMedecinAt"));
    }

    // ─── isolation multi-centre ───────────────────────────────────────────────

    @Test
    void details_should_filter_by_centerId_strictly() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        UUID wrongCenter = UUID.randomUUID();

        Seance seance = new Seance(SEANCE_ID, PATIENT_ID, CENTER_ID, LocalDate.now());
        Patient patient = new Patient();
        patient.setId(PatientId.of(PATIENT_ID));

        when(useCase.getDetails(CenterId.of(CENTER_ID), SEANCE_ID))
                .thenReturn(new SeanceDetails(seance, patient, null, null));
        when(useCase.getDetails(CenterId.of(wrongCenter), SEANCE_ID))
                .thenThrow(new IllegalArgumentException("Seance introuvable"));

        SeanceRestController controller = buildController(useCase);

        // Le bon centre doit fonctionner
        ResponseEntity<?> ok = controller.details(SEANCE_ID, CENTER_ID);
        assertEquals(200, ok.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) ok.getBody();
        assertNotNull(payload);
        assertTrue(payload.containsKey("consommables"));
        assertTrue(payload.containsKey("consommablesTotalValorise"));
        assertTrue(payload.containsKey("forfait"));

        // Un autre centre doit échouer (isolation multi-centre)
        assertThrows(IllegalArgumentException.class, () -> controller.details(SEANCE_ID, wrongCenter));
    }
}




