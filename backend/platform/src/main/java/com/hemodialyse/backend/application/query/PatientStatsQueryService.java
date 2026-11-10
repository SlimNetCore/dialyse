package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.StatistiquesMedicalesPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Statistiques du patient : présentation (JSON, CSV, PDF) seulement. Les mesures médicales viennent du langage publié du Dossier médical
 * (DM9, ADR 0101 : {@link StatistiquesMedicalesPubliees}, mêmes requêtes qu'avant, clés en MAJUSCULES du SGBD : KB-MED-103, ordre non défini
 * à date égale : KB-MED-109) ; les séances et le volet paramédical du langage publié de la Séance (SE7e, ADR 0099). Plus aucun SQL ici.
 */
@Service
public class PatientStatsQueryService {

    private final StatistiquesMedicalesPubliees medicales;
    private final com.hemodialyse.backend.infrastructure.reporting.DocumentIdentityProvider identity;
    /**
     * Séances du patient et mesures de leur volet paramédical (UF réelle = saisie, sinon perte de poids x 1000 ; tension en texte
     * « 130/80 » ; poids sec en vigueur de la prescription médicale) : langage publié de la Séance (SE7e, ADR 0099).
     */
    private final StatistiquesDesSeancesPubliees statistiques;

    public PatientStatsQueryService(StatistiquesMedicalesPubliees medicales, StatistiquesDesSeancesPubliees statistiques,
                                    com.hemodialyse.backend.infrastructure.reporting.DocumentIdentityProvider identity) {
        this.medicales = medicales;
        this.statistiques = statistiques;
        this.identity = identity;
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
        StatistiquesMedicalesPubliees.Moyennes moyennes = medicales.moyennes(centerId, patientId, from, to);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("avgHbGDl", moyennes.hemoglobine());
        out.put("avgKtV", moyennes.ktV());
        out.put("avgFerritineNgMl", moyennes.ferritine());
        out.put("hbTrend", medicales.tendanceDesBilans(centerId, patientId, from, to));
        out.put("epoTrend", medicales.tendanceDesPrescriptionsDEpo(centerId, patientId, from, to));
        return out;
    }

    public String exportCsv(UUID centerId, UUID patientId, LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = medicales.lignesDExport(centerId, patientId, from, to);

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
                .append("<div class='kpi'>Seances: ").append(safeCount(paramedical.get("seanceCount"))).append("</div>")
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

    /** Un nombre de séances s'affiche en entier (KB-SEA-85 c corrigé, ADR 0118) : « 6 » et non « 6.00 ». */
    private String safeCount(Object value) {
        if (value == null) {
            return "0";
        }
        if (value instanceof Number n) {
            return String.valueOf(n.longValue());
        }
        return Objects.toString(value, "0");
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
        return medicales.poidsSecCibleEnVigueur(centerId, patientId, at != null ? at : LocalDate.now()).orElse(null);
    }
}
