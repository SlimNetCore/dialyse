package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Alert;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Historique des alertes du tableau de bord de la direction : à chaque recalcul temps réel, compare les alertes
 * actuellement levées à celles déjà ouvertes en base et journalise les apparitions et les résolutions.
 * <p>
 * Alimenté uniquement pendant qu'au moins une direction est connectée à la société (même limite que le reste du
 * temps réel, voir {@link DirectionRealtimeService}) : ce n'est pas un audit exhaustif, mais un historique récent
 * utile à la direction qui consulte régulièrement son tableau de bord.
 */
@Service
public class DirectionAlertHistoryService {

    private final JdbcTemplate jdbc;

    public DirectionAlertHistoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Compare les alertes actives à l'historique ouvert : ouvre les nouvelles, referme celles qui ont disparu.
     */
    public void record(UUID societeId, List<Alert> current) {
        record(societeId, current, OffsetDateTime.now());
    }

    void record(UUID societeId, List<Alert> current, OffsetDateTime now) {
        List<Open> open = jdbc.query("SELECT id, center_id, code FROM direction_alert_history "
                        + "WHERE societe_id = ? AND resolved_at IS NULL",
                (rs, i) -> new Open(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
                        rs.getString("code")),
                societeId);

        Set<String> currentKeys = new HashSet<>();
        Timestamp ts = Timestamp.from(now.toInstant());
        for (Alert a : current) {
            currentKeys.add(a.centerId() + "|" + a.code());
            boolean alreadyOpen = open.stream().anyMatch(o -> o.centerId().equals(a.centerId()) && o.code().equals(a.code()));
            if (!alreadyOpen) {
                jdbc.update("INSERT INTO direction_alert_history "
                                + "(id, societe_id, center_id, centre_nom, code, severity, valeur, first_seen_at) "
                                + "VALUES (?,?,?,?,?,?,?,?)",
                        UUID.randomUUID(), societeId, a.centerId(), a.centre(), a.code(), a.severity().name(),
                        a.valeur(), ts);
            }
        }
        for (Open o : open) {
            if (!currentKeys.contains(o.centerId() + "|" + o.code())) {
                jdbc.update("UPDATE direction_alert_history SET resolved_at = ? WHERE id = ?", ts, o.id());
            }
        }
    }

    /**
     * Historique récent (alertes en cours puis résolues), du plus récemment actif au plus ancien.
     */
    public List<Entry> history(UUID societeId, int limit) {
        return jdbc.query("SELECT center_id, centre_nom, code, severity, valeur, first_seen_at, resolved_at "
                        + "FROM direction_alert_history WHERE societe_id = ? "
                        + "ORDER BY COALESCE(resolved_at, first_seen_at) DESC, first_seen_at DESC LIMIT ?",
                (rs, i) -> new Entry(rs.getObject("center_id", UUID.class), rs.getString("centre_nom"),
                        rs.getString("code"), rs.getString("severity"), rs.getBigDecimal("valeur"),
                        rs.getObject("first_seen_at", OffsetDateTime.class),
                        rs.getObject("resolved_at", OffsetDateTime.class)),
                societeId, limit);
    }

    private record Open(UUID id, UUID centerId, String code) {
    }

    public record Entry(UUID centerId, String centre, String code, String severity, BigDecimal valeur,
                        OffsetDateTime firstSeenAt, OffsetDateTime resolvedAt) {
    }
}
