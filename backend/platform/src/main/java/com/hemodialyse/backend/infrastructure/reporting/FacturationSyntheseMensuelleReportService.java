package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.BarresDeRepartition;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FormatDImpression;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ImpressionDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Impression de la synthèse mensuelle de facturation : adaptateur du port {@link ImpressionDeLaSynthese} du module Facturation
 * (slice F5). Le module lit les données (caisses, indicateurs, répartitions, totaux enregistrés des factures) ; ce service
 * construit à la volée le gabarit Jasper (une colonne HT et TTC par caisse), le compile dans un fichier temporaire et le rend.
 * Le tableau forfait x caisse est lu par le gabarit lui-même ; le TTC d'une ligne est sa part du TTC enregistré de la facture
 * (ADR 0034), les montants globaux sont les totaux enregistrés passés en paramètres.
 */
@Service
public class FacturationSyntheseMensuelleReportService implements ImpressionDeLaSynthese {

    private final JasperReportService jasperReportService;

    public FacturationSyntheseMensuelleReportService(JasperReportService jasperReportService) {
        this.jasperReportService = jasperReportService;
    }

    @Override
    public byte[] imprimer(SyntheseMensuelle synthese, FormatDImpression format) {
        List<CaisseColumn> caisses = new ArrayList<>();
        for (SyntheseMensuelle.Caisse caisse : synthese.colonnes()) {
            caisses.add(new CaisseColumn(caisse.code(), caisse.libelle(), "HT_" + caisses.size(), "TTC_" + caisses.size()));
        }
        String jrxml = buildDynamicTemplate(caisses);

        Path tempJrxml = null;
        try {
            tempJrxml = Files.createTempFile("synthese-facturation-", ".jrxml");
            Files.writeString(tempJrxml, jrxml, StandardCharsets.UTF_8);
            Map<String, Object> params = new HashMap<>();
            params.put("CENTER_ID", synthese.centre().toString());
            params.put("PERIOD_START", synthese.debut().toString());
            params.put("PERIOD_END", synthese.fin().toString());
            params.put("DISTRIBUTION_BY_CAISSE_TEXT", BarresDeRepartition.texte(synthese.parCaisse()));
            params.put("DISTRIBUTION_BY_STATUS_TEXT", BarresDeRepartition.texte(synthese.parSituation()));
            params.put("TOTAL_PATIENTS", synthese.patients());
            params.put("TOTAL_SEANCES", synthese.seances());
            params.put("TOTAL_FACTURES", synthese.factures());
            params.put("TOTAL_HT_FACTURES", synthese.totalHt());
            params.put("TOTAL_TVA_FACTURES", synthese.totalTva());
            params.put("TOTAL_TTC_FACTURES", synthese.totalTtc());
            return jasperReportService.generateReport(tempJrxml.toString(), params, format.name());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Echec generation synthese dynamique: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
        } finally {
            supprimer(tempJrxml);
        }
    }

