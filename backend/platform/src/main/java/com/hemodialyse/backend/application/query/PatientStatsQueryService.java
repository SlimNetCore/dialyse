package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees;
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
    private final com.hemodialyse.backend.infrastructure.reporting.DocumentIdentityProvider identity;
    /**
     * Séances du patient et mesures de leur volet paramédical (UF réelle = saisie, sinon perte de poids x 1000 ; tension en texte
     * « 130/80 » ; poids sec en vigueur de la prescription médicale) : langage publié de la Séance (SE7e, ADR 0099).
     */
    private final StatistiquesDesSeancesPubliees statistiques;

    public PatientStatsQueryService(JdbcTemplate jdbc, StatistiquesDesSeancesPubliees statistiques) {
        this.jdbc = jdbc;
        this.statistiques = statistiques;
        this.identity = new com.hemodialyse.backend.infrastructure.reporting.DocumentIdentityProvider(jdbc);
    }

    private static Double zeroSiNul(Double value) {
        return value != null ? value : 0.0d;
    }

    private static final java.util.regex.Pattern TA_PATTERN =
            java.util.regex.Pattern.compile("^\\s*(\\d{2,3})\\s*[/\\-]\\s*(\\d{2,3})");

    /**
     * Extrait {systolique, diastolique} d'une TA texte (« 130/80 », « 130-80 »). Valeurs nulles si illisible.
     */
    static Integer[] parseTension(String raw) {
        if (raw == null) {
            return new Integer[]{null, null};
        }
        java.util.regex.Matcher m = TA_PATTERN.matcher(raw);
        if (!m.find()) {
            return new Integer[]{null, null};
        }
        return new Integer[]{Integer.valueOf(m.group(1)), Integer.valueOf(m.group(2))};
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
                "SELECT p.date_prescription, p.epo_dose_ui, p.epo_article_id, p.epo_voie, "
                        + "p.epo_frequence_valeur, p.epo_frequence_unite "
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

        String html = identity.decorateHtml(buildPdfHtml(centerId, patientId, from, to, paramedical, medical), centerId);
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

    /**
     * Statistiques paramédicales : les séances et leur volet sont lus par le langage publié de la Séance (SE7e, ADR 0099), mêmes requêtes
     * et même ordre qu'avant (tous statuts, volet joint sans centre : KB-SEA-85 ; ordre par date seule et libellés du SGBD : KB-SEA-86 ;
     * précision des moyennes du SGBD : KB-SEA-87). Moyenne absente rendue {@code 0.0} comme avant.
     */
    public Map<String, Object> getParamedicalStats(UUID centerId, UUID patientId, LocalDate from, LocalDate to) {
        Long seanceCount = statistiques.nombreDeSeances(centerId, patientId, from, to);
        Double avgPoidsAvant = zeroSiNul(statistiques.moyenneDuPoidsAvant(centerId, patientId, from, to));
        Double avgPoidsApres = zeroSiNul(statistiques.moyenneDuPoidsApres(centerId, patientId, from, to));
        Double avgUfReelle = zeroSiNul(statistiques.moyenneDeLUltrafiltration(centerId, patientId, from, to));

        List<Map<String, Object>> poidsEvolution = statistiques.evolutionDuPoids(centerId, patientId, from, to);

        List<Map<String, Object>> taEvolution = new ArrayList<>();
        for (var tensions : statistiques.evolutionDeLaTension(centerId, patientId, from, to)) {
            Integer[] avant = parseTension(tensions.tensionAvant());
            Integer[] apres = parseTension(tensions.tensionApres());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date_seance", tensions.date() == null ? null : java.sql.Date.valueOf(tensions.date()));
            row.put("ta_systolique_avant", avant[0]);
            row.put("ta_diastolique_avant", avant[1]);
            row.put("ta_systolique_apres", apres[0]);
            row.put("ta_diastolique_apres", apres[1]);
            taEvolution.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seanceCount", seanceCount);
        out.put("avgPoidsAvantKg", avgPoidsAvant);
        out.put("avgPoidsApresKg", avgPoidsApres);
        out.put("avgUfReelleMl", avgUfReelle);
        out.put("poidsSecCibleKg", currentPoidsSec(centerId, patientId, to));
        out.put("poidsEvolution", poidsEvolution);
        out.put("taEvolution", taEvolution);
        return out;
    }

    /**
     * Poids sec cible en vigueur à la fin de la période (ou aujourd'hui) : dernière prescription
     * portant un poids sec, datée au plus tard de {@code at}. Null si jamais prescrit.
     */
    private Double currentPoidsSec(UUID centerId, UUID patientId, LocalDate at) {
        List<Double> values = jdbc.queryForList(
                "SELECT pm.poids_sec_cible_kg FROM prescriptions_medicales pm "
                        + "WHERE pm.center_id = ? AND pm.patient_id = ? AND pm.poids_sec_cible_kg IS NOT NULL "
                        + "AND pm.date_prescription <= ? ORDER BY pm.date_prescription DESC, pm.updated_at DESC LIMIT 1",
                Double.class, centerId, patientId, at != null ? at : LocalDate.now());
        return values.isEmpty() ? null : values.get(0);
    }

    private Double queryDouble(String sql, UUID centerId, UUID patientId) {
        Double value = jdbc.queryForObject(sql, Double.class, centerId, patientId);
        return value != null ? value : 0.0d;
    }
}



