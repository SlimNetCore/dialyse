package com.hemodialyse.backend.application.seance;

import com.hemodialyse.backend.application.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Séances « créées » oubliées : seules celles des 7 derniers jours passés (aujourd'hui exclu), du centre demandé et
 * jamais validées sont comptées ; la notification ne vise que l'administrateur.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SeanceRegularisationServiceIntegrationTest {

    private static final UUID CENTER_A = UUID.fromString("66666666-6666-6666-6666-666666666661");
    private static final UUID CENTER_B = UUID.fromString("66666666-6666-6666-6666-666666666662");
    private static final UUID PATIENT = UUID.fromString("66666666-6666-6666-6666-666666666663");
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 5);

    @Autowired
    private SeanceRegularisationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        seance(CENTER_A, AUJOURDHUI.minusDays(1), "CREE");
        seance(CENTER_A, AUJOURDHUI.minusDays(4), "CREE");
        seance(CENTER_A, AUJOURDHUI.minusDays(7), "CREE");
        seance(CENTER_A, AUJOURDHUI.minusDays(8), "CREE");        // trop ancienne
        seance(CENTER_A, AUJOURDHUI, "CREE");                     // aujourd'hui : pas encore oubliée
        seance(CENTER_A, AUJOURDHUI.minusDays(2), "VALIDEE");     // déjà validée
        seance(CENTER_B, AUJOURDHUI.minusDays(3), "CREE");        // autre centre
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM seances WHERE center_id IN (?, ?)", CENTER_A, CENTER_B);
    }

    private void seance(UUID center, LocalDate date, String statut) {
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), PATIENT, center, date, statut, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void counts_the_unvalidated_sessions_of_the_last_seven_past_days_and_finds_the_oldest() {
        var resume = service.aRegulariser(CENTER_A, AUJOURDHUI);

        assertEquals(3, resume.total());
        assertEquals(AUJOURDHUI.minusDays(7), resume.plusAncienne());
    }

    @Test
    void is_scoped_to_the_requested_center() {
        var resume = service.aRegulariser(CENTER_B, AUJOURDHUI);

        assertEquals(1, resume.total());
        assertEquals(AUJOURDHUI.minusDays(3), resume.plusAncienne());
    }

    @Test
    void reports_nothing_when_no_session_was_forgotten() {
        var resume = service.aRegulariser(UUID.fromString("66666666-6666-6666-6666-666666666699"), AUJOURDHUI);

        assertEquals(0, resume.total());
        assertNull(resume.plusAncienne());
    }

    @Test
    @SuppressWarnings("unchecked")
    void the_reminder_targets_the_administrator_only() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        NotificationService notifications = new NotificationService(messaging,
                mock(com.hemodialyse.backend.application.direction.DirectionRealtimeService.class));

        notifications.notifySeancesARegulariser(CENTER_A, 3, AUJOURDHUI.minusDays(7));

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq("/topic/center/" + CENTER_A + "/events"), captor.capture());
        Map<String, Object> event = (Map<String, Object>) captor.getValue();
        Map<String, String> payload = (Map<String, String>) event.get("payload");
        assertEquals("SEANCES_A_REGULARISER", event.get("type"));
        assertEquals("ADMIN", payload.get("targetRoles"));
        assertEquals("3", payload.get("nbSeances"));
        assertEquals("2026-09-28", payload.get("plusAncienne"));
        verify(messaging, org.mockito.Mockito.atLeastOnce()).convertAndSend(any(String.class), any(Object.class));
    }
}
