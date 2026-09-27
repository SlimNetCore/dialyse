package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Alert;
import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Severity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Historique des alertes de la direction : ouverture au premier constat, pas de doublon tant qu'elle persiste,
 * résolution quand elle disparaît, puis nouvelle ouverture si elle réapparaît plus tard.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class DirectionAlertHistoryServiceTest {

    private static final UUID SOC = UUID.fromString("99996000-0000-0000-0000-000000000001");
    private static final UUID CENTRE = UUID.fromString("99996000-0000-0000-0000-0000000000a1");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DirectionAlertHistoryService service;

    private static Alert alert(BigDecimal valeur) {
        return new Alert(CENTRE, "Centre Historique", "STOCK_SOUS_SEUIL", Severity.WARNING, valeur);
    }

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM direction_alert_history WHERE societe_id = ?", SOC);
    }

    @Test
    void an_alert_is_opened_once_and_stays_open_while_it_persists() {
        OffsetDateTime t0 = OffsetDateTime.now();
        service.record(SOC, List.of(alert(new BigDecimal("3"))), t0);
        service.record(SOC, List.of(alert(new BigDecimal("3"))), t0.plusMinutes(1));

        List<DirectionAlertHistoryService.Entry> history = service.history(SOC, 10);
        assertEquals(1, history.size(), "aucun doublon tant que l'alerte persiste");
        assertNull(history.get(0).resolvedAt());
        assertEquals("STOCK_SOUS_SEUIL", history.get(0).code());
    }

    @Test
    void an_alert_that_disappears_is_marked_resolved() {
        OffsetDateTime t0 = OffsetDateTime.now();
        service.record(SOC, List.of(alert(new BigDecimal("3"))), t0);
        service.record(SOC, List.of(), t0.plusMinutes(5));

        List<DirectionAlertHistoryService.Entry> history = service.history(SOC, 10);
        assertEquals(1, history.size());
        assertNotNull(history.get(0).resolvedAt(), "l'alerte disparue doit être marquée résolue");
    }

    @Test
    void a_reappearing_alert_opens_a_new_entry() {
        OffsetDateTime t0 = OffsetDateTime.now();
        service.record(SOC, List.of(alert(new BigDecimal("3"))), t0);
        service.record(SOC, List.of(), t0.plusMinutes(1));
        service.record(SOC, List.of(alert(new BigDecimal("4"))), t0.plusMinutes(10));

        List<DirectionAlertHistoryService.Entry> history = service.history(SOC, 10);
        assertEquals(2, history.size(), "une résolue, une nouvelle ouverte");
        long open = history.stream().filter(e -> e.resolvedAt() == null).count();
        assertEquals(1, open);
    }

    @Test
    void history_is_scoped_to_its_societe() {
        UUID other = UUID.randomUUID();
        try {
            service.record(SOC, List.of(alert(new BigDecimal("3"))), OffsetDateTime.now());
            service.record(other, List.of(alert(new BigDecimal("3"))), OffsetDateTime.now());

            assertEquals(1, service.history(SOC, 10).size());
            assertTrue(service.history(other, 10).stream().noneMatch(e -> e.centre() == null));
        } finally {
            jdbc.update("DELETE FROM direction_alert_history WHERE societe_id = ?", other);
        }
    }
}
