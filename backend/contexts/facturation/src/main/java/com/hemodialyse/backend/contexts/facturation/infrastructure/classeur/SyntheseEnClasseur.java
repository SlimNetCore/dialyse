package com.hemodialyse.backend.contexts.facturation.infrastructure.classeur;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le classeur Excel de la synthèse mensuelle (PL8, KB-PL8-4 corrigé) : un vrai tableau, une ligne par forfait, une colonne HT et une TTC
 * par caisse puis les totaux, valeurs numériques arrondies à 2 décimales ; ensuite les montants enregistrés, les indicateurs et les
 * répartitions. Nom de feuille complet (limite Excel : 31 caractères). Période sans facture : feuille d'en-têtes et mention explicite.
 */
public final class SyntheseEnClasseur {

    public static final String NOM_DE_LA_FEUILLE = "Synthèse mensuelle facturation";
    static final String SANS_FACTURE = "Aucune facture sur la période.";

    private SyntheseEnClasseur() {
    }

    public static byte[] classeur(SyntheseMensuelle synthese) {
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Sheet feuille = classeur.createSheet(NOM_DE_LA_FEUILLE);
            CellStyle montant = classeur.createCellStyle();
            montant.setDataFormat(classeur.createDataFormat().getFormat("#,##0.00"));
            CellStyle gras = classeur.createCellStyle();
            Font police = classeur.createFont();
            police.setBold(true);
            gras.setFont(police);

            int ligne = 0;
            ligne = texte(feuille, ligne, gras, "Synthèse mensuelle de facturation", "Période : " + synthese.debut() + " au " + synthese.fin());
            ligne++;

            List<SyntheseMensuelle.Caisse> colonnes = synthese.colonnes();
            Row entete = feuille.createRow(ligne++);
            int c = 0;
            cellule(entete, c++, "Forfait", gras);
            for (SyntheseMensuelle.Caisse caisse : colonnes) {
                cellule(entete, c++, caisse.libelle() + " HT", gras);
                cellule(entete, c++, caisse.libelle() + " TTC", gras);
            }
            cellule(entete, c++, "Total HT", gras);
            cellule(entete, c, "Total TTC", gras);

            Map<String, Map<String, SyntheseMensuelle.Ligne>> parForfait = new java.util.TreeMap<>();
            for (SyntheseMensuelle.Ligne l : synthese.lignes()) {
                parForfait.computeIfAbsent(l.forfait(), k -> new HashMap<>()).put(l.caisse(), l);
            }
            BigDecimal[] sommes = new BigDecimal[colonnes.size() * 2 + 2];
            java.util.Arrays.fill(sommes, BigDecimal.ZERO);
            for (Map.Entry<String, Map<String, SyntheseMensuelle.Ligne>> forfait : parForfait.entrySet()) {
                Row row = feuille.createRow(ligne++);
                row.createCell(0).setCellValue(forfait.getKey());
                BigDecimal totalHt = BigDecimal.ZERO;
                BigDecimal totalTtc = BigDecimal.ZERO;
                for (int i = 0; i < colonnes.size(); i++) {
                    SyntheseMensuelle.Ligne l = forfait.getValue().get(colonnes.get(i).code());
                    BigDecimal ht = l == null ? BigDecimal.ZERO : l.ht();
                    BigDecimal ttc = l == null ? BigDecimal.ZERO : l.ttc();
                    nombre(row, 1 + 2 * i, ht, montant);
                    nombre(row, 2 + 2 * i, ttc, montant);
                    sommes[2 * i] = sommes[2 * i].add(ht);
                    sommes[2 * i + 1] = sommes[2 * i + 1].add(ttc);
                    totalHt = totalHt.add(ht);
                    totalTtc = totalTtc.add(ttc);
                }
                nombre(row, 1 + 2 * colonnes.size(), totalHt, montant);
                nombre(row, 2 + 2 * colonnes.size(), totalTtc, montant);
                sommes[sommes.length - 2] = sommes[sommes.length - 2].add(totalHt);
                sommes[sommes.length - 1] = sommes[sommes.length - 1].add(totalTtc);
            }
            if (parForfait.isEmpty()) {
                feuille.createRow(ligne++).createCell(0).setCellValue(SANS_FACTURE);
            } else {
                Row total = feuille.createRow(ligne++);
                cellule(total, 0, "Total", gras);
                for (int i = 0; i < sommes.length; i++) {
                    nombre(total, 1 + i, sommes[i], montant);
                }
            }

            ligne++;
            ligne = montantNomme(feuille, ligne, montant, "Montant HT enregistré", synthese.totalHt());
            ligne = montantNomme(feuille, ligne, montant, "Montant TVA enregistré", synthese.totalTva());
            ligne = montantNomme(feuille, ligne, montant, "Montant TTC enregistré", synthese.totalTtc());
            ligne = entier(feuille, ligne, "Patients", synthese.patients());
            ligne = entier(feuille, ligne, "Séances", synthese.seances());
            ligne = entier(feuille, ligne, "Factures", synthese.factures());

            ligne++;
            ligne = repartition(feuille, ligne, gras, "Répartition par caisse", synthese.parCaisse());
            repartition(feuille, ligne, gras, "Répartition par situation du patient", synthese.parSituation());

            for (int i = 0; i <= colonnes.size() * 2 + 2; i++) {
                feuille.autoSizeColumn(i);
            }
            classeur.write(sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int texte(Sheet feuille, int ligne, CellStyle gras, String titre, String sousTitre) {
        cellule(feuille.createRow(ligne), 0, titre, gras);
        feuille.createRow(ligne + 1).createCell(0).setCellValue(sousTitre);
        return ligne + 2;
    }

    private static int montantNomme(Sheet feuille, int ligne, CellStyle montant, String nom, BigDecimal valeur) {
        Row row = feuille.createRow(ligne);
        row.createCell(0).setCellValue(nom);
        nombre(row, 1, valeur, montant);
        return ligne + 1;
    }

    private static int entier(Sheet feuille, int ligne, String nom, long valeur) {
        Row row = feuille.createRow(ligne);
        row.createCell(0).setCellValue(nom);
        row.createCell(1).setCellValue(valeur);
        return ligne + 1;
    }

    private static int repartition(Sheet feuille, int ligne, CellStyle gras, String titre, List<SyntheseMensuelle.Repartition> tranches) {
        Row entete = feuille.createRow(ligne++);
        cellule(entete, 0, titre, gras);
        cellule(entete, 1, "Patients", gras);
        cellule(entete, 2, "Séances", gras);
        for (SyntheseMensuelle.Repartition t : tranches) {
            Row row = feuille.createRow(ligne++);
            row.createCell(0).setCellValue(t.libelle());
            row.createCell(1).setCellValue(t.patients());
            row.createCell(2).setCellValue(t.seances());
        }
        return ligne;
    }

    private static void cellule(Row row, int colonne, String valeur, CellStyle style) {
        Cell cell = row.createCell(colonne);
        cell.setCellValue(valeur);
        cell.setCellStyle(style);
    }

    private static void nombre(Row row, int colonne, BigDecimal valeur, CellStyle style) {
        Cell cell = row.createCell(colonne);
        cell.setCellValue(valeur.setScale(2, RoundingMode.HALF_UP).doubleValue());
        cell.setCellStyle(style);
    }
}
