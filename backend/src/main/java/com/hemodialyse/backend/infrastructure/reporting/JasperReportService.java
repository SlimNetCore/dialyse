package com.hemodialyse.backend.infrastructure.reporting;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.poi.export.JRXlsExporter;
import net.sf.jasperreports.export.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.*;
import java.sql.Connection;
import java.util.*;

/**
 * Service for advanced report generation using JasperReports engine.
 * Supports:
 * - Dynamic .jrxml compilation
 * - Programmatic report generation from SQL queries
 * - PDF, Excel, HTML output
 * - JDBC data source (multi-table joins)
 * - Images (header/footer)
 */
@Service
public class JasperReportService {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public JasperReportService(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    // ─── From .jrxml (stored in DB or uploaded) ─────────────────────────

    /**
     * Compile a .jrxml template string into a JasperReport.
     */
    public JasperReport compileFromXml(String jrxml) throws JRException {
        return JasperCompileManager.compileReport(new ByteArrayInputStream(jrxml.getBytes()));
    }

    /**
     * Fill a compiled report with JDBC data + parameters.
     */
    public JasperPrint fillReport(JasperReport report, Map<String, Object> params) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            return JasperFillManager.fillReport(report, params, conn);
        }
    }

    /**
     * Fill a report with a collection (Bean data source) instead of JDBC.
     */
    public JasperPrint fillReportWithBeans(JasperReport report, Map<String, Object> params,
                                            Collection<?> beans) throws JRException {
        JRBeanCollectionDataSource ds = new JRBeanCollectionDataSource(beans);
        return JasperFillManager.fillReport(report, params, ds);
    }

    // ─── From SQL (dynamic) ─────────────────────────────────────────────

    /**
     * Run SQL, collect results, and fill a report with the data as bean collection.
     */
    public JasperPrint fillReportFromSql(JasperReport report, Map<String, Object> params,
                                          String sql) throws JRException {
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        JRBeanCollectionDataSource ds = new JRBeanCollectionDataSource(rows);
        return JasperFillManager.fillReport(report, params, ds);
    }

    // ─── Export helpers ─────────────────────────────────────────────────

    public byte[] exportToPdf(JasperPrint print) throws JRException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos));
        exporter.exportReport();
        return baos.toByteArray();
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

    public String exportToHtml(JasperPrint print) throws JRException {
        // JasperReports 7 removed direct HTML export from core.
        // We generate PDF and embed it in an HTML page with a viewer.
        byte[] pdf = exportToPdf(print);
        String base64 = java.util.Base64.getEncoder().encodeToString(pdf);
        return "<html><head><meta charset='UTF-8'><title>Report Preview</title></head><body style='margin:0'>" +
               "<iframe src='data:application/pdf;base64," + base64 + "' width='100%' height='100%' style='border:none;min-height:95vh'></iframe>" +
               "</body></html>";
    }

    // ─── Programmatic report builder ────────────────────────────────────

    /**
     * Build a simple tabular report .jrxml programmatically from column definitions.
     * This is the BIRT-like feature: define columns, the system generates the template.
     *
     * @param title      Report title
     * @param columns    column aliases from SQL
     * @param headers    column display headers
     * @param pageFormat "A4" or "A5" or "LETTER"
     * @param landscape  true for landscape
     * @return .jrxml XML string
     */
    public String buildDynamicJrxml(String title, List<String> columns, List<String> headers,
                                     String pageFormat, boolean landscape,
                                     String headerImageBase64, String footerImageBase64) {
        int pageW, pageH;
        switch (pageFormat != null ? pageFormat.toUpperCase() : "A4") {
            case "A5":  pageW = 420; pageH = 595; break;
            case "LETTER": pageW = 612; pageH = 792; break;
            default:    pageW = 595; pageH = 842; break;
        }
        if (landscape) { int tmp = pageW; pageW = pageH; pageH = tmp; }

        int margin = 30;
        int contentW = pageW - 2 * margin;
        int colWidth = columns.isEmpty() ? contentW : contentW / columns.size();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\"\n");
        xml.append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        xml.append("  xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\"\n");
        xml.append("  name=\"dynamic_report\" pageWidth=\"").append(pageW).append("\" pageHeight=\"").append(pageH).append("\"\n");
        xml.append("  leftMargin=\"").append(margin).append("\" rightMargin=\"").append(margin).append("\"\n");
        xml.append("  topMargin=\"").append(margin).append("\" bottomMargin=\"").append(margin).append("\">\n\n");

        // Parameters
        xml.append("  <parameter name=\"REPORT_TITLE\" class=\"java.lang.String\"/>\n");
        xml.append("  <parameter name=\"HEADER_IMAGE\" class=\"java.lang.String\"/>\n");
        xml.append("  <parameter name=\"FOOTER_IMAGE\" class=\"java.lang.String\"/>\n");
        xml.append("  <parameter name=\"CENTER_NAME\" class=\"java.lang.String\"/>\n");
        xml.append("  <parameter name=\"GENERATED_DATE\" class=\"java.lang.String\"/>\n\n");

        // Fields — one per SQL column
        for (String col : columns) {
            xml.append("  <field name=\"").append(col).append("\" class=\"java.lang.Object\"/>\n");
        }
        xml.append("\n");

        // ── Title band (with optional header image) ──
        xml.append("  <title>\n    <band height=\"70\">\n");
        if (headerImageBase64 != null && !headerImageBase64.isBlank()) {
            xml.append("      <image><reportElement x=\"0\" y=\"0\" width=\"").append(contentW).append("\" height=\"40\"/><imageExpression><![CDATA[$P{HEADER_IMAGE}]]></imageExpression></image>\n");
        }
        xml.append("      <textField><reportElement x=\"0\" y=\"42\" width=\"").append(contentW).append("\" height=\"24\"/>");
        xml.append("<textElement textAlignment=\"Center\"><font size=\"16\" isBold=\"true\"/></textElement>");
        xml.append("<textFieldExpression><![CDATA[$P{REPORT_TITLE}]]></textFieldExpression></textField>\n");
        xml.append("    </band>\n  </title>\n\n");

        // ── Column header band ──
        xml.append("  <columnHeader>\n    <band height=\"22\">\n");
        int x = 0;
        for (int i = 0; i < headers.size(); i++) {
            int w = (i == headers.size() - 1) ? contentW - x : colWidth;
            xml.append("      <staticText><reportElement x=\"").append(x).append("\" y=\"0\" width=\"").append(w).append("\" height=\"20\" backcolor=\"#E8F5E9\" mode=\"Opaque\"/>");
            xml.append("<textElement><font size=\"10\" isBold=\"true\"/></textElement>");
            xml.append("<text><![CDATA[").append(escapeXml(headers.get(i))).append("]]></text></staticText>\n");
            x += w;
        }
        xml.append("    </band>\n  </columnHeader>\n\n");

        // ── Detail band ──
        xml.append("  <detail>\n    <band height=\"18\">\n");
        x = 0;
        for (int i = 0; i < columns.size(); i++) {
            int w = (i == columns.size() - 1) ? contentW - x : colWidth;
            xml.append("      <textField isBlankWhenNull=\"true\"><reportElement x=\"").append(x).append("\" y=\"0\" width=\"").append(w).append("\" height=\"16\"/>");
            xml.append("<textElement><font size=\"9\"/></textElement>");
            xml.append("<textFieldExpression><![CDATA[$F{").append(columns.get(i)).append("}]]></textFieldExpression></textField>\n");
            x += w;
        }
        xml.append("    </band>\n  </detail>\n\n");

        // ── Page footer ──
        xml.append("  <pageFooter>\n    <band height=\"40\">\n");
        if (footerImageBase64 != null && !footerImageBase64.isBlank()) {
            xml.append("      <image><reportElement x=\"0\" y=\"0\" width=\"").append(contentW).append("\" height=\"25\"/><imageExpression><![CDATA[$P{FOOTER_IMAGE}]]></imageExpression></image>\n");
        }
        xml.append("      <textField><reportElement x=\"0\" y=\"26\" width=\"").append(contentW / 2).append("\" height=\"12\"/>");
        xml.append("<textElement><font size=\"7\"/></textElement>");
        xml.append("<textFieldExpression><![CDATA[\"Généré le \" + $P{GENERATED_DATE}]]></textFieldExpression></textField>\n");
        xml.append("      <textField><reportElement x=\"").append(contentW / 2).append("\" y=\"26\" width=\"").append(contentW / 2).append("\" height=\"12\"/>");
        xml.append("<textElement textAlignment=\"Right\"><font size=\"7\"/></textElement>");
        xml.append("<textFieldExpression><![CDATA[\"Page \" + $V{PAGE_NUMBER}]]></textFieldExpression></textField>\n");
        xml.append("    </band>\n  </pageFooter>\n\n");

        xml.append("</jasperReport>\n");
        return xml.toString();
    }

    private String escapeXml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}






