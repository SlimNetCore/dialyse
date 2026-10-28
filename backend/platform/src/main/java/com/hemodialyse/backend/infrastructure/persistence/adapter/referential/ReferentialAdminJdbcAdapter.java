package com.hemodialyse.backend.infrastructure.persistence.adapter.referential;

import com.hemodialyse.backend.domain.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.referential.admin.port.ReferentialAdminRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.adapter.referential.ReferentialAdminTables.Column;
import com.hemodialyse.backend.infrastructure.persistence.adapter.referential.ReferentialAdminTables.Reference;
import com.hemodialyse.backend.infrastructure.persistence.adapter.referential.ReferentialAdminTables.Table;
import com.hemodialyse.backend.infrastructure.persistence.adapter.referential.ReferentialAdminTables.Usage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adaptateur JDBC des référentiels administrables (tables sans entité JPA).
 * Chaque requête est filtrée sur {@code center_id} ; chaque écriture invalide les caches du centre.
 */
@Component
public class ReferentialAdminJdbcAdapter implements ReferentialAdminRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(ReferentialAdminJdbcAdapter.class);

    private final JdbcTemplate jdbc;
    private final ReferentialCacheEvictor cacheEvictor;

    public ReferentialAdminJdbcAdapter(JdbcTemplate jdbc, ReferentialCacheEvictor cacheEvictor) {
        this.jdbc = jdbc;
        this.cacheEvictor = cacheEvictor;
    }

    private static String select(Table table) {
        StringBuilder sql = new StringBuilder("SELECT t.id");
        table.columns().forEach(c -> sql.append(", t.").append(c.column()));
        table.references().forEach(r -> sql.append(", ").append(r.displayExpression()).append(" AS ref_").append(r.field()));
        return sql.append(' ').toString();
    }

    private static String from(Table table) {
        StringBuilder sql = new StringBuilder("FROM ").append(table.name()).append(" t");
        for (Reference ref : table.references()) {
            String alias = "r_" + ref.field();
            sql.append(" LEFT JOIN ").append(ref.table()).append(' ').append(alias)
                    .append(" ON ").append(alias).append(".id = t.").append(table.column(ref.field()).column())
                    .append(" AND ").append(alias).append(".center_id = t.center_id");
        }
        return sql.toString();
    }

    private static String where(Table table, CenterId centerId, String search, List<Object> params) {
        params.add(centerId.value());
        if (search == null || search.isBlank()) return " WHERE t.center_id = ?";
        String like = "%" + search.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String conditions = table.searchColumns().stream()
                .map(col -> "LOWER(COALESCE(" + col + ", '')) LIKE ? ESCAPE '\\'")
                .collect(Collectors.joining(" OR "));
        table.searchColumns().forEach(col -> params.add(like));
        return " WHERE t.center_id = ? AND (" + conditions + ")";
    }

    private static RowMapper<ReferentialEntry> mapper(Table table) {
        return (rs, i) -> {
            Map<String, String> values = new LinkedHashMap<>();
            for (Column c : table.columns()) values.put(c.field(), read(rs, c));
            Map<String, String> refs = new HashMap<>();
            for (Reference r : table.references()) {
                String display = rs.getString("ref_" + r.field());
                if (display != null && values.get(r.field()) != null) refs.put(r.field(), display);
            }
            return new ReferentialEntry(rs.getObject("id", UUID.class), values, refs);
        };
    }

    private static String read(ResultSet rs, Column c) throws SQLException {
        return switch (c.type()) {
            case TEXT -> rs.getString(c.column());
            case DECIMAL -> {
                BigDecimal value = rs.getBigDecimal(c.column());
                yield value == null ? null : value.toPlainString();
            }
            case INTEGER -> {
                int value = rs.getInt(c.column());
                yield rs.wasNull() ? null : String.valueOf(value);
            }
            case UUID -> {
                Object value = rs.getObject(c.column());
                yield value == null ? null : value.toString();
            }
        };
    }

    private static Object toSql(Column c, String value) {
        if (value == null) return null;
        return switch (c.type()) {
            case TEXT -> value;
            case DECIMAL -> new BigDecimal(value);
            case INTEGER -> Integer.valueOf(value);
            case UUID -> UUID.fromString(value);
        };
    }

    @Override
    public PagedResult<ReferentialEntry> findPaged(CenterId centerId, ReferentialKind kind, String search, int page, int size) {
        Table table = ReferentialAdminTables.of(kind);
        List<Object> params = new ArrayList<>();
        String where = where(table, centerId, search, params);

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from(table) + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);
        List<ReferentialEntry> items = jdbc.query(
                select(table) + from(table) + where + " ORDER BY " + table.orderBy() + ", t.id LIMIT ? OFFSET ?",
                mapper(table), pageParams.toArray());
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    // ---- SQL ---------------------------------------------------------------------------------------------------

    @Override
    public Optional<ReferentialEntry> findById(CenterId centerId, ReferentialKind kind, UUID id) {
        Table table = ReferentialAdminTables.of(kind);
        return jdbc.query(select(table) + from(table) + " WHERE t.center_id = ? AND t.id = ?",
                mapper(table), centerId.value(), id).stream().findFirst();
    }

    @Override
    public List<ReferentialEntry> findAllForMatching(CenterId centerId, ReferentialKind kind) {
        Table table = ReferentialAdminTables.of(kind);
        return jdbc.query(select(table) + from(table) + " WHERE t.center_id = ?", mapper(table), centerId.value());
    }

    @Override
    public UUID insert(CenterId centerId, ReferentialKind kind, Map<String, String> values) {
        Table table = ReferentialAdminTables.of(kind);
        UUID id = UUID.randomUUID();
        String columns = table.columns().stream().map(Column::column).collect(Collectors.joining(", "));
        String placeholders = table.columns().stream().map(c -> "?").collect(Collectors.joining(", "));
        List<Object> params = new ArrayList<>(List.of(id, centerId.value()));
        table.columns().forEach(c -> params.add(toSql(c, values.get(c.field()))));

        jdbc.update("INSERT INTO " + table.name() + " (id, center_id, " + columns + ") VALUES (?, ?, " + placeholders + ")",
                params.toArray());
        cacheEvictor.evictAfterCommit(centerId, table.caches());
        return id;
    }

    @Override
    public void update(CenterId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        Table table = ReferentialAdminTables.of(kind);
        String assignments = table.columns().stream().map(c -> c.column() + " = ?").collect(Collectors.joining(", "));
        List<Object> params = new ArrayList<>();
        table.columns().forEach(c -> params.add(toSql(c, values.get(c.field()))));
        params.add(id);
        params.add(centerId.value());

        jdbc.update("UPDATE " + table.name() + " SET " + assignments + " WHERE id = ? AND center_id = ?", params.toArray());
        cacheEvictor.evictAfterCommit(centerId, table.caches());
    }

    @Override
    public void delete(CenterId centerId, ReferentialKind kind, UUID id) {
        Table table = ReferentialAdminTables.of(kind);
        jdbc.update("DELETE FROM " + table.name() + " WHERE id = ? AND center_id = ?", id, centerId.value());
        cacheEvictor.evictAfterCommit(centerId, table.caches());
    }

    @Override
    public long countUsages(CenterId centerId, ReferentialKind kind, UUID id) {
        long total = 0;
        for (Usage usage : ReferentialAdminTables.of(kind).usages()) {
            try {
                Long count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + usage.table() + " WHERE " + usage.column() + " = ?", Long.class, id);
                total += count == null ? 0 : count;
            } catch (DataAccessException e) {
                // Table absente d'un schéma allégé (ex. H2 de développement sans le module concerné).
                log.debug("Contrôle d'usage ignoré sur {}.{} : {}", usage.table(), usage.column(), e.getMessage());
            }
        }
        return total;
    }
}