    private static void supprimer(Path tempJrxml) {
        if (tempJrxml == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempJrxml);
            Files.deleteIfExists(tempJrxml.resolveSibling(tempJrxml.getFileName().toString().replace(".jrxml", ".jasper")));
        } catch (IOException ignored) {
            // fichier temporaire : sans conséquence
        }
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
                .append("           (CASE WHEN f.total_ht = 0 THEN 0 ELSE fl.line_ht * f.total_ttc / f.total_ht END) AS line_ttc\n")
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
                .append("    <parameter name=\"PERIOD_END\" class=\"java.lang.String\"/>\n")
                .append("    <parameter name=\"DISTRIBUTION_BY_CAISSE_TEXT\" class=\"java.lang.String\"/>\n")
                .append("    <parameter name=\"DISTRIBUTION_BY_STATUS_TEXT\" class=\"java.lang.String\"/>\n")
                .append("    <parameter name=\"TOTAL_PATIENTS\" class=\"java.lang.Long\"/>\n")
                .append("    <parameter name=\"TOTAL_SEANCES\" class=\"java.lang.Long\"/>\n")
                .append("    <parameter name=\"TOTAL_FACTURES\" class=\"java.lang.Long\"/>\n")
                .append("    <parameter name=\"TOTAL_HT_FACTURES\" class=\"java.math.BigDecimal\"/>\n")
                .append("    <parameter name=\"TOTAL_TVA_FACTURES\" class=\"java.math.BigDecimal\"/>\n")
                .append("    <parameter name=\"TOTAL_TTC_FACTURES\" class=\"java.math.BigDecimal\"/>\n\n")
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
                .append("        <band height=\"360\">\n")
                .append("            <staticText>\n")
                .append("                <reportElement x=\"0\" y=\"0\" width=\"").append(columnWidth).append("\" height=\"20\"/>\n")
                .append("                <textElement textAlignment=\"Left\" verticalAlignment=\"Middle\"><font size=\"11\" isBold=\"true\"/></textElement>\n")
                .append("                <text><![CDATA[Vue d'ensemble mensuelle]]></text>\n")
                .append("            </staticText>\n")
                .append("            <rectangle>\n")
                .append("                <reportElement x=\"0\" y=\"24\" width=\"140\" height=\"44\" backcolor=\"#E8F5E9\" mode=\"Opaque\"/>\n")
                .append("            </rectangle>\n")
                .append("            <staticText>\n")
                .append("                <reportElement x=\"6\" y=\"28\" width=\"128\" height=\"12\"/>\n")
                .append("                <textElement><font size=\"8\" isBold=\"true\"/></textElement>\n")
                .append("                <text><![CDATA[Montant HT]]></text>\n")
                .append("            </staticText>\n")
                .append("            <textField pattern=\"#,##0.00\">\n")
                .append("                <reportElement x=\"6\" y=\"42\" width=\"128\" height=\"20\"/>\n")
                .append("                <textElement><font size=\"12\" isBold=\"true\"/></textElement>\n")
                .append("                <textFieldExpression><![CDATA[$P{TOTAL_HT_FACTURES}]]></textFieldExpression>\n")
                .append("            </textField>\n")
                .append("            <rectangle>\n")
                .append("                <reportElement x=\"150\" y=\"24\" width=\"140\" height=\"44\" backcolor=\"#E3F2FD\" mode=\"Opaque\"/>\n")
                .append("            </rectangle>\n")
                .append("            <staticText>\n")
                .append("                <reportElement x=\"156\" y=\"28\" width=\"128\" height=\"12\"/>\n")
                .append("                <textElement><font size=\"8\" isBold=\"true\"/></textElement>\n")
                .append("                <text><![CDATA[Montant TTC]]></text>\n")
                .append("            </staticText>\n")
                .append("            <textField pattern=\"#,##0.00\">\n")
                .append("                <reportElement x=\"156\" y=\"42\" width=\"128\" height=\"20\"/>\n")
                .append("                <textElement><font size=\"12\" isBold=\"true\"/></textElement>\n")
                .append("                <textFieldExpression><![CDATA[$P{TOTAL_TTC_FACTURES}]]></textFieldExpression>\n")
                .append("            </textField>\n")
                .append("            <rectangle>\n")
                .append("                <reportElement x=\"690\" y=\"24\" width=\"110\" height=\"44\" backcolor=\"#FFEBEE\" mode=\"Opaque\"/>\n")
                .append("            </rectangle>\n")
                .append("            <staticText>\n")
                .append("                <reportElement x=\"696\" y=\"28\" width=\"98\" height=\"12\"/>\n")
                .append("                <textElement><font size=\"8\" isBold=\"true\"/></textElement>\n")
                .append("                <text><![CDATA[Montant TVA]]></text>\n")
                .append("            </staticText>\n")
                .append("            <textField pattern=\"#,##0.00\">\n")
                .append("                <reportElement x=\"696\" y=\"42\" width=\"98\" height=\"20\"/>\n")
                .append("                <textElement><font size=\"12\" isBold=\"true\"/></textElement>\n")
                .append("                <textFieldExpression><![CDATA[$P{TOTAL_TVA_FACTURES}]]></textFieldExpression>\n")
                .append("            </textField>\n")
                .append("            <rectangle>\n")
                .append("                <reportElement x=\"300\" y=\"24\" width=\"120\" height=\"44\" backcolor=\"#FFF8E1\" mode=\"Opaque\"/>\n")
                .append("            </rectangle>\n")
                .append("            <staticText>\n")
                .append("                <reportElement x=\"306\" y=\"28\" width=\"108\" height=\"12\"/>\n")
                .append("                <textElement><font size=\"8\" isBold=\"true\"/></textElement>\n")
                .append("                <text><![CDATA[Patients]]></text>\n")
                .append("            </staticText>\n")
                .append("            <textField>\n")
                .append("                <reportElement x=\"306\" y=\"42\" width=\"108\" height=\"20\"/>\n")
                .append("                <textElement><font size=\"12\" isBold=\"true\"/></textElement>\n")
                .append("                <textFieldExpression><![CDATA[$P{TOTAL_PATIENTS}]]></textFieldExpression>\n")
                .append("            </textField>\n")
                .append("            <rectangle>\n")
                .append("                <reportElement x=\"430\" y=\"24\" width=\"120\" height=\"44\" backcolor=\"#F3E5F5\" mode=\"Opaque\"/>\n")
                .append("            </rectangle>\n")
                .append("            <staticText>\n")
                .append("                <reportElement x=\"436\" y=\"28\" width=\"108\" height=\"12\"/>\n")
                .append("                <textElement><font size=\"8\" isBold=\"true\"/></textElement>\n")
                .append("                <text><![CDATA[Seances]]></text>\n")
                .append("            </staticText>\n")
                .append("            <textField>\n")
                .append("                <reportElement x=\"436\" y=\"42\" width=\"108\" height=\"20\"/>\n")
                .append("                <textElement><font size=\"12\" isBold=\"true\"/></textElement>\n")
                .append("                <textFieldExpression><![CDATA[$P{TOTAL_SEANCES}]]></textFieldExpression>\n")
                .append("            </textField>\n")
                .append("            <rectangle>\n")
                .append("                <reportElement x=\"560\" y=\"24\" width=\"120\" height=\"44\" backcolor=\"#ECEFF1\" mode=\"Opaque\"/>\n")
                .append("            </rectangle>\n")
                .append("            <staticText>\n")
                .append("                <reportElement x=\"566\" y=\"28\" width=\"108\" height=\"12\"/>\n")
                .append("                <textElement><font size=\"8\" isBold=\"true\"/></textElement>\n")
                .append("                <text><![CDATA[Factures]]></text>\n")
                .append("            </staticText>\n")
                .append("            <textField>\n")
                .append("                <reportElement x=\"566\" y=\"42\" width=\"108\" height=\"20\"/>\n")
                .append("                <textElement><font size=\"12\" isBold=\"true\"/></textElement>\n")
                .append("                <textFieldExpression><![CDATA[$P{TOTAL_FACTURES}]]></textFieldExpression>\n")
                .append("            </textField>\n")
                .append("            <line>\n")
                .append("                <reportElement x=\"0\" y=\"78\" width=\"").append(columnWidth).append("\" height=\"1\" forecolor=\"#B0BEC5\"/>\n")
                .append("            </line>\n")
                .append("            <staticText>\n")
                .append("                <reportElement x=\"0\" y=\"84\" width=\"").append(columnWidth / 2).append("\" height=\"16\"/>\n")
                .append("                <textElement><font size=\"9\" isBold=\"true\"/></textElement>\n")
                .append("                <text><![CDATA[Repartition patients/seances par caisse]]></text>\n")
                .append("            </staticText>\n")
                .append("            <staticText>\n")
                .append("                <reportElement x=\"").append(columnWidth / 2).append("\" y=\"84\" width=\"").append(columnWidth - (columnWidth / 2)).append("\" height=\"16\"/>\n")
                .append("                <textElement textAlignment=\"Left\"><font size=\"9\" isBold=\"true\"/></textElement>\n")
                .append("                <text><![CDATA[Repartition patients/seances par situation patient]]></text>\n")
                .append("            </staticText>\n")
                .append("            <textField isBlankWhenNull=\"true\" textAdjust=\"StretchHeight\">\n")
                .append("                <reportElement x=\"0\" y=\"104\" width=\"").append(columnWidth / 2 - 8).append("\" height=\"244\"/>\n")
                .append("                <textElement><font size=\"8\"/></textElement>\n")
                .append("                <textFieldExpression><![CDATA[$P{DISTRIBUTION_BY_CAISSE_TEXT}]]></textFieldExpression>\n")
                .append("            </textField>\n")
                .append("            <textField isBlankWhenNull=\"true\" textAdjust=\"StretchHeight\">\n")
                .append("                <reportElement x=\"").append(columnWidth / 2 + 8).append("\" y=\"104\" width=\"").append(columnWidth / 2 - 8).append("\" height=\"244\"/>\n")
                .append("                <textElement><font size=\"8\"/></textElement>\n")
                .append("                <textFieldExpression><![CDATA[$P{DISTRIBUTION_BY_STATUS_TEXT}]]></textFieldExpression>\n")
                .append("            </textField>\n")
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
