package com.hemodialyse.backend.infrastructure.reporting;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.export.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Service Jasper simplifié :
 *   1. Compile un fichier .jrxml (depuis un chemin disque ou classpath)
 *   2. Remplit le rapport via une connexion JDBC + paramètres
 *   3. Exporte en PDF, Excel (XLS) ou HTML (PDF embarqué)
 *
 *  Résolution du chemin (par ordre de priorité) :
 *   1. Chemin absolu  (si fourni en absolu et le fichier existe)
 *   2. app.reports.base-dir + chemin  (propriété configurable)
 *   3. user.dir/reports/ + nom du fichier
 *   4. user.dir/backend/reports/ + nom du fichier
 *   5. classpath:/reports/ + nom du fichier
 */
@Service
public class JasperReportService {

    private static final Logger log = LoggerFactory.getLogger(JasperReportService.class);
    private final DataSource dataSource;

    /** Configurable via REPORTS_DIR env var or app.reports.base-dir property */
    @Value("${app.reports.base-dir:}")
    private String reportsBaseDir;

    public JasperReportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ──────────────────────────── Compile ────────────────────────────────

    /**
     * Compile un fichier .jrxml. Si le .jasper compilé existe déjà et est
     * plus récent que le .jrxml, on le réutilise directement.
     */
    public JasperReport compileReport(String jrxmlPath) throws JRException, IOException {
        Path source = resolveTemplatePath(jrxmlPath);
        if (source != null && Files.exists(source)) {
            // Check for pre-compiled .jasper next to .jrxml
            Path compiled = source.resolveSibling(
                    source.getFileName().toString().replace(".jrxml", ".jasper"));

            if (Files.exists(compiled) &&
                Files.getLastModifiedTime(compiled).compareTo(Files.getLastModifiedTime(source)) >= 0) {
                try {
                    log.debug("Chargement du rapport compilé : {}", compiled);
                    return (JasperReport) JRLoader.loadObject(compiled.toFile());
                } catch (Exception ex) {
                    // Fallback: recompilation si le cache est invalide/corrompu
                    log.warn(".jasper invalide, recompilation forcée: {} ({})", compiled, ex.getMessage());
                }
            }

            log.info("Compilation du rapport (filesystem) : {}", source.toAbsolutePath());
            JasperReport report = JasperCompileManager.compileReport(source.toString());

            // Persist .jasper for next time (best effort)
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(compiled.toFile()))) {
                oos.writeObject(report);
            } catch (Exception ex) {
                log.warn("Impossible d'écrire le cache .jasper: {} ({})", compiled, ex.getMessage());
            }
            return report;
        }

        // Fallback classpath (compatible jar/boot fat-jar): compile via InputStream
        ClassPathResource cpr = resolveClasspathResource(jrxmlPath);
        if (cpr != null && cpr.exists()) {
            log.info("Compilation du rapport (classpath) : {}", cpr.getPath());
            try (InputStream is = cpr.getInputStream()) {
                return JasperCompileManager.compileReport(is);
            }
        }

        throw new FileNotFoundException("Template introuvable : " + jrxmlPath);
    }

    // ──────────────────────────── Fill ───────────────────────────────────

    /**
     * Remplit le rapport avec les paramètres et une connexion JDBC.
     * La requête SQL est définie DANS le fichier .jrxml lui-même.
     */
    public JasperPrint fillReport(JasperReport report, Map<String, Object> params) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            return JasperFillManager.fillReport(report, params, conn);
        }
    }

    // ──────────────────────────── Export ─────────────────────────────────

    public byte[] exportToPdf(JasperPrint print) throws JRException {
        return JasperExportManager.exportReportToPdf(print);
    }

    public byte[] exportToExcel(JasperPrint print) throws JRException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JRXlsExporter exporter = new JRXlsExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos));
        SimpleXlsReportConfiguration config = new SimpleXlsReportConfiguration();
        config.setOnePagePerSheet(false);
        config.setDetectCellType(true);
        config.setWhitePageBackground(false);
        exporter.setConfiguration(config);
        exporter.exportReport();
        return baos.toByteArray();
    }

    public byte[] exportToHtml(JasperPrint print) throws JRException {
        // JasperReports 7 a supprimé l'export HTML du core.
        // On génère un PDF embarqué dans une page HTML.
        byte[] pdf = exportToPdf(print);
        String base64 = java.util.Base64.getEncoder().encodeToString(pdf);
        String html = "<html><head><meta charset='UTF-8'><title>Report</title></head>"
                + "<body style='margin:0'>"
                + "<iframe src='data:application/pdf;base64," + base64
                + "' width='100%' height='100%' style='border:none;min-height:95vh'></iframe>"
                + "</body></html>";
        return html.getBytes(StandardCharsets.UTF_8);
    }

    // ──────────────────────────── Raccourci tout-en-un ───────────────────

    /**
     * Compile + remplit + exporte en une seule opération.
     *
     * @param jrxmlPath chemin du fichier .jrxml
     * @param params    paramètres Jasper (centerId, patientId, etc.)
     * @param format    PDF, EXCEL ou HTML
     * @return les bytes du document généré
     */
    public byte[] generateReport(String jrxmlPath, Map<String, Object> params, String format)
            throws Exception {

        JasperReport report = compileReport(jrxmlPath);
        JasperPrint print = fillReport(report, params);

        return switch (format.toUpperCase()) {
            case "EXCEL", "XLS", "XLSX" -> exportToExcel(print);
            case "HTML"                 -> exportToHtml(print);
            default                     -> exportToPdf(print);
        };
    }

    /**
     * Resolve a jrxml path from multiple locations:
     * 1) absolute path
     * 2) reportsBaseDir (app.reports.base-dir) + relative path
     * 3) user.dir/reports/<filename>
     * 4) user.dir/<path>
     * 5) user.dir/backend/<path>
     * 6) classpath (géré séparément via InputStream)
     */
    private Path resolveTemplatePath(String jrxmlPath) throws IOException {
        if (jrxmlPath == null || jrxmlPath.isBlank()) return null;

        String filename = Path.of(jrxmlPath).getFileName().toString();

        // 1) absolute
        Path raw = Path.of(jrxmlPath);
        if (raw.isAbsolute() && Files.exists(raw)) {
            log.debug("jrxml found (absolute): {}", raw);
            return raw;
        }

        // 2) configured base dir
        if (reportsBaseDir != null && !reportsBaseDir.isBlank()) {
            Path p = Path.of(reportsBaseDir, jrxmlPath).normalize();
            if (Files.exists(p)) { log.debug("jrxml found (base-dir): {}", p); return p; }
            Path p2 = Path.of(reportsBaseDir, filename).normalize();
            if (Files.exists(p2)) { log.debug("jrxml found (base-dir+filename): {}", p2); return p2; }
        }

        String userDir = System.getProperty("user.dir");

        // 3) user.dir/reports/<filename>
        Path p3 = Path.of(userDir, "reports", filename).normalize();
        if (Files.exists(p3)) { log.debug("jrxml found (user.dir/reports): {}", p3); return p3; }

        // 4) user.dir/<path>
        Path p4 = Path.of(userDir, jrxmlPath).normalize();
        if (Files.exists(p4)) { log.debug("jrxml found (user.dir/path): {}", p4); return p4; }

        // 5) user.dir/backend/<path>
        Path p5 = Path.of(userDir, "backend", jrxmlPath).normalize();
        if (Files.exists(p5)) { log.debug("jrxml found (user.dir/backend): {}", p5); return p5; }

        log.warn("jrxml NOT found anywhere for path='{}', filename='{}', user.dir='{}'",
                jrxmlPath, filename, userDir);
        return null;
    }

    private ClassPathResource resolveClasspathResource(String jrxmlPath) {
        if (jrxmlPath == null || jrxmlPath.isBlank()) return null;

        String cleaned = jrxmlPath.replace("\\", "/");
        if (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        String filename = Path.of(cleaned).getFileName().toString();

        ClassPathResource direct = new ClassPathResource(cleaned);
        if (direct.exists()) return direct;

        ClassPathResource underReports = new ClassPathResource("reports/" + filename);
        if (underReports.exists()) return underReports;

        return null;
    }
}
