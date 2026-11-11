package com.hemodialyse.backend.infrastructure.persistence.adapter.referential;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.Transporteurs;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminRepositoryPort;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
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
    private final Transporteurs transporteurs;
    private final ForfaitsDuCentre forfaits;
    private final GestionDesPayeurs payeurs;

    public ReferentialAdminJdbcAdapter(JdbcTemplate jdbc, ReferentialCacheEvictor cacheEvictor, Transporteurs transporteurs,
                                       ForfaitsDuCentre forfaits, GestionDesPayeurs payeurs) {
        this.jdbc = jdbc;
        this.payeurs = payeurs;
        this.cacheEvictor = cacheEvictor;
        this.forfaits = forfaits;
        this.transporteurs = transporteurs;
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

    /** Les médecins sont désactivés, jamais supprimés (V52) : les lectures ignorent les médecins inactifs. */
    private static String actifSeulement(Table table) {
        return "medecin".equals(table.name()) ? " AND t.actif = TRUE" : "";
    }

    private static String where(Table table, CentreId centerId, String search, List<Object> params) {
        params.add(centerId.valeur());
        if (search == null || search.isBlank()) return " WHERE t.center_id = ?" + actifSeulement(table);
        String like = "%" + search.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String conditions = table.searchColumns().stream()
                .map(col -> "LOWER(COALESCE(" + col + ", '')) LIKE ? ESCAPE '\\'")
                .collect(Collectors.joining(" OR "));
        table.searchColumns().forEach(col -> params.add(like));
        return " WHERE t.center_id = ? AND (" + conditions + ")" + actifSeulement(table);
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
    public PagedResult<ReferentialEntry> findPaged(CentreId centerId, ReferentialKind kind, String search, int page, int size) {
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
    public Optional<ReferentialEntry> findById(CentreId centerId, ReferentialKind kind, UUID id) {
        Table table = ReferentialAdminTables.of(kind);
        return jdbc.query(select(table) + from(table) + " WHERE t.center_id = ? AND t.id = ?" + actifSeulement(table),
                mapper(table), centerId.valeur(), id).stream().findFirst();
    }

    @Override
    public List<ReferentialEntry> findAllForMatching(CentreId centerId, ReferentialKind kind) {
        if (kind == ReferentialKind.TRANSPORTEUR) {
            // RF1 (ADR 0122) : les transporteurs appartiennent à patient-couverture ; la reprise des patients les retrouve par leur nom.
            return transporteurs.lister(centerId.valeur()).stream()
                    .map(t -> new ReferentialEntry(t.id(), java.util.Collections.singletonMap("nom", t.nom()), Map.of())).toList();
        }
        if (kind == ReferentialKind.FORFAIT) {
            // RF2 (ADR 0124) : le catalogue de forfaits appartient à patient-couverture ; la reprise de l'historique les retrouve par leur code.
            return forfaits.lister(centerId.valeur()).stream().map(f -> {
                Map<String, String> valeurs = new LinkedHashMap<>();
                valeurs.put("code", f.code());
                valeurs.put("libelle", f.libelle());
                valeurs.put("prix", f.prix() == null ? null : f.prix().toPlainString());
                return new ReferentialEntry(f.id(), valeurs, Map.of());
            }).toList();
        }
        if (kind == ReferentialKind.CAISSE || kind == ReferentialKind.AGENCE || kind == ReferentialKind.CENTRE_PAYEUR) {
            // RF3a (ADR 0126) : la chaîne des payeurs appartient à patient-couverture ; la reprise des patients retrouve un payeur par son code.
            GestionDesPayeurs.Niveau niveau = kind == ReferentialKind.CAISSE ? GestionDesPayeurs.Niveau.CAISSE
                    : kind == ReferentialKind.AGENCE ? GestionDesPayeurs.Niveau.AGENCE : GestionDesPayeurs.Niveau.CENTRE_PAYEUR;
            return payeurs.toutes(centerId.valeur(), niveau).stream().map(p -> {
                Map<String, String> valeurs = new LinkedHashMap<>();
                valeurs.put("code", p.code());
                valeurs.put("nom", p.nom());
                if (kind == ReferentialKind.CAISSE) valeurs.put("typeCaisse", p.typeCaisse());
                if (kind == ReferentialKind.AGENCE) valeurs.put("caisse", p.parent() == null ? null : p.parent().toString());
                if (kind == ReferentialKind.CENTRE_PAYEUR) {
                    valeurs.put("adresse", p.adresse());
                    valeurs.put("agence", p.parent() == null ? null : p.parent().toString());
                }
                return new ReferentialEntry(p.id(), valeurs, Map.of());
            }).toList();
        }
        Table table = ReferentialAdminTables.of(kind);
        return jdbc.query(select(table) + from(table) + " WHERE t.center_id = ?" + actifSeulement(table), mapper(table), centerId.valeur());
    }

    @Override
    public UUID insert(CentreId centerId, ReferentialKind kind, Map<String, String> values) {
        Table table = ReferentialAdminTables.of(kind);
        UUID id = UUID.randomUUID();
        String columns = table.columns().stream().map(Column::column).collect(Collectors.joining(", "));
        String placeholders = table.columns().stream().map(c -> "?").collect(Collectors.joining(", "));
        List<Object> params = new ArrayList<>(List.of(id, centerId.valeur()));
        table.columns().forEach(c -> params.add(toSql(c, values.get(c.field()))));

        jdbc.update("INSERT INTO " + table.name() + " (id, center_id, " + columns + ") VALUES (?, ?, " + placeholders + ")",
                params.toArray());
        cacheEvictor.evictAfterCommit(centerId, table.caches());
        return id;
    }

    @Override
    public void update(CentreId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        Table table = ReferentialAdminTables.of(kind);
        String assignments = table.columns().stream().map(c -> c.column() + " = ?").collect(Collectors.joining(", "));
        List<Object> params = new ArrayList<>();
        table.columns().forEach(c -> params.add(toSql(c, values.get(c.field()))));
        params.add(id);
        params.add(centerId.valeur());

        jdbc.update("UPDATE " + table.name() + " SET " + assignments + " WHERE id = ? AND center_id = ?", params.toArray());
        cacheEvictor.evictAfterCommit(centerId, table.caches());
    }

    @Override
    public void delete(CentreId centerId, ReferentialKind kind, UUID id) {
        Table table = ReferentialAdminTables.of(kind);
        if ("medecin".equals(table.name())) {
            jdbc.update("UPDATE medecin SET actif = FALSE WHERE id = ? AND center_id = ?", id, centerId.valeur());
        } else {
            jdbc.update("DELETE FROM " + table.name() + " WHERE id = ? AND center_id = ?", id, centerId.valeur());
        }
        cacheEvictor.evictAfterCommit(centerId, table.caches());
    }

    @Override
    public long countUsages(CentreId centerId, ReferentialKind kind, UUID id) {
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

