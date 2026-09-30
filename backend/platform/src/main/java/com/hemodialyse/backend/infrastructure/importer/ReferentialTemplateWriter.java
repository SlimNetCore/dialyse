package com.hemodialyse.backend.infrastructure.importer;

import com.hemodialyse.backend.domain.referential.admin.model.FieldType;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialField;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
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
 * Modèle d'import téléchargeable : en-têtes attendus + une ligne d'exemple (+ onglet « Instructions » en Excel).
 */
@Component
public class ReferentialTemplateWriter {

    static String describe(ReferentialField field) {
        String base = switch (field.type()) {
            case TEXT -> "Texte, " + field.maxLength() + " caractères maximum";
            case PHONE -> "Numéro de téléphone (chiffres, espaces, +, -)";
            case DECIMAL -> "Nombre positif, 2 décimales max. (ex. 5600 ou 5600,50)";
            case ENUM -> "Une valeur parmi : " + String.join(", ", field.allowedValues());
            case REFERENCE -> "Code d'un élément existant de « " + field.referenceKind().label() + " »";
        };
        if (field.type() != FieldType.REFERENCE && field.defaultValue() != null) {
            base += " — vide = " + field.defaultValue();
        }
        return base;
    }

    private static String quote(String value) {
        return value.contains(";") || value.contains("\"") || value.contains("\n")
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private static String safeSheetName(String name) {
        String cleaned = name.replaceAll("[\\\\/*?:\\[\\]']", " ").trim();
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    public byte[] csv(ReferentialKind kind) {
        String header = kind.fields().stream().map(f -> quote(f.label())).collect(Collectors.joining(";"));
        String example = kind.fields().stream().map(f -> quote(f.example() == null ? "" : f.example()))
                .collect(Collectors.joining(";"));
        // BOM : Excel ouvre ainsi le fichier en UTF-8 (accents préservés).
        return ("\uFEFF" + header + "\r\n" + example + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    public byte[] xlsx(ReferentialKind kind) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);

            Sheet data = workbook.createSheet(safeSheetName(kind.label()));
            Row header = data.createRow(0);
            Row example = data.createRow(1);
            List<ReferentialField> fields = kind.fields();
            for (int i = 0; i < fields.size(); i++) {
                header.createCell(i).setCellValue(fields.get(i).label());
                header.getCell(i).setCellStyle(bold);
                example.createCell(i).setCellValue(fields.get(i).example() == null ? "" : fields.get(i).example());
                data.setColumnWidth(i, 24 * 256);
            }

            Sheet help = workbook.createSheet("Instructions");
            String[] titles = {"Colonne", "Obligatoire", "Format attendu"};
            Row helpHeader = help.createRow(0);
            for (int i = 0; i < titles.length; i++) {
                helpHeader.createCell(i).setCellValue(titles[i]);
                helpHeader.getCell(i).setCellStyle(bold);
            }
            for (int i = 0; i < fields.size(); i++) {
                ReferentialField field = fields.get(i);
                Row row = help.createRow(i + 1);
                row.createCell(0).setCellValue(field.label());
                row.createCell(1).setCellValue(field.requiredColumn() ? "Oui" : "Non");
                row.createCell(2).setCellValue(describe(field));
            }
            help.setColumnWidth(0, 24 * 256);
            help.setColumnWidth(1, 12 * 256);
            help.setColumnWidth(2, 90 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

