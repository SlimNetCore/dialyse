package com.hemodialyse.backend.contexts.notifications.infrastructure.persistence;

import com.hemodialyse.backend.contexts.notifications.application.NotificationDuJournal;
import com.hemodialyse.backend.contexts.notifications.application.NotificationsDuJournal;
import com.hemodialyse.backend.contexts.notifications.domain.Notification;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.domain.TypeDeNotification;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
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
 * Journal sur {@code notification_evenement} (lectures par utilisateur dans {@code notification_lecture}) : mêmes tables que la plateforme
 * d'avant NOT1. Les rôles ciblés sont stockés entre virgules (« ,ADMIN,SECRETAIRE, ») ; une valeur vide vise tout le monde. Le rôle se cherche
 * par POSITION (texte exact) et non par LIKE : le souligne et le pour-cent ne sont pas des jokers (KB-NOT1-1).
 */
public class NotificationsDuJournalJdbc implements NotificationsDuJournal {

    private static final TypeReference<Map<String, String>> CONTENU = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public NotificationsDuJournalJdbc(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    private static Timestamp horodatage(Instant instant) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    /** « Visible par cet utilisateur » : sans cible, ou ciblant exactement l'un de ses rôles, ou adressée à son compte. */
    private static String filtreDAudience(Set<String> roles, String utilisateur, List<Object> args) {
        StringBuilder sql = new StringBuilder("(n.target_roles = ''");
        for (String role : roles) {
            sql.append(" OR POSITION(CAST(? AS VARCHAR) IN n.target_roles) > 0");
            args.add("," + role + ",");
        }
        sql.append(" OR n.target_user_id = ?");
        args.add(utilisateur);
        return sql.append(')').toString();
    }

    @Override
    public void enregistrer(Notification notification) {
        String roles = notification.audience().rolesEnTexte();
        jdbc.update("INSERT INTO notification_evenement (id, center_id, type, payload, target_roles, cree_le, target_user_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", notification.id().valeur(), notification.centre().valeur(), notification.type().valeur(),
                mapper.writeValueAsString(notification.contenu()), roles.isEmpty() ? "" : "," + roles + ",", horodatage(notification.creeLe()),
                notification.audience().destinataire());
    }

    @Override
    public boolean existe(NotificationId id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE id = ?", Integer.class, id.valeur());
        return n != null && n > 0;
    }

    @Override
    public PagedResult<NotificationDuJournal> lister(CentreId centre, String utilisateur, Set<String> roles, Instant depuis, int page, int taille) {
        List<Object> filtre = new ArrayList<>();
        String audienceSql = filtreDAudience(roles, utilisateur, filtre);
        List<Object> argsTotal = new ArrayList<>(List.of(centre.valeur(), horodatage(depuis)));
        argsTotal.addAll(filtre);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement n WHERE n.center_id = ? AND n.cree_le >= ? AND "
                + audienceSql, Long.class, argsTotal.toArray());

        List<Object> args = new ArrayList<>();
        args.add(utilisateur);
        args.add(centre.valeur());
        args.add(horodatage(depuis));
        args.addAll(filtre);
        args.add(taille);
        args.add((long) page * taille);
        List<NotificationDuJournal> items = jdbc.query("SELECT n.id, n.type, n.payload, n.cree_le, "
                        + "(l.notification_id IS NOT NULL) AS lue FROM notification_evenement n "
                        + "LEFT JOIN notification_lecture l ON l.notification_id = n.id AND l.user_id = ? "
                        + "WHERE n.center_id = ? AND n.cree_le >= ? AND " + audienceSql
                        + " ORDER BY n.cree_le DESC, n.id LIMIT ? OFFSET ?",
                (rs, i) -> new NotificationDuJournal(new NotificationId(rs.getObject("id", UUID.class)), new TypeDeNotification(rs.getString("type")),
                        mapper.readValue(rs.getString("payload"), CONTENU), instant(rs.getTimestamp("cree_le")), rs.getBoolean("lue")),
                args.toArray());
        return PagedResult.of(items, total == null ? 0 : total, page, taille);
    }

    @Override
    public void marquerLues(CentreId centre, String utilisateur, Set<String> roles, Collection<NotificationId> ids, Instant quand) {
        for (NotificationId id : ids) {
            List<Object> args = new ArrayList<>(List.of(utilisateur, horodatage(quand), id.valeur(), centre.valeur()));
            String audienceSql = filtreDAudience(roles, utilisateur, args);
            args.add(utilisateur);
            jdbc.update("INSERT INTO notification_lecture (notification_id, user_id, lue_le) "
                    + "SELECT n.id, ?, ? FROM notification_evenement n WHERE n.id = ? AND n.center_id = ? AND " + audienceSql
                    + " AND NOT EXISTS (SELECT 1 FROM notification_lecture l WHERE l.notification_id = n.id AND l.user_id = ?)", args.toArray());
        }
    }

    @Override
    public void toutMarquerLu(CentreId centre, String utilisateur, Set<String> roles, Instant depuis, Instant quand) {
        List<Object> args = new ArrayList<>(List.of(utilisateur, horodatage(quand), centre.valeur(), horodatage(depuis)));
        String audienceSql = filtreDAudience(roles, utilisateur, args);
        args.add(utilisateur);
        jdbc.update("INSERT INTO notification_lecture (notification_id, user_id, lue_le) "
                + "SELECT n.id, ?, ? FROM notification_evenement n WHERE n.center_id = ? AND n.cree_le >= ? AND "
                + audienceSql + " AND NOT EXISTS (SELECT 1 FROM notification_lecture l WHERE l.notification_id = n.id "
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
