package com.hemodialyse.backend.infrastructure.importer;

import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lit un fichier CSV (séparateur « ; », « , » ou tabulation, détecté) ou Excel (.xlsx / .xls, première feuille)
 * en {@link ImportTable}. La première ligne non vide contient les en-têtes.
 * <p>
 * Un fichier illisible est refusé avec un message explicite ; le contrôle du contenu (colonnes manquantes,
 * valeurs invalides) est l'affaire du domaine.
 */
@Component
public class TabularFileReader {

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    /**
     * Garde-fou technique : le domaine refuse bien avant (voir MAX_IMPORT_ROWS).
     */
    private static final int MAX_ROWS_READ = 20_000;
    private static final int MAX_COLUMNS = 100;

    private static Format detectFormat(String filename, byte[] content) {
        boolean zip = content.length > 3 && content[0] == 'P' && content[1] == 'K';
        boolean ole2 = content.length > 7 && (content[0] & 0xFF) == 0xD0 && (content[1] & 0xFF) == 0xCF;
        if (zip || ole2) return Format.EXCEL;
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            throw new BusinessException("IMPORT_UNREADABLE_FILE",
                    "Le fichier porte l'extension Excel mais son contenu n'est pas un classeur Excel valide.");
        }
        if (name.isEmpty() || name.endsWith(".csv") || name.endsWith(".txt")) return Format.CSV;
        throw new BusinessException("IMPORT_UNSUPPORTED_FORMAT",
                "Format non pris en charge : utilisez un fichier .csv, .xlsx ou .xls.");
    }

    private static ImportTable readExcel(byte[] content) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) return new ImportTable(List.of(), List.of());
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            List<String> headers = null;
            List<ImportTable.Row> rows = new ArrayList<>();
            int last = Math.min(sheet.getLastRowNum(), MAX_ROWS_READ);
            for (int r = sheet.getFirstRowNum(); r <= last; r++) {
                List<String> cells = readRow(sheet.getRow(r), formatter, evaluator);
                if (headers == null) {
                    if (cells.stream().anyMatch(c -> !c.isBlank())) headers = cells;
                    continue;
                }
                rows.add(new ImportTable.Row(r + 1, cells));
            }
            return new ImportTable(headers == null ? List.of() : headers, rows);
        } catch (EncryptedDocumentException e) {
            throw new BusinessException("IMPORT_ENCRYPTED_FILE", "Le classeur est protégé par mot de passe : retirez la protection puis réessayez.");
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("IMPORT_UNREADABLE_FILE", "Le classeur Excel est illisible ou endommagé.");
        }
    }

    private static List<String> readRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> cells = new ArrayList<>();
        if (row == null || row.getLastCellNum() < 0) return cells;
        int lastCell = Math.min(row.getLastCellNum(), MAX_COLUMNS);
        for (int c = 0; c < lastCell; c++) {
            Cell cell = row.getCell(c);
            cells.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim());
        }
        return cells;
    }

    // ---- Excel -------------------------------------------------------------------------------------------------

    private static ImportTable readCsv(byte[] content) {
        String text = decode(content);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
        char delimiter = detectDelimiter(text);

        List<String> headers = null;
        List<ImportTable.Row> rows = new ArrayList<>();
        for (CsvRecord record : parseCsv(text, delimiter)) {
            if (headers == null) {
                if (record.cells().stream().anyMatch(c -> !c.isBlank())) headers = record.cells();
                continue;
            }
            if (rows.size() >= MAX_ROWS_READ) break;
            rows.add(new ImportTable.Row(record.lineNumber(), record.cells()));
        }
        return new ImportTable(headers == null ? List.of() : headers, rows);
    }

    /**
     * UTF-8 si le contenu est valide, sinon Windows-1252 (CSV exporté par Excel en français).
     */
    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(content, Charset.forName("windows-1252"));
        }
    }

    // ---- CSV ---------------------------------------------------------------------------------------------------

    /**
     * Séparateur le plus fréquent (hors guillemets) sur la première ligne non vide.
     */
    static char detectDelimiter(String text) {
        int semicolons = 0, commas = 0, tabs = 0;
        boolean quoted = false, seenContent = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') quoted = !quoted;
            else if (!quoted && (ch == '\n' || ch == '\r')) {
                if (seenContent) break;
            } else if (!quoted) {
                if (ch == ';') semicolons++;
                else if (ch == ',') commas++;
                else if (ch == '\t') tabs++;
                if (!Character.isWhitespace(ch)) seenContent = true;
            }
        }
        if (tabs > semicolons && tabs > commas) return '\t';
        return commas > semicolons ? ',' : ';';
    }

    /**
     * RFC 4180 : guillemets, guillemets doublés, retours à la ligne dans une valeur entre guillemets.
     */
    static List<CsvRecord> parseCsv(String text, char delimiter) {
        List<CsvRecord> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int recordStart = 1;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (ch == '\n') line++;
                    cell.append(ch);
                }
            } else if (ch == '"' && cell.toString().isBlank()) {
                cell.setLength(0);
                quoted = true;
            } else if (ch == delimiter) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                cells.add(cell.toString().trim());
                cell.setLength(0);
                records.add(new CsvRecord(recordStart, cells));
                cells = new ArrayList<>();
                line++;
                recordStart = line;
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !cells.isEmpty()) {
            cells.add(cell.toString().trim());
            records.add(new CsvRecord(recordStart, cells));
        }
        return records;
    }

    public ImportTable read(String filename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException("IMPORT_EMPTY_FILE", "Le fichier est vide.");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new BusinessException("IMPORT_FILE_TOO_LARGE", "Le fichier dépasse 5 Mo : découpez-le en plusieurs fichiers.");
        }
        return switch (detectFormat(filename, content)) {
            case EXCEL -> readExcel(content);
            case CSV -> readCsv(content);
        };
    }

    private enum Format {CSV, EXCEL}

    record CsvRecord(int lineNumber, List<String> cells) {
    }
}

