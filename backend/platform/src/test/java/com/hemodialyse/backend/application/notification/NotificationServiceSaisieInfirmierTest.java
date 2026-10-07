package com.hemodialyse.backend.application.notification;

import com.hemodialyse.backend.application.direction.DirectionRealtimeService;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chaque saisie de l'infirmier est publiée à destination du seul médecin du centre ; les anciens évènements de séance ne
 * visent plus le médecin (il est prévenu par ce message qui nomme l'auteur).
 */
class NotificationServiceSaisieInfirmierTest {

    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final NotificationJournalPort journal = mock(NotificationJournalPort.class);
    private final NotificationService service = new NotificationService(messaging, mock(DirectionRealtimeService.class),
            journal);
    private final UUID centre = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private Map<String, Object> publie(String destinationAttendue) {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq(destinationAttendue), event.capture());
        return (Map<String, Object>) event.getValue();
    }

    @Test
    void a_nurse_entry_goes_to_the_doctor_of_the_center_with_the_author_and_the_patient() {
        UUID patient = UUID.randomUUID();

        service.notifySaisieInfirmier(centre, "PARAMEDICAL", patient, "Dupont", "Jean", "inf-01", "2026-10-04");

        Map<String, Object> evt = publie("/topic/center/" + centre + "/events");
        assertEquals("SAISIE_INFIRMIER", evt.get("type"));
        assertEquals(centre.toString(), evt.get("centerId"));
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) evt.get("payload");
        assertEquals("MEDECIN", payload.get("targetRoles"));
        assertEquals("PARAMEDICAL", payload.get("saisie"));
        assertEquals("Dupont", payload.get("patientNom"));
        assertEquals("Jean", payload.get("patientPrenom"));
        assertEquals("inf-01", payload.get("auteur"));
        assertEquals("2026-10-04", payload.get("date"));
        assertEquals(patient.toString(), payload.get("patientId"));
    }

    @Test
    void a_durable_alert_is_journaled_and_pushed_with_its_identifier() {
        service.notifySeancesARegulariser(centre, 2, LocalDate.of(2026, 10, 1));
        service.notifyGenerateurIndisponible(centre, "A-G1", "HORS_SERVICE", java.util.List.of("BENALI Karim"));

        Map<String, Object> evt = publieTous().get(1);
        assertEquals("GENERATEUR_INDISPONIBLE", evt.get("type"));
        verify(journal).enregistrer(eq(UUID.fromString((String) evt.get("id"))), eq(centre),
                eq("GENERATEUR_INDISPONIBLE"), any(), any());
    }

    @Test
    void a_transient_event_is_pushed_without_being_journaled() {
        service.notifyPatientCreated(centre, UUID.randomUUID(), "P1", "Benali", "Karim");

        verify(journal, org.mockito.Mockito.never()).enregistrer(any(), any(), any(), any(), any());
        assertNull(publieTous().get(0).get("id"));
    }

    @Test
    void a_journal_failure_never_blocks_the_real_time_alert() {
        doThrow(new IllegalStateException("base inaccessible")).when(journal).enregistrer(any(), any(), any(), any(), any());

        service.notifyGenerateurIndisponible(centre, "A-G1", "HORS_SERVICE", java.util.List.of());

        Map<String, Object> evt = publieTous().get(0);
        assertEquals("GENERATEUR_INDISPONIBLE", evt.get("type"));
        assertNull(evt.get("id"));
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Map<String, Object>> publieTous() {
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(messaging, org.mockito.Mockito.atLeastOnce()).convertAndSend(eq("/topic/center/" + centre + "/events"),
                events.capture());
        return events.getAllValues().stream().map(o -> (Map<String, Object>) o).toList();
    }

    @Test
    void every_nurse_entry_type_is_accepted_and_an_unknown_one_is_refused() {
        for (String type : NotificationService.SAISIES_INFIRMIER) {
            service.notifySaisieInfirmier(centre, type, null, null, null, null, null);
        }
        assertEquals(8, NotificationService.SAISIES_INFIRMIER.size());
        assertThrows(IllegalArgumentException.class,
                () -> service.notifySaisieInfirmier(centre, "INCONNU", null, null, null, null, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void the_legacy_session_events_no_longer_target_the_doctor() {
        UUID s = UUID.randomUUID();
        UUID p = UUID.randomUUID();
        service.notifySeanceCreated(centre, s, p, "N", "P", "2026-10-04");
        service.notifySeanceValidated(centre, s, p, "N", "P", "2026-10-04");
        service.notifySeanceParamedicalSaved(centre, s, p, "N", "P", "2026-10-04");
        service.notifySeanceUpdated(centre, s);

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(messaging, org.mockito.Mockito.times(4)).convertAndSend(eq("/topic/center/" + centre + "/events"), events.capture());
        for (Object o : events.getAllValues()) {
            Map<String, String> payload = (Map<String, String>) ((Map<String, Object>) o).get("payload");
            assertFalse(payload.get("targetRoles").contains("MEDECIN"), String.valueOf(((Map<String, Object>) o).get("type")));
        }
    }

    @Test
    void the_notifier_resolves_the_patient_and_never_breaks_the_entry_it_accompanies() {
        NotificationService notifications = mock(NotificationService.class);
        PatientRepositoryPort patients = mock(PatientRepositoryPort.class);
        Patient patient = new Patient();
        patient.setNom("Dupont");
        patient.setPrenom("Jean");
        UUID patientId = UUID.randomUUID();
        when(patients.findById(any(), eq(CenterId.of(centre)))).thenReturn(Optional.of(patient));
        SaisieInfirmierNotifier notifier = new SaisieInfirmierNotifier(notifications, patients);

        notifier.saisie(centre, "ABSENCE", patientId, LocalDate.of(2026, 10, 4));

        verify(notifications).notifySaisieInfirmier(eq(centre), eq("ABSENCE"), eq(patientId), eq("Dupont"), eq("Jean"),
                eq("system"), eq("2026-10-04"));

        doThrow(new IllegalStateException("broker down")).when(notifications)
                .notifySaisieInfirmier(any(), any(), any(), any(), any(), any(), any());
        notifier.saisie(centre, "ABSENCE", patientId, null);   // aucune exception ne remonte
    }
}
