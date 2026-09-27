package com.hemodialyse.backend.application.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Écriture asynchrone de la traçabilité : {@link #enqueue} ne fait jamais de requête base de données — il pose
 * l'évènement dans une file en mémoire et rend la main immédiatement, pour ne jamais ralentir la requête HTTP qui
 * l'a produit. {@link AuditScheduler} appelle {@link #flush()} régulièrement pour écrire par lot.
 * <p>
 * File bornée : si elle déborde (écriture base indisponible plus longtemps que prévu), les évènements les plus
 * anciens sont abandonnés plutôt que de laisser la mémoire croître sans limite — la traçabilité ne doit jamais faire
 * planter l'application.
 */
@Service
public class AuditWriterService {

    private static final Logger log = LoggerFactory.getLogger(AuditWriterService.class);
    private static final int MAX_QUEUED = 5_000;
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final Queue<AuditEvent> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();

    public AuditWriterService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static void setUuid(java.sql.PreparedStatement ps, int index, UUID value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    public void enqueue(AuditEvent event) {
        if (queueSize.incrementAndGet() > MAX_QUEUED) {
            queue.poll();
            queueSize.decrementAndGet();
            if (dropped.incrementAndGet() % 100 == 1) {
                log.warn("File d'audit saturée : évènements les plus anciens abandonnés ({} au total)", dropped.get());
            }
        }
        queue.add(event);
    }

    /**
     * Écrit par lots tout ce qui est en attente. Appelé par le planificateur, jamais par le thread HTTP.
     */
    public void flush() {
        List<AuditEvent> batch = new ArrayList<>(BATCH_SIZE);
        AuditEvent e;
        while ((e = queue.poll()) != null) {
            queueSize.decrementAndGet();
            batch.add(e);
            if (batch.size() == BATCH_SIZE) {
                write(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            write(batch);
        }
    }

    private void write(List<AuditEvent> batch) {
        try {
            jdbc.batchUpdate("INSERT INTO audit_log (id, occurred_at, user_id, username, roles, center_id, "
                            + "societe_id, action_code, entity_type, entity_id, libelle, http_method, "
                            + "route_template, status_code, duration_ms, ip_address) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    batch, batch.size(), (ps, ev) -> {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setTimestamp(2, Timestamp.from(ev.occurredAt().toInstant()));
                        setUuid(ps, 3, ev.userId());
                        ps.setString(4, ev.username());
                        ps.setString(5, ev.roles());
                        setUuid(ps, 6, ev.centerId());
                        setUuid(ps, 7, ev.societeId());
                        ps.setString(8, ev.actionCode());
                        ps.setString(9, ev.entityType());
                        ps.setString(10, ev.entityId());
                        ps.setString(11, ev.libelle());
                        ps.setString(12, ev.httpMethod());
                        ps.setString(13, ev.routeTemplate());
                        ps.setInt(14, ev.statusCode());
                        ps.setLong(15, ev.durationMs());
                        ps.setString(16, ev.ipAddress());
                    });
        } catch (RuntimeException ex) {
            // La traçabilité ne doit jamais faire échouer l'application : un lot perdu est journalisé, pas propagé.
            log.error("Écriture du journal d'audit impossible ({} évènement(s) perdu(s))", batch.size(), ex);
        }
    }

    /**
     * Purge les entrées plus anciennes que la rétention configurée ; renvoie le nombre de lignes supprimées.
     */
    public int purgeOlderThan(int retentionDays) {
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        return jdbc.update("DELETE FROM audit_log WHERE occurred_at < ?", java.sql.Date.valueOf(cutoff));
    }

    /**
     * Nombre d'évènements en attente d'écriture (diagnostic).
     */
    public int queued() {
        return queueSize.get();
    }
}
