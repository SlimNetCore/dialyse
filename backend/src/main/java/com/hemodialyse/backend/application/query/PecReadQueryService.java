package com.hemodialyse.backend.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application read-model service used by controllers.
 * Keeps SQL/JdbcTemplate out of web layer.
 */
@Service
public class PecReadQueryService {

    private static void addLike(StringBuilder where, List<Object> params, String field, String value) {
        if (value == null || value.isBlank()) return;
        where.append(" AND LOWER(").append(field).append(") LIKE ? ");
        params.add("%" + value.toLowerCase() + "%");
    }

    private final JdbcTemplate jdbc;

    public PecReadQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listAttestationsByCenter(UUID centerId) {
        return jdbc.queryForList(
            "SELECT a.id, a.patient_id, a.date_debut, a.date_fin, p.code_patient, p.nom, p.prenom, p.numero_assurance " +
            "FROM attestation_droit a " +
            "JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id " +
            "WHERE a.center_id = ? ORDER BY a.date_fin DESC",
            centerId
        );
    }

    public List<Map<String, Object>> listPecByCenterDetailed(UUID centerId) {
        return jdbc.queryForList(
            "SELECT pc.id, pc.patient_id, pc.date_debut_demande, pc.date_fin_demande, pc.statut, " +
            "pc.date_debut_effectif, pc.date_fin_effectif, p.code_patient, p.nom, p.prenom, p.numero_assurance " +
            "FROM prise_en_charge pc " +
            "JOIN patients p ON p.id = pc.patient_id AND p.center_id = pc.center_id " +
            "WHERE pc.center_id = ? ORDER BY pc.date_fin_demande DESC",
            centerId
        );
    }

    private static void addGlobalSearch(StringBuilder where, List<Object> params, String search, String... fields) {
        if (search == null || search.isBlank()) return;
        String s = "%" + search.toLowerCase() + "%";
        where.append(" AND (");
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) where.append(" OR ");
            where.append(fields[i]).append(" LIKE ? ");
            params.add(s);
        }
        where.append(") ");
    }

    private static void addDateFilter(StringBuilder where, List<Object> params, String field, String value) {
        if (value == null || value.isBlank()) return;
        DateRange parsed = parseFlexibleDateRange(value);
        if (parsed == null) {
            where.append(" AND 1 = 0 ");
            return;
        }
        if (parsed.from() != null) {
            where.append(" AND ").append(field).append(" >= ? ");
            params.add(parsed.from());
        }
        if (parsed.to() != null) {
            where.append(" AND ").append(field).append(" <= ? ");
            params.add(parsed.to());
        }
    }

    private static LocalDate parseFlexibleDate(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isBlank()) return null;
        if (raw.contains("T")) raw = raw.substring(0, raw.indexOf('T'));
        if (raw.contains(" ")) raw = raw.substring(0, raw.indexOf(' '));
        try {
            return LocalDate.parse(raw);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static DateRange parseFlexibleDateRange(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isBlank()) return null;

        if (!raw.contains("..")) {
            LocalDate exact = parseFlexibleDate(raw);
            return exact == null ? null : new DateRange(exact, exact);
        }

        String[] parts = raw.split("\\.\\.", 2);
        String fromRaw = parts.length > 0 ? parts[0].trim() : "";
        String toRaw = parts.length > 1 ? parts[1].trim() : "";

        LocalDate from = fromRaw.isBlank() ? null : parseFlexibleDate(fromRaw);
        LocalDate to = toRaw.isBlank() ? null : parseFlexibleDate(toRaw);

        if ((!fromRaw.isBlank() && from == null) || (!toRaw.isBlank() && to == null)) {
            return null;
        }

        if (from != null && to != null && from.isAfter(to)) {
            return new DateRange(to, from);
        }

        return new DateRange(from, to);
    }

    public PageResult listAttestationsByCenterPaged(UUID centerId, int page, int size, String search,
                                                    String code, String nom, String assurance,
                                                    String debut, String fin) {
        String from =
                " FROM attestation_droit a " +
                        " JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id ";

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE a.center_id = ? ");
        params.add(centerId);

        addGlobalSearch(where, params, search,
                "LOWER(p.code_patient)", "LOWER(p.nom)", "LOWER(p.prenom)", "LOWER(p.numero_assurance)");
        addLike(where, params, "p.code_patient", code);
        addLike(where, params, "CONCAT(COALESCE(p.nom,''), ' ', COALESCE(p.prenom,''))", nom);
        addLike(where, params, "p.numero_assurance", assurance);
        addDateFilter(where, params, "a.date_debut", debut);
        addDateFilter(where, params, "a.date_fin", fin);

        long total = jdbc.queryForObject("SELECT COUNT(1)" + from + where, Long.class, params.toArray());

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add(page * size);
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT a.id, a.patient_id, a.date_debut, a.date_fin, p.code_patient, p.nom, p.prenom, p.numero_assurance " +
                        from + where + " ORDER BY a.date_fin DESC LIMIT ? OFFSET ?",
                dataParams.toArray()
        );

        return new PageResult(items, total, page, size);
    }

    public PageResult listPecByCenterDetailedPaged(UUID centerId, int page, int size, String search,
                                                   String code, String nom, String assurance,
                                                   String debut, String fin, String statut) {
        String from =
                " FROM prise_en_charge pc " +
                        " JOIN patients p ON p.id = pc.patient_id AND p.center_id = pc.center_id ";

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE pc.center_id = ? ");
        params.add(centerId);

        addGlobalSearch(where, params, search,
                "LOWER(p.code_patient)", "LOWER(p.nom)", "LOWER(p.prenom)", "LOWER(p.numero_assurance)", "LOWER(pc.statut)");
        addLike(where, params, "p.code_patient", code);
        addLike(where, params, "CONCAT(COALESCE(p.nom,''), ' ', COALESCE(p.prenom,''))", nom);
        addLike(where, params, "p.numero_assurance", assurance);
        addLike(where, params, "pc.statut", statut);
        addDateFilter(where, params, "pc.date_debut_demande", debut);
        addDateFilter(where, params, "pc.date_fin_demande", fin);

        long total = jdbc.queryForObject("SELECT COUNT(1)" + from + where, Long.class, params.toArray());

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add(page * size);
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT pc.id, pc.patient_id, pc.date_debut_demande, pc.date_fin_demande, pc.statut, " +
                        "pc.date_debut_effectif, pc.date_fin_effectif, p.code_patient, p.nom, p.prenom, p.numero_assurance " +
                        from + where + " ORDER BY pc.date_fin_demande DESC LIMIT ? OFFSET ?",
                dataParams.toArray()
        );

        return new PageResult(items, total, page, size);
    }

    public record PageResult(List<Map<String, Object>> items, long total, int page, int size) {
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}

