package com.hemodialyse.backend.application.query;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PatientStatsQueryService {

    private final JdbcTemplate jdbc;

    public PatientStatsQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> getParamedicalStats(UUID centerId, UUID patientId, LocalDate from, LocalDate to) {
        String dateFilter = buildSeanceDateFilter(from, to);

        String baseFrom = " FROM seances s LEFT JOIN volet_paramedical vp ON vp.seance_id = s.id "
                + "WHERE s.center_id = ? AND s.patient_id = ? " + dateFilter;

        Long seanceCount = queryLong("SELECT COUNT(1)" + baseFrom, centerId, patientId);
        Double avgPoidsAvant = queryDouble("SELECT AVG(vp.poids_avant_kg)" + baseFrom, centerId, patientId);
        Double avgPoidsApres = queryDouble("SELECT AVG(vp.poids_apres_kg)" + baseFrom, centerId, patientId);
        Double avgUfReelle = queryDouble("SELECT AVG(vp.uf_reelle_ml)" + baseFrom, centerId, patientId);

        List<Map<String, Object>> poidsEvolution = jdbc.queryForList(
                "SELECT s.date_seance, vp.poids_avant_kg, vp.poids_apres_kg, vp.poids_sec_cible_kg, vp.uf_reelle_ml "
                        + baseFrom.replace("SELECT COUNT(1)", "")
                        + " ORDER BY s.date_seance ASC",
                centerId, patientId
        );

        List<Map<String, Object>> taEvolution = jdbc.queryForList(
                "SELECT s.date_seance, vp.ta_systolique_avant, vp.ta_diastolique_avant, "
                        + "vp.ta_systolique_apres, vp.ta_diastolique_apres "
                        + baseFrom.replace("SELECT COUNT(1)", "")
                        + " ORDER BY s.date_seance ASC",
                centerId, patientId
        );

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seanceCount", seanceCount);
        out.put("avgPoidsAvantKg", avgPoidsAvant);
        out.put("avgPoidsApresKg", avgPoidsApres);
        out.put("avgUfReelleMl", avgUfReelle);
        out.put("poidsEvolution", poidsEvolution);
        out.put("taEvolution", taEvolution);
        return out;
    }

    public Map<String, Object> getMedicalStats(UUID centerId, UUID patientId, LocalDate from, LocalDate to) {
        String dateFilter = buildAnalyseDateFilter(from, to);

        String baseFrom = " FROM resultats_analyses ra WHERE ra.center_id = ? AND ra.patient_id = ? " + dateFilter;

        Double avgHb = queryDouble("SELECT AVG(ra.hb_g_dl)" + baseFrom, centerId, patientId);
        Double avgKtV = queryDouble("SELECT AVG(ra.kt_v_mensuel)" + baseFrom, centerId, patientId);
        Double avgFerritine = queryDouble("SELECT AVG(ra.ferritine_ng_ml)" + baseFrom, centerId, patientId);

        List<Map<String, Object>> hbTrend = jdbc.queryForList(
                "SELECT ra.date_prelevement, ra.hb_g_dl, ra.kt_v_mensuel, ra.ferritine_ng_ml "
                        + baseFrom.replace("SELECT AVG(ra.hb_g_dl)", "")
                        + " ORDER BY ra.date_prelevement ASC",
                centerId, patientId
        );

        List<Map<String, Object>> epoTrend = jdbc.queryForList(
                "SELECT p.date_prescription, p.epo_dose_ui, p.epo_molecule, p.epo_voie, p.epo_frequence "
                        + "FROM prescriptions_medicales p "
                        + "WHERE p.center_id = ? AND p.patient_id = ? " + buildPrescriptionDateFilter(from, to)
                        + " ORDER BY p.date_prescription ASC",
                centerId, patientId
        );

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("avgHbGDl", avgHb);
        out.put("avgKtV", avgKtV);
        out.put("avgFerritineNgMl", avgFerritine);
        out.put("hbTrend", hbTrend);
        out.put("epoTrend", epoTrend);
        return out;
    }

    public String exportCsv(UUID centerId, UUID patientId, LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ra.date_prelevement, ra.hb_g_dl, ra.kt_v_mensuel, ra.ferritine_ng_ml, ra.phosphore_mg_dl, ra.calcium_mg_dl "
                        + "FROM resultats_analyses ra "
                        + "WHERE ra.center_id = ? AND ra.patient_id = ? " + buildAnalyseDateFilter(from, to)
                        + " ORDER BY ra.date_prelevement ASC",
                centerId, patientId
        );

        String header = "date_prelevement,hb_g_dl,kt_v_mensuel,ferritine_ng_ml,phosphore_mg_dl,calcium_mg_dl";
        String body = rows.stream()
                .map(r -> String.join(",",
                        safeCsv(r.get("DATE_PRELEVEMENT")),
                        safeCsv(r.get("HB_G_DL")),
                        safeCsv(r.get("KT_V_MENSUEL")),
                        safeCsv(r.get("FERRITINE_NG_ML")),
                        safeCsv(r.get("PHOSPHORE_MG_DL")),
                        safeCsv(r.get("CALCIUM_MG_DL"))
                ))
                .collect(Collectors.joining("\n"));

        return body.isBlank() ? header + "\n" : header + "\n" + body + "\n";
    }

    public byte[] exportPdf(UUID centerId, UUID patientId, LocalDate from, LocalDate to) {
        Map<String, Object> paramedical = getParamedicalStats(centerId, patientId, from, to);
        Map<String, Object> medical = getMedicalStats(centerId, patientId, from, to);

        String html = buildPdfHtml(centerId, patientId, from, to, paramedical, medical);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de generer le PDF stats", e);
        }
    }

    private String buildPdfHtml(UUID centerId,
                                UUID patientId,
                                LocalDate from,
                                LocalDate to,
                                Map<String, Object> paramedical,
                                Map<String, Object> medical) {
        String fromLabel = from != null ? from.toString() : "-";
        String toLabel = to != null ? to.toString() : "-";
        String generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset='UTF-8'/><style>")
                .append("body{font-family:Arial,sans-serif;font-size:12px;color:#111;margin:24px;}")
                .append("h1{font-size:20px;margin:0 0 8px 0;}h2{font-size:16px;margin:20px 0 8px 0;}")
                .append(".muted{color:#666;font-size:11px;margin-bottom:12px;}")
                .append(".kpis{display:block;margin:8px 0 12px 0;}")
                .append(".kpi{margin:3px 0;}")
                .append("table{width:100%;border-collapse:collapse;margin-top:8px;}")
                .append("th,td{border:1px solid #ddd;padding:6px;text-align:left;}")
                .append("th{background:#f5f7fb;}")
                .append("</style></head><body>");

        sb.append("<h1>Rapport statistiques patient</h1>")
                .append("<div class='muted'>Centre: ").append(escapeHtml(centerId.toString()))
                .append(" | Patient: ").append(escapeHtml(patientId.toString()))
                .append(" | Periode: ").append(escapeHtml(fromLabel)).append(" -> ").append(escapeHtml(toLabel))
                .append(" | Genere le: ").append(escapeHtml(generatedAt))
                .append("</div>");

        sb.append("<h2>Statistiques paramedicales</h2>")
                .append("<div class='kpis'>")
                .append("<div class='kpi'>Seances: ").append(safeNumber(paramedical.get("seanceCount"))).append("</div>")
                .append("<div class='kpi'>Poids avant moyen (kg): ").append(safeNumber(paramedical.get("avgPoidsAvantKg"))).append("</div>")
                .append("<div class='kpi'>Poids apres moyen (kg): ").append(safeNumber(paramedical.get("avgPoidsApresKg"))).append("</div>")
                .append("<div class='kpi'>UF reelle moyenne (mL): ").append(safeNumber(paramedical.get("avgUfReelleMl"))).append("</div>")
                .append("</div>");

        appendParamedicalTable(sb, castRows(paramedical.get("poidsEvolution")));

        sb.append("<h2>Statistiques medicales</h2>")
                .append("<div class='kpis'>")
                .append("<div class='kpi'>Hb moyenne (g/dL): ").append(safeNumber(medical.get("avgHbGDl"))).append("</div>")
                .append("<div class='kpi'>Kt/V moyen: ").append(safeNumber(medical.get("avgKtV"))).append("</div>")
                .append("<div class='kpi'>Ferritine moyenne (ng/mL): ").append(safeNumber(medical.get("avgFerritineNgMl"))).append("</div>")
                .append("</div>");

        appendMedicalTable(sb, castRows(medical.get("hbTrend")));

        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendParamedicalTable(StringBuilder sb, List<Map<String, Object>> rows) {
        sb.append("<table><thead><tr><th>Date seance</th><th>Poids avant</th><th>Poids apres</th><th>Poids sec cible</th></tr></thead><tbody>");
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='4'>Aucune donnee</td></tr>");
        } else {
            for (Map<String, Object> row : rows) {
                sb.append("<tr><td>").append(escapeHtml(safeCell(row, "date_seance", "DATE_SEANCE")))
                        .append("</td><td>").append(escapeHtml(safeCell(row, "poids_avant_kg", "POIDS_AVANT_KG")))
                        .append("</td><td>").append(escapeHtml(safeCell(row, "poids_apres_kg", "POIDS_APRES_KG")))
                        .append("</td><td>").append(escapeHtml(safeCell(row, "poids_sec_cible_kg", "POIDS_SEC_CIBLE_KG")))
                        .append("</td></tr>");
            }
        }
        sb.append("</tbody></table>");
    }

    private void appendMedicalTable(StringBuilder sb, List<Map<String, Object>> rows) {
        sb.append("<table><thead><tr><th>Date prelevement</th><th>Hb</th><th>Kt/V</th><th>Ferritine</th></tr></thead><tbody>");
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='4'>Aucune donnee</td></tr>");
        } else {
            for (Map<String, Object> row : rows) {
                sb.append("<tr><td>").append(escapeHtml(safeCell(row, "date_prelevement", "DATE_PRELEVEMENT")))
                        .append("</td><td>").append(escapeHtml(safeCell(row, "hb_g_dl", "HB_G_DL")))
                        .append("</td><td>").append(escapeHtml(safeCell(row, "kt_v_mensuel", "KT_V_MENSUEL")))
                        .append("</td><td>").append(escapeHtml(safeCell(row, "ferritine_ng_ml", "FERRITINE_NG_ML")))
                        .append("</td></tr>");
            }
        }
        sb.append("</tbody></table>");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castRows(Object value) {
        if (value instanceof List<?> rows) {
            return rows.stream()
                    .filter(Map.class::isInstance)
                    .map(r -> (Map<String, Object>) r)
                    .toList();
        }
        return List.of();
    }

    private String safeCell(Map<String, Object> row, String keyA, String keyB) {
        Object value = row.get(keyA);
        if (value == null) {
            value = row.get(keyB);
        }
        return value == null ? "" : String.valueOf(value);
    }

    private String safeNumber(Object value) {
        if (value == null) {
            return "0";
        }
        if (value instanceof Number n) {
            return String.format(java.util.Locale.ROOT, "%.2f", n.doubleValue());
        }
        return Objects.toString(value, "0");
    }

    private String escapeHtml(String raw) {
        if (raw == null) return "";
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String safeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String raw = String.valueOf(value);
        String escaped = raw.replace("\"", "\"\"");
        return '"' + escaped + '"';
    }

    private String buildSeanceDateFilter(LocalDate from, LocalDate to) {
        if (from != null && to != null) return " AND s.date_seance BETWEEN '" + from + "' AND '" + to + "'";
        if (from != null) return " AND s.date_seance >= '" + from + "'";
        if (to != null) return " AND s.date_seance <= '" + to + "'";
        return "";
    }

    private String buildAnalyseDateFilter(LocalDate from, LocalDate to) {
        if (from != null && to != null) return " AND ra.date_prelevement BETWEEN '" + from + "' AND '" + to + "'";
        if (from != null) return " AND ra.date_prelevement >= '" + from + "'";
        if (to != null) return " AND ra.date_prelevement <= '" + to + "'";
        return "";
    }

    private String buildPrescriptionDateFilter(LocalDate from, LocalDate to) {
        if (from != null && to != null) return " AND p.date_prescription BETWEEN '" + from + "' AND '" + to + "'";
        if (from != null) return " AND p.date_prescription >= '" + from + "'";
        if (to != null) return " AND p.date_prescription <= '" + to + "'";
        return "";
    }

    private Long queryLong(String sql, UUID centerId, UUID patientId) {
        Long value = jdbc.queryForObject(sql, Long.class, centerId, patientId);
        return value != null ? value : 0L;
    }

    private Double queryDouble(String sql, UUID centerId, UUID patientId) {
        Double value = jdbc.queryForObject(sql, Double.class, centerId, patientId);
        return value != null ? value : 0.0d;
    }
}



