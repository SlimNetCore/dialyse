package com.hemodialyse.backend.infrastructure.persistence.adapter.notification;

import com.hemodialyse.backend.application.notification.NotificationJournalPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Journal durable des alertes sur {@code notification_evenement} (lectures par utilisateur dans
 * {@code notification_lecture}). Les rôles ciblés sont stockés entre virgules (« ,ADMIN,SECRETAIRE, ») : une valeur
 * vide vise tout le monde. Seules les alertes des {@link #FENETRE} derniers jours sont proposées.
 */
@Component
public class NotificationJournalJdbcAdapter implements NotificationJournalPort {

    static final Duration FENETRE = Duration.ofDays(30);
    private static final TypeReference<Map<String, String>> PAYLOAD = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public NotificationJournalJdbcAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    private static Timestamp horodatage(Instant instant) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    /**
     * Filtre « visible par ces rôles » : alerte sans cible, ou ciblant au moins l'un d'eux.
     */
    private static String filtreRoles(Set<String> roles, List<Object> args) {
        StringBuilder sql = new StringBuilder("(n.target_roles = ''");
        for (String role : roles) {
            sql.append(" OR n.target_roles LIKE ?");
            args.add("%," + role + ",%");
        }
        return sql.append(')').toString();
    }

    @Override
    public void enregistrer(UUID id, UUID centerId, String type, Map<String, String> payload, Instant creeLe) {
        String cibles = payload.getOrDefault("targetRoles", "").trim();
        jdbc.update("INSERT INTO notification_evenement (id, center_id, type, payload, target_roles, cree_le) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", id, centerId, type, mapper.writeValueAsString(payload),
                cibles.isEmpty() ? "" : "," + cibles + ",", horodatage(creeLe));
    }

    @Override
    public PagedResult<Alerte> lister(UUID centerId, String userId, Set<String> roles, int page, int size) {
        List<Object> filtre = new ArrayList<>();
        String roleSql = filtreRoles(roles, filtre);
        List<Object> argsTotal = new ArrayList<>(List.of(centerId, horodatage(Instant.now().minus(FENETRE))));
        argsTotal.addAll(filtre);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement n WHERE n.center_id = ? "
                + "AND n.cree_le >= ? AND " + roleSql, Long.class, argsTotal.toArray());

        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.add(centerId);
        args.add(horodatage(Instant.now().minus(FENETRE)));
        args.addAll(filtre);
        args.add(size);
        args.add((long) page * size);
        List<Alerte> items = jdbc.query("SELECT n.id, n.type, n.payload, n.cree_le, "
                        + "(l.notification_id IS NOT NULL) AS lue FROM notification_evenement n "
                        + "LEFT JOIN notification_lecture l ON l.notification_id = n.id AND l.user_id = ? "
                        + "WHERE n.center_id = ? AND n.cree_le >= ? AND " + roleSql
                        + " ORDER BY n.cree_le DESC, n.id LIMIT ? OFFSET ?",
                (rs, i) -> new Alerte(rs.getObject("id", UUID.class), rs.getString("type"),
                        mapper.readValue(rs.getString("payload"), PAYLOAD), instant(rs.getTimestamp("cree_le")),
                        rs.getBoolean("lue")), args.toArray());
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public void marquerLues(UUID centerId, String userId, Collection<UUID> ids) {
        for (UUID id : ids) {
            jdbc.update("INSERT INTO notification_lecture (notification_id, user_id, lue_le) "
                    + "SELECT n.id, ?, ? FROM notification_evenement n WHERE n.id = ? AND n.center_id = ? "
                    + "AND NOT EXISTS (SELECT 1 FROM notification_lecture l WHERE l.notification_id = n.id "
                    + "AND l.user_id = ?)", userId, horodatage(Instant.now()), id, centerId, userId);
        }
    }

    @Override
    public void toutMarquerLu(UUID centerId, String userId, Set<String> roles) {
        List<Object> args = new ArrayList<>(List.of(userId, horodatage(Instant.now()), centerId,
                horodatage(Instant.now().minus(FENETRE))));
        String roleSql = filtreRoles(roles, args);
        args.add(userId);
        jdbc.update("INSERT INTO notification_lecture (notification_id, user_id, lue_le) "
                + "SELECT n.id, ?, ? FROM notification_evenement n WHERE n.center_id = ? AND n.cree_le >= ? AND "
                + roleSql + " AND NOT EXISTS (SELECT 1 FROM notification_lecture l WHERE l.notification_id = n.id "
                + "AND l.user_id = ?)", args.toArray());
    }

    @Override
    public int purger(Instant avant) {
        Timestamp limite = horodatage(avant);
        jdbc.update("DELETE FROM notification_lecture WHERE notification_id IN "
                + "(SELECT id FROM notification_evenement WHERE cree_le < ?)", limite);
        return jdbc.update("DELETE FROM notification_evenement WHERE cree_le < ?", limite);
    }
}
