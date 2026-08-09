package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceDetails;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
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
        when(useCase.list(any(CenterId.class))).thenReturn(List.of());

        SeanceRestController controller = buildController(useCase);
        ResponseEntity<?> response = controller.list(CENTER_ID);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        verify(useCase).list(CenterId.of(CENTER_ID));
    }

    @Test
    void list_should_return_seance_items_for_center() {
        SeanceUseCase useCase = mock(SeanceUseCase.class);
        SeanceListItem item = new SeanceListItem(
                SEANCE_ID, CENTER_ID, PATIENT_ID,
                "PAT-001", "Dupont", "Jean",
                LocalDate.now(), SeanceStatus.FACTUREE,
                null, null, null, null
        );
        when(useCase.list(CenterId.of(CENTER_ID))).thenReturn(List.of(item));

        SeanceRestController controller = buildController(useCase);
        ResponseEntity<?> response = controller.list(CENTER_ID);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(List.class, response.getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) response.getBody();
        assertEquals(1, rows.size());
        assertEquals(SeanceStatus.FACTUREE, rows.getFirst().get("status"));
        verify(useCase).list(CenterId.of(CENTER_ID));
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
                null, null, null, null
        );
        when(useCase.list(CenterId.of(CENTER_ID))).thenReturn(List.of(item));
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
        ResponseEntity<?> response = controller.list(CENTER_ID);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(List.class, response.getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) response.getBody();
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
        when(useCase.list(CenterId.of(CENTER_ID))).thenReturn(List.of());
        when(useCase.list(CenterId.of(otherCenter))).thenReturn(List.of());

        SeanceRestController controller = buildController(useCase);
        controller.list(CENTER_ID);
        controller.list(otherCenter);

        verify(useCase).list(CenterId.of(CENTER_ID));
        verify(useCase).list(CenterId.of(otherCenter));
        // Chaque appel utilise strictement le centerId fourni
        verify(useCase, never()).list(argThat(c ->
                !c.value().equals(CENTER_ID) && !c.value().equals(otherCenter)
        ));
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
                "PAT-001"
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




