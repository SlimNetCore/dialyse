package com.hemodialyse.backend.infrastructure.reporting;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FacturationSyntheseMensuelleReportService {

    private static final Set<String> ALLOWED_FORMATS = Set.of("PDF", "EXCEL", "XLS", "XLSX", "HTML");

    private final JdbcTemplate jdbc;
    private final JasperReportService jasperReportService;

    public FacturationSyntheseMensuelleReportService(JdbcTemplate jdbc, JasperReportService jasperReportService) {
        this.jdbc = jdbc;
        this.jasperReportService = jasperReportService;
    }

    public byte[] generate(UUID centerId, LocalDate periodStart, LocalDate periodEnd, String format) throws Exception {
        if (centerId == null) {
            throw new IllegalArgumentException("centerId est obligatoire");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("periodStart et periodEnd sont obligatoires");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("periodStart doit etre inferieure ou egale a periodEnd");
        }

        String normalizedFormat = normalizeFormat(format);
        List<CaisseColumn> caisses = loadCaisses(centerId, periodStart, periodEnd);
        String jrxml = buildDynamicTemplate(caisses);

        Path tempJrxml = Files.createTempFile("synthese-facturation-", ".jrxml");
        Files.writeString(tempJrxml, jrxml, StandardCharsets.UTF_8);

        try {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("CENTER_ID", centerId.toString());
                params.put("PERIOD_START", periodStart.toString());
                params.put("PERIOD_END", periodEnd.toString());
                return jasperReportService.generateReport(
                        tempJrxml.toString(),
                        params,
                        normalizedFormat
                );
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Echec generation synthese dynamique: " + e.getClass().getSimpleName() + " - " + e.getMessage(),
                        e
                );
            }
        } finally {
            Path tempJasper = tempJrxml.resolveSibling(tempJrxml.getFileName().toString().replace(".jrxml", ".jasper"));
            Files.deleteIfExists(tempJrxml);
            Files.deleteIfExists(tempJasper);
        }
    }

    private String normalizeFormat(String format) {
        String normalized = (format == null || format.isBlank()) ? "PDF" : format.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_FORMATS.contains(normalized)) {
            throw new IllegalArgumentException("format non supporte: " + normalized);
        }
        return normalized;
    }

    private List<CaisseColumn> loadCaisses(UUID centerId, LocalDate periodStart, LocalDate periodEnd) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT DISTINCT
                               COALESCE(NULLIF(TRIM(ca.code), ''), 'INCONNU') AS caisse_code,
                               COALESCE(NULLIF(TRIM(ca.nom), ''), 'Inconnu') AS caisse_label
                        FROM factures f
                        LEFT JOIN agence a ON a.id = f.agence_id_snapshot AND a.center_id = f.center_id
                        LEFT JOIN caisse_assurance ca ON ca.id = a.caisse_id AND ca.center_id = f.center_id
                        WHERE f.center_id = ?
                          AND f.date_facturation BETWEEN ? AND ?
                        ORDER BY caisse_label ASC
                        """,
                centerId,
                Date.valueOf(periodStart),
                Date.valueOf(periodEnd)
        );

        List<CaisseColumn> result = new ArrayList<>();
        Set<String> uniqueCodes = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String code = String.valueOf(row.get("CAISSE_CODE"));
            String label = String.valueOf(row.get("CAISSE_LABEL"));
            if (!uniqueCodes.add(code)) {
                continue;
            }
            result.add(new CaisseColumn(code, label, "HT_" + result.size(), "TTC_" + result.size()));
        }

        if (result.isEmpty()) {
            result.add(new CaisseColumn("INCONNU", "Inconnu", "HT_0", "TTC_0"));
        }
        return result;
    }

    private String buildDynamicTemplate(List<CaisseColumn> caisses) {
        int left = 20;
        int right = 20;
        int firstCol = 170;
        int valueCol = 80;
        int totalCol = 90;
        int contentWidth = firstCol + (caisses.size() * 2 * valueCol) + (2 * totalCol);
        int pageWidth = Math.max(842, left + right + contentWidth);
        int columnWidth = pageWidth - left - right;

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT src.forfait_label AS forfait_label,\n");
        for (int i = 0; i < caisses.size(); i++) {
            CaisseColumn column = caisses.get(i);
            queryBuilder.append("       SUM(CASE WHEN src.caisse_code = '")
                    .append(sqlLiteral(column.code()))
                    .append("' THEN src.line_ht ELSE 0 END) AS ")
                    .append(column.htField())
                    .append(",\n");
            queryBuilder.append("       SUM(CASE WHEN src.caisse_code = '")
                    .append(sqlLiteral(column.code()))
                    .append("' THEN src.line_ttc ELSE 0 END) AS ")
                    .append(column.ttcField())
                    .append(",\n");
        }
        queryBuilder.append("       SUM(src.line_ht) AS total_ht,\n")
                .append("       SUM(src.line_ttc) AS total_ttc\n")
                .append("FROM (\n")
                .append("    SELECT fl.forfait_label,\n")
                .append("           COALESCE(NULLIF(TRIM(ca.code), ''), 'INCONNU') AS caisse_code,\n")
                .append("           fl.line_ht AS line_ht,\n")
                .append("           (fl.line_ht * (1 + (f.tva_rate / 100))) AS line_ttc\n")
                .append("    FROM facture_lignes fl\n")
                .append("    INNER JOIN factures f ON f.id = fl.facture_id AND f.center_id = fl.center_id\n")
                .append("    LEFT JOIN agence a ON a.id = f.agence_id_snapshot AND a.center_id = f.center_id\n")
                .append("    LEFT JOIN caisse_assurance ca ON ca.id = a.caisse_id AND ca.center_id = f.center_id\n")
                .append("    WHERE f.center_id = CAST($P{CENTER_ID} AS UUID)\n")
                .append("      AND f.date_facturation BETWEEN CAST($P{PERIOD_START} AS DATE) AND CAST($P{PERIOD_END} AS DATE)\n")
                .append(") src\n")
                .append("GROUP BY src.forfait_label\n")
                .append("ORDER BY src.forfait_label");

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<jasperReport xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
                .append("              xmlns=\"http://jasperreports.sourceforge.net/jasperreports\"\n")
                .append("              xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\"\n")
                .append("              name=\"synthese_mensuelle_facturation_dynamic\" pageWidth=\"").append(pageWidth).append("\" pageHeight=\"595\" orientation=\"Landscape\"\n")
                .append("              leftMargin=\"").append(left).append("\" rightMargin=\"").append(right).append("\" topMargin=\"20\" bottomMargin=\"20\" columnWidth=\"").append(columnWidth).append("\">\n\n")
                .append("    <parameter name=\"CENTER_ID\" class=\"java.lang.String\"/>\n")
                .append("    <parameter name=\"PERIOD_START\" class=\"java.lang.String\"/>\n")
                .append("    <parameter name=\"PERIOD_END\" class=\"java.lang.String\"/>\n\n")
                .append("    <queryString>\n")
                .append("        <![CDATA[\n")
                .append(queryBuilder)
                .append("\n        ]]>\n")
                .append("    </queryString>\n\n")
                .append("    <field name=\"FORFAIT_LABEL\" class=\"java.lang.String\"/>\n");

        for (CaisseColumn column : caisses) {
            xml.append("    <field name=\"").append(column.htField()).append("\" class=\"java.math.BigDecimal\"/>\n");
            xml.append("    <field name=\"").append(column.ttcField()).append("\" class=\"java.math.BigDecimal\"/>\n");
        }
        xml.append("    <field name=\"TOTAL_HT\" class=\"java.math.BigDecimal\"/>\n")
                .append("    <field name=\"TOTAL_TTC\" class=\"java.math.BigDecimal\"/>\n\n")
                .append("    <variable name=\"GLOBAL_TOTAL_HT\" class=\"java.math.BigDecimal\" calculation=\"Sum\">\n")
                .append("        <variableExpression><![CDATA[$F{TOTAL_HT}]]></variableExpression>\n")
                .append("    </variable>\n")
                .append("    <variable name=\"GLOBAL_TOTAL_TTC\" class=\"java.math.BigDecimal\" calculation=\"Sum\">\n")
                .append("        <variableExpression><![CDATA[$F{TOTAL_TTC}]]></variableExpression>\n")
                .append("    </variable>\n\n")
                .append("    <title>\n")
                .append("        <band height=\"70\">\n")
                .append("            <staticText>\n")
                .append("                <reportElement x=\"0\" y=\"0\" width=\"").append(columnWidth).append("\" height=\"28\"/>\n")
                .append("                <textElement textAlignment=\"Center\" verticalAlignment=\"Middle\">\n")
                .append("                    <font size=\"16\" isBold=\"true\"/>\n")
                .append("                </textElement>\n")
                .append("                <text><![CDATA[SYNTHESE MENSUELLE FACTURATION]]></text>\n")
                .append("            </staticText>\n")
                .append("            <textField>\n")
                .append("                <reportElement x=\"0\" y=\"34\" width=\"").append(columnWidth / 2).append("\" height=\"16\"/>\n")
                .append("                <textElement><font size=\"9\"/></textElement>\n")
                .append("                <textFieldExpression><![CDATA[\"Centre: \" + $P{CENTER_ID}]]></textFieldExpression>\n")
                .append("            </textField>\n")
                .append("            <textField>\n")
                .append("                <reportElement x=\"").append(columnWidth / 2).append("\" y=\"34\" width=\"").append(columnWidth - (columnWidth / 2)).append("\" height=\"16\"/>\n")
                .append("                <textElement textAlignment=\"Right\"><font size=\"9\"/></textElement>\n")
                .append("                <textFieldExpression><![CDATA[\"Periode: \" + $P{PERIOD_START} + \" au \" + $P{PERIOD_END}]]></textFieldExpression>\n")
                .append("            </textField>\n")
                .append("        </band>\n")
                .append("    </title>\n\n")
                .append("    <columnHeader>\n")
                .append("        <band height=\"34\">\n");

        int x = 0;
        xml.append(headerCell(x, firstCol, "Forfait"));
        x += firstCol;
        for (CaisseColumn column : caisses) {
            xml.append(headerCell(x, valueCol, cdataSafe(column.label()) + " HT"));
            x += valueCol;
            xml.append(headerCell(x, valueCol, cdataSafe(column.label()) + " TTC"));
            x += valueCol;
        }
        xml.append(headerCell(x, totalCol, "Total HT"));
        x += totalCol;
        xml.append(headerCell(x, totalCol, "Total TTC"));

        xml.append("        </band>\n")
                .append("    </columnHeader>\n\n")
                .append("    <detail>\n")
                .append("        <band height=\"18\">\n");

        x = 0;
        xml.append(textCellString(x, firstCol, "$F{FORFAIT_LABEL}", false));
        x += firstCol;
        for (CaisseColumn column : caisses) {
            xml.append(textCellNumber(x, valueCol, "$F{" + column.htField() + "}", true));
            x += valueCol;
            xml.append(textCellNumber(x, valueCol, "$F{" + column.ttcField() + "}", true));
            x += valueCol;
        }
        xml.append(textCellNumber(x, totalCol, "$F{TOTAL_HT}", true));
        x += totalCol;
        xml.append(textCellNumber(x, totalCol, "$F{TOTAL_TTC}", true));

        xml.append("        </band>\n")
                .append("    </detail>\n\n")
                .append("    <summary>\n")
                .append("        <band height=\"250\">\n")
                .append("            <staticText>\n")
                .append("                <reportElement x=\"0\" y=\"0\" width=\"280\" height=\"16\"/>\n")
                .append("                <textElement><font size=\"10\" isBold=\"true\"/></textElement>\n")
                .append("                <text><![CDATA[Totaux globaux]]></text>\n")
                .append("            </staticText>\n")
                .append("            <textField pattern=\"#,##0.00\">\n")
                .append("                <reportElement x=\"0\" y=\"20\" width=\"280\" height=\"16\"/>\n")
                .append("                <textElement><font size=\"9\"/></textElement>\n")
                .append("                <textFieldExpression><![CDATA[\"Montant HT: \" + $V{GLOBAL_TOTAL_HT}]]></textFieldExpression>\n")
                .append("            </textField>\n")
                .append("            <textField pattern=\"#,##0.00\">\n")
                .append("                <reportElement x=\"0\" y=\"40\" width=\"280\" height=\"16\"/>\n")
                .append("                <textElement><font size=\"9\"/></textElement>\n")
                .append("                <textFieldExpression><![CDATA[\"Montant TTC: \" + $V{GLOBAL_TOTAL_TTC}]]></textFieldExpression>\n")
                .append("            </textField>\n")
                .append("            <barChart>\n")
                .append("                <chart>\n")
                .append("                    <reportElement x=\"290\" y=\"0\" width=\"").append(columnWidth - 290).append("\" height=\"240\"/>\n")
                .append("                    <chartTitle/>\n")
                .append("                </chart>\n")
                .append("                <categoryDataset>\n");

        for (CaisseColumn column : caisses) {
            xml.append("                    <categorySeries>\n")
                    .append("                        <seriesExpression><![CDATA[\"").append(cdataSafe(column.label())).append(" TTC\"]]></seriesExpression>\n")
                    .append("                        <categoryExpression><![CDATA[$F{FORFAIT_LABEL}]]></categoryExpression>\n")
                    .append("                        <valueExpression><![CDATA[$F{").append(column.ttcField()).append("}]]></valueExpression>\n")
                    .append("                    </categorySeries>\n");
        }

        xml.append("                </categoryDataset>\n")
                .append("                <barPlot><plot/></barPlot>\n")
                .append("            </barChart>\n")
                .append("        </band>\n")
                .append("    </summary>\n")
                .append("</jasperReport>\n");

        return xml.toString();
    }

    private String headerCell(int x, int width, String text) {
        return "            <staticText>"
                + "<reportElement x=\"" + x + "\" y=\"0\" width=\"" + width + "\" height=\"34\" backcolor=\"#1B5E20\" forecolor=\"#FFFFFF\" mode=\"Opaque\"/>"
                + "<textElement textAlignment=\"Center\" verticalAlignment=\"Middle\"><font size=\"8\" isBold=\"true\"/></textElement>"
                + "<text><![CDATA[" + text + "]]></text>"
                + "</staticText>\n";
    }

    private String textCellString(int x, int width, String expression, boolean rightAlign) {
        String align = rightAlign ? " textAlignment=\"Right\"" : "";
        return "            <textField isBlankWhenNull=\"true\">"
                + "<reportElement x=\"" + x + "\" y=\"0\" width=\"" + width + "\" height=\"16\"/>"
                + "<textElement" + align + "><font size=\"8\"/></textElement>"
                + "<textFieldExpression><![CDATA[" + expression + "]]></textFieldExpression>"
                + "</textField>\n";
    }

    private String textCellNumber(int x, int width, String expression, boolean rightAlign) {
        String align = rightAlign ? " textAlignment=\"Right\"" : "";
        return "            <textField isBlankWhenNull=\"true\" pattern=\"#,##0.00\">"
                + "<reportElement x=\"" + x + "\" y=\"0\" width=\"" + width + "\" height=\"16\"/>"
                + "<textElement" + align + "><font size=\"8\"/></textElement>"
                + "<textFieldExpression><![CDATA[" + expression + "]]></textFieldExpression>"
                + "</textField>\n";
    }

    private String sqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private String cdataSafe(String value) {
        return value == null ? "" : value.replace("]]>", "] ]>");
    }

    private record CaisseColumn(String code, String label, String htField, String ttcField) {
    }
}




