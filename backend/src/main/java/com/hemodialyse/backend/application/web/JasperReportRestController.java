package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.infrastructure.reporting.JasperReportService;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * REST controller for advanced JasperReports-based reporting.
 *
 * Workflow:
 *  1. POST /api/v1/jasper/test-sql   → test SQL, get columns
 *  2. POST /api/v1/jasper/generate   → auto-build .jrxml + export PDF/Excel/HTML
 *  3. POST /api/v1/jasper/compile    → compile custom .jrxml and export
 */
@RestController
@RequestMapping("/api/v1/jasper")
public class JasperReportRestController {

    private final JasperReportService jasperService;
    private final JdbcTemplate jdbc;

    public JasperReportRestController(JasperReportService jasperService, JdbcTemplate jdbc) {
        this.jasperService = jasperService;
        this.jdbc = jdbc;
    }

    // ─── 1. Test SQL and get columns ────────────────────────────────────

    record SqlTestRequest(String sql, UUID centerId) {}
    record SqlTestResponse(List<String> columns, List<Map<String, Object>> sampleRows, int totalRows, String error) {}

    @PostMapping("/test-sql")
    public ResponseEntity<SqlTestResponse> testSql(@RequestBody SqlTestRequest req) {
        try {
            String sql = req.sql().replace(":centerId", "'" + req.centerId() + "'");
            if (!sql.trim().toUpperCase().startsWith("SELECT")) {
                return ResponseEntity.ok(new SqlTestResponse(List.of(), List.of(), 0, "Seules les requêtes SELECT sont autorisées"));
            }
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
            List<Map<String, Object>> sample = rows.size() > 5 ? rows.subList(0, 5) : rows;
            return ResponseEntity.ok(new SqlTestResponse(columns, sample, rows.size(), null));
        } catch (Exception e) {
            return ResponseEntity.ok(new SqlTestResponse(List.of(), List.of(), 0, e.getMessage()));
        }
    }

    // ─── 2. Generate report from SQL (auto-build .jrxml) ────────────────

    record GenerateRequest(
        String title,
        String sql,
        UUID centerId,
        String centerName,
        List<String> columns,
        List<String> headers,
        String format,       // PDF, EXCEL, HTML
        String pageFormat,   // A4, A5, LETTER
        boolean landscape,
        String headerImage,
        String footerImage
    ) {}

    @PostMapping(value = "/generate")
    public ResponseEntity<byte[]> generate(@RequestBody GenerateRequest req) {
        try {
            // 1) Build .jrxml dynamically
            String jrxml = jasperService.buildDynamicJrxml(
                req.title(), req.columns(), req.headers(),
                req.pageFormat(), req.landscape(),
                req.headerImage(), req.footerImage()
            );

            // 2) Compile
            JasperReport compiled = jasperService.compileFromXml(jrxml);

            // 3) Execute SQL and fill
            String sql = req.sql().replace(":centerId", "'" + req.centerId() + "'");
            List<Map<String, Object>> rows = jdbc.queryForList(sql);

            Map<String, Object> params = new HashMap<>();
            params.put("REPORT_TITLE", req.title() != null ? req.title() : "Rapport");
            params.put("CENTER_NAME", req.centerName() != null ? req.centerName() : "");
            params.put("GENERATED_DATE", LocalDate.now().toString());
            params.put("HEADER_IMAGE", req.headerImage() != null ? req.headerImage() : "");
            params.put("FOOTER_IMAGE", req.footerImage() != null ? req.footerImage() : "");

            JasperPrint print = jasperService.fillReportWithBeans(compiled, params, rows);

            // 4) Export
            String format = req.format() != null ? req.format().toUpperCase() : "PDF";
            return switch (format) {
                case "EXCEL", "XLSX" -> {
                    byte[] data = jasperService.exportToExcel(print);
                    HttpHeaders h = new HttpHeaders();
                    h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                    h.add("Content-Disposition", "attachment; filename=report.xlsx");
                    yield ResponseEntity.ok().headers(h).body(data);
                }
                case "HTML" -> {
                    String html = jasperService.exportToHtml(print);
                    yield ResponseEntity.ok()
                            .contentType(MediaType.TEXT_HTML)
                            .body(html.getBytes());
                }
                default -> {
                    byte[] data = jasperService.exportToPdf(print);
                    HttpHeaders h = new HttpHeaders();
                    h.setContentType(MediaType.APPLICATION_PDF);
                    h.add("Content-Disposition", "inline; filename=report.pdf");
                    yield ResponseEntity.ok().headers(h).body(data);
                }
            };
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Erreur: " + e.getMessage()).getBytes());
        }
    }

    // ─── 3. Compile custom .jrxml and export ────────────────────────────

    record CompileRequest(
        String jrxml,
        String sql,
        UUID centerId,
        Map<String, String> parameters,
        String format  // PDF, EXCEL, HTML
    ) {}

    @PostMapping(value = "/compile")
    public ResponseEntity<byte[]> compile(@RequestBody CompileRequest req) {
        try {
            JasperReport compiled = jasperService.compileFromXml(req.jrxml());

            Map<String, Object> params = new HashMap<>();
            if (req.parameters() != null) params.putAll(req.parameters());
            params.put("GENERATED_DATE", LocalDate.now().toString());

            JasperPrint print;
            if (req.sql() != null && !req.sql().isBlank()) {
                String sql = req.sql().replace(":centerId", "'" + req.centerId() + "'");
                List<Map<String, Object>> rows = jdbc.queryForList(sql);
                print = jasperService.fillReportWithBeans(compiled, params, rows);
            } else {
                print = jasperService.fillReportWithBeans(compiled, params, List.of());
            }

            String format = req.format() != null ? req.format().toUpperCase() : "PDF";
            return switch (format) {
                case "EXCEL", "XLSX" -> {
                    byte[] data = jasperService.exportToExcel(print);
                    HttpHeaders h = new HttpHeaders();
                    h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                    h.add("Content-Disposition", "attachment; filename=report.xlsx");
                    yield ResponseEntity.ok().headers(h).body(data);
                }
                case "HTML" -> {
                    String html = jasperService.exportToHtml(print);
                    yield ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html.getBytes());
                }
                default -> {
                    byte[] data = jasperService.exportToPdf(print);
                    HttpHeaders h = new HttpHeaders();
                    h.setContentType(MediaType.APPLICATION_PDF);
                    h.add("Content-Disposition", "inline; filename=report.pdf");
                    yield ResponseEntity.ok().headers(h).body(data);
                }
            };
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Erreur compilation: " + e.getMessage()).getBytes());
        }
    }

    // ─── 4. Get generated .jrxml for preview ────────────────────────────

    record PreviewJrxmlRequest(
        String title, List<String> columns, List<String> headers,
        String pageFormat, boolean landscape, String headerImage, String footerImage
    ) {}

    @PostMapping(value = "/preview-jrxml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> previewJrxml(@RequestBody PreviewJrxmlRequest req) {
        String jrxml = jasperService.buildDynamicJrxml(
            req.title(), req.columns(), req.headers(),
            req.pageFormat(), req.landscape(), req.headerImage(), req.footerImage()
        );
        return ResponseEntity.ok(jrxml);
    }
}

