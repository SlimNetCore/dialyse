package com.hemodialyse.backend.infrastructure.importer;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.stock.model.ComptageImporte;
import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.LigneInventaire;
import com.hemodialyse.backend.domain.stock.model.ResultatImportComptage.Anomalie;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Feuille de comptage Excel d'un inventaire : génération (à imprimer ou à remplir) et relecture une fois remplie.
 * <p>
 * La colonne A (masquée) porte l'identifiant de la ligne d'inventaire : c'est elle qui sert au rapprochement.
 * À défaut (ligne recopiée à la main), le rapprochement se fait par code article + n° de lot.
 */
@Component
public class FeuilleComptageExcel {

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    static final String COL_ID = "ID";
    static final String COL_CODE = "Code";
    static final String COL_QTY = "Quantité comptée";
    static final String COL_LOT = "N° de lot";
    static final String COL_MOTIF = "Motif écart";
    /**
     * Motifs d'écart : code stocké → libellé proposé dans la feuille.
     */
    static final Map<String, String> MOTIFS = new LinkedHashMap<>();
    private static final int MAX_ROWS = 20_000;
    private static final int HEADER_SEARCH_ROWS = 20;
    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> MOTIF_PAR_CLE = new HashMap<>();

    static {
        MOTIFS.put("CASSE", "Casse");
        MOTIFS.put("PEREMPTION", "Péremption");
        MOTIFS.put("ERREUR_SAISIE", "Erreur de saisie");
        MOTIFS.put("PERTE", "Perte / vol");
        MOTIFS.put("NON_ENREGISTRE", "Mouvement non enregistré");
        MOTIFS.put("RETOUR", "Retour");
        MOTIFS.put("AUTRE", "Autre");
    }

    static {
        MOTIFS.forEach((code, libelle) -> {
            MOTIF_PAR_CLE.put(key(code), code);
            MOTIF_PAR_CLE.put(key(libelle), code);
        });
    }

