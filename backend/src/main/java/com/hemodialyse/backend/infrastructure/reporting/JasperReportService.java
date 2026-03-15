package com.hemodialyse.backend.infrastructure.reporting;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.poi.export.JRXlsExporter;
import net.sf.jasperreports.export.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;

/**
 * Service Jasper simplifié :
 *   1. Compile un fichier .jrxml (depuis un chemin disque)
 *   2. Remplit le rapport via une connexion JDBC + paramètres
 *   3. Exporte en PDF, Excel (XLS) ou HTML (PDF embarqué)
 */
@Service
public class JasperReportService {

    private static final Logger log = LoggerFactory.getLogger(JasperReportService.class);
    private final DataSource dataSource;

    public JasperReportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ──────────────────────────── Compile ────────────────────────────────

    /**
     * Compile un fichier .jrxml. Si le .jasper compilé existe déjà et est
     * plus récent que le .jrxml, on le réutilise directement.
     */
    public JasperReport compileReport(String jrxmlPath) throws JRException, IOException {
        Path source = Path.of(jrxmlPath);
        if (!Files.exists(source)) {
            throw new FileNotFoundException("Template introuvable : " + jrxmlPath);
        }

        // Check for pre-compiled .jasper next to .jrxml
        Path compiled = source.resolveSibling(
                source.getFileName().toString().replace(".jrxml", ".jasper"));

        if (Files.exists(compiled) &&
            Files.getLastModifiedTime(compiled).compareTo(Files.getLastModifiedTime(source)) >= 0) {
            log.debug("Chargement du rapport compilé : {}", compiled);
            return (JasperReport) JRLoader.loadObject(compiled.toFile());
        }

        log.info("Compilation du rapport : {}", jrxmlPath);
        JasperReport report = JasperCompileManager.compileReport(jrxmlPath);

        // Persist .jasper for next time
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(compiled.toFile()))) {
            oos.writeObject(report);
        }
        return report;
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
        return html.getBytes();
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
}

