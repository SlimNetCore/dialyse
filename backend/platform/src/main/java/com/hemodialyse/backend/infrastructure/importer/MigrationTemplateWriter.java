package com.hemodialyse.backend.infrastructure.importer;

import com.hemodialyse.backend.domain.migration.model.ColumnDef;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Modèles de fichiers de reprise : en-têtes attendus, ligne d'exemple et onglet « Instructions ».
 */
@Component
public class MigrationTemplateWriter {

    static String describe(ColumnDef column) {
        String base = switch (column.type()) {
            case TEXT -> "Texte" + (column.maxLength() > 0 ? ", " + column.maxLength() + " caractères maximum" : "");
            case DATE -> "Date JJ/MM/AAAA (ou AAAA-MM-JJ)";
            case INTEGER -> "Nombre entier";
            case DECIMAL -> "Nombre (virgule ou point décimal, ex. 5600,50)";
            case BOOLEAN -> "oui / non";
            case PHONE -> "Téléphone, 8 à 15 chiffres";
            case EMAIL -> "Adresse e-mail";
            case DAYS -> "Jours séparés par des virgules : Lun, Mar, Mer, Jeu, Ven, Sam, Dim";
            case ENUM -> "Une valeur parmi : " + String.join(", ", column.allowedValues())
                    + " (les autres valeurs pourront être associées lors de la vérification)";
            case REFERENCE -> "Référence existante (voir l'intitulé de la colonne)";
        };
        return column.defaultValue() != null ? base + " — vide = " + column.defaultValue() : base;
    }

    private static String quote(String value) {
        return value.contains(";") || value.contains("\"") || value.contains("\n")
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    public byte[] csv(MigrationEntity entity) {
        String header = entity.columns().stream().map(c -> quote(c.label())).collect(Collectors.joining(";"));
        String example = entity.columns().stream().map(c -> quote(c.example() == null ? "" : c.example()))
                .collect(Collectors.joining(";"));
        return ("\uFEFF" + header + "\r\n" + example + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    public byte[] xlsx(MigrationEntity entity) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);
            CellStyle text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));

            Sheet data = workbook.createSheet(entity.label().replaceAll("[\\\\/*?:\\[\\]']", " ").trim());
            Row header = data.createRow(0);
            Row example = data.createRow(1);
            List<ColumnDef> columns = entity.columns();
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i).label());
                header.getCell(i).setCellStyle(bold);
                example.createCell(i).setCellValue(columns.get(i).example() == null ? "" : columns.get(i).example());
                // Colonnes au format texte : Excel ne transforme ni les n° d'assurance ni les codes en nombres.
                data.setDefaultColumnStyle(i, text);
                data.setColumnWidth(i, 22 * 256);
            }

            Sheet help = workbook.createSheet("Instructions");
            String[] titles = {"Colonne", "Obligatoire", "Format attendu"};
            Row helpHeader = help.createRow(0);
            for (int i = 0; i < titles.length; i++) {
                helpHeader.createCell(i).setCellValue(titles[i]);
                helpHeader.getCell(i).setCellStyle(bold);
            }
            for (int i = 0; i < columns.size(); i++) {
                ColumnDef column = columns.get(i);
                Row row = help.createRow(i + 1);
                row.createCell(0).setCellValue(column.label());
                row.createCell(1).setCellValue(column.requiredColumn() ? "Oui" : "Non");
                row.createCell(2).setCellValue(describe(column));
            }
            help.setColumnWidth(0, 32 * 256);
            help.setColumnWidth(1, 12 * 256);
            help.setColumnWidth(2, 100 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}