    /**
     * Refuse une feuille d'un autre inventaire (titre « Inventaire INV-… du … »).
     */
    private static void verifierReference(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator, String attendue) {
        for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), 2); r++) {
            Row row = sheet.getRow(r);
            if (row == null || row.getLastCellNum() < 0) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String value = text(row, c, formatter, evaluator);
                if (value.startsWith("Inventaire ") && value.contains(" du ")) {
                    String reference = value.substring("Inventaire ".length(), value.indexOf(" du ")).trim();
                    if (!reference.equalsIgnoreCase(attendue)) {
                        throw new BusinessException("INVENTORY_SHEET_OTHER_INVENTORY",
                                "Cette feuille concerne l'inventaire " + reference + " et non " + attendue + ".");
                    }
                    return;
                }
            }
        }
    }

    // ---- Écriture ----------------------------------------------------------------------------------------------

    private static Map<String, Integer> headers(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Integer> cols = new HashMap<>();
        if (row == null || row.getLastCellNum() < 0) return cols;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            String k = key(text(row, c, formatter, evaluator));
            if (!k.isEmpty()) cols.putIfAbsent(k.equals("motif") ? key(COL_MOTIF) : k, c);
        }
        return cols;
    }

    // ---- Lecture -----------------------------------------------------------------------------------------------

    private static String text(Row row, Integer col, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (col == null) return "";
        Cell cell = row.getCell(col);
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
    }

    /**
     * Cellule numérique (valeur exacte, sans bruit binaire) ou texte « 12,5 » ; {@code null} si vide.
     */
    static BigDecimal quantite(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA ? evaluator.evaluateFormulaCell(cell) : cell.getCellType();
        if (type == CellType.NUMERIC) {
            return new BigDecimal(NumberToTextConverter.toText(cell.getNumericCellValue()));
        }
        String raw = formatter.formatCellValue(cell, evaluator)
                .replace('\u00A0', ' ').replace('\u202F', ' ').replace(" ", "").replace(',', '.').trim();
        return raw.isEmpty() ? null : new BigDecimal(raw);
    }

    private static UUID uuid(String value) {
        try {
            return value.isEmpty() ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Clé de comparaison : minuscules, sans accents ni ponctuation (« N° de lot » = « n de lot »).
     */
    static String key(String value) {
        String n = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String nz(String value) {
        return value != null ? value : "";
    }

    /**
     * @param aveugle sans quantité théorique (bonne pratique : on compte sans connaître le stock informatique)
     */
    public byte[] ecrire(Inventaire inv, boolean aveugle) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            CellStyle saisie = wb.createCellStyle();
            saisie.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            saisie.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet sheet = wb.createSheet("Comptage");
            Row title = sheet.createRow(0);
            title.createCell(1).setCellValue("Inventaire " + inv.getReference() + " du " + inv.getDateInventaire().format(FR));
            title.getCell(1).setCellStyle(bold);
            sheet.createRow(1).createCell(1).setCellValue(
                    "Renseignez la colonne « " + COL_QTY + " » (0 si le lot est absent) et le motif en cas d'écart, "
                            + "puis importez ce fichier depuis l'écran de comptage. Ne modifiez pas la colonne A (masquée).");

            List<String> headers = new ArrayList<>(List.of(COL_ID, COL_CODE, "Article", "Unité", COL_LOT, "Péremption"));
            if (!aveugle) headers.add("Théorique");
            headers.addAll(List.of(COL_QTY, COL_MOTIF, "Compté par"));
            int qtyCol = headers.indexOf(COL_QTY);
            int motifCol = headers.indexOf(COL_MOTIF);

            Row header = sheet.createRow(3);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
                header.getCell(i).setCellStyle(bold);
                sheet.setColumnWidth(i, (i == 2 ? 40 : i == motifCol ? 26 : 16) * 256);
            }
            sheet.setColumnHidden(0, true);
            sheet.createFreezePane(0, 4);

            int r = 4;
            for (LigneInventaire l : inv.getLignes()) {
                Row row = sheet.createRow(r++);
                int c = 0;
                row.createCell(c++).setCellValue(l.getId().toString());
                row.createCell(c++).setCellValue(nz(l.getArticleCode()));
                row.createCell(c++).setCellValue(nz(l.getArticleLibelle()));
                row.createCell(c++).setCellValue(nz(l.getUnite()));
                row.createCell(c++).setCellValue(nz(l.getNumeroLot()));
                row.createCell(c++).setCellValue(l.getDatePeremption() != null ? l.getDatePeremption().format(FR) : "");
                if (!aveugle) row.createCell(c++).setCellValue(l.getQuantiteTheorique().doubleValue());
                Cell qty = row.createCell(c++);
                if (l.getQuantiteComptee() != null) qty.setCellValue(l.getQuantiteComptee().doubleValue());
                qty.setCellStyle(saisie);
                Cell motif = row.createCell(c++);
                if (l.getMotifEcart() != null)
                    motif.setCellValue(MOTIFS.getOrDefault(l.getMotifEcart(), l.getMotifEcart()));
                motif.setCellStyle(saisie);
                row.createCell(c).setCellValue(nz(l.getComptePar()));
            }

            if (r > 4) {
                DataValidationHelper helper = sheet.getDataValidationHelper();
                DataValidation qtyRule = helper.createValidation(
                        helper.createDecimalConstraint(DataValidationConstraintOps.GE, "0", null),
                        new CellRangeAddressList(4, r - 1, qtyCol, qtyCol));
                qtyRule.setShowErrorBox(true);
                qtyRule.createErrorBox("Quantité invalide", "Saisissez une quantité positive ou nulle.");
                sheet.addValidationData(qtyRule);
                DataValidation motifRule = helper.createValidation(
                        helper.createExplicitListConstraint(MOTIFS.values().toArray(String[]::new)),
                        new CellRangeAddressList(4, r - 1, motifCol, motifCol));
                motifRule.setShowErrorBox(true);
                sheet.addValidationData(motifRule);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Lecture lire(byte[] content, String referenceAttendue) {
        if (content == null || content.length == 0) {
            throw new BusinessException("IMPORT_EMPTY_FILE", "Le fichier est vide.");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new BusinessException("IMPORT_FILE_TOO_LARGE", "Le fichier dépasse 5 Mo.");
        }
        boolean zip = content.length > 3 && content[0] == 'P' && content[1] == 'K';
        boolean ole2 = content.length > 7 && (content[0] & 0xFF) == 0xD0 && (content[1] & 0xFF) == 0xCF;
        if (!zip && !ole2) {
            throw new BusinessException("IMPORT_UNSUPPORTED_FORMAT",
                    "Importez la feuille de comptage au format Excel (.xlsx), telle que téléchargée puis remplie.");
        }
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.FRANCE);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            verifierReference(sheet, formatter, evaluator, referenceAttendue);

            int headerRow = -1;
            Map<String, Integer> cols = Map.of();
            for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS); r++) {
                Map<String, Integer> found = headers(sheet.getRow(r), formatter, evaluator);
                if (found.containsKey(key(COL_QTY))) {
                    headerRow = r;
                    cols = found;
                    break;
                }
            }
            if (headerRow < 0 || (!cols.containsKey(key(COL_ID)) && !cols.containsKey(key(COL_CODE)))) {
                throw new BusinessException("INVENTORY_SHEET_INVALID",
                        "Colonnes « " + COL_QTY + " » et « " + COL_CODE + " » introuvables : utilisez la feuille de comptage téléchargée.");
            }

            List<ComptageImporte> lignes = new ArrayList<>();
            List<Anomalie> anomalies = new ArrayList<>();
            int last = Math.min(sheet.getLastRowNum(), headerRow + MAX_ROWS);
            for (int r = headerRow + 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                int numero = r + 1;
                String id = text(row, cols.get(key(COL_ID)), formatter, evaluator);
                String code = text(row, cols.get(key(COL_CODE)), formatter, evaluator);
                if (id.isEmpty() && code.isEmpty()) continue;
                String lot = text(row, cols.get(key(COL_LOT)), formatter, evaluator);
                String motifTexte = text(row, cols.get(key(COL_MOTIF)), formatter, evaluator);

                BigDecimal quantite;
                try {
                    quantite = quantite(row.getCell(cols.get(key(COL_QTY))), formatter, evaluator);
                } catch (NumberFormatException e) {
                    anomalies.add(new Anomalie(numero, "Quantité illisible pour l'article " + code + " : nombre attendu."));
                    continue;
                }
                if (quantite != null && quantite.signum() < 0) {
                    anomalies.add(new Anomalie(numero, "Quantité négative pour l'article " + code + "."));
                    continue;
                }
                String motif = null;
                if (!motifTexte.isEmpty()) {
                    motif = MOTIF_PAR_CLE.get(key(motifTexte));
                    if (motif == null) {
                        anomalies.add(new Anomalie(numero, "Motif « " + motifTexte + " » inconnu : choisissez parmi "
                                + String.join(", ", MOTIFS.values()) + "."));
                        continue;
                    }
                }
                lignes.add(new ComptageImporte(numero, uuid(id), code.isEmpty() ? null : code,
                        lot.isEmpty() ? null : lot, quantite, motif));
            }
            return new Lecture(lignes, anomalies);
        } catch (EncryptedDocumentException e) {
            throw new BusinessException("IMPORT_ENCRYPTED_FILE", "Le classeur est protégé par mot de passe : retirez la protection puis réessayez.");
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("IMPORT_UNREADABLE_FILE", "Le classeur Excel est illisible ou endommagé.");
        }
    }

    /**
     * Résultat de la lecture : lignes exploitables et lignes illisibles.
     */
    public record Lecture(List<ComptageImporte> lignes, List<Anomalie> anomalies) {
    }

    /**
     * Opérateurs des contraintes de validation Excel (POI les expose en constantes int).
     */
    private static final class DataValidationConstraintOps {
        static final int GE = org.apache.poi.ss.usermodel.DataValidationConstraint.OperatorType.GREATER_OR_EQUAL;
    }
}

