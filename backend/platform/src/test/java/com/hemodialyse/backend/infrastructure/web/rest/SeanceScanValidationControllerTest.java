package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceDetails;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase.ScanResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.ScanSeanceQrRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scan d'un patient : l'infirmier (ou l'administrateur) valide directement la séance du jour, la secrétaire ne fait que
 * la créer ; chaque saisie de l'infirmier prévient le médecin du centre.
 */
class SeanceScanValidationControllerTest {

    private static final UUID CENTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID SEANCE_ID = UUID.randomUUID();

    private final SeanceUseCase useCase = mock(SeanceUseCase.class);
    private final NotificationService notif = mock(NotificationService.class);
    private final SeanceRestController controller = new SeanceRestController(useCase, notif, mock(JdbcTemplate.class));

    private static void connectedAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Seance seance(SeanceStatus status) {
        Seance s = new Seance(SEANCE_ID, PATIENT_ID, CENTER_ID, LocalDate.now());
        s.setStatus(status);
        return s;
    }

    private void stubDetails(Seance seance) {
        Patient patient = new Patient();
        patient.setId(PatientId.of(PATIENT_ID));
        patient.setNom("Dupont");
        patient.setPrenom("Jean");
        patient.setCodePatient("PAT-ROU00008");
        when(useCase.getDetails(CenterId.of(CENTER_ID), SEANCE_ID)).thenReturn(new SeanceDetails(seance, patient, null, null));
    }

    @Test
    void a_nurse_scan_validates_the_session_directly_and_notifies_the_doctor() {
        connectedAs("inf-01", "INFIRMIER");
        Seance validee = seance(SeanceStatus.VALIDEE);
        when(useCase.scanAndValidate(CenterId.of(CENTER_ID), "PAT-ROU00008", "inf-01"))
                .thenReturn(new ScanResult(validee, true, true, false));
        stubDetails(validee);

        var response = controller.scanQr(new ScanSeanceQrRequest(CENTER_ID, "PAT-ROU00008"));

        Map<String, Object> body = body(response);
        assertEquals(SeanceStatus.VALIDEE, body.get("status"));
        assertEquals(true, body.get("created"));
        assertEquals(true, body.get("validatedNow"));
        assertEquals(false, body.get("alreadyValidated"));
        assertEquals("Dupont", body.get("patientNom"));
        assertEquals("PAT-ROU00008", body.get("patientCode"));
        verify(useCase, never()).createFromQr(any(), anyString());
        verify(notif).notifySeanceValidated(eq(CENTER_ID), eq(SEANCE_ID), eq(PATIENT_ID), eq("Dupont"), eq("Jean"), anyString());
        verify(notif).notifySaisieInfirmier(eq(CENTER_ID), eq("SEANCE_VALIDEE"), eq(PATIENT_ID), eq("Dupont"), eq("Jean"),
                eq("inf-01"), anyString());
    }

    @Test
    void a_second_scan_of_a_validated_session_changes_and_notifies_nothing() {
        connectedAs("inf-01", "INFIRMIER");
        Seance deja = seance(SeanceStatus.VALIDEE);
        when(useCase.scanAndValidate(any(), anyString(), anyString())).thenReturn(new ScanResult(deja, false, false, true));
        stubDetails(deja);

        Map<String, Object> body = body(controller.scanQr(new ScanSeanceQrRequest(CENTER_ID, "PAT-ROU00008")));

        assertEquals(true, body.get("alreadyValidated"));
        assertEquals(false, body.get("validatedNow"));
        verify(notif, never()).notifySaisieInfirmier(any(), anyString(), any(), any(), any(), any(), any());
        verify(notif, never()).notifySeanceCreated(any(), any(), any(), any(), any(), any());
    }

    @Test
    void an_administrator_scan_also_validates() {
        connectedAs("admin", "ADMIN");
        Seance validee = seance(SeanceStatus.VALIDEE);
        when(useCase.scanAndValidate(CenterId.of(CENTER_ID), "X", "admin")).thenReturn(new ScanResult(validee, false, true, false));
        stubDetails(validee);

        assertEquals(SeanceStatus.VALIDEE, body(controller.scanQr(new ScanSeanceQrRequest(CENTER_ID, "X"))).get("status"));
    }

    @Test
    void a_secretary_scan_only_creates_the_session_for_the_nurse_to_validate() {
        connectedAs("secretaire", "SECRETAIRE");
        Seance creee = seance(SeanceStatus.CREE);
        when(useCase.createFromQr(CenterId.of(CENTER_ID), "PAT-ROU00008")).thenReturn(creee);
        stubDetails(creee);

        Map<String, Object> body = body(controller.scanQr(new ScanSeanceQrRequest(CENTER_ID, "PAT-ROU00008")));

        assertEquals(SeanceStatus.CREE, body.get("status"));
        assertEquals(false, body.get("validatedNow"));
        assertEquals(false, body.get("alreadyValidated"));
        verify(useCase, never()).scanAndValidate(any(), anyString(), anyString());
        verify(notif).notifySaisieInfirmier(eq(CENTER_ID), eq("SEANCE_CREEE"), eq(PATIENT_ID), any(), any(),
                eq("secretaire"), anyString());
    }

    @Test
    void adding_a_consommable_to_a_validated_session_issues_only_that_line_and_notifies_the_doctor() {
        connectedAs("inf-01", "INFIRMIER");
        UUID article = UUID.randomUUID();
        Seance validee = seance(SeanceStatus.VALIDEE);
        stubDetails(validee);

        var response = controller.addConsommable(SEANCE_ID,
                new SeanceRestController.AddConsommableRequest(CENTER_ID, article, new BigDecimal("2")));

        assertEquals(200, response.getStatusCode().value());
        verify(useCase).addConsommableSeance(CenterId.of(CENTER_ID), SEANCE_ID, article, new BigDecimal("2"), "inf-01");
        verify(notif).notifySeanceUpdated(CENTER_ID, SEANCE_ID);
        verify(notif).notifySaisieInfirmier(eq(CENTER_ID), eq("CONSOMMABLE_AJOUT"), eq(PATIENT_ID), eq("Dupont"), eq("Jean"),
                eq("inf-01"), anyString());
        assertTrue(true);
        assertFalse(false);
    }
}
