package com.hemodialyse.backend.application.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Écriture par lot du journal d'audit : rien n'est écrit avant {@link AuditWriterService#flush()} (le thread HTTP
 * n'attend jamais l'écriture), et la purge respecte la rétention.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AuditWriterServiceTest {

    private static final UUID CENTRE = UUID.fromString("99995000-0000-0000-0000-0000000000a1");

    @Autowired
    private AuditWriterService service;
    @Autowired
    private JdbcTemplate jdbc;

    private static AuditEvent event(OffsetDateTime at) {
        return new AuditEvent(at, UUID.randomUUID(), "u-test", "ROLE_ADMIN", CENTRE, null,
                "PATIENTS_CREATION", "patients", "p-1", "Création : patients", "POST",
                "/api/v1/patients", 200, 12, "127.0.0.1");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_log WHERE center_id = ?", CENTRE);
    }

    @Test
    void nothing_is_written_until_flush_is_called() {
        service.enqueue(event(OffsetDateTime.now()));

        Integer countBeforeFlush = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE center_id = ?", Integer.class, CENTRE);
        assertEquals(0, countBeforeFlush, "l'écriture doit être différée, jamais synchrone");

        service.flush();

        Integer countAfterFlush = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE center_id = ?", Integer.class, CENTRE);
        assertEquals(1, countAfterFlush);
    }

    @Test
    void flush_writes_every_queued_event_in_one_go() {
        for (int i = 0; i < 12; i++) {
            service.enqueue(event(OffsetDateTime.now()));
        }
        service.flush();

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE center_id = ?", Integer.class, CENTRE);
        assertEquals(12, count);
        assertEquals(0, service.queued());
    }

    @Test
    void purge_removes_only_entries_older_than_the_retention() {
        service.enqueue(event(OffsetDateTime.now().minusDays(400)));
        service.enqueue(event(OffsetDateTime.now().minusDays(1)));
        service.flush();

        int deleted = service.purgeOlderThan(365);

        assertTrue(deleted >= 1);
        Integer remaining = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE center_id = ?", Integer.class, CENTRE);
        assertEquals(1, remaining, "seule l'entrée récente doit subsister");
    }
}
