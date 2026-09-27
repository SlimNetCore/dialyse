package com.hemodialyse.backend.application.audit;

import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consultation paginée du journal d'audit (AGENTS.md §9 : aucune liste sans pagination). Le périmètre — un centre
 * précis pour un administrateur, toute la plateforme pour le propriétaire — est décidé par l'appelant
 * ({@code AuditRestController}), jamais ici : ce service exécute la recherche qu'on lui demande.
 */
@Service
public class AuditQueryService {

    private final JdbcTemplate jdbc;

    public AuditQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PagedResult<Entry> search(Search s) {
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (s.centerId() != null) {
            where.add("center_id = ?");
            params.add(s.centerId());
        }
        if (s.societeId() != null) {
            where.add("societe_id = ?");
            params.add(s.societeId());
        }
        if (s.userId() != null) {
            where.add("user_id = ?");
            params.add(s.userId());
        }
        if (s.actionCode() != null && !s.actionCode().isBlank()) {
            where.add("action_code = ?");
            params.add(s.actionCode());
        }
        if (s.from() != null) {
            where.add("occurred_at >= ?");
            params.add(Date.valueOf(s.from()));
        }
        if (s.to() != null) {
            where.add("occurred_at < ?");
            params.add(Date.valueOf(s.to().plusDays(1)));
        }
        String clause = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);

        long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log" + clause, Long.class, params.toArray());

        int page = Math.max(0, s.page());
        int size = Math.max(1, Math.min(s.size(), 200));
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(page * size);
        List<Entry> items = jdbc.query("SELECT occurred_at, user_id, username, roles, center_id, societe_id, "
                        + "action_code, entity_type, entity_id, libelle, http_method, route_template, status_code, "
                        + "duration_ms, ip_address FROM audit_log" + clause
                        + " ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> new Entry(
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("username"),
                        rs.getString("roles"),
                        rs.getObject("center_id", UUID.class),
                        rs.getObject("societe_id", UUID.class),
                        rs.getString("action_code"),
                        rs.getString("entity_type"),
                        rs.getString("entity_id"),
                        rs.getString("libelle"),
                        rs.getString("http_method"),
                        rs.getString("route_template"),
                        rs.getInt("status_code"),
                        rs.getLong("duration_ms"),
                        rs.getString("ip_address")),
                pageParams.toArray());
        return PagedResult.of(items, total, page, size);
    }

    /**
     * Codes d'action distincts déjà journalisés dans le périmètre demandé, pour peupler un filtre.
     */
    public List<String> distinctActionCodes(UUID centerId, UUID societeId) {
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (centerId != null) {
            where.add("center_id = ?");
            params.add(centerId);
        }
        if (societeId != null) {
            where.add("societe_id = ?");
            params.add(societeId);
        }
        String clause = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        return jdbc.queryForList("SELECT DISTINCT action_code FROM audit_log" + clause + " ORDER BY action_code",
                String.class, params.toArray());
    }

    public record Search(UUID centerId, UUID societeId, UUID userId, String actionCode, LocalDate from,
                         LocalDate to, int page, int size) {
    }

    public record Entry(OffsetDateTime occurredAt, UUID userId, String username, String roles, UUID centerId,
                        UUID societeId, String actionCode, String entityType, String entityId, String libelle,
                        String httpMethod, String routeTemplate, int statusCode, long durationMs, String ipAddress) {
    }
}
