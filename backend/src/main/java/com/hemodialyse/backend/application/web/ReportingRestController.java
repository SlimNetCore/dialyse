package com.hemodialyse.backend.application.web;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/reporting")
public class ReportingRestController {

    private final JdbcTemplate jdbc;

    public ReportingRestController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record TemplateDto(
        UUID id,
        UUID centerId,
        String code,
        String name,
        String reportType,
        String pageFormat,
        String orientation,
        String layoutMode,
        String fieldSchema,
        String templateHtml,
        String dataSourceSql,
        String headerImage,
        String footerImage,
        boolean active
    ) {}

    @GetMapping("/templates")
    public ResponseEntity<?> listTemplates(@RequestParam UUID centerId, @RequestParam(required = false) String reportType) {
        String sql = "SELECT id, center_id, code, name, report_type, page_format, orientation, layout_mode, field_schema, template_html, data_source_sql, header_image, footer_image, active " +
                "FROM report_template WHERE center_id = ? " + (reportType != null ? "AND report_type = ? " : "") + "ORDER BY name";

        var rows = reportType != null
                ? jdbc.queryForList(sql, centerId, reportType)
                : jdbc.queryForList(sql, centerId);
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/templates")
    public ResponseEntity<?> createTemplate(@RequestBody TemplateDto req) {
        UUID id = req.id() != null ? req.id() : UUID.randomUUID();
        jdbc.update("INSERT INTO report_template (id, center_id, code, name, report_type, page_format, orientation, layout_mode, field_schema, template_html, data_source_sql, header_image, footer_image, active) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, req.centerId(), req.code(), req.name(), req.reportType(), req.pageFormat(), req.orientation(), req.layoutMode(), req.fieldSchema(), req.templateHtml(), req.dataSourceSql(), req.headerImage(), req.footerImage(), req.active());
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<?> updateTemplate(@PathVariable UUID id, @RequestBody TemplateDto req) {
        jdbc.update("UPDATE report_template SET code=?, name=?, report_type=?, page_format=?, orientation=?, layout_mode=?, field_schema=?, template_html=?, data_source_sql=?, header_image=?, footer_image=?, active=? WHERE id=? AND center_id=?",
                req.code(), req.name(), req.reportType(), req.pageFormat(), req.orientation(), req.layoutMode(), req.fieldSchema(), req.templateHtml(), req.dataSourceSql(), req.headerImage(), req.footerImage(), req.active(), id, req.centerId());
        return ResponseEntity.ok(Map.of("id", id));
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<?> deleteTemplate(@PathVariable UUID id, @RequestParam UUID centerId) {
        jdbc.update("DELETE FROM report_template WHERE id = ? AND center_id = ?", id, centerId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping(value = "/render/{reportType}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderReport(@PathVariable String reportType,
                                               @RequestParam UUID centerId,
                                               @RequestParam UUID patientId) {
        Map<String, Object> tpl = jdbc.queryForMap(
                "SELECT template_html, data_source_sql, header_image, footer_image FROM report_template WHERE center_id = ? AND report_type = ? AND active = TRUE ORDER BY created_at DESC LIMIT 1",
                centerId, reportType
        );
        String template = Objects.toString(tpl.get("TEMPLATE_HTML"), Objects.toString(tpl.get("template_html"), ""));
        String dataSourceSql = Objects.toString(tpl.get("DATA_SOURCE_SQL"), Objects.toString(tpl.get("data_source_sql"), ""));
        String headerImage = Objects.toString(tpl.get("HEADER_IMAGE"), Objects.toString(tpl.get("header_image"), ""));
        String footerImage = Objects.toString(tpl.get("FOOTER_IMAGE"), Objects.toString(tpl.get("footer_image"), ""));

        Map<String, String> data = loadData(centerId, patientId, reportType);
        List<Map<String, Object>> dataRows = List.of();
        if (dataSourceSql != null && !dataSourceSql.isBlank()) {
            var result = executeTemplateDataSourceFull(dataSourceSql, centerId, patientId);
            data.putAll(result.singleRow());
            dataRows = result.rows();
        }
        String html = applyTemplate(template, data, dataRows);
        html = wrapWithHeaderFooter(html, headerImage, footerImage);
        return ResponseEntity.ok(html);
    }

    @GetMapping(value = "/render/template/{templateId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderReportByTemplate(@PathVariable UUID templateId,
                                                         @RequestParam UUID centerId,
                                                         @RequestParam UUID patientId) {
        String html = buildRenderedHtml(templateId, centerId, patientId);
        return ResponseEntity.ok(html);
    }

    @GetMapping("/models")
    public ResponseEntity<?> availableModels() {
        return ResponseEntity.ok(List.of(
                Map.of("code", "ATTESTATION", "label", "Attestation ouverture de droit"),
                Map.of("code", "PEC", "label", "Prise en charge"),
                Map.of("code", "FICHE_SIGNALETIQUE", "label", "Fiche signaletique"),
                Map.of("code", "CUSTOM", "label", "Rapport personnalisé")
        ));
    }

    /** List available tables/views that can be used in SQL data sources */
    @GetMapping("/datasource/tables")
    public ResponseEntity<?> availableTables() {
        return ResponseEntity.ok(List.of(
            Map.of("table", "patient", "description", "Patients"),
            Map.of("table", "attestation", "description", "Attestations d'ouverture de droit"),
            Map.of("table", "pec", "description", "Prises en charge"),
            Map.of("table", "centre_payeur", "description", "Centres payeurs"),
            Map.of("table", "agence", "description", "Agences"),
            Map.of("table", "caisse_assurance", "description", "Caisses d'assurance"),
            Map.of("table", "salle", "description", "Salles de dialyse"),
            Map.of("table", "medecin", "description", "Médecins"),
            Map.of("table", "transporteur", "description", "Transporteurs"),
            Map.of("table", "forfait", "description", "Forfaits"),
            Map.of("table", "centers", "description", "Centres de dialyse"),
            Map.of("table", "app_user", "description", "Utilisateurs")
        ));
    }

    /** Export PDF from a specific template */
    @GetMapping(value = "/export/pdf/{templateId}", produces = "application/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID templateId,
                                            @RequestParam UUID centerId,
                                            @RequestParam UUID patientId) {
        String html = buildRenderedHtml(templateId, centerId, patientId);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add("Content-Disposition", "inline; filename=report.pdf");
            return ResponseEntity.ok().headers(headers).body(os.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String buildRenderedHtml(UUID templateId, UUID centerId, UUID patientId) {
        Map<String, Object> tpl = jdbc.queryForMap(
                "SELECT report_type, template_html, data_source_sql, header_image, footer_image FROM report_template WHERE id = ? AND center_id = ?",
                templateId, centerId
        );
        String reportType = Objects.toString(tpl.get("REPORT_TYPE"), Objects.toString(tpl.get("report_type"), ""));
        String template = Objects.toString(tpl.get("TEMPLATE_HTML"), Objects.toString(tpl.get("template_html"), ""));
        String dataSourceSql = Objects.toString(tpl.get("DATA_SOURCE_SQL"), Objects.toString(tpl.get("data_source_sql"), ""));
        String headerImage = Objects.toString(tpl.get("HEADER_IMAGE"), Objects.toString(tpl.get("header_image"), ""));
        String footerImage = Objects.toString(tpl.get("FOOTER_IMAGE"), Objects.toString(tpl.get("footer_image"), ""));

        Map<String, String> data = loadData(centerId, patientId, reportType);
        List<Map<String, Object>> dataRows = List.of();
        if (dataSourceSql != null && !dataSourceSql.isBlank()) {
            var result = executeTemplateDataSourceFull(dataSourceSql, centerId, patientId);
            data.putAll(result.singleRow);
            dataRows = result.rows;
        }
        String html = applyTemplate(template, data, dataRows);
        return wrapWithHeaderFooter(html, headerImage, footerImage);
    }

    private Map<String, String> loadData(UUID centerId, UUID patientId, String reportType) {
        Map<String, String> data = new HashMap<>();

        var p = jdbc.queryForMap("SELECT nom, prenom, sexe, date_naissance, adresse, tel_mobile, numero_assurance FROM patient WHERE id = ? AND center_id = ?",
                patientId, centerId);
        data.put("patient.nom", Objects.toString(p.get("nom"), ""));
        data.put("patient.prenom", Objects.toString(p.get("prenom"), ""));
        data.put("patient.sexe", Objects.toString(p.get("sexe"), ""));
        data.put("patient.dateNaissance", Objects.toString(p.get("date_naissance"), ""));
        data.put("patient.adresse", Objects.toString(p.get("adresse"), ""));
        data.put("patient.telMobile", Objects.toString(p.get("tel_mobile"), ""));
        data.put("patient.numeroAssurance", Objects.toString(p.get("numero_assurance"), ""));

        String centerName = jdbc.queryForObject("SELECT name FROM centers WHERE id = ?", String.class, centerId);
        data.put("center.name", centerName != null ? centerName : "");

        if ("ATTESTATION".equals(reportType)) {
            List<Map<String, Object>> a = jdbc.queryForList("SELECT date_debut, date_fin FROM attestation WHERE patient_id = ? AND center_id = ? ORDER BY date_fin DESC", patientId, centerId);
            if (!a.isEmpty()) {
                data.put("attestation.dateDebut", Objects.toString(a.get(0).get("date_debut"), ""));
                data.put("attestation.dateFin", Objects.toString(a.get(0).get("date_fin"), ""));
            }
        }

        if ("PEC".equals(reportType)) {
            List<Map<String, Object>> pec = jdbc.queryForList("SELECT date_debut_demande, date_fin_demande, statut FROM pec WHERE patient_id = ? AND center_id = ? ORDER BY date_fin_demande DESC", patientId, centerId);
            if (!pec.isEmpty()) {
                data.put("pec.dateDebutDemande", Objects.toString(pec.get(0).get("date_debut_demande"), ""));
                data.put("pec.dateFinDemande", Objects.toString(pec.get(0).get("date_fin_demande"), ""));
                data.put("pec.statut", Objects.toString(pec.get(0).get("statut"), ""));
            }
        }

        return data;
    }

    private String applyTemplate(String template, Map<String, String> data) {
        return applyTemplate(template, data, List.of());
    }

    private String applyTemplate(String template, Map<String, String> data, List<Map<String, Object>> dataRows) {
        String html = template;

        // Process {{#each data.rows}} ... {{/each}} repeatable sections
        Pattern eachPattern = Pattern.compile("\\{\\{#each data\\.rows\\}\\}(.*?)\\{\\{/each\\}\\}", Pattern.DOTALL);
        Matcher m = eachPattern.matcher(html);
        while (m.find()) {
            String rowTemplate = m.group(1);
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> row : dataRows) {
                String rowHtml = rowTemplate;
                for (var e : row.entrySet()) {
                    String key = e.getKey();
                    String val = Objects.toString(e.getValue(), "");
                    rowHtml = rowHtml.replace("{{row." + key + "}}", val);
                    // Also support lowercase
                    rowHtml = rowHtml.replace("{{row." + key.toLowerCase() + "}}", val);
                }
                sb.append(rowHtml);
            }
            html = html.replace(m.group(0), sb.toString());
        }

        // Simple token replacement
        for (var e : data.entrySet()) {
            html = html.replace("{{" + e.getKey() + "}}", e.getValue() != null ? e.getValue() : "");
        }
        html = html.replace("{{generatedAt}}", LocalDate.now().toString());
        return html;
    }

    record DataSourceResult(Map<String, String> singleRow, List<Map<String, Object>> rows) {}

    private DataSourceResult executeTemplateDataSourceFull(String sqlTemplate, UUID centerId, UUID patientId) {
        String sql = sqlTemplate
                .replace(":centerId", "'" + centerId + "'")
                .replace(":patientId", "'" + patientId + "'");

        Map<String, String> singleRow = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        if (!rows.isEmpty()) {
            Map<String, Object> first = rows.get(0);
            for (var e : first.entrySet()) {
                singleRow.put("data." + e.getKey(), Objects.toString(e.getValue(), ""));
            }
        }
        singleRow.put("data.rowCount", String.valueOf(rows.size()));
        return new DataSourceResult(singleRow, rows);
    }

    private Map<String, String> executeTemplateDataSource(String sqlTemplate, UUID centerId, UUID patientId) {
        return executeTemplateDataSourceFull(sqlTemplate, centerId, patientId).singleRow();
    }

    private String wrapWithHeaderFooter(String bodyHtml, String headerImage, String footerImage) {
        String header = (headerImage != null && !headerImage.isBlank())
                ? "<div style='text-align:center;margin-bottom:14px'><img src='" + headerImage + "' style='max-width:100%;max-height:110px' /></div>"
                : "";
        String footer = (footerImage != null && !footerImage.isBlank())
                ? "<div style='text-align:center;margin-top:16px'><img src='" + footerImage + "' style='max-width:100%;max-height:90px' /></div>"
                : "";

        return "<html><head><meta charset='UTF-8'><style>@page{size:A4;margin:16mm}body{font-family:Arial,sans-serif;color:#1f2937}</style></head><body>"
                + header + bodyHtml + footer + "</body></html>";
    }
}




