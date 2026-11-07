package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceDetails;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
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
    private final SeanceRestController controller = new SeanceRestController(useCase, notif, mock(JdbcTemplate.class),
            mock(com.hemodialyse.backend.infrastructure.security.CenterAccessGuard.class),
            mock(com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage.class), mock(com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance.class));

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

    // Le scan (SE1) est servi par `contexts/seance` : ses cas sont dans `ScannerUnPatientTest` et les goldens SE1.

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
