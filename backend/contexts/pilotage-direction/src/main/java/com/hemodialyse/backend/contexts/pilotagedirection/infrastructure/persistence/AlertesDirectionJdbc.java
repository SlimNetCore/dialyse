package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TypeAlerte;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur JDBC du port {@link AlerteDirectionRepository}. Toutes les requêtes sont
 * filtrées par {@code center_id} (règle multi-centre, AGENTS.md §2).
 */
public class AlertesDirectionJdbc implements AlerteDirectionRepository {

    private final JdbcTemplate jdbc;

    public AlertesDirectionJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private AlerteDirection mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        TypeAlerte type = TypeAlerte.valueOf(rs.getString("type"));
        String seuil = rs.getString("seuil");
        String message = rs.getString("message");
        LocalDate date = rs.getDate("date_alerte") == null ? null
                : rs.getDate("date_alerte").toLocalDate();
        boolean lue = Boolean.TRUE.equals(rs.getObject("lue", Boolean.class));
        OffsetDateTime createdAt = rs.getTimestamp("created_at") == null ? null
                : rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime updatedAt = rs.getTimestamp("updated_at") == null ? null
                : rs.getTimestamp("updated_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        return AlerteDirection.reconstituer(new AlerteDirectionId(id), new CentreId(centreId),
                type, seuil, message, date, lue, createdAt, updatedAt);
    }

    @Override
    public void save(AlerteDirection alerte) {
        int n = jdbc.update("UPDATE alertes_direction SET type=?, seuil=?, message=?, date_alerte=?, "
                        + "lue=?, updated_at=? "
                        + "WHERE id=? AND center_id=?",
                alerte.type().name(), alerte.seuil(), alerte.message(),
                alerte.date() == null ? null : Date.valueOf(alerte.date()),
                alerte.lue(),
                java.sql.Timestamp.from(alerte.updatedAt().toInstant()),
                alerte.id().value(), alerte.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO alertes_direction (id, center_id, type, seuil, message, "
                            + "date_alerte, lue, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?)",
                    alerte.id().value(), alerte.centreId().valeur(), alerte.type().name(),
                    alerte.seuil(), alerte.message(),
                    alerte.date() == null ? null : Date.valueOf(alerte.date()),
                    alerte.lue(),
                    java.sql.Timestamp.from(alerte.createdAt().toInstant()),
                    java.sql.Timestamp.from(alerte.updatedAt().toInstant()));
        }
    }

    @Override
    public Optional<AlerteDirection> trouver(AlerteDirectionId id, CentreId centreId) {
        List<AlerteDirection> r = jdbc.query("SELECT id, center_id, type, seuil, message, date_alerte, "
                        + "lue, created_at, updated_at "
                        + "FROM alertes_direction WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.value(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public boolean existeParCentreEtJour(CentreId centreId, LocalDate date) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM alertes_direction "
                        + "WHERE center_id = ? AND date_alerte = ?",
                Integer.class, centreId.valeur(), Date.valueOf(date));
        return n != null && n > 0;
    }

    @Override
    public PagedResult<AlerteDirection> listerParCentre(CentreId centreId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM alertes_direction WHERE center_id = ?",
                Long.class, centreId.valeur());
        List<AlerteDirection> items = jdbc.query("SELECT id, center_id, type, seuil, message, date_alerte, "
                        + "lue, created_at, updated_at "
                        + "FROM alertes_direction WHERE center_id = ? "
                        + "ORDER BY date_alerte DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), size, page * size);
        return PagedResult.of(items, total, page, size);
    }
}
